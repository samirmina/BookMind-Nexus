package com.bookassistant.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes exception messages for user-facing display.
 * <p>
 * Strips Java class names, stack traces, and technical noise while preserving
 * the actual user-actionable cause. Maps known technical errors to friendly messages.
 * </p>
 */
public final class ErrorMessageSanitizer {

    private static final Pattern JAVA_CLASS_PATTERN = Pattern.compile(
            "(?:java\\.lang\\.|java\\.io\\.|java\\.net\\.|org\\.springframework\\.|com\\.)[\\w.$]+"
    );
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "\\(?[A-Za-z]:\\\\.*?\\.[A-Za-z]+:\\d+\\).*"
    );
    private static final Pattern AT_PATTERN = Pattern.compile("\\s+at\\s+\\S+.*");

    private ErrorMessageSanitizer() {}

    /**
     * Sanitize an exception message for display to end users.
     *
     * @param rawMessage the raw exception message, may be null
     * @return a cleaned, user-friendly message
     */
    public static String sanitize(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "An unexpected error occurred. Please try again.";
        }

        String cleaned = rawMessage;

        // Remove stack trace lines if they leaked into the message
        cleaned = AT_PATTERN.matcher(cleaned).replaceAll("");

        // Remove file paths and line numbers
        cleaned = STACK_TRACE_PATTERN.matcher(cleaned).replaceAll("");

        // Remove Java class names
        cleaned = JAVA_CLASS_PATTERN.matcher(cleaned).replaceAll("");

        // Strip leading separator left by class name removal, e.g. ": Failed..."
        cleaned = cleaned.replaceAll("^\\s*:?\\s+", "");

        // Collapse whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // If cleaning removed everything meaningful, return a generic message
        if (cleaned.length() < 3 || cleaned.matches("^[^a-zA-Z]*$")) {
            return "An unexpected error occurred. Please try again.";
        }

        // Capitalize first letter
        if (cleaned.length() > 0) {
            cleaned = cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
        }

        return cleaned;
    }

    /**
     * Classify an exception into a user-friendly category for routing to the right bundle key.
     */
    public static ErrorCategory classify(Throwable e) {
        if (e == null) {
            return ErrorCategory.GENERIC;
        }

        // Check root cause first for wrapped exceptions
        Throwable root = e.getCause() != null ? e.getCause() : e;
        String message = (root.getMessage() == null ? "" : root.getMessage()).toLowerCase();
        String wrapperMessage = (e.getMessage() == null ? "" : e.getMessage()).toLowerCase();
        String className = root.getClass().getSimpleName().toLowerCase();

        if (className.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
            return ErrorCategory.TIMEOUT;
        }
        if (className.contains("connect") || message.contains("connection refused") || message.contains("no connection")) {
            return ErrorCategory.SERVICE_UNAVAILABLE;
        }
        if (className.contains("io") || message.contains("io error") || message.contains("broken pipe")) {
            return ErrorCategory.SERVICE_UNAVAILABLE;
        }
        if (className.contains("illegalargument") || message.contains("invalid") || message.contains("not a pdf")) {
            return ErrorCategory.INVALID_INPUT;
        }
        if (message.contains("module") && message.contains("has no attribute")) {
            return ErrorCategory.DEPENDENCY_ERROR;
        }
        if (message.contains("no text") || message.contains("empty")) {
            return ErrorCategory.EMPTY_RESULT;
        }
        if (message.contains("auth") || message.contains("token") || message.contains("oauth")) {
            return ErrorCategory.AUTH_FAILURE;
        }
        if (message.contains("notion") || message.contains("external note")) {
            return ErrorCategory.NOTION_ERROR;
        }
        if (message.contains("ai") || message.contains("ollama") || message.contains("model")) {
            return ErrorCategory.AI_FAILURE;
        }

        return ErrorCategory.GENERIC;
    }

    public enum ErrorCategory {
        TIMEOUT,
        SERVICE_UNAVAILABLE,
        INVALID_INPUT,
        EXTRACTION_FAILED,
        DEPENDENCY_ERROR,
        EMPTY_RESULT,
        AUTH_FAILURE,
        NOTION_ERROR,
        AI_FAILURE,
        GENERIC
    }
}
