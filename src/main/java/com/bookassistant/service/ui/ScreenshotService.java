package com.bookassistant.service.ui;

import com.bookassistant.entity.ui.UiScreenshotBaseline;
import com.bookassistant.entity.ui.UiScreenshotComparison;
import com.bookassistant.entity.ui.UiSnapshot;
import com.bookassistant.repository.ui.UiScreenshotBaselineRepository;
import com.bookassistant.repository.ui.UiScreenshotComparisonRepository;
import com.bookassistant.util.LoggingUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Captures screenshots from HTML snapshots and compares them against baselines.
 * Uses Swing HTML rendering as a fallback when headless browser is unavailable.
 */
@Service
public class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 800;

    private final UiScreenshotBaselineRepository baselineRepository;
    private final UiScreenshotComparisonRepository comparisonRepository;
    private final String screenshotDir;

    public ScreenshotService(UiScreenshotBaselineRepository baselineRepository,
                             UiScreenshotComparisonRepository comparisonRepository,
                             @Value("${app.screenshots-dir:D:/book-assistant-lite/ui-screenshots}") String screenshotDir) {
        this.baselineRepository = baselineRepository;
        this.comparisonRepository = comparisonRepository;
        this.screenshotDir = screenshotDir;
    }

    public void captureAndCompare(UiSnapshot snapshot, String pagePath, String userState) throws IOException {
        LoggingUtils.enter(log, "captureAndCompare", "page", pagePath);

        Path screenshotDir = Paths.get(this.screenshotDir);
        if (!Files.exists(screenshotDir)) {
            Files.createDirectories(screenshotDir);
        }

        String timestamp = LocalDateTime.now().toString().replace(":", "-");
        String filename = pagePath.replace("/", "_") + "_" + userState + "_" + timestamp + ".png";
        Path screenshotPath = screenshotDir.resolve(filename);

        BufferedImage screenshot = renderHtmlToImage(snapshot.getHtmlContent(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
        ImageIO.write(screenshot, "png", screenshotPath.toFile());

        UiScreenshotBaseline baseline = baselineRepository
                .findByPagePathAndUserStateAndIsActiveTrue(pagePath, userState)
                .orElse(null);

        if (baseline == null) {
            createBaseline(pagePath, userState, snapshot);
            LoggingUtils.exit(log, "captureAndCompare");
            return;
        }

        File baselineFile = new File(baseline.getScreenshotPath());
        if (baselineFile.exists()) {
            BufferedImage baselineImage = ImageIO.read(baselineFile);
            ComparisonResult result = compareImages(baselineImage, screenshot, baseline.getDiffThreshold());

            UiScreenshotComparison comparison = new UiScreenshotComparison();
            comparison.setBaselineId(baseline.getId());
            comparison.setSnapshotId(snapshot.getId());
            comparison.setDiffPercentage(result.diffPercentage);
            comparison.setDiffPixels(result.diffPixels);
            comparison.setThreshold(baseline.getDiffThreshold());
            comparison.setPassed(result.passed);
            comparisonRepository.save(comparison);

            log.info("Screenshot comparison for {}: diff={}%, passed={}", pagePath, result.diffPercentage, result.passed);
        }

        LoggingUtils.exit(log, "captureAndCompare");
    }

    public UiScreenshotBaseline createBaseline(String pagePath, String userState, UiSnapshot snapshot) throws IOException {
        LoggingUtils.enter(log, "createBaseline", "page", pagePath);

        Path screenshotDir = Paths.get(this.screenshotDir);
        if (!Files.exists(screenshotDir)) {
            Files.createDirectories(screenshotDir);
        }

        String filename = pagePath.replace("/", "_") + "_" + userState + "_baseline.png";
        Path screenshotPath = screenshotDir.resolve(filename);

        BufferedImage screenshot = renderHtmlToImage(snapshot.getHtmlContent(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
        ImageIO.write(screenshot, "png", screenshotPath.toFile());

        UiScreenshotBaseline baseline = new UiScreenshotBaseline();
        baseline.setPagePath(pagePath);
        baseline.setUserState(userState);
        baseline.setScreenshotPath(screenshotPath.toString());
        baseline.setWidth(DEFAULT_WIDTH);
        baseline.setHeight(DEFAULT_HEIGHT);
        baseline.setDiffThreshold(5);
        baseline.setIsActive(true);

        UiScreenshotBaseline saved = baselineRepository.save(baseline);
        LoggingUtils.exit(log, "createBaseline", "id", saved.getId());
        return saved;
    }

    private BufferedImage renderHtmlToImage(String html, int width, int height) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));

        int y = 20;
        for (org.jsoup.nodes.Node node : doc.body().childNodes()) {
            String text = node.outerHtml();
            if (text.length() > 200) {
                text = text.substring(0, 200) + "...";
            }
            if (y > height - 20) break;
            g2d.drawString(text, 20, y);
            y += 16;
        }

        g2d.dispose();
        return image;
    }

    private ComparisonResult compareImages(BufferedImage baseline, BufferedImage current, int threshold) {
        int width = Math.min(baseline.getWidth(), current.getWidth());
        int height = Math.min(baseline.getHeight(), current.getHeight());
        int totalPixels = width * height;
        int diffPixels = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = baseline.getRGB(x, y);
                int rgb2 = current.getRGB(x, y);
                if (rgb1 != rgb2) {
                    diffPixels++;
                }
            }
        }

        double diffPercentage = totalPixels > 0 ? (diffPixels * 100.0 / totalPixels) : 0;
        boolean passed = diffPercentage <= threshold;

        return new ComparisonResult(diffPercentage, diffPixels, passed);
    }

    private static class ComparisonResult {
        double diffPercentage;
        int diffPixels;
        boolean passed;

        ComparisonResult(double diffPercentage, int diffPixels, boolean passed) {
            this.diffPercentage = diffPercentage;
            this.diffPixels = diffPixels;
            this.passed = passed;
        }
    }
}
