package com.bookassistant.controller.book;

import com.bookassistant.model.BookData;
import com.bookassistant.entity.Book;
import com.bookassistant.repository.BookRepository;
import com.bookassistant.service.file.BookStorageService;
import com.bookassistant.service.file.PersistentBookStore;
import com.bookassistant.service.infrastructure.ExtractionCacheService;
import com.bookassistant.service.infrastructure.ExtractionProgressService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.util.ErrorMessageSanitizer;
import com.bookassistant.session.BookSession;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.controller.web.ModelAttributeBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles PDF book uploads and starts background extraction.
 * <p>
 * <b>What:</b> Accepts a multipart {@code .pdf} file, validates it, checks the
 * extraction cache, and either returns a cached result immediately or starts a
 * background extraction job. Returns a Job ID to the frontend for SSE progress tracking.
 * <p>
 * <b>Why:</b> Enables real progress reporting for large PDFs by decoupling the
 * upload request from the extraction process.
 * <p>
 * <b>Where:</b> Entry point for new book content; enforces file-type and size
 * limits before expensive parsing begins.
 */
@RestController
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private static final String COMPONENT_UPLOAD = "UploadController";

    private final BookSession bookSession;
    private final PersistentBookStore databaseBookStore;
    private final BookRepository bookRepository;
    private final MessageSource messageSource;
    private final ExtractionCacheService extractionCacheService;
    private final ExtractionProgressService extractionProgressService;
    private final RuntimeLogService runtimeLogService;
    private final BookStorageService bookStorageService;

    public UploadController(BookSession bookSession,
                            PersistentBookStore databaseBookStore, BookRepository bookRepository,
                            MessageSource messageSource,
                            ExtractionCacheService extractionCacheService, ExtractionProgressService extractionProgressService,
                            RuntimeLogService runtimeLogService, BookStorageService bookStorageService) {
        this.bookSession = bookSession;
        this.databaseBookStore = databaseBookStore;
        this.bookRepository = bookRepository;
        this.messageSource = messageSource;
        this.extractionCacheService = extractionCacheService;
        this.extractionProgressService = extractionProgressService;
        this.runtimeLogService = runtimeLogService;
        this.bookStorageService = bookStorageService;
        log.info("UploadController initialized");
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadBookFile(@RequestParam("file") MultipartFile file,
                                                 HttpSession session) {
        LoggingUtils.enter(log, "uploadBookFile", "fileName", file.getOriginalFilename());
        long startTime = System.currentTimeMillis();

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"" + messageSource.getMessage("upload.file.empty", null, LocaleContextHolder.getLocale()) + "\"}");
            }

            String fileName = file.getOriginalFilename();
            if (ModelAttributeBinder.isEmpty(fileName)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"" + messageSource.getMessage("upload.file.invalid.name", null, LocaleContextHolder.getLocale()) + "\"}");
            }

            if (!fileName.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"" + messageSource.getMessage("upload.file.not.pdf", null, LocaleContextHolder.getLocale()) + "\"}");
            }

            byte[] bytes = file.getBytes();
            String hash = extractionCacheService.computeHash(bytes);
            Long userId = (Long) session.getAttribute("userId");

            // Check cache first
            var cached = extractionCacheService.load(hash);
            if (cached != null && cached.hasContent()) {
                log.info("Cache hit on upload | hash={} | fileName={}", hash, fileName);
                String jobId = UUID.randomUUID().toString();
                runtimeLogService.info(COMPONENT_UPLOAD, jobId, "Cache hit", Map.of(
                        "hash", hash,
                        "fileName", fileName,
                        "pages", cached.pageCount()
                ));
                extractionProgressService.startJob(jobId, fileName, file.getSize(), "pix2text");

                Book cachedBook = databaseBookStore.saveBook(userId, fileName, fileName, cached.pageCount(), cached.fullText());
                Path pdfPath = bookStorageService.resolveBookPdfPath(cachedBook.getId(), fileName);
                Files.createDirectories(pdfPath.getParent());
                Files.write(pdfPath, bytes);
                cachedBook.setFilePath(pdfPath.toString());
                bookRepository.save(cachedBook);

                extractionProgressService.cacheHit(jobId, new BookData(cached.fileName(), cached.pages(), cached.fullText()));
                return ResponseEntity.ok("{\"jobId\":\"" + jobId + "\",\"status\":\"CACHED\",\"bookId\":" + cachedBook.getId() + "}");
            }

            // Start background save
            String jobId = UUID.randomUUID().toString();
            extractionProgressService.startJob(jobId, fileName, file.getSize(), "pix2text");
            runtimeLogService.info(COMPONENT_UPLOAD, jobId, "Background save started", Map.of(
                    "fileName", fileName,
                    "size", file.getSize()
            ));

            // Run save to disk in background
            saveBookToDisk(file, jobId, userId);

            log.info("Upload accepted | jobId={} | fileName={} | size={} | duration={}ms",
                    jobId, fileName, file.getSize(), System.currentTimeMillis() - startTime);

            return ResponseEntity.accepted()
                    .body("{\"jobId\":\"" + jobId + "\",\"status\":\"PENDING\"}");

        } catch (IllegalArgumentException e) {
            log.warn("Upload validation failed | error={}", e.getMessage());
            String sanitized = ErrorMessageSanitizer.sanitize(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"" + sanitized + "\"}");

        } catch (Exception e) {
            LoggingUtils.exception(log, "uploadBookFile", e);
            log.error("Upload failed | error={}", e.getMessage());

            ErrorMessageSanitizer.ErrorCategory category = ErrorMessageSanitizer.classify(e);
            String sanitized = ErrorMessageSanitizer.sanitize(e.getMessage());
            String errorKey = switch (category) {
                case TIMEOUT -> "upload.extraction.timeout";
                case SERVICE_UNAVAILABLE -> "upload.extraction.service.unavailable";
                case INVALID_INPUT -> "upload.extraction.invalid.input";
                case EMPTY_RESULT -> "upload.extraction.empty.result";
                case DEPENDENCY_ERROR -> "upload.extraction.dependency.error";
                default -> "upload.extraction.failed";
            };
            String errorMsg = messageSource.getMessage(errorKey, new Object[]{sanitized}, LocaleContextHolder.getLocale());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + errorMsg + "\"}");
        }
    }

    /**
     * Saves the uploaded PDF to the PDF source directory and creates a database record.
     * <p>
     * <b>What:</b> Stores the raw PDF bytes in the configured PDF source directory
     * (app.pdf-source-dir) and creates a metadata record in the database.
     * No text extraction happens here — extraction is triggered on-demand by AI features.
     * <p>
     * <b>Why:</b> Keeps the upload fast and avoids extracting books the user never reads.
     * Extraction only happens when the user selects a page range and requests an AI service.
     */
    private void saveBookToDisk(MultipartFile file, String jobId, Long userId) {
        byte[] bytes;
        String fileName;
        try {
            bytes = file.getBytes();
            fileName = file.getOriginalFilename();
        } catch (IOException e) {
            log.error("Failed to read upload | jobId={} | error={}", jobId, e.getMessage());
            extractionProgressService.failJob(jobId, e.getMessage());
            return;
        }

        new Thread(() -> {
            try {
                extractionProgressService.updateProgress(jobId, 0, 0, "Saving PDF");

                Book savedBook = databaseBookStore.saveBook(userId, fileName, fileName, 0, "");
                runtimeLogService.info(COMPONENT_UPLOAD, jobId, "Book metadata saved", Map.of(
                        "userId", userId,
                        "bookId", savedBook.getId(),
                        "fileName", fileName
                ));

                Path pdfPath = bookStorageService.resolveBookPdfPath(savedBook.getId(), fileName);
                Files.createDirectories(pdfPath.getParent());
                Files.write(pdfPath, bytes);

                savedBook.setFilePath(pdfPath.toString());
                int pageCount = getPageCount(bytes);
                savedBook.setPageCount(pageCount);
                savedBook.setFullText("");
                bookRepository.save(savedBook);

                runtimeLogService.info(COMPONENT_UPLOAD, jobId, "PDF saved to source directory", Map.of(
                        "bookId", savedBook.getId(),
                        "path", pdfPath.toString(),
                        "size", bytes.length,
                        "pageCount", pageCount
                ));

                extractionProgressService.completeJob(jobId, new BookData(fileName, List.of(), ""), savedBook.getId());
                log.info("Book upload completed | jobId={} | bookId={} | pages={}", jobId, savedBook.getId(), pageCount);
            } catch (Exception e) {
                log.error("Book upload failed | jobId={} | error={}", jobId, e.getMessage());
                extractionProgressService.failJob(jobId, e.getMessage());
            }
        }, "upload-" + jobId).start();
    }

    private int getPageCount(byte[] pdfBytes) {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            log.warn("Failed to get page count from PDF", e);
            return 0;
        }
    }

    @PostMapping("/books/{bookId}/load")
    public ResponseEntity<String> loadBookIntoSession(@PathVariable Long bookId, HttpSession session) {
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"Login required\"}");
            }

            bookSession.loadBookById(bookId);
            log.info("Book loaded into session | bookId={} | userId={}", bookId, userId);
            return ResponseEntity.ok("{\"status\":\"LOADED\"}");
        } catch (Exception e) {
            log.error("Failed to load book into session | bookId={} | error={}", bookId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
