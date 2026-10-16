package com.bookassistant.controller.study;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.text.MarkdownService;
import com.bookassistant.service.text.TextDirectionService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.session.BookSession;
import com.bookassistant.model.BookData;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.controller.web.ModelAttributeBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

/**
 * Generates AI-powered comprehension tests for selected page ranges.
 * <p>
 * <b>What:</b> Accepts a start and end page, extracts the text, and delegates to
 * {@link AiService#createComprehensionTest(String, int, int, String)} to produce
 * multiple-choice, short-answer, and critical-thinking questions.
 * <p>
 * <b>Why:</b> Focused study assessment is a first-class feature; isolating it in its own
 * controller keeps the endpoint map one-to-one with user actions.
 */
@Controller
public class ComprehensionController {

    private static final Logger log = LoggerFactory.getLogger(ComprehensionController.class);
    
    private final BookSession bookSession;
    private final AiService aiService;
    private final MarkdownService markdownService;
    private final TextDirectionService textDirectionService;
    private final BookOutputService bookOutputService;
    private final MessageSource messageSource;
    private final OnDemandExtractionService onDemandExtractionService;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession        session-scoped book state
     * @param aiService          LLM interaction facade
     * @param markdownService    Markdown-to-HTML converter
     * @param textDirectionService RTL/LTR formatter
     */
    public ComprehensionController(BookSession bookSession, AiService aiService, 
                                   MarkdownService markdownService, TextDirectionService textDirectionService,
                                   BookOutputService bookOutputService, MessageSource messageSource,
                                   OnDemandExtractionService onDemandExtractionService) {
        if (bookSession == null) throw new IllegalArgumentException("BookSession must not be null");
        if (aiService == null) throw new IllegalArgumentException("AiService must not be null");
        if (markdownService == null) throw new IllegalArgumentException("MarkdownService must not be null");
        if (textDirectionService == null) throw new IllegalArgumentException("TextDirectionService must not be null");
        
        this.bookSession = bookSession;
        this.aiService = aiService;
        this.markdownService = markdownService;
        this.textDirectionService = textDirectionService;
        this.bookOutputService = bookOutputService;
        this.messageSource = messageSource;
        this.onDemandExtractionService = onDemandExtractionService;
        
        log.info("ComprehensionController initialized");
    }

    /**
     * Handles {@code POST /comprehension}.
     * <p>
     * <b>Where:</b> Required for the comprehension-test study feature.
     *
     * @param startPage      1-based start page
     * @param endPage        1-based end page (inclusive)
     * @param outputLanguage desired response language code
     * @param model           the view model
     * @return the Thymeleaf view name
     */
    @PostMapping("/comprehension")
    public String generateComprehension(@RequestParam int startPage,
                                        @RequestParam int endPage,
                                        @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                        HttpSession session, Model model) {
        LoggingUtils.enter(log, "generateComprehension", "range", startPage + "-" + endPage);
        long startTime = System.currentTimeMillis();

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized comprehension access attempt");
            return "redirect:/login";
        }

        // Validate page range
        if (startPage < 1) {
            LoggingUtils.validationFailure(log, "startPage", "must be >= 1", startPage);
            ModelAttributeBinder.addError(model, messageSource.getMessage("comprehension.start.page.invalid", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }
        if (endPage < startPage) {
            LoggingUtils.validationFailure(log, "endPage", "must be >= startPage", endPage);
            ModelAttributeBinder.addError(model, messageSource.getMessage("comprehension.end.page.invalid", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = outputLanguage != null ? outputLanguage : bookLang;
        if (lang == null) lang = "en";

        try {
            String text = bookSession.extractTextFromPageRange(startPage, endPage);
            
            if (ModelAttributeBinder.isEmpty(text)) {
Long bookId = bookSession.resolveBookId(userId);
                if (bookId != null) {
                    try {
                        bookSession.loadBookById(bookId);
                    } catch (Exception e) {
                        log.warn("Failed to load book from DB for comprehension | bookId={} | error={}", bookId, e.getMessage());
                    }
                }
                if (bookId != null && bookSession.getPageCount() == 0) {
                    try {
                        BookData extracted = onDemandExtractionService.extractPages(bookId, startPage, endPage);
                        bookSession.setBook(extracted);
                        bookSession.setCurrentBookId(bookId);
                        text = bookSession.extractTextFromPageRange(startPage, endPage);
                        log.info("On-demand comprehension extraction complete | bookId={} | pages={}", bookId, extracted.getPageCount());
                    } catch (IOException e) {
                        log.error("On-demand extraction failed for comprehension | bookId={} | error={}", bookId, e.getMessage());
                    }
                }
            }
            
            if (ModelAttributeBinder.isEmpty(text)) {
                log.warn("Comprehension test failed: no text in range {}-{}", startPage, endPage);
                ModelAttributeBinder.addError(model, messageSource.getMessage("comprehension.no.text", null, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }

            log.info("Processing comprehension test | pages={}-{} | textLen={} | lang={}", startPage, endPage, text.length(), lang);

            String answer = aiService.createComprehensionTest(text, startPage, endPage, lang);
            
            // Check for AI error
            if (ModelAttributeBinder.isEmpty(answer) || answer.startsWith("AI error:")) {
                log.warn("Comprehension test failed: AI error");
                ModelAttributeBinder.addError(model, messageSource.getMessage("comprehension.error.with.answer", new Object[]{answer}, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }
            
            String htmlAnswer = markdownService.convertMarkdownToHtml(answer);
            TextDirectionService.FormattedResponse formattedResponse = 
                textDirectionService.createFormattedResponse(htmlAnswer, lang);

            model.addAttribute("comprehension", htmlAnswer);
            model.addAttribute("comprehensionDirection", formattedResponse.getDirection());
            model.addAttribute("comprehensionLanguage", formattedResponse.getLanguage());
            model.addAttribute("comprehensionHtmlAttributes", formattedResponse.getHtmlAttributes());
            model.addAttribute("comprehensionInlineStyle", formattedResponse.getInlineStyle());
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);

            Long bookId = bookSession.resolveBookId(userId);
            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, "comprehension", htmlAnswer, null, null);
            }

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "generateComprehension", "duration", duration + "ms");
            log.info("Comprehension test completed | duration={}ms", duration);

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "generateComprehension", e);
            log.error("Comprehension test failed | duration={}ms | error={}", duration, e.getMessage());
            ModelAttributeBinder.addError(model, messageSource.getMessage("comprehension.processing.error", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }
    }
}


