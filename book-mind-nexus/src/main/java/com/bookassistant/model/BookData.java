package com.bookassistant.model;

import java.util.List;

/**
 * Immutable value object representing a fully parsed book.
 * <p>
 * <b>What:</b> Carrier for the original filename, ordered page records, and the
 * concatenated full-text string.
 * <p>
 * <b>Why:</b> A record provides a concise, type-safe domain model that is safe to
 * share across thread boundaries (all fields are final).
 */
public record BookData(String fileName, List<PageData> pages, String fullText) {
    
    /**
     * This name was chosen because this function checks if the book has content.
     * Returns true if the book has pages and text.
     */
    public boolean hasContent() {
        return pages != null && !pages.isEmpty() && fullText != null && !fullText.isBlank();
    }
    
    /**
     * This name was chosen because this function gets the total page count.
     * Returns the number of pages in the book.
     */
    public int getPageCount() {
        return pages != null ? pages.size() : 0;
    }
    
    /**
     * This name was chosen because this function gets the total word count.
     * Sums up word counts from all pages.
     */
    public int getTotalWordCount() {
        if (pages == null) {
            return 0;
        }
        return pages.stream().mapToInt(PageData::getWordCount).sum();
    }
    
    /**
     * This name was chosen because this function gets a specific page by number.
     * Returns the page data for the given page number, or null if not found.
     */
    public PageData getPage(int pageNumber) {
        if (pages == null) {
            return null;
        }
        return pages.stream()
            .filter(p -> p.pageNumber() == pageNumber)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * This name was chosen because this function checks if a page range is valid.
     * Returns true if start and end pages exist in the book.
     */
    public boolean isValidPageRange(int startPage, int endPage) {
        return startPage >= 1 && endPage <= getPageCount() && startPage <= endPage;
    }
}


