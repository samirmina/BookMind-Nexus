package com.bookassistant.service.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds a per-font decode map from a PDF's actual font encodings.
 * <p>
 * <b>What:</b> Inspects every font in the document. For fonts with a custom
 * {@code /Differences} array, it maps character codes to glyph names and then
 * to Unicode via the Adobe Glyph List (AGL). This is the book's <i>real</i>
 * encoding, not PDFBox's default WinAnsi/ToUnicode fallback which is often
 * wrong for math/symbol fonts.
 * <p>
 * <b>Why:</b> The PDF text layer is only reliable if decoded with the font's
 * intended encoding. A broken ToUnicode CMap maps symbols to wrong characters
 * (e.g. code 0x23 in a math font becomes {@code €} instead of {@code Σ}).
 * By reading the {@code /Differences} array directly, we recover the symbol's
 * true identity.
 * <p>
 * <b>Where:</b> Used by {@link org.apache.pdfbox.text.MathAwareTextStripper} during per-page
 * text extraction to correct the Unicode stream before it reaches the AI.
 */
public class PdfEncodingDecoder {

    private static final Logger log = LoggerFactory.getLogger(PdfEncodingDecoder.class);

    private final Map<String, Map<Integer, String>> fontDecodingMaps = new HashMap<>();

    public PdfEncodingDecoder(PDDocument document) {
        inspectFonts(document);
    }

    /**
     * Returns the corrected Unicode for a character from a specific font.
     * If no correction is needed, returns the original Unicode.
     */
    public String decode(String originalUnicode, PDFont font, int[] codes) {
        if (originalUnicode == null || codes == null || codes.length == 0) {
            return originalUnicode;
        }

        String fontKey = getFontKey(font);
        Map<Integer, String> fontMap = fontDecodingMaps.get(fontKey);
        if (fontMap == null || fontMap.isEmpty()) {
            return originalUnicode;
        }

        StringBuilder result = new StringBuilder();
        for (int code : codes) {
            String decoded = fontMap.get(code);
            if (decoded != null) {
                result.append(decoded);
            } else {
                result.append(Character.toChars(code));
            }
        }
        return result.toString();
    }

    /**
     * Returns true if this decoder found at least one custom encoding.
     */
    public boolean hasCustomEncodings() {
        return !fontDecodingMaps.isEmpty();
    }

    private void inspectFonts(PDDocument document) {
        Set<String> seenFontKeys = new HashSet<>();
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (COSName fontName : resources.getFontNames()) {
                PDFont font;
                try {
                    font = resources.getFont(fontName);
                } catch (IOException e) {
                    log.debug("Could not load font {}: {}", fontName, e.getMessage());
                    continue;
                }
                if (font == null) continue;
                String key = getFontKey(font);
                if (seenFontKeys.contains(key)) continue;
                seenFontKeys.add(key);
                buildFontMap(font);
            }
        }
        log.info("PdfEncodingDecoder | fontsWithCustomEncoding={}", fontDecodingMaps.size());
        for (var entry : fontDecodingMaps.entrySet()) {
            log.info("  Font: {} | customCodes={}", entry.getKey(), entry.getValue().keySet());
        }
    }

    private void buildFontMap(PDFont font) {
        COSDictionary encodingDict = getEncodingDictionary(font);
        if (encodingDict == null) return;

        COSArray differences = (COSArray) encodingDict.getDictionaryObject(COSName.DIFFERENCES);
        if (differences == null) return;

        String key = getFontKey(font);
        Map<Integer, String> codeMap = new HashMap<>();

        int startCode = -1;
        for (COSBase item : differences) {
            if (item instanceof COSInteger) {
                startCode = ((COSInteger) item).intValue();
            } else if (item instanceof COSName && startCode >= 0) {
                String glyphName = ((COSName) item).getName();
                String unicode = normalizeGlyphName(glyphName);
                if (unicode != null && !unicode.isEmpty()) {
                    codeMap.put(startCode, unicode);
                }
                startCode++;
            }
        }

        if (!codeMap.isEmpty()) {
            fontDecodingMaps.put(key, codeMap);
            log.info("Built encoding map for font {} | entries={}", key, codeMap.size());
        }
    }

    private String normalizeGlyphName(String glyphName) {
        String unicode = GlyphList.getAdobeGlyphList().toUnicode(glyphName);
        if (unicode != null && !unicode.isEmpty()) {
            return unicode;
        }

        String base = glyphName;
        String[] suffixes = {
            "display", "text", "math",
            "bigg", "Big", "big", "lg", "rm",
            "tp", "bt"
        };
        for (String suffix : suffixes) {
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }

        unicode = GlyphList.getAdobeGlyphList().toUnicode(base);
        if (unicode != null && !unicode.isEmpty()) {
            return unicode;
        }

        return MATH_FALLBACK.get(base);
    }

    private COSDictionary getEncodingDictionary(PDFont font) {
        try {
            COSDictionary dict = font.getCOSObject();
            if (dict == null) return null;
            COSBase encoding = dict.getDictionaryObject(COSName.ENCODING);
            if (encoding instanceof COSDictionary) {
                return (COSDictionary) encoding;
            }
        } catch (Exception e) {
            log.debug("Could not read encoding for font: {}", e.getMessage());
        }
        return null;
    }

    private String getFontKey(PDFont font) {
        if (font instanceof PDType1Font type1) {
            return type1.getBaseFont();
        }
        try {
            COSDictionary dict = font.getCOSObject();
            if (dict != null) {
                String baseFont = dict.getString(COSName.BASE_FONT);
                if (baseFont != null) return baseFont;
            }
        } catch (Exception e) {
            // ignore
        }
        return font.toString();
    }

    private static final Map<String, String> MATH_FALLBACK = Map.ofEntries(
        Map.entry("summation", "\u2211"),
        Map.entry("product", "\u220F"),
        Map.entry("radical", "\u221A"),
        Map.entry("integral", "\u222B"),
        Map.entry("bracketleft", "["),
        Map.entry("bracketright", "]"),
        Map.entry("parenleft", "("),
        Map.entry("parenright", ")"),
        Map.entry("braceleft", "{"),
        Map.entry("braceright", "}"),
        Map.entry("minus", "\u2212"),
        Map.entry("element", "\u2208"),
        Map.entry("arrowleft", "\u2190"),
        Map.entry("intersection", "\u2229"),
        Map.entry("union", "\u222A"),
        Map.entry("greaterequal", "\u2265"),
        Map.entry("nabla", "\u2207"),
        Map.entry("prime", "\u2032"),
        Map.entry("latticetop", "\u22A4"),
        Map.entry("asteriskmath", "\u2217"),
        Map.entry("multiply", "\u00D7"),
        Map.entry("divide", "\u00F7"),
        Map.entry("plusminus", "\u00B1"),
        Map.entry("notequal", "\u2260"),
        Map.entry("lessequal", "\u2264"),
        Map.entry("approxequal", "\u2248"),
        Map.entry("degree", "\u00B0"),
        Map.entry("infinity", "\u221E"),
        Map.entry("partialdiff", "\u2202"),
        Map.entry("sigma", "\u03C3"),
        Map.entry("mu", "\u03BC"),
        Map.entry("theta", "\u03B8"),
        Map.entry("alpha", "\u03B1"),
        Map.entry("beta", "\u03B2"),
        Map.entry("gamma", "\u03B3"),
        Map.entry("delta", "\u03B4"),
        Map.entry("epsilon", "\u03B5"),
        Map.entry("lambda", "\u03BB"),
        Map.entry("omega", "\u03C9")
    );
}
