package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a tracked procedure execution outcome.
 * <p>
 * <b>What:</b> Logs input, outcome, result, and any errors for every significant
 * procedure in the system.
 * <p>
 * <b>Why:</b> Enables analysis of success rates, failure patterns, and
 * performance across different procedures.
 * <p>
 * <b>Where:</b> Procedure outcome registry in the telemetry schema.
 */
@Entity
@Table(name = "procedures", schema = "telemetry")
public class TelemetryProcedure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();

    @Column(nullable = false, length = 200)
    private String procedureName;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "book_id")
    private Long bookId;

    @Column(columnDefinition = "JSONB")
    private String inputSummary;

    @Column(nullable = false, length = 20)
    private String outcome;  // success, failure, partial, timeout

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_id")
    private Long errorId;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(columnDefinition = "JSONB")
    private String context;

    @PrePersist
    protected void onCreate() {
        executedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public String getProcedureName() { return procedureName; }
    public void setProcedureName(String procedureName) { this.procedureName = procedureName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getInputSummary() { return inputSummary; }
    public void setInputSummary(String inputSummary) { this.inputSummary = inputSummary; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public Long getErrorId() { return errorId; }
    public void setErrorId(Long errorId) { this.errorId = errorId; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}
