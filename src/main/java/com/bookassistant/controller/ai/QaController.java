package com.bookassistant.controller.ai;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.BookStorageService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.service.infrastructure.CacheService;
import com.bookassistant.service.infrastructure.CacheService.CacheIndex;
import com.bookassistant.service.infrastructure.CacheService.CachedImage;
import com.bookassistant.service.integration.CrossPlatformQaService;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import com.bookassistant.service.text.MarkdownService;
import com.bookassistant.service.text.TextDirectionService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Answers free-form questions against the loaded book using a chunked retrieval strategy.
 * <p>
 * <b>What:</b> Accepts a user question, retrieves relevant book text via the
 * session-scoped {@link BookSession}, and generates an AI answer.
 * <p>
 * <b>Why:</b> Large books exceed the LLM context window; the chunked QA strategy
 * splits the book into manageable pieces and synthesizes a final answer, per
 * {@code PROJECT_RULES.md} Section 3.
 * <p>
 * <b>Where:</b> Required for the QA study feature. See {@link #answerFromChunks(String, String)}
 * for the chunked algorithm.
 */
@Controller
public class QaController {

    private static final Logger log = LoggerFactory.getLogger(QaController.class);

    private static final int MAX_QUESTION_LENGTH = 10240;
    private static final int DIRECT_QA_THRESHOLD = 50_000;
    private static final int MAX_CHUNK_TOKENS = 2000;

    private final BookSession bookSession;
    private final AiService aiService;
    private final MarkdownService markdownService;
    private final TextDirectionService textDirectionService;
    private final CacheService cacheService;
    private final CrossPlatformQaService crossPlatformQaService;
    private final LinkedAccountService linkedAccountService;
    private final ExternalNoteService externalNoteService;
    private final BookOutputService bookOutputService;
    private final MessageSource messageSource;
    private final OnDemandExtractionService onDemandExtractionService;
    private final BookStorageService bookStorageService;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession        session-scoped book state
     * @param aiService          LLM interaction facade
     * @param markdownService    Markdown-to-HTML converter for displaying answers
     * @param textDirectionService RTL/LTR formatter for Arabic/English output
     * @param cacheService       figure cache for matching images to AI references
     */
    public QaController(BookSession bookSession, AiService aiService, 
                        MarkdownService markdownService, TextDirectionService textDirectionService,
                        CacheService cacheService, CrossPlatformQaService crossPlatformQaService,
                        LinkedAccountService linkedAccountService, ExternalNoteService externalNoteService,
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
        this.cacheService = cacheService;
        this.crossPlatformQaService = crossPlatformQaService;
        this.linkedAccountService = linkedAccountService;
        this.externalNoteService = externalNoteService;
        this.bookOutputService = bookOutputService;
        this.messageSource = messageSource;
        this.onDemandExtractionService = onDemandExtractionService;
        this.bookStorageService = bookStorageService;

        log.info("QaController initialized");
    }

    /**
     * Handles {@code POST /qa} and returns a rendered answer.
     * <p>
     * <b>Algorithm:</b>
     * <ol>
     *   <li>Validate question length.</li>
     *   <li>If the full book text is {@code <= DIRECT_QA_THRESHOLD} characters, send it
     *       directly to the model in one call.</li>
     *   <li>Otherwise, delegate to {@link #answerFromChunks(String, String)} which
     *       processes the book page-by-page, extracts only relevant passages, and
     *       synthesizes a final answer.</li>
     *   <li>Post-process the answer through Markdown and text-direction services.</li>
     * </ol>
     *
     * @param question        the user's question
     * @param outputLanguage  desired response language code
     * @param model           the view model
     * @return the Thymeleaf view name
     */
    @PostMapping("/qa")
    public String answerQuestion(@RequestParam String question,
                                 @RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                 @RequestParam(value = "crossPlatform", required = false, defaultValue = "false") boolean crossPlatform,
                                 HttpSession session,
                                 Model model) {
        LoggingUtils.enter(log, "answerQuestion", "questionLen", ModelAttributeBinder.safeLength(question), "crossPlatform", crossPlatform);
        long startTime = System.currentTimeMillis();

        if (ModelAttributeBinder.isEmpty(question)) {
            LoggingUtils.validationFailure(log, "question", "must not be empty", question);
            ModelAttributeBinder.addError(model, messageSource.getMessage("qa.question.required", null, LocaleContextHolder.getLocale()));
            return "index";
        }

        if (question.length() > MAX_QUESTION_LENGTH) {
            LoggingUtils.validationFailure(log, "question", "too long", question.length() + " chars");
            ModelAttributeBinder.addError(model, messageSource.getMessage("qa.question.too.long", null, LocaleContextHolder.getLocale()));
            return "index";
        }

        String bookLanguage = bookSession.getDetectedLanguage();
        String targetLanguage = outputLanguage != null ? outputLanguage : bookLanguage;

        log.info("Processing QA | language={} | questionLen={} | crossPlatform={}", targetLanguage, question.length(), crossPlatform);

        boolean crossPlatformEnabled = crossPlatform;
        int externalNotesCount = 0;
        boolean hasLinkedAccount = false;

        try {
            if (!bookSession.hasBook()) {
                log.warn("QA failed: no book loaded");
                 ModelAttributeBinder.addError(model, messageSource.getMessage("qa.book.required", null, LocaleContextHolder.getLocale()));
                model.addAttribute("hasBook", false);
                model.addAttribute("bookLanguage", bookLanguage);
                model.addAttribute("crossPlatformEnabled", crossPlatformEnabled);
                model.addAttribute("hasLinkedAccount", false);
                model.addAttribute("externalNotesCount", 0);
                return "index";
            }

            Long userId = (Long) session.getAttribute("userId");
            String answer;

            if (crossPlatformEnabled) {
                if (userId == null) {
                     ModelAttributeBinder.addError(model, messageSource.getMessage("qa.login.required", null, LocaleContextHolder.getLocale()));
                    model.addAttribute("hasBook", true);
                    model.addAttribute("bookLanguage", bookLanguage);
                    model.addAttribute("crossPlatformEnabled", false);
                    model.addAttribute("hasLinkedAccount", false);
                    model.addAttribute("externalNotesCount", 0);
                    return "index";
                }

                List<com.bookassistant.entity.LinkedAccount> linkedAccounts = linkedAccountService.findUserAccounts(userId);
                hasLinkedAccount = linkedAccounts.stream().anyMatch(a -> "notion".equals(a.getPlatform()));

                String bookText = bookSession.getBook().map(b -> b.fullText()).orElse("");
                String externalNotes = loadExternalNotesContext(userId);
                int notesWithContent = externalNotes.isBlank() ? 0 : externalNotes.split("\n").length;
                int totalNotesCount = externalNoteService.findByUserId(userId).size();
                externalNotesCount = totalNotesCount > 0 ? totalNotesCount : notesWithContent;
                log.info("Cross-platform QA | hasLinkedAccount={} | totalNotes={} | notesWithContent={} | notesLen={}", 
                    hasLinkedAccount, totalNotesCount, notesWithContent, externalNotes.length());
                if (log.isDebugEnabled() && !externalNotes.isBlank()) {
                    log.debug("External notes preview | first1000={}", externalNotes.substring(0, Math.min(1000, externalNotes.length())));
                }
                Long bookId = bookSession.resolveBookId(userId);
                answer = crossPlatformQaService.answerWithContext(question, bookText, externalNotes, targetLanguage);
            } else {
                Long bookId = bookSession.resolveBookId(userId);
                if (bookId != null) {
                    try {
                        bookSession.loadBookById(bookId);
                    } catch (Exception e) {
                        log.warn("Failed to load book from DB for QA | bookId={} | error={}", bookId, e.getMessage());
                    }
                }
                if (bookId != null && bookSession.getPageCount() == 0) {
                    try {
                        BookData extracted = onDemandExtractionService.extractPages(bookId, 1, bookSession.getPdfPageCount() == 0 ? 9999 : bookSession.getPdfPageCount());
                        bookSession.setBook(extracted);
                        bookSession.setCurrentBookId(bookId);
                        bookSession.persistExtractedText(bookId, extracted.fullText());
                        log.info("On-demand QA extraction complete | bookId={} | pages={}", bookId, extracted.getPageCount());
                    } catch (IOException e) {
                        log.error("On-demand extraction failed for QA | bookId={} | error={}", bookId, e.getMessage());
                    }
                }
                if (bookId != null) {
                    log.info("Using RAG QA | bookId={}", bookId);
                    String systemPrompt = createSystemPrompt(targetLanguage);
                    String userContent = createUserContent(question, "", targetLanguage);
                    answer = aiService.getAnswerFromAi(systemPrompt, userContent, targetLanguage, bookId, null);
                } else {
                    String bookText = bookSession.getBook().map(b -> b.fullText()).orElse("");
                    if (bookText.length() <= DIRECT_QA_THRESHOLD) {
                        log.info("Using direct QA | textLen={}", bookText.length());
                        String systemPrompt = createSystemPrompt(targetLanguage);
                        String userContent = createUserContent(question, bookText, targetLanguage);
                        answer = aiService.getAnswerFromAi(systemPrompt, userContent, targetLanguage);
                    } else {
                        log.info("Using chunked QA | textLen={} | totalPages={}", bookText.length(), bookSession.getPageCount());
                        answer = answerFromChunks(question, targetLanguage);
                    }
                }
            }

            if (ModelAttributeBinder.isEmpty(answer) || answer.startsWith("AI error:")) {
                log.warn("QA failed: AI error | {}", answer);
                String errorMessage = messageSource.getMessage("qa.error.with.answer", new Object[]{answer}, LocaleContextHolder.getLocale());
                ModelAttributeBinder.addError(model, errorMessage);
                model.addAttribute("qa", "<p class='text-danger'>" + errorMessage + "</p>");
                model.addAttribute("qaDirection", bookLanguage);
                model.addAttribute("qaLanguage", bookLanguage);
                model.addAttribute("qaHtmlAttributes", "");
                model.addAttribute("qaInlineStyle", "");
                model.addAttribute("hasBook", true);
                model.addAttribute("bookLanguage", bookLanguage);
                model.addAttribute("crossPlatformEnabled", crossPlatformEnabled);
                model.addAttribute("hasLinkedAccount", hasLinkedAccount);
                model.addAttribute("externalNotesCount", externalNotesCount);
                return "index";
            }

            String htmlAnswer = markdownService.convertMarkdownToHtml(answer);
            TextDirectionService.FormattedResponse formattedResponse = 
                textDirectionService.createFormattedResponse(htmlAnswer, targetLanguage);

            model.addAttribute("qa", htmlAnswer);
            model.addAttribute("qaDirection", formattedResponse.getDirection());
            model.addAttribute("qaLanguage", formattedResponse.getLanguage());
            model.addAttribute("qaHtmlAttributes", formattedResponse.getHtmlAttributes());
            model.addAttribute("qaInlineStyle", formattedResponse.getInlineStyle());
            model.addAttribute("hasBook", true);
            model.addAttribute("bookLanguage", bookLanguage);
            model.addAttribute("crossPlatformEnabled", crossPlatformEnabled);
            model.addAttribute("hasLinkedAccount", hasLinkedAccount);
            model.addAttribute("externalNotesCount", externalNotesCount);

            Long bookId = bookSession.resolveBookId(userId);
            if (bookId != null) {
                bookOutputService.saveOutput(userId, bookId, "qa", htmlAnswer, null, null);
            }

            addCacheToModel(model, answer);

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "answerQuestion", "duration", duration + "ms");
            log.info("QA completed | duration={}ms | answerLen={}", duration, htmlAnswer.length());

            return "index";

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "answerQuestion", e);
            log.error("QA failed | duration={}ms | error={}", duration, e.getMessage());
            String errorMessage = messageSource.getMessage("qa.processing.error", null, LocaleContextHolder.getLocale());
            ModelAttributeBinder.addError(model, errorMessage);
            model.addAttribute("qa", "<p class='text-danger'>" + errorMessage + "</p>");
            model.addAttribute("qaDirection", bookLanguage);
            model.addAttribute("qaLanguage", bookLanguage);
            model.addAttribute("qaHtmlAttributes", "");
            model.addAttribute("qaInlineStyle", "");
            model.addAttribute("hasBook", bookSession.hasBook());
            model.addAttribute("bookLanguage", bookLanguage);
            model.addAttribute("crossPlatformEnabled", crossPlatformEnabled);
            model.addAttribute("hasLinkedAccount", hasLinkedAccount);
            model.addAttribute("externalNotesCount", externalNotesCount);
            return "index";
        }
    }

    /**
     * Implements the chunked QA retrieval strategy.
     * <p>
     * <b>Algorithm:</b>
     * <ol>
     *   <li>Determine chunk size dynamically: {@code Math.max(30, Math.min(60, totalPages / 20))}.</li>
     *   <li>For each chunk, extract the text and ask the LLM to extract only passages
     *       directly relevant to the question (no summarization or interpretation).</li>
     *   <li>Collect all non-error extracts.</li>
     *   <li>If only one extract survives, use it directly.</li>
     *   <li>Otherwise, concatenate extracts with horizontal rules and ask the LLM a
     *       final synthesis prompt.</li>
     *   <li>If synthesis fails, fall back to the raw concatenated extracts.</li>
     * </ol>
     * <p>
     * <b>Where:</b> Required for handling books larger than the LLM context window;
     * implements the chunked retrieval-augmented generation (RAG) pattern described
     * in {@code PROJECT_RULES.md} Section 3.
     *
     * @param question the user's original question
     * @param language the target response language code
     * @return the synthesized AI answer
     */
    private String answerFromChunks(String question, String language) {
        int totalPages = bookSession.getPageCount();
        int chunkSize = Math.max(30, Math.min(60, totalPages / 20));
        log.info("Chunked QA | chunkSize={} | totalChunks={}", chunkSize, (totalPages + chunkSize - 1) / chunkSize);

        String extractPrompt = createExtractPrompt(language);
        List<String> extracts = new ArrayList<>();

        for (int i = 1; i <= totalPages; i += chunkSize) {
            int startPage = i;
            int endPage = Math.min(i + chunkSize - 1, totalPages);
            log.debug("Extracting relevance | pages={}-{}", startPage, endPage);

            String textChunk = bookSession.extractTextFromPageRange(startPage, endPage);
            if (ModelAttributeBinder.isEmpty(textChunk)) {
                log.warn("Empty chunk {}-{}, skipping", startPage, endPage);
                continue;
            }

            int originalChunkLen = textChunk.length();
            textChunk = aiService.truncateToTokens(textChunk, MAX_CHUNK_TOKENS);
            if (textChunk.length() < originalChunkLen) {
                log.debug("Truncated chunk {}-{} from {} to {} chars", startPage, endPage, originalChunkLen, textChunk.length());
            }

            String userContent = createExtractUserContent(question, textChunk, language, startPage, endPage);
            String extract = aiService.getAnswerFromAi(extractPrompt, userContent, language);

            if (!ModelAttributeBinder.isEmpty(extract) && !extract.startsWith("AI error:")) {
                extracts.add(extract);
            } else {
                String errorDetail = extract != null ? extract : "null response";
                log.warn("QA EXTRACT SKIP | reason=AI_ERROR | pages={}-{} | error={}", startPage, endPage, errorDetail);
            }
        }

        if (extracts.isEmpty()) {
            log.warn("All chunk extracts failed");
            return "AI error: No content could be extracted from the book to answer this question.";
        }

        if (extracts.size() == 1) {
            log.info("Single extract, using directly | len={}", extracts.get(0).length());
            return extracts.get(0);
        }

        String combined = String.join("\n\n---\n\n", extracts);
        log.info("Combined extracts | count={} | totalLen={}", extracts.size(), combined.length());

        String systemPrompt = createSystemPrompt(language);
        String finalUserContent = createFinalAnswerUserContent(question, combined, language);
        String finalAnswer = aiService.getAnswerFromAi(systemPrompt, finalUserContent, language);

        if (ModelAttributeBinder.isEmpty(finalAnswer) || finalAnswer.startsWith("AI error:")) {
            log.warn("Final answer synthesis failed, falling back to combined extracts");
            return combined;
        }

        return finalAnswer;
    }

    private String createExtractPrompt(String language) {
        String cacheContext = buildCacheContext();
        if ("en".equals(language)) {
            return """
                ABSOLUTE RULES:
                1. NO COMMENTARY: Zero conversational text. No "It seems...", "Here's...", "Based on...".
                2. NO PARAPHRASING: Every word must match the source text exactly.
                3. MATH PRESERVATION: Preserve ALL math notation exactly:
                   - \\[...\\] (display math)
                   - $$...$$ (display math)
                   - \\(...\\) (inline math)
                   - <math>...</math> (XML tags)
                   Do NOT convert, simplify, or remove any mathematical notation.
                4. NO HTML IN MATH: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br> inside math.
                   Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
                 5. PAGE RANGES: Only cite pages within the provided range.
                 6. LANGUAGE: Respond entirely in English. Do not mix languages.

                You are a verbatim passage extractor. You do NOT think, analyze, or summarize.
                You ONLY copy exact passages from the provided text.

                """ + cacheContext + """

                TASK: Copy verbatim the passages that directly answer the user's question.
                - Copy passages EXACTLY as written in the text
                - Only include content DIRECTLY relevant to the question
                - If nothing is relevant, respond with "No relevant content found."
                - Preserve the author's exact wording
                - When referencing content from a specific page, include the page number
                - Extract detailed, relevant passages with full context.
                """;
        }
        return """
            قواعد مطلقة:
            1. لا تعليقات: لا نص محادثة. ممنوع "يبدو أن...", "هاك...", "بناءً على...".
            2. لا إعادة صياغة: كل كلمة يجب أن تطابق النص الأصلي تماماً.
            3. حفظ الرياضيات: احتفظ بجميع علامات الرياضيات تماماً:
               - \\[...\\] (رياضيات معروضة)
               - $$...$$ (رياضيات معروضة)
               - \\(...\\) (رياضيات مضمنة)
               - <math>...</math> (وسوم XML)
               لا تحول أو تبسط أو تحذف أي علامات رياضيات.
            4. لا HTML في الرياضيات: لا تستخدم وسوم HTML مثل <sup>, <sub>, <em>, <strong>, <br> داخل الرياضيات.
               استخدم LaTeX فقط: الأسس ك ^{}, الفهارس ك _{}, غامق ك \\mathbf{}, إلخ.
             5. نطاقات الصفحات: استشهد فقط بالصفحات داخل النطاق المقدم.
             6. اللغة: أجب باللغة العربية فقط. لا تخلط بين اللغات.

            أنت مستخرج محتوى كتب دقيق. لا تفكر أو تحلل أو تلخص.
            أنت تنسخ المقاطع فقط من النص المقدم.

            """ + cacheContext + """

            المهمة: انسخ المقاطع التي تجيب مباشرة على سؤال المستخدم.
            - انسخ المقاطع تماماً كما هي مكتوبة في النص
            - تضمين المحتوى المرتبط مباشرة بالسؤال فقط
            - إذا لم يكن هناك محتوى ذو صلة، رد بـ "لم يتم العثور على محتوى ذو صلة"
            - حافظ على صياغة المؤلف الأصلية
            - عند الإشارة إلى محتوى من صفحة معينة، اذكر رقم الصفحة
            - استخرج مقاطع مفصلة وذات صلة مع السياق الكامل.
            """;
    }

    private String buildCacheContext() {
        String bookName = bookSession.getBook().map(b -> b.fileName()).orElse("");
        if (ModelAttributeBinder.isEmpty(bookName)) return "";
        if (bookName.contains(".")) {
            bookName = bookName.substring(0, bookName.lastIndexOf('.'));
        }
        CacheIndex index = cacheService.loadIndex(bookName);
        if (index == null || index.isEmpty()) return "";
        String ctx = cacheService.buildImageContext(index);
        if (ModelAttributeBinder.isEmpty(ctx)) return "";
        return "\n\n" + ctx;
    }

    private String createExtractUserContent(String question, String textChunk, String language, int startPage, int endPage) {
        String prefix = "Book text (pages " + startPage + "-" + endPage + "):\n";
        if ("en".equals(language)) {
            return prefix + "Question: " + question + "\n\nText to extract from:\n" + textChunk;
        }
        return prefix + "السؤال: " + question + "\n\nالنص المطلوب الاستخراج منه:\n" + textChunk;
    }

    private String createFinalAnswerUserContent(String question, String extracts, String language) {
        if ("en".equals(language)) {
            return "Book text:\n" + "Question: " + question + "\n\nRelevant extracts from the book:\n" + extracts;
        }
        return "Book text:\n" + "السؤال: " + question + "\n\nالمقتطفات ذات الصلة من الكتاب:\n" + extracts;
    }

    private String createSystemPrompt(String language) {
        if ("en".equals(language)) {
            return """
                You are an expert book analyst. Answer questions based ONLY on the book content provided.
                
                CRITICAL RULE: When linked account notes are provided, you MUST structure your answer in exactly these three parts:
                
                1. What is in the book that is CORE CONTENT relative to the notes (expands on, validates, or deepens the notes).
                2. What constitutes KEY INSIGHTS from the notes (same concepts, examples, or claims found in both).
                3. What is NOT covered in the notes (new information the user has not yet recorded).
                
                Guidelines:
                - **Accuracy**: Cite specific passages or concepts from the text
                - **Completeness**: Provide thorough answers, not just yes/no
                - **Context**: Explain relevant background from the book
                - **Honesty**: If the book doesn't address something, say so clearly
                - **Depth**: Go beyond surface-level; explain why and how
                - **Notes Integration**: Explicitly reference the user's linked notes when comparing
                
                Structure your answer:
                1. Direct answer to the question
                2. Three-part comparison against linked notes (if notes provided)
                3. Supporting evidence from the text
                4. Related concepts or implications
                5. Practical application (if relevant)
                
                Do not provide condensed answers. Provide thorough, detailed responses. Cite page numbers [Page X] for all claims and evidence.
                Write clearly and professionally.
                
                CRITICAL LANGUAGE RULE: Respond entirely in English. All text must be in English. Do not mix languages.
                PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
                """;
        }
        return """
            أنت خبير محلل كتب. أجب عن الأسئلة بناءً على محتوى الكتاب المقدم فقط.
            
            قاعدة حرجة: عند توفر ملاحظات الحسابات المرتبطة، يجب عليك هيكلة إجابتك في ثلاثة أجزاء بالضبط:
            
            1. ما هو في الكتاب المحتوى الأساسي للملاحظات (يوسع، يتحقق، أو يعمق الملاحظات).
            2. ما هو الرؤى الرئيسية من الملاحظات (مفاهيم، أمثلة، أو ادعاءات موجودة في كلاهما).
            3. ما هو غير مغطى في الملاحظات (معلومات جديدة لم يسجلها المستخدم بعد).
            
            إرشادات:
            - **الدقة**: استشهد بعبارات أو مفاهيم محددة من النص
            - **الشمولية**: قدّم إجابات وافية، وليس فقط نعم/لا
            - **السياق**: اشرح الخلفية ذات الصلة من الكتاب
            - **الصدق**: إذا كان الكتاب لا يتناول شيئاً، فقل ذلك بوضوح
            - **العمق**: تجاوز السطحي؛ اشرح لماذا وكيف
            - **تكامل الملاحظات**: أشرِ صراحة إلى ملاحظات المستخدم عند المقارنة
            
            نظّم إجابتك:
            1. إجابة مباشرة على السؤال
            2. مقارنة من ثلاثة أجزاء ضد الملاحظات المرتبطة (إذا توفرت)
            3. أدلة داعمة من النص
            4. مفاهيم أو آثار ذات صلة
            5. تطبيق عملي (إذا كان ذا صلة)
            
            لا تقدم إجابات مختصرة. قدم إجابات شاملة ومفصلة. استشهد بأرقام الصفحات [Page X] لكل الادعاءات والأدلة.
            اكتب بوضوح ومهنية.
            
            قاعدة لغة حرجة: أجب باللغة العربية فقط. جميع النصوص يجب أن تكون بالعربية. لا تخلط بين اللغات.
            PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
            FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
            """;
    }

    private String createUserContent(String question, String text, String language) {
        if ("en".equals(language)) {
            return "Book text:\n" + "Question: " + question + "\n\nText:\n" + text;
        }
        return "Book text:\n" + "السؤال: " + question + "\n\nالنص:\n" + text;
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

    private String loadExternalNotesContext(Long userId) {
        List<com.bookassistant.entity.ExternalNote> notes = externalNoteService.findByUserId(userId);
        if (notes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int withContent = 0;
        int emptyContent = 0;
        for (com.bookassistant.entity.ExternalNote note : notes) {
            boolean hasContent = !com.bookassistant.util.StringUtils.isEmpty(note.getContent());
            if (hasContent) withContent++; else emptyContent++;
            sb.append("- ").append(note.getTitle()).append(": ");
            if (hasContent) {
                sb.append(note.getContent());
            } else {
                sb.append("[No content synced yet]");
            }
            sb.append("\n");
        }
        log.info("Loaded external notes | userId={} | total={} | withContent={} | emptyContent={}", 
            userId, notes.size(), withContent, emptyContent);
        return sb.toString();
    }
}




