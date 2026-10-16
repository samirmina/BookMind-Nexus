package com.bookassistant.controller.ai;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.BookStorageService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.service.text.MarkdownService;
import com.bookassistant.service.text.TextDirectionService;
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
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles book analysis requests: reality check, concept map, and risk flags.
 * <p>
 * <b>What:</b> Three public endpoints that send structured system prompts to the AI
 * model, receive analysis results, format them as HTML, and inject direction metadata
 * for bilingual display.
 * <p>
 * <b>Why:</b> Reuses the shared {@link #processAnalysis(String, int, int, String, org.springframework.ui.Model, SystemPromptFactory)}
 * template to keep controller logic DRY and map endpoints one-to-one with user actions,
 * per {@code PROJECT_RULES.md} Section 1.
 */
@Controller
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    
    private static final int MAX_CHUNK_PAGES = 10;

    private final BookSession bookSession;
    private final AiService aiService;
    private final MarkdownService markdownService;
    private final TextDirectionService textDirectionService;
    private final BookOutputService bookOutputService;
    private final MessageSource messageSource;
    private final OnDemandExtractionService onDemandExtractionService;
    private final BookStorageService bookStorageService;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession        session-scoped book state
     * @param aiService          LLM interaction facade
     * @param markdownService    Markdown-to-HTML converter
     * @param textDirectionService RTL/LTR formatter
     */
    public AnalysisController(BookSession bookSession, AiService aiService, 
                              MarkdownService markdownService, TextDirectionService textDirectionService,
                              BookOutputService bookOutputService, MessageSource messageSource,
                               OnDemandExtractionService onDemandExtractionService,
                               BookStorageService bookStorageService) {
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
        this.bookStorageService = bookStorageService;
        
        log.info("AnalysisController initialized");
    }

    /**
     * This name was chosen because this function performs a reality check on the text.
     * Analyzes assumptions and realism in the content.
     */
    @PostMapping("/reality-check")
    public String performRealityCheck(@RequestParam int startPage,
                                      @RequestParam int endPage,
                                      @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                      HttpSession session, Model model) {
        log.info("Reality check request | pages={}-{}", startPage, endPage);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized reality-check access attempt");
            return "redirect:/login";
        }

        return processAnalysis("reality-check", startPage, endPage, outputLanguage, model, userId,
            (lang) -> "ar".equals(lang)
                ? """
                    أنت محلل نقدي خبير. مهمتك فحص واقعية الادعاءات والافتراضات في النص المقدم.
                    
                    قدّم تحليلك بالتنسيق التالي:
                    
                    ## الادعاءات الرئيسية
                    - سرد الادعاءات أو الافتراضات الأساسية في النص
                    
                    ## فحص الواقعية
                    لكل ادعاء، قيّم:
                    - **مدعوم بأدلة**: هل هناك بيانات، إحصائيات، أو مراجع تدعمه؟
                    - **منطقي**: هل الاستنتاج يتبع المقدمات بشكل معقول؟
                    - **عملي**: هل يمكن تطبيقه في الواقع؟
                    - **متحيز**: هل هناك تحيز أو افتراضات غير مفحوصة؟
                    
                    ## التحذيرات
                    - الافتراضات المشكوك فيها
                    - المبالغات أو التعميمات
                    - ما تم تجاهله أو إغفاله
                    
                    ## الحكم النهائي
                    تقييم شامل لمدى واقعية النص (استخدم: واقعي جداً / واقعي إلى حد ما / مختلط / غير واقعي إلى حد ما / غير واقعي)
                    
                      كن دقيقاً، موضوعياً، واستخدم أمثلة محددة من النص.
                      Do not generate a condensed high-level overview. Provide a detailed, page-by-page analysis covering the full range. You must cite explicit page numbers [Page X] for every claim, assumption, or example you evaluate.
                      You MUST cover the FULL page range provided; do not skip pages.
                       CRITICAL LANGUAGE RULE: Respond entirely in Arabic. All text must be in Arabic. Do not mix languages.
                        PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                       """
                : """
                      You are an expert critical analyst. Your task is to examine the realism of claims and assumptions in the provided text.
                      
                      Structure your reality check analysis as follows:
                      
                      ## Key Claims
                      - List the main claims or assumptions in the text
                      
                      ## Reality Check
                      For each claim, evaluate:
                      - **Evidence-supported**: Are there data, statistics, or references supporting it?
                      - **Logical**: Does the conclusion reasonably follow from the premises?
                      - **Practical**: Can it be applied in reality?
                      - **Biased**: Are there biases or unexamined assumptions?
                      
                      ## Red Flags
                      - Questionable assumptions
                      - Overstatements or generalizations
                      - What's ignored or omitted
                      
                      ## Final Verdict
                      Overall assessment of text's realism (use: Very Realistic / Somewhat Realistic / Mixed / Somewhat Unrealistic / Unrealistic)
                      
                       Be precise, objective, and use specific examples from the text.
                       Do not generate a condensed high-level overview. Provide a detailed, page-by-page analysis covering the full range. You must cite explicit page numbers [Page X] for every claim, assumption, or example you evaluate.
                       You MUST cover the FULL page range provided; do not skip pages.
                       CRITICAL LANGUAGE RULE: Respond entirely in English. All text must be in English. Do not mix languages.
                        PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                        """);
    }

    /**
     * Creates a concept map from the text.
     * <p>
     * <b>What:</b> Extracts important concepts, their relationships, hierarchy,
     * key principles, and practical applications from the given page range.
     * <p>
     * <b>Why:</b> Required for the Insights concept-map feature.
     * <p>
     * <b>Where:</b> Implements the knowledge-representation strategy.
     */
    @PostMapping("/concept-map")
    public String createConceptMap(@RequestParam int startPage,
                                   @RequestParam int endPage,
                                   @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                   HttpSession session, Model model) {
        log.info("Concept map request | pages={}-{}", startPage, endPage);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized concept-map access attempt");
            return "redirect:/login";
        }

        return processAnalysis("concept-map", startPage, endPage, outputLanguage, model, userId,
            (lang) -> "ar".equals(lang)
                 ? """
                     أنت خبير في تمثيل المعرفة. مهمتك استخراج خريطة مفاهيم من النص ONLY.
                     
                     قدّم عملك بالتنسيق التالي:
                     
                     ## المفاهيم الأساسية
                     قائمة بأهم 5-10 مفاهيم في النص، مع تعريف موجز لكل منها:
                     - **المفهوم**: تعريف مختصر
                     
                     ## العلاقات بين المفاهيم
                     صف كيف ترتبط المفاهيم ببعضها:
                     - المفهوم أ ← [نوع العلاقة] → المفهوم ب
                     - مثال: "الإنتاجية" ← [تتأثر بـ] → "إدارة الوقت"
                     
                     ## التسلسل الهرمي
                     نظّم المفاهيم من العام إلى الخاص:
                     - مفهوم عام
                       - مفهوم فرعي 1
                       - مفهوم فرعي 2
                     - مفهوم عام آخر
                       - مفهوم فرعي 3
                     
                     ## المبادئ الرئيسية
                     - المبادئ أو القوانين الأساسية المستخلصة
                     - كيف تترابط هذه المبادئ
                     
                      ## التطبيقات العملية
                      كيف يمكن تطبيق هذه المفاهيم في الواقع؟
                      
                         استخدم تنسيقاً واضحاً يسهل فهمه وتذكره.
                         Do not provide a condensed summary. Extract comprehensive concepts from the full page range. You must cite explicit page numbers [Page X] for every concept, relationship, and principle.
                         You MUST cover the FULL page range provided; do not skip pages.
                        
                         CRITICAL LANGUAGE RULE: Respond entirely in Arabic. All text, labels, and descriptions must be in Arabic. Do not mix languages.
                          PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                           FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
                          


                         At the END of your response, include a JSON block with the graph structure:
                       ```json
                       {
                         "nodes": [
                           {"id": "1", "label": "Concept Name", "group": "core"},
                           {"id": "2", "label": "Sub-concept", "group": "sub"}
                         ],
                         "edges": [
                           {"from": "1", "to": "2", "label": "relationship type"}
                         ]
                       }
                       ```
                       RULES FOR JSON:
                       - ONLY include concepts that are explicitly mentioned in the text.
                       - VERIFY each concept label appears verbatim in the text before including it.
                       - If a concept does not appear word-for-word in the text, OMIT it entirely.
                       - Do NOT invent, guess, or hallucinate concepts, nodes, or edges.
                       - If you cannot identify at least 2 concepts, return an empty nodes array and empty edges array.
                       - Groups must be exactly: "core", "sub", "principle", "application".
                       - The JSON must be valid and parseable.
                       """
                   : """
                      You are a knowledge representation expert. Your task is to extract a comprehensive concept map from the text ONLY.
                      
                      Structure your output as follows:
                      
                      ## Core Concepts
                      List the 5-10 most important concepts with brief definitions:
                      - **Concept**: Brief definition
                      
                      ## Relationships Between Concepts
                      Describe how concepts connect:
                      - Concept A ← [relationship type] → Concept B
                      - Example: "Productivity" ← [influenced by] → "Time Management"
                      
                      ## Hierarchy
                      Organize concepts from general to specific:
                      - Broad concept
                        - Sub-concept 1
                        - Sub-concept 2
                      - Another broad concept
                        - Sub-concept 3
                      
                      ## Key Principles
                      - Fundamental principles or laws extracted
                      - How these principles interconnect
                      
                       ## Practical Applications
                       How can these concepts be applied in reality?
                       
                        Use clear formatting that's easy to understand and remember.
                        
                        Do not provide a condensed summary. Extract comprehensive concepts from the full page range. You must cite explicit page numbers [Page X] for every concept, relationship, and principle.
                        
                       CRITICAL LANGUAGE RULE: Respond entirely in English. All text, labels, and descriptions must be in English. Do not mix languages.
                        PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.

                        At the END of your response, include a JSON block with the graph structure:
                      ```json
                      {
                        "nodes": [
                          {"id": "1", "label": "Concept Name", "group": "core"},
                          {"id": "2", "label": "Sub-concept", "group": "sub"}
                        ],
                        "edges": [
                          {"from": "1", "to": "2", "label": "relationship type"}
                        ]
                      }
                      ```
                      RULES FOR JSON:
                      - ONLY include concepts that are explicitly mentioned in the text.
                      - VERIFY each concept label appears verbatim in the text before including it.
                      - If a concept does not appear word-for-word in the text, OMIT it entirely.
                      - Do NOT invent, guess, or hallucinate concepts, nodes, or edges.
                      - If you cannot identify at least 2 concepts, return an empty nodes array and empty edges array.
                      - Groups must be exactly: "core", "sub", "principle", "application".
                      - The JSON must be valid and parseable.
                      """);
    }

    /**
     * Identifies risk flags in the text.
     * <p>
     * <b>What:</b> Extracts warnings, risk alerts, and practical recommendations
     * from the given page range.
     * <p>
     * <b>Why:</b> Required for the Insights risk-flags feature.
     * <p>
     * <b>Where:</b> Implements the risk-analysis strategy.
     */
    @PostMapping("/risk-flags")
    public String identifyRiskFlags(@RequestParam int startPage,
                                    @RequestParam int endPage,
                                    @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                    HttpSession session, Model model) {
        log.info("Risk flags request | pages={}-{}", startPage, endPage);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized risk-flags access attempt");
            return "redirect:/login";
        }

        return processAnalysis("risk-flags", startPage, endPage, outputLanguage, model, userId,
            (lang) -> "ar".equals(lang)
                ? """
                    أنت محلل مخاطر خبير. مهمتك تحديد واستخراج جميع تحذيرات المخاطر من النص.
                    
                    قدّم تحليلك بالتنسيق التالي:
                    
                    ## 🚨 المخاطر الحرجة
                    المخاطر التي قد تسبب ضرراً جسيماً إذا تم تجاهلها:
                    - **الخطر**: الوصف
                      - الاحتمال: [منخفض / متوسط / عالي]
                      - الأثر: [منخفض / متوسط / كارثي]
                      - العلامات التحذيرية: كيف تكتشفه مبكراً
                    
                    ## ⚠️ المخاطر المتوسطة
                    مخاطر مهمة تتطلب الانتباه:
                    - **الخطر**: الوصف
                      - كيفية التخفيف: خطوات عملية
                    
                    ## 📋 اعتبارات مهمة
                    نقاط تستحق الانتباه لكنها ليست مخاطر حرجة:
                    - اعتبار 1
                    - اعتبار 2
                    
                    ## سيناريوهات الخطر
                    صف أسوأ الحالات المحتملة:
                    - السيناريو: ماذا يحدث؟
                    - الظروف: متى يحدث؟
                    - الوقاية: كيف تمنعه؟
                    
                    ## توصيات عملية
                    خطوات محددة لتجنب أو تخفيف المخاطر:
                    1. توصية قابلة للتنفيذ
                    2. توصية أخرى
                    
                       كن شاملاً وعملياً. ركّز على ما يهم القارئ فعلياً.
                       Do not provide a condensed list. Identify all risks comprehensively across the full page range. You must cite explicit page numbers [Page X] for every risk, scenario, and recommendation.
                       You MUST cover the FULL page range provided; do not skip pages.
                        CRITICAL LANGUAGE RULE: Respond entirely in Arabic. All text must be in Arabic. Do not mix languages.
                         PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                           FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
                        """
                   : """
                       You are an expert risk analyst. Your task is to identify and extract all risk warnings from the text.
                       
                       Structure your analysis as follows:
                      
                      ## 🚨 Critical Risks
                      Risks that could cause serious harm if ignored:
                      - **Risk**: Description
                        - Likelihood: [Low / Medium / High]
                        - Impact: [Low / Medium / Severe]
                        - Warning signs: How to detect it early
                      
                      ## ⚠️ Moderate Risks
                      Important risks requiring attention:
                      - **Risk**: Description
                        - Mitigation: Practical steps
                      
                      ## 📋 Important Considerations
                      Points worth noting but not critical risks:
                      - Consideration 1
                      - Consideration 2
                      
                      ## Risk Scenarios
                      Describe worst-case scenarios:
                      - Scenario: What happens?
                      - Conditions: When does it occur?
                      - Prevention: How to avoid it?
                      
                      ## Practical Recommendations
                      Specific steps to avoid or mitigate risks:
                      1. Actionable recommendation
                      2. Another recommendation
                      
                        Be comprehensive and practical. Focus on what truly matters to the reader.
                        Do not provide a condensed list. Identify all risks comprehensively across the full page range. You must cite explicit page numbers [Page X] for every risk, scenario, and recommendation.
                        You MUST cover the FULL page range provided; do not skip pages.
                         CRITICAL LANGUAGE RULE: Respond entirely in English. All text must be in English. Do not mix languages.
                         PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                           FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
                         """);
    }

    /**
     * Processes analysis requests with validation, prompt dispatch, and response formatting.
     * <p>
     * <b>What:</b> Validates the page range, builds the language-appropriate prompt
     * via the factory, calls the AI service, and stores the model attributes.
     * <p>
     * <b>Why:</b> Centralizes the shared analysis pipeline so reality-check, concept-map,
     * and risk-flags flows do not duplicate validation or formatting logic.
     * <p>
     * <b>Where:</b> Required for the Insights analysis tabs per the retrieval-augmented
     * extraction strategy.
     *
     * @param analysisType  the type of analysis to perform
     * @param startPage     1-based start page
     * @param endPage       1-based inclusive end page
     * @param outputLanguage target language code
     * @param model         the view model
     * @param promptFactory function that supplies the system prompt
     * @return the Thymeleaf view name
     */
