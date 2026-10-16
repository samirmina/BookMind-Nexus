package com.bookassistant.util;

/**
 * Centralized null-safe string utilities.
 * <p>
 * <b>What:</b> Provides {@link #isEmpty(String)} and {@link #safeLength(String)} used
 * throughout the application to avoid repetitive null checks in controllers and services.
 * <p>
 * <b>Why:</b> Single-responsibility, stateless utility class per
 * {@code PROJECT_RULES.md} Section 1.
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Returns {@code true} if the string is {@code null} or blank.
     * <p>
     * <b>Where:</b> Required for consistent null-safety across the codebase.
     *
     * @param str the string to test
     * @return {@code true} if null or blank
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Returns the string length, or {@code 0} if the string is {@code null}.
     *
     * @param str the string to measure
     * @return the length, or {@code 0} if null
     */
    public static int safeLength(String str) {
        return str != null ? str.length() : 0;
    }
}


