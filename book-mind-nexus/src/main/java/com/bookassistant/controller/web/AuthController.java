package com.bookassistant.controller.web;

import com.bookassistant.entity.User;
import com.bookassistant.service.auth.UserService;
import com.bookassistant.util.ErrorMessageSanitizer;
import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles user authentication: registration, login, and logout.
 * <p>
 * <b>What:</b> Provides web endpoints for account creation and session-based authentication.
 * <p>
 * <b>Why:</b> Required to identify real users (replacing the hardcoded demo-user) and
 * to enforce ownership of books and linked accounts.
 * <p>
 * <b>Where:</b> Implements the web-layer authentication flow per {@code PROJECT_RULES.md} Section 1.
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String SESSION_USER_ID = "userId";
    private static final String SESSION_USER_EMAIL = "userEmail";

    private final UserService userService;
    private final MessageSource messageSource;

    public AuthController(UserService userService, MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    /**
     * Handles {@code GET /register}.
     * <p>
     * <b>Where:</b> Shows the registration form.
     */
    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        LoggingUtils.enter(log, "showRegisterPage");
        model.addAttribute("bookLanguage", "en");
        LoggingUtils.exit(log, "showRegisterPage");
        return "auth/register";
    }

    /**
     * Handles {@code POST /register}.
     * <p>
     * <b>Where:</b> Processes registration form submission.
     */
    @PostMapping("/register")
    public String processRegistration(@RequestParam("email") String email,
                                      @RequestParam("password") String password,
                                      @RequestParam(value = "name", required = false) String name,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        LoggingUtils.enter(log, "processRegistration", "email", email);

        try {
            User user = userService.register(email, password, name);
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_USER_EMAIL, user.getEmail());
            log.info("Registration successful | userId={} | email={}", user.getId(), user.getEmail());
            String regSuccess = messageSource.getMessage("register.success", null, LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", regSuccess);
            return "redirect:/app";
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed | error={}", e.getMessage());
            String sanitized = ErrorMessageSanitizer.sanitize(e.getMessage());
            model.addAttribute("error", sanitized);
            model.addAttribute("bookLanguage", "en");
            return "auth/register";
        } finally {
            LoggingUtils.exit(log, "processRegistration");
        }
    }

    /**
     * Handles {@code GET /login}.
     * <p>
     * <b>Where:</b> Shows the login form.
     */
    @GetMapping("/login")
    public String showLoginPage(Model model) {
        LoggingUtils.enter(log, "showLoginPage");
        model.addAttribute("bookLanguage", "en");
        LoggingUtils.exit(log, "showLoginPage");
        return "auth/login";
    }

    /**
     * Handles {@code POST /login}.
     * <p>
     * <b>Where:</b> Processes login form submission.
     */
    @PostMapping("/login")
    public String processLogin(@RequestParam("email") String email,
                               @RequestParam("password") String password,
                               HttpSession session,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        LoggingUtils.enter(log, "processLogin", "email", email);

        try {
            User user = userService.login(email, password);
            session.setAttribute(SESSION_USER_ID, user.getId());
            session.setAttribute(SESSION_USER_EMAIL, user.getEmail());
            log.info("Login successful | userId={} | email={}", user.getId(), user.getEmail());
            String loginSuccess = messageSource.getMessage("login.success", null, LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", loginSuccess);
            return "redirect:/app";
        } catch (IllegalArgumentException e) {
            log.warn("Login failed | error={}", e.getMessage());
            String sanitized = ErrorMessageSanitizer.sanitize(e.getMessage());
            model.addAttribute("error", sanitized);
            model.addAttribute("bookLanguage", "en");
            return "auth/login";
        } finally {
            LoggingUtils.exit(log, "processLogin");
        }
    }

    /**
     * Handles {@code POST /logout}.
     * <p>
     * <b>Where:</b> Destroys the user's HTTP session.
     */
    @PostMapping("/logout")
    public String processLogout(HttpSession session, RedirectAttributes redirectAttributes) {
        LoggingUtils.enter(log, "processLogout");

        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        session.invalidate();
        log.info("User logged out | userId={}", userId);
        String logoutSuccess = messageSource.getMessage("logout.success", null, LocaleContextHolder.getLocale());
        redirectAttributes.addFlashAttribute("success", logoutSuccess);
        return "redirect:/";
    }
}


