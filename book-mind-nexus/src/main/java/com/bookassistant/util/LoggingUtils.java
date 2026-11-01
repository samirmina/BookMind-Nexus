package com.bookassistant.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized logging utility with correlation-ID support and structured logging.
 * <p>
 * <b>What:</b> Provides entry/exit logging helpers, external-call tracing, validation
 * failure logging, and resource-usage reporting with consistent correlation-ID threading.
 * <p>
 * <b>Why:</b> Eliminates ad-hoc log patterns across controllers and services; guarantees
 * that every log line carries a correlation ID for traceability.
 * <p>
 * <b>Where:</b> Required for operational observability per the application's logging
 * convention.
 */
public class LoggingUtils {

    private static final AtomicLong requestCounter = new AtomicLong(0);
    
    // Slow operation threshold in milliseconds (2 seconds)
    private static final long SLOW_OPERATION_THRESHOLD_MS = 2000;
    
    // Memory warning threshold (80% of max)
    private static final double MEMORY_WARNING_THRESHOLD = 0.8;

    // Prevent instantiation
    private LoggingUtils() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Generates a unique correlation ID and stores start-time context in MDC.
     * <p>
     * <b>Where:</b> Called at the start of each HTTP request by {@link RequestLoggingFilter}.
     *
     * @return the generated correlation ID string
     */
    public static String generateCorrelationId() {
        long requestNum = requestCounter.incrementAndGet();
        String correlationId = String.format("req-%d-%s", requestNum, UUID.randomUUID().toString().substring(0, 8));
        MDC.put("correlationId", correlationId);
        MDC.put("requestStartTime", String.valueOf(System.currentTimeMillis()));
        return correlationId;
    }

    /**
     * This name was chosen because this function gets the current correlation ID from MDC.
     * Returns the correlation ID for the current request thread.
     */
    public static String getCorrelationId() {
        return MDC.get("correlationId");
    }

    /**
     * This name was chosen because this function clears the correlation ID at request end.
     * Should be called at the end of each HTTP request.
     */
    public static void clearCorrelationId() {
        MDC.remove("correlationId");
        MDC.remove("requestStartTime");
    }

