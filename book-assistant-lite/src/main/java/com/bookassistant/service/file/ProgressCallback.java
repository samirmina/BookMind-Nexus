package com.bookassistant.service.file;

/**
 * Callback for reporting extraction progress.
 * <p>
 * <b>What:</b> Functional interface used by {@link FileParserService} to report
 * phase changes and page-level progress during PDF extraction.
 * <p>
 * <b>Why:</b> Decouples progress reporting from the extraction logic, allowing
 * the caller to decide how to handle progress updates (e.g., update a registry,
 * emit SSE, log, etc.).
 */
public interface ProgressCallback {
    void onProgress(String phase, int processedPages, int totalPages);
}
