package com.bookassistant.controller.web;

import com.bookassistant.service.infrastructure.CacheService;
import com.bookassistant.service.infrastructure.CacheService.CacheIndex;
import com.bookassistant.session.BookSession;
import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the main application shell and the about page.
 * <p>
 * <b>What:</b> Populates the model with session state (book loaded, language,
 * cache index) before returning the single-page view.
 * <p>
 * <b>Why:</b> Single responsibility per {@code PROJECT_RULES.md} Section 1: this
 * controller is the sole producer of the home-page model context.
 */
@Controller
public class PageController {

    private static final Logger log = LoggerFactory.getLogger(PageController.class);
    private static final String SESSION_USER_ID = "userId";

    private final BookSession bookSession;
    private final CacheService cacheService;

    /**
     * Creates the controller with required dependencies.
     *
     * @param bookSession   session-scoped book state
     * @param cacheService  figure cache for matching images to the current book
     */
    public PageController(BookSession bookSession, CacheService cacheService) {
        if (bookSession == null) throw new IllegalArgumentException("BookSession must not be null");
        this.bookSession = bookSession;
        this.cacheService = cacheService;
        log.info("PageController initialized");
    }

    /**
     * Handles {@code GET /} and returns the landing page for anonymous users.
     * <p>
     * <b>Where:</b> Authenticated users are redirected to {@code /app} so they
     * never see the marketing landing page.
     *
     * @param session the HTTP session
     * @return view name or redirect
     */
    @GetMapping("/")
    public String showLandingPage() {
        log.debug("Landing page requested");
        return "home";
    }

    @GetMapping("/app")
    public String showAppPage(Model model, HttpSession session) {
        log.debug("App page requested");
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized app access attempt");
            return "redirect:/login";
        }
        model.addAttribute("hasBook", bookSession.hasBook());
        model.addAttribute("bookLanguage", bookSession.getDetectedLanguage());
        bookSession.getBook().ifPresent(book -> {
            model.addAttribute("bookName", book.fileName());
            String bookName = book.fileName();
            if (bookName.contains(".")) {
                bookName = bookName.substring(0, bookName.lastIndexOf('.'));
            }
            CacheIndex index = cacheService.loadIndex(bookName);
            if (index != null && !index.isEmpty()) {
                model.addAttribute("cacheBookName", index.bookName);
                model.addAttribute("cacheTotalImages", index.totalImages);
            }
        });
        return "index";
    }

    @GetMapping("/about")
    public String showAboutPage() {
        log.debug("About page requested");
        return "about";
    }
}
