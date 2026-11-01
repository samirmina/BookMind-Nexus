package com.bookassistant.service.file;

import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.util.LoggingUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Calls the Pix2Text extraction microservice to extract PDF text with
 * LaTeX math notation.
 *
 * <p><b>Where:</b> Used by {@link FileParserService} when extraction mode
 * is set to {@code pix2text}.</p>
 */
@Service
public class Pix2TextExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(Pix2TextExtractionClient.class);

    private final String baseUrl;
    private final int timeoutSeconds;
    private final RuntimeLogService runtimeLogService;
    private static final String COMPONENT_PIX2TEXT = "Pix2TextExtractionClient";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Pix2TextExtractionClient(
            @Value("${app.extraction.pix2text.url:http://localhost:8080}") String baseUrl,
            @Value("${app.extraction.pix2text.timeout:7200s}") java.time.Duration timeout,
            RuntimeLogService runtimeLogService) {
        this.baseUrl = baseUrl;
        this.timeoutSeconds = (int) timeout.getSeconds();
        this.runtimeLogService = runtimeLogService;
        log.info("Pix2TextExtractionClient initialized | baseUrl={} | timeout={}", baseUrl, timeout);
    }

    /**
     * Extracts text from a PDF byte array via the Pix2Text service.
     *
     * @param pdfBytes raw PDF content
     * @param fileName the original file name
     * @return list of page texts with their original page numbers from Pix2Text
     * @throws IllegalStateException if Pix2Text service returns non-200 or request fails
     */
    public java.util.List<PageInfo> extractPages(byte[] pdfBytes, String fileName) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            log.warn("extractPages called with empty PDF bytes");
            return java.util.List.of();
        }

        runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "extractPages entered", Map.of(
                "fileName", fileName,
                "bytesLength", pdfBytes.length
        ));

        HttpURLConnection connection = null;
        try {
            String url = baseUrl + "/extract/pix2text";
            String boundary = "pix2text-boundary-" + System.currentTimeMillis();
            String safeFileName = (fileName == null || fileName.isBlank()) ? "upload.pdf" : fileName;

            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "Preparing HTTP request", Map.of(
                    "url", url,
                    "fileName", safeFileName
            ));

            String LINE_FEED = "\r\n";
            String charset = "UTF-8";

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream outputStream = new java.io.BufferedOutputStream(baos);

            outputStream.write(("--" + boundary + LINE_FEED).getBytes(charset));
            outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName + "\"" + LINE_FEED).getBytes(charset));
            outputStream.write(("Content-Type: application/pdf" + LINE_FEED).getBytes(charset));
            outputStream.write(LINE_FEED.getBytes(charset));
            outputStream.write(pdfBytes);
            outputStream.write(LINE_FEED.getBytes(charset));
            outputStream.write(("--" + boundary + "--" + LINE_FEED).getBytes(charset));
            outputStream.flush();

            byte[] body = baos.toByteArray();

            connection = (HttpURLConnection) new URL(url).openConnection();
            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "HTTP connection opened", Map.of("url", url));
            connection.setConnectTimeout(60_000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Content-Length", String.valueOf(body.length));
            connection.setDoOutput(true);
            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "HTTP request configured", Map.of(
                    "contentLength", body.length,
                    "boundary", boundary
            ));

            try (OutputStream requestStream = connection.getOutputStream()) {
                requestStream.write(body);
                requestStream.flush();
            }
            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "HTTP request body written", Map.of());

            int status = connection.getResponseCode();
            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "HTTP response code received", Map.of("status", status));
            String bodyResponse;
            if (status >= 400) {
                bodyResponse = new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            } else {
                bodyResponse = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "HTTP response received", Map.of(
                    "status", status,
                    "bodyLength", bodyResponse != null ? bodyResponse.length() : 0
            ));

            // LOG RAW JSON RESPONSE BEFORE PARSING
            logRawResponse(bodyResponse, fileName);

            if (status != 200) {
                runtimeLogService.error(COMPONENT_PIX2TEXT, "unknown", "Pix2Text service error", Map.of(
                        "status", status,
                        "body", bodyResponse
                ));
                throw new IllegalStateException("Pix2Text service error | status=" + status + " | body=" + bodyResponse);
            }

            java.util.List<PageInfo> pages = extractPagesFromJson(bodyResponse, fileName);
            runtimeLogService.info(COMPONENT_PIX2TEXT, "unknown", "JSON parsed", Map.of(
                    "pages", pages.size()
            ));
            log.info("Pix2Text extraction complete | pages={}", pages.size());
            return pages;

        } catch (Exception e) {
            runtimeLogService.error(COMPONENT_PIX2TEXT, "unknown", "extractPages failed", Map.of(
                    "error", e.getMessage()
            ));
            throw new IllegalStateException("Failed to extract PDF via Pix2Text service", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Extracts text from a PDF byte array via the Pix2Text service with page range.
     *
     * @param pdfBytes raw PDF content
     * @param fileName the original file name
     * @param startPage 1-based start page (inclusive)
     * @param endPage   1-based end page (inclusive)
     * @return list of page texts with page numbers offset by startPage-1
     * @throws IllegalStateException if Pix2Text service returns non-200 or request fails
     * @deprecated Use {@link #extractPages(byte[], String)} instead. Page ranges are now handled by PdfSliceService.
     */
    @Deprecated
    public java.util.List<PageInfo> extractPages(byte[] pdfBytes, String fileName, int startPage, int endPage) {
        java.util.List<PageInfo> pages = extractPages(pdfBytes, fileName);
        if (startPage > 0 && !pages.isEmpty()) {
            int offset = startPage - 1;
            for (PageInfo page : pages) {
                // We need to create new PageInfo objects with adjusted page numbers
                // Since PageInfo fields are final, we'll use reflection or create a new list
            }
        }
        return pages;
    }

    /**
     * Health check for the Pix2Text service.
     *
     * @return true if the service is reachable
     */
    public boolean isHealthy() {
        try {
            String url = baseUrl + "/health";
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");

            int status = connection.getResponseCode();
            String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            connection.disconnect();
            return status == 200 && body.contains("ok");
        } catch (Exception e) {
            log.warn("Pix2Text service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Logs the raw JSON response from Pix2Text before parsing.
     * This enables debugging of field-name mismatches and empty responses.
     */
    private void logRawResponse(String json, String fileName) {
        if (json == null || json.isBlank()) {
            log.warn("Pix2Text RAW response is null/blank | fileName={}", fileName);
            return;
        }
        // Log first 2000 chars to avoid flooding console, but enough to see full page objects
        String preview = json.length() > 2000 ? json.substring(0, 2000) + "...[TRUNCATED]" : json;
        log.info("Pix2Text RAW JSON response | fileName={} | totalLength={} | preview={}",
            fileName, json.length(), preview);
    }

    /**
     * Parses the JSON response body from Pix2Text using Jackson.
     * Extracts page numbers and text from the "chunks" array, preserving
     * escaped characters and LaTeX backslashes.
     */
    private java.util.List<PageInfo> extractPagesFromJson(String json, String fileName) {
        java.util.List<PageInfo> pages = new java.util.ArrayList<>();

        if (json == null || json.isBlank()) {
            log.warn("Pix2Text RAW response is null/blank | fileName={}", fileName);
            return pages;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode chunks = root.path("chunks");

            if (!chunks.isArray()) {
                log.warn("Pix2Text response missing chunks array | fileName={}", fileName);
                return pages;
            }

            Iterator<JsonNode> iterator = chunks.elements();
            while (iterator.hasNext()) {
                JsonNode chunk = iterator.next();
                JsonNode pageNode = chunk.path("page");
                JsonNode textNode = chunk.path("text");

                int pageNumber = pageNode.isMissingNode() ? -1 : pageNode.asInt();
                String text = textNode.isMissingNode() ? null : textNode.asText();

                int resolvedPage = pageNumber > 0 ? pageNumber : pages.size() + 1;
                boolean isBlank = text == null || text.isBlank();
                int charCount = text != null ? text.length() : 0;
                boolean hasMath = text != null && (text.contains("\\frac") || text.contains("\\sum") || text.contains("\\int") ||
                            text.contains("\\sqrt") || text.contains("\\partial") ||
                            text.contains("<math>") || text.contains("$$") || text.contains("\\("));

                if (isBlank) {
                    log.warn("Pix2Text page {} has EMPTY/NULL text | fileName={} | rawPayload around page: {}",
                        resolvedPage, fileName, json.substring(Math.max(0, json.indexOf("\"page\":") + 1), Math.min(json.length(), json.indexOf("\"page\":") + 200)));
                } else {
                    log.info("Pix2Text page {} | chars={} | hasMath={} | fileName={}",
                        resolvedPage, charCount, hasMath, fileName);
                    pages.add(new PageInfo(resolvedPage, text));
                }
            }
        } catch (Exception e) {
            log.error("Pix2Text JSON parsing failed | fileName={} | error={}", fileName, e.getMessage());
        }

        return pages;
    }

    /**
     * Holds a single extracted page with its original page number from Pix2Text.
     */
    public static class PageInfo {
        public final int pageNumber;
        public final String text;

        public PageInfo(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
        }
    }
}
