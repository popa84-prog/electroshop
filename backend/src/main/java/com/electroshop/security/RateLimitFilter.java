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
 * Simple, self-contained in-memory per-IP token-bucket rate limiter applied to
 * sensitive API surfaces, to slow down brute-force / credential-stuffing and
 * scripted-abuse attempts (feature #6: "rate limiting la API-uri sensibile").
 *
 * No external dependency: each client IP gets {@code capacity} tokens that refill
 * fully every {@code refillMinutes}. Two independent buckets are kept per IP —
 * one (tight) for the auth endpoints where brute-forcing passwords/2FA codes is
 * the concern, one (looser) for admin/product write operations where the concern
 * is scripted abuse rather than password guessing — so a burst of normal admin
 * clicks never trips the same limiter that guards login.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> writeBuckets = new ConcurrentHashMap<>();

    private final long authCapacity;
    private final long authRefillIntervalMs;
    private final long writeCapacity;
    private final long writeRefillIntervalMs;

    public RateLimitFilter(
            @Value("${app.ratelimit.auth.capacity}") long authCapacity,
            @Value("${app.ratelimit.auth.refill-minutes}") long authRefillMinutes,
            @Value("${app.ratelimit.write.capacity}") long writeCapacity,
            @Value("${app.ratelimit.write.refill-minutes}") long writeRefillMinutes) {
        this.authCapacity = authCapacity;
        this.authRefillIntervalMs = authRefillMinutes * 60_000L;
        this.writeCapacity = writeCapacity;
        this.writeRefillIntervalMs = writeRefillMinutes * 60_000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAuthEndpoint(request) && !isSensitiveWrite(request);
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        return request.getRequestURI().contains("/auth/");
    }

    /** Mutating (non-GET) calls into the admin panel or the product-mutation endpoints. */
    private boolean isSensitiveWrite(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.contains("/admin/") || uri.contains("/products/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientKey = resolveClientIp(request);
        boolean auth = isAuthEndpoint(request);
        Map<String, Bucket> pool = auth ? authBuckets : writeBuckets;
        long capacity = auth ? authCapacity : writeCapacity;
        long refillIntervalMs = auth ? authRefillIntervalMs : writeRefillIntervalMs;

        Bucket bucket = pool.computeIfAbsent(clientKey, k -> new Bucket(capacity, refillIntervalMs));

        if (bucket.tryConsume()) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429); // 429 Too Many Requests (no named constant in the Servlet API)
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
