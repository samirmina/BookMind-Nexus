package com.bookmind.gateway.telemetry;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Telemetry record for AI Gateway requests.
 * Stored in PostgreSQL for monitoring and debugging.
 */
@Entity
@Table(name = "gateway_telemetry", schema = "telemetry")
public class GatewayTelemetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // success, error, timeout

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "error_count")
    private Integer errorCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_params", columnDefinition = "JSONB")
    private String requestParams;

    @Column(name = "response_metadata", columnDefinition = "JSONB")
    private String responseMetadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GatewayTelemetryRecord() {}

    public GatewayTelemetryRecord(Long id, String taskId, String sessionId, String userId,
                                   String taskType, String provider, String model, String status,
                                   Long durationMs, Integer inputTokens, Integer outputTokens,
                                   Integer errorCount, String errorMessage, String requestParams,
                                   String responseMetadata, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.taskType = taskType;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.durationMs = durationMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.errorCount = errorCount;
        this.errorMessage = errorMessage;
        this.requestParams = requestParams;
        this.responseMetadata = responseMetadata;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public String getResponseMetadata() { return responseMetadata; }
    public void setResponseMetadata(String responseMetadata) { this.responseMetadata = responseMetadata; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}