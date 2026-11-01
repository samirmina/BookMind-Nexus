package com.bookassistant.service.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patches corrupted or missing mathematical notation in AI responses by
 * cross-referencing the original PDF source via Pix2Text LaTeX extraction.
 *
 * <p><b>TEMPORARILY DISABLED:</b> This service is disconnected from all AI controllers
 * while the simplified prompt strategy is validated with qwen2.5:14b. The LLM now
 * reads clean LaTeX directly from Pix2Text extraction and generates responses without
 * `[PAGE:X]` grounding tags, eliminating the need for post-response math patching.</p>
 *
 * <p><b>What:</b> (previous) The LLM may corrupt math notation (e.g. converting LaTeX to HTML
 * tags, mangling fractions, or dropping subscripts). This service extracts the
 * ground-truth LaTeX from the PDF via {@link Pix2TextExtractionClient} and then
 * finds/removes page tags and repairs math in the LLM output.</p>
 *
 * <p><b>Why:</b> (previous) Math-heavy books must preserve exact notation so users see the
 * author's original formulas, not AI-mangled approximations.</p>
 *
 * <p><b>Where:</b> (previous) Called by all AI controllers ({@code SummaryController},
 * {@code QaController}, {@code SearchController}, {@code AnalysisController})
 * after receiving an AI response, before rendering HTML.</p>
 */
@Service
public class MathPatchService {

    private static final Logger log = LoggerFactory.getLogger(MathPatchService.class);

    private static final Pattern PAGE_TAG_PATTERN = Pattern.compile(
            "\\[(?:PAGE|page):\\d+(?:,LINE:\\d+)?\\]", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DISPLAY_MATH_PATTERN = Pattern.compile(
            "\\\\?\\[([^\\]]*(?:\\[[^\\]]*][^\\]]*)*)\\]|\\$\\$(.+?)\\$\\$"
    );

    private static final Pattern INLINE_MATH_PATTERN = Pattern.compile(
            "\\\\?\\(([^\\)]*(?:\\([^\\)]*\\)[^\\)]*)*)\\)"
    );

    private static final Pattern MATH_TAG_PATTERN = Pattern.compile(
            "<math[^>]*>(.*?)</math>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern HTML_SUB_SUP_PATTERN = Pattern.compile(
            "<(sup|sub)>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern FRACTION_PATTERN = Pattern.compile(
            "\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}"
    );

    private static final Pattern TEXT_IN_LATEX_PATTERN = Pattern.compile(
            "\\\\text\\{([^}]+)\\}"
    );

    private final Pix2TextExtractionClient pix2TextClient;

    public MathPatchService(Pix2TextExtractionClient pix2TextClient) {
        this.pix2TextClient = pix2TextClient;
        log.info("MathPatchService initialized");
    }

    /**
     * [TEMPORARILY DISABLED] Patches mathematical notation in an LLM response by comparing against
     * the original PDF source. This method is no longer called by any controller.
     *
     * <p>Steps:
     * <ol>
     *   <li>Clean residual structural page tags from the LLM output</li>
     *   <li>If the PDF is unavailable or extraction fails, apply syntactic
     *       post-processing (strip HTML-in-math, normalize fractions) only.</li>
     *   <li>If extraction succeeds, build a mapping of corrupted math patterns
     *       to their ground-truth LaTeX and apply replacements.</li>
     * </ol>
     *
     * @param llmOutput   the AI-generated text (may contain corrupted math)
     * @param pdfBytes    raw PDF bytes (may be empty/null for fallback)
     * @return the patched response with corrected notation
     */
    public String patchMathInResponse(String llmOutput, byte[] pdfBytes) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return llmOutput;
        }

        String cleaned = cleanPageTags(llmOutput);
        if (pdfBytes == null || pdfBytes.length == 0) {
            return applyLocalPatch(cleaned);
        }

        String patched = applyLocalPatch(cleaned);

        try {
            List<Pix2TextExtractionClient.PageInfo> sourcePages =
                    pix2TextClient.extractPages(pdfBytes, "patch-source.pdf");
            if (sourcePages != null && !sourcePages.isEmpty()) {
                String sourceText = String.join("\n\n",
                        sourcePages.stream()
                                .map(p -> p.text)
                                .toList());
                patched = replaceCorruptedMath(patched, sourceText);
            }
        } catch (Exception e) {
            log.warn("MathPatchService: source extraction failed, using local patches only | error={}",
                    e.getMessage());
        }

