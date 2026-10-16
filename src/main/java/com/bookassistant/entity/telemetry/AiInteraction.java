package com.bookassistant.entity.telemetry;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents an AI interaction log entry.
 * <p>
 * <b>What:</b> Tracks every AI request with prompt hash, response length,
 * language, duration, and success/failure status.
 * <p>
 * <b>Why:</b> Enables analysis of AI performance, error patterns, and
 * output quality over time.
 * <p>
 * <b>Where:</b> AI interaction log in the telemetry schema.
 */
@Entity
@Table(name = "ai_interactions", schema = "telemetry")
public class AiInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "book_id")
    private Long bookId;

    @Column(nullable = false, length = 50)
    private String tabUsed;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "response_length")
    private Integer responseLength;

    @Column(length = 10)
    private String language;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "tokens_estimate")
    private Integer tokensEstimate;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(name = "error_id")
    private Long errorId;

    @Column(name = "book_output_id")
    private Long bookOutputId;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column
    private Double temperature;

    @Column(columnDefinition = "JSONB")
    private String context;

    @PrePersist
    protected void onCreate() {
        occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getTabUsed() { return tabUsed; }
    public void setTabUsed(String tabUsed) { this.tabUsed = tabUsed; }
    public String getPromptHash() { return promptHash; }
    public void setPromptHash(String promptHash) { this.promptHash = promptHash; }
    public Integer getResponseLength() { return responseLength; }
    public void setResponseLength(Integer responseLength) { this.responseLength = responseLength; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Integer getTokensEstimate() { return tokensEstimate; }
    public void setTokensEstimate(Integer tokensEstimate) { this.tokensEstimate = tokensEstimate; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Long getErrorId() { return errorId; }
    public void setErrorId(Long errorId) { this.errorId = errorId; }
    public Long getBookOutputId() { return bookOutputId; }
    public void setBookOutputId(Long bookOutputId) { this.bookOutputId = bookOutputId; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}
