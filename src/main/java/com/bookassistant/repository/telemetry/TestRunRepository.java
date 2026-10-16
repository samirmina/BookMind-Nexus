package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for test runs.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the test runs table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for test runs.
 */
public interface TestRunRepository extends JpaRepository<TestRun, Long> {
    List<TestRun> findByMavenGoalOrderByRunAtDesc(String mavenGoal);
    List<TestRun> findByRunAtAfterOrderByRunAtDesc(LocalDateTime since);

    @Query(value = """
        SELECT maven_goal, COUNT(*) as runs, 
               AVG(duration_ms) as avg_duration,
               SUM(failures) as total_failures,
               SUM(errors) as total_errors
        FROM telemetry.test_runs
        WHERE run_at >= :since
        GROUP BY maven_goal
        ORDER BY run_at DESC
        """, nativeQuery = true)
    List<Object[]> getTestRunStatsSince(@Param("since") LocalDateTime since);
}
