package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents an audit entry for account linking operations.
 * <p>
 * <b>What:</b> Tracks every account linking attempt with full state before/after,
 * errors, and resolution type.
 * <p>
 * <b>Why:</b> Account linking is a complex issue that has caused multiple bugs
 * (duplicate key, white-label errors). This audit trail allows precise identification
 * of failure patterns and successful resolution paths.
 * <p>
 * <b>Where:</b> Account linking audit table in the telemetry schema.
 */
@Entity
@Table(name = "account_link_audit", schema = "telemetry")
public class AccountLinkAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 20)
    private String action;  // attempt, success, failure, unlink

    @Column(columnDefinition = "JSONB")
    private String preState;

    @Column(columnDefinition = "JSONB")
    private String postState;

    @Column(name = "error_id")
    private Long errorId;

    @Column(columnDefinition = "JSONB")
    private String errorDetails;

    @Column(length = 50)
    private String resolution;  // created_new, updated_existing, idempotent_skip, blocked

    @Column(name = "linked_account_id")
    private Long linkedAccountId;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPreState() { return preState; }
    public void setPreState(String preState) { this.preState = preState; }
    public String getPostState() { return postState; }
    public void setPostState(String postState) { this.postState = postState; }
    public Long getErrorId() { return errorId; }
    public void setErrorId(Long errorId) { this.errorId = errorId; }
    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public Long getLinkedAccountId() { return linkedAccountId; }
    public void setLinkedAccountId(Long linkedAccountId) { this.linkedAccountId = linkedAccountId; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
