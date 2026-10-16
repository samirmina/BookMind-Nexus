package com.bookassistant.session;

import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.BookPersistenceService;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Session-scoped component that holds the currently loaded book and its detected language.
 * <p>
 * <b>What:</b> Stores {@link BookData} and the auto-detected language code ("ar" or "en")
 * for the duration of a user session.
 * <p>
 * <b>Why:</b> Per {@code PROJECT_RULES.md} Section 1, session-scoped state must be
 * isolated from request-scoped controllers; this component is the single owner of the
 * domain object.
 * <p>
 * <b>Where:</b> Required for all study flows that need access to book content without
 * re-parsing the PDF on every request.
 */
@Component
public class BookSession {

    private static final Logger log = LoggerFactory.getLogger(BookSession.class);
    private static final Pattern ARABIC_PATTERN = Pattern.compile("[\\u0600-\\u06FF]");

    private final Object lock = new Object();

    private BookData currentBook;
    private Long currentBookId;
    private String detectedLanguage;

    @Value("${app.user.id:1}")
    private Long userId;

    private final BookPersistenceService bookPersistenceService;

    public BookSession(BookPersistenceService bookPersistenceService) {
        this.bookPersistenceService = bookPersistenceService;
    }

    /**
     * Replaces the current book with a new one.
     * <p>
     * <b>Concurrency:</b> The entire mutation is guarded by {@code synchronized (lock)}
     * because HTTP sessions are shared across concurrent requests from the same browser.
     * <p>
     * <b>Where:</b> Required for session-isolation per {@code PROJECT_RULES.md} Section 1.
     *
     * @param book the new book data; must not be null and must contain content
     */
    public void setBook(BookData book) {
        LoggingUtils.enter(log, "setBook", "fileName", book != null ? book.fileName() : "null");

        // Validate input
        if (book == null) {
            LoggingUtils.validationFailure(log, "book", "must not be null", "null");
            throw new IllegalArgumentException("Book must not be null");
        }
        if (!book.hasContent()) {
            LoggingUtils.validationFailure(log, "book", "has no content", book.fileName());
            throw new IllegalArgumentException("Book must have content");
        }

        synchronized (lock) {
            log.info("Setting book in session | fileName={} | textLen={}",
                book.fileName(), book.fullText().length());

            this.currentBook = book;
            this.currentBookId = null;
            this.detectedLanguage = detectLanguage(book.fullText());

            log.info("Book set successfully | language={} | pageCount={}",
                detectedLanguage, getPageCount());
        }

        LoggingUtils.exit(log, "setBook", "language", detectedLanguage);
    }

    /**
     * Sets the current user ID for the session.
     * <p>
     * <b>What:</b> Updates the user ID used for database persistence operations.
     * <p>
     * <b>Why:</b> Required for scoping book saves and reading progress to the
     * authenticated user.
     * <p>
     * <b>Where:</b> Set during login and used by persistence methods.
     *
     * @param userId the user ID; must be a positive number
     */
    /**
     * Sets the current book ID for the session.
     *
     * @param bookId the persisted book entity ID; may be null if not yet saved
     */
    public void setCurrentBookId(Long bookId) {
        this.currentBookId = bookId;
    }

    /**
     * Returns the current persisted book entity ID.
     *
     * @return the book ID, or null if the book has not been saved yet
     */
    public Long getCurrentBookId() {
        return currentBookId;
    }

    /**
     * Loads a book from the database into the current session by its persisted ID.
     * <p>
     * <b>What:</b> Rebuilds {@link BookData} from the {@link com.bookassistant.entity.Book}
     * record and sets it as the active book for this session.
     * <p>
     * <b>Why:</b> After an async upload the book exists in PostgreSQL but the
     * session-scoped {@code BookSession} is still empty; this method lets the
     * frontend explicitly bind the saved book to the current session.
     * <p>
     * <b>Where:</b> Called from a request-scoped controller after upload completes.
     */
    public void loadBookById(Long bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException("bookId must not be null");
        }

        com.bookassistant.entity.Book entity = bookPersistenceService.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found | bookId=" + bookId));

