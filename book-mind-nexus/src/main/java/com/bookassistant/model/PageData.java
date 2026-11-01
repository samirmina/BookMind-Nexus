package com.bookassistant.model;

/**
 * Immutable value object representing a single extracted page.
 * <p>
 * <b>What:</b> Carrier for a 1-based page number and the extracted text string.
 * <p>
 * <b>Why:</b> Keeps page metadata independent from parsing logic; useful for partial
 * extraction and page-range queries.
 */
public record PageData(int pageNumber, String text) {
    
    /**
     * This name was chosen because this function checks if the page has text content.
     * Returns true if the page contains non-empty text.
     */
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
    
    /**
     * This name was chosen because this function gets the word count of the page.
     * Useful for statistics and processing estimates.
     */
    public int getWordCount() {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.split("\\s+").length;
    }
}


