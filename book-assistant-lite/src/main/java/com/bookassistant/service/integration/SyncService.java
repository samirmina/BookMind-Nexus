package com.bookassistant.service.integration;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.ExternalNoteRepository;
import com.bookassistant.repository.LinkedAccountRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Syncs notes from external linked platforms into the local database.
 * <p>
 * <b>What:</b> Iterates over a user's linked accounts, fetches notes via adapters,
 * and stores them in {@code external_notes}.
 * <p>
 * <b>Why:</b> Required to make external notes available for the cross-platform Q&A
 * and learning-path features without querying external APIs on every request.
 * <p>
 * <b>Where:</b> Implements the sync orchestration per the project's integration layer.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final LinkedAccountRepository linkedAccountRepository;
    private final ExternalNoteRepository externalNoteRepository;
    private final List<ExternalPlatformAdapter> adapters;

    public SyncService(LinkedAccountRepository linkedAccountRepository,
                       ExternalNoteRepository externalNoteRepository,
                       List<ExternalPlatformAdapter> adapters) {
        this.linkedAccountRepository = linkedAccountRepository;
        this.externalNoteRepository = externalNoteRepository;
        this.adapters = adapters;
    }

    /**
     * Syncs all external notes for a user.
     *
     * @param userId the user ID
     * @return number of notes synced
     */
    public int syncUserNotes(Long userId) {
        LoggingUtils.enter(log, "syncUserNotes", "userId", userId);
        List<LinkedAccount> accounts = linkedAccountRepository.findByUserId(userId);
        int totalSynced = 0;

        log.info("Sync started | userId={} | linkedAccounts={}", userId, accounts.size());

        for (LinkedAccount account : accounts) {
            log.info("Syncing platform | userId={} | platform={} | accountId={}", userId, account.getPlatform(), account.getId());
            for (ExternalPlatformAdapter adapter : adapters) {
                if (adapter.getPlatformName().equals(account.getPlatform())) {
                    log.info("Using adapter | userId={} | platform={} | adapter={}", userId, account.getPlatform(), adapter.getClass().getSimpleName());
                    List<ExternalNote> notes = adapter.fetchNotes(userId, account);
                    log.info("Adapter returned {} notes for userId={} platform={}", notes.size(), userId, account.getPlatform());
                    
                    for (ExternalNote note : notes) {
                        note.setUserId(userId);
                        note.setLinkedAccountId(account.getId());
                        note.setSyncedAt(LocalDateTime.now());
                        boolean exists = externalNoteRepository.existsByUserIdAndPlatformNoteId(userId, note.getPlatformNoteId());
                        log.info("Checking duplicate | userId={} | platformNoteId={} | exists={}", userId, note.getPlatformNoteId(), exists);
                        if (!exists) {
                            externalNoteRepository.save(note);
                            totalSynced++;
                            log.info("Saved note | userId={} | platformNoteId={} | title={}", userId, note.getPlatformNoteId(), note.getTitle());
                        } else {
                            log.info("Skipped duplicate | userId={} | platformNoteId={}", userId, note.getPlatformNoteId());
                        }
                    }
                    log.info("Synced {} notes from platform {} for user {}",
                        notes.size(), account.getPlatform(), userId);
                }
            }
        }

        log.info("Sync completed | userId={} | totalSynced={}", userId, totalSynced);
        return totalSynced;
    }
}