        String fullText = entity.getFullText();
        List<PageData> pages;
        if (fullText == null || fullText.isBlank()) {
            pages = new ArrayList<>();
            fullText = "";
        } else {
            String[] parts = fullText.split("\n\n", -1);
            pages = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                String trimmed = parts[i].trim();
                if (!trimmed.isEmpty()) {
                    pages.add(new PageData(i + 1, trimmed));
                }
            }
            if (pages.isEmpty()) {
                pages.add(new PageData(1, fullText.trim()));
            }
        }

        BookData bookData = new BookData(
                entity.getFileName(),
                pages,
                fullText
        );

        synchronized (lock) {
            this.currentBook = bookData;
            this.currentBookId = entity.getId();
            this.detectedLanguage = detectLanguage(fullText);
        }
        log.info("Book loaded into session | bookId={} | fileName={} | pages={} | hasText={}", entity.getId(), entity.getFileName(), pages.size(), fullText != null && !fullText.isBlank());
    }

    /**
     * Ensures the session has text for the requested page range.
     * <p>
     * <b>What:</b> If the current book has no pages (on-demand extraction flow),
     * triggers extraction for the requested range and loads the result into the session.
     * <p>
     * <b>Why:</b> Avoids failing silently when a user requests AI features on a book
     * that was uploaded without full-text extraction.
     * <p>
     * <b>Where:</b> Called by AI controllers before processing page ranges.
     */
    public void ensurePagesLoaded(long bookId, int startPage, int endPage, OnDemandExtractionService extractionService) throws IOException {
        synchronized (lock) {
            if (currentBook != null && currentBook.pages() != null && !currentBook.pages().isEmpty()) {
                boolean hasCoverage = currentBook.pages().stream()
                        .anyMatch(p -> p.pageNumber() >= startPage && p.pageNumber() <= endPage);
                if (hasCoverage) {
                    return;
                }
            }
        }

        BookData extracted = extractionService.extractPages(bookId, startPage, endPage);
        setBook(extracted);
        this.currentBookId = bookId;
        log.info("On-demand extraction loaded into session | bookId={} | pages={}", bookId, extracted.getPageCount());
    }

    public Long resolveBookId(Long userId) {
        if (currentBookId != null) return currentBookId;
        if (currentBook == null) return null;

            List<com.bookassistant.entity.Book> existing = bookPersistenceService.findByUserIdAndTitle(userId, currentBook.fileName());
        if (!existing.isEmpty()) {
            currentBookId = existing.get(0).getId();
        } else {
            com.bookassistant.entity.Book entity = new com.bookassistant.entity.Book();
            entity.setUserId(userId);
            entity.setTitle(currentBook.fileName());
            entity.setFileName(currentBook.fileName());
            entity.setFilePath("");
            entity.setPageCount(getPageCount());
            entity.setFullText(currentBook.fullText());
            entity.setLanguage(getDetectedLanguage());
            com.bookassistant.entity.Book saved = bookPersistenceService.save(entity);
            currentBookId = saved.getId();
        }
        return currentBookId;
    }

    /**
     * Sets the current user ID for the session.
     * <p>
     * <b>What:</b> Updates the user ID used for database persistence operations.
     * <p>
     * <b>Why:</b> Required for scoping book saves and reading progress to the
     * authenticated user.
     * <p>
     * <b>Where:</b> Set during login and used by persistence methods.
     *
     * @param userId the user ID; must be a positive number
     */
    public void setUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
        this.userId = userId;
        log.debug("Session user ID set | userId={}", userId);
    }

    /**
     * Returns the current user ID.
     * <p>
     * <b>What:</b> Returns the user ID associated with this session.
     * <p>
     * <b>Why:</b> Required for database operations that need to scope data to a user.
     * <p>
     * <b>Where:</b> Used by persistence and sync services.
     *
     * @return the current user ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * This name was chosen because this function persists the current book to the database.
     * Creates or updates the Book record associated with the current user.
     */
    public void saveCurrentBook() {
        if (userId == null || userId <= 0) {
            log.warn("Cannot save book: no user ID set");
            return;
        }
        if (currentBook == null || !currentBook.hasContent()) {
            log.warn("Cannot save book: no book loaded");
            return;
        }

        com.bookassistant.entity.Book entity = new com.bookassistant.entity.Book();
        entity.setUserId(userId);
        entity.setTitle(currentBook.fileName());
        entity.setFileName(currentBook.fileName());
        entity.setFilePath("");
        entity.setPageCount(getPageCount());
        entity.setFullText(currentBook.fullText());
        entity.setLanguage(getDetectedLanguage());

            com.bookassistant.entity.Book saved = bookPersistenceService.save(entity);
        this.currentBookId = saved.getId();
        log.info("Book saved to database | userId={} | fileName={} | bookId={}", userId, currentBook.fileName(), saved.getId());
    }

    /**
     * Persists extracted text to an existing book record and triggers RAG indexing.
     * <p>
     * <b>What:</b> Updates the {@code fullText} of an already-persisted book and
     * calls {@link BookPersistenceService#save(Book)} to trigger RAG indexing.
     * <p>
     * <b>Why:</b> On-demand extraction (e.g. QA, search) extracts text into the
     * session but must also persist it so RAG can index the content for future requests.
     * <p>
     * <b>Where:</b> Called by AI controllers after on-demand extraction completes.
     */
    public void persistExtractedText(Long bookId, String fullText) {
        if (bookId == null || fullText == null || fullText.isBlank()) {
            log.warn("Cannot persist extracted text: missing bookId or empty text");
            return;
        }

        com.bookassistant.entity.Book entity = bookPersistenceService.findById(bookId).orElse(null);
        if (entity == null) {
            log.warn("Cannot persist extracted text: book not found | bookId={}", bookId);
            return;
        }

        entity.setFullText(fullText);
        bookPersistenceService.save(entity);
        log.info("Persisted extracted text for RAG indexing | bookId={} | textLen={}", bookId, fullText.length());
    }

    /**
     * Persists the current reading progress for the active book.
     * <p>
     * <b>What:</b> Saves or updates the current page number in the
     * {@code reading_progress} table for the current user and book.
     * <p>
     * <b>Why:</b> Allows users to resume reading from their last position.
     * <p>
     * <b>Where:</b> Required for the reading-progress feature.
     *
     * @param currentPage the current page number
     */
    public void saveReadingProgress(int currentPage) {
        if (userId == null || userId <= 0) {
            log.warn("Cannot save progress: no user ID set");
            return;
        }
        if (currentBook == null) {
            log.warn("Cannot save progress: no book loaded");
            return;
        }

        com.bookassistant.entity.Book book = bookPersistenceService.findByUserId(userId)
            .stream()
            .filter(b -> b.getTitle().equals(currentBook.fileName()))
            .findFirst()
            .orElse(null);

        if (book == null) {
            log.warn("Cannot save progress: book not found in database");
            return;
        }

        com.bookassistant.entity.ReadingProgress progress = bookPersistenceService
            .findProgressByUserIdAndBookId(userId, book.getId())
            .orElseGet(() -> {
                com.bookassistant.entity.ReadingProgress p = new com.bookassistant.entity.ReadingProgress();
                p.setUserId(userId);
                p.setBookId(book.getId());
                return p;
            });

        progress.setCurrentPage(currentPage);
        progress.setTotalPages(getPageCount());
        bookPersistenceService.saveProgress(progress);

        log.info("Reading progress saved | userId={} | bookId={} | page={}",
            userId, book.getId(), currentPage);
    }

    /**
     * Loads the saved reading progress for the current book.
     * <p>
     * <b>What:</b> Returns the last-saved current page from the database, or {@code 1}
     * if no progress is recorded.
     * <p>
     * <b>Why:</b> Allows the UI to restore the user's reading position.
     * <p>
     * <b>Where:</b> Required for session restoration on app load.
     *
     * @return the current page number
     */
    public int loadReadingProgress() {
        if (userId == null || userId <= 0 || currentBook == null) {
            return 1;
        }

        return bookPersistenceService.findByUserId(userId).stream()
            .filter(b -> b.getTitle().equals(currentBook.fileName()))
            .findFirst()
            .flatMap(book -> bookPersistenceService.findProgressByUserIdAndBookId(userId, book.getId()))
            .map(com.bookassistant.entity.ReadingProgress::getCurrentPage)
            .orElse(1);
    }
    public Optional<BookData> getBook() {
        synchronized (lock) {
            return Optional.ofNullable(currentBook);
        }
    }

    /**
     * This name was chosen because this function checks if a book is loaded.
     * Thread-safe with synchronization for consistent reads.
     */
    public boolean hasBook() {
        synchronized (lock) {
            return currentBook != null;
        }
    }

    /**
     * This name was chosen because this function gets the detected language.
     * Returns the language code (ar/en) detected from book content.
     */
    public String getDetectedLanguage() {
        synchronized (lock) {
            return detectedLanguage != null ? detectedLanguage : "en";
        }
    }

    /**
     * This name was chosen because this function gets the page count.
     * Returns the number of pages in the current book.
     */
    public int getPageCount() {
        synchronized (lock) {
            if (currentBook == null || currentBook.pages() == null) {
                return 0;
            }
            return currentBook.pages().size();
        }
    }

    /**
     * Extracts text from the page range [startPage, endPage] (1-based, inclusive).
     * <p>
     * <b>Concurrency:</b> The page list reference is captured inside the synchronized
     * block to prevent {@link java.util.ConcurrentModificationException} if another
     * request replaces the book during iteration.
     * <p>
     * <b>Where:</b> Required for all study endpoints that operate on a page subset.
     *
     * @param startPage 1-based start page
     * @param endPage   1-based end page (inclusive)
     * @return concatenated page texts separated by double newlines
     */
    public String extractTextFromPageRange(int startPage, int endPage) {
        LoggingUtils.enter(log, "extractTextFromPageRange",
            "range", startPage + "-" + endPage);

        if (startPage < 1) {
            LoggingUtils.validationFailure(log, "startPage", "must be >= 1", startPage);
            throw new IllegalArgumentException("Start page must be >= 1");
        }
        if (endPage < startPage) {
            LoggingUtils.validationFailure(log, "endPage", "must be >= startPage", endPage);
            throw new IllegalArgumentException("End page must be >= start page");
        }

        synchronized (lock) {
            BookData book = this.currentBook;
            log.info("extractTextFromPageRange | hasBook={} | pagesNull={} | totalPagesInSession={}", 
                book != null, book != null && book.pages() == null, book != null ? book.pages().size() : 0);

            if (book == null) {
                log.warn("Cannot extract text: no book in session");
                return "";
            }

            if (book.pages() == null || book.pages().isEmpty()) {
                log.warn("Cannot extract text: book has no pages");
                return "";
            }

            List<PageData> pagesCopy = List.copyOf(book.pages());
            log.info("Pages in session | total={} | pageNumbers={}", 
                pagesCopy.size(), 
                pagesCopy.stream().map(PageData::pageNumber).toList());

            String result = pagesCopy.stream()
                    .filter(p -> p.pageNumber() >= startPage && p.pageNumber() <= endPage)
                    .map(p -> "--- [Page " + p.pageNumber() + "] ---\n" + p.text())
                    .collect(Collectors.joining("\n\n"));

            log.info("Extracted text length: {}", result.length());
            LoggingUtils.exit(log, "extractTextFromPageRange", "textLen", result.length());
            return result;
        }
    }

    /**
     * This name was chosen because this function clears the session state.
     * Thread-safe with synchronization for consistent writes.
     */
    public void clear() {
        synchronized (lock) {
            log.info("Clearing book session");
            this.currentBook = null;
            this.currentBookId = null;
            this.detectedLanguage = null;
        }
    }

    /**
     * Returns the actual PDF page count from the persisted book entity.
     * <p>
     * <b>What:</b> Queries the database for the book's pageCount field, which
     * is set during upload even when no text has been extracted yet.
     * <p>
     * <b>Why:</b> The session BookData may have zero pages in the on-demand
     * extraction flow, but the PDF itself has a known page count stored in
     * the database.
     *
     * @return the PDF page count, or 0 if not found
     */
    public int getPdfPageCount() {
        Long bookId = resolveBookId(getUserId());
        if (bookId == null) {
            return 0;
        }
        return bookPersistenceService.findById(bookId)
                .map(com.bookassistant.entity.Book::getPageCount)
                .orElse(0);
    }

    /**
     * This name was chosen because this function detects the language of text.
     * Analyzes character patterns to determine if text is Arabic or English.
     */
    private String detectLanguage(String text) {
        if (isEmpty(text)) {
            log.warn("Detecting language for empty text, defaulting to 'en'");
            return "en";
        }

        // Sample first 1000 characters for efficiency
        String sample = text.length() > 1000 ? text.substring(0, 1000) : text;
        int arabicChars = 0;
        int latinChars = 0;

        for (char c : sample.toCharArray()) {
            if (ARABIC_PATTERN.matcher(String.valueOf(c)).find()) {
                arabicChars++;
            } else if (Character.isLetter(c) && c < 128) {
                latinChars++;
            }
        }

        String result = arabicChars >= latinChars ? "ar" : "en";
        log.debug("Language detection | arabic={} | latin={} | result={}",
            arabicChars, latinChars, result);
        return result;
    }

    /**
     * This name was chosen because this function checks if a string is empty.
     * Delegates to centralized StringUtils for consistency.
     */
    private boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }
}


