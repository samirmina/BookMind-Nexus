package com.bookassistant.service.file;

import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.config.RuntimeConfigService;
import com.bookassistant.service.infrastructure.ExtractionCacheService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnDemandExtractionServiceTest {

    private BookStorageService bookStorageService;
    private ExtractionCacheService extractionCacheService;
    private RuntimeLogService runtimeLogService;
    private Pix2TextExtractionClient pix2TextClient;
    private PdfSliceService pdfSliceService;
    private RuntimeConfigService runtimeConfigService;
    private OnDemandExtractionService onDemandExtractionService;

    @BeforeEach
    void setUp() {
        bookStorageService = Mockito.mock(BookStorageService.class);
        extractionCacheService = Mockito.mock(ExtractionCacheService.class);
        runtimeLogService = Mockito.mock(RuntimeLogService.class);
        pix2TextClient = Mockito.mock(Pix2TextExtractionClient.class);
        pdfSliceService = Mockito.mock(PdfSliceService.class);
        runtimeConfigService = Mockito.mock(RuntimeConfigService.class);
        onDemandExtractionService = new OnDemandExtractionService(
                bookStorageService, extractionCacheService, runtimeLogService,
                pix2TextClient, pdfSliceService, runtimeConfigService);
    }

    private byte[] createValidPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void testModelOnlyMode_StartPageGreaterThan1_ExtractsCorrectPageNumbers() throws Exception {
        byte[] pdfBytes = createValidPdf();
        byte[] slicedPdf = createValidPdf();
        when(bookStorageService.loadPdf(1L)).thenReturn(java.util.Optional.of(pdfBytes));
        when(runtimeConfigService.getExtractionMode()).thenReturn("model-only");
        when(pdfSliceService.slicePageRange(pdfBytes, 5, 5)).thenReturn(slicedPdf);
        when(extractionCacheService.computeHash(slicedPdf)).thenReturn("hash-start5");
        when(extractionCacheService.load("hash-start5")).thenReturn(null);

        BookData result = onDemandExtractionService.extractPages(1L, 5, 5);

        assertTrue(result.pages().size() >= 1,
            "Should extract pages even when startPage > 1");
        assertEquals(5, result.pages().get(0).pageNumber(),
            "First extracted page should have absolute page number 5");
        verify(extractionCacheService).save(eq("hash-start5"), anyString(), eq("model-only"), anyList(), anyString());
    }

    @Test
    void testModelOnlyMode_RoutesToPdfBox_SkipsPix2Text() throws Exception {
        byte[] pdfBytes = createValidPdf();
        byte[] slicedPdf = createValidPdf();
        when(bookStorageService.loadPdf(1L)).thenReturn(java.util.Optional.of(pdfBytes));
        when(runtimeConfigService.getExtractionMode()).thenReturn("model-only");
        when(pdfSliceService.slicePageRange(pdfBytes, 1, 2)).thenReturn(slicedPdf);
        when(extractionCacheService.computeHash(slicedPdf)).thenReturn("hash123");
        when(extractionCacheService.load("hash123")).thenReturn(null);

        BookData result = onDemandExtractionService.extractPages(1L, 1, 2);

        verify(pix2TextClient, never()).extractPages(any(), any());
        verify(extractionCacheService).save(eq("hash123"), anyString(), eq("model-only"), anyList(), anyString());
        assertNotNull(result);
    }

    @Test
    void testPix2TextMode_RoutesToPix2Text() throws Exception {
        byte[] pdfBytes = createValidPdf();
        byte[] slicedPdf = createValidPdf();
        when(bookStorageService.loadPdf(1L)).thenReturn(java.util.Optional.of(pdfBytes));
        when(runtimeConfigService.getExtractionMode()).thenReturn("pix2text");
        when(pdfSliceService.slicePageRange(pdfBytes, 1, 2)).thenReturn(slicedPdf);
        when(extractionCacheService.computeHash(slicedPdf)).thenReturn("hash456");
        when(extractionCacheService.load("hash456")).thenReturn(null);
        when(pix2TextClient.extractPages(slicedPdf, "upload.pdf"))
                .thenReturn(List.of(new Pix2TextExtractionClient.PageInfo(1, "text")));

        BookData result = onDemandExtractionService.extractPages(1L, 1, 2);

        verify(pix2TextClient).extractPages(slicedPdf, "upload.pdf");
        assertNotNull(result);
    }

    @Test
    void testModelOnlyMode_CacheHit_ReturnsCachedData() throws Exception {
        byte[] pdfBytes = createValidPdf();
        byte[] slicedPdf = createValidPdf();
        com.bookassistant.model.ExtractionCacheEntry cachedEntry =
                new com.bookassistant.model.ExtractionCacheEntry("hash123", "test.pdf", "model-only",
                        System.currentTimeMillis(), 2, "cached text",
                        List.of(new PageData(1, "page 1"), new PageData(2, "page 2")));
        when(bookStorageService.loadPdf(1L)).thenReturn(java.util.Optional.of(pdfBytes));
        when(runtimeConfigService.getExtractionMode()).thenReturn("model-only");
        when(pdfSliceService.slicePageRange(pdfBytes, 1, 2)).thenReturn(slicedPdf);
        when(extractionCacheService.computeHash(slicedPdf)).thenReturn("hash123");
        when(extractionCacheService.load("hash123")).thenReturn(cachedEntry);

        BookData result = onDemandExtractionService.extractPages(1L, 1, 2);

        verify(pix2TextClient, never()).extractPages(any(), any());
        verify(extractionCacheService, never()).save(anyString(), anyString(), anyString(), anyList(), anyString());
        assertNotNull(result);
        assertEquals(2, result.pages().size());
    }
}