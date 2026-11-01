package com.bookassistant.controller.study;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.session.BookSession;
import com.bookassistant.model.BookData;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.controller.web.ModelAttributeBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Generates an actionable study plan from selected book pages.
 * <p>
 * <b>What:</b> Receives a page range, extracts the text, and asks the AI to convert
 * it into a structured action plan.
 * <p>
 * <b>Why:</b> Provides a concrete, actionable output for learners who want practical
 * next steps rather than abstract summaries.
 */
@Controller
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);
    
    private final BookSession bookSession;
    private final AiService aiService;
    private final BookOutputService bookOutputService;
    private final MessageSource messageSource;
    private final OnDemandExtractionService onDemandExtractionService;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession session-scoped book state
     * @param aiService   LLM interaction facade
     */
    public PlanController(BookSession bookSession, AiService aiService, BookOutputService bookOutputService, MessageSource messageSource,
                          OnDemandExtractionService onDemandExtractionService) {
        if (bookSession == null) throw new IllegalArgumentException("BookSession must not be null");
        if (aiService == null) throw new IllegalArgumentException("AiService must not be null");
        
        this.bookSession = bookSession;
        this.aiService = aiService;
        this.bookOutputService = bookOutputService;
        this.messageSource = messageSource;
        this.onDemandExtractionService = onDemandExtractionService;
        
        log.info("PlanController initialized");
    }

    /**
     * This name was chosen because this function generates an actionable plan from the given pages.
     * Validates inputs and converts content to action items.
     */
    @PostMapping("/action-plan")
    public String generateActionPlan(@RequestParam int startPage,
                                     @RequestParam int endPage,
                                     @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                     HttpSession session, Model model) {
        LoggingUtils.enter(log, "generateActionPlan", "range", startPage + "-" + endPage);
        long startTime = System.currentTimeMillis();

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized action-plan access attempt");
            return "redirect:/login";
        }

        // Validate page range
        if (startPage < 1) {
            LoggingUtils.validationFailure(log, "startPage", "must be >= 1", startPage);
            ModelAttributeBinder.addError(model, messageSource.getMessage("plan.start.page.invalid", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }
        if (endPage < startPage) {
            LoggingUtils.validationFailure(log, "endPage", "must be >= startPage", endPage);
            ModelAttributeBinder.addError(model, messageSource.getMessage("plan.end.page.invalid", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = outputLanguage != null ? outputLanguage : bookLang;

        try {
            String text = bookSession.extractTextFromPageRange(startPage, endPage);
            
            if (ModelAttributeBinder.isEmpty(text)) {
                Long bookId = bookSession.resolveBookId(userId);
                if (bookId != null) {
                    try {
                        bookSession.loadBookById(bookId);
                    } catch (Exception e) {
                        log.warn("Failed to load book from DB for plan | bookId={} | error={}", bookId, e.getMessage());
                    }
                }
                if (bookId != null && bookSession.getPageCount() == 0) {
                    try {
                        BookData extracted = onDemandExtractionService.extractPages(bookId, startPage, endPage);
                        bookSession.setBook(extracted);
                        bookSession.setCurrentBookId(bookId);
                        text = bookSession.extractTextFromPageRange(startPage, endPage);
                        log.info("On-demand plan extraction complete | bookId={} | pages={}", bookId, extracted.getPageCount());
                    } catch (IOException e) {
                        log.error("On-demand extraction failed for plan | bookId={} | error={}", bookId, e.getMessage());
                    }
                }
            }
            
            if (ModelAttributeBinder.isEmpty(text)) {
                log.warn("Action plan failed: no text in range {}-{}", startPage, endPage);
                ModelAttributeBinder.addError(model, messageSource.getMessage("plan.no.text", null, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }

            log.info("Processing action plan | pages={}-{} | textLen={}", startPage, endPage, text.length());

            String systemPrompt = "en".equals(lang)
                ? "Convert these pages into an actionable action plan. Do not provide a condensed plan. Create a comprehensive, detailed action plan covering all pages. You must cite explicit page numbers [Page X] for each recommended action. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc."
                : "حوّل هذه الصفحات إلى خطة تنفيذية عملية. لا تقدم خطة مختصرة. أنشئ خطة تنفيذية شاملة ومفصلة تغطي جميع الصفحات. يجب أن تستشهد بأرقام الصفحات الصريحة [Page X] لكل إجراء موصى به. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.";
            
            String userContent = "Book text (pages " + startPage + "-" + endPage + "):\n" + text;
            String answer = aiService.getAnswerFromAi(systemPrompt, userContent, lang);
            
            // Check for AI error
            if (ModelAttributeBinder.isEmpty(answer) || answer.startsWith("AI error:")) {
                log.warn("Action plan failed: AI error");
                ModelAttributeBinder.addError(model, messageSource.getMessage("plan.error.with.answer", new Object[]{answer}, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }
            
            model.addAttribute("actionPlan", answer);
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);

            Long bookId = bookSession.resolveBookId(userId);
            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, "action_plan", answer, null, null);
            }

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "generateActionPlan", "duration", duration + "ms");
            log.info("Action plan completed | duration={}ms", duration);

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "generateActionPlan", e);
            log.error("Action plan failed | duration={}ms | error={}", duration, e.getMessage());
            ModelAttributeBinder.addError(model, messageSource.getMessage("plan.processing.error", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }
    }
}




