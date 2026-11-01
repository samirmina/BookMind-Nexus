package com.bookassistant.service.infrastructure;

import com.bookassistant.model.ExtractionCacheEntry;
import com.bookassistant.model.PageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * File-based semantic cache for PDF extraction results.
 * <p>
 * <b>What:</b> Stores and retrieves {@link ExtractionCacheEntry} snapshots on the
 * local filesystem under {@code cache/extractions/}. The cache key is the SHA-256
 * hash of the raw PDF bytes, so the same content is reused regardless of filename.
 * <p>
 * <b>Why:</b> Pix2Text extraction is expensive. Re-extracting identical PDFs
 * wastes GPU time and server resources. This cache makes re-uploads of the same
 * book instantaneous.
 * <p>
 * <b>Where:</b> Called by {@link com.bookassistant.service.file.FileParserService}
 * before any extraction microservice is contacted.
 */
@Service
public class ExtractionCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionCacheService.class);
    private final Path cacheDir;

    public ExtractionCacheService(@org.springframework.beans.factory.annotation.Value("${app.cache-dir:external/cache}") String cacheDir) {
        this.cacheDir = Paths.get(cacheDir, "extractions").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.cacheDir);
            log.info("Extraction cache initialized | dir={}", this.cacheDir);
        } catch (IOException e) {
            log.error("Failed to initialize extraction cache", e);
            throw new IllegalStateException("Cannot initialize extraction cache", e);
        }
    }

    /**
     * Computes the SHA-256 content hash for the given PDF bytes.
     * <p>
     * <b>What:</b> Deterministic content address for any PDF.
     * <p>
     * <b>Why:</b> Two PDFs with identical bytes produce the same hash, enabling
     * cross-filename cache hits.
     *
     * @param pdfBytes raw PDF content
     * @return hex-encoded SHA-256 hash
     */
    public String computeHash(byte[] pdfBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pdfBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute PDF hash", e);
        }
    }

    /**
     * Loads a cached extraction entry by content hash.
     *
     * @param hash SHA-256 hash of the PDF
     * @return cached entry, or {@code null} if not found or unreadable
     */
    public ExtractionCacheEntry load(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        try {
            Path cacheFile = this.cacheDir.resolve(hash + ".json");
            if (!Files.exists(cacheFile)) {
                return null;
            }
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            return parseEntry(json);
        } catch (Exception e) {
            log.warn("Failed to load extraction cache | hash={}", hash, e);
            try {
                Path cacheFile = this.cacheDir.resolve(hash + ".json");
                Files.deleteIfExists(cacheFile);
                log.info("Deleted corrupt cache file | hash={} | path={}", hash, cacheFile.toAbsolutePath());
            } catch (IOException deleteException) {
                log.warn("Failed to delete corrupt cache file | hash={}", hash, deleteException);
            }
            return null;
        }
    }

    /**
     * Saves an extraction result to the cache.
     * <p>
     * <b>What:</b> Persists the entry as JSON under {@code cache/extractions/{hash}.json}.
     * <p>
     * <b>Why:</b> Future uploads of the same PDF content can be served from disk
     * without re-running Pix2Text or Marker.
     *
     * @param hash        SHA-256 hash of the PDF
     * @param fileName    original uploaded filename
     * @param extractionMode extraction mode that produced this entry
     * @param pages       ordered list of extracted pages
     * @param fullText    concatenated full text of the book
     */
    public void save(String hash, String fileName, String extractionMode, List<PageData> pages, String fullText) {
        if (hash == null || hash.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(cacheDir);
            int pageCount = pages != null ? pages.size() : 0;
            ExtractionCacheEntry entry = new ExtractionCacheEntry(
                    hash, fileName, extractionMode, Instant.now().toEpochMilli(), pageCount, fullText, pages
            );
            String json = toJson(entry);
            Path cacheFile = this.cacheDir.resolve(hash + ".json");
            Files.writeString(cacheFile, json, StandardCharsets.UTF_8);
            log.info("Saved extraction cache | hash={} | pages={} | file={}", hash, pageCount, cacheFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to save extraction cache | hash={}", hash, e);
        }
    }

    /**
     * Returns {@code true} if a cache entry exists for the given hash.
     */
    public boolean has(String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        return Files.exists(cacheDir.resolve(hash + ".json"));
    }

    private ExtractionCacheEntry parseEntry(String json) {
        String fileName = extractJsonString(json, "fileName");
        String extractionMode = extractJsonString(json, "extractionMode");
        String timestampStr = extractJsonString(json, "timestamp");
        String pageCountStr = extractJsonString(json, "pageCount");
        String fullText = extractJsonString(json, "fullText");

        long timestamp = timestampStr.isBlank() ? 0 : Long.parseLong(timestampStr);
        int pageCount = pageCountStr.isBlank() ? 0 : Integer.parseInt(pageCountStr);

        List<PageData> pages = new ArrayList<>();
        int pagesIdx = json.indexOf("\"pages\"");
        if (pagesIdx >= 0) {
            int arrayStart = json.indexOf('[', pagesIdx);
            if (arrayStart >= 0) {
                int searchFrom = arrayStart + 1;
                while (true) {
                    int pageObjStart = json.indexOf('{', searchFrom);
                    if (pageObjStart < 0) break;
                    int pageObjEnd = json.indexOf('}', pageObjStart);
                    if (pageObjEnd < 0) break;
                    String pageObj = json.substring(pageObjStart, pageObjEnd + 1);
                    int pageNumber = Integer.parseInt(extractJsonString(pageObj, "pageNumber"));
                    String text = extractJsonString(pageObj, "text");
                    pages.add(new PageData(pageNumber, text));
                    searchFrom = pageObjEnd + 1;
                }
            }
        }

        return new ExtractionCacheEntry(null, fileName, extractionMode, timestamp, pageCount, fullText, pages);
    }

    private String toJson(ExtractionCacheEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"hash\":\"").append(escapeJson(entry.hash())).append("\",");
        sb.append("\"fileName\":").append(escapeJson(entry.fileName())).append(",");
        sb.append("\"extractionMode\":").append(escapeJson(entry.extractionMode())).append(",");
        sb.append("\"timestamp\":").append(entry.timestamp()).append(",");
        sb.append("\"pageCount\":").append(entry.pageCount()).append(",");
        sb.append("\"fullText\":").append(escapeJson(entry.fullText())).append(",");
        sb.append("\"pages\":[");
        for (int i = 0; i < entry.pages().size(); i++) {
            PageData p = entry.pages().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"pageNumber\":")
              .append(p.pageNumber())
              .append(",\"text\":")
              .append(escapeJson(p.text()))
              .append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        idx += search.length();
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == '\t')) idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return "";
        idx++;
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') {
                if (idx + 1 < json.length() && json.charAt(idx + 1) == '"') {
                    sb.append('"');
                    idx += 2;
                } else {
                    break;
                }
            } else if (c == '\\') {
                if (idx + 1 < json.length()) {
                    char next = json.charAt(idx + 1);
                    switch (next) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        default -> sb.append(next);
                    }
                    idx += 2;
                } else {
                    sb.append(c);
                    idx++;
                }
            } else {
                sb.append(c);
                idx++;
            }
        }
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
