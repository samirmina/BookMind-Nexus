package com.bookassistant.service.infrastructure;

import com.bookassistant.service.file.BookExtractionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry for active PDF extraction jobs.
 * <p>
 * <b>What:</b> Stores {@link BookExtractionJob} instances by job ID and provides
 * thread-safe access for progress updates and SSE delivery.
 * <p>
 * <b>Why:</b> Extraction runs in background threads; the registry is the bridge
 * between the worker thread and the SSE endpoint that streams progress to the UI.
 * <p>
 * <b>Where:</b> Used by {@link com.bookassistant.service.file.FileParserService}
 * to report progress, and by the SSE controller to stream updates.
 */
@Service
public class BookExtractionJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(BookExtractionJobRegistry.class);
    private final Map<String, BookExtractionJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> pendingEvents = new ConcurrentHashMap<>();

    /**
     * Registers a new job.
     */
    public void register(BookExtractionJob job) {
        jobs.put(job.jobId(), job);
        log.info("Job registered | jobId={} | fileName={}", job.jobId(), job.fileName());
    }

    /**
     * Returns the current state of a job, or {@code null} if not found.
     */
    public BookExtractionJob get(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Atomically replaces a job with an updated snapshot.
     */
    public void update(BookExtractionJob updatedJob) {
        jobs.put(updatedJob.jobId(), updatedJob);
    }

    /**
     * Removes a job from the registry after completion or failure.
     */
    public void remove(String jobId) {
        jobs.remove(jobId);
        emitters.remove(jobId);
        pendingEvents.remove(jobId);
    }

    /**
     * Number of active jobs in the registry.
     */
    public int size() {
        return jobs.size();
    }

    /**
     * Registers an SSE emitter for a job.
     */
    public void registerEmitter(String jobId, SseEmitter emitter) {
        emitters.put(jobId, emitter);
    }

    /**
     * Unregisters an SSE emitter for a job.
     */
    public void unregisterEmitter(String jobId) {
        emitters.remove(jobId);
    }

    /**
     * Returns the SSE emitter for a job, or {@code null} if not found.
     */
    public SseEmitter getEmitter(String jobId) {
        return emitters.get(jobId);
    }

    /**
     * Queues an SSE event payload for a job that has no connected emitter yet.
     * Queued events are replayed in order when an emitter registers.
     */
    public void queueEvent(String jobId, Map<String, Object> event) {
        pendingEvents.computeIfAbsent(jobId, k -> new ConcurrentLinkedQueue<>()).add(event);
    }

    /**
     * Drains and returns all pending events for a job, or {@code null} if none.
     */
    public ConcurrentLinkedQueue<Map<String, Object>> drainPendingEvents(String jobId) {
        return pendingEvents.remove(jobId);
    }
}
