package com.bookassistant.service.file;

import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.config.RuntimeConfigService;
import com.bookassistant.service.infrastructure.ExtractionCacheService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.service.file.PdfEncodingDecoder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import com.bookassistant.service.file.CustomMathAwareTextStripper;
import com.bookassistant.service.file.PdfEncodingDecoder;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates the full extraction pipeline for AI features:
 * PDFBox base text → Pix2Text math correction → validation.
 * <p>
 * <b>What:</b> Fetches a page range, extracts base text with PDFBox,
 * detects misinterpreted math formulas, re-extracts only those pages
 * with Pix2Text, replaces the bad math, and validates the final chunk.
 * <p>
 * <b>Why:</b> PDFBox gives clean readable text but mangles math.
 * Pix2Text gives correct LaTeX math but can be heavy on formulas.
 * This coordinator combines both: PDFBox for normal text, Pix2Text
 * only for pages with misinterpreted math.
 * <p>
 * <b>Where:</b> Used by AI controllers (summary, QA, search, analysis)
 * to ensure the chunk sent to the AI is accurate and complete.
 */
@Service
public class ChunkExtractionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ChunkExtractionCoordinator.class);
    private static final String COMPONENT = "ChunkExtractionCoordinator";

    private final Pix2TextExtractionClient pix2textClient;
    private final ExtractionCacheService extractionCacheService;
    private final RuntimeLogService runtimeLogService;
    private final MathFormulaDetector mathFormulaDetector;
    private final RuntimeConfigService runtimeConfigService;

    public ChunkExtractionCoordinator(Pix2TextExtractionClient pix2textClient,
                                      ExtractionCacheService extractionCacheService,
                                      RuntimeLogService runtimeLogService,
                                      MathFormulaDetector mathFormulaDetector,
                                      RuntimeConfigService runtimeConfigService) {
        this.pix2textClient = pix2textClient;
        this.extractionCacheService = extractionCacheService;
        this.runtimeLogService = runtimeLogService;
        this.mathFormulaDetector = mathFormulaDetector;
        this.runtimeConfigService = runtimeConfigService;
        log.info("ChunkExtractionCoordinator initialized");
    }

    /**
     * Extracts a validated page range using the PDFBox + Pix2Text pipeline.
     * <p>
     * <b>What:</b> Extracts base text with PDFBox, detects math pages,
     * re-extracts math pages with Pix2Text, replaces them, and validates.
     * <p>
     * <b>Why:</b> Ensures AI features receive accurate text with correct
     * math notation, not pure Pix2Text output which can be math-heavy.
     *
     * @param pdfBytes raw PDF content
     * @param startPage 1-based start page (inclusive)
     * @param endPage 1-based end page (inclusive)
     * @return validated BookData with mixed PDFBox/Pix2Text pages
     * @throws IOException if PDF cannot be read
     */
    public BookData extractValidatedChunk(byte[] pdfBytes, int startPage, int endPage) throws IOException {
        LoggingUtils.enter(log, "extractValidatedChunk", "range", startPage + "-" + endPage);
        long startTime = System.currentTimeMillis();

        // Step 1: Extract base text with PDFBox
        List<PageData> pdfBoxPages = extractWithPdfBox(pdfBytes, startPage, endPage);
        if (pdfBoxPages.isEmpty()) {
            log.warn("PDFBox extraction returned no pages | range={}-{}", startPage, endPage);
            return new BookData("pages-" + startPage + "-" + endPage, List.of(), "");
        }

        String mode = runtimeConfigService.getExtractionMode();
        if ("model-only".equalsIgnoreCase(mode)) {
            log.info("Model Only mode: returning PDFBox text directly | range={}-{}", startPage, endPage);
            String fullText = combineText(pdfBoxPages);
            LoggingUtils.exit(log, "extractValidatedChunk", "duration", (System.currentTimeMillis() - startTime) + "ms");
            return new BookData("pages-" + startPage + "-" + endPage, pdfBoxPages, fullText);
        }

        // Step 2: Detect pages with misinterpreted math
        List<int[]> mathPageRanges = findMathPageRanges(pdfBoxPages);
        if (mathPageRanges.isEmpty()) {
            log.info("No math formulas detected, using PDFBox text for all pages");
            String fullText = combineText(pdfBoxPages);
            LoggingUtils.exit(log, "extractValidatedChunk", "duration", (System.currentTimeMillis() - startTime) + "ms");
            return new BookData("pages-" + startPage + "-" + endPage, pdfBoxPages, fullText);
        }

        log.info("Detected math formulas in {} page range(s), re-extracting with Pix2Text", mathPageRanges.size());

        // Step 3: Re-extract math pages with Pix2Text
        log.info("Math page ranges detected | ranges={} | inputRange={}-{}",
                mathPageRanges.size(), startPage, endPage);
        for (int[] r : mathPageRanges) {
            log.info("  Math range: {}-{}", r[0], r[1]);
        }
        Map<Integer, PageData> pix2TextPages = extractMathPagesWithPix2Text(pdfBytes, mathPageRanges);

        // Step 4: Replace PDFBox pages with Pix2Text pages where available and sanitize text
        List<PageData> result = new ArrayList<>();
        for (PageData page : pdfBoxPages) {
            PageData replacement = pix2TextPages.get(page.pageNumber());
            String text = (replacement != null && replacement.hasText()) ? replacement.text() : page.text();
            result.add(new PageData(page.pageNumber(), sanitizeExtractedText(text)));
        }

        // Step 5: Validate final chunk
        validateChunk(result, startPage, endPage);

        String fullText = combineText(result);
        LoggingUtils.exit(log, "extractValidatedChunk", "duration", (System.currentTimeMillis() - startTime) + "ms");
        return new BookData("pages-" + startPage + "-" + endPage, result, fullText);
    }

    private List<PageData> extractWithPdfBox(byte[] bytes, int startPage, int endPage) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int totalPages = document.getNumberOfPages();
            int actualStart = Math.max(1, startPage);
            int actualEnd = Math.min(totalPages, endPage);

            if (actualStart > actualEnd) {
                return List.of();
            }

            PdfEncodingDecoder decoder = new PdfEncodingDecoder(document);
            PDFTextStripper stripper = decoder.hasCustomEncodings()
                    ? new CustomMathAwareTextStripper(decoder)
                    : new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<PageData> pages = new ArrayList<>();
            for (int pageNum = actualStart; pageNum <= actualEnd; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document).trim();
                pages.add(new PageData(pageNum, pageText));
            }
            return pages;
        }
    }

    private List<int[]> findMathPageRanges(List<PageData> pdfBoxPages) {
        List<int[]> ranges = new ArrayList<>();
        int rangeStart = -1;

        for (PageData page : pdfBoxPages) {
            boolean hasMath = mathFormulaDetector.hasMisinterpretedMath(page.text());
            if (hasMath) {
                if (rangeStart == -1) {
                    rangeStart = page.pageNumber();
                }
            } else {
                if (rangeStart != -1) {
                    ranges.add(new int[]{rangeStart, page.pageNumber() - 1});
                    rangeStart = -1;
                }
            }
        }
        if (rangeStart != -1) {
            int lastPage = pdfBoxPages.get(pdfBoxPages.size() - 1).pageNumber();
            ranges.add(new int[]{rangeStart, lastPage});
        }
        return ranges;
    }

    private Map<Integer, PageData> extractMathPagesWithPix2Text(byte[] bytes, List<int[]> ranges) throws IOException {
        Map<Integer, PageData> result = new LinkedHashMap<>();

        for (int[] range : ranges) {
            int rangeStart = range[0];
            int rangeEnd = range[1];

            try {
                String hash = extractionCacheService.computeHash(bytes);
                var cached = extractionCacheService.load(hash);
                if (cached != null && cached.hasContent()) {
                    List<PageData> cachedPages = filterPages(cached.pages(), rangeStart, rangeEnd);
                    for (PageData p : cachedPages) {
                        result.put(p.pageNumber(), p);
                    }
                    log.info("Loaded {} math pages from cache | range={}-{}",
                            cachedPages.size(), rangeStart, rangeEnd);
                    continue;
                }
            } catch (Exception e) {
                log.warn("Cache lookup failed for math pages extraction", e);
            }

            try {
                log.info("Requesting Pix2Text math extraction | range={}-{} | ranges={}",
                        rangeStart, rangeEnd, ranges.size());
                List<Pix2TextExtractionClient.PageInfo> pageInfos =
                        pix2textClient.extractPages(bytes, "upload.pdf", rangeStart, rangeEnd);

                for (Pix2TextExtractionClient.PageInfo info : pageInfos) {
                    if (info.text != null && !info.text.isBlank()) {
                        result.put(info.pageNumber, new PageData(info.pageNumber, info.text));
                    }
                }
                log.info("Pix2Text extraction complete for math pages | range={}-{} | pages={}",
                        rangeStart, rangeEnd, result.size());
            } catch (Exception e) {
                log.error("Pix2Text extraction failed for math pages | range={}-{} | error={}",
                        rangeStart, rangeEnd, e.getMessage());
            }
        }

        return result;
    }

    private List<PageData> filterPages(List<PageData> allPages, int startPage, int endPage) {
        return allPages.stream()
                .filter(p -> p.pageNumber() >= startPage && p.pageNumber() <= endPage)
                .toList();
    }

    private void validateChunk(List<PageData> pages, int expectedStart, int expectedEnd) {
        if (pages.isEmpty()) {
            log.warn("Validated chunk is empty | expected range={}-{}", expectedStart, expectedEnd);
            return;
        }

        int minPage = pages.stream().mapToInt(PageData::pageNumber).min().orElse(0);
        int maxPage = pages.stream().mapToInt(PageData::pageNumber).max().orElse(0);

        if (minPage != expectedStart) {
            log.warn("Chunk validation: unexpected start page | expected={} | actual={}", expectedStart, minPage);
        }
        if (maxPage != expectedEnd) {
            log.warn("Chunk validation: unexpected end page | expected={} | actual={}", expectedEnd, maxPage);
        }

        long emptyPages = pages.stream().filter(p -> !p.hasText()).count();
        if (emptyPages > 0) {
            log.warn("Chunk validation: {} empty pages detected", emptyPages);
        }

        log.info("Chunk validation complete | pages={} | range={}-{} | minPage={} | maxPage={}",
                pages.size(), expectedStart, expectedEnd, minPage, maxPage);
    }

    private String sanitizeExtractedText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        text = sanitizeMathSymbols(text);
        text = text.replaceAll("(?i)^\\s*Page\\s*\\([^)]*\\)\\s*", "");
        text = text.replaceAll("(?i)^\\s*Element\\s*\\(\\{[^}]*\\}\\)\\s*", "");
        text = text.replaceAll("\\s*box\\s*:\\s*\\[[^\\]]*\\]", "");
        text = text.replaceAll("\\s*score\\s*:\\s*[^,\\}\\s]+", "");
        text = text.replaceAll("\\s*position\\s*:\\s*[^,\\}\\s]+", "");
        text = text.replaceAll("\\s*id\\s*:\\s*[^,\\}\\s]+", "");
        text = text.replaceAll("\\p{C}", "");
        text = text.replaceAll("[ \\t]+", " ").trim();
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text;
    }

    private String sanitizeMathSymbols(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        text = text.replace("\u2260", "-");
        text = text.replace("\u00FF", "\\sum_");
        text = text.replace("\u0178", "\\prod_");
        return text;
    }

    private String combineText(List<PageData> pages) {
        StringBuilder full = new StringBuilder();
        for (PageData page : pages) {
            if (page.hasText()) {
                full.append(page.text()).append("\n\n");
            }
        }
        return full.toString();
    }
}
