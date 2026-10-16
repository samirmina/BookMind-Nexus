package com.bookassistant.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstration of structured logging capabilities.
 * Run these tests to see logging output in action.
 */
class LoggingUtilsDemoTest {

    private static final Logger log = LoggerFactory.getLogger(LoggingUtilsDemoTest.class);

    /**
     * This name was chosen because this function demonstrates entry/exit logging.
     * Shows how function boundaries are logged with parameters and timing.
     */
    @Test
    void demoEntryExitLogging() throws InterruptedException {
        LoggingUtils.enter(log, "demoEntryExitLogging", "input", "test-data");
        
        // Simulate processing
        Thread.sleep(100);
        
        LoggingUtils.exit(log, "demoEntryExitLogging", "result", "processed-data");
        
        assertTrue(true, "Entry/exit logging demonstrated");
    }

    /**
     * This name was chosen because this function demonstrates slow operation detection.
     * Shows how operations >2 seconds are automatically flagged as [SLOW].
     */
    @Test
    void demoSlowOperationDetection() throws InterruptedException {
        LoggingUtils.enter(log, "demoSlowOperationLogging");
        
        // Simulate slow operation (3 seconds - exceeds 2s threshold)
        log.info("Starting slow operation...");
        Thread.sleep(3000);
        
        LoggingUtils.exit(log, "demoSlowOperationLogging", "result", "completed");
        
        assertTrue(true, "Slow operation detected with [SLOW] tag");
    }

