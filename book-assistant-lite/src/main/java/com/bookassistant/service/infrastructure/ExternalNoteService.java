package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.repository.ExternalNoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for accessing external note data.
 * <p>
 * <b>What:</b> Provides methods to retrieve external notes for a user.
 * <p>
 * <b>Why:</b> Controllers must not import repositories directly per project rules.
 * This service wraps {@link ExternalNoteRepository} to maintain the controller → service → repository layer separation.
 */
@Service
public class ExternalNoteService {

    private static final Logger log = LoggerFactory.getLogger(ExternalNoteService.class);

    private final ExternalNoteRepository externalNoteRepository;

    public ExternalNoteService(ExternalNoteRepository externalNoteRepository) {
        this.externalNoteRepository = externalNoteRepository;
    }

    /**
     * Finds all external notes for a user.
     *
     * @param userId the user ID
     * @return list of external notes
     */
    public List<ExternalNote> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return externalNoteRepository.findByUserId(userId);
    }
}
