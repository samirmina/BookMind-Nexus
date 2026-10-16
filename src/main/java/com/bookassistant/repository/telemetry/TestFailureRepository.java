package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.TestFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for test failures.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the test failures table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for test failures.
 */
public interface TestFailureRepository extends JpaRepository<TestFailure, Long> {
    List<TestFailure> findByRunIdOrderByTestClass(Long runId);
    List<TestFailure> findByIsRegressionTrueOrderByIdDesc();
    List<TestFailure> findByBugIdOrderByIdDesc(Long bugId);
    List<TestFailure> findByErrorHashOrderByIdDesc(String errorHash);

    @Query(value = """
        SELECT test_class, test_method, COUNT(*) as failure_count,
               MAX(run_id) as latest_run_id
        FROM telemetry.test_failures
        WHERE is_regression = true
        GROUP BY test_class, test_method
        ORDER BY failure_count DESC
        """, nativeQuery = true)
    List<Object[]> findRegressionFailurePatterns();
}
