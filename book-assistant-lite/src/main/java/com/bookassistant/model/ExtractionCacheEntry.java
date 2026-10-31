package com.bookassistant.model;

import java.util.List;

/**
 * Cache entry for extracted PDF content.
 * <p>
 * <b>What:</b> Immutable snapshot of a completed extraction, keyed by PDF content hash.
 * <p>
 * <b>Why:</b> Allows the same book to be re-uploaded under any filename without
 * repeating expensive extraction. The cache is content-addressed, not name-addressed.
 */
public record ExtractionCacheEntry(
        String hash,
        String fileName,
        String extractionMode,
        long timestamp,
        int pageCount,
        String fullText,
        List<PageData> pages
) {
    /**
     * Convenience factory for cache misses.
     */
    public static ExtractionCacheEntry empty(String hash) {
        return new ExtractionCacheEntry(hash, null, null, 0L, 0, null, List.of());
    }

    public boolean hasContent() {
        return pages != null && !pages.isEmpty() && fullText != null && !fullText.isBlank();
    }
}
