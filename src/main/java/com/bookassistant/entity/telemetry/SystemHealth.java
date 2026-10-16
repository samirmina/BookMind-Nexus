package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a system health check entry.
 * <p>
 * <b>What:</b> Tracks the health status of system components
 * (database, AI API, file system, cache) over time.
 * <p>
 * <b>Why:</b> Enables identification of degradation patterns and
 * correlation between system health and user-facing errors.
 * <p>
 * <b>Where:</b> System health registry in the telemetry schema.
 */
@Entity
@Table(name = "system_health", schema = "telemetry")
public class SystemHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt = LocalDateTime.now();

    @Column(nullable = false, length = 100)
    private String component;  // database, ai_api, file_system, cache

    @Column(nullable = false, length = 20)
    private String status;  // healthy, degraded, down

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(columnDefinition = "JSONB")
    private String details;

    @Column(name = "error_id")
    private Long errorId;

    @PrePersist
    protected void onCreate() {
        checkedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Long getErrorId() { return errorId; }
    public void setErrorId(Long errorId) { this.errorId = errorId; }
}
