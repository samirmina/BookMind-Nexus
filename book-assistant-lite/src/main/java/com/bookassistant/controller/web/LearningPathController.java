package com.bookassistant.controller.web;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import com.bookassistant.service.integration.LearningPathService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import com.bookassistant.session.BookSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

import java.util.List;

/**
 * Generates personalized learning paths based on book content and user external notes.
 * <p>
 * <b>What:</b> Accepts user preferences and generates a structured study plan.
 * <p>
 * <b>Why:</b> Required for the learning-path feature that adapts to the user's
 * existing knowledge from external accounts.
 * <p>
 * <b>Where:</b> Implements the web-layer learning-path flow.
 */
@Controller
public class LearningPathController {

    private static final Logger log = LoggerFactory.getLogger(LearningPathController.class);

    private final LearningPathService learningPathService;
    private final ExternalNoteService externalNoteService;
    private final BookOutputService bookOutputService;
    private final BookSession bookSession;

    public LearningPathController(LearningPathService learningPathService, ExternalNoteService externalNoteService,
                                  BookOutputService bookOutputService, BookSession bookSession) {
        this.learningPathService = learningPathService;
        this.externalNoteService = externalNoteService;
        this.bookOutputService = bookOutputService;
        this.bookSession = bookSession;
    }

    @PostMapping("/learning-path")
    public String generateLearningPath(@RequestParam(value = "outputLanguage", required = false) String outputLanguage,
                                       HttpSession session,
                                       Model model) {
        LoggingUtils.enter(log, "generateLearningPath");

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        String bookLanguage = "en";
        String targetLanguage = !StringUtils.isEmpty(outputLanguage) ? outputLanguage : bookLanguage;

        String externalNotes = loadExternalNotesContext(userId);
        String path = learningPathService.generateLearningPath(externalNotes, targetLanguage);

        model.addAttribute("learningPath", path);
        model.addAttribute("hasBook", true);
        model.addAttribute("bookLanguage", bookLanguage);

        Long bookId = bookSession.resolveBookId(userId);
        if (bookId != null) {
            bookOutputService.saveOutput(userId, bookId, "learning_path", path, null, null);
        }

        LoggingUtils.exit(log, "generateLearningPath");
        return "index";
    }

    private String loadExternalNotesContext(Long userId) {
        List<ExternalNote> notes = externalNoteService.findByUserId(userId);
        if (notes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ExternalNote note : notes) {
            if (!StringUtils.isEmpty(note.getContent())) {
                sb.append("- ").append(note.getTitle()).append(": ").append(note.getContent()).append("\n");
            }
        }
        return sb.toString();
    }
}


