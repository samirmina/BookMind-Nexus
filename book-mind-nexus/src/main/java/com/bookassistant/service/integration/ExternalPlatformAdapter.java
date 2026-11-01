package com.bookassistant.service.integration;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.ExternalNoteRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Base adapter interface for external platform integrations.
 * <p>
 * <b>What:</b> Defines the contract for fetching notes from external services.
 * <p>
 * <b>Why:</b> Enables the application to retrieve user data from Notion
 * and other platforms for cross-platform analysis.
 */
public interface ExternalPlatformAdapter {
    String getPlatformName();
    List<ExternalNote> fetchNotes(Long userId, LinkedAccount account);
}


