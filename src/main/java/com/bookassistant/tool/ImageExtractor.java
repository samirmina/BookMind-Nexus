package com.bookassistant.tool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone utility that extracts embedded images from a PDF using PDFBox 3.0.4 rendering
 * and a white-space projection algorithm.
 * <p>
 * <b>What:</b> Renders every page to a bitmap, detects non-background rectangles via
 * horizontal/vertical pixel-projection, crops them, and writes the figures plus a
 * rendered-page thumbnail into {@code cache/{bookName}/images/} and {@code pages/}.
 * <p>
 * <b>Why:</b> Online AI models need figure inputs; PDFBox 3.0.4 removed the older
 * {@code PDFormXObject} extraction path, so raster-based figure detection is the
 * reliable cross-version fallback.
 * <p>
 * <b>Where:</b> Required for PDFBox 3.0.4 memory-handling and figure-extraction strategy.
 */
public final class ImageExtractor {

    private static final Logger log = LoggerFactory.getLogger(ImageExtractor.class);
    private ImageExtractor() {}

    /**
     * CLI entry point. Expects a PDF path and an optional book name.
     *
     * @param args     command-line arguments
     * @see #extract(String, String)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            log.info("Usage: ImageExtractor <pdf-file> [book-name]");
            log.info("  Extracts all embedded images from a PDF into cache/{book-name}/");
            System.exit(1);
        }

        String pdfPath = args[0];
        String bookName = args.length > 1 ? args[1] : deriveBookName(pdfPath);

        int exitCode = extract(pdfPath, bookName);
        System.exit(exitCode);
    }

    /**
     * Core extraction pipeline.
     * <ol>
     *   <li>Validates the PDF file exists.</li>
     *   <li>Clears any previous cache for the book.</li>
     *   <li>Renders each page at 150 DPI.</li>
     *   <li>Runs the white-space projection algorithm per rendered page.</li>
     *   <li>Writes an {@code index.json} with all discovered figures.</li>
     * </ol>
     *
     * @param pdfPath  absolute path to the source PDF
     * @param bookName logical name used for the cache directory
     * @return {@code 0} on success, {@code 1} on failure
     */
    public static int extract(String pdfPath, String bookName) {
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            log.error("Error: File not found: {}", pdfPath);
            return 1;
        }

        if (bookName == null) {
            bookName = deriveBookName(pdfPath);
        }
        String safeName = sanitizeName(bookName);
        String projectRoot = System.getProperty("user.dir");
        Path cacheDir = Paths.get(projectRoot, "cache", safeName);
        Path imagesDir = cacheDir.resolve("images");

