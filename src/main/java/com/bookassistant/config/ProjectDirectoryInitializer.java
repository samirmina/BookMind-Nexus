package com.bookassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ensures all project-local directories exist at startup.
 * <p>
 * <b>What:</b> Creates {@code cache/}, {@code logs/}, and {@code tmp/} under the
 * project working directory so that uploads, extracted images, logs, and temp files
 * never fall back to system-wide locations.
 * <p>
 * <b>Why:</b> Required so the app is fully portable and its data, binaries, and
 * runtime artifacts are contained inside the project folder. Closing the console
 * stops the app and leaves all artifacts in place.
 * <p>
 * <b>Where:</b> Runs once on application ready; no external configuration needed
 * because {@code user.dir} is set by the launcher.
 */
@Configuration
public class ProjectDirectoryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectDirectoryInitializer.class);

    private final String cacheDir;
    private final String logsDir;
    private final String uploadDir;
    private final String storageDir;
    private final String screenshotsDir;
    private final String pdfSourceDir;

    public ProjectDirectoryInitializer(
            @Value("${app.cache-dir:external/cache}") String cacheDir,
            @Value("${app.logs-dir:external/logs}") String logsDir,
            @Value("${app.upload-dir:external/tmp}") String uploadDir,
            @Value("${app.storage-dir:D:/book-assistant-lite/storage/books}") String storageDir,
            @Value("${app.screenshots-dir:D:/book-assistant-lite/ui-screenshots}") String screenshotsDir,
            @Value("${app.pdf-source-dir:D:/book-assistant-lite/external/pdfs}") String pdfSourceDir) {
        this.cacheDir = cacheDir;
        this.logsDir = logsDir;
        this.uploadDir = uploadDir;
        this.storageDir = storageDir;
        this.screenshotsDir = screenshotsDir;
        this.pdfSourceDir = pdfSourceDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path cache = Paths.get(cacheDir).toAbsolutePath().normalize();
        Path logs = Paths.get(logsDir).toAbsolutePath().normalize();
        Path tmp = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path storage = Paths.get(storageDir).toAbsolutePath().normalize();
        Path screenshots = Paths.get(screenshotsDir).toAbsolutePath().normalize();
        Path pdfSource = Paths.get(pdfSourceDir).toAbsolutePath().normalize();

        Files.createDirectories(cache);
        Files.createDirectories(logs);
        Files.createDirectories(tmp);
        Files.createDirectories(storage);
        Files.createDirectories(screenshots);
        Files.createDirectories(pdfSource);

        log.info("Project directories ready | cache={} | logs={} | tmp={} | storage={} | screenshots={} | pdfSource={}",
            cache.toAbsolutePath(), logs.toAbsolutePath(), tmp.toAbsolutePath(), storage.toAbsolutePath(), screenshots.toAbsolutePath(), pdfSource.toAbsolutePath());
    }
}
