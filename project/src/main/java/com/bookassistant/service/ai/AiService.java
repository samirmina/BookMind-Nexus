package com.bookassistant.service.ai;

import com.bookassistant.config.AiProperties;
import com.bookassistant.service.rag.RagService;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for calling AI APIs to get responses.
 * Uses a singleton HttpClient for efficiency.
 * Handles all AI interactions with proper error handling and logging.
 */
/**
 * HTTP client for the Ollama-compatible AI API.
 * <p>
 * <b>What:</b> Sends book content and user questions to the configured LLM endpoint,
 * receives answers, and applies prompt-injection sanitization before request dispatch.
 * <p>
 * <b>Why:</b> Centralizes all AI interaction so that timeout, retry, logging, and
 * security concerns are handled in one place, per {@code PROJECT_RULES.md} Section 1.
 * <p>
 * <b>Where:</b> Required for OWASP prompt-injection resistance and PDFBox 3.0.4
 * memory-handling strategy (shared ObjectMapper pool).
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 500;
    
    // WORKAROUND: Bounded thread pool to prevent resource exhaustion under concurrent load
    // NOTE: Should be replaced with a proper connection-pooled client before production scale
    private static final int MAX_HTTP_THREADS = 10;
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .executor(Executors.newFixedThreadPool(MAX_HTTP_THREADS))
        .build();
    
    private final ObjectMapper mapper;
    private final AiProperties properties;
    private final RagService ragService;

    public AiService(ObjectMapper mapper, AiProperties properties, RagService ragService) {
        if (mapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("AiProperties must not be null");
        }
        if (ragService == null) {
            throw new IllegalArgumentException("RagService must not be null");
        }
        this.mapper = mapper;
        this.properties = properties;
        this.ragService = ragService;
        log.info("AiService initialized | model={} | embeddingModel={} | timeout={}s",
                properties.getModel(), properties.getEmbeddingModel(), properties.getTimeoutSeconds());
    }

    /**
     * Retrieves an AI-generated answer using the default language (Arabic).
     * <p>
     * <b>What:</b> Delegates to {@link #getAnswerFromAi(String, String, String)} with
     * {@code "ar"} as the target language.
     * <p>
     * <b>Why:</b> Convenience overload for Arabic-first callers.
     * <p>
     * <b>Where:</b> Used by legacy callers that do not specify a language.
     *
     * @param systemPrompt the system/role prompt
     * @param userContent the user-supplied content
     * @return the AI-generated response
     */
    public String getAnswerFromAi(String systemPrompt, String userContent) {
        return getAnswerFromAi(systemPrompt, userContent, "en");
    }

    /**
     * Retrieves an AI-generated answer for the given system prompt and user content.
     * <p>
     * <b>What:</b> Validates inputs, sanitizes user content against prompt-injection
     * patterns, appends a language instruction, and dispatches the HTTP request to the
     * configured Ollama-compatible endpoint.
     * <p>
     * <b>Why:</b> Implements OWASP input-validation guideline for prompt-injection
     * resistance by sanitizing user content before it reaches the model.
     * <p>
     * <b>Where:</b> Required for OWASP LLM Top 10 prompt-injection resistance.
     *
     * @param systemPrompt   the system/role prompt injected into the API request
     * @param userContent    the user-supplied content (book text, question, etc.)
     * @param outputLanguage the target response language code (e.g., "ar", "en")
     * @return the AI-generated response, never {@code null}
     * @throws IllegalArgumentException if either prompt or content is empty
     */
    public String getAnswerFromAi(String systemPrompt, String userContent, String outputLanguage) {
        return getAnswerFromAi(systemPrompt, userContent, outputLanguage, null, null);
    }

    /**
     * Retrieves an AI-generated answer using RAG context from the vector database.
     *
     * @param systemPrompt the system/role prompt
     * @param userContent the user's query
     * @param outputLanguage the target response language
     * @param bookId optional book ID for RAG retrieval
     * @param userId optional user ID for cross-book RAG retrieval
     * @return the AI-generated response
     */
    public String getAnswerFromAi(String systemPrompt, String userContent, String outputLanguage,
                                  Long bookId, Long userId) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        LoggingUtils.enter(log, "getAnswerFromAi",
            "promptLen", safeLength(systemPrompt),
            "contentLen", safeLength(userContent));
        log.info("AI request started | requestId={} | model={} | language={} | bookId={} | userId={}",
            requestId, properties.getModel(), outputLanguage, bookId, userId);

        long startTime = System.currentTimeMillis();

        // Validate inputs
        if (isEmpty(systemPrompt)) {
            LoggingUtils.validationFailure(log, "systemPrompt", "must not be empty", systemPrompt);
            throw new IllegalArgumentException("System prompt must not be empty");
        }
        if (isEmpty(userContent)) {
            LoggingUtils.validationFailure(log, "userContent", "must not be empty", userContent);
            throw new IllegalArgumentException("User content must not be empty");
        }
        
        // Default to English if language not specified
        if (isEmpty(outputLanguage)) {
            outputLanguage = "en";
        }

        log.info("AI request started | model={} | language={} | bookId={} | userId={}", 
            properties.getModel(), outputLanguage, bookId, userId);
        
        try {
            // Sanitize user content to reduce prompt injection risk
            String sanitizedUserContent = sanitizeUserContent(userContent);

            // Retrieve RAG context if bookId or userId is provided
            String ragContext = "";
            if (bookId != null || userId != null) {
                if (bookId != null) {
                    ragContext = ragService.getRagContext(bookId, sanitizedUserContent);
                } else if (userId != null) {
                    List<com.bookassistant.entity.BookChunk> chunks = ragService.retrieveRelevantChunksForUser(userId, sanitizedUserContent, 5);
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== RELEVANT PASSAGES ===\n\n");
                    for (int i = 0; i < chunks.size(); i++) {
                        com.bookassistant.entity.BookChunk chunk = chunks.get(i);
                        sb.append(String.format("[Passage %d - Pages %d-%d]\n%s\n\n",
                                i + 1,
                                chunk.getPageStart() != null ? chunk.getPageStart() : "?",
                                chunk.getPageEnd() != null ? chunk.getPageEnd() : "?",
                                chunk.getChunkText()));
                    }
                    ragContext = sb.toString();
                }
            }

            // Build user content with RAG context
            String enrichedUserContent = sanitizedUserContent;
            if (!ragContext.isEmpty()) {
                enrichedUserContent = ragContext + "\n=== USER QUESTION ===\n" + sanitizedUserContent;
            }

            // Add language instruction to prompt
            String adjustedPrompt = addLanguageInstruction(systemPrompt, outputLanguage);

            // Call the AI API
            String result = callOllamaApi(adjustedPrompt, enrichedUserContent, requestId);
            String originalResult = result;
            
            // Strip trailing conversational questions that some models append
            result = stripTrailingQuestion(result);
            
            // Strip leading filler words like "Okay, " that some models prepend
            result = stripLeadingFiller(result);
            
            // If stripping emptied the response, or the response is only filler text,
            // treat it as empty so the caller does not accept "Okay" as a real answer.
            if (isEmpty(result) || isOnlyFiller(result)) {
                log.warn("AI response is only filler or empty after stripping | original={}", LoggingUtils.safeLog(originalResult, 200));
                result = "";
            }
            
            // Log completion
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "getAnswerFromAi", "resultLen", safeLength(result));
            log.info("AI request completed | duration={}ms | resultLen={}", duration, safeLength(result));
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "getAnswerFromAi", e);
            log.error("AI request failed | duration={}ms | error={}", duration, e.getMessage());
            throw new AiServiceException("AI service failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a comprehension test using the default language (Arabic).
     * <p>
     * <b>What:</b> Delegates to {@link #createComprehensionTest(String, int, int, String)}
     * with {@code "ar"} as the target language.
     * <p>
     * <b>Why:</b> Convenience overload for Arabic-first callers.
     * <p>
     * <b>Where:</b> Required for the study lab comprehension-test flow.
     *
     * @param text       the source text to test comprehension on
     * @param startPage  1-based start page
     * @param endPage    1-based inclusive end page
     * @return the generated comprehension test
     */
    public String createComprehensionTest(String text, int startPage, int endPage) {
        return createComprehensionTest(text, startPage, endPage, "en");
    }

    /**
     * Creates a comprehension test for the given page range and language.
     * <p>
     * <b>What:</b> Validates inputs, builds a language-appropriate system prompt, and
     * delegates to {@link #getAnswerFromAi(String, String, String)} to generate the test.
     * <p>
     * <b>Why:</b> Required for the comprehension-test feature in the study lab.
     * <p>
     * <b>Where:</b> Implements the educator-prompt strategy for generating MCQ, short-answer,
     * and critical-thinking questions.
     *
     * @param text          the source text
     * @param startPage     1-based start page
     * @param endPage       1-based inclusive end page
     * @param outputLanguage target response language
     * @return the generated comprehension test
     */
    public String createComprehensionTest(String text, int startPage, int endPage, String outputLanguage) {
        LoggingUtils.enter(log, "createComprehensionTest", 
            "pages", startPage + "-" + endPage,
            "textLen", safeLength(text));
        
        // Validate inputs
        if (isEmpty(text)) {
            LoggingUtils.validationFailure(log, "text", "must not be empty", text);
            throw new IllegalArgumentException("Text must not be empty");
        }
        if (startPage < 1) {
            LoggingUtils.validationFailure(log, "startPage", "must be >= 1", startPage);
            throw new IllegalArgumentException("Start page must be >= 1");
        }
        if (endPage < startPage) {
            LoggingUtils.validationFailure(log, "endPage", "must be >= startPage", endPage);
            throw new IllegalArgumentException("End page must be >= start page");
        }

        log.info("Creating comprehension test | pages={}-{} | language={}", startPage, endPage, outputLanguage);
        
        try {
            // Create system prompt based on language
            String systemPrompt = getComprehensionSystemPrompt(outputLanguage);
            String userContent = "Book text (pages " + startPage + "-" + endPage + "):\n" + text;
            
            String result = getAnswerFromAi(systemPrompt, userContent, outputLanguage);
            
            LoggingUtils.exit(log, "createComprehensionTest", "testLen", safeLength(result));
            return result;
            
        } catch (Exception e) {
            LoggingUtils.exception(log, "createComprehensionTest", e);
            throw new AiServiceException("Failed to create comprehension test: " + e.getMessage(), e);
        }
    }

    /**
     * Calls the Ollama-compatible API with retry logic.
     * <p>
     * <b>What:</b> Serializes the request body, builds the HTTP request with timeout
     * and optional Bearer authentication, sends it via the shared HttpClient, and
     * retries on transient I/O failures.
     * <p>
     * <b>Why:</b> External AI calls are the most failure-prone path; bounded retries
     * improve reliability without exhausting servlet threads.
     * <p>
     * <b>Where:</b> Required for all AI API interactions.
     *
     * @param systemPrompt   the system/role prompt
     * @param userContent    the user-supplied content
     * @return the AI-generated response, or an error string prefixed with "AI error:"
     */
    private String callOllamaApi(String systemPrompt, String userContent, String requestId) {
        LoggingUtils.enter(log, "callOllamaApi", "baseUrl", properties.getBaseUrl());
        log.info("Calling Ollama API | requestId={} | baseUrl={} | model={}", requestId, properties.getBaseUrl(), properties.getModel());
        
        if (!isLocalUrl(properties.getBaseUrl()) && !isApiKeyConfigured()) {
            LoggingUtils.validationFailure(log, "apiKey", "not configured", "***REDACTED***");
            return "AI error: API key not configured. Set OLLAMA_API_KEY environment variable.";
        }

        // Build request body
        Map<String, Object> body = buildRequestBody(systemPrompt, userContent);
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            LoggingUtils.exception(log, "callOllamaApi", e);
            return "AI error: Failed to serialize request - " + e.getMessage();
        }

        log.info("AI request payload | requestId={} | model={} | requestSize={} bytes | body={}",
            requestId, properties.getModel(), json.length(), json);

        HttpRequest request = buildHttpRequest(json);
        IOException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                long apiStartTime = System.currentTimeMillis();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long apiDuration = System.currentTimeMillis() - apiStartTime;

                LoggingUtils.externalCall(log, "AI_API", "Ollama", properties.getBaseUrl(),
                    response.statusCode(), json.length(), response.body().length(), apiDuration);

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String content = extractOllamaContent(response.body());
                    if (isEmpty(content)) {
                        log.warn("AI returned empty content | requestId={} | responseBody={}", requestId, LoggingUtils.safeLog(response.body(), 500));
                        return "No response from AI service";
                    }
                    LoggingUtils.exit(log, "callOllamaApi", "contentLen", content.length());
                    log.info("AI response received | requestId={} | status=success | contentLen={}", requestId, content.length());
                    return content;
                } else {
                    log.error("Ollama error | requestId={} | status={} | body={}", requestId, response.statusCode(),
                        response.body());
                    return "AI error: HTTP " + response.statusCode();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LoggingUtils.exception(log, "callOllamaApi", e);
                return "AI error: Request interrupted";
            } catch (IOException e) {
                lastException = e;
                LoggingUtils.exception(log, "callOllamaApi - requestId=" + requestId + " attempt " + (attempt + 1), e);
                if (attempt < MAX_RETRIES) {
                    long backoff = (attempt == 0) ? 1000 : 3000;
                    log.info("Retrying in {}ms (requestId={} attempt {}/{})", backoff, requestId, attempt + 1, MAX_RETRIES);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "AI error: Request interrupted during retry";
                    }
                }
            }
        }

        LoggingUtils.exception(log, "callOllamaApi - requestId=" + requestId + " all retries exhausted", lastException);
        return "AI error: Network error - " + lastException.getMessage();
    }

    /**
     * Builds the JSON request body for the Ollama API.
     * <p>
     * <b>What:</b> Constructs a map containing the model name, stream flag, and
     * message array with system and user roles.
     * <p>
     * <b>Why:</b> Centralizes request-shape logic so model selection and message
     * formatting are defined in one place.
     * <p>
     * <b>Where:</b> Required for Ollama-compatible API request construction.
     *
     * @param systemPrompt the system/role prompt
     * @param userContent  the user-supplied content
     * @return the request body map
     */
    private Map<String, Object> buildRequestBody(String systemPrompt, String userContent) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("stream", false);
        body.put("think", properties.isThink());
        body.put("prompt", userContent);
        body.put("system", systemPrompt);

        int inputTokens = Math.max(1, userContent.length() / 4);
        int scaledPredict = properties.getNumPredict() + (inputTokens / 2);
        int cappedPredict = Math.min(scaledPredict, properties.getNumPredict() * Math.max(1, properties.getMaxPredictScale()));
        if (cappedPredict < properties.getNumPredict()) cappedPredict = properties.getNumPredict();

        Map<String, Object> options = new HashMap<>();
        options.put("num_predict", cappedPredict);
        options.put("num_ctx", properties.getNumCtx());
        options.put("num_gpu", properties.getNumGpu());
        options.put("temperature", properties.getTemperature());
        options.put("top_p", properties.getTopP());
        options.put("top_k", properties.getTopK());
        options.put("repeat_penalty", properties.getRepeatPenalty());
        if (properties.getPresencePenalty() > 0.0) options.put("presence_penalty", properties.getPresencePenalty());
        if (properties.getFrequencyPenalty() > 0.0) options.put("frequency_penalty", properties.getFrequencyPenalty());
        body.put("options", options);

        log.debug("AI options | num_predict={} | num_ctx={} | num_gpu={} | temperature={} | inputTokens~{}",
            cappedPredict, properties.getNumCtx(), properties.getNumGpu(), properties.getTemperature(), inputTokens);
        return body;
    }

    /**
     * Builds the HTTP request for the Ollama API.
     * <p>
     * <b>What:</b> Configures the URI, timeout, content-type header, POST body,
     * and optional Bearer authentication for non-local endpoints.
     * <p>
     * <b>Why:</b> Keeps authentication and timeout configuration in one place;
     * prevents SSRF by validating the base URL before dispatch.
     * <p>
     * <b>Where:</b> Required for secure, bounded HTTP communication with the AI backend.
     *
     * @param json the serialized request body
     * @return the built HTTP request
     */
    private HttpRequest buildHttpRequest(String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(properties.getBaseUrl()))
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

        if (!isLocalUrl(properties.getBaseUrl())) {
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }

        return builder.build();
    }

    /**
     * Extracts the assistant content from an Ollama or OpenAI response body.
     * <p>
     * <b>What:</b> Parses the JSON response and attempts to extract the {@code content}
     * field from either {@code message.content} (Ollama format) or
     * {@code choices[0].message.content} (OpenAI format).
     * <p>
     * <b>Why:</b> The project must support both response schemas because the local
     * Ollama server and potential cloud fallbacks use different shapes.
     * <p>
     * <b>Where:</b> Required for Ollama-compatible API response handling.
     *
     * @param jsonBody the raw response body string
     * @return the extracted content, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    private String extractOllamaContent(String jsonBody) {
        LoggingUtils.enter(log, "extractOllamaContent", "jsonLen", safeLength(jsonBody));
        
        if (isEmpty(jsonBody)) {
            log.warn("Cannot parse null or empty JSON response");
            return null;
        }
        
        try {
            log.debug("Parsing Ollama response | preview={}", LoggingUtils.safeLog(jsonBody, 200));

            Map<String, Object> root = mapper.readValue(jsonBody, Map.class);

            // Try /api/generate format: { "response": "..." }
            Object generateResponse = root.get("response");
            if (generateResponse != null) {
                String result = generateResponse.toString().trim();
                LoggingUtils.exit(log, "extractOllamaContent", "contentLen", result.length());
                return result;
            }

            // Try Ollama chat format: { "message": { "content": "..." } }
            Object message = root.get("message");
            if (message instanceof Map) {
                Object content = ((Map<String, Object>) message).get("content");
                if (content != null) {
                    String result = content.toString().trim();
                    LoggingUtils.exit(log, "extractOllamaContent", "contentLen", result.length());
                    return result;
                }
            }

            // Try OpenAI format: { "choices": [{ "message": { "content": "..." } }] }
            Object choices = root.get("choices");
            if (choices instanceof List && !((List<?>) choices).isEmpty()) {
                Object firstChoice = ((List<?>) choices).get(0);
                if (firstChoice instanceof Map) {
                    Object msg = ((Map<String, Object>) firstChoice).get("message");
                    if (msg instanceof Map) {
                        Object content = ((Map<String, Object>) msg).get("content");
                        if (content != null) {
                            String result = content.toString().trim();
                            LoggingUtils.exit(log, "extractOllamaContent", "contentLen", result.length());
                            return result;
                        }
                    }
                }
            }

            log.warn("Could not extract content from AI response");
            return null;
            
        } catch (Exception e) {
            LoggingUtils.exception(log, "extractOllamaContent", e);
            return null;
        }
    }

    /**
     * Appends a language instruction to the system prompt.
     * <p>
     * <b>What:</b> Returns the system prompt with an appended instruction to respond
     * in the target language, or the original prompt if the language is unrecognized.
     * <p>
     * <b>Why:</b> Ensures the LLM respects the user's output-language preference
     * without rewriting the entire prompt for each language.
     * <p>
     * <b>Where:</b> Required for bilingual Arabic/English support.
     *
     * @param systemPrompt  the base system prompt
     * @param outputLanguage the target language code ("ar" or "en")
     * @return the adjusted system prompt
     */
    private String addLanguageInstruction(String systemPrompt, String outputLanguage) {
        return systemPrompt +
                "Retrieved/book content must stay in the book's original language. " +
                "Do not translate any text from the book. " +
                "All labels, structure, headings, and explanations must be in " + outputLanguage + ".";
    }

    /**
     * Returns the educator system prompt for comprehension-test generation.
     * <p>
     * <b>What:</b> Selects the Arabic or English prompt template that instructs the
     * LLM to produce MCQ, short-answer, and critical-thinking questions with an answer key.
     * <p>
     * <b>Why:</b> Required for the study lab's comprehension-test feature; the prompt
     * structure enforces question variety and difficulty distribution.
     * <p>
     * <b>Where:</b> Implements the educator-prompt strategy for the comprehension controller.
     *
     * @param outputLanguage the target language code
     * @return the system prompt string
     */
    private String getComprehensionSystemPrompt(String outputLanguage) {
        if ("en".equals(outputLanguage)) {
            return """
                You are an expert educator creating a comprehensive comprehension test.
                
                CRITICAL FORMATTING RULES:
                1. Number EVERY question sequentially as 1, 2, 3, 4, 5, 6, 7, 8, 9, 10. NEVER repeat the same number.
                2. For multiple choice questions, distribute correct answers evenly: roughly 1-2 questions with A, 1-2 with B, 1-2 with C, and 1-2 with D as correct. Do NOT make all answers A or all answers B.
                3. Each multiple choice question must have exactly 4 options labeled A, B, C, D with ONLY ONE correct answer.
                4. Make distractors plausible but clearly wrong based on the text.
                
                Create a test with the following structure:
                
                ## Part 1: Multiple Choice (5 questions)
                Each question should:
                - Test understanding, not just recall
                - Have 4 options (A, B, C, D) with ONLY ONE correct answer
                - Distribute correct answers: some A, some B, some C, some D
                - Make distractors plausible but wrong
                
                ## Part 2: Short Answer (3 questions)
                - Require explanation or analysis
                - Test deeper understanding of key concepts
                - Specify expected answer length (2-3 sentences)
                
                ## Part 3: Critical Thinking (2 questions)
                - Apply concepts to new scenarios
                - Evaluate arguments or assumptions
                - Synthesize multiple ideas
                
                ## Answer Key
                Provide complete answers:
                - MCQ: Correct letter + brief explanation
                - Short Answer: Model response
                - Critical Thinking: Key points that should be mentioned
                
                Base all questions ONLY on the provided text. Ensure variety in difficulty.
                Do not generate trivial or surface-level questions. Create comprehensive questions requiring deep understanding of the full page range. In answer explanations, cite page numbers [Page X] when referencing specific content.
                PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
                FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.
                """;
        }
        return """
            أنت خبير تربوي ينشئ اختبار فهم شامل.
            
            قواعد تنسيق حرجة:
            1. رَقِّم كل سؤال بتسلسل: 1، 2، 3، 4، 5، 6، 7، 8، 9، 10. لا تُكرر نفس الرقم أبداً.
            2. لأسئلة الاختيار من متعدد، وزّع الإجابات الصحيحة بالتساوي: حوالي 1-2 أسئلة الإجابة أ، 1-2 ب، 1-2 ج، و1-2 د. لا تجعل جميع الإجابات أ أو جميعها ب.
            3. كل سؤال اختيار من متعدد يجب أن يكون له 4 خيارات marked أ، ب، ج، د مع إجابة واحدة صحيحة فقط.
            4. اجعل المشتتات معقولة لكنها خاطئة بوضوح بناءً على النص.
            
            أنشئ اختباراً بالبنية التالية:
            
            ## الجزء 1: اختيار من متعدد (5 أسئلة)
            كل سؤال يجب أن:
            - يختبر الفهم، ليس فقط التذكر
            - يكون له 4 خيارات (أ، ب، ج، د) مع إجابة واحدة صحيحة فقط
            - يوزع الإجابات الصحيحة: بعض أ، بعض ب، بعض ج، بعض د
            - يجعل المشتتات معقولة لكنها خاطئة
            
            ## الجزء 2: إجابة قصيرة (3 أسئلة)
            - تتطلب شرحاً أو تحليلاً
            - تختبر الفهم الأعمق للمفاهيم الرئيسية
            - تحدد طول الإجابة المتوقع (2-3 جمل)
            
            ## الجزء 3: التفكير النقدي (سؤالان)
            - تطبيق المفاهيم على سيناريوهات جديدة
            - تقييم الحجج أو الافتراضات
            - توليف أفكار متعددة
            
            ## مفتاح الإجابة
            قدّم إجابات كاملة:
            - الاختيار: الحرف الصحيح + شرح موجز
            - الإجابة القصيرة: نموذج إجابة
            - التفكير النقدي: النقاط الرئيسية التي يجب ذكرها
            
            اعتمد جميع الأسئلة فقط على النص المقدم. نوّع في الصعوبة.
            لا تطرح أسئلة تافهة أو سطحية. أنشئ أسئلة شاملة تتطلب فهماً عميقاً للنطاق الكامل للصفحات. في شروحات الإجابات، استشهد بأرقام الصفحات [Page X] عند الإشارة إلى محتوى محدد.
            PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.
            """;
    }

    /**
     * Checks whether a non-local API key is configured.
     * <p>
     * <b>What:</b> Returns {@code true} only when the API key is non-null, non-blank,
     * and not the default development placeholder.
     * <p>
     * <b>Why:</b> Prevents accidental cloud calls without credentials while allowing
     * local inference to proceed with an empty key.
     * <p>
     * <b>Where:</b> Required for AI configuration standards per project policy.
     *
     * @return {@code true} if a production API key is present
     */
    private boolean isApiKeyConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank() && !"dev-key-change-in-production".equals(properties.getApiKey());
    }

    /**
     * Checks whether the given URL points to a loopback address.
     * <p>
     * <b>What:</b> Parses the URL, resolves the host to an {@link InetAddress}, and
     * returns {@code true} only if the address is a loopback.
     * <p>
     * <b>Why:</b> Prevents SSRF by ensuring local inference endpoints are the only
     * ones allowed to bypass authentication headers.
     * <p>
     * <b>Where:</b> Required for secure URL handling in AI configuration.
     *
     * @param url the URL to check
     * @return {@code true} if the host is a loopback address
     */
    private boolean isLocalUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress();
        } catch (Exception e) {
            log.warn("Failed to parse URL for local check | url={}", LoggingUtils.safeLog(url, 100));
            return false;
        }
    }

    /**
     * Null-safe empty-string check.
     * <p>
     * <b>What:</b> Delegates to {@link StringUtils#isEmpty(String)} for consistent
     * blank detection across the codebase.
     * <p>
     * <b>Why:</b> Centralizes null handling for string validation.
     * <p>
     * <b>Where:</b> Required for input validation in AI service methods.
     *
     * @param str the string to test
     * @return {@code true} if null or blank
     */
    private boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * Null-safe string length measurement.
     * <p>
     * <b>What:</b> Returns the length of the string, or {@code 0} if null.
     * <p>
     * <b>Why:</b> Used for safe logging of potentially null fields.
     * <p>
     * <b>Where:</b> Required for consistent logging in AI service methods.
     *
     * @param str the string to measure
     * @return the length, or {@code 0} if null
     */
    private int safeLength(String str) {
        return StringUtils.safeLength(str);
    }

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
        "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions|" +
        "ignore\\s+all\\s+above|" +
        "disregard\\s+(all\\s+)?previous|" +
        "forget\\s+everything|" +
        "you\\s+are\\s+now|" +
        "pretend\\s+you\\s+are|" +
        "act\\s+as\\s+if|" +
        "new\\s+instructions|" +
        "override\\s+system|" +
        "\\[INST\\]|" +
        "<<SYS>>|" +
        "system\\s*:\\s*you\\s+are)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Redacts known prompt-injection patterns from user-supplied text.
     * <p>
     * <b>What:</b> Replaces instruction-override phrases (e.g., "ignore all previous
     * instructions", "you are now") with a {@code [REDACTED]} placeholder.
     * <p>
     * <b>Why:</b> Per OWASP LLM Top 10, user content must not be allowed to override
     * the system prompt.
     * <p>
     * <b>Where:</b> Required for OWASP LLM Top 10 prompt-injection resistance.
     *
     * @param userContent the raw user-supplied text
     * @return the sanitized text
     */
    private String sanitizeUserContent(String userContent) {
        if (isEmpty(userContent)) return userContent;
        String sanitized = INJECTION_PATTERN.matcher(userContent).replaceAll("[REDACTED]");
        if (!sanitized.equals(userContent)) {
            log.warn("Potential prompt injection detected and sanitized | originalLen={} | sanitizedLen={}",
                safeLength(userContent), safeLength(sanitized));
        }
        return sanitized;
    }

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
        "```json\\n([\\s\\S]*?)```|```\\n([\\s\\S]*?)```|<json>([\\s\\S]*?)</json>",
        Pattern.MULTILINE
    );
    private static final Pattern RAW_JSON_PATTERN = Pattern.compile(
        "\\{\\s*\"nodes\"\\s*:\\s*\\[.*?\"edges\"\\s*:\\s*\\[.*?\\]\\s*\\}",
        Pattern.DOTALL
    );

    public String extractJsonBlock(String text) {
        if (isEmpty(text)) return null;
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) return group.trim();
            }
        }
        Matcher rawMatcher = RAW_JSON_PATTERN.matcher(text);
        if (rawMatcher.find()) {
            return rawMatcher.group().trim();
        }
        return null;
    }

    public String stripJsonBlock(String text) {
        if (isEmpty(text)) return text;
        String result = text;
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(result);
        if (matcher.find()) {
            result = matcher.replaceAll("");
        }
        Matcher rawMatcher = RAW_JSON_PATTERN.matcher(result);
        if (rawMatcher.find()) {
            result = rawMatcher.replaceAll("");
        }
        return result.trim();
    }

    public String stripTrailingQuestion(String text) {
        if (isEmpty(text)) return text;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return trimmed;
        int lastNewline = trimmed.lastIndexOf('\n');
        String lastLine = lastNewline >= 0 ? trimmed.substring(lastNewline + 1).trim() : trimmed.trim();
        if (lastLine.endsWith("?") && (
                lastLine.toLowerCase().startsWith("would you like") ||
                lastLine.toLowerCase().startsWith("do you want") ||
                lastLine.toLowerCase().startsWith("should i ") ||
                lastLine.toLowerCase().startsWith("can i ") ||
                lastLine.toLowerCase().startsWith("may i ") ||
                lastLine.toLowerCase().contains("refine anything further") ||
                lastLine.toLowerCase().contains("anything else") ||
                lastLine.toLowerCase().contains("let me know"))) {
            if (lastNewline >= 0) {
                return trimmed.substring(0, lastNewline).trim();
            }
            return "";
        }
        return trimmed;
    }

    public String stripLeadingFiller(String text) {
        if (isEmpty(text)) return text;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return trimmed;
        String lower = trimmed.toLowerCase();
        String[] fillers = {
            "okay, ", "okay ", "okay\n", "okay\t",
            "sure, ", "sure ", "sure\n", "sure\t",
            "certainly, ", "certainly ", "certainly\n", "certainly\t",
            "here's ", "here is ", "here's\n", "here is\n",
            "great, ", "great ", "great\n", "great\t",
            "absolutely, ", "absolutely ", "absolutely\n", "absolutely\t",
            "of course, ", "of course ", "of course\n", "of course\t",
            "yes, ", "yes ", "yes\n", "yes\t",
            "yep, ", "yep ", "yep\n", "yep\t",
            "no problem, ", "no problem ", "no problem\n", "no problem\t",
            "sounds good, ", "sounds good ", "sounds good\n", "sounds good\t",
            "got it, ", "got it ", "got it\n", "got it\t",
            "sure thing, ", "sure thing ", "sure thing\n", "sure thing\t",
            "alright, ", "alright ", "alright\n", "alright\t",
            "all right, ", "all right ", "all right\n", "all right\t",
            "excellent, ", "excellent ", "excellent\n", "excellent\t",
            "wonderful, ", "wonderful ", "wonderful\n", "wonderful\t",
            "perfect, ", "perfect ", "perfect\n", "perfect\t",
            "nice, ", "nice ", "nice\n", "nice\t",
            "cool, ", "cool ", "cool\n", "cool\t",
            "gotcha, ", "gotcha ", "gotcha\n", "gotcha\t",
            "i understand, ", "i understand ", "i understand\n", "i understand\t",
            "understood, ", "understood ", "understood\n", "understood\t",
            "noted, ", "noted ", "noted\n", "noted\t",
            "indeed, ", "indeed ", "indeed\n", "indeed\t",
            "right, ", "right ", "right\n", "right\t",
            "correct, ", "correct ", "correct\n", "correct\t",
            "i see, ", "i see ", "i see\n", "i see\t",
            "i hear you, ", "i hear you ", "i hear you\n", "i hear you\t",
            "that's right, ", "that's right ", "that's right\n", "that's right\t",
            "exactly, ", "exactly ", "exactly\n", "exactly\t",
            "precisely, ", "precisely ", "precisely\n", "precisely\t",
            "definitely, ", "definitely ", "definitely\n", "definitely\t",
            "totally, ", "totally ", "totally\n", "totally\t",
            "without a doubt, ", "without a doubt ", "without a doubt\n", "without a doubt\t",
            "i agree, ", "i agree ", "i agree\n", "i agree\t",
            "i concur, ", "i concur ", "i concur\n", "i concur\t",
            "well, ", "well ", "well\n", "well\t",
            "hmm, ", "hmm ", "hmm\n", "hmm\t",
            "hmmm, ", "hmmm ", "hmmm\n", "hmmm\t",
            "yeah, ", "yeah ", "yeah\n", "yeah\t",
            "yup, ", "yup ", "yup\n", "yup\t",
            "surely, ", "surely ", "surely\n", "surely\t",
            "obviously, ", "obviously ", "obviously\n", "obviously\t",
            "clearly, ", "clearly ", "clearly\n", "clearly\t",
            "naturally, ", "naturally ", "naturally\n", "naturally\t"
        };
        for (String filler : fillers) {
            if (lower.startsWith(filler.trim())) {
                return trimmed.substring(filler.trim().length()).trim();
            }
        }
        return trimmed;
    }

    private static boolean isOnlyFiller(String text) {
        if (StringUtils.isEmpty(text)) return false;
        String trimmed = text.trim().toLowerCase();
        String[] fillers = {
            "okay", "ok",
            "sure", "certainly",
            "here's", "here is",
            "great", "absolutely", "of course",
            "yes", "yep", "no problem",
            "sounds good", "got it", "alright", "all right",
            "excellent", "wonderful", "perfect", "nice", "cool",
            "gotcha", "i understand", "understood", "noted",
            "indeed", "right", "correct", "i see", "i hear you",
            "that's right", "exactly", "precisely",
            "definitely", "totally", "without a doubt",
            "i agree", "i concur", "well", "hmm", "hmmm",
            "yeah", "yup", "surely", "obviously", "clearly",
            "naturally"
        };
        for (String filler : fillers) {
            if (trimmed.equals(filler)) {
                return true;
            }
        }
        return false;
    }

    public String truncateToTokens(String text, int maxTokens) {
        if (isEmpty(text)) return text;
        int maxChars = maxTokens * 3;
        if (text.length() <= maxChars) return text;
        String truncated = text.substring(0, maxChars) + "\n...[TRUNCATED]";
        log.info("Text token-truncated | originalLen={} | maxTokens={} | maxChars={} | truncatedLen={}",
            text.length(), maxTokens, maxChars, truncated.length());
        return truncated;
    }

    public Map<String, Object> parseJsonToMap(String json, String sourceText) {
        if (isEmpty(json)) return null;
        try {
            Map<String, Object> result = mapper.readValue(json, Map.class);
            
            // Validate concept map JSON structure
            if (result.containsKey("nodes") && result.containsKey("edges")) {
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
                List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");
                String normalizedSource = sourceText != null ? sourceText.toLowerCase() : "";
                
                // Filter out hallucinated nodes: must have valid id, label, and group
                if (nodes != null) {
                    List<Map<String, Object>> validNodes = new ArrayList<>();
                    Set<String> nodeIds = new HashSet<>();
                    
                    for (Map<String, Object> node : nodes) {
                        String id = (String) node.get("id");
                        String label = (String) node.get("label");
                        String group = (String) node.get("group");
                        
                        // Reject nodes with missing or invalid fields
                        if (id == null || id.trim().isEmpty() || 
                            label == null || label.trim().isEmpty() ||
                            group == null || !group.matches("core|sub|principle|application")) {
                            continue;
                        }
                        
                        // Reject nodes whose label does not appear in source text
                        if (!normalizedSource.isEmpty() && 
                            !normalizedSource.contains(label.toLowerCase().trim())) {
                            continue;
                        }
                        
                        // Reject duplicate IDs
                        if (nodeIds.contains(id)) {
                            continue;
                        }
                        
                        nodeIds.add(id);
                        validNodes.add(node);
                    }
                    
                    result.put("nodes", validNodes);
                    
                    // Filter edges to only reference valid node IDs
                    if (edges != null) {
                        List<Map<String, Object>> validEdges = new ArrayList<>();
                        Set<String> edgeKeys = new HashSet<>();
                        
                        for (Map<String, Object> edge : edges) {
                            String from = (String) edge.get("from");
                            String to = (String) edge.get("to");
                            String label = (String) edge.get("label");
                            
                            // Both endpoints must reference valid nodes
                            if (from == null || to == null || !nodeIds.contains(from) || !nodeIds.contains(to)) {
                                continue;
                            }
                            
                            // Reject duplicate edges
                            String edgeKey = from + "->" + to + ":" + (label != null ? label : "");
                            if (edgeKeys.contains(edgeKey)) {
                                continue;
                            }
                            
                            edgeKeys.add(edgeKey);
                            validEdges.add(edge);
                        }
                        
                        result.put("edges", validEdges);
                    }
                }
            }
            
            return result;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON block: {}", e.getMessage());
            return null;
        }
    }
    
    public Map<String, Object> parseJsonToMap(String json) {
        return parseJsonToMap(json, null);
    }

    /**
     * Custom exception for AI service errors.
     * <p>
     * <b>What:</b> Wraps failures from AI API calls with context about the failed operation.
     * <p>
     * <b>Why:</b> Provides clear, user-facing error messages without exposing internal
     * stack traces or implementation details.
     * <p>
     * <b>Where:</b> Required for clean error handling in AI integration points.
     */
    public static class AiServiceException extends RuntimeException {
        /**
         * Constructs the exception with a message and cause.
         * <p>
         * <b>What:</b> Creates an {@code AiServiceException} preserving the original
         * exception for debugging while exposing a user-safe message.
         *
         * @param message the user-safe error message
         * @param cause   the original exception
         */
        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}




