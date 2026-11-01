package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.TelemetryProcedure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for telemetry procedures.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the procedure outcomes table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for telemetry procedures.
 */
public interface TelemetryProcedureRepository extends JpaRepository<TelemetryProcedure, Long> {
    List<TelemetryProcedure> findByProcedureNameOrderByExecutedAtDesc(String procedureName);
    List<TelemetryProcedure> findByOutcomeOrderByExecutedAtDesc(String outcome);
    List<TelemetryProcedure> findByUserIdOrderByExecutedAtDesc(Long userId);
    List<TelemetryProcedure> findByExecutedAtAfterOrderByExecutedAtDesc(LocalDateTime since);

    @Query(value = """
        SELECT procedure_name, outcome, COUNT(*) as count, AVG(duration_ms) as avg_duration
        FROM telemetry.procedures
        WHERE executed_at >= :since
        GROUP BY procedure_name, outcome
        ORDER BY procedure_name, outcome
        """, nativeQuery = true)
    List<Object[]> getProcedureStatsSince(@Param("since") LocalDateTime since);
}
