package com.bookassistant.controller.web;

import com.bookassistant.util.StringUtils;
import org.springframework.ui.Model;

/**
 * Null-safe helper for adding common attributes to the Thymeleaf {@link Model}.
 * <p>
 * <b>What:</b> Centralized utilities that wrap {@link Model#addAttribute} calls with
 * null checks and predefined attribute keys.
 * <p>
 * <b>Why:</b> Eliminates repetitive null-safety boilerplate in controllers and ensures
 * attribute keys are consistent across the application.
 */
public final class ModelAttributeBinder {

    /**
     * Adds an error message to the model only when both arguments are non-null.
     * <p>
     * <b>Where:</b> Used by every controller endpoint that needs to surface a user-facing error.
     *
     * @param model  the Thymeleaf model
     * @param message the error message text
     */
    public static void addError(Model model, String message) {
        if (model != null && message != null) {
            model.addAttribute("error", message);
        }
    }

    /**
     * Adds a success message to the model only when both arguments are non-null.
     *
     * @param model  the Thymeleaf model
     * @param message the success message text
     */
    public static void addSuccess(Model model, String message) {
        if (model != null && message != null) {
            model.addAttribute("success", message);
        }
    }

    /**
     * Delegates to {@link StringUtils#isEmpty(String)} for null-safe blank checking.
     *
     * @param str the string to test
     * @return {@code true} if the string is null or blank
     */
    public static boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * Delegates to {@link StringUtils#safeLength(String)} for null-safe length reporting.
     *
     * @param str the string to measure
     * @return the length, or {@code 0} if the string is null
     */
    public static int safeLength(String str) {
        return StringUtils.safeLength(str);
    }

    // Prevent instantiation
    private ModelAttributeBinder() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
}


