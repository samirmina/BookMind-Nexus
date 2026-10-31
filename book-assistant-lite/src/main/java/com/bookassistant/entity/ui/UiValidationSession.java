package com.bookassistant.entity.ui;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Logs a UI validation session run.
 */
@Entity
@Table(name = "ui_validation_sessions")
public class UiValidationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column
    private Integer pagesValidated = 0;

    @Column
    private Integer anomaliesFound = 0;

    @Column
    private Integer screenshotsCaptured = 0;

    @Column
    private Boolean overallPassed;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getPagesValidated() { return pagesValidated; }
    public void setPagesValidated(Integer pagesValidated) { this.pagesValidated = pagesValidated; }
    public Integer getAnomaliesFound() { return anomaliesFound; }
    public void setAnomaliesFound(Integer anomaliesFound) { this.anomaliesFound = anomaliesFound; }
    public Integer getScreenshotsCaptured() { return screenshotsCaptured; }
    public void setScreenshotsCaptured(Integer screenshotsCaptured) { this.screenshotsCaptured = screenshotsCaptured; }
    public Boolean getOverallPassed() { return overallPassed; }
    public void setOverallPassed(Boolean overallPassed) { this.overallPassed = overallPassed; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
