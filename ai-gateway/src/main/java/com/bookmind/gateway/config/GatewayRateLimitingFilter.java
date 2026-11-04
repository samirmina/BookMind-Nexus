package com.bookmind.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory rate limiter for AI Gateway endpoints.
 * <p>
 * Tracks requests per remote address within a sliding time window.
 * Exceeding the limit returns HTTP 429.
 */
public class GatewayRateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 60;
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_TRACKED_IPS = 10_000;

    private static final Map<String, ConcurrentLinkedDeque<Instant>> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        attempts.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());
        ConcurrentLinkedDeque<Instant> timestamps = attempts.get(clientIp);

        timestamps.removeIf(t -> t.isBefore(windowStart));
        pruneEmptyBuckets();

        if (timestamps.size() >= MAX_REQUESTS) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        timestamps.addLast(now);
        filterChain.doFilter(request, response);
    }

    private void pruneEmptyBuckets() {
        if (attempts.size() <= MAX_TRACKED_IPS) {
            return;
        }
        Iterator<Map.Entry<String, ConcurrentLinkedDeque<Instant>>> it = attempts.entrySet().iterator();
        while (it.hasNext() && attempts.size() > MAX_TRACKED_IPS) {
            Map.Entry<String, ConcurrentLinkedDeque<Instant>> entry = it.next();
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
        if (attempts.size() > MAX_TRACKED_IPS) {
            Iterator<Map.Entry<String, ConcurrentLinkedDeque<Instant>>> it2 = attempts.entrySet().iterator();
            while (it2.hasNext() && attempts.size() > MAX_TRACKED_IPS) {
                it2.next();
                it2.remove();
            }
        }
    }
}
