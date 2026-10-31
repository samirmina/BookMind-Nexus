package com.bookassistant.controller.admin;

import com.bookassistant.entity.ConsoleEntry;
import com.bookassistant.service.infrastructure.ConsoleEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Provides admin views for captured console output.
 * <p>
 * <b>What:</b> Displays console entries stored in the database, with filtering
 * by log level and pagination.
 * <p>
 * <b>Why:</b> Allows debugging and project history review without accessing
 * server-side log files.
 */
@Controller
@RequestMapping("/admin/console")
public class ConsoleEntryController {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEntryController.class);
    private static final int PAGE_SIZE = 100;

    private final ConsoleEntryService consoleEntryService;

    public ConsoleEntryController(ConsoleEntryService consoleEntryService) {
        this.consoleEntryService = consoleEntryService;
    }

    @GetMapping
    public String viewConsoleEntries(
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            Model model) {

        List<ConsoleEntry> entries;
        long totalEntries;

        if (level != null && !level.isBlank()) {
            entries = consoleEntryService.findByLevel(level.toUpperCase());
            totalEntries = consoleEntryService.count();
        } else if (source != null && !source.isBlank()) {
            entries = consoleEntryService.findBySource(source);
            totalEntries = consoleEntryService.count();
        } else {
            entries = consoleEntryService.findRecentEntries(
                PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "occurredAt"))
            );
            totalEntries = consoleEntryService.count();
        }

        model.addAttribute("entries", entries);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("currentPage", page);
        model.addAttribute("selectedLevel", level);
        model.addAttribute("selectedSource", source);

        long errorCount = consoleEntryService.findByLevel("ERROR").size();
        long warnCount = consoleEntryService.findByLevel("WARN").size();
        long infoCount = consoleEntryService.findByLevel("INFO").size();

        model.addAttribute("errorCount", errorCount);
        model.addAttribute("warnCount", warnCount);
        model.addAttribute("infoCount", infoCount);

        return "admin/console";
    }
}
