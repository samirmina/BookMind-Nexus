package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.AccountLinkAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for account link audit entries.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the account linking audit table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for account link audit.
 */
public interface AccountLinkAuditRepository extends JpaRepository<AccountLinkAudit, Long> {
    List<AccountLinkAudit> findByUserIdOrderByOccurredAtDesc(Long userId);
    List<AccountLinkAudit> findByPlatformOrderByOccurredAtDesc(String platform);
    List<AccountLinkAudit> findByActionOrderByOccurredAtDesc(String action);
    List<AccountLinkAudit> findByOccurredAtAfterOrderByOccurredAtDesc(LocalDateTime since);

    @Query(value = """
        SELECT platform, action, COUNT(*) as count
        FROM telemetry.account_link_audit
        WHERE occurred_at >= :since
        GROUP BY platform, action
        ORDER BY count DESC
        """, nativeQuery = true)
        List<Object[]> countByPlatformAndActionSince(@Param("since") LocalDateTime since);

    @Query(value = """
        SELECT platform, COUNT(*) as failures, COUNT(DISTINCT error_id) as unique_errors
        FROM telemetry.account_link_audit
        WHERE action = 'failure'
          AND occurred_at >= :since
        GROUP BY platform
        ORDER BY failures DESC
        """, nativeQuery = true)
    List<Object[]> findFailurePatternsSince(@Param("since") LocalDateTime since);
}