        Path logFile = null;
        try {
            // Clear old cache
            if (Files.exists(imagesDir)) {
                try (var files = Files.list(imagesDir)) {
                    files.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException e) {} });
                }
            }
            Files.createDirectories(imagesDir);
            logFile = cacheDir.resolve("extract.log");
            writeLog(logFile, "=== Extraction Started ===");
            writeLog(logFile, "PDF: " + pdfFile.getAbsolutePath());
            writeLog(logFile, "Book: " + bookName);
            writeLog(logFile, "Cache: " + cacheDir);
        } catch (IOException e) {
            log.error("Error: Cannot create cache directory: {}", imagesDir, e);
            return 1;
        }

            log.info("Book: \"{}\"", bookName);
            log.info("File: {}", pdfFile.getName());
            log.info("Cache: {}", cacheDir);
            log.info("");

        byte[] pdfBytes;
        try {
            pdfBytes = Files.readAllBytes(pdfFile.toPath());
        } catch (IOException e) {
            log.error("Error: Cannot read file: {}", e.getMessage());
            if (logFile != null) writeLog(logFile, "Error: Cannot read PDF file", e);
            return 1;
        }

        int renderedPages = 0;
        List<ImageEntry> allImages = new ArrayList<>();
        int totalPages = 0;
        long startTime = System.currentTimeMillis();

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            totalPages = doc.getNumberOfPages();

            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
                int pageNum = pageIdx + 1;
                int[] seqHolder = {0};
                List<ImageEntry> pageImages = new ArrayList<>();
                Set<String> pageFingerprints = new HashSet<>();

                // Render full page at 150 DPI for downstream projection analysis
                BufferedImage rendered = null;
                try {
                    Path pagesDir = cacheDir.resolve("pages");
                    Files.createDirectories(pagesDir);
                    rendered = new PDFRenderer(doc)
                        .renderImageWithDPI(pageIdx, 150);
                    String pageFile = "p_" + String.format("%03d", pageNum) + ".png";
                    ImageIO.write(rendered, "PNG", pagesDir.resolve(pageFile).toFile());
                    renderedPages++;
                    long sizeKB = pagesDir.resolve(pageFile).toFile().length() / 1024;
                    pageImages.add(new ImageEntry("pages/" + pageFile, pageNum, "PNG",
                        rendered.getWidth(), rendered.getHeight(), sizeKB, "rendered"));
                    log.info("  -> [rendered] {}", pageFile);
                } catch (IOException e) {
                    // WORKAROUND: silently ignore individual page render failures so the
                    // extraction continues with remaining pages; failed pages are simply skipped.
                }

                // Run the white-space projection algorithm on the rendered page bitmap
                if (rendered != null) {
                    extractFiguresFromRender(rendered, pageNum,
                        imagesDir, safeName, pageImages, seqHolder, logFile, pageFingerprints);
                }

                allImages.addAll(pageImages);

                int totalFound = allImages.size();
                int progress = ((pageIdx + 1) * 100) / totalPages;
                long elapsed = System.currentTimeMillis() - startTime;
                double speed = elapsed > 0 ? (totalFound * 1000.0 / elapsed) : 0;

                StringBuilder bar = new StringBuilder("[");
                int filled = progress / 5;
                for (int i = 0; i < 20; i++) {
                    bar.append(i < filled ? '#' : '.');
                }
                bar.append("]");

                log.info("{} {}% | Page {}/{} | Found: {} images | Speed: {:.1f} img/s",
                        bar, progress, (pageIdx + 1), totalPages, totalFound, speed);

                if (!pageImages.isEmpty()) {
                    for (ImageEntry ie : pageImages) {
                        log.info("  -> {} (page {})", ie.file, ie.page);
                    }
                }
            }

        } catch (IOException e) {
            log.error("Error reading PDF: {}", e.getMessage());
            if (logFile != null) writeLog(logFile, "Error reading PDF", e);
            return 1;
        }

        long totalTime = System.currentTimeMillis() - startTime;

        List<Integer> pagesWithImages = allImages.stream()
            .map(e -> e.page)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        log.info("");
        log.info("=== Extraction Complete ===");
        log.info("Total:       {} images from {}/{} pages",
                allImages.size(), pagesWithImages.size(), totalPages);
        log.info("Rendered:    {}/{} pages", renderedPages, totalPages);
        log.info("Output:      {}", imagesDir);
        log.info("Index:       {}", cacheDir.resolve("index.json"));
        log.info("Time:        {}s", totalTime / 1000);

        if (logFile != null) {
            writeLog(logFile, "=== Extraction Complete ===");
            writeLog(logFile, "Total images: " + allImages.size());
            writeLog(logFile, "Rendered pages: " + renderedPages + "/" + totalPages);
            writeLog(logFile, "Pages with images: " + pagesWithImages.size() + "/" + totalPages);
            writeLog(logFile, "Duration: " + (totalTime / 1000) + "s");
        }

        saveIndex(cacheDir.resolve("index.json"), safeName, pdfFile.getName(), totalPages, allImages);

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer.exe", cacheDir.toAbsolutePath().toString()});
            }
        } catch (IOException e) {
            // WORKAROUND: silently ignore explorer launch failure; not critical to extraction
        }

        return 0;
    }

    /**
     * Detects figure rectangles inside a rendered page bitmap using a two-pass
     * white-space projection algorithm.
     * <p>
     * <b>Algorithm:</b>
     * <ol>
     *   <li>Scale the full-resolution page down to a maximum of 600 px on the longest
     *       side and convert to 8-bit grayscale for fast pixel access.</li>
     *   <li><b>Horizontal projection:</b> count dark pixels per row; gaps with fewer than
     *       {@code 2%} of the scaled width dark pixels are marked as white-space gaps.
     *       Consecutive non-gap rows form a <i>horizontal strip</i>.</li>
     *   <li><b>Vertical projection (per strip):</b> count dark pixels per column; gaps
     *       with fewer than {@code 2%} of the strip height dark pixels become vertical
     *       gaps. Consecutive non-gap columns within the strip form a candidate cell.</li>
     *   <li>Scale each cell back to full-resolution coordinates and discard cells smaller
     *       than 100×100 px (noise filter).</li>
     *   <li><b>Proximity merge:</b> iteratively merge overlapping/enlarged rectangles
     *       until no more merges occur, recombining split parts of the same figure.</li>
     *   <li>Deduplicate via spatial fingerprinting (quantized origin and size).</li>
     * </ol>
     *
     * @param pageImage        the rendered full-page bitmap
     * @param pageNum          1-based page number for naming
     * @param imagesDir         target directory for cropped figure PNGs
     * @param safeName          sanitized book name used in output filenames
     * @param out               mutable list receiving discovered {@link ImageEntry} objects
     * @param seqRef            mutable sequence counter used to number figures on the page
     * @param logFile           optional log file for diagnostics
     * @param pageFingerprints  mutable set of spatial fingerprints to prevent duplicates
     */
    private static void extractFiguresFromRender(BufferedImage pageImage,
            int pageNum, Path imagesDir, String safeName, List<ImageEntry> out, int[] seqRef,
            Path logFile, Set<String> pageFingerprints) {
        int fullW = pageImage.getWidth();
        int fullH = pageImage.getHeight();
        if (fullW < 200 || fullH < 200) return;

        double scale = Math.min(1.0, 600.0 / Math.max(fullW, fullH));
        int sw = (int) (fullW * scale);
        int sh = (int) (fullH * scale);

        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = small.createGraphics();
        g2d.drawImage(pageImage, 0, 0, sw, sh, null);
        g2d.dispose();
        byte[] src = ((DataBufferByte) small.getRaster().getDataBuffer()).getData();

        int darkThreshold = 240;

        // Horizontal projection
        int[] rowCounts = new int[sh];
        for (int y = 0; y < sh; y++) {
            int c = 0;
            for (int x = 0; x < sw; x++) {
                if ((src[y * sw + x] & 0xFF) < darkThreshold) c++;
            }
            rowCounts[y] = c;
        }
        int hThresh = (int) (sw * 0.02);
        boolean[] hGap = new boolean[sh];
        for (int y = 0; y < sh; y++) {
            if (rowCounts[y] < hThresh) {
                int len = 0;
                while (y + len < sh && rowCounts[y + len] < hThresh) len++;
                if (len >= 3) for (int i = 0; i < len; i++) hGap[y + i] = true;
                y += len - 1;
            }
        }

        List<int[]> hStrips = new ArrayList<>();
        int start = -1;
        for (int y = 0; y < sh; y++) {
            if (!hGap[y]) { if (start == -1) start = y; }
            else if (start != -1) { hStrips.add(new int[]{start, y - 1}); start = -1; }
        }
        if (start != -1) hStrips.add(new int[]{start, sh - 1});

        double invScale = 1.0 / scale;
        List<Rectangle> figureRects = new ArrayList<>();

        for (int[] strip : hStrips) {
            int stripH = strip[1] - strip[0] + 1;
            if (stripH < 6) continue;

            int[] colCounts = new int[sw];
            for (int x = 0; x < sw; x++) {
                int c = 0;
                for (int y = strip[0]; y <= strip[1]; y++) {
                    if ((src[y * sw + x] & 0xFF) < darkThreshold) c++;
                }
                colCounts[x] = c;
            }
            int vThresh = (int) (stripH * 0.02);
            boolean[] vGap = new boolean[sw];
            for (int x = 0; x < sw; x++) {
                if (colCounts[x] < vThresh) {
                    int len = 0;
                    while (x + len < sw && colCounts[x + len] < vThresh) len++;
                    if (len >= 3) for (int i = 0; i < len; i++) vGap[x + i] = true;
                    x += len - 1;
                }
            }

            int vStart = -1;
            for (int x = 0; x < sw; x++) {
                if (!vGap[x]) { if (vStart == -1) vStart = x; }
                else if (vStart != -1) {
                    int cellW = x - vStart;
                    if (cellW >= 6) {
                        int cropX = Math.max(0, (int)(vStart * invScale));
                        int cropY = Math.max(0, (int)(strip[0] * invScale));
                        int cropW = Math.min(fullW - cropX, (int)(cellW * invScale));
                        int cropH = Math.min(fullH - cropY, (int)(stripH * invScale));
                        if (cropW >= 100 && cropH >= 100)
                            figureRects.add(new Rectangle(cropX, cropY, cropW, cropH));
                    }
                    vStart = -1;
                }
            }
            if (vStart != -1) {
                int cellW = sw - vStart;
                if (cellW >= 6) {
                    int cropX = Math.max(0, (int)(vStart * invScale));
                    int cropY = Math.max(0, (int)(strip[0] * invScale));
                    int cropW = Math.min(fullW - cropX, (int)(cellW * invScale));
                    int cropH = Math.min(fullH - cropY, (int)(stripH * invScale));
                    if (cropW >= 100 && cropH >= 100)
                        figureRects.add(new Rectangle(cropX, cropY, cropW, cropH));
                }
            }
        }

        // Proximity merge to recombine exhibition parts
        boolean merged = true;
        int proximityGap = Math.max(20, (int)(Math.min(fullW, fullH) * 0.02));
        while (merged) {
            merged = false;
            for (int i = 0; i < figureRects.size() && !merged; i++) {
                for (int j = i + 1; j < figureRects.size() && !merged; j++) {
                    Rectangle a = figureRects.get(i);
                    Rectangle b = figureRects.get(j);
                    Rectangle ea = new Rectangle(a.x - proximityGap, a.y - proximityGap,
                        a.width + 2 * proximityGap, a.height + 2 * proximityGap);
                    Rectangle eb = new Rectangle(b.x - proximityGap, b.y - proximityGap,
                        b.width + 2 * proximityGap, b.height + 2 * proximityGap);
                    if (ea.intersects(eb)) {
                        a.add(b);
                        figureRects.remove(j);
                        merged = true;
                    }
                }
            }
        }

        for (Rectangle rect : figureRects) {
            int g = 10;
            String figKey = "fig_" + (rect.x / g) + "_" + (rect.y / g) + "_"
                + ((rect.x + rect.width) / g) + "_" + ((rect.y + rect.height) / g);
            if (!pageFingerprints.add(figKey)) continue;

            int cx = Math.max(0, rect.x);
            int cy = Math.max(0, rect.y);
            int cw = Math.min(fullW - cx, rect.width);
            int ch = Math.min(fullH - cy, rect.height);
            if (cw < 100 || ch < 100) continue;

            BufferedImage figure;
            try {
                figure = pageImage.getSubimage(cx, cy, cw, ch);
            } catch (Exception e) {
                continue;
            }

            seqRef[0]++;
            String figSlug = safeName + "_p" + String.format("%03d", pageNum)
                + "-f" + String.format("%02d", seqRef[0]);
            Path figPath = imagesDir.resolve(figSlug + ".png");
            try {
                ImageIO.write(figure, "PNG", figPath.toFile());
            } catch (IOException e) {
                seqRef[0]--;
                continue;
            }
            long sizeKB = figPath.toFile().length() / 1024;
            out.add(new ImageEntry(figSlug + ".png", pageNum, "PNG", cw, ch, sizeKB, "figure"));
            log.info("    -> [figure] {}.png ({}x{})", figSlug, cw, ch);
        }
    }

    private static void saveIndex(Path indexPath, String bookName, String sourceFile, int totalPages, List<ImageEntry> images) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"bookName\": \"").append(escapeJson(bookName)).append("\",\n");
        sb.append("  \"sourceFile\": \"").append(escapeJson(sourceFile)).append("\",\n");
        sb.append("  \"totalPages\": ").append(totalPages).append(",\n");
        sb.append("  \"totalImages\": ").append(images.size()).append(",\n");
        sb.append("  \"images\": [\n");

        for (int i = 0; i < images.size(); i++) {
            ImageEntry ie = images.get(i);
            sb.append("    {\"file\": \"").append(escapeJson(ie.file)).append("\",");
            sb.append("\"page\": ").append(ie.page).append(",");
            sb.append("\"width\": ").append(ie.width).append(",");
            sb.append("\"height\": ").append(ie.height).append(",");
            sb.append("\"sizeKB\": ").append(ie.sizeKB).append(",");
            sb.append("\"type\": \"").append(escapeJson(ie.type)).append("\",");
            sb.append("\"format\": \"").append(escapeJson(ie.format)).append("\",");
            sb.append("\"topic\": null");
            sb.append("}");
            if (i < images.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");

        try {
            Files.writeString(indexPath, sb.toString());
        } catch (IOException e) {
            log.warn("Warning: Cannot write index.json: {}", e.getMessage());
        }
    }

    private static void writeLog(Path logFile, String message) {
        try {
            Files.writeString(logFile,
                "[" + LocalDateTime.now() + "] " + message + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // ignore
        }
    }

    private static void writeLog(Path logFile, String message, Throwable error) {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            error.printStackTrace(pw);
            writeLog(logFile, message + System.lineSeparator() + sw);
        } catch (IOException e) {
            // ignore
        }
    }

    private static String deriveBookName(String pdfPath) {
        String name = new File(pdfPath).getName();
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    private static String sanitizeName(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9\\-_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static class ImageEntry {
        final String file;
        final int page;
        final String format;
        final int width;
        final int height;
        final long sizeKB;
        final String type;

        ImageEntry(String file, int page, String format, int width, int height, long sizeKB, String type) {
            this.file = file;
            this.page = page;
            this.format = format;
            this.width = width;
            this.height = height;
            this.sizeKB = sizeKB;
            this.type = type;
        }
    }
}