    /**
     * This name was chosen because this function logs function entry with parameters.
     * Uses DEBUG level for parameter values.
     */
    public static void enter(Logger log, String functionName) {
        if (log.isDebugEnabled()) {
            log.debug("ENTER {} | correlationId={}", functionName, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs function entry with one parameter.
     * Uses DEBUG level for parameter values.
     */
    public static void enter(Logger log, String functionName, String paramName, Object paramValue) {
        if (log.isDebugEnabled()) {
            log.debug("ENTER {} | {}={} | correlationId={}", 
                functionName, paramName, truncate(paramValue), getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs function entry with multiple parameters.
     * Uses DEBUG level for parameter values.
     */
    public static void enter(Logger log, String functionName, 
                            String param1Name, Object param1Value,
                            String param2Name, Object param2Value) {
        if (log.isDebugEnabled()) {
            log.debug("ENTER {} | {}={} | {}={} | correlationId={}", 
                functionName, 
                param1Name, truncate(param1Value),
                param2Name, truncate(param2Value),
                getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs function exit with result.
     * Includes execution time if start time was recorded.
     */
    public static void exit(Logger log, String functionName) {
        if (log.isDebugEnabled()) {
            log.debug("EXIT {} | correlationId={}", functionName, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs function exit with result value.
     * Includes execution time and warns if operation was slow.
     */
    public static void exit(Logger log, String functionName, String resultName, Object result) {
        long duration = getDuration();
        String resultStr = truncate(result);
        
        if (duration > SLOW_OPERATION_THRESHOLD_MS) {
            log.warn("EXIT {} | {}={} | duration={}ms [SLOW] | correlationId={}", 
                functionName, resultName, resultStr, duration, getCorrelationId());
        } else if (log.isDebugEnabled()) {
            log.debug("EXIT {} | {}={} | duration={}ms | correlationId={}", 
                functionName, resultName, resultStr, duration, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs function exit with count result.
     * Specialized for methods returning counts/sizes.
     */
    public static void exitWithCount(Logger log, String functionName, int count) {
        long duration = getDuration();
        
        if (duration > SLOW_OPERATION_THRESHOLD_MS) {
            log.warn("EXIT {} | count={} | duration={}ms [SLOW] | correlationId={}", 
                functionName, count, duration, getCorrelationId());
        } else if (log.isDebugEnabled()) {
            log.debug("EXIT {} | count={} | duration={}ms | correlationId={}", 
                functionName, count, duration, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs external API calls.
     * Logs endpoint, parameters, response code, and duration.
     */
    public static void externalCall(Logger log, String callType, String target, 
                                   String endpoint, int statusCode, long durationMs) {
        String statusLevel = statusCode >= 200 && statusCode < 300 ? "SUCCESS" : 
                            statusCode >= 400 && statusCode < 500 ? "CLIENT_ERROR" : "SERVER_ERROR";
        
        if (statusCode >= 500) {
            log.error("EXTERNAL_CALL | type={} | target={} | endpoint={} | status={} | duration={}ms | correlationId={}", 
                callType, target, endpoint, statusCode, durationMs, getCorrelationId());
        } else if (statusCode >= 400) {
            log.warn("EXTERNAL_CALL | type={} | target={} | endpoint={} | status={} | duration={}ms | correlationId={}", 
                callType, target, endpoint, statusCode, durationMs, getCorrelationId());
        } else if (log.isInfoEnabled()) {
            log.info("EXTERNAL_CALL | type={} | target={} | endpoint={} | status={} | duration={}ms | correlationId={}", 
                callType, target, endpoint, statusCode, durationMs, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs external API calls with request/response size.
     * Includes additional details about payload sizes.
     */
    public static void externalCall(Logger log, String callType, String target, 
                                   String endpoint, int statusCode, 
                                   long requestSize, long responseSize, long durationMs) {
        externalCall(log, callType, target, endpoint, statusCode, durationMs);
        if (log.isDebugEnabled()) {
            log.debug("EXTERNAL_CALL_DETAILS | requestBytes={} | responseBytes={} | correlationId={}", 
                requestSize, responseSize, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs database operations.
     * Logs query type, table, and result count.
     */
    public static void dbOperation(Logger log, String operation, String table, 
                                  long durationMs, int resultCount) {
        if (durationMs > SLOW_OPERATION_THRESHOLD_MS) {
            log.warn("DB_OPERATION | op={} | table={} | count={} | duration={}ms [SLOW] | correlationId={}", 
                operation, table, resultCount, durationMs, getCorrelationId());
        } else if (log.isDebugEnabled()) {
            log.debug("DB_OPERATION | op={} | table={} | count={} | duration={}ms | correlationId={}", 
                operation, table, resultCount, durationMs, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs file operations.
     * Logs operation type, file path, and result.
     */
    public static void fileOperation(Logger log, String operation, String filePath, 
                                    long fileSize, long durationMs) {
        if (durationMs > SLOW_OPERATION_THRESHOLD_MS) {
            log.warn("FILE_OPERATION | op={} | path={} | size={} | duration={}ms [SLOW] | correlationId={}", 
                operation, filePath, fileSize, durationMs, getCorrelationId());
        } else if (log.isDebugEnabled()) {
            log.debug("FILE_OPERATION | op={} | path={} | size={} | duration={}ms | correlationId={}", 
                operation, filePath, fileSize, durationMs, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs state changes with before/after values.
     * Useful for auditing and debugging state transitions.
     */
    public static void stateChange(Logger log, String entity, String entityId, 
                                  String fieldName, Object before, Object after) {
        if (log.isInfoEnabled()) {
            log.info("STATE_CHANGE | entity={} | id={} | field={} | BEFORE={} | AFTER={} | correlationId={}", 
                entity, entityId, fieldName, truncate(before), truncate(after), getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs validation failures with exact values.
     * Uses WARN level for validation failures.
     */
    public static void validationFailure(Logger log, String fieldName, String reason, Object value) {
        log.warn("VALIDATION_FAILURE | field={} | reason={} | value='{}' | correlationId={}", 
            fieldName, reason, truncate(value), getCorrelationId());
    }

    /**
     * This name was chosen because this function logs exceptions with full stack trace.
     * Never swallows exceptions - always logs them.
     */
    public static void exception(Logger log, String context, Throwable e) {
        log.error("EXCEPTION | context={} | type={} | message={} | correlationId={}", 
            context, e.getClass().getSimpleName(), e.getMessage(), getCorrelationId(), e);
    }

    /**
     * This name was chosen because this function logs resource usage (memory, threads).
     * Called at startup and periodically during operation.
     */
    public static void logResourceUsage(Logger log) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();
        double heapPercent = heapMax > 0 ? (double) heapUsed / heapMax : 0;
        
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        
        StringBuilder sb = new StringBuilder();
        sb.append("RESOURCE_USAGE | ");
        sb.append(String.format("heapUsed=%dMB | ", heapUsed / (1024 * 1024)));
        sb.append(String.format("heapMax=%dMB | ", heapMax / (1024 * 1024)));
        sb.append(String.format("heapPercent=%.1f%% | ", heapPercent * 100));
        sb.append(String.format("nonHeapUsed=%dMB | ", nonHeapUsage.getUsed() / (1024 * 1024)));
        sb.append(String.format("threads=%d | ", threadCount));
        sb.append(String.format("peakThreads=%d | ", peakThreadCount));
        sb.append("correlationId=").append(getCorrelationId());
        
        if (heapPercent > MEMORY_WARNING_THRESHOLD) {
            log.warn(sb.toString());
        } else if (log.isInfoEnabled()) {
            log.info(sb.toString());
        }
    }

    /**
     * This name was chosen because this function logs health check results.
     * Called periodically to verify system health.
     */
    public static void healthCheck(Logger log, String component, boolean healthy, String details) {
        String status = healthy ? "OK" : "FAIL";
        if (!healthy) {
            log.error("HEALTH_CHECK | component={} | status={} | details={} | correlationId={}", 
                component, status, details, getCorrelationId());
        } else if (log.isInfoEnabled()) {
            log.info("HEALTH_CHECK | component={} | status={} | details={} | correlationId={}", 
                component, status, details, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function logs cache statistics.
     * Shows hit rate and size information.
     */
    public static void cacheStats(Logger log, String cacheName, 
                                 long hits, long misses, long size) {
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0;
        
        if (log.isInfoEnabled()) {
            log.info("CACHE_STATS | name={} | hits={} | misses={} | hitRate={:.1f}% | size={} | correlationId={}", 
                cacheName, hits, misses, hitRate * 100, size, getCorrelationId());
        }
    }

    /**
     * This name was chosen because this function gets the duration of the current request.
     * Calculates time since request start.
     */
    public static long getDuration() {
        String startTimeStr = MDC.get("requestStartTime");
        if (startTimeStr == null) {
            return 0;
        }
        try {
            long startTime = Long.parseLong(startTimeStr);
            return System.currentTimeMillis() - startTime;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * This name was chosen because this function truncates long values for logging.
     * Prevents log flooding with large data.
     */
    private static String truncate(Object value) {
        if (value == null) {
            return "null";
        }
        String str = value.toString();
        if (str.length() > 200) {
            return str.substring(0, 200) + "... [truncated]";
        }
        return str;
    }

    /**
     * This name was chosen because this function creates a safe string for logging.
     * Handles null and truncates long strings.
     */
    public static String safeLog(String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "... [truncated]";
    }
}


