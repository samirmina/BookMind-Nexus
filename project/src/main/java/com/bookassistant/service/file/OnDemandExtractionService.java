package com.bookassistant.service.file;

import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.config.RuntimeConfigService;
import com.bookassistant.service.infrastructure.ExtractionCacheService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.util.LoggingUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OnDemandExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OnDemandExtractionService.class);
    private static final String COMPONENT = "OnDemandExtractionService";

    private final BookStorageService bookStorageService;
    private final ExtractionCacheService extractionCacheService;
    private final RuntimeLogService runtimeLogService;
    private final Pix2TextExtractionClient pix2TextClient;
    private final PdfSliceService pdfSliceService;
    private final RuntimeConfigService runtimeConfigService;

    public OnDemandExtractionService(BookStorageService bookStorageService,
                                     ExtractionCacheService extractionCacheService,
                                     RuntimeLogService runtimeLogService,
                                     Pix2TextExtractionClient pix2TextClient,
                                     PdfSliceService pdfSliceService,
                                     RuntimeConfigService runtimeConfigService) {
        this.bookStorageService = bookStorageService;
        this.extractionCacheService = extractionCacheService;
        this.runtimeLogService = runtimeLogService;
        this.pix2TextClient = pix2TextClient;
        this.pdfSliceService = pdfSliceService;
        this.runtimeConfigService = runtimeConfigService;
        log.info("OnDemandExtractionService initialized");
    }

    /**
     * Extracts text from a specific page range of a saved book.
     *
     * @param bookId    the persisted book ID
     * @param startPage 1-based start page (inclusive)
     * @param endPage   1-based end page (inclusive)
     * @return BookData containing only the requested pages
     * @throws IOException if the PDF cannot be read
     */
    public BookData extractPages(long bookId, int startPage, int endPage) throws IOException {
        runtimeLogService.info(COMPONENT, String.valueOf(bookId), "Extracting pages", java.util.Map.of(
                "startPage", startPage,
                "endPage", endPage
        ));

        java.util.Optional<byte[]> pdfBytesOpt = bookStorageService.loadPdf(bookId);
        if (pdfBytesOpt.isEmpty()) {
            throw new IOException("Book PDF not found on disk | bookId=" + bookId);
        }

        byte[] originalPdf = pdfBytesOpt.get();
        log.info("Extracting pages {}-{} from book {} | originalPdfSize={}", startPage, endPage, bookId, originalPdf.length);

        String mode = runtimeConfigService.getExtractionMode();
        log.info("Extraction mode | mode={}", mode);

        if ("model-only".equalsIgnoreCase(mode)) {
            log.info("Routing to Model Only extraction | bookId={} | range={}-{}", bookId, startPage, endPage);
            return extractPagesWithPdfBox(originalPdf, startPage, endPage);
        }

        return extractPagesWithPix2Text(originalPdf, startPage, endPage);
    }

    private BookData extractPagesWithPix2Text(byte[] originalPdf, int startPage, int endPage) throws IOException {
        byte[] slicedPdf = pdfSliceService.slicePageRange(originalPdf, startPage, endPage);
        log.info("PDF sliced | requested={}-{} | slicedSize={}", startPage, endPage, slicedPdf.length);

        String hash = extractionCacheService.computeHash(slicedPdf);
        try {
            var cached = extractionCacheService.load(hash);
            if (cached != null && cached.hasContent()) {
                log.info("Cache hit for sliced PDF | hash={} | pages={}", hash, cached.pageCount());
                List<PageData> cachedPages = cached.pages().stream()
                        .filter(p -> p.pageNumber() >= startPage && p.pageNumber() <= endPage)
                        .toList();
                String fullText = cachedPages.stream()
                        .filter(PageData::hasText)
                        .map(PageData::text)
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
                return new BookData("pages-" + startPage + "-" + endPage, cachedPages, fullText);
            }
        } catch (Exception e) {
            log.warn("Cache lookup failed for sliced PDF", e);
        }

        int originalStartPage = startPage;
        List<Pix2TextExtractionClient.PageInfo> pageInfos =
                pix2TextClient.extractPages(slicedPdf, "upload.pdf");

        int emptyPagesSkipped = 0;
        List<Integer> emptyPageNumbers = new ArrayList<>();
        List<PageData> pages = new ArrayList<>();
        for (Pix2TextExtractionClient.PageInfo info : pageInfos) {
            if (info.text != null && !info.text.isBlank()) {
                int originalPageNumber = originalStartPage + info.pageNumber - 1;
                pages.add(new PageData(originalPageNumber, info.text));
            } else {
                emptyPagesSkipped++;
                emptyPageNumbers.add(info.pageNumber);
                log.warn("OnDemandExtractionService skipped empty page | originalPage={} | pix2Page={} | textLength={}",
                    originalStartPage + info.pageNumber - 1, info.pageNumber,
                    info.text != null ? info.text.length() : -1);
            }
        }

        if (emptyPagesSkipped > 0) {
            log.warn("OnDemandExtractionService empty-page summary | emptyCount={} | pages={} | range={}-{}",
                emptyPagesSkipped, emptyPageNumbers, startPage, endPage);
        }

        String fullText = pages.stream()
                .filter(PageData::hasText)
                .map(PageData::text)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        runtimeLogService.info(COMPONENT, "sliced-pdf", "Pages extracted", java.util.Map.of(
                "pagesExtracted", pages.size(),
                "emptyPagesSkipped", emptyPagesSkipped,
                "textLength", fullText.length(),
                "startPage", startPage,
                "endPage", endPage
        ));

        return new BookData("pages-" + startPage + "-" + endPage, pages, fullText);
    }

    private BookData extractPagesWithPdfBox(byte[] originalPdf, int startPage, int endPage) throws IOException {
        byte[] slicedPdf = pdfSliceService.slicePageRange(originalPdf, startPage, endPage);
        log.info("Model Only: PDF sliced | requested={}-{} | slicedSize={}", startPage, endPage, slicedPdf.length);

        String hash = extractionCacheService.computeHash(slicedPdf);
        try {
            var cached = extractionCacheService.load(hash);
            if (cached != null && cached.hasContent()) {
                log.info("Model Only: Cache hit for sliced PDF | hash={} | pages={}", hash, cached.pageCount());
                List<PageData> cachedPages = cached.pages().stream()
                        .filter(p -> p.pageNumber() >= startPage && p.pageNumber() <= endPage)
                        .toList();
                String fullText = cachedPages.stream()
                        .filter(PageData::hasText)
                        .map(PageData::text)
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
                return new BookData("pages-" + startPage + "-" + endPage, cachedPages, fullText);
            }
        } catch (Exception e) {
            log.warn("Model Only: Cache lookup failed for sliced PDF", e);
        }

        List<PageData> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(slicedPdf)) {
            int totalPages = document.getNumberOfPages();
            int actualStart = 1;
            int actualEnd = totalPages;

            if (totalPages <= 0) {
                log.warn("Model Only: Sliced PDF has no pages | startPage={} | endPage={}", startPage, endPage);
                return new BookData("pages-" + startPage + "-" + endPage, List.of(), "");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int pageNum = actualStart; pageNum <= actualEnd; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document).trim();
                int absolutePageNumber = startPage + pageNum - actualStart;
                pages.add(new PageData(absolutePageNumber, pageText));
                log.info("Model Only: page {} extracted | chars={}", absolutePageNumber, pageText.length());
            }
        }

        String fullText = pages.stream()
                .filter(PageData::hasText)
                .map(PageData::text)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        runtimeLogService.info(COMPONENT, String.valueOf(startPage), "Model Only pages extracted", java.util.Map.of(
                "pagesExtracted", pages.size(),
                "textLength", fullText.length(),
                "startPage", startPage,
                "endPage", endPage
        ));

        try {
            extractionCacheService.save(hash, "upload.pdf", "model-only", pages, fullText);
        } catch (Exception e) {
            log.warn("Model Only: Failed to save cache", e);
        }

        log.info("Model Only: extraction complete | pages={} | textLength={}", pages.size(), fullText.length());
        return new BookData("pages-" + startPage + "-" + endPage, pages, fullText);
    }
}
