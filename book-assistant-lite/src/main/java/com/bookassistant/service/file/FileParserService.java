package com.bookassistant.service.file;

import com.bookassistant.model.BookData;
import com.bookassistant.model.ExtractionCacheEntry;
import com.bookassistant.service.infrastructure.ExtractionCacheService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileParserService {

    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);
    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024;

    private final MessageSource messageSource;
    private final BookStorageService bookStorageService;
    private final ExtractionCacheService extractionCacheService;
    private final RuntimeLogService runtimeLogService;
    private static final String COMPONENT_PARSER = "FileParserService";

    public FileParserService(MessageSource messageSource, BookStorageService bookStorageService, ExtractionCacheService extractionCacheService, RuntimeLogService runtimeLogService) {
        this.messageSource = messageSource;
        this.bookStorageService = bookStorageService;
        this.extractionCacheService = extractionCacheService;
        this.runtimeLogService = runtimeLogService;
        log.info("FileParserService initialized");
    }

    private static Map<String, Object> safeMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object value = keyValues[i + 1];
            map.put(key, value != null ? value : "null");
        }
        return map;
    }

    /**
     * Parses raw PDF bytes and stores them for on-demand extraction.
     * No full-text extraction is performed at upload time.
     */
    public BookData parsePdfBytes(byte[] bytes, String fileName, ProgressCallback progress) throws IOException {
        LoggingUtils.enter(log, "parsePdfBytes",
                "fileName", fileName,
                "fileSize", bytes != null ? bytes.length : 0);
        runtimeLogService.info(COMPONENT_PARSER, "unknown", "parsePdfBytes entered", safeMap(
                "fileName", fileName,
                "bytesLength", bytes != null ? bytes.length : 0
        ));

        long startTime = System.currentTimeMillis();

        if (bytes == null || bytes.length == 0) {
            runtimeLogService.warn(COMPONENT_PARSER, "unknown", "Empty bytes received");
            throw new IllegalArgumentException(messageSource.getMessage("upload.file.empty", null, LocaleContextHolder.getLocale()));
        }
        runtimeLogService.info(COMPONENT_PARSER, "unknown", "Bytes non-null check passed", safeMap());

        String hash = extractionCacheService.computeHash(bytes);
        runtimeLogService.info(COMPONENT_PARSER, "unknown", "Hash computed", safeMap("hash", hash));

        ExtractionCacheEntry cached = extractionCacheService.load(hash);
        if (cached != null && cached.hasContent()) {
            runtimeLogService.info(COMPONENT_PARSER, "unknown", "Cache hit", safeMap(
                    "hash", hash,
                    "pages", cached.pageCount()
            ));
            log.info("Loaded cached extraction | hash={} | fileName={} | pages={} | textLen={}",
                    hash, cached.fileName(), cached.pageCount(), cached.fullText().length());
            return new BookData(cached.fileName(), cached.pages(), cached.fullText());
        }
        runtimeLogService.info(COMPONENT_PARSER, "unknown", "Cache miss", safeMap("hash", hash));

        // Store raw PDF bytes for on-demand extraction
        bookStorageService.storePdf(bytes, fileName);

        // Return empty BookData - extraction happens on-demand
        BookData bookData = new BookData(fileName, List.of(), "");
        
        long duration = System.currentTimeMillis() - startTime;
        LoggingUtils.fileOperation(log, "PARSE", fileName, 0, duration);

        log.info("PDF uploaded successfully | fileName={} | size={} | duration={}ms",
                fileName, bytes.length, duration);

        return bookData;
    }

    /**
     * Parses a PDF multipart file into a {@link BookData} object.
     * Results are cached under {@code cache/extractions/} by PDF content hash.
     */
    public BookData parsePdfFile(MultipartFile file, ProgressCallback progress) throws IOException {
        return parsePdfBytes(file.getBytes(), file.getOriginalFilename(), progress);
    }

    /**
     * Backward-compatible overload without progress reporting.
     */
    public BookData parsePdfFile(MultipartFile file) throws IOException {
        return parsePdfFile(file, null);
    }
}
