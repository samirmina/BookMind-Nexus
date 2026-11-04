package com.bookassistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory rate limiter for authentication endpoints.
 * <p>
 * Tracks POST requests to {@code /login} and {@code /register} per remote address
 * within a sliding time window. Exceeding the limit returns HTTP 429.
 * <p>
 * The client-address map is bounded: when it reaches capacity the oldest entry is
 * evicted so a flood of distinct IPs cannot exhaust memory. Empty entries are pruned
 * on each cleanup pass so the map does not grow unboundedly over time.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_TRACKED_IPS = 10_000;

    @Value("${rate.limit.trust-forwarded:false}")
    private boolean trustForwarded;

    private static final Map<String, ConcurrentLinkedDeque<Instant>> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isAuthEndpoint(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            String clientIp = getClientIp(request);
            Instant now = Instant.now();
            Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

            attempts.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());
            ConcurrentLinkedDeque<Instant> timestamps = attempts.get(clientIp);

            // Remove expired entries and prune empty buckets
            timestamps.removeIf(t -> t.isBefore(windowStart));
            pruneEmptyBuckets();

            if (timestamps.size() >= MAX_ATTEMPTS) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many login attempts. Please try again later.\"}");
                return;
            }

            timestamps.addLast(now);
        }
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
        // Last resort: evict oldest non-empty bucket to stay bounded
        if (attempts.size() > MAX_TRACKED_IPS) {
            Iterator<Map.Entry<String, ConcurrentLinkedDeque<Instant>>> it2 = attempts.entrySet().iterator();
            while (it2.hasNext() && attempts.size() > MAX_TRACKED_IPS) {
                it2.next();
                it2.remove();
            }
        }
    }

    private boolean isAuthEndpoint(String path) {
        return path.equals("/login") || path.equals("/register");
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustForwarded && isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp;
            }
        }
        return remoteAddr;
    }

    /**
     * Only trusts {@code X-Forwarded-For}/{@code X-Real-IP} when the direct
     * connection originates from a loopback or RFC1918 address (a trusted
     * front-end proxy). Prevents IP spoofing when the app is exposed directly
     * to the internet.
     */
    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || isPrivateAddress(address)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static boolean isPrivateAddress(java.net.InetAddress address) {
        byte[] addr = address.getAddress();
        if (addr.length == 4) {
            int a = addr[0] & 0xFF;
            int b = addr[1] & 0xFF;
            if (a == 10) {
                return true;
            }
            if (a == 172 && (b & 0xF0) == 16) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            if (a == 100 && (b & 0xC0) == 64) {
                return true;
            }
        }
        return false;
    }
}
