package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.TelemetryError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Data access for telemetry errors.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the error registry table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for telemetry errors.
 */
public interface TelemetryErrorRepository extends JpaRepository<TelemetryError, Long> {
    Optional<TelemetryError> findByErrorHash(String errorHash);
    List<TelemetryError> findByStatusOrderByLastSeenAtDesc(String status);
    List<TelemetryError> findByCategoryOrderByOccurrenceCountDesc(String category);
    List<TelemetryError> findByAffectedComponentOrderByLastSeenAtDesc(String affectedComponent);
    List<TelemetryError> findByIsRegressionTrueOrderByLastSeenAtDesc();

    @Query(value = """
        SELECT * FROM telemetry.errors
        WHERE status != 'fixed'
        ORDER BY occurrence_count DESC, last_seen_at DESC
        """, nativeQuery = true)
    List<TelemetryError> findActiveErrors();
}
