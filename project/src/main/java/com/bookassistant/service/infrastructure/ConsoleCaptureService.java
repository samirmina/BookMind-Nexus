package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.ConsoleEntry;
import com.bookassistant.repository.ConsoleEntryRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures System.out and System.err output and persists it to the database.
 * <p>
 * <b>What:</b> Intercepts Java console output by replacing {@code System.out} and
 * {@code System.err} with custom {@link PrintStream}s that buffer complete lines,
 * parse log metadata, and save each entry asynchronously to the database.
 * <p>
 * <b>Why:</b> Provides a complete audit trail of application console output for
 * debugging, project history, and post-mortem analysis without relying on log files.
 * <p>
 * <b>Where:</b> Started during application bootstrap and runs for the JVM lifetime.
 */
@Service
public class ConsoleCaptureService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleCaptureService.class);
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+" +
        "\\[([^\\]]+)\\]\\s+" +
        "(TRACE|DEBUG|INFO|WARN|ERROR)\\s+" +
        "([^\\s]+)\\s+[-]\\s+(.*)"
    );

    private final ConsoleEntryRepository consoleEntryRepository;
    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "console-capture");
        t.setDaemon(true);
        return t;
    });

    private PrintStream originalOut;
    private PrintStream originalErr;

    public ConsoleCaptureService(ConsoleEntryRepository consoleEntryRepository) {
        this.consoleEntryRepository = consoleEntryRepository;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Console capture shutdown hook triggered");
            stopCapture();
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
                tpe.shutdown();
                try {
                    if (!tpe.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        tpe.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    tpe.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Console capture shutdown complete");
        }, "console-capture-shutdown"));
        log.info("ConsoleCaptureService initialized");
    }

    /**
     * Starts capturing System.out and System.err.
     * <p>
     * <b>Where:</b> Called once during application startup.
     */
    public void startCapture() {
        if (originalOut != null || originalErr != null) {
            log.warn("Console capture already started");
            return;
        }

        originalOut = System.out;
        originalErr = System.err;

        ConsoleOutputStream outStream = new ConsoleOutputStream(originalOut, "STDOUT", this::saveEntry);
        ConsoleOutputStream errStream = new ConsoleOutputStream(originalErr, "STDERR", this::saveEntry);

        System.setOut(new PrintStream(outStream, true));
        System.setErr(new PrintStream(errStream, true));

        log.info("Console capture started");
    }

    /**
     * Stops capturing and restores original streams.
     */
    public void stopCapture() {
        if (originalOut != null) {
            System.setOut(originalOut);
            originalOut = null;
        }
        if (originalErr != null) {
            System.setErr(originalErr);
            originalErr = null;
        }
        log.info("Console capture stopped");
    }

    private void saveEntry(String source, String line) {
        String level = "INFO";
        String component = source;

        Matcher matcher = LOG_PATTERN.matcher(line);
        if (matcher.find()) {
            level = matcher.group(3);
            component = matcher.group(4);
        } else if (line.startsWith("WARN") || line.startsWith("WARNING")) {
            level = "WARN";
        } else if (line.startsWith("ERROR") || line.startsWith("Exception")) {
            level = "ERROR";
        } else if (line.startsWith("DEBUG")) {
            level = "DEBUG";
        } else if (line.startsWith("TRACE")) {
            level = "TRACE";
        }

        final String finalLevel = level;
        final String finalComponent = component;
        final String finalMessage = line;

        executor.execute(() -> {
            try {
                ConsoleEntry entry = new ConsoleEntry(finalLevel, finalComponent, finalMessage);
                consoleEntryRepository.save(entry);
            } catch (Exception e) {
                log.error("Failed to save console entry | error={}", e.getMessage(), e);
            }
        });
    }

    /**
     * Custom OutputStream that buffers characters and invokes a callback
     * for each complete line terminated by newline.
     */
    private static class ConsoleOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final PrintStream delegate;
        private final String source;
        private final LineConsumer consumer;

        interface LineConsumer {
            void consume(String source, String line);
        }

        ConsoleOutputStream(PrintStream delegate, String source, LineConsumer consumer) {
            this.delegate = delegate;
            this.source = source;
            this.consumer = consumer;
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
            if (b == '\n') {
                flushLine();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
            byte[] currentBytes = buffer.toByteArray();
            int searchStart = 0;
            while (searchStart < currentBytes.length) {
                int newlineIndex = indexOf(currentBytes, (byte) '\n', searchStart);
                if (newlineIndex >= 0) {
                    String line = new String(currentBytes, 0, newlineIndex, StandardCharsets.UTF_8);
                    consumer.consume(source, stripTrailingWhitespace(line));
                    searchStart = newlineIndex + 1;
                } else {
                    break;
                }
            }
            if (searchStart > 0) {
                byte[] remaining = new byte[currentBytes.length - searchStart];
                System.arraycopy(currentBytes, searchStart, remaining, 0, remaining.length);
                buffer.reset();
                buffer.write(remaining);
            } else if (buffer.size() > 0 && len > 0) {
                buffer.reset();
                buffer.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            if (buffer.size() > 0) {
                flushLine();
            }
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void flushLine() {
            try {
                String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                line = stripTrailingWhitespace(line);
                if (!line.isEmpty()) {
                    consumer.consume(source, line);
                }
            } catch (Exception e) {
                delegate.print("[ConsoleCaptureError] " + e.getMessage());
            } finally {
                buffer.reset();
            }
        }

        private int indexOf(byte[] data, byte target, int start) {
            for (int i = start; i < data.length; i++) {
                if (data[i] == target) return i;
            }
            return -1;
        }

        private String stripTrailingWhitespace(String s) {
            int len = s.length();
            while (len > 0 && Character.isWhitespace(s.charAt(len - 1))) {
                len--;
            }
            return s.substring(0, len);
        }
    }
}
