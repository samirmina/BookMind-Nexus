package com.bookassistant.service.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads the {@code cache/{bookName}/index.json} index and correlates AI response text
 * with the figures extracted by {@link ImageExtractor}.
 * <p>
 * <b>What:</b> Loads the per-book image cache index and matches page numbers found in
 * AI-generated answers against cached figures so the UI can display the relevant images.
 * <p>
 * <b>Why:</b> Keeps AI responses contextually grounded; without it, the QA and summary
 * pages would show no figures even though the model may reference them.
 * <p>
 * <b>Where:</b> Required for figure-aware study flows; temporary until a database-backed
 * search index replaces the file-system cache.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final Pattern PAGE_PATTERN = Pattern.compile("(?i)(?:page|p\\.|\\bpage\\b)\\s*(\\d+)");

    private final ObjectMapper mapper;
    private final String cacheDir;

    public CacheService(ObjectMapper mapper, @org.springframework.beans.factory.annotation.Value("${app.cache-dir:cache}") String cacheDir) {
        this.mapper = mapper;
        this.cacheDir = cacheDir;
    }

    /**
     * Loads the cache index for the given book name.
     * <p>
     * The book name is sanitized to a lowercase, filesystem-safe string before the
     * {@code index.json} lookup.
     *
     * @param bookName the logical book name (typically the PDF filename without extension)
     * @return a non-null {@link CacheIndex}; {@link CacheIndex#EMPTY} if not found
     */
    public CacheIndex loadIndex(String bookName) {
        if (bookName == null || bookName.isBlank()) {
            return CacheIndex.EMPTY;
        }
        String safeName = bookName.toLowerCase()
            .replaceAll("[^a-z0-9\\-_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
        Path indexPath = Paths.get(cacheDir, safeName, "index.json");
        if (!Files.exists(indexPath)) {
            return CacheIndex.EMPTY;
        }
        try {
            String json = Files.readString(indexPath);
            Map<String, Object> root = mapper.readValue(json, Map.class);
            List<Map<String, Object>> rawImages = (List<Map<String, Object>>) root.getOrDefault("images", Collections.emptyList());
            List<CachedImage> images = new ArrayList<>();
            for (Map<String, Object> raw : rawImages) {
                images.add(new CachedImage(
                    (String) raw.get("file"),
                    ((Number) raw.getOrDefault("page", 0)).intValue(),
                    (String) raw.get("type"),
                    (String) raw.get("format"),
                    (String) raw.get("topic")
                ));
            }
            String bookNameFromIndex = (String) root.get("bookName");
            int totalPages = ((Number) root.getOrDefault("totalPages", 0)).intValue();
            int totalImages = ((Number) root.getOrDefault("totalImages", 0)).intValue();
            return new CacheIndex(bookNameFromIndex, totalPages, totalImages, images);
        } catch (IOException e) {
            log.warn("Failed to read cache index for book '{}': {}", bookName, e.getMessage());
            return CacheIndex.EMPTY;
        } catch (Exception e) {
            log.warn("Failed to parse cache index for book '{}': {}", bookName, e.getMessage());
            return CacheIndex.EMPTY;
        }
    }

    /**
     * Scans the given AI response text for page-number references and returns the
     * corresponding cached images.
     * <p>
     * <b>Workaround:</b> {@link NumberFormatException} during page-number extraction is
     * silently swallowed because regex matches on user-generated text can produce
     * non-numeric group values. This is temporary; stricter input validation or a
     * number-only regex should be introduced before the next release.
     *
     * @param index the cache index to search
     * @param text  the AI response text
     * @return matching {@link CachedImage} entries, possibly empty
     */
    public List<CachedImage> findImagesForPages(CacheIndex index, String text) {
        if (index == null || index.isEmpty() || text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        Matcher matcher = PAGE_PATTERN.matcher(text);
        List<Integer> mentionedPages = new ArrayList<>();
        while (matcher.find()) {
            try {
                mentionedPages.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                // WORKAROUND: silently skip non-numeric page references until regex is hardened
            }
        }
        if (mentionedPages.isEmpty()) {
            return Collections.emptyList();
        }
        return index.images.stream()
            .filter(img -> mentionedPages.contains(img.page))
            .collect(Collectors.toList());
    }

    public String buildImageContext(CacheIndex index) {
        if (index == null || index.isEmpty()) {
            return "";
        }
        List<Integer> pagesWithImages = index.images.stream()
            .map(img -> img.page)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        return "The book contains " + index.totalImages + " embedded figures on pages: "
            + pagesWithImages.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "))
            + ". When discussing key concepts, reference relevant figures by their page number.";
    }

    public static class CacheIndex {
        public static final CacheIndex EMPTY = new CacheIndex(null, 0, 0, Collections.emptyList());

        public final String bookName;
        public final int totalPages;
        public final int totalImages;
        public final List<CachedImage> images;

        public CacheIndex(String bookName, int totalPages, int totalImages, List<CachedImage> images) {
            this.bookName = bookName;
            this.totalPages = totalPages;
            this.totalImages = totalImages;
            this.images = images;
        }

        public boolean isEmpty() {
            return images == null || images.isEmpty();
        }
    }

    public static class CachedImage {
        public final String file;
        public final int page;
        public final String type;
        public final String format;
        public final String topic;

        public CachedImage(String file, int page, String type, String format, String topic) {
            this.file = file;
            this.page = page;
            this.type = type;
            this.format = format;
            this.topic = topic;
        }
    }
}


