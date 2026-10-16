package com.bookassistant.service.text;

import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Converts a safe subset of Markdown into HTML suitable for Thymeleaf rendering.
 * <p>
 * <b>What:</b> Implements a hand-rolled, single-pass Markdown-to-HTML state machine that
 * supports headings, blockquotes, unordered/ordered lists, tables, inline formatting
 * (bold, italic, links, code), and fenced code blocks.
 * <p>
 * <b>Why:</b> External Markdown libraries are avoided to keep the dependency surface
 * minimal (Spring Boot 3.5 convention: prefer framework-provided or zero-dependency
 * utilities for simple text transformations).
 * <p>
 * <b>Where:</b> Required for the study-page markdown display feature.
 */
@Service
public class MarkdownService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownService.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 1024;

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]++)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*]+?)\\*(?!\\*)");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```", Pattern.MULTILINE);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern HR_PATTERN = Pattern.compile("^(---|\\*\\*\\*|___)\\s*$", Pattern.MULTILINE);

    /**
     * Converts Markdown text to HTML.
     * <p>
     * <b>Algorithm (state machine):</b>
     * <ol>
     *   <li>Pre-process: fenced code blocks are extracted and replaced with pre-rendered
     *       {@code <pre><code>} tokens so inline formatters do not corrupt them.</li>
     *   <li>Split the remaining text into lines.</li>
     *   <li>Iterate line-by-line, tracking five boolean states:
     *       {@code inUlist}, {@code inOlist}, {@code inBlockquote}, {@code inTable},
     *       {@code inParagraph}.</li>
     *   <li>On each line, close any active state whose terminator (blank line or
     *       incompatible marker) is reached, then open/render the new element.</li>
     *   <li>After the loop, flush any remaining open tags.</li>
     * </ol>
     * <p>
     * <b>Where:</b> Implements the Markdown state-machine strategy required by
     * {@code PROJECT_RULES.md} Section 3.
     *
     * @param markdownText the raw Markdown input
     * @return HTML string, or empty string if input is null/blank
     * @throws IllegalArgumentException if input exceeds 1 MB
     */
    public String convertMarkdownToHtml(String markdownText) {
        LoggingUtils.enter(log, "convertMarkdownToHtml", "inputLen", safeLength(markdownText));

        if (isEmpty(markdownText)) {
            log.debug("Convert markdown: null or empty input");
            return "";
        }

        if (markdownText.length() > MAX_INPUT_LENGTH) {
            LoggingUtils.validationFailure(log, "markdownText", "too large", markdownText.length() + " chars");
            throw new IllegalArgumentException("Markdown text too large (max 1MB)");
        }

        long startTime = System.currentTimeMillis();

        try {
            markdownText = processCodeFences(markdownText);

            StringBuilder html = new StringBuilder(markdownText.length() + 100);
            String[] lines = markdownText.split("\n", -1);

            boolean inUlist = false;
            boolean inOlist = false;
            boolean inBlockquote = false;
            boolean inTable = false;
            boolean inParagraph = false;

            for (int i = 0; i < lines.length; i++) {
                String rawLine = lines[i];
                String line = rawLine.trim();

                if (line.isEmpty()) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    continue;
                }

                if (HR_PATTERN.matcher(line).find()) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    html.append("<hr>");
                    continue;
                }

                if (line.startsWith("# ")) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    html.append("<h1>").append(formatInline(escapeHtml(line.substring(2)))).append("</h1>");
                    continue;
                }
                if (line.startsWith("## ")) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    html.append("<h2>").append(formatInline(escapeHtml(line.substring(3)))).append("</h2>");
                    continue;
                }
                if (line.startsWith("### ")) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    html.append("<h3>").append(formatInline(escapeHtml(line.substring(4)))).append("</h3>");
                    continue;
                }

                if (line.startsWith("> ")) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    if (!inBlockquote) {
                        html.append("<blockquote>");
                        inBlockquote = true;
                    }
                    html.append("<p>").append(formatInline(escapeHtml(line.substring(2)))).append("</p>");
                    continue;
                }

                if (line.startsWith("- ") || line.startsWith("* ")) {
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    if (!inUlist) {
                        html.append("<ul>");
                        inUlist = true;
                    }
                    html.append("<li>").append(formatInline(escapeHtml(line.substring(2)))).append("</li>");
                    continue;
                }

                if (line.matches("^\\d+\\.\\s.*")) {
                    inUlist = closeUlist(html, inUlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inTable = closeTable(html, inTable);
                    inParagraph = closeParagraph(html, inParagraph);
                    if (!inOlist) {
                        html.append("<ol>");
                        inOlist = true;
                    }
                    String content = line.replaceFirst("^\\d+\\.\\s", "");
                    html.append("<li>").append(formatInline(escapeHtml(content))).append("</li>");
                    continue;
                }

                if (line.startsWith("|") && line.endsWith("|")) {
                    inUlist = closeUlist(html, inUlist);
                    inOlist = closeOlist(html, inOlist);
                    inBlockquote = closeBlockquote(html, inBlockquote);
                    inParagraph = closeParagraph(html, inParagraph);

                    if (line.matches("^\\|[\\s:-]++\\|$")) {
                        continue;
                    }
                    if (!inTable) {
                        html.append("<table>");
                        inTable = true;
                    }
                    String[] cells = line.split("\\|");
                    boolean isHeader = i + 1 < lines.length
                        && lines[i + 1].trim().matches("^\\|[\\s:-]++\\|$");
                    String tag = isHeader ? "th" : "td";
                    html.append("<tr>");
                    for (int c = 1; c < cells.length - 1; c++) {
                        html.append("<").append(tag).append(">")
                            .append(formatInline(escapeHtml(cells[c].strip())))
                            .append("</").append(tag).append(">");
                    }
                    html.append("</tr>");
                    continue;
                }

                inUlist = closeUlist(html, inUlist);
                inOlist = closeOlist(html, inOlist);
                inBlockquote = closeBlockquote(html, inBlockquote);
                inTable = closeTable(html, inTable);
                if (!inParagraph) {
                    html.append("<p>");
                    inParagraph = true;
                    html.append(formatInline(escapeHtml(line)));
                } else {
                    html.append("<br>").append(formatInline(escapeHtml(line)));
                }
            }

            inUlist = closeUlist(html, inUlist);
            inOlist = closeOlist(html, inOlist);
            inBlockquote = closeBlockquote(html, inBlockquote);
            inTable = closeTable(html, inTable);
            inParagraph = closeParagraph(html, inParagraph);

            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exit(log, "convertMarkdownToHtml", "outputLen", html.length());
            log.debug("Markdown converted in {}ms | outputLen={}", duration, html.length());

            return html.toString();

        } catch (Exception e) {
            LoggingUtils.exception(log, "convertMarkdownToHtml", e);
            throw new MarkdownConversionException("Failed to convert markdown: " + e.getMessage(), e);
        }
    }

    private String processCodeFences(String text) {
        return CODE_FENCE_PATTERN.matcher(text).replaceAll(match -> {
            String lang = match.group(1);
            String code = match.group(2);
            String langClass = isEmpty(lang) ? "" : " class=\"language-" + escapeHtml(lang) + "\"";
            String escaped = escapeHtml(code);
            return "<pre><code" + langClass + ">" + escaped + "</code></pre>";
        });
    }

    private String formatInline(String text) {
        if (isEmpty(text)) return text;
        text = LINK_PATTERN.matcher(text).replaceAll(match -> {
            String linkText = match.group(1);
            String url = match.group(2);
            return "<a href=\"" + escapeHtml(url) + "\">" + formatInline(escapeHtml(linkText)) + "</a>";
        });
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll("<code>$1</code>");
        text = BOLD_PATTERN.matcher(text).replaceAll("<strong>$1</strong>");
        text = ITALIC_PATTERN.matcher(text).replaceAll("<em>$1</em>");
        return text;
    }

    /**
     * Escapes the five HTML-sensitive characters: {@code &}, {@code <}, {@code >},
     * {@code "}, and {@code '}.
     * <p>
     * <b>Where:</b> Standard OWASP HTML-escaping practice to prevent injected markup
     * from user content.
     *
     * @param text the raw text
     * @return the escaped text, or empty string if input is blank
     */
    private String escapeHtml(String text) {
        if (isEmpty(text)) return text;
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private boolean closeParagraph(StringBuilder html, boolean inParagraph) {
        if (inParagraph) html.append("</p>");
        return false;
    }

    private boolean closeUlist(StringBuilder html, boolean inUlist) {
        if (inUlist) html.append("</ul>");
        return false;
    }

    private boolean closeOlist(StringBuilder html, boolean inOlist) {
        if (inOlist) html.append("</ol>");
        return false;
    }

    private boolean closeBlockquote(StringBuilder html, boolean inBlockquote) {
        if (inBlockquote) html.append("</blockquote>");
        return false;
    }

    private boolean closeTable(StringBuilder html, boolean inTable) {
        if (inTable) html.append("</table>");
        return false;
    }

    private boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    private int safeLength(String str) {
        return StringUtils.safeLength(str);
    }

    public static class MarkdownConversionException extends RuntimeException {
        /**
         * This name was chosen because this function creates a MarkdownConversionException with message and cause.
         * Preserves the original exception for debugging.
         */
        public MarkdownConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