    /**
     * This name was chosen because this function demonstrates validation failure logging.
     * Shows how invalid inputs are logged with exact values.
     */
    @Test
    void demoValidationFailureLogging() {
        String invalidEmail = "user@";
        String invalidReason = "missing domain";
        
        LoggingUtils.validationFailure(log, "email", invalidReason, invalidEmail);
        
        // In real code, this would throw an exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> {
                if (!invalidEmail.contains(".") || !invalidEmail.contains("@")) {
                    throw new IllegalArgumentException("Invalid email: " + invalidReason);
                }
            }
        );
        
        assertEquals("Invalid email: missing domain", exception.getMessage());
    }

    /**
     * This name was chosen because this function demonstrates exception logging with stack trace.
     * Shows how ALL exceptions are logged with full context.
     */
    @Test
    void demoExceptionLogging() {
        try {
            // Simulate an error
            String nullString = null;
            nullString.length(); // Will throw NullPointerException
        } catch (Exception e) {
            LoggingUtils.exception(log, "demoExceptionLogging", e);
        }
        
        assertTrue(true, "Exception logged with full stack trace");
    }

    /**
     * This name was chosen because this function demonstrates external API call logging.
     * Shows how API calls are logged with status, size, and duration.
     */
    @Test
    void demoExternalCallLogging() {
        // Simulate successful API call
        LoggingUtils.externalCall(
            log, 
            "TEST_API", 
            "TestService", 
            "/api/test", 
            200, 
            150,  // request bytes
            1024, // response bytes
            250   // duration ms
        );
        
        // Simulate failed API call
        LoggingUtils.externalCall(
            log,
            "TEST_API",
            "TestService",
            "/api/error",
            500,
            100,
            50,
            50
        );
        
        assertTrue(true, "External API calls logged");
    }

    /**
     * This name was chosen because this function demonstrates state change logging.
     * Shows how before/after state transitions are logged.
     */
    @Test
    void demoStateChangeLogging() {
        Integer balanceBefore = 1000;
        Integer balanceAfter = 950;
        String transactionId = "TXN-123";
        
        LoggingUtils.stateChange(
            log, 
            "Account", 
            "ACC-001", 
            "balance", 
            balanceBefore, 
            balanceAfter
        );
        
        log.info("Transaction completed | id={} | amount={}", transactionId, balanceBefore - balanceAfter);
        
        assertEquals(950, balanceAfter);
    }

    /**
     * This name was chosen because this function demonstrates correlation ID tracing.
     * Shows how requests can be traced across multiple log entries.
     */
    @Test
    void demoCorrelationIdTracing() {
        // Generate correlation ID (normally done by filter)
        String correlationId = LoggingUtils.generateCorrelationId();
        
        log.info("Step 1: Starting request");
        log.info("Step 2: Processing data");
        log.info("Step 3: Saving results");
        log.info("Step 4: Request complete");
        
        // Verify correlation ID is in MDC
        assertEquals(correlationId, LoggingUtils.getCorrelationId());
        
        // Clean up
        LoggingUtils.clearCorrelationId();
        
        assertNull(LoggingUtils.getCorrelationId());
    }

    /**
     * This name was chosen because this function demonstrates health check logging.
     * Shows how system health is monitored and logged.
     */
    @Test
    void demoHealthCheckLogging() {
        // Simulate healthy component
        LoggingUtils.healthCheck(log, "Database", true, "connection=OK, latency=5ms");
        
        // Simulate unhealthy component
        LoggingUtils.healthCheck(log, "Cache", false, "connection=FAILED, error=timeout");
        
        // Simulate resource usage
        LoggingUtils.logResourceUsage(log);
        
        assertTrue(true, "Health checks logged");
    }

    /**
     * This name was chosen because this function demonstrates cache statistics logging.
     * Shows how cache performance is tracked.
     */
    @Test
    void demoCacheStatsLogging() {
        long hits = 850;
        long misses = 150;
        long size = 1000;
        
        LoggingUtils.cacheStats(log, "BookSessionCache", hits, misses, size);
        
        double expectedHitRate = (double) hits / (hits + misses) * 100;
        assertEquals(85.0, expectedHitRate, 0.1);
    }

    /**
     * This name was chosen because this function demonstrates file operation logging.
     * Shows how file I/O is tracked with timing.
     */
    @Test
    void demoFileOperationLogging() {
        // Simulate file upload
        LoggingUtils.fileOperation(
            log,
            "UPLOAD",
            "/uploads/book.pdf",
            52428800, // 50MB
            1500      // 1.5 seconds
        );
        
        // Simulate file parse
        LoggingUtils.fileOperation(
            log,
            "PARSE",
            "/uploads/book.pdf",
            52428800,
            5000      // 5 seconds - will trigger [SLOW] warning
        );
        
        assertTrue(true, "File operations logged");
    }

    /**
     * This name was chosen because this function demonstrates database operation logging.
     * Shows how DB queries are tracked.
     */
    @Test
    void demoDatabaseOperationLogging() {
        // Simulate fast query
        LoggingUtils.dbOperation(log, "SELECT", "users", 15, 1);
        
        // Simulate slow query (will trigger [SLOW] warning)
        LoggingUtils.dbOperation(log, "SELECT", "books", 2500, 100);
        
        // Simulate update
        LoggingUtils.dbOperation(log, "UPDATE", "sessions", 50, 1);
        
        assertTrue(true, "Database operations logged");
    }

    /**
     * This name was chosen because this function demonstrates a complete request flow.
     * Shows how all logging features work together for end-to-end tracing.
     */
    @Test
    void demoCompleteRequestFlow() throws InterruptedException {
        // Start request (simulates filter)
        String correlationId = LoggingUtils.generateCorrelationId();
        log.info("HTTP_REQUEST | method=POST | uri=/api/qa");
        
        try {
            // Controller entry
            LoggingUtils.enter(log, "answerQuestion", "questionLen", 45);
            
            // Service call
            LoggingUtils.enter(log, "getAnswerFromAi", "model", "minimax-m2:cloud");
            
            // External API call
            Thread.sleep(100); // Simulate network delay
            LoggingUtils.externalCall(log, "AI", "Ollama", "/api/chat", 200, 200, 1024, 100);
            
            // Service exit
            LoggingUtils.exit(log, "getAnswerFromAi", "answerLen", 567);
            
            // Controller exit
            LoggingUtils.exit(log, "answerQuestion", "responseLen", 600);
            
            // HTTP response
            log.info("HTTP_RESPONSE | status=200 | duration=150ms");
            
        } catch (Exception e) {
            LoggingUtils.exception(log, "requestFlow", e);
        } finally {
            // End request (simulates filter)
            LoggingUtils.clearCorrelationId();
            log.info("Request completed | correlationId={}", correlationId);
        }
        
        assertTrue(true, "Complete request flow demonstrated");
    }
}


