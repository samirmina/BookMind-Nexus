package com.bookassistant.service.file;

import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Detects whether a text chunk likely contains misinterpreted math formulas
 * from PDFBox extraction.
 * <p>
 * <b>What:</b> Heuristic detector for LaTeX-like artifacts in PDFBox output.
 * <p>
 * <b>Why:</b> PDFBox MathAwareTextStripper can produce garbled math notation
 * when the source PDF contains actual formulas. This detector identifies such
 * pages so they can be re-extracted with Pix2Text instead.
 * <p>
 * <b>Where:</b> Used by {@link OnDemandExtractionService} in hybrid mode.
 */
@Component
public class MathFormulaDetector {

    private static final Logger log = LoggerFactory.getLogger(MathFormulaDetector.class);

    // LaTeX command patterns that indicate misinterpreted math
    private static final String LATEX_COMMAND = "\\\\(?:frac|sin|cos|tan|log|sqrt|sum|int|partial|mathrm|mathbf|mathbb|mathcal|mathit|gamma|alpha|beta|theta|lambda|sigma|omega|otimes|oplus|times|div|neq|leq|geq|approx|equiv|rightarrow|leftarrow|cdot|ldots|cdots|vdots|ddots|frac|binom|sqrt|text|overline|underline|hat|vec|dot|ddot|bar|tilde|prime|prime|infty|nabla|forall|exists|in|subset|supset|cup|cap|emptyset|mathbb|mathcal|mathfrak|mathscr|mathsf|mathtt|mathit|mathbf|mathrm|textrm|textit|textsf|texttt|textsl|textsc|textmd|textup|textnormal|textbf|textit|textrm|textsf|texttt|textsl|textsc|textmd|textup|textnormal|emph|underline|overline|hat|vec|dot|ddot|bar|tilde|prime|grave|acute|check|breve|ring|ddot|tilde|hat|vec|dot|ddot|bar|tilde|prime|grave|acute|check|breve|ring|widehat|widetilde|overrightarrow|overleftarrow|underrightarrow|underleftarrow|underleftrightarrow|overleftrightarrow|underleftrightarrow|hookrightarrow|hookleftarrow|mapsto|longmapsto|to|gets|leftrightarrows|rightleftarrows|leftrightsquigarrow|rightsquigarrow|leadsto|longrightarrow|longleftarrow|longmapsto|longleftrightarrow|Longrightarrow|Longleftarrow|Longleftrightarrow|dots|cdots|ldots|vdots|ddots|cdots|iddots|adots|bdots|cdots|iddots|adots|bdots|cdots|iddots|adots|bdots)";

    // Math mode delimiters
    private static final String MATH_DELIMITER = "\\$\\$|\\$";

    // LaTeX grouping braces that appear in plain text
    private static final String LATEX_BRACES = "[{}]";

    // Superscript/subscript indicators
    private static final String SUPERSUB = "[\\^_]";

    // Broken math Unicode indicators (PDFBox often replaces LaTeX with these)
    private static final String BROKEN_MATH_UNICODE = "[≠ΩÎÔ∑∏∫∂∇∈⊂⊃∪∩≤≥≈≡±∓×÷⋯⋮⋰⋱]";

    private static final java.util.regex.Pattern LATEX_COMMAND_PATTERN = java.util.regex.Pattern.compile(LATEX_COMMAND);
    private static final java.util.regex.Pattern MATH_DELIMITER_PATTERN = java.util.regex.Pattern.compile(MATH_DELIMITER);
    private static final java.util.regex.Pattern LATEX_BRACES_PATTERN = java.util.regex.Pattern.compile(LATEX_BRACES);
    private static final java.util.regex.Pattern SUPERSUB_PATTERN = java.util.regex.Pattern.compile(SUPERSUB);
    private static final java.util.regex.Pattern BROKEN_MATH_UNICODE_PATTERN = java.util.regex.Pattern.compile(BROKEN_MATH_UNICODE);

    /**
     * Checks whether the given text likely contains misinterpreted math formulas
     * from PDFBox extraction.
     *
     * @param text the extracted text to check
     * @return true if the text likely contains misinterpreted math
     */
    public boolean hasMisinterpretedMath(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        // Check for LaTeX commands like \frac, \sin, etc.
        if (LATEX_COMMAND_PATTERN.matcher(text).find()) {
            log.debug("Detected LaTeX command in PDFBox output | preview={}", truncate(text, 200));
            return true;
        }

        // Check for math mode delimiters
        if (MATH_DELIMITER_PATTERN.matcher(text).find()) {
            log.debug("Detected math delimiter in PDFBox output | preview={}", truncate(text, 200));
            return true;
        }

        // Check for isolated LaTeX-style braces in text
        if (LATEX_BRACES_PATTERN.matcher(text).find()) {
            log.debug("Detected LaTeX braces in PDFBox output | preview={}", truncate(text, 200));
            return true;
        }

        // Check for superscript/subscript indicators
        if (SUPERSUB_PATTERN.matcher(text).find()) {
            log.debug("Detected superscript/subscript indicator in PDFBox output | preview={}", truncate(text, 200));
            return true;
        }

        // Check for broken Unicode math symbols from PDFBox
        if (BROKEN_MATH_UNICODE_PATTERN.matcher(text).find()) {
            log.info(">>> MATH DETECTED (Broken Unicode) in page text. Routing to Pix2Text! Preview: {}", truncate(text, 100));
            return true;
        }

        return false;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
