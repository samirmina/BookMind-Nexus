package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.SystemHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for system health checks.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the system health table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for system health.
 */
public interface SystemHealthRepository extends JpaRepository<SystemHealth, Long> {
    List<SystemHealth> findByComponentOrderByCheckedAtDesc(String component);
    List<SystemHealth> findByStatusOrderByCheckedAtDesc(String status);
    List<SystemHealth> findByCheckedAtAfterOrderByCheckedAtDesc(LocalDateTime since);

    @Query(value = """
        SELECT component, status, COUNT(*) as count, AVG(latency_ms) as avg_latency
        FROM telemetry.system_health
        WHERE checked_at >= :since
        GROUP BY component, status
        ORDER BY component, status
        """, nativeQuery = true)
    List<Object[]> getHealthStatsSince(@Param("since") LocalDateTime since);
}
