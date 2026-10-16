package com.bookassistant.controller.web;

import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.session.BookSession;
import com.bookassistant.service.telemetry.TelemetryService;
import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class OutputsController {

    private static final Logger log = LoggerFactory.getLogger(OutputsController.class);

    private static final int PAGE_SIZE = 20;

    private final BookOutputService bookOutputService;
    private final BookSession bookSession;
    private final TelemetryService telemetryService;

    public OutputsController(BookOutputService bookOutputService, BookSession bookSession, TelemetryService telemetryService) {
        this.bookOutputService = bookOutputService;
        this.bookSession = bookSession;
        this.telemetryService = telemetryService;
    }

    @GetMapping("/outputs")
    public String listOutputs(HttpSession session,
                              @RequestParam(value = "tab", required = false) String tab,
                              @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                              Model model) {
        LoggingUtils.enter(log, "listOutputs", "tab", tab, "page", page);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        List<com.bookassistant.entity.BookOutput> outputs;
        int totalPages;

        if (tab != null && !tab.isBlank()) {
            outputs = bookOutputService.findByUserIdAndTabUsed(userId, tab);
            totalPages = (outputs.size() / PAGE_SIZE) + 1;
            outputs = outputs.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, outputs.size()));
            model.addAttribute("filterTab", tab);
        } else {
            outputs = bookOutputService.findByUserId(userId);
            totalPages = (outputs.size() / PAGE_SIZE) + 1;
            outputs = outputs.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, outputs.size()));
        }

        model.addAttribute("outputs", outputs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());

        LoggingUtils.exit(log, "listOutputs", "count", outputs.size());
        return "outputs/index";
    }

    @PostMapping("/outputs/{id}/delete")
    public String deleteOutput(HttpSession session,
                               @PathVariable Long id,
                               @RequestParam(value = "tab", required = false) String tab,
                               @RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        LoggingUtils.enter(log, "deleteOutput", "id", id);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        try {
            bookOutputService.deleteOutput(id, userId);
            telemetryService.logEvent("output", "delete_output", "info", userId, null, null, "book_output", id, "Output deleted", null, null, null);
        } catch (Exception e) {
            LoggingUtils.exception(log, "deleteOutput", e);
            log.error("Failed to delete output: {}", id, e);
            telemetryService.logEvent("output", "delete_output_failed", "error", userId, null, null, "book_output", id, "Failed to delete output: " + e.getMessage(), null, e.getStackTrace().toString(), null);
        }

        String redirectUrl = "/outputs";
        if (tab != null && !tab.isBlank()) {
            redirectUrl += "?tab=" + tab + "&page=" + page;
        } else if (page > 0) {
            redirectUrl += "?page=" + page;
        }

        LoggingUtils.exit(log, "deleteOutput", "redirectUrl", redirectUrl);
        return "redirect:" + redirectUrl;
    }
}
