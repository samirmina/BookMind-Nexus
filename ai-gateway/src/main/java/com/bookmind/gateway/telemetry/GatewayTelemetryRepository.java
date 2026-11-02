package com.bookmind.gateway.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for gateway telemetry.
 */
interface GatewayTelemetryRepository extends JpaRepository<GatewayTelemetryRecord, Long> {
    List<GatewayTelemetryRecord> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<GatewayTelemetryRecord> findByProviderOrderByCreatedAtDesc(String provider);
    List<GatewayTelemetryRecord> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatusAndCreatedAtBetween(String status, Instant start, Instant end);
}