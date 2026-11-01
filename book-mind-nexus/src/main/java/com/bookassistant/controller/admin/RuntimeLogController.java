package com.bookassistant.controller.admin;

import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.service.infrastructure.RuntimeLogService.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes runtime logs for the upload/extraction pipeline.
 * <p>
 * <b>What:</b> Provides a read-only endpoint to inspect {@link RuntimeLogService}
 * entries by job ID or as recent global entries.
 * <p>
 * <b>Why:</b> Enables diagnosing SSE and extraction issues without attaching
 * a debugger or tailing server logs.
 */
@RestController
public class RuntimeLogController {

    private static final Logger log = LoggerFactory.getLogger(RuntimeLogController.class);

    private final RuntimeLogService runtimeLogService;

    public RuntimeLogController(RuntimeLogService runtimeLogService) {
        this.runtimeLogService = runtimeLogService;
    }

    @GetMapping("/admin/runtime-logs")
    public ResponseEntity<Map<String, Object>> getRuntimeLogs(
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "200") int limit) {
        try {
            if (jobId != null && !jobId.isBlank()) {
                List<Entry> entries = runtimeLogService.getEntries(jobId);
                return ResponseEntity.ok(Map.of(
                        "jobId", jobId,
                        "entries", entries,
                        "count", entries.size()
                ));
            }

            List<Entry> entries = runtimeLogService.getRecentEntries(Math.min(limit, 1000));
            return ResponseEntity.ok(Map.of(
                    "entries", entries,
                    "count", entries.size()
            ));
        } catch (Exception e) {
            log.error("Failed to load runtime logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
