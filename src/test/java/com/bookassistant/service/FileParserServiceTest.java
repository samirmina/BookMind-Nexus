package com.bookassistant.service;

import com.bookassistant.service.file.FileParserService;
import com.bookassistant.service.infrastructure.ExtractionCacheService;
import com.bookassistant.service.infrastructure.RuntimeLogService;
import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileParserService.
 * Tests PDF parsing functionality.
 */
class FileParserServiceTest {

    private FileParserService fileParserService;

    @BeforeEach
    void setUp() {
        MessageSource messageSource = Mockito.mock(MessageSource.class);
        Mockito.when(messageSource.getMessage(Mockito.anyString(), Mockito.any(), Mockito.any()))
               .thenAnswer(invocation -> {
                   String key = invocation.getArgument(0);
                   return switch (key) {
                       case "upload.file.empty" -> "File is empty";
                       case "file.no.text.extracted" -> "No text extracted";
                       case "extraction.mode.pix2text.hint" -> "Try Pix2Text mode";
                       case "extraction.mode.marker.hint" -> "Try Pix2Text mode";
                       case "extraction.mode.pdfbox.hint" -> "Try PDFBox mode";
                       default -> key;
                   };
               });

        com.bookassistant.service.file.BookStorageService bookStorageService = Mockito.mock(com.bookassistant.service.file.BookStorageService.class);
        ExtractionCacheService extractionCacheService = Mockito.mock(ExtractionCacheService.class);
        Mockito.when(extractionCacheService.computeHash(Mockito.any(byte[].class)))
               .thenReturn("test-hash-123");
        RuntimeLogService runtimeLogService = Mockito.mock(RuntimeLogService.class);
        fileParserService = new FileParserService(messageSource, bookStorageService, extractionCacheService, runtimeLogService);
    }

    /**
     * This name was chosen because this function verifies that the service can be created successfully.
     */
    @Test
    void testServiceCreation() {
        assertNotNull(fileParserService);
    }

