package com.bookassistant;

import com.bookassistant.tool.ImageExtractor;
import com.bookassistant.service.infrastructure.ConsoleCaptureService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Book Assistant Lite Spring Boot application.
 * <p>
 * <b>What:</b> Bootstraps the Spring Boot web server, loads PDF books into the
 * session, and exposes AI-powered study endpoints (QA, summary, analysis).
 * <p>
 * <b>Why:</b> A single runnable class is required by Spring Boot 3.5 convention;
 * it also doubles as a CLI harness for {@link ImageExtractor} so the same artifact
 * can be used in both server and offline mode without a separate utility jar.
 * <p>
 * <b>Where:</b> Per Spring Boot 3.5 {@code @SpringBootApplication} convention.
 */
@SpringBootApplication
@EnableScheduling
public class BookAssistantLiteApplication {

    /**
     * Application entry point. Delegates to {@link ImageExtractor} when the
     * {@code --extract-images} CLI flag is present, otherwise starts the web server.
     */
    public static void main(String[] args) {
        if (args.length >= 2 && "--extract-images".equals(args[0])) {
            String pdfPath = args[1];
            String bookName = args.length > 2 ? args[2] : null;
            int exitCode = ImageExtractor.extract(pdfPath, bookName);
            System.exit(exitCode);
        }
        SpringApplication.run(BookAssistantLiteApplication.class, args);
    }

    /**
     * Opens the default browser after the server starts.
     * <p>
     * <b>Workaround:</b> The browser-launch step is surrounded by an empty catch
     * block so that a failure to open the browser never prevents the application
     * from starting. This is intentional because the browser is a convenience
     * feature, not a functional requirement.
     *
     * @param env the Spring environment (used to read {@code server.port})
     * @return an {@link ApplicationRunner} that launches the browser
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app", name = "open-browser", matchIfMissing = false)
    ApplicationRunner openBrowser(Environment env) {
        return args -> {
            String port = env.getProperty("server.port", "8083");
            if (port.contains(":")) {
                port = port.substring(port.lastIndexOf(':') + 1);
            }
            String url = "http://localhost:" + port;
            String os = System.getProperty("os.name").toLowerCase();
            Runtime rt = Runtime.getRuntime();
            try {
                if (os.contains("win")) {
                    rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                } else if (os.contains("mac")) {
                    rt.exec(new String[]{"open", url});
                } else {
                    rt.exec(new String[]{"xdg-open", url});
                }
            } catch (Exception e) {
                // WORKAROUND: silently ignore browser-launch failures so the app can start
            }
        };
    }

    @Bean
    ApplicationRunner startConsoleCapture(ConsoleCaptureService consoleCaptureService) {
        return args -> {
            consoleCaptureService.startCapture();
        };
    }
}


