package com.bookassistant.service.text;

import com.bookassistant.controller.web.ModelAttributeBinder;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Detects the language of a text sample and produces RTL/LTR formatting metadata.
 * <p>
 * <b>What:</b> Identifies whether text is Arabic or English by scanning Unicode
 * character ranges, then returns the appropriate CSS class, inline style, and
 * HTML attributes for correct visual rendering.
 * <p>
 * <b>Why:</b> Book texts are bilingual (Arabic/English); without explicit direction
 * markers the browser would render misaligned text.
 * <p>
 * <b>Where:</b> Required for RTL/LTR formatting logic in the study UI.
 */
@Service
public class TextDirectionService {

    private static final Logger log = LoggerFactory.getLogger(TextDirectionService.class);

    /**
     * Detects the language of a text sample by counting Arabic vs. Latin characters
     * in the first 1 000 characters.
     * <p>
     * <b>Algorithm:</b>
     * <ol>
     *   <li>Limit the sample to the first 1 000 characters for performance.</li>
      *   <li>Count characters in Unicode ranges: Arabic ({@code \\u0600}-\\u{06FF},
     *       Arabic Supplement, Arabic Extended-A) and Latin ({@code a-z}, {@code A-Z}).</li>
     *   <li>If Arabic count exceeds Latin count, return "ar"; otherwise "en".</li>
     * </ol>
     * <p>
     * <b>Where:</b> Implements the RTL/LTR detection strategy required by
     * {@code PROJECT_RULES.md} Section 3.
     *
     * @param text the raw text
     * @return "ar" if Arabic dominates, "en" otherwise; never {@code null}
     */
    public String detectLanguage(String text) {
        LoggingUtils.enter(log, "detectLanguage", "textLen", safeLength(text));
        
        if (ModelAttributeBinder.isEmpty(text)) {
            log.debug("Detect language: null or empty text, defaulting to 'en'");
            return "en";
        }

        int arabicCount = 0;
        int englishCount = 0;

        // Sample first 1000 characters for efficiency
        String sample = text.length() > 1000 ? text.substring(0, 1000) : text;

        for (char c : sample.toCharArray()) {
            // Arabic Unicode ranges
            if ((c >= '\u0600' && c <= '\u06FF') ||    // Arabic
                (c >= '\u0750' && c <= '\u077F') ||    // Arabic Supplement
                (c >= '\u08A0' && c <= '\u08FF')) {    // Arabic Extended-A
                arabicCount++;
            }
            // English/Latin ranges
            else if ((c >= 'a' && c <= 'z') ||
                     (c >= 'A' && c <= 'Z')) {
                englishCount++;
            }
        }

        // If Arabic characters are more than English, detect as Arabic
        String result = arabicCount > englishCount ? "ar" : "en";
        log.debug("Language detection | arabic={} | english={} | result={}", arabicCount, englishCount, result);
        
        LoggingUtils.exit(log, "detectLanguage", "result", result);
        return result;
    }

    /**
     * This name was chosen because this function gets the text direction for a given language.
     * Returns rtl for Arabic, ltr for English.
     */
    public String getDirection(String language) {
        return "ar".equals(language) ? "rtl" : "ltr";
    }

    /**
     * This name was chosen because this function gets the HTML lang attribute for a given language.
     * Returns language code for proper HTML markup.
     */
    public String getHtmlLang(String language) {
        return "ar".equals(language) ? "ar" : "en";
    }

    /**
     * Builds a {@link FormattedResponse} from content and an optional language hint.
     * <p>
     * <b>Where:</b> Centralizes RTL/LTR formatting logic so that every study endpoint
     * returns consistent direction metadata.
     *
     * @param content     the HTML content to display
     * @param language    the language code, or {@code null} to auto-detect
     * @return a fully populated formatted response
     */
    public FormattedResponse createFormattedResponse(String content, String language) {
        LoggingUtils.enter(log, "createFormattedResponse", "contentLen", safeLength(content));
        
        String detectedLanguage = (!ModelAttributeBinder.isEmpty(language)) ? language : detectLanguage(content);
        String direction = getDirection(detectedLanguage);
        String htmlLang = getHtmlLang(detectedLanguage);

        FormattedResponse response = new FormattedResponse(content, detectedLanguage, direction, htmlLang);
        
        log.debug("Created formatted response | language={} | direction={}", detectedLanguage, direction);
        LoggingUtils.exit(log, "createFormattedResponse", "direction", direction);
        
        return response;
    }

    /**
     * This name was chosen because this function creates a formatted response by auto-detecting language.
     * Convenience method that detects language from content.
     */
    public FormattedResponse createFormattedResponse(String content) {
        return createFormattedResponse(content, null);
    }

    /**
     * This name was chosen because this function gets the safe length of a string.
     * Delegates to centralized StringUtils for consistency.
     */
    private int safeLength(String str) {
        return StringUtils.safeLength(str);
    }

    /**
     * Model class for formatted response with language metadata.
     * Provides CSS classes, inline styles, and HTML attributes for proper text display.
     */
    public static class FormattedResponse {
        private final String content;
        private final String language;
        private final String direction;
        private final String htmlLang;

        /**
         * This name was chosen because this function creates a FormattedResponse with all metadata.
         * Stores content along with language detection results.
         */
        public FormattedResponse(String content, String language, String direction, String htmlLang) {
            this.content = content;
            this.language = language;
            this.direction = direction;
            this.htmlLang = htmlLang;
        }

        /**
         * This name was chosen because this function gets the content text.
         * Returns the original HTML content.
         */
        public String getContent() {
            return content;
        }

        /**
         * This name was chosen because this function gets the detected language code.
         * Returns 'ar' for Arabic or 'en' for English.
         */
        public String getLanguage() {
            return language;
        }

        /**
         * This name was chosen because this function gets the text direction.
         * Returns 'rtl' for right-to-left or 'ltr' for left-to-right.
         */
        public String getDirection() {
            return direction;
        }

        /**
         * This name was chosen because this function gets the HTML lang attribute value.
         * Returns language code for HTML lang attribute.
         */
        public String getHtmlLang() {
            return htmlLang;
        }

        /**
         * This name was chosen because this function returns CSS class string for text direction.
         * Returns 'text-rtl' or 'text-ltr' for CSS styling.
         */
        public String getCssClass() {
            return "text-" + direction;
        }

        /**
         * This name was chosen because this function returns inline style for the response.
         * Provides direction and text-align properties.
         */
        public String getInlineStyle() {
            return "direction: " + direction + "; text-align: " + ("rtl".equals(direction) ? "right" : "left") + ";";
        }

        /**
         * This name was chosen because this function returns HTML attributes for proper text display.
         * Provides dir and lang attributes for HTML elements.
         */
        public String getHtmlAttributes() {
            return "dir=\"" + direction + "\" lang=\"" + htmlLang + "\"";
        }
    }
}


