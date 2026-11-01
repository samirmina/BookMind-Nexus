package com.bookassistant.repository;

import com.bookassistant.entity.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Data access for reading progress.
 * <p>
 * <b>What:</b> Provides CRUD operations for reading_progress.
 * <p>
 * <b>Where:</b> Implements the repository pattern for the ReadingProgress aggregate.
 */
public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {
    Optional<ReadingProgress> findByUserIdAndBookId(Long userId, Long bookId);
}


