package com.bookassistant.service.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PdfSliceService {

    private static final Logger log = LoggerFactory.getLogger(PdfSliceService.class);

    /**
     * Slices the original PDF to the exact requested page range.
     *
     * @param originalPdf the original PDF bytes
     * @param startPage   1-based start page (inclusive)
     * @param endPage     1-based end page (inclusive)
     * @return a new PDF byte array containing only the requested pages
     * @throws IllegalArgumentException if the page range is invalid
     * @throws IOException if the PDF cannot be read or written
     */
    public byte[] slicePageRange(byte[] originalPdf, int startPage, int endPage) throws IOException {
        if (originalPdf == null || originalPdf.length == 0) {
            throw new IllegalArgumentException("Original PDF bytes must not be null or empty");
        }
        if (startPage < 1 || endPage < startPage) {
            throw new IllegalArgumentException("Invalid page range: startPage=" + startPage + ", endPage=" + endPage);
        }

        try (PDDocument document = Loader.loadPDF(originalPdf)) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new IllegalArgumentException("PDF has no pages");
            }

            int actualStart = Math.max(1, startPage);
            int actualEnd = Math.min(totalPages, endPage);

            if (actualStart > actualEnd) {
                throw new IllegalArgumentException(
                        "Page range out of bounds: startPage=" + startPage + ", endPage=" + endPage +
                        ", totalPages=" + totalPages
                );
            }

            log.info("Slicing PDF | requested={}-{} | actual={}-{} | totalPages={}",
                    startPage, endPage, actualStart, actualEnd, totalPages);

            if (actualStart == 1 && actualEnd == totalPages) {
                log.info("Slicing requested full document, returning original bytes");
                return originalPdf;
            }

            try (PDDocument slicedDoc = new PDDocument()) {
                for (int i = actualStart - 1; i < actualEnd; i++) {
                    PDPage sourcePage = document.getPage(i);
                    slicedDoc.addPage(sourcePage);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                slicedDoc.save(baos);
                byte[] result = baos.toByteArray();

                log.info("PDF sliced | slicedSize={}", result.length);
                return result;
            }
        }
    }

    /**
     * Returns the total number of pages in a PDF.
     *
     * @param pdfBytes the PDF bytes
     * @return the total page count
     * @throws IOException if the PDF cannot be read
     */
    public int getPageCount(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return 0;
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        }
    }
}