private String processAnalysis(String analysisType, int startPage, int endPage,
                                    String outputLanguage, Model model, Long userId, SystemPromptFactory promptFactory) {
        LoggingUtils.enter(log, "processAnalysis", "type", analysisType, "range", startPage + "-" + endPage);
        long startTime = System.currentTimeMillis();

        // Validate page range
        if (startPage < 1) {
            LoggingUtils.validationFailure(log, "startPage", "must be >= 1", startPage);
            ModelAttributeBinder.addError(model, messageSource.getMessage("analysis.start.page.invalid", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }
        if (endPage < startPage) {
            LoggingUtils.validationFailure(log, "endPage", "must be >= startPage", endPage);
            ModelAttributeBinder.addError(model, messageSource.getMessage("analysis.end.page.invalid", null, LocaleContextHolder.getLocale()));
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
            return "index";
        }

        String bookLang = bookSession.getDetectedLanguage();
        String lang = outputLanguage != null ? outputLanguage : bookLang;

        if (!bookSession.hasBook()) {
            String errorMessage = messageSource.getMessage("analysis.no.book", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            setAnalysisError(model, analysisType, errorMessage, bookLang);
            model.addAttribute("hasBook", false);
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }

        Long bookId = bookSession.resolveBookId(userId);
        if (bookId != null) {
            try {
                bookSession.loadBookById(bookId);
            } catch (Exception e) {
                log.warn("Failed to load book from DB for analysis | bookId={} | error={}", bookId, e.getMessage());
            }
        }
        if (bookId != null && bookSession.getPageCount() == 0) {
            try {
                BookData extracted = onDemandExtractionService.extractPages(bookId, startPage, endPage);
                bookSession.setBook(extracted);
                bookSession.setCurrentBookId(bookId);
                log.info("On-demand analysis extraction complete | bookId={} | pages={}", bookId, extracted.getPageCount());
            } catch (IOException e) {
                log.error("On-demand extraction failed for analysis | bookId={} | error={}", bookId, e.getMessage());
            }
        }

        try {
            int totalPages = endPage - startPage + 1;
            int chunkSize = Math.min(MAX_CHUNK_PAGES, totalPages);
            List<String> chunkResults = new ArrayList<>();
            Map<String, Object> combinedGraphData = null;

            for (int page = startPage; page <= endPage; page += chunkSize) {
                int chunkStart = page;
                int chunkEnd = Math.min(page + chunkSize - 1, endPage);
                log.info("Analyzing chunk | type={} | pages={}-{}", analysisType, chunkStart, chunkEnd);

                String text = bookSession.extractTextFromPageRange(chunkStart, chunkEnd);
                if (ModelAttributeBinder.isEmpty(text)) {
                    log.warn("{} | empty text for pages {}-{}, skipping", analysisType, chunkStart, chunkEnd);
                    continue;
                }

                String systemPrompt = promptFactory.create(lang);
                String userContent = "Book text (pages " + chunkStart + "-" + chunkEnd + "):\n" + text;
                String answer = aiService.getAnswerFromAi(systemPrompt, userContent, lang);

                if (ModelAttributeBinder.isEmpty(answer) || answer.startsWith("AI error:")) {
                    log.warn("{} failed for pages {}-{}: AI error", analysisType, chunkStart, chunkEnd);
                    continue;
                }

                String prefix = "Pages " + chunkStart + "-" + chunkEnd + ":\n";
                chunkResults.add(prefix + answer);

                if ("concept-map".equals(analysisType) && combinedGraphData == null) {
                    String graphJson = aiService.extractJsonBlock(answer);
                    combinedGraphData = aiService.parseJsonToMap(graphJson, text);
                }
            }

            if (chunkResults.isEmpty()) {
                log.warn("{} failed: all chunks failed", analysisType);
                String errorMessage = messageSource.getMessage("analysis.error", null, LocaleContextHolder.getLocale());
                ModelAttributeBinder.addError(model, errorMessage);
                setAnalysisError(model, analysisType, errorMessage, bookLang);
                model.addAttribute("hasBook", bookSession.hasBook());
                model.addAttribute("bookLanguage", bookLang);
                return "index";
            }

            String combinedAnswer = String.join("\n\n==========\n\n", chunkResults);

            String cleanAnswer = aiService.stripJsonBlock(combinedAnswer);
            String htmlAnswer = markdownService.convertMarkdownToHtml(cleanAnswer);
            TextDirectionService.FormattedResponse formattedResponse =
                textDirectionService.createFormattedResponse(htmlAnswer, lang);

            addAnalysisResultToModel(model, analysisType, htmlAnswer, formattedResponse, bookLang, combinedGraphData);

            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, analysisType, htmlAnswer, null, null);
            }

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "processAnalysis", "duration", duration + "ms");
            log.info("{} completed | duration={}ms | chunks={}", analysisType, duration, chunkResults.size());

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "processAnalysis", e);
            log.error("{} failed | duration={}ms | error={}", analysisType, duration, e.getMessage());
            String errorMessage = messageSource.getMessage("analysis.error", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            setAnalysisError(model, analysisType, errorMessage, bookLang);
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLang);
            return "index";
        }
    }

    /**
     * Maps analysis results to model attributes based on analysis type.
     * <p>
     * <b>What:</b> Dispatches the rendered HTML answer and direction metadata to the
     * correct model attribute keys for each analysis tab.
     * <p>
     * <b>Why:</b> Centralizes view-model population so the template can render any
     * analysis result with a single response-box pattern.
     * <p>
     * <b>Where:</b> Required for the Insights analysis tabs.
     *
     * @param model           the view model
     * @param analysisType    the type of analysis performed
     * @param htmlAnswer      the rendered HTML answer
     * @param formattedResponse the direction/formatted metadata
     * @param bookLang        the book's detected language
     * @param graphData       optional graph JSON for concept-map tab
     */
    private void addAnalysisResultToModel(Model model, String analysisType, String htmlAnswer,
                                          TextDirectionService.FormattedResponse formattedResponse, String bookLang) {
        addAnalysisResultToModel(model, analysisType, htmlAnswer, formattedResponse, bookLang, null);
    }

    private void addAnalysisResultToModel(Model model, String analysisType, String htmlAnswer, 
                                          TextDirectionService.FormattedResponse formattedResponse, String bookLang,
                                          Map<String, Object> graphData) {
        switch (analysisType) {
            case "reality-check":
                model.addAttribute("realityCheck", htmlAnswer);
                model.addAttribute("realityCheckDirection", formattedResponse.getDirection());
                model.addAttribute("realityCheckLanguage", formattedResponse.getLanguage());
                model.addAttribute("realityCheckHtmlAttributes", formattedResponse.getHtmlAttributes());
                model.addAttribute("realityCheckInlineStyle", formattedResponse.getInlineStyle());
                break;
            case "concept-map":
                model.addAttribute("conceptMap", htmlAnswer);
                model.addAttribute("conceptMapDirection", formattedResponse.getDirection());
                model.addAttribute("conceptMapLanguage", formattedResponse.getLanguage());
                model.addAttribute("conceptMapHtmlAttributes", formattedResponse.getHtmlAttributes());
                model.addAttribute("conceptMapInlineStyle", formattedResponse.getInlineStyle());
                if (graphData != null) {
                    try {
                        model.addAttribute("conceptMapJson",
                            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(graphData));
                    } catch (Exception e) {
                        log.warn("Failed to serialize graph JSON", e);
                    }
                }
                break;
            case "risk-flags":
                model.addAttribute("riskFlags", htmlAnswer);
                model.addAttribute("riskFlagsDirection", formattedResponse.getDirection());
                model.addAttribute("riskFlagsLanguage", formattedResponse.getLanguage());
                model.addAttribute("riskFlagsHtmlAttributes", formattedResponse.getHtmlAttributes());
                model.addAttribute("riskFlagsInlineStyle", formattedResponse.getInlineStyle());
                break;
            default:
                log.warn("Unknown analysis type: {}", analysisType);
        }
        model.addAttribute("hasBook", bookSession.hasBook());
        model.addAttribute("bookLanguage", bookLang);
    }

    private void setAnalysisError(Model model, String analysisType, String errorMessage, String bookLang) {
        String attributeName;
        switch (analysisType) {
            case "reality-check":
                attributeName = "realityCheck";
                break;
            case "concept-map":
                attributeName = "conceptMap";
                break;
            case "risk-flags":
                attributeName = "riskFlags";
                break;
            default:
                return;
        }
        model.addAttribute(attributeName, "<p class='text-danger'>" + errorMessage + "</p>");
        model.addAttribute(attributeName + "Direction", bookLang);
        model.addAttribute(attributeName + "Language", bookLang);
        model.addAttribute(attributeName + "HtmlAttributes", "");
        model.addAttribute(attributeName + "InlineStyle", "");
    }

    /**
     * Functional interface for creating system prompts based on language.
     * Allows flexible prompt generation for different analysis types.
     */
    @FunctionalInterface
    private interface SystemPromptFactory {
        /**
         * Creates the system prompt string for the given language.
         * <p>
         * <b>What:</b> Returns the language-appropriate prompt template for the analysis type.
         * <p>
         * <b>Why:</b> Allows each analysis tab to define its own prompt while sharing
         * the validation and formatting pipeline.
         * <p>
         * <b>Where:</b> Required for the Insights analysis tabs.
         *
         * @param language the target language code
         * @return the system prompt string
         */
        String create(String language);
    }
}


