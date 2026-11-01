package com.bookmind.gateway.telemetry;

import com.bookmind.gateway.telemetry.GatewayTelemetryEntities.GatewayTelemetryRecord;
import com.bookmind.gateway.telemetry.GatewayTelemetryEntities.GatewayTelemetryRepository;
import lombok.Builder;
import lombok.Data;
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
    
    @Data
    @Builder
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
    }
    
    /**
     * Record a gateway request for telemetry.
     */
    @Transactional
    public void recordRequest(TelemetryRequest request) {
        try {
            GatewayTelemetryRecord record = GatewayTelemetryRecord.builder()
                .taskId(request.getTaskId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .taskType(request.getTaskType())
                .provider(request.getProvider())
                .model(request.getModel())
                .status(request.getStatus())
                .durationMs(request.getDurationMs())
                .inputTokens(request.getInputTokens())
                .outputTokens(request.getOutputTokens())
                .errorCount(request.getErrorCount() != null ? request.getErrorCount() : 0)
                .errorMessage(request.getErrorMessage())
                .requestParams(request.getRequestParams())
                .responseMetadata(request.getResponseMetadata())
                .build();
            
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