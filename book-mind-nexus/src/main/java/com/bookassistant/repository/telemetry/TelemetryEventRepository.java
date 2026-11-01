package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for telemetry events.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the event log table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for telemetry events.
 */
public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, Long> {
    List<TelemetryEvent> findByCategoryOrderByOccurredAtDesc(String category);
    List<TelemetryEvent> findBySeverityOrderByOccurredAtDesc(String severity);
    List<TelemetryEvent> findByUserIdOrderByOccurredAtDesc(Long userId);
    List<TelemetryEvent> findByOccurredAtAfterOrderByOccurredAtDesc(LocalDateTime since);
    List<TelemetryEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, Long entityId);

    @Query(value = """
        SELECT * FROM telemetry.events
        WHERE to_tsvector('english', message) @@ plainto_tsquery('english', :query)
        ORDER BY occurred_at DESC
        LIMIT 100
        """, nativeQuery = true)
    List<TelemetryEvent> searchByMessage(@Param("query") String query);
}
