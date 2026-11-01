package com.bookassistant.service.config;

import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Manages runtime-configurable settings that can be changed without restarting the app.
 * Settings are persisted in cache/config/runtime.properties.
 */
@Service
public class RuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigService.class);
    private static final Path RUNTIME_CONFIG = Path.of("cache", "config", "runtime.properties");
    private static final Properties defaults = new Properties();

    static {
        defaults.setProperty("app.extraction.mode", "model-only");
    }

    public String getExtractionMode() {
        Properties props = loadRuntimeProperties();
        return props.getProperty("app.extraction.mode", defaults.getProperty("app.extraction.mode"));
    }

    public void setExtractionMode(String mode) {
        Properties props = loadRuntimeProperties();
        props.setProperty("app.extraction.mode", mode);
        saveRuntimeProperties(props);
        log.info("Runtime extraction mode updated | mode={}", mode);
    }

    private Properties loadRuntimeProperties() {
        Properties props = new Properties(defaults);
        if (Files.exists(RUNTIME_CONFIG)) {
            try (InputStream is = Files.newInputStream(RUNTIME_CONFIG, StandardOpenOption.READ)) {
                props.load(is);
            } catch (IOException e) {
                log.warn("Failed to load runtime config | error={}", e.getMessage());
            }
        }
        return props;
    }

    private void saveRuntimeProperties(Properties props) {
        try {
            Files.createDirectories(RUNTIME_CONFIG.getParent());
            try (OutputStream os = Files.newOutputStream(RUNTIME_CONFIG,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                props.store(os, "BookMind Nexus Runtime Configuration");
            }
        } catch (IOException e) {
            log.error("Failed to save runtime config | error={}", e.getMessage());
        }
    }
}
