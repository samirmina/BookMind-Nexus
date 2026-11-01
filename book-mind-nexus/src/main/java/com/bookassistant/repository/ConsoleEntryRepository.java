package com.bookassistant.repository;

import com.bookassistant.entity.ConsoleEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for console entries.
 * <p>
 * <b>What:</b> Provides queries to retrieve captured console output from the database.
 * <p>
 * <b>Why:</b> Allows filtering and pagination of console history for debugging.
 */
public interface ConsoleEntryRepository extends JpaRepository<ConsoleEntry, Long> {
    List<ConsoleEntry> findByLevel(String level);
    List<ConsoleEntry> findBySource(String source);
    List<ConsoleEntry> findByOccurredAtAfter(LocalDateTime after);
    List<ConsoleEntry> findByOccurredAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT c FROM ConsoleEntry c WHERE c.level IN :levels ORDER BY c.occurredAt DESC")
    List<ConsoleEntry> findByLevels(@Param("levels") List<String> levels);
    
    @Query("SELECT c FROM ConsoleEntry c ORDER BY c.occurredAt DESC")
    List<ConsoleEntry> findRecentEntries(Pageable pageable);
}
