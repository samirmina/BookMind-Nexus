package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.CoverageSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for coverage snapshots.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the coverage snapshots table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for coverage snapshots.
 */
public interface CoverageSnapshotRepository extends JpaRepository<CoverageSnapshot, Long> {
    List<CoverageSnapshot> findByClassNameOrderByCapturedAtDesc(String className);
    List<CoverageSnapshot> findByBelowThresholdTrueOrderByCapturedAtDesc();
    List<CoverageSnapshot> findByCapturedAtAfterOrderByCapturedAtDesc(LocalDateTime since);

    @Query(value = """
        SELECT class_name, 
               AVG(line_coverage_pct) as avg_line,
               AVG(branch_coverage_pct) as avg_branch,
               AVG(method_coverage_pct) as avg_method,
               COUNT(*) as snapshots
        FROM telemetry.coverage_snapshots
        WHERE captured_at >= :since
        GROUP BY class_name
        ORDER BY avg_line ASC
        """, nativeQuery = true)
    List<Object[]> getCoverageTrendSince(@Param("since") LocalDateTime since);
}
