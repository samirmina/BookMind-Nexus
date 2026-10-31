package com.bookassistant.service.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link PDFTextStripper} that corrects Unicode using the PDF's actual font
 * encodings and reconstructs positioned math glyphs (super/subscripts) only
 * for math/symbol fonts.
 * <p>
 * <b>What:</b> Overrides {@link #processTextPosition(TextPosition)} to intercept
 * each character, look up the font-specific correct Unicode from
 * {@link PdfEncodingDecoder}, and capture position data for math reconstruction.
 * <p>
 * <b>Why:</b> Math-heavy PDFs often embed symbol fonts with broken ToUnicode
 * maps. Additionally, subscripts/superscripts are positioned glyphs that
 * PDFBox extracts as flat text. By analyzing Y-offsets AND font size, we
 * recover the intended vertical layout without mangling normal ascenders/descenders.
 * <p>
 * <b>Where:</b> Used by {@link com.bookassistant.service.file.FileParserService}
 * and {@link com.bookassistant.service.file.OnDemandExtractionService} during
 * per-page extraction.
 */
public class CustomMathAwareTextStripper extends PDFTextStripper {

    private static final Logger log = LoggerFactory.getLogger(CustomMathAwareTextStripper.class);
    private static final float Y_OFFSET_THRESHOLD = 0.35f;
    private static final float SIZE_RATIO_THRESHOLD = 0.85f;

    private final PdfEncodingDecoder decoder;
    private final java.util.List<GlyphSnapshot> glyphs = new java.util.ArrayList<>();

    public CustomMathAwareTextStripper(PdfEncodingDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        String unicodeToStore = text.getUnicode();

        if (decoder != null) {
            PDFont font = text.getFont();
            int[] codes = text.getCharacterCodes();
            String originalUnicode = text.getUnicode();
            if (codes != null && codes.length > 0 && font != null && originalUnicode != null && !originalUnicode.isEmpty()) {
                String corrected = decoder.decode(originalUnicode, font, codes);
                if (!corrected.equals(originalUnicode)) {
                    log.debug("Unicode correction applied | original={} | corrected={}", originalUnicode, corrected);
                    unicodeToStore = corrected;
                }
            }
        }

        glyphs.add(new GlyphSnapshot(
            text.getX(),
            text.getYDirAdj(),
            text.getWidth(),
            text.getFontSizeInPt(),
            unicodeToStore,
            text.getFont() != null ? text.getFont().getName() : ""
        ));
        super.processTextPosition(text);
    }

    @Override
    public String getText(PDDocument doc) throws IOException {
        glyphs.clear();
        StringWriter writer = new StringWriter();
        writeText(doc, writer);
        return buildMathText();
    }

    private String buildMathText() {
        if (glyphs.isEmpty()) {
            return "";
        }

        float baselineY = computeBaselineY();
        float baselineFontSize = computeBaselineFontSize();
        StringBuilder result = new StringBuilder();
        GlyphSnapshot prev = null;

        for (GlyphSnapshot glyph : glyphs) {
            if (prev != null) {
                float gap = glyph.x - (prev.x + prev.width);
                if (gap > baselineFontSize * 0.25f) {
                    result.append(' ');
                }
            }

            float yOffset = glyph.y - baselineY;
            float normalizedOffset = baselineFontSize > 0 ? yOffset / baselineFontSize : 0f;
            float normalizedSize = baselineFontSize > 0 ? glyph.fontSize / baselineFontSize : 1f;

            String unicode = glyph.unicode;
            boolean isMathFont = isMathFont(glyph.fontName);
            boolean isSuperscript = false;
            boolean isSubscript = false;

            if (isMathFont) {
                isSuperscript = normalizedOffset < -Y_OFFSET_THRESHOLD && normalizedSize < SIZE_RATIO_THRESHOLD;
                isSubscript = normalizedOffset > Y_OFFSET_THRESHOLD && normalizedSize < SIZE_RATIO_THRESHOLD;
            }

            if (isSuperscript) {
                unicode = convertToSuperscript(unicode);
            } else if (isSubscript) {
                unicode = convertToSubscript(unicode);
            }

            result.append(unicode);
            prev = glyph;
        }

        return result.toString();
    }

    private boolean isMathFont(String fontName) {
        if (fontName == null || fontName.isEmpty()) {
            return false;
        }
        String lower = fontName.toLowerCase();
        return lower.contains("math") || lower.contains("symbol") || lower.contains("extension");
    }

    private float computeBaselineY() {
        Map<Float, Integer> yCounts = new HashMap<>();
        float maxCount = 0;
        float baselineY = 0f;

        for (GlyphSnapshot glyph : glyphs) {
            float bucket = Math.round(glyph.y);
            int count = yCounts.getOrDefault(bucket, 0) + 1;
            yCounts.put(bucket, count);
            if (count > maxCount) {
                maxCount = count;
                baselineY = bucket;
            }
        }

        return baselineY;
    }

    private float computeBaselineFontSize() {
        Map<Float, Integer> sizeCounts = new HashMap<>();
        float maxCount = 0;
        float baselineFontSize = 0f;

        for (GlyphSnapshot glyph : glyphs) {
            float bucket = Math.round(glyph.fontSize * 10f) / 10f;
            int count = sizeCounts.getOrDefault(bucket, 0) + 1;
            sizeCounts.put(bucket, count);
            if (count > maxCount) {
                maxCount = count;
                baselineFontSize = bucket;
            }
        }

        return baselineFontSize > 0 ? baselineFontSize : 10f;
    }

    private String convertToSuperscript(String unicode) {
        StringBuilder result = new StringBuilder();
        for (char c : unicode.toCharArray()) {
            String sup = SUPERSCRIPTS.get(c);
            if (sup != null) {
                result.append(sup);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String convertToSubscript(String unicode) {
        StringBuilder result = new StringBuilder();
        for (char c : unicode.toCharArray()) {
            String sub = SUBSCRIPTS.get(c);
            if (sub != null) {
                result.append(sub);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private record GlyphSnapshot(float x, float y, float width, float fontSize, String unicode, String fontName) {
    }

    private static final Map<Character, String> SUPERSCRIPTS = new HashMap<>();
    static {
        SUPERSCRIPTS.put('0', "\u2070");
        SUPERSCRIPTS.put('1', "\u00B9");
        SUPERSCRIPTS.put('2', "\u00B2");
        SUPERSCRIPTS.put('3', "\u00B3");
        SUPERSCRIPTS.put('4', "\u2074");
        SUPERSCRIPTS.put('5', "\u2075");
        SUPERSCRIPTS.put('6', "\u2076");
        SUPERSCRIPTS.put('7', "\u2077");
        SUPERSCRIPTS.put('8', "\u2078");
        SUPERSCRIPTS.put('9', "\u2079");
        SUPERSCRIPTS.put('i', "\u2071");
        SUPERSCRIPTS.put('n', "\u207F");
        SUPERSCRIPTS.put('+', "\u207A");
        SUPERSCRIPTS.put('-', "\u207B");
        SUPERSCRIPTS.put('=', "\u207C");
    }

    private static final Map<Character, String> SUBSCRIPTS = new HashMap<>();
    static {
        SUBSCRIPTS.put('0', "\u2080");
        SUBSCRIPTS.put('1', "\u2081");
        SUBSCRIPTS.put('2', "\u2082");
        SUBSCRIPTS.put('3', "\u2083");
        SUBSCRIPTS.put('4', "\u2084");
        SUBSCRIPTS.put('5', "\u2085");
        SUBSCRIPTS.put('6', "\u2086");
        SUBSCRIPTS.put('7', "\u2087");
        SUBSCRIPTS.put('8', "\u2088");
        SUBSCRIPTS.put('9', "\u2089");
        SUBSCRIPTS.put('a', "\u2090");
        SUBSCRIPTS.put('e', "\u2091");
        SUBSCRIPTS.put('i', "\u1D62");
        SUBSCRIPTS.put('j', "\u2C7C");
        SUBSCRIPTS.put('k', "\u2096");
        SUBSCRIPTS.put('l', "\u2097");
        SUBSCRIPTS.put('m', "\u2098");
        SUBSCRIPTS.put('n', "\u2099");
        SUBSCRIPTS.put('o', "\u2092");
        SUBSCRIPTS.put('p', "\u209A");
        SUBSCRIPTS.put('r', "\u1D63");
        SUBSCRIPTS.put('s', "\u209B");
        SUBSCRIPTS.put('t', "\u209C");
        SUBSCRIPTS.put('u', "\u1D64");
        SUBSCRIPTS.put('v', "\u1D65");
    }
}
