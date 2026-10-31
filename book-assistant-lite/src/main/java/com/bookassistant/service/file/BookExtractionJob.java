package com.bookassistant.service.file;

import com.bookassistant.model.BookData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the state and progress of a single PDF extraction job.
 * <p>
 * <b>What:</b> Tracks a background extraction from upload to completion,
 * including page-level progress, status, and final result.
 * <p>
 * <b>Why:</b> Enables real progress reporting via SSE while large PDFs
 * are being extracted in the background.
 * <p>
 * <b>Where:</b> Created by {@link com.bookassistant.controller.book.UploadController},
 * executed by {@link com.bookassistant.service.file.FileParserService},
 * consumed by the SSE endpoint and frontend progress UI.
 */
public record BookExtractionJob(
        String jobId,
        String fileName,
        long fileSize,
        String extractionMode,
        JobStatus status,
        int totalPages,
        int processedPages,
        int progressPercent,
        String currentPhase,
        String errorMessage,
        BookData result,
        Instant createdAt,
        Instant completedAt,
        Long bookId
) {

    /**
     * Lifecycle states for an extraction job.
     */
    public enum JobStatus {
        PENDING,       // Job created, waiting to start
        IN_PROGRESS,   // Extraction running
        COMPLETED,     // Extraction finished successfully
        FAILED,        // Extraction failed
        CACHED         // Result served from cache instantly
    }

    /**
     * Factory for a new job before extraction begins.
     */
    public static BookExtractionJob create(String jobId, String fileName, long fileSize, String extractionMode) {
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                JobStatus.PENDING,
                0,
                0,
                0,
                "Initializing",
                null,
                null,
                Instant.now(),
                null,
                null
        );
    }

    public BookExtractionJob withProgress(int processedPages, int totalPages, String currentPhase) {
        int percent = totalPages > 0 ? (int) Math.round((processedPages * 100.0) / totalPages) : 0;
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                status,
                totalPages,
                processedPages,
                percent,
                currentPhase,
                errorMessage,
                result,
                createdAt,
                completedAt,
                bookId
        );
    }

    public BookExtractionJob completed(BookData result) {
        Instant now = Instant.now();
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                JobStatus.COMPLETED,
                totalPages,
                processedPages,
                100,
                "Completed",
                null,
                result,
                createdAt,
                now,
                bookId
        );
    }

    public BookExtractionJob cached(BookData result) {
        Instant now = Instant.now();
        int total = result != null ? result.getPageCount() : 0;
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                JobStatus.CACHED,
                total,
                total,
                100,
                "Loaded from cache",
                null,
                result,
                createdAt,
                now,
                bookId
        );
    }

    public BookExtractionJob failed(String errorMessage) {
        Instant now = Instant.now();
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                JobStatus.FAILED,
                totalPages,
                processedPages,
                progressPercent,
                "Failed",
                errorMessage,
                result,
                createdAt,
                now,
                bookId
        );
    }

    public BookExtractionJob inProgress(int totalPages) {
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                JobStatus.IN_PROGRESS,
                totalPages,
                0,
                0,
                "Extracting",
                errorMessage,
                result,
                createdAt,
                completedAt,
                bookId
        );
    }

    public BookExtractionJob withBookId(Long bookId) {
        return new BookExtractionJob(
                jobId,
                fileName,
                fileSize,
                extractionMode,
                status,
                totalPages,
                processedPages,
                progressPercent,
                currentPhase,
                errorMessage,
                result,
                createdAt,
                completedAt,
                bookId
        );
    }
}
