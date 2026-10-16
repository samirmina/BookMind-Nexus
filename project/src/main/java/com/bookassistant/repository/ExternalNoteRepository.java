package com.bookassistant.repository;

import com.bookassistant.entity.ExternalNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Data access for external notes synced from linked platforms.
 * <p>
 * <b>What:</b> Provides CRUD operations for external_notes.
 * <p>
 * <b>Where:</b> Implements the repository pattern for the ExternalNote aggregate.
 */
public interface ExternalNoteRepository extends JpaRepository<ExternalNote, Long> {
    List<ExternalNote> findByUserId(Long userId);
    List<ExternalNote> findByUserIdAndLinkedAccountId(Long userId, Long linkedAccountId);
    boolean existsByUserIdAndPlatformNoteId(Long userId, String platformNoteId);
}


