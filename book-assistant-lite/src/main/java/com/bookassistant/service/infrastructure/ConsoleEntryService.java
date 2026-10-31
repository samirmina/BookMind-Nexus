package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.ConsoleEntry;
import com.bookassistant.repository.ConsoleEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for accessing console entry data.
 * <p>
 * <b>What:</b> Provides methods to retrieve console entries with filtering and pagination.
 * <p>
 * <b>Why:</b> {@link com.bookassistant.controller.admin.ConsoleEntryController} must not import
 * repositories directly per project rules. This service wraps {@link ConsoleEntryRepository}
 * to maintain the controller → service → repository layer separation.
 */
@Service
public class ConsoleEntryService {

    private final ConsoleEntryRepository consoleEntryRepository;

    public ConsoleEntryService(ConsoleEntryRepository consoleEntryRepository) {
        this.consoleEntryRepository = consoleEntryRepository;
    }

    public List<ConsoleEntry> findByLevel(String level) {
        return consoleEntryRepository.findByLevel(level);
    }

    public List<ConsoleEntry> findBySource(String source) {
        return consoleEntryRepository.findBySource(source);
    }

    public List<ConsoleEntry> findRecentEntries(PageRequest pageRequest) {
        return consoleEntryRepository.findRecentEntries(pageRequest);
    }

    public long count() {
        return consoleEntryRepository.count();
    }
}
