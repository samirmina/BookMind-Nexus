package com.bookassistant.service.infrastructure;

import com.bookassistant.model.BookData;
import com.bookassistant.service.file.BookExtractionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class ExtractionProgressService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionProgressService.class);
    private static final String COMPONENT_SERVICE = "ExtractionProgressService";
    private final BookExtractionJobRegistry registry;
    private final RuntimeLogService runtimeLogService;
    private final ExecutorService sendExecutor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(100),
            r -> new Thread(r, "sse-sender-" + System.currentTimeMillis()),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public ExtractionProgressService(BookExtractionJobRegistry registry, RuntimeLogService runtimeLogService) {
        this.registry = registry;
        this.runtimeLogService = runtimeLogService;
    }

    public BookExtractionJob startJob(String jobId, String fileName, long fileSize, String extractionMode) {
        BookExtractionJob job = BookExtractionJob.create(jobId, fileName, fileSize, extractionMode);
        registry.register(job);
        runtimeLogService.info(COMPONENT_SERVICE, jobId, "Job started", Map.of(
                "fileName", fileName,
                "fileSize", fileSize,
                "mode", extractionMode
        ));
        sendEvent(jobId);
        log.info("Extraction job started | jobId={} | fileName={} | mode={}", jobId, fileName, extractionMode);
        return job;
    }

    public void updateProgress(String jobId, int processedPages, int totalPages, String currentPhase) {
        BookExtractionJob current = registry.get(jobId);
        if (current == null) {
            log.warn("Progress update for unknown job | jobId={}", jobId);
            return;
        }
        BookExtractionJob updated = current.withProgress(processedPages, totalPages, currentPhase);
        registry.update(updated);
        runtimeLogService.info(COMPONENT_SERVICE, jobId, "Progress updated", Map.of(
                "processedPages", processedPages,
                "totalPages", totalPages,
                "phase", currentPhase
        ));
        sendEvent(jobId);
    }

    public void completeJob(String jobId, BookData result) {
        BookExtractionJob current = registry.get(jobId);
        if (current == null) {
            log.warn("Complete request for unknown job | jobId={}", jobId);
            return;
        }
        BookExtractionJob updated = current.completed(result);
        registry.update(updated);
        runtimeLogService.info(COMPONENT_SERVICE, jobId, "Job completed", Map.of(
                "pages", result != null ? result.getPageCount() : 0
        ));
        sendEvent(jobId);
        log.info("Extraction job completed | jobId={} | pages={}", jobId, result != null ? result.getPageCount() : 0);
    }

    public void completeJob(String jobId, BookData result, Long bookId) {
        BookExtractionJob current = registry.get(jobId);
        if (current == null) {
            log.warn("Complete request for unknown job | jobId={}", jobId);
            return;
        }
        BookExtractionJob updated = current.completed(result).withBookId(bookId);
        registry.update(updated);
        runtimeLogService.info(COMPONENT_SERVICE, jobId, "Job completed", Map.of(
                "pages", result != null ? result.getPageCount() : 0,
                "bookId", bookId
        ));
        sendEvent(jobId);
        log.info("Extraction job completed | jobId={} | pages={} | bookId={}", jobId, result != null ? result.getPageCount() : 0, bookId);
    }

    public void cacheHit(String jobId, BookData result) {
        BookExtractionJob current = registry.get(jobId);
        if (current == null) {
            log.warn("Cache hit request for unknown job | jobId={}", jobId);
            return;
        }
        BookExtractionJob updated = current.cached(result);
        registry.update(updated);
        runtimeLogService.info(COMPONENT_SERVICE, jobId, "Cache hit", Map.of(
                "pages", result != null ? result.getPageCount() : 0
        ));
        sendEvent(jobId);
        log.info("Extraction cache hit | jobId={} | pages={}", jobId, result != null ? result.getPageCount() : 0);
    }

    public void failJob(String jobId, String errorMessage) {
        BookExtractionJob current = registry.get(jobId);
        if (current == null) {
            log.warn("Fail request for unknown job | jobId={}", jobId);
            return;
        }
        BookExtractionJob updated = current.failed(errorMessage);
        registry.update(updated);
        runtimeLogService.error(COMPONENT_SERVICE, jobId, "Job failed", Map.of(
                "errorMessage", errorMessage
        ));
        sendEvent(jobId);
        log.error("Extraction job failed | jobId={} | error={}", jobId, errorMessage);
    }

    public void cleanupJob(String jobId) {
        registry.remove(jobId);
        log.debug("Job cleaned up | jobId={}", jobId);
    }

    public BookExtractionJob getJob(String jobId) {
        return registry.get(jobId);
    }

    public void registerEmitter(String jobId, SseEmitter emitter) {
        registry.registerEmitter(jobId, emitter);
        BookExtractionJob current = registry.get(jobId);

        // Replay any events that were queued before the emitter connected
        var pending = registry.drainPendingEvents(jobId);
        if (pending != null && !pending.isEmpty()) {
            log.info("Replaying {} pending SSE events | jobId={}", pending.size(), jobId);
            int delayMs = 200;
            for (Map<String, Object> event : pending) {
                sendSync(emitter, event, jobId);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (current != null) {
            sendEvent(jobId);
        }
        scheduleHeartbeat(jobId, emitter);
    }

    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void shutdown() {
        sendExecutor.shutdownNow();
    }

    private void scheduleHeartbeat(String jobId, SseEmitter emitter) {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                BookExtractionJob job = registry.get(jobId);
                if (job == null || job.status() == BookExtractionJob.JobStatus.COMPLETED
                        || job.status() == BookExtractionJob.JobStatus.FAILED) {
                    return;
                }
                emitter.send(SseEmitter.event().name("heartbeat").data(Map.of(
                        "jobId", jobId,
                        "timestamp", System.currentTimeMillis()
                )));
            } catch (Exception e) {
                log.debug("Heartbeat failed | jobId={} | error={}", jobId, e.getMessage());
            }
        }, 15, 15, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void unregisterEmitter(String jobId) {
        registry.unregisterEmitter(jobId);
    }

    private void sendEvent(String jobId) {
        SseEmitter emitter = registry.getEmitter(jobId);
        BookExtractionJob job = registry.get(jobId);
        if (job == null) return;

        Map<String, Object> payload = Map.of(
                "jobId", job.jobId(),
                "status", job.status().name(),
                "fileName", job.fileName(),
                "processedPages", job.processedPages(),
                "totalPages", job.totalPages(),
                "phase", job.currentPhase(),
                "errorMessage", job.errorMessage() == null ? "" : job.errorMessage(),
                "bookId", job.bookId() == null ? "" : job.bookId()
        );

        if (emitter == null) {
            // No emitter connected yet — queue the event for replay when one registers
            registry.queueEvent(jobId, payload);
            return;
        }

        sendExecutor.submit(() -> {
            try {
                emitter.send(SseEmitter.event().data(payload));
                if (job.status() == BookExtractionJob.JobStatus.COMPLETED
                        || job.status() == BookExtractionJob.JobStatus.FAILED) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.debug("Failed to send SSE event | jobId={} | error={}", jobId, e.getMessage());
                registry.unregisterEmitter(jobId);
            }
        });
    }

    private void sendSync(SseEmitter emitter, Map<String, Object> payload, String jobId) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (Exception e) {
            log.debug("Failed to replay pending SSE event | jobId={} | error={}", jobId, e.getMessage());
        }
    }
}
