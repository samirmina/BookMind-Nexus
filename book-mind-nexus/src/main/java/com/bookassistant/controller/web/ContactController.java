package com.bookassistant.controller.web;

import com.bookassistant.controller.web.ModelAttributeBinder;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Handles contact-form display and submission.
 * <p>
 * <b>What:</b> Shows the contact page (GET) and accepts name, email, and message
 * submissions (POST), logging them for demo purposes.
 * <p>
 * <b>Why:</b> Single responsibility per {@code PROJECT_RULES.md} Section 1; keeps
 * user-message processing isolated from study logic. View resolution is handled
 * by {@code JavaHtmlViewResolver}.
 */
@Controller
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final MessageSource messageSource;

    public ContactController(MessageSource messageSource) {
        this.messageSource = messageSource;
        log.info("ContactController initialized");
    }

    @GetMapping("/contact")
    public String showContactPage() {
        log.debug("Contact page requested");
        return "contact";
    }

    @PostMapping("/contact")
    public String processContactFormSubmission(@RequestParam String name,
                                                @RequestParam String email,
                                                @RequestParam String message,
                                                Model model) {
        LoggingUtils.enter(log, "processContactFormSubmission", "name", name);
        log.info("Contact form submission | name={} | email={}", name, email);

        // Validate inputs
        if (ModelAttributeBinder.isEmpty(name)) {
            LoggingUtils.validationFailure(log, "name", "must not be empty", name);
            model.addAttribute("error", messageSource.getMessage("contact.name.required", null, LocaleContextHolder.getLocale()));
            return "contact";
        }

        if (ModelAttributeBinder.isEmpty(email)) {
            LoggingUtils.validationFailure(log, "email", "must not be empty", email);
            model.addAttribute("error", messageSource.getMessage("contact.email.required", null, LocaleContextHolder.getLocale()));
            return "contact";
        }

        if (ModelAttributeBinder.isEmpty(message)) {
            LoggingUtils.validationFailure(log, "message", "must not be empty", message);
            model.addAttribute("error", messageSource.getMessage("contact.message.required", null, LocaleContextHolder.getLocale()));
            return "contact";
        }

        // In a real application, this would send the message to support
        // For now, just log and show success

        log.info("Contact message received | name={} | email={} | messageLen={}",
            name, email, message.length());

        model.addAttribute("success", messageSource.getMessage("contact.success", null, LocaleContextHolder.getLocale()));

        LoggingUtils.exit(log, "processContactFormSubmission", "status", "success");
        return "contact";
    }
}