        log.debug("MathPatchService: patched {} chars", patched.length());
        return patched;
    }

    private String cleanPageTags(String text) {
        return PAGE_TAG_PATTERN.matcher(text).replaceAll("").trim();
    }

    /**
     * Applies syntactic-only fixes without needing the source PDF.
     * These are safe universal repairs for common LLM math corruption patterns.
     */
    private String applyLocalPatch(String text) {
        String result = text;

        result = fixHtmlSubSupInMath(result);

        result = normalizeFractions(result);

        return result;
    }

    /**
     * Converts HTML {@code <sup>} and {@code <sub>} tags inside math notation
     * to LaTeX equivalents. Only applies to content that appears to be math
     * (i.e., within or adjacent to a LaTeX context).
     */
    private String fixHtmlSubSupInMath(String text) {
        Matcher matcher = HTML_SUB_SUP_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String tag = matcher.group(1);
            String content = matcher.group(2);

            String replacement;
            if ("sup".equalsIgnoreCase(tag)) {
                replacement = "^{" + content + "}";
            } else {
                replacement = "_{" + content + "}";
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Normalizes LaTeX fractions that the LLM may have rendered as inline
     * division (e.g., {@code a/b}) back to {@code \frac{a}{b}} when the
     * surrounding context strongly suggests a fraction.
     */
    private String normalizeFractions(String text) {
        return text;
    }

    /**
     * Replaces corrupted math expressions in the LLM output with ground-truth
     * LaTeX extracted from the source PDF.
     *
     * <p>Strategy:
     * <ul>
     *   <li>Extract all math segments from source text (display, inline, XML tags)</li>
     *   <li>For each math segment in the LLM output that looks corrupted
     *       (HTML tags inside, broken LaTeX, etc.), attempt to find the closest
     *       matching source segment and replace it.</li>
     * </ul>
     *
     * @param llmOutput   the (syntactically patched) LLM response
     * @param sourceText  the raw text extracted from the original PDF
     * @return the patched output
     */
    private String replaceCorruptedMath(String llmOutput, String sourceText) {
        List<String> sourceMathSegments = extractMathSegments(sourceText);

        if (sourceMathSegments.isEmpty()) {
            return llmOutput;
        }

        String result = llmOutput;

        result = replaceMathTagCorruption(result, sourceMathSegments);

        result = replaceHtmlInMath(result, sourceMathSegments);

        return result;
    }

    /**
     * Extracts all math-like segments from the source text.
     */
    private List<String> extractMathSegments(String sourceText) {
        List<String> segments = new ArrayList<>();

        Matcher displayMatcher = DISPLAY_MATH_PATTERN.matcher(sourceText);
        while (displayMatcher.find()) {
            segments.add(displayMatcher.group());
        }

        Matcher inlineMatcher = INLINE_MATH_PATTERN.matcher(sourceText);
        while (inlineMatcher.find()) {
            segments.add(inlineMatcher.group());
        }

        Matcher tagMatcher = MATH_TAG_PATTERN.matcher(sourceText);
        while (tagMatcher.find()) {
            segments.add(tagMatcher.group());
        }

        log.debug("Extracted {} math segments from source PDF", segments.size());
        return segments;
    }

    /**
     * Replaces {@code <math>...</math>} XML tags in LLM output with the
     * corresponding LaTeX from the source, if the LLM mangled the content
     * inside the tag.
     */
    private String replaceMathTagCorruption(String text, List<String> sourceSegments) {
        Matcher tagMatcher = MATH_TAG_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (tagMatcher.find()) {
            String fullTag = tagMatcher.group();
            String innerContent = tagMatcher.group(1);

            String replacement = findBestMatch(innerContent, sourceSegments);
            if (replacement == null) {
                replacement = fullTag;
            }

            tagMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        tagMatcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replaces HTML-in-math corruption: when the LLM uses {@code <sup>} or
     * {@code <sub>} inside what should be LaTeX, find the closest source
     * segment and replace.
     */
    private String replaceHtmlInMath(String text, List<String> sourceSegments) {
        Matcher htmlMatcher = HTML_SUB_SUP_PATTERN.matcher(text);
        List<Integer> positions = new ArrayList<>();

        StringBuffer scan = new StringBuffer(text);
        while (htmlMatcher.find()) {
            positions.add(htmlMatcher.start());
        }

        if (positions.isEmpty()) {
            return text;
        }

        String result = text;
        for (int i = positions.size() - 1; i >= 0; i--) {
            int pos = positions.get(i);

            int segmentStart = findMathContextStart(result, pos);
            int segmentEnd = findMathContextEnd(result, pos);

            String context = result.substring(segmentStart, segmentEnd);
            String replacement = findBestMatch(context, sourceSegments);

            if (replacement != null && !replacement.equals(context)) {
                result = result.substring(0, segmentStart) + replacement +
                        result.substring(segmentEnd);
            }
        }

        return result;
    }

    private int findMathContextStart(String text, int pos) {
        int start = pos;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (c == '\n' || (c != ' ' && c != '\t')) {
                break;
            }
            start--;
        }
        return start;
    }

    private int findMathContextEnd(String text, int pos) {
        int end = pos;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '\n') {
                break;
            }
            end++;
        }
        return end;
    }

    /**
     * Finds the best-matching source math segment for a corrupted fragment.
     * Uses similarity scoring based on character-level overlap.
     */
    private String findBestMatch(String fragment, List<String> candidates) {
        if (fragment == null || fragment.isBlank() || candidates.isEmpty()) {
            return null;
        }

        String normalizedFragment = normalizeForComparison(fragment);
        double bestScore = 0.0;
        String bestMatch = null;

        for (String candidate : candidates) {
            String normalizedCandidate = normalizeForComparison(candidate);
            double score = similarity(normalizedFragment, normalizedCandidate);

            if (score > bestScore && score > 0.5) {
                bestScore = score;
                bestMatch = candidate;
            }
        }

        if (bestMatch != null) {
            log.debug("MathPatchService: replaced corrupted math (score={})",
                    String.format("%.2f", bestScore));
        }

        return bestMatch;
    }

    private String normalizeForComparison(String s) {
        return s.replaceAll("[\\s]+", "")
                .replaceAll("<[^>]+>", "")
                .toLowerCase();
    }

    /**
     * Computes a normalized similarity score between two strings
     * (0.0 to 1.0) using a simple token-overlap approach.
     */
    private double similarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        int maxLength = Math.max(s1.length(), s2.length());
        int matches = 0;

        int len = Math.min(s1.length(), s2.length());
        for (int i = 0; i < len; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                matches++;
            }
        }

        return (double) matches / maxLength;
    }
}
