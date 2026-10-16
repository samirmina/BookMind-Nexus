package com.bookassistant.service.file;

import com.bookassistant.entity.Book;
import com.bookassistant.repository.BookRepository;
import com.bookassistant.service.infrastructure.BookPersistenceService;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class BookStorageService {

    private static final Logger log = LoggerFactory.getLogger(BookStorageService.class);

    private final BookRepository bookRepository;
    private final BookPersistenceService bookPersistenceService;
    private final Path pdfSourceRoot;

    public BookStorageService(BookRepository bookRepository, BookPersistenceService bookPersistenceService,
                              @Value("${app.pdf-source-dir:D:/book-assistant-lite/external/pdfs}") String pdfSourceDir) {
        this.bookRepository = bookRepository;
        this.bookPersistenceService = bookPersistenceService;
        this.pdfSourceRoot = Paths.get(pdfSourceDir).toAbsolutePath().normalize();
        initializeStorage();
    }

    private void initializeStorage() {
        try {
            Files.createDirectories(pdfSourceRoot);
            log.info("PDF source directory initialized | root={}", pdfSourceRoot);
        } catch (IOException e) {
            log.error("Failed to initialize PDF source directory", e);
            throw new IllegalStateException("Cannot initialize PDF source directory", e);
        }
    }

    public Path getPdfSourceRoot() {
        return pdfSourceRoot;
    }

    public Path resolveBookPdfPath(Long bookId, String fileName) {
        return pdfSourceRoot.resolve("book_" + bookId + "_" + fileName);
    }

    public boolean isPdfSourcePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(pdfSourceRoot);
    }

    /**
     * Loads the raw PDF bytes for a book from disk.
     *
     * @param bookId the book ID
     * @return the PDF bytes, or empty if not found
     */
    public Optional<byte[]> loadPdf(Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null || book.getFilePath() == null) {
            return Optional.empty();
        }

        try {
            Path pdfPath = Paths.get(book.getFilePath());
            if (Files.exists(pdfPath)) {
                byte[] bytes = Files.readAllBytes(pdfPath);
                log.info("PDF loaded from disk | bookId={} | path={} | size={}", bookId, pdfPath, bytes.length);
                return Optional.of(bytes);
            }
        } catch (IOException e) {
            log.error("Failed to load PDF from disk | bookId={} | path={}", bookId, book.getFilePath(), e);
        }
        return Optional.empty();
    }

    /**
     * Stores raw PDF bytes for a book on disk.
     *
     * @param pdfBytes the PDF bytes
     * @param fileName the original file name
     * @return the path where the PDF was stored
     * @throws IOException if the PDF cannot be written
     */
    public Path storePdf(byte[] pdfBytes, String fileName) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF bytes must not be null or empty");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be null or blank");
        }

        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path pdfPath = pdfSourceRoot.resolve("upload_" + System.currentTimeMillis() + "_" + safeFileName);
        Files.write(pdfPath, pdfBytes);
        log.info("PDF stored | path={} | size={}", pdfPath, pdfBytes.length);
        return pdfPath;
    }

    /**
     * Deletes the PDF file from the source directory when a book is deleted.
     * Only deletes files within the configured PDF source directory.
     *
     * @param bookId the book ID
     */
    public void deletePdf(Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null || book.getFilePath() == null) {
            return;
        }

        try {
            Path pdfPath = Paths.get(book.getFilePath());
            if (Files.exists(pdfPath) && isPdfSourcePath(pdfPath)) {
                Files.deleteIfExists(pdfPath);
                log.info("PDF deleted from source directory | bookId={} | path={}", bookId, pdfPath);
            }
        } catch (IOException e) {
            log.error("Failed to delete PDF from source directory | bookId={} | path={}", bookId, book.getFilePath(), e);
        }
    }
}
