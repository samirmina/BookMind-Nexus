package com.bookassistant.service.file;

import com.bookassistant.entity.Book;
import com.bookassistant.repository.BookRepository;
import com.bookassistant.service.infrastructure.BookPersistenceService;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersistentBookStore {

    private static final Logger log = LoggerFactory.getLogger(PersistentBookStore.class);

    private final BookRepository bookRepository;
    private final BookPersistenceService bookPersistenceService;

    public PersistentBookStore(BookRepository bookRepository, BookPersistenceService bookPersistenceService) {
        this.bookRepository = bookRepository;
        this.bookPersistenceService = bookPersistenceService;
    }

    public Book saveBook(Long userId, String title, String fileName, int pageCount, String fullText) {
        LoggingUtils.enter(log, "saveBook", "userId", userId, "fileName", fileName);

        String safeName = fileName != null && fileName.contains(".")
            ? fileName.substring(0, fileName.lastIndexOf('.'))
            : fileName;
        safeName = safeName.toLowerCase().replaceAll("[^a-z0-9\\-_]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");

        Book book = new Book();
        book.setUserId(userId);
        book.setTitle(title != null ? title : fileName);
        book.setFileName(fileName);
        String folder = "user_" + userId + "/books/" + safeName;
        book.setBookFolder(folder);
        book.setFilePath(folder + "/" + fileName);
        book.setPageCount(pageCount);
        book.setFullText(fullText);
        book.setTags(new String[]{"upload"});

        Book saved = bookPersistenceService.save(book);
        log.info("Book saved to database | userId={} | bookId={} | fileName={} | folder={}",
            userId, saved.getId(), fileName, book.getBookFolder());
        return saved;
    }

    public List<Book> findUserBooks(Long userId) {
        return bookRepository.findByUserId(userId);
    }

    public Optional<Book> findById(Long bookId) {
        return bookRepository.findById(bookId);
    }

    public void deleteBook(Long bookId) {
        LoggingUtils.enter(log, "deleteBook", "bookId", bookId);
        bookRepository.deleteById(bookId);
        log.info("Book deleted | bookId={}", bookId);
    }
}
