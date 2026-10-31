package com.bookassistant.controller.ai;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.BookStorageService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.CacheService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.service.infrastructure.CacheService.CacheIndex;
import com.bookassistant.service.infrastructure.CacheService.CachedImage;
import com.bookassistant.service.text.MarkdownService;
import com.bookassistant.service.text.TextDirectionService;
import com.bookassistant.session.BookSession;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.controller.web.ModelAttributeBinder;
import com.bookassistant.model.BookData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.bookassistant.dto.PageSummaryRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generates general and per-page AI summaries with a chunked pipeline.
 * <p>
 * <b>What:</b> Splits the book into page chunks, asks the LLM to extract the most
 * valuable passages without interpretation, then synthesizes a final concise summary.
 * <p>
 * <b>Why:</b> Large books cannot be summarized in a single API call due to context
 * window limits; the two-phase extract-then-synthesize approach preserves authorial
 * wording while keeping token usage bounded.
 * <p>
 * <b>Where:</b> Required for the general and page-range summary study features.
 */
@Controller
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);
    
    private static final int MAX_CHUNK_SIZE = 5;
    private static final int MAX_CHUNK_TOKENS = 4000;

    private final BookSession bookSession;
    private final AiService aiService;
    private final MarkdownService markdownService;
    private final TextDirectionService textDirectionService;
    private final CacheService cacheService;
    private final BookOutputService bookOutputService;
    private final MessageSource messageSource;
    private final OnDemandExtractionService onDemandExtractionService;
    private final BookStorageService bookStorageService;
    private final String pagesMode;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession        session-scoped book state
     * @param aiService          LLM interaction facade
     * @param markdownService    Markdown-to-HTML converter
     * @param textDirectionService RTL/LTR formatter
     * @param cacheService       figure cache for matching images
     */
    public SummaryController(BookSession bookSession, AiService aiService, 
                             MarkdownService markdownService, TextDirectionService textDirectionService,
                             CacheService cacheService, BookOutputService bookOutputService, MessageSource messageSource,
                              OnDemandExtractionService onDemandExtractionService,
                              BookStorageService bookStorageService,
                              @Value("${app.summary.pages-mode:aisummary}") String pagesMode) {
        this.bookSession = bookSession;
        this.aiService = aiService;
        this.markdownService = markdownService;
        this.textDirectionService = textDirectionService;
        this.cacheService = cacheService;
        this.bookOutputService = bookOutputService;
        this.messageSource = messageSource;
        this.onDemandExtractionService = onDemandExtractionService;
        this.bookStorageService = bookStorageService;
        this.pagesMode = pagesMode != null ? pagesMode : "fulltext";
        log.info("SummaryController initialized | pagesMode={}", this.pagesMode);
    }

    @PostMapping("/summary/general")
    public String generateGeneralSummary(@RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                         HttpSession session, Model model) {
        LoggingUtils.enter(log, "generateGeneralSummary", "language", outputLanguage);
        long startTime = System.currentTimeMillis();

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized summary access attempt");
            return "redirect:/login";
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = outputLanguage != null ? outputLanguage : bookLang;

        model.addAttribute("hasBook", bookSession.hasBook());
        model.addAttribute("bookLanguage", bookLang);

        if (!bookSession.hasBook()) {
            log.warn("General summary failed: no book loaded");
            String errorMessage = messageSource.getMessage("summary.book.required", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("summaryGeneral", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("summaryGeneralDirection", bookLang);
            model.addAttribute("summaryGeneralLanguage", bookLang);
            model.addAttribute("summaryGeneralHtmlAttributes", "");
            model.addAttribute("summaryGeneralInlineStyle", "");
            return "index";
        }

        int totalPages = bookSession.getPageCount();
        log.info("Starting general summary | totalPages={} | language={} | hasBook={}", totalPages, lang, bookSession.hasBook());
        String bookFileName = bookSession.getBook().map(BookData::fileName).orElse("null");
        log.info("Book in session | fileName={} | pagesInSession={}", bookFileName, bookSession.getBook().map(b -> b.pages().size()).orElse(0));

        Long bookId = bookSession.resolveBookId(userId);
        if (bookId != null) {
            try {
                bookSession.loadBookById(bookId);
            } catch (Exception e) {
                log.warn("Failed to load book from DB for general summary | bookId={} | error={}", bookId, e.getMessage());
            }
        }
        if (bookId != null && bookSession.getPageCount() == 0) {
            int pdfPageCount = bookSession.getPdfPageCount();
            if (pdfPageCount > 0) {
                try {
                    BookData extracted = onDemandExtractionService.extractPages(bookId, 1, pdfPageCount);
                    bookSession.setBook(extracted);
                    bookSession.setCurrentBookId(bookId);
                    totalPages = extracted.getPageCount();
                    log.info("On-demand general extraction complete | bookId={} | pages={}", bookId, totalPages);
                } catch (Exception e) {
                    log.error("On-demand extraction failed for general summary | bookId={} | error={}", bookId, e.getMessage());
                }
            }
        }

        try {
            int chunkSize = Math.min(MAX_CHUNK_SIZE, Math.max(10, totalPages / 50));
            java.util.List<String> chunkSummaries = new java.util.ArrayList<>();
            java.util.List<String> skipReasons = new java.util.ArrayList<>();
            int totalChunks = 0;

            for (int i = 1; i <= totalPages; i += chunkSize) {
                int startPage = i;
                int endPage = Math.min(i + chunkSize - 1, totalPages);
                totalChunks++;
                log.info("Summarizing chunk | pages={}-{}", startPage, endPage);

                String textChunk = bookSession.extractTextFromPageRange(startPage, endPage);
                log.info("Extracted text for chunk {}-{} | length={}", startPage, endPage, textChunk != null ? textChunk.length() : 0);
                if (ModelAttributeBinder.isEmpty(textChunk)) {
                    String reason = "Skipped pages " + startPage + "-" + endPage + ": no extracted text from Pix2Text";
                    log.warn("SUMMARY SKIP | reason=EMPTY_TEXT | pages={}-{} | bookId={}", startPage, endPage, bookId);
                    skipReasons.add(reason);
                    continue;
                }

                int originalChunkLen = textChunk.length();
                textChunk = aiService.truncateToTokens(textChunk, MAX_CHUNK_TOKENS);
                if (textChunk.length() < originalChunkLen) {
                    log.warn("General summary chunk truncated | pages={}-{} | originalLen={} | truncatedLen={}", startPage, endPage, originalChunkLen, textChunk.length());
                }

                String userContent = "Book text (pages " + startPage + "-" + endPage + "):\n" + textChunk;
                String systemPrompt = getChunkSummaryPrompt(startPage, endPage);
                String chunkSummary = aiService.getAnswerFromAi(systemPrompt, userContent, lang);
                log.info("Chunk summary result for pages {}-{} | length={} | startsWithError={}", startPage, endPage, chunkSummary != null ? chunkSummary.length() : 0, chunkSummary != null && chunkSummary.startsWith("AI error:"));

                if (ModelAttributeBinder.isEmpty(chunkSummary) || chunkSummary.startsWith("AI error:")) {
                    String errorDetail = chunkSummary != null ? chunkSummary : "null response";
                    String reason = "Skipped pages " + startPage + "-" + endPage + ": AI error — " + errorDetail;
                    log.warn("SUMMARY SKIP | reason=AI_ERROR | pages={}-{} | error={}", startPage, endPage, errorDetail);
                    skipReasons.add(reason);
                    continue;
                }

                chunkSummaries.add(chunkSummary);
            }

            model.addAttribute("summaryPagesSkippedChunks", skipReasons.size());
            model.addAttribute("summaryPagesTotalChunks", totalChunks);
            model.addAttribute("summaryPagesSkipReasons", skipReasons);

            if (chunkSummaries.isEmpty()) {
                log.error("chunkSummaries is empty | totalPages={} | hasBook={}", totalPages, bookSession.hasBook());
                String errorMessage = messageSource.getMessage("summary.error", null, LocaleContextHolder.getLocale());
                ModelAttributeBinder.addError(model, errorMessage);
                model.addAttribute("summaryGeneral", "<p class='text-danger'>" + errorMessage + "</p>");
                model.addAttribute("summaryGeneralDirection", bookLang);
                model.addAttribute("summaryGeneralLanguage", bookLang);
                model.addAttribute("summaryGeneralHtmlAttributes", "");
                model.addAttribute("summaryGeneralInlineStyle", "");
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }

            String finalSummary = String.join("\n\n==========\n\n", chunkSummaries);

            String htmlAnswer = markdownService.convertMarkdownToHtml(finalSummary);
            TextDirectionService.FormattedResponse formattedResponse = 
                textDirectionService.createFormattedResponse(htmlAnswer, lang);

            model.addAttribute("summaryGeneral", htmlAnswer);
            model.addAttribute("summaryGeneralDirection", formattedResponse.getDirection());
            model.addAttribute("summaryGeneralLanguage", formattedResponse.getLanguage());
            model.addAttribute("summaryGeneralHtmlAttributes", formattedResponse.getHtmlAttributes());
            model.addAttribute("summaryGeneralInlineStyle", formattedResponse.getInlineStyle());

            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, "summary_general", htmlAnswer, null, null);
            }

            addCacheToModel(model, finalSummary);

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "generateGeneralSummary", "duration", duration + "ms");
            log.info("General summary completed | duration={}ms", duration);

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "generateGeneralSummary", e);
            log.error("General summary failed | duration={}ms | error={}", duration, e.getMessage());
            String baseMessage = messageSource.getMessage("summary.processing.error", null, LocaleContextHolder.getLocale());
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String errorMessage = baseMessage + ": " + detail;
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("summaryGeneral", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("summaryGeneralDirection", bookLang);
            model.addAttribute("summaryGeneralLanguage", bookLang);
            model.addAttribute("summaryGeneralHtmlAttributes", "");
            model.addAttribute("summaryGeneralInlineStyle", "");
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }
    }

    @PostMapping("/summary/pages")
    public String generatePagesSummary(@RequestParam int startPage,
                                      @RequestParam int endPage,
                                      @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                      @RequestParam(value = "chunkSize", required = false, defaultValue = "auto") String chunkSizeParam,
                                      HttpSession session, Model model) {
        LoggingUtils.enter(log, "generatePagesSummary", "range", startPage + "-" + endPage);
        long startTime = System.currentTimeMillis();

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized pages summary access attempt");
            return "redirect:/login";
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = outputLanguage != null ? outputLanguage : bookLang;

        model.addAttribute("hasBook", bookSession.hasBook());
        model.addAttribute("bookLanguage", bookLang);

        if (!bookSession.hasBook()) {
            log.warn("Pages summary failed: no book loaded");
            String errorMessage = messageSource.getMessage("summary.book.required", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("summaryPages", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("summaryPagesDirection", bookLang);
            model.addAttribute("summaryPagesLanguage", bookLang);
            model.addAttribute("summaryPagesHtmlAttributes", "");
            model.addAttribute("summaryPagesInlineStyle", "");
            return "index";
        }

        if (startPage < 1 || endPage < startPage) {
            String errorMessage = messageSource.getMessage("summary.invalid.range", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("summaryPages", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("summaryPagesDirection", bookLang);
            model.addAttribute("summaryPagesLanguage", bookLang);
            model.addAttribute("summaryPagesHtmlAttributes", "");
            model.addAttribute("summaryPagesInlineStyle", "");
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }

        int totalPages = bookSession.getPageCount();
        if (totalPages == 0) {
            totalPages = bookSession.getPdfPageCount();
        }
        if (endPage > totalPages && totalPages > 0) {
            String errorMessage = messageSource.getMessage("summary.end.page.exceeds", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("summaryPages", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("summaryPagesDirection", bookLang);
            model.addAttribute("summaryPagesLanguage", bookLang);
            model.addAttribute("summaryPagesHtmlAttributes", "");
            model.addAttribute("summaryPagesInlineStyle", "");
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }

        Long bookId = bookSession.resolveBookId(userId);
        if (bookId != null) {
            try {
                bookSession.loadBookById(bookId);
            } catch (Exception e) {
                log.warn("Failed to load book from DB for pages summary | bookId={} | error={}", bookId, e.getMessage());
            }
        }
        if (bookId != null && bookSession.getPageCount() == 0) {
            try {
                BookData extracted = onDemandExtractionService.extractPages(bookId, startPage, endPage);
                bookSession.setBook(extracted);
                bookSession.setCurrentBookId(bookId);
                log.info("On-demand pages extraction complete | bookId={} | pages={}", bookId, extracted.getPageCount());
            } catch (Exception e) {
                log.error("On-demand extraction failed for pages summary | bookId={} | error={}", bookId, e.getMessage());
            }
        }

        try {
            String answer;
            java.util.List<String> skipReasons = new java.util.ArrayList<>();
            int skippedChunks = 0;
            int processedChunks = 0;

            if ("aisummary".equalsIgnoreCase(pagesMode)) {
                int requestedPages = endPage - startPage + 1;
                
                // Determine chunk size: auto, manual, or model-only
                int chunkSize;
                if ("auto".equalsIgnoreCase(chunkSizeParam)) {
                    chunkSize = calculateAutoChunkSize(startPage, endPage, lang);
                    log.info("Auto chunk size calculated | pages={}-{} | chunkSize={}", startPage, endPage, chunkSize);
                } else if ("model-only".equalsIgnoreCase(chunkSizeParam) || "modelonly".equalsIgnoreCase(chunkSizeParam)) {
                    chunkSize = requestedPages;
                } else {
                    try {
                        chunkSize = Integer.parseInt(chunkSizeParam);
                        chunkSize = Math.max(1, Math.min(chunkSize, requestedPages));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid chunk size parameter: {}, using auto", chunkSizeParam);
                        chunkSize = calculateAutoChunkSize(startPage, endPage, lang);
                    }
                }
                
                java.util.List<String> chunkSummaries = new java.util.ArrayList<>();

                for (int i = startPage; i <= endPage; i += chunkSize) {
                    int chunkStart = i;
                    int chunkEnd = Math.min(i + chunkSize - 1, endPage);
                    log.info("Summarizing chunk | pages={}-{}", chunkStart, chunkEnd);

                    String textChunk = bookSession.extractTextFromPageRange(chunkStart, chunkEnd);
                    if (ModelAttributeBinder.isEmpty(textChunk)) {
                        String reason = "Skipped pages " + chunkStart + "-" + chunkEnd + ": no extracted text from Pix2Text";
                        log.warn("SUMMARY SKIP | reason=EMPTY_TEXT | pages={}-{}", chunkStart, chunkEnd);
                        skippedChunks++;
                        skipReasons.add(reason);
                        continue;
                    }
                    log.debug("Chunk {}-{} extracted text length: {}", chunkStart, chunkEnd, textChunk.length());

                    int originalChunkLen = textChunk.length();
                    textChunk = aiService.truncateToTokens(textChunk, MAX_CHUNK_TOKENS);
                    if (textChunk.length() < originalChunkLen) {
                        log.warn("Pages summary chunk truncated | pages={}-{} | originalLen={} | truncatedLen={}", chunkStart, chunkEnd, originalChunkLen, textChunk.length());
                    }

                    String userContent = "Book text (pages " + chunkStart + "-" + chunkEnd + "):\n" + textChunk;
                    String systemPrompt = getChunkSummaryPrompt(chunkStart, chunkEnd);
                    String chunkSummary = aiService.getAnswerFromAi(systemPrompt, userContent, lang);

                    if (ModelAttributeBinder.isEmpty(chunkSummary)) {
                        String reason = "Skipped pages " + chunkStart + "-" + chunkEnd + ": AI returned empty response";
                        log.warn("SUMMARY SKIP | reason=AI_EMPTY | pages={}-{}", chunkStart, chunkEnd);
                        skippedChunks++;
                        skipReasons.add(reason);
                        continue;
                    }
                    log.debug("Chunk {}-{} AI response length: {}", chunkStart, chunkEnd, chunkSummary.length());

                    if (chunkSummary.startsWith("AI error:")) {
                        log.warn("AI error for chunk {}-{}: {}", chunkStart, chunkEnd, chunkSummary);
                        skippedChunks++;
                        continue;
                    }

                    chunkSummaries.add("Pages " + chunkStart + "-" + chunkEnd + ":\n" + chunkSummary);
                    processedChunks++;

                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Chunk processing interrupted");
                        break;
                    }
                }

                log.info("Pages summary complete | requested={}-{} | processed={} | skipped={} | totalChunks={}",
                    startPage, endPage, processedChunks, skippedChunks,
                    (endPage - startPage + 1 + chunkSize - 1) / chunkSize);

                int totalChunks = (requestedPages + chunkSize - 1) / chunkSize;
                model.addAttribute("summaryPagesProcessedChunks", processedChunks);
                model.addAttribute("summaryPagesSkippedChunks", skippedChunks);
                model.addAttribute("summaryPagesTotalChunks", totalChunks);

                if (skippedChunks > 0) {
                    String warningMessage = messageSource.getMessage("summary.chunks.skipped", new Object[]{skippedChunks, totalChunks}, LocaleContextHolder.getLocale());
                    ModelAttributeBinder.addError(model, warningMessage);
                }

                if (chunkSummaries.isEmpty()) {
                    String errorMessage = messageSource.getMessage("summary.error", null, LocaleContextHolder.getLocale());
                    ModelAttributeBinder.addError(model, errorMessage);
                    model.addAttribute("summaryPages", "<p class='text-danger'>" + errorMessage + "</p>");
                    model.addAttribute("summaryPagesDirection", bookLang);
                    model.addAttribute("summaryPagesLanguage", bookLang);
                    model.addAttribute("summaryPagesHtmlAttributes", "");
                    model.addAttribute("summaryPagesInlineStyle", "");
                    model.addAttribute("hasBook", bookSession.hasBook());
                    model.addAttribute("bookLanguage", bookLang);
                    return "index";
                }

                answer = String.join("\n\n==========\n\n", chunkSummaries);
            } else {
                log.info("Extracting full text for pages {}-{}", startPage, endPage);
                String fullText = bookSession.extractTextFromPageRange(startPage, endPage);
                if (ModelAttributeBinder.isEmpty(fullText)) {
                    String errorMessage = messageSource.getMessage("summary.no.text", null, LocaleContextHolder.getLocale());
                    ModelAttributeBinder.addError(model, errorMessage);
                    model.addAttribute("summaryPages", "<p class='text-danger'>" + errorMessage + "</p>");
                    model.addAttribute("summaryPagesDirection", bookLang);
                    model.addAttribute("summaryPagesLanguage", bookLang);
                    model.addAttribute("summaryPagesHtmlAttributes", "");
                    model.addAttribute("summaryPagesInlineStyle", "");
                    model.addAttribute("hasBook", bookSession.hasBook());
                    model.addAttribute("bookLanguage", bookLang);
                    return "index";
                }
                answer = fullText;
            }
            
            String htmlAnswer = markdownService.convertMarkdownToHtml(answer);
            TextDirectionService.FormattedResponse formattedResponse = 
                textDirectionService.createFormattedResponse(htmlAnswer, lang);

            model.addAttribute("summaryPages", htmlAnswer);
            model.addAttribute("summaryPagesDirection", formattedResponse.getDirection());
            model.addAttribute("summaryPagesLanguage", formattedResponse.getLanguage());
            model.addAttribute("summaryPagesHtmlAttributes", formattedResponse.getHtmlAttributes());
            model.addAttribute("summaryPagesInlineStyle", formattedResponse.getInlineStyle());
            model.addAttribute("summaryPagesSkippedChunks", skippedChunks);
            model.addAttribute("summaryPagesTotalChunks", processedChunks + skippedChunks);
            model.addAttribute("summaryPagesSkipReasons", skipReasons);
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);

            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, "summary_pages", htmlAnswer, null, null);
            }

            addCacheToModel(model, answer);

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "generatePagesSummary", "duration", duration + "ms");
            log.info("Pages summary completed | duration={}ms", duration);

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "generatePagesSummary", e);
            log.error("Pages summary failed | duration={}ms | error={}", duration, e.getMessage());
            String baseMessage = messageSource.getMessage("summary.processing.error", null, LocaleContextHolder.getLocale());
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String errorMessage = baseMessage + ": " + detail;
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("summaryPages", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("summaryPagesDirection", bookLang);
            model.addAttribute("summaryPagesLanguage", bookLang);
            model.addAttribute("summaryPagesHtmlAttributes", "");
            model.addAttribute("summaryPagesInlineStyle", "");
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }
    }

    @PostMapping(
            value = "/summary/pages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generatePagesSummaryJson(
            @RequestBody PageSummaryRequest request,
            HttpSession session) {

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        if (request.startPage() < 1 || request.endPage() < request.startPage()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid page range"));
        }

        if (request.bookId() != null) {
            try {
                bookSession.loadBookById(request.bookId());
            } catch (Exception e) {
                log.warn("Failed to load book from DB | bookId={} | error={}", request.bookId(), e.getMessage());
            }
        }

        if (!bookSession.hasBook()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No book loaded in session"));
        }

        int totalPages = bookSession.getPageCount();
        if (totalPages == 0) {
            totalPages = bookSession.getPdfPageCount();
        }
        if (request.endPage() > totalPages && totalPages > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "End page exceeds total book pages"));
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = request.outputLanguage() != null ? request.outputLanguage() : bookLang;
        String chunkSizeParam = request.chunkSize() != null ? request.chunkSize() : "auto";

        int startPage = request.startPage();
        int endPage = request.endPage();

        try {
            String answer;
            java.util.List<String> skipReasons = new java.util.ArrayList<>();
            int skippedChunks = 0;
            int processedChunks = 0;

            if ("aisummary".equalsIgnoreCase(pagesMode)) {
                int requestedPages = endPage - startPage + 1;
                int chunkSize;
                if ("auto".equalsIgnoreCase(chunkSizeParam)) {
                    chunkSize = calculateAutoChunkSize(startPage, endPage, lang);
                } else if ("model-only".equalsIgnoreCase(chunkSizeParam) || "modelonly".equalsIgnoreCase(chunkSizeParam)) {
                    chunkSize = requestedPages;
                } else {
                    try {
                        chunkSize = Integer.parseInt(chunkSizeParam);
                        chunkSize = Math.max(1, Math.min(chunkSize, requestedPages));
                    } catch (NumberFormatException e) {
                        chunkSize = calculateAutoChunkSize(startPage, endPage, lang);
                    }
                }

                java.util.List<String> chunkSummaries = new java.util.ArrayList<>();

                for (int i = startPage; i <= endPage; i += chunkSize) {
                    int chunkStart = i;
                    int chunkEnd = Math.min(i + chunkSize - 1, endPage);

                    String textChunk = bookSession.extractTextFromPageRange(chunkStart, chunkEnd);
                    if (ModelAttributeBinder.isEmpty(textChunk)) {
                        String reason = "Skipped pages " + chunkStart + "-" + chunkEnd + ": no extracted text";
                        skipReasons.add(reason);
                        skippedChunks++;
                        continue;
                    }

                    textChunk = aiService.truncateToTokens(textChunk, MAX_CHUNK_TOKENS);
                    String userContent = "Book text (pages " + chunkStart + "-" + chunkEnd + "):\n" + textChunk;
                    String systemPrompt = getChunkSummaryPrompt(chunkStart, chunkEnd);
                    String chunkSummary = aiService.getAnswerFromAi(systemPrompt, userContent, lang);

                    if (ModelAttributeBinder.isEmpty(chunkSummary) || chunkSummary.startsWith("AI error:")) {
                        String reason = "Skipped pages " + chunkStart + "-" + chunkEnd + ": AI error — " + chunkSummary;
                        skipReasons.add(reason);
                        skippedChunks++;
                        continue;
                    }

                    chunkSummaries.add("Pages " + chunkStart + "-" + chunkEnd + ":\n" + chunkSummary);
                    processedChunks++;
                }

                if (chunkSummaries.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "No summary could be generated"));
                }

                answer = String.join("\n\n==========\n\n", chunkSummaries);
            } else {
                String fullText = bookSession.extractTextFromPageRange(startPage, endPage);
                if (ModelAttributeBinder.isEmpty(fullText)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "No text extracted from pages"));
                }
                answer = fullText;
            }

            String htmlAnswer = markdownService.convertMarkdownToHtml(answer);
            TextDirectionService.FormattedResponse formattedResponse =
                    textDirectionService.createFormattedResponse(htmlAnswer, lang);

            Map<String, Object> response = new HashMap<>();
            response.put("summaryPages", htmlAnswer);
            response.put("summaryPagesDirection", formattedResponse.getDirection());
            response.put("summaryPagesLanguage", formattedResponse.getLanguage());
            response.put("summaryPagesHtmlAttributes", formattedResponse.getHtmlAttributes());
            response.put("summaryPagesInlineStyle", formattedResponse.getInlineStyle());
            response.put("summaryPagesSkippedChunks", skippedChunks);
            response.put("summaryPagesTotalChunks", processedChunks + skippedChunks);
            response.put("summaryPagesSkipReasons", skipReasons);

            if (request.bookId() != null && userId != null) {
                bookOutputService.saveOutput(userId, request.bookId(), "summary_pages", htmlAnswer, null, null);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Pages summary failed | error={}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Processing failed: " + e.getMessage()));
        }
    }

    private String getChunkSummaryPrompt(int chunkStart, int chunkEnd) {
        return """
            CRITICAL RULES — APPLY TO EVERY LINE OF OUTPUT:
            1. NO COMMENTARY: Do NOT add any introductory phrases, explanations, or conversational filler.
               FORBIDDEN: "It seems like...", "Here's a summary...", "Based on the text...", etc.
            2. NO PARAPHRASING: Copy EXACTLY word-for-word. Every sentence must match the source text.
            3. MATH PRESERVATION: Preserve ALL mathematical notation in its EXACT original form:
               - Display math: \\[...\\] and $$...$$
               - Inline math: \\(...\\)
               - XML tags: <math>...</math>
               Do NOT convert, simplify, or remove any mathematical notation.
            4. NO HTML IN MATH: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br> inside math.
               Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
            5. PAGE CITATIONS: ONLY cite page numbers between Page %d and Page %d. NEVER cite pages outside this range.
            6. COVERAGE: You MUST cover the FULL page range provided; do not skip pages.
            7. LANGUAGE: Keep the book's original language. Use the UI language for category labels only.

            NOW COPY VERBATIM into these categories:

            Core Content: Copy verbatim sentences that express theories, principles, definitions,
            or conceptual frameworks. These are the foundational ideas the book is built on—
            the "what is" and "why it matters" parts.

            Key Insights: Copy verbatim sentences that express practical advice, actionable steps,
            methods, techniques, warnings, or counter-intuitive observations. These are the
            "how to" and "what to watch out for" parts—the actionable knowledge.

            Notable Passages: Copy verbatim the most impactful quotes, memorable statements,
            or powerful assertions made by the author. These are the passages a reader would
            want to highlight, bookmark, or share—the "remember this" moments.
            """.formatted(chunkStart, chunkEnd);
    }

    private String getFinalSummaryPrompt() {
        return """
            CRITICAL RULES — APPLY TO EVERY LINE OF OUTPUT:
            1. NO COMMENTARY: Do NOT add any introductory phrases, explanations, or conversational filler.
               FORBIDDEN: "It seems like...", "Here's a summary...", "Based on the text...", etc.
            2. NO PARAPHRASING: Copy EXACTLY word-for-word. Every sentence must match the source text.
            3. MATH PRESERVATION: Preserve ALL mathematical notation in its EXACT original form:
               - Display math: \\[...\\] and $$...$$
               - Inline math: \\(...\\)
               - XML tags: <math>...</math>
               Do NOT convert, simplify, or remove any mathematical notation.
            4. NO HTML IN MATH: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br> inside math.
               Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
             5. LANGUAGE: Keep the book's original language. Use the UI language for category labels only.

            NOW COMBINE the following extracts into one organized document.
            ONLY copy text EXACTLY as written. Do NOT summarize, paraphrase, or interpret.

            Combine all extracts into these THREE mandatory categories:

            Core Content: Copy verbatim all theories, principles, definitions, or conceptual
            frameworks—the foundational ideas the book is built on.

            Key Insights: Copy verbatim all practical advice, actionable steps, methods,
            techniques, warnings, or counter-intuitive observations—the actionable knowledge.

            Notable Passages: Copy verbatim all impactful quotes, memorable statements, or
            powerful assertions made by the author—the passages a reader would want to
            highlight, bookmark, or share.

            RULES:
            - Copy text EXACTLY word-for-word from the book
            - Do NOT add your own words or explanations
            - Do NOT summarize or paraphrase
            - Preserve the author's exact wording
            - Preserve page citations [Page X] from the original extracts
            - You MUST cover the FULL page range provided; do not skip pages
            """;
    }

    private int calculateAutoChunkSize(int startPage, int endPage, String language) {
        int sampleCount = Math.min(3, endPage - startPage + 1);
        long totalChars = 0;
        int sampledPages = 0;
        
        for (int i = 0; i < sampleCount; i++) {
            int pageNum = startPage + (i * Math.max(1, (endPage - startPage + 1) / sampleCount));
            if (pageNum > endPage) pageNum = endPage;
            
            String text = bookSession.extractTextFromPageRange(pageNum, pageNum);
            if (!ModelAttributeBinder.isEmpty(text)) {
                totalChars += text.length();
                sampledPages++;
            }
        }
        
        if (sampledPages == 0) {
            log.warn("Auto chunk size: no text extracted from sample pages, using default of 5");
            return 5;
        }
        
        double avgCharsPerPage = (double) totalChars / sampledPages;
        log.info("Auto chunk size | sampledPages={} | avgCharsPerPage={}", sampledPages, String.format("%.0f", avgCharsPerPage));
        
        // Estimate chunk size based on average chars per page
        // Gemma3:4b with 4K context can handle roughly 2000-2500 chars of input text
        // Be conservative: aim for ~2000 chars per chunk to leave room for prompt and output
        int targetCharsPerChunk = 2000;
        int estimatedChunkSize = (int) Math.ceil(targetCharsPerChunk / avgCharsPerPage);
        
        // Clamp between 1 and 20
        int chunkSize = Math.max(1, Math.min(estimatedChunkSize, 20));
        
        log.info("Auto chunk size result | avgCharsPerPage={} | estimatedChunkSize={} | finalChunkSize={}", 
            String.format("%.0f", avgCharsPerPage), estimatedChunkSize, chunkSize);
        
        return chunkSize;
    }

    private void addCacheToModel(Model model, String responseText) {
        String bookName = bookSession.getBook().map(b -> b.fileName()).orElse("");
        if (ModelAttributeBinder.isEmpty(bookName)) return;
        if (bookName.contains(".")) {
            bookName = bookName.substring(0, bookName.lastIndexOf('.'));
        }
        CacheIndex index = cacheService.loadIndex(bookName);
        if (index == null || index.isEmpty()) return;

        List<CachedImage> matched = cacheService.findImagesForPages(index, responseText);
        model.addAttribute("cacheBookName", index.bookName);
        model.addAttribute("cacheImages", matched);
        model.addAttribute("cacheTotalImages", index.totalImages);
    }
}