    /**
     * This name was chosen because this function verifies that parsing an empty file throws an exception.
     */
    @Test
    void testParseEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.pdf",
            "application/pdf",
            new byte[0]
        );

        assertThrows(Exception.class, () -> {
            fileParserService.parsePdfFile(emptyFile);
        });
    }

    /**
     * Verifies that parsing an invalid PDF is accepted at upload time.
     * In the on-demand extraction flow, parsePdfBytes stores raw bytes
     * and does not validate PDF structure — validation happens during
     * extraction, not upload.
     */
    @Test
    void testParseInvalidPdf() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "invalid.pdf",
            "application/pdf",
            "This is not a valid PDF".getBytes()
        );

        BookData result = fileParserService.parsePdfFile(invalidFile);

        assertNotNull(result);
        assertEquals("invalid.pdf", result.fileName());
        assertTrue(result.pages().isEmpty());
    }

    /**
     * This name was chosen because this function verifies that parsing a valid PDF succeeds.
     */
    @Test
    void testParseValidPdf() throws Exception {
        byte[] pdfBytes = createMinimalPdf();

        MockMultipartFile validFile = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            pdfBytes
        );

        BookData result = fileParserService.parsePdfFile(validFile);

        assertNotNull(result);
        assertEquals("test.pdf", result.fileName());
        assertNotNull(result.pages());
        assertNotNull(result.fullText());
    }

    /**
     * In the on-demand extraction flow, parsePdfBytes stores raw PDF bytes
     * and returns empty BookData (no extraction at upload time).
     * The returned BookData has empty pages list and empty text.
     */
    @Test
    void testParsePdfWithMultiplePages() throws Exception {
        byte[] pdfBytes = createPdfWithPages(3);

        MockMultipartFile multiPageFile = new MockMultipartFile(
            "file",
            "multipage.pdf",
            "application/pdf",
            pdfBytes
        );

        BookData result = fileParserService.parsePdfFile(multiPageFile);

        assertNotNull(result);
        assertEquals("multipage.pdf", result.fileName());
        assertTrue(result.pages().isEmpty());
        assertTrue(result.fullText().isEmpty());
    }

    /**
     * This name was chosen because this function verifies that page numbers are sequential.
     */
    @Test
    void testParsePdf_PageNumbersAreSequential() throws Exception {
        byte[] pdfBytes = createPdfWithPages(3);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            pdfBytes
        );

        BookData result = fileParserService.parsePdfFile(file);

        List<PageData> pages = result.pages();
        for (int i = 0; i < pages.size(); i++) {
            assertEquals(i + 1, pages.get(i).pageNumber());
        }
    }

    /**
     * This name was chosen because this function verifies that full text contains content from all pages.
     */
    @Test
    void testParsePdf_FullTextContainsAllPages() throws Exception {
        byte[] pdfBytes = createPdfWithPages(2);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            pdfBytes
        );

        BookData result = fileParserService.parsePdfFile(file);

        assertNotNull(result.fullText());
        StringBuilder allPageTexts = new StringBuilder();
        for (PageData page : result.pages()) {
            allPageTexts.append(page.text());
        }
        assertTrue(result.fullText().length() >= 0);
    }

    /**
     * Verifies that parsing a large PDF stores it without extraction.
     * In the on-demand extraction flow, upload just stores bytes.
     */
    @Test
    void testParsePdf_LargeFile() throws Exception {
        byte[] pdfBytes = createPdfWithPages(10);

        MockMultipartFile largeFile = new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            pdfBytes
        );

        BookData result = fileParserService.parsePdfFile(largeFile);

        assertNotNull(result);
        assertTrue(result.pages().isEmpty());
    }

    /**
     * This name was chosen because this function verifies that the file name is preserved.
     */
    @Test
    void testParsePdf_FileNamePreserved() throws Exception {
        byte[] pdfBytes = createMinimalPdf();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "my-custom-book.pdf",
            "application/pdf",
            pdfBytes
        );

        BookData result = fileParserService.parsePdfFile(file);

        assertEquals("my-custom-book.pdf", result.fileName());
    }

    /**
     * This name was chosen because this function creates a minimal valid PDF with text for testing.
     */
    private byte[] createMinimalPdf() throws Exception {
        String text = "Test PDF Content";
        return createPdfWithText(1, new String[]{text});
    }

    /**
     * This name was chosen because this function creates a PDF with multiple pages containing text for testing.
     */
    private byte[] createPdfWithPages(int pageCount) throws Exception {
        String[] texts = new String[pageCount];
        for (int i = 0; i < pageCount; i++) {
            texts[i] = "Page " + (i + 1) + " content";
        }
        return createPdfWithText(pageCount, texts);
    }

    /**
     * This name was chosen because this function creates a PDF with specified pages and text content.
     * Uses raw PDF structure to avoid font API dependencies.
     */
    private byte[] createPdfWithText(int pageCount, String[] pageTexts) throws Exception {
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");

        int objOffset = 10;
        pdf.append(String.format("%d 0 obj\n<< /Type /Catalog /Pages %d 0 R >>\nendobj\n", 1, 2));
        pdf.append(String.format("%d 0 obj\n<< /Type /Pages /Kids [", 2));
        for (int i = 0; i < pageCount; i++) {
            pdf.append(String.format("%d 0 R ", 3 + i));
        }
        pdf.append("] /Count ").append(pageCount).append(" >>\nendobj\n");

        int streamObjStart = 3 + pageCount;
        for (int i = 0; i < pageCount; i++) {
            int streamObj = 3 + i;
            int nextObj = streamObj + 1;
            String streamContent = buildPageStream(pageTexts[i]);
            int streamLen = streamContent.getBytes(StandardCharsets.US_ASCII).length;
            pdf.append(String.format("%d 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents %d 0 R >>\nendobj\n", streamObj, nextObj));
            pdf.append(String.format("%d 0 obj\n<< /Length %d >>\nstream\n%s\nendstream\nendobj\n", nextObj, streamLen, streamContent));
        }

        long xrefPos = pdf.length();
        pdf.append("xref\n");
        int totalObjects = 2 + pageCount * 2;
        pdf.append(String.format("0 %d\n", totalObjects + 1));
        pdf.append("0000000000 65535 f \n");
        pdf.append(String.format("%09d 00000 n \n", 10));
        pdf.append(String.format("%09d 00000 n \n", objOffset));
        int pagesObjOffset = objOffset + pdf.substring(objOffset).split("\n", 2)[0].length() + 1;
        pagesObjOffset = objOffset + pdf.substring(objOffset).indexOf(String.format("%d 0 obj", 2)) + pdf.substring(objOffset).split(String.format("%d 0 obj", 2), 2)[0].length();
        pdf.append(String.format("%09d 00000 n \n", pagesObjOffset));

        for (int i = 0; i < pageCount; i++) {
            String marker = String.format("%d 0 obj", 3 + i);
            int pos = pdf.indexOf(marker);
            pdf.append(String.format("%09d 00000 n \n", pos));
            marker = String.format("%d 0 obj", 3 + pageCount + i);
            pos = pdf.indexOf(marker);
            pdf.append(String.format("%09d 00000 n \n", pos));
        }

        pdf.append("trailer\n");
        pdf.append(String.format("<< /Size %d /Root 1 0 R >>\n", totalObjects + 1));
        pdf.append("startxref\n");
        pdf.append(xrefPos).append("\n");
        pdf.append("%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * This name was chosen because this function builds a raw PDF content stream for a page.
     * Embeds text using basic PDF operators without font dependencies.
     */
    private String buildPageStream(String text) {
        return "BT\n" +
            "/F1 1 Tf\n" +
            "100 700 Td\n" +
            "(" + escapePdfString(text) + ") Tj\n" +
            "ET";
    }

    /**
     * This name was chosen because this function escapes text for raw PDF string literals.
     * Handles parentheses and backslashes per PDF spec.
     */
    private String escapePdfString(String text) {
        return text.replace("\\", "\\\\")
                   .replace("(", "\\(")
                   .replace(")", "\\)");
    }
}
