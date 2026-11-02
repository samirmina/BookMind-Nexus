package com.bookmind.gateway.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Telemetry service for AI Gateway.
 * Records all gateway requests for monitoring and debugging.
 */
@Service
public class GatewayTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(GatewayTelemetryService.class);

    private final GatewayTelemetryRepository telemetryRepository;

    public GatewayTelemetryService(GatewayTelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    /**
     * Telemetry request data holder with builder.
     */
    public static class TelemetryRequest {
        private String taskId;
        private String sessionId;
        private String userId;
        private String taskType;
        private String provider;
        private String model;
        private String status;
        private Long durationMs;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer errorCount;
        private String errorMessage;
        private String requestParams;
        private String responseMetadata;

        private TelemetryRequest() {}

        public static Builder builder() {
            return new Builder();
        }

        public String getTaskId() { return taskId; }
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public String getTaskType() { return taskType; }
        public String getProvider() { return provider; }
        public String getModel() { return model; }
        public String getStatus() { return status; }
        public Long getDurationMs() { return durationMs; }
        public Integer getInputTokens() { return inputTokens; }
        public Integer getOutputTokens() { return outputTokens; }
        public Integer getErrorCount() { return errorCount; }
        public String getErrorMessage() { return errorMessage; }
        public String getRequestParams() { return requestParams; }
        public String getResponseMetadata() { return responseMetadata; }

        public static class Builder {
            private String taskId;
            private String sessionId;
            private String userId;
            private String taskType;
            private String provider;
            private String model;
            private String status;
            private Long durationMs;
            private Integer inputTokens;
            private Integer outputTokens;
            private Integer errorCount;
            private String errorMessage;
            private String requestParams;
            private String responseMetadata;

            private Builder() {}

            public Builder taskId(String taskId) { this.taskId = taskId; return this; }
            public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder taskType(String taskType) { this.taskType = taskType; return this; }
            public Builder provider(String provider) { this.provider = provider; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder status(String status) { this.status = status; return this; }
            public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }
            public Builder inputTokens(Integer inputTokens) { this.inputTokens = inputTokens; return this; }
            public Builder outputTokens(Integer outputTokens) { this.outputTokens = outputTokens; return this; }
            public Builder errorCount(Integer errorCount) { this.errorCount = errorCount; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public Builder requestParams(String requestParams) { this.requestParams = requestParams; return this; }
            public Builder responseMetadata(String responseMetadata) { this.responseMetadata = responseMetadata; return this; }

            public TelemetryRequest build() {
                TelemetryRequest request = new TelemetryRequest();
                request.taskId = taskId;
                request.sessionId = sessionId;
                request.userId = userId;
                request.taskType = taskType;
                request.provider = provider;
                request.model = model;
                request.status = status;
                request.durationMs = durationMs;
                request.inputTokens = inputTokens;
                request.outputTokens = outputTokens;
                request.errorCount = errorCount;
                request.errorMessage = errorMessage;
                request.requestParams = requestParams;
                request.responseMetadata = responseMetadata;
                return request;
            }
        }
    }

    /**
     * Record a gateway request for telemetry.
     */
    @Transactional
    public void recordRequest(TelemetryRequest request) {
        try {
            GatewayTelemetryRecord record = new GatewayTelemetryRecord();
            record.setTaskId(request.getTaskId());
            record.setSessionId(request.getSessionId());
            record.setUserId(request.getUserId());
            record.setTaskType(request.getTaskType());
            record.setProvider(request.getProvider());
            record.setModel(request.getModel());
            record.setStatus(request.getStatus());
            record.setDurationMs(request.getDurationMs());
            record.setInputTokens(request.getInputTokens());
            record.setOutputTokens(request.getOutputTokens());
            record.setErrorCount(request.getErrorCount() != null ? request.getErrorCount() : 0);
            record.setErrorMessage(request.getErrorMessage());
            record.setRequestParams(request.getRequestParams());
            record.setResponseMetadata(request.getResponseMetadata());

            telemetryRepository.save(record);
            log.debug("Recorded telemetry | taskId={} | provider={} | status={}",
                request.getTaskId(), request.getProvider(), request.getStatus());

        } catch (Exception e) {
            log.error("Failed to record telemetry", e);
        }
    }

    /**
     * Get telemetry records for a session.
     */
    public List<GatewayTelemetryRecord> getSessionTelemetry(String sessionId) {
        return telemetryRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    /**
     * Get telemetry for a provider.
     */
    public List<GatewayTelemetryRecord> getProviderTelemetry(String provider) {
        return telemetryRepository.findByProviderOrderByCreatedAtDesc(provider);
    }

    /**
     * Get error records.
     */
    public List<GatewayTelemetryRecord> getErrors() {
        return telemetryRepository.findByStatusOrderByCreatedAtDesc("error");
    }

    /**
     * Get telemetry stats for a time range.
     */
    public long getErrorCount(Instant start, Instant end) {
        return telemetryRepository.countByStatusAndCreatedAtBetween("error", start, end);
    }

    /**
     * Get telemetry stats for a time range.
     */
    public long getSuccessCount(Instant start, Instant end) {
        return telemetryRepository.countByStatusAndCreatedAtBetween("success", start, end);
    }
}