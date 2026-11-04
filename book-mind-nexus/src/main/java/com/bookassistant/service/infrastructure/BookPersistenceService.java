package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.Book;
import com.bookassistant.entity.ReadingProgress;
import com.bookassistant.model.BookData;
import com.bookassistant.repository.BookRepository;
import com.bookassistant.repository.ReadingProgressRepository;
import com.bookassistant.service.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Persistence service for book and reading progress data.
 * <p>
 * <b>What:</b> Provides methods to save, load, and delete books and reading progress.
 * Also triggers RAG indexing when books are saved or deleted.
 * <p>
 * <b>Why:</b> {@link com.bookassistant.session.BookSession} must not import repositories
 * directly per project rules. This service wraps {@link BookRepository} and
 * {@link ReadingProgressRepository} to maintain the session → service → repository
 * layer separation.
 */
@Service
public class BookPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(BookPersistenceService.class);

    private final BookRepository bookRepository;
    private final ReadingProgressRepository readingProgressRepository;
    private final RagService ragService;

    public BookPersistenceService(BookRepository bookRepository, ReadingProgressRepository readingProgressRepository, RagService ragService) {
        this.bookRepository = bookRepository;
        this.readingProgressRepository = readingProgressRepository;
        this.ragService = ragService;
    }

    public Optional<Book> findById(Long id) {
        return bookRepository.findById(id);
    }

    public List<Book> findByUserId(Long userId) {
        return bookRepository.findByUserId(userId);
    }

    public List<Book> findByUserIdAndTitle(Long userId, String title) {
        return bookRepository.findByUserIdAndTitle(userId, title);
    }

    public Book save(Book book) {
        Book saved = bookRepository.save(book);
        log.info("Book saved | bookId={} | title={}", saved.getId(), saved.getTitle());

        // Trigger RAG indexing asynchronously (non-blocking)
        if (saved.getFullText() != null && !saved.getFullText().isBlank()) {
            indexBookAsync(saved);
        }

        return saved;
    }

    /**
     * Indexes a book for RAG search in a background thread.
     * <p>
     * <b>Why:</b> Keeps the upload response fast by deferring expensive
     * embedding generation to a separate thread. Failures here do not
     * block the upload or cause the book save to be rolled back.
     */
    @Async
    public void indexBookAsync(Book book) {
        book.setIndexingStatus("PENDING");
        bookRepository.save(book);
        try {
            ragService.indexBook(book);
            book.setIndexingStatus("COMPLETED");
            book.setIndexingError(null);
            bookRepository.save(book);
            log.info("RAG indexing completed | bookId={}", book.getId());
        } catch (Exception e) {
            book.setIndexingStatus("FAILED");
            book.setIndexingError(e.getMessage());
            bookRepository.save(book);
            log.error("RAG indexing failed for book | bookId={} | error={}", book.getId(), e.getMessage());
        }
    }

    public void deleteById(Long id) {
        Book book = bookRepository.findById(id).orElse(null);
        if (book != null) {
            ragService.deleteBookChunks(book.getId());
            bookRepository.deleteById(id);
            log.info("Book deleted | bookId={}", id);
        }
    }

    public Optional<ReadingProgress> findProgressByUserIdAndBookId(Long userId, Long bookId) {
        return readingProgressRepository.findByUserIdAndBookId(userId, bookId);
    }

    public ReadingProgress saveProgress(ReadingProgress progress) {
        return readingProgressRepository.save(progress);
    }
}
