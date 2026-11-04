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

    // Require at least 100 MB free before accepting new uploads.
    private static final long MIN_FREE_SPACE_BYTES = 100L * 1024 * 1024;

    private final BookRepository bookRepository;
    private final BookPersistenceService bookPersistenceService;
    private final Path pdfSourceRoot;

    public BookStorageService(BookRepository bookRepository, BookPersistenceService bookPersistenceService,
                              @Value("${app.pdf-source-dir:external/pdfs}") String pdfSourceDir) {
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

    /**
     * Verifies that the PDF source volume has enough free space to accept an upload
     * of the given size. Throws {@link IllegalStateException} if there is insufficient
     * space, preventing the disk from being filled by large uploads.
     *
     * @param requiredBytes the size of the upload about to be written
     */
    public void ensureDiskSpace(long requiredBytes) {
        try {
            long freeSpace = pdfSourceRoot.toFile().getUsableSpace();
            long needed = requiredBytes + MIN_FREE_SPACE_BYTES;
            if (freeSpace < needed) {
                throw new IllegalStateException(
                    "Insufficient disk space | free=" + freeSpace + " bytes | required=" + needed + " bytes");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Unable to check disk space | root={} | error={}", pdfSourceRoot, e.getMessage());
        }
    }

    public Path resolveBookPdfPath(Long bookId, String fileName) {
        return pdfSourceRoot.resolve("book_" + bookId + "_" + fileName);
    }

    public boolean isPdfSourcePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(pdfSourceRoot);
    }

    /**
     * Loads the raw PDF bytes for a book from disk, verifying the book belongs to
     * the given user (IDOR protection).
     *
     * @param bookId the book ID
     * @param userId the owning user ID
     * @return the PDF bytes, or empty if not found or not owned by the user
     */
    public Optional<byte[]> loadPdf(Long bookId, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId).orElse(null);
        if (book == null || book.getFilePath() == null) {
            return Optional.empty();
        }

        try {
            Path pdfPath = Paths.get(book.getFilePath());
            if (!isPdfSourcePath(pdfPath)) {
                log.warn("PDF path escapes source directory | bookId={} | path={}", bookId, pdfPath);
                return Optional.empty();
            }
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
     * Only deletes files within the configured PDF source directory, and only
     * when the book belongs to the given user (IDOR protection).
     *
     * @param bookId the book ID
     * @param userId the owning user ID
     */
    public void deletePdf(Long bookId, Long userId) {
        Book book = bookRepository.findByIdAndUserId(bookId, userId).orElse(null);
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
