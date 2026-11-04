package com.bookassistant.config;

import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Servlet filter that injects correlation IDs, logs every inbound request, and logs
 * the corresponding response completion.
 * <p>
 * <b>What:</b> Assigns a unique correlation ID per request, logs method/URI/remote
 * address at INFO, headers at DEBUG, and warns on slow responses (over 2 s).
 * <p>
 * <b>Why:</b> Enables end-to-end tracing across async boundaries; required for
 * operational observability in the study application.
 */
@Component
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
        "/styles.css", "/js/", "/images/", "/favicon.ico", "/actuator/health"
    ));

    // OAuth-sensitive query parameters that must be masked in logs
    private static final Set<String> SENSITIVE_QUERY_PARAMS = new HashSet<>(Arrays.asList(
        "code", "state", "access_token", "refresh_token", "error", "error_description"
    ));

    /**
     * Filters every HTTP request.
     * <p>
     * <b>Where:</b> Required for correlation-ID-based tracing per the LoggingUtils design.
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @param chain    the filter chain
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Generate correlation ID for this request
        String correlationId = LoggingUtils.generateCorrelationId();
        
        // Log request entry
        logRequest(httpRequest);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Continue with filter chain
            chain.doFilter(request, response);
            
            // Log response
            long duration = System.currentTimeMillis() - startTime;
            logResponse(httpResponse, duration);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggingUtils.exception(log, "Request processing failed", e);
            throw e;
        } finally {
            // Clear correlation ID
            LoggingUtils.clearCorrelationId();
        }
    }

    /**
     * This name was chosen because this function logs incoming HTTP request details.
     * Logs method, URI, headers, and parameters at DEBUG level.
     */
    private void logRequest(HttpServletRequest request) {
        if (isExcluded(request.getRequestURI())) {
            return;
        }

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String remoteAddr = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String correlationId = LoggingUtils.getCorrelationId();

        if (log.isInfoEnabled()) {
            log.info("HTTP_REQUEST | method={} | uri={}{} | remoteAddr={} | correlationId={}", 
                method, uri, queryString != null ? "?" + maskSensitiveQueryParams(queryString) : "", remoteAddr, correlationId);
        }

        if (log.isDebugEnabled()) {
            log.debug("HTTP_REQUEST_HEADERS | userAgent={} | correlationId={}", userAgent, correlationId);
        }
    }

    /**
     * Masks sensitive OAuth query parameters (code, state, access_token, etc.) so
     * they are not written to application logs.
     */
    private static String maskSensitiveQueryParams(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        String[] params = queryString.split("&");
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) masked.append('&');
            String param = params[i];
            int eqIdx = param.indexOf('=');
            if (eqIdx > 0) {
                String key = param.substring(0, eqIdx);
                if (SENSITIVE_QUERY_PARAMS.contains(key)) {
                    masked.append(key).append("=***");
                } else {
                    masked.append(param);
                }
            } else {
                masked.append(param);
            }
        }
        return masked.toString();
    }

    /**
     * This name was chosen because this function logs HTTP response details.
     * Logs status code and duration, warns on slow responses.
     */
    private void logResponse(HttpServletResponse response, long duration) {
        String correlationId = LoggingUtils.getCorrelationId();
        int status = response.getStatus();
        
        // Slow operation threshold: 2 seconds
        long slowThreshold = 2000;
        
        if (duration > slowThreshold) {
            log.warn("HTTP_RESPONSE | status={} | duration={}ms [SLOW] | correlationId={}", 
                status, duration, correlationId);
        } else if (log.isInfoEnabled()) {
            log.info("HTTP_RESPONSE | status={} | duration={}ms | correlationId={}", 
                status, duration, correlationId);
        }
    }

    /**
     * This name was chosen because this function checks if a path should be excluded from logging.
     * Static resources and health checks don't need detailed logging.
     */
    private boolean isExcluded(String path) {
        if (path == null) {
            return false;
        }
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }
}


