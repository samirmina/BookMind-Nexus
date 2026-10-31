package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a tracked error in the system.
 * <p>
 * <b>What:</b> Deduplicates errors by hash and tracks occurrence count,
 * first/last seen, and resolution status.
 * <p>
 * <b>Why:</b> Allows the project to identify recurring issues and whether
 * fixes actually resolve them or if regressions occur.
 * <p>
 * <b>Where:</b> Central error registry in the telemetry schema.
 */
@Entity
@Table(name = "errors", schema = "telemetry")
public class TelemetryError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt = LocalDateTime.now();

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    @Column(nullable = false, length = 64)
    private String errorHash;

    @Column(nullable = false, length = 100)
    private String errorType;

    @Column(nullable = false, length = 500)
    private String errorMessage;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "affected_component", length = 200)
    private String affectedComponent;

    @Column(nullable = false, length = 20)
    private String status = "open";

    @Column(nullable = false, length = 20)
    private String severity = "major";

    @Column(nullable = false)
    private Integer occurrenceCount = 1;

    @Column(nullable = false)
    private Boolean isRegression = false;

    @Column(name = "regression_of")
    private Long regressionOf;

    @Column(columnDefinition = "TEXT")
    private String fixDescription;

    @Column(name = "fix_commit", length = 40)
    private String fixCommit;

    @Column(name = "fixed_at")
    private LocalDateTime fixedAt;

    @PrePersist
    protected void onCreate() {
        firstSeenAt = LocalDateTime.now();
        lastSeenAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastSeenAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getErrorHash() { return errorHash; }
    public void setErrorHash(String errorHash) { this.errorHash = errorHash; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getAffectedComponent() { return affectedComponent; }
    public void setAffectedComponent(String affectedComponent) { this.affectedComponent = affectedComponent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public Boolean getIsRegression() { return isRegression; }
    public void setIsRegression(Boolean isRegression) { this.isRegression = isRegression; }
    public Long getRegressionOf() { return regressionOf; }
    public void setRegressionOf(Long regressionOf) { this.regressionOf = regressionOf; }
    public String getFixDescription() { return fixDescription; }
    public void setFixDescription(String fixDescription) { this.fixDescription = fixDescription; }
    public String getFixCommit() { return fixCommit; }
    public void setFixCommit(String fixCommit) { this.fixCommit = fixCommit; }
    public LocalDateTime getFixedAt() { return fixedAt; }
    public void setFixedAt(LocalDateTime fixedAt) { this.fixedAt = fixedAt; }
}
