package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.User;
import com.bookassistant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized runtime log service for the upload/extraction pipeline.
 * <p>
 * <b>What:</b> Records timestamped events from every component involved in
 * PDF upload, cache lookup, extraction, SSE streaming, and frontend feedback.
 * <p>
 * <b>Why:</b> Makes it possible to reconstruct the exact sequence of events
 * across async boundaries and diagnose why progress does or does not reach
 * the browser.
 * <p>
 * <b>Where:</b> Used by {@link UploadController}, {@link ExtractionProgressController},
 * {@link ExtractionProgressService}, and {@link FileParserService}.
 */
@Service
public class RuntimeLogService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeLogService.class);

    public enum Level {
        INFO, WARN, ERROR
    }

    public record Entry(
            String id,
            String component,
            String jobId,
            Level level,
            String message,
            Map<String, Object> details,
            long timestampMillis
    ) {}

    private static final String COMPONENT_UPLOAD = "UploadController";
    private static final String COMPONENT_PROGRESS = "ExtractionProgressController";
    private static final String COMPONENT_SERVICE = "ExtractionProgressService";
    private static final String COMPONENT_PARSER = "FileParserService";

    private final Map<String, List<Entry>> entriesByJob = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    public RuntimeLogService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void info(String component, String jobId, String message) {
        log(component, jobId, Level.INFO, message, Map.of());
    }

    public void info(String component, String jobId, String message, Map<String, Object> details) {
        log(component, jobId, Level.INFO, message, details);
    }

    public void warn(String component, String jobId, String message) {
        log(component, jobId, Level.WARN, message, Map.of());
    }

    public void warn(String component, String jobId, String message, Map<String, Object> details) {
        log(component, jobId, Level.WARN, message, details);
    }

    public void error(String component, String jobId, String message) {
        log(component, jobId, Level.ERROR, message, Map.of());
    }

    public void error(String component, String jobId, String message, Map<String, Object> details) {
        log(component, jobId, Level.ERROR, message, details);
    }

    public List<Entry> getEntries(String jobId) {
        return entriesByJob.getOrDefault(jobId, Collections.emptyList());
    }

    public List<Entry> getRecentEntries(int maxEntries) {
        List<Entry> all = new ArrayList<>();
        for (List<Entry> list : entriesByJob.values()) {
            all.addAll(list);
        }
        all.sort(Comparator.comparingLong(Entry::timestampMillis));
        if (all.size() > maxEntries) {
            return all.subList(all.size() - maxEntries, all.size());
        }
        return all;
    }

    private void log(String component, String jobId, Level level, String message, Map<String, Object> details) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Entry entry = new Entry(id, component, jobId, level, message, new LinkedHashMap<>(details), now);

        entriesByJob.computeIfAbsent(jobId, k -> new ArrayList<>()).add(entry);

        if (level == Level.ERROR) {
            log.error("[runtime-log] component={} | jobId={} | message={} | details={}", component, jobId, message, details);
        } else if (level == Level.WARN) {
            log.warn("[runtime-log] component={} | jobId={} | message={}", component, jobId, message);
        } else {
            log.info("[runtime-log] component={} | jobId={} | message={}", component, jobId, message);
        }
    }
}
