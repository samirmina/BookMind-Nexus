package com.bookassistant.controller.book;

import com.bookassistant.service.infrastructure.ExtractionProgressService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exposes Server-Sent Events for extraction progress.
 * <p>
 * <b>What:</b> Provides a streaming endpoint that the frontend can connect to
 * via {@code EventSource} to receive real-time progress updates for a given job.
 * <p>
 * <b>Why:</b> Enables the UI to show a live progress bar and phase indicator
 * during long-running PDF extraction without polling.
 * <p>
 * <b>Where:</b> Consumed by the upload flow in {@code index.html}.
 */
@RestController
public class ExtractionProgressController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionProgressController.class);
    private static final String COMPONENT_PROGRESS = "ExtractionProgressController";

    private final ExtractionProgressService extractionProgressService;
    private final RuntimeLogService runtimeLogService;

    public ExtractionProgressController(ExtractionProgressService extractionProgressService, RuntimeLogService runtimeLogService) {
        this.extractionProgressService = extractionProgressService;
        this.runtimeLogService = runtimeLogService;
    }

    @GetMapping(value = "/upload/progress/{jobId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String jobId) {
        log.info("SSE connection opened | jobId={}", jobId);
        runtimeLogService.info(COMPONENT_PROGRESS, jobId, "SSE connection opened");
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        extractionProgressService.registerEmitter(jobId, emitter);

        emitter.onCompletion(() -> {
            runtimeLogService.info(COMPONENT_PROGRESS, jobId, "SSE emitter completed");
            extractionProgressService.unregisterEmitter(jobId);
        });
        emitter.onTimeout(() -> {
            runtimeLogService.warn(COMPONENT_PROGRESS, jobId, "SSE emitter timed out");
            extractionProgressService.unregisterEmitter(jobId);
        });
        emitter.onError(e -> {
            runtimeLogService.error(COMPONENT_PROGRESS, jobId, "SSE emitter error", Map.of(
                    "error", e != null ? e.getMessage() : "unknown"
            ));
            extractionProgressService.unregisterEmitter(jobId);
        });

        return emitter;
    }
}
