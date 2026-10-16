package com.bookassistant.service;

import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.infrastructure.BookPersistenceService;
import com.bookassistant.session.BookSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BookSession component.
 * Tests language detection and book management functionality.
 */
class BookSessionTest {

    private BookSession bookSession;

    @BeforeEach
    void setUp() {
        BookPersistenceService bookPersistenceService = Mockito.mock(BookPersistenceService.class);
        bookSession = new BookSession(bookPersistenceService);
    }

    /**
     * This name was chosen because this function verifies that the initial session state is empty.
     */
    @Test
    void testInitialSessionState() {
        // Session should start empty
        assertFalse(bookSession.hasBook());
        assertTrue(bookSession.getBook().isEmpty());
        assertEquals("en", bookSession.getDetectedLanguage());
    }

    /**
     * This name was chosen because this function verifies that setting and getting a book works correctly.
     */
    @Test
    void testSetBookAndGetBook() {
        // Create a test book
        List<PageData> pages = List.of(new PageData(1, "Page 1 text"));
        BookData book = new BookData("test.pdf", pages, "Test content");

        // Set the book
        bookSession.setBook(book);

        // Verify book is stored
        assertTrue(bookSession.hasBook());
        assertTrue(bookSession.getBook().isPresent());
        assertEquals("test.pdf", bookSession.getBook().get().fileName());
    }

    /**
     * This name was chosen because this function verifies that clearing the session works correctly.
     */
    @Test
    void testClearSession() {
        // Set a book first
        List<PageData> pages = List.of(new PageData(1, "Page 1 text"));
        BookData book = new BookData("test.pdf", pages, "Test content");
        bookSession.setBook(book);

        // Clear the session
        bookSession.clear();

        // Verify session is cleared
        assertFalse(bookSession.hasBook());
        assertTrue(bookSession.getBook().isEmpty());
    }

    /**
     * This name was chosen because this function verifies that Arabic content is detected correctly.
     */
    @Test
    void testLanguageDetection_ArabicContent() {
        // Create book with predominantly Arabic content
        String arabicText = "هذا نص عربي يحتوي على كلمات عربية كثيرة جداً";
        List<PageData> pages = List.of(new PageData(1, arabicText));
        BookData book = new BookData("arabic.pdf", pages, arabicText);

        bookSession.setBook(book);

        assertEquals("ar", bookSession.getDetectedLanguage());
    }

    /**
     * This name was chosen because this function verifies that English content is detected correctly.
     */
    @Test
    void testLanguageDetection_EnglishContent() {
        // Create book with predominantly English content
        String englishText = "This is an English text with many English words and sentences.";
        List<PageData> pages = List.of(new PageData(1, englishText));
        BookData book = new BookData("english.pdf", pages, englishText);

        bookSession.setBook(book);

        assertEquals("en", bookSession.getDetectedLanguage());
    }

    /**
     * This name was chosen because this function verifies that mixed content with more Arabic is detected as Arabic.
     */
    @Test
    void testLanguageDetection_MixedContentMoreArabic() {
        // Create book with mixed content but more Arabic characters
        // Arabic text has more characters than English
        String mixedText = "English. هذا نص عربي طويل جداً يحتوي على كلمات عربية كثيرة أكثر من الإنجليزية. More Arabic نص عربي طويل وأكثر";
        List<PageData> pages = List.of(new PageData(1, mixedText));
        BookData book = new BookData("mixed.pdf", pages, mixedText);

        bookSession.setBook(book);

        // Should detect Arabic since it has more Arabic characters
        assertEquals("ar", bookSession.getDetectedLanguage());
    }

    /**
     * This name was chosen because this function verifies that empty content defaults to English.
     */
    @Test
    void testLanguageDetection_EmptyContent_Rejected() {
        // Empty content should be rejected by setBook validation
        List<PageData> pages = List.of(new PageData(1, ""));
        BookData book = new BookData("empty.pdf", pages, "");
        assertThrows(IllegalArgumentException.class, () -> bookSession.setBook(book));
    }

    /**
     * This name was chosen because this function verifies that extracting text from a page range works correctly.
     */
    @Test
    void testExtractTextFromPageRange() {
        // Create book with multiple pages
        List<PageData> pages = List.of(
            new PageData(1, "Page 1 content"),
            new PageData(2, "Page 2 content"),
            new PageData(3, "Page 3 content"),
            new PageData(4, "Page 4 content"),
            new PageData(5, "Page 5 content")
        );

        BookData book = new BookData("multipage.pdf", pages, "Full text");
        bookSession.setBook(book);

        // Extract pages 2-4
        String extracted = bookSession.extractTextFromPageRange(2, 4);

        assertTrue(extracted.contains("Page 2 content"));
        assertTrue(extracted.contains("Page 3 content"));
        assertTrue(extracted.contains("Page 4 content"));
        assertFalse(extracted.contains("Page 1 content"));
        assertFalse(extracted.contains("Page 5 content"));
    }

    /**
     * This name was chosen because this function verifies that extracting text returns empty when no book is loaded.
     */
    @Test
    void testExtractTextFromPageRange_NoBook() {
        // Should return empty string when no book is loaded
        String extracted = bookSession.extractTextFromPageRange(1, 10);
        assertTrue(extracted.isEmpty());
    }

    /**
     * This name was chosen because this function verifies that extracting text returns empty for invalid page range.
     */
    @Test
    void testExtractTextFromPageRange_InvalidRange() {
        // Create book with pages
        List<PageData> pages = List.of(
            new PageData(1, "Page 1"),
            new PageData(2, "Page 2")
        );
        BookData book = new BookData("test.pdf", pages, "Full text");
        bookSession.setBook(book);

        // Invalid range should return empty
        String extracted = bookSession.extractTextFromPageRange(10, 20);
        assertTrue(extracted.isEmpty());
    }

    /**
     * This name was chosen because this function verifies that extracting a single page works correctly.
     */
    @Test
    void testExtractTextFromPageRange_SinglePage() {
        // Create book with pages
        List<PageData> pages = List.of(
            new PageData(1, "Page 1"),
            new PageData(2, "Page 2"),
            new PageData(3, "Page 3")
        );
        BookData book = new BookData("test.pdf", pages, "Full text");
        bookSession.setBook(book);

        // Extract single page
        String extracted = bookSession.extractTextFromPageRange(2, 2);
        assertEquals("--- [Page 2] ---\nPage 2", extracted);
    }

    /**
     * This name was chosen because this function verifies that language detection works correctly for large text.
     */
    @Test
    void testLanguageDetection_LargeText() {
        // Test with large text (should only sample first 1000 chars)
        StringBuilder largeArabicText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeArabicText.append("نص عربي ");
        }

        List<PageData> pages = List.of(new PageData(1, largeArabicText.toString()));
        BookData book = new BookData("large.pdf", pages, largeArabicText.toString());
        bookSession.setBook(book);

        assertEquals("ar", bookSession.getDetectedLanguage());
    }
}


