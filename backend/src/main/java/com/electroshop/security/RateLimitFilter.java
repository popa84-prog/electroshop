package com.electroshop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple, self-contained in-memory per-IP token-bucket rate limiter applied to the
 * authentication endpoints, to slow down brute-force / credential-stuffing attempts.
 *
 * No external dependency: each client IP gets {@code capacity} tokens that refill
 * fully every {@code refillMinutes}.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final long refillIntervalMs;

    public RateLimitFilter(
            @Value("${app.ratelimit.auth.capacity}") long capacity,
            @Value("${app.ratelimit.auth.refill-minutes}") long refillMinutes) {
        this.capacity = capacity;
        this.refillIntervalMs = refillMinutes * 60_000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit the auth endpoints (login/register/refresh).
        return !request.getRequestURI().contains("/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientKey = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> new Bucket(capacity, refillIntervalMs));

        if (bucket.tryConsume()) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Thread-safe token bucket with periodic full refill.
     */
    private static final class Bucket {
        private final long capacity;
        private final long refillIntervalMs;
        private double tokens;
        private long lastRefill;

        Bucket(long capacity, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed <= 0) {
                return;
            }
            double refilled = (double) elapsed / refillIntervalMs * capacity;
            if (refilled > 0) {
                tokens = Math.min(capacity, tokens + refilled);
                lastRefill = now;
            }
        }
    }
}
