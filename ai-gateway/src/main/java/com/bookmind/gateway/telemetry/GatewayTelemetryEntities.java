package com.bookmind.gateway.telemetry;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Telemetry record for AI Gateway requests.
 * Stored in PostgreSQL for monitoring and debugging.
 */
@Entity
@Table(name = "gateway_telemetry", schema = "telemetry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

/**
 * Repository for gateway telemetry.
 */
interface GatewayTelemetryRepository extends org.springframework.data.jpa.repository.JpaRepository<GatewayTelemetryRecord, Long> {
    List<GatewayTelemetryRecord> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<GatewayTelemetryRecord> findByProviderOrderByCreatedAtDesc(String provider);
    List<GatewayTelemetryRecord> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatusAndCreatedAtBetween(String status, Instant start, Instant end);
}