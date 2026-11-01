package com.bookassistant.controller.ai;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.BookStorageService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.session.BookSession;
import com.bookassistant.model.BookData;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.controller.web.ModelAttributeBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Performs bounded semantic search inside the loaded book.
 * <p>
 * <b>What:</b> Truncates the full book text to a maximum of 50 KB, sends the query
 * and text to the AI model, and renders the answer.
 * <p>
 * <b>Why:</b> Prevents oversized prompts that would exceed the LLM context window or
 * cause timeouts; enforces a 1 KB query limit to avoid DoS.
 */
@Controller
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    
    private static final int MAX_QUERY_LENGTH = 1024;
    private static final int MAX_SEARCH_TOKENS = 12000;

    private final BookSession bookSession;
    private final AiService aiService;
    private final BookOutputService bookOutputService;
    private final MessageSource messageSource;
    private final OnDemandExtractionService onDemandExtractionService;
    private final BookStorageService bookStorageService;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession session-scoped book state
     * @param aiService   LLM interaction facade
     */
    public SearchController(BookSession bookSession, AiService aiService, BookOutputService bookOutputService, MessageSource messageSource,
                             OnDemandExtractionService onDemandExtractionService,
                             BookStorageService bookStorageService) {
        if (bookSession == null) throw new IllegalArgumentException("BookSession must not be null");
        if (aiService == null) throw new IllegalArgumentException("AiService must not be null");
        
        this.bookSession = bookSession;
        this.aiService = aiService;
        this.bookOutputService = bookOutputService;
        this.messageSource = messageSource;
        this.onDemandExtractionService = onDemandExtractionService;
        this.bookStorageService = bookStorageService;
        
        log.info("SearchController initialized");
    }

    /**
     * Handles {@code POST /semantic-search}.
     * <p>
     * <b>Where:</b> Required for the semantic-search study feature; truncates text to
     * prevent oversized AI prompts.
     *
     * @param query          the search query
     * @param outputLanguage desired response language code
     * @param model          the view model
     * @return the Thymeleaf view name
     */
    @PostMapping("/semantic-search")
    public String performSemanticSearch(@RequestParam String query,
                                        @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                        HttpSession session, Model model) {
        LoggingUtils.enter(log, "performSemanticSearch", "queryLen", ModelAttributeBinder.safeLength(query));
        long startTime = System.currentTimeMillis();

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized semantic-search access attempt");
            return "redirect:/login";
        }

        // Validate query
        if (ModelAttributeBinder.isEmpty(query)) {
            LoggingUtils.validationFailure(log, "query", "must not be empty", query);
            ModelAttributeBinder.addError(model, messageSource.getMessage("search.query.required", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }

        // Validate query length
        if (query.length() > MAX_QUERY_LENGTH) {
            LoggingUtils.validationFailure(log, "query", "too long", query.length() + " chars");
            ModelAttributeBinder.addError(model, messageSource.getMessage("search.query.too.long", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = outputLanguage != null ? outputLanguage : bookLang;

        try {
            // Check if book is loaded
            if (!bookSession.hasBook()) {
                log.warn("Semantic search failed: no book loaded");
                 ModelAttributeBinder.addError(model, messageSource.getMessage("search.book.required", null, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", false);
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }

            String text = bookSession.getBook().map(b -> b.fullText()).orElse("");
            Long bookId = bookSession.resolveBookId(userId);
            if (bookId != null) {
                try {
                    bookSession.loadBookById(bookId);
                    text = bookSession.getBook().map(b -> b.fullText()).orElse(text);
                } catch (Exception e) {
                    log.warn("Failed to load book from DB for search | bookId={} | error={}", bookId, e.getMessage());
                }
            }
            if (bookId != null && text.isBlank()) {
                try {
                    BookData extracted = onDemandExtractionService.extractPages(bookId, 1, bookSession.getPdfPageCount() == 0 ? 9999 : bookSession.getPdfPageCount());
                    bookSession.setBook(extracted);
                    bookSession.setCurrentBookId(bookId);
                    bookSession.persistExtractedText(bookId, extracted.fullText());
                    text = extracted.fullText();
                    log.info("On-demand search extraction complete | bookId={} | pages={}", bookId, extracted.getPageCount());
                } catch (IOException e) {
                    log.error("On-demand extraction failed for search | bookId={} | error={}", bookId, e.getMessage());
                }
            }
            String answer;
            
            if (bookId != null && !text.isBlank()) {
                log.info("Using RAG semantic search | bookId={}", bookId);
                String systemPrompt = "en".equals(lang)
                    ? "Search semantically in the text and answer the question. Do not provide a condensed summary. Provide detailed findings with explicit page citations [Page X] for all claims. CRITICAL: Respond entirely in English. All text must be in English. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc."
                    : "ابحث دلالياً في النص وأجب عن السؤال. لا تقدم ملخصاً مختصراً. قدّم نتائج مفصلة مع استشهادات صريحة بأرقام الصفحات [Page X] لكل ادعاء. قاعدة حرجة: أجب باللغة العربية فقط. جميع النصوص يجب أن تكون بالعربية. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.";
                
                String userContent = "en".equals(lang)
                    ? "Query: " + query
                    : "السؤال: " + query;
                
                answer = aiService.getAnswerFromAi(systemPrompt, userContent, lang, bookId, null);
                log.info("Semantic search (primary) completed");
            } else {
                int originalTextLen = text.length();
                text = aiService.truncateToTokens(text, MAX_SEARCH_TOKENS);
                if (text.length() < originalTextLen) {
                    log.warn("Semantic search text truncated | originalLen={} | maxTokens={}", originalTextLen, MAX_SEARCH_TOKENS);
                }
                
                log.info("Processing semantic search (fallback) | language={} | textLen={}", lang, text.length());

                String systemPrompt = "en".equals(lang)
                    ? "Search semantically in the text and answer the question. Do not provide a condensed summary. Provide detailed findings with explicit page citations [Page X] for all claims. CRITICAL: Respond entirely in English. All text must be in English. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc."
                    : "ابحث دلالياً في النص وأجب عن السؤال. لا تقدم ملخصاً مختصراً. قدّم نتائج مفصلة مع استشهادات صريحة بأرقام الصفحات [Page X] لكل ادعاء. قاعدة حرجة: أجب باللغة العربية فقط. جميع النصوص يجب أن تكون بالعربية. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.";
                
                String userContent = "en".equals(lang)
                    ? "Query: " + query + "\n\nBook text:\n" + text
                    : "السؤال: " + query + "\n\nالنص:\n" + text;
                
                answer = aiService.getAnswerFromAi(systemPrompt, userContent, lang);
                log.info("Semantic search (fallback) completed");
            }
            
            // Check for AI error
            if (ModelAttributeBinder.isEmpty(answer) || answer.startsWith("AI error:")) {
                log.warn("Semantic search failed: AI error");
                 ModelAttributeBinder.addError(model, messageSource.getMessage("search.error.with.answer", new Object[]{answer}, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }
            
            model.addAttribute("semanticSearch", answer);
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);

            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, "semantic_search", answer, null, null);
            }

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "performSemanticSearch", "duration", duration + "ms");
            log.info("Semantic search completed | duration={}ms", duration);

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "performSemanticSearch", e);
            log.error("Semantic search failed | duration={}ms | error={}", duration, e.getMessage());
            ModelAttributeBinder.addError(model, messageSource.getMessage("search.processing.error", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }
    }

    private void addCacheToModel(Model model, String responseText) {
    }
}

