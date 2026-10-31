package com.bookassistant.repository.telemetry;

import com.bookassistant.entity.telemetry.AiInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for AI interactions.
 * <p>
 * <b>What:</b> Provides CRUD operations and analytical queries for
 * the AI interaction log table.
 * <p>
 * <b>Where:</b> Implements the repository pattern for AI interactions.
 */
public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {
    List<AiInteraction> findByUserIdOrderByOccurredAtDesc(Long userId);
    List<AiInteraction> findByTabUsedOrderByOccurredAtDesc(String tabUsed);
    List<AiInteraction> findBySuccessOrderByOccurredAtDesc(Boolean success);
    List<AiInteraction> findByOccurredAtAfterOrderByOccurredAtDesc(LocalDateTime since);

    @Query(value = """
        SELECT tab_used, COUNT(*) as count, AVG(duration_ms) as avg_duration, 
               SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failures
        FROM telemetry.ai_interactions
        WHERE occurred_at >= :since
        GROUP BY tab_used
        ORDER BY failures DESC
        """, nativeQuery = true)
    List<Object[]> getAiTabStatsSince(@Param("since") LocalDateTime since);
}
