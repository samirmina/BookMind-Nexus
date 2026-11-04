package com.bookassistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone client-side tool for processing book content with AI services.
 * <p>
 * <b>What:</b> Reads a PDF book from the local filesystem, extracts text from a
 * user-specified page range, sends it to a chosen AI service, and writes the
 * result back to an output file. Runs entirely on the client side -- no book
 * data is ever uploaded to a web server.
 * <p>
 * <b>Why:</b> Large book files are impractical to upload. This tool keeps the
 * source material on the client machine and only sends the relevant page range
 * to the AI service. The web application orchestrates the UI; this tool handles
 * the heavy lifting locally.
 * <p>
 * <b>Where:</b> Invoked by the web application via process execution or CLI.
 * Results are written to a file that the web app can retrieve and present to
 * the user for saving.
 */
public final class BookClientTool {

    private static final Logger log = LoggerFactory.getLogger(BookClientTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private BookClientTool() {}

    /**
     * CLI entry point.
     * <p>
     * Usage:
     *   java BookClientTool &lt;book-path&gt; &lt;start-page&gt; &lt;end-page&gt;
     *     &lt;ai-service&gt; &lt;api-key&gt; &lt;output-file&gt;
     * <p>
     * Supported AI services: ollama, openai, anthropic, google-vertex
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println("Usage: BookClientTool <book-path> <start-page> <end-page> "
                + "<ai-service> <api-key> <output-file>");
            System.err.println("  AI services: ollama, openai, anthropic, google-vertex");
            System.exit(1);
        }

        String bookPath = args[0];
        int startPage;
        int endPage;
        try {
            startPage = Integer.parseInt(args[1]);
            endPage = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Error: start-page and end-page must be integers");
            System.exit(1);
            return;
        }
        String aiService = args[3];
        String apiKey = args[4];
        String outputFile = args[5];

        int exitCode = processBook(bookPath, startPage, endPage, aiService, apiKey, outputFile);
        System.exit(exitCode);
    }

    /**
     * Processes a book page range through an AI service.
     *
     * @param bookPath   absolute path to the source PDF
     * @param startPage  1-based inclusive start page
     * @param endPage    1-based inclusive end page
     * @param aiService  target AI service (ollama, openai, anthropic, google-vertex)
     * @param apiKey     API key for the selected service
     * @param outputFile path where the result should be written
     * @return 0 on success, 1 on failure
     */
    public static int processBook(
            String bookPath,
            int startPage,
            int endPage,
            String aiService,
            String apiKey,
            String outputFile) {

        log.info("Processing book | path={} | pages={}-{} | service={} | output={}",
            bookPath, startPage, endPage, aiService, outputFile);

        Path pdfPath = Paths.get(bookPath);
        if (!Files.exists(pdfPath) || !Files.isRegularFile(pdfPath)) {
            log.error("Book file not found: {}", bookPath);
            return 1;
        }

        if (startPage < 1 || endPage < startPage) {
            log.error("Invalid page range: {}-{}", startPage, endPage);
            return 1;
        }

        // Extract text from the requested page range
        String pageText;
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            int totalPages = doc.getNumberOfPages();
            if (startPage > totalPages) {
                log.error("Start page {} exceeds total pages {}", startPage, totalPages);
                return 1;
            }
            int actualEndPage = Math.min(endPage, totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(actualEndPage);
            pageText = stripper.getText(doc);

            log.info("Extracted text | pages={}-{} | chars={} | totalPages={}",
                startPage, actualEndPage, pageText.length(), totalPages);
        } catch (IOException e) {
            log.error("Failed to read PDF: {}", e.getMessage(), e);
            return 1;
        }

        if (pageText == null || pageText.trim().isEmpty()) {
            log.error("No text extracted from pages {}-{}", startPage, endPage);
            return 1;
        }

        // Send to AI service
        String result;
        try {
            result = callAiService(aiService, apiKey, pageText);
        } catch (Exception e) {
            log.error("AI service call failed: {}", e.getMessage(), e);
            return 1;
        }

        // Write result to output file
        try {
            Path outPath = Paths.get(outputFile);
            Files.createDirectories(outPath.getParent());
            Files.writeString(outPath, result, StandardCharsets.UTF_8);
            log.info("Result written | output={} | chars={}", outputFile, result.length());
        } catch (IOException e) {
            log.error("Failed to write output file: {}", e.getMessage(), e);
            return 1;
        }

        return 0;
    }

    /**
     * Calls the specified AI service with the extracted text.
     *
     * @param aiService target AI service
     * @param apiKey    API key
     * @param text      text to process
     * @return the AI service response
     * @throws Exception if the call fails
     */
    private static String callAiService(String aiService, String apiKey, String text)
        throws Exception {

        switch (aiService.toLowerCase()) {
            case "ollama":
                return callOllama(apiKey, text);
            case "openai":
                return callOpenAI(apiKey, text);
            case "anthropic":
                return callAnthropic(apiKey, text);
            case "google-vertex":
                return callGoogleVertex(apiKey, text);
            default:
                throw new IllegalArgumentException("Unsupported AI service: " + aiService);
        }
    }

    private static String callOllama(String baseUrl, String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama3");
        body.put("prompt", "Summarize the following text:\n\n" + text);
        body.put("stream", false);

        String json = MAPPER.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(300))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("Ollama API error | status=" + response.statusCode()
                + " | body=" + response.body());
        }

        JsonNode node = MAPPER.readTree(response.body());
        return node.has("response") ? node.get("response").asText() : response.body();
    }

    private static String callOpenAI(String apiKey, String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-3.5-turbo");
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "Summarize the following text:\n\n" + text);
        messages.add(userMsg);
        body.put("messages", messages);
        body.put("stream", false);

        String json = MAPPER.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(300))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("OpenAI API error | status=" + response.statusCode()
                + " | body=" + response.body());
        }

        JsonNode node = MAPPER.readTree(response.body());
        return node.path("choices").path(0).path("message").path("content").asText(response.body());
    }

    private static String callAnthropic(String apiKey, String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-3-haiku-20240307");
        body.put("max_tokens", 4096);
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", "Summarize the following text:\n\n" + text
        )));

        String json = MAPPER.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(300))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("Anthropic API error | status=" + response.statusCode()
                + " | body=" + response.body());
        }

        JsonNode node = MAPPER.readTree(response.body());
        return node.path("content").path(0).path("text").asText(response.body());
    }

    private static String callGoogleVertex(String apiKey, String text) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
            + "?key=" + apiKey;

        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", "Summarize the following text:\n\n" + text);
        parts.add(part);
        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);
        body.put("contents", List.of(content));

        String json = MAPPER.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(300))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("Google Vertex API error | status=" + response.statusCode()
                + " | body=" + response.body());
        }

        JsonNode node = MAPPER.readTree(response.body());
        return node.path("candidates").path(0).path("content").path("parts").path(0)
            .path("text").asText(response.body());
    }
}
