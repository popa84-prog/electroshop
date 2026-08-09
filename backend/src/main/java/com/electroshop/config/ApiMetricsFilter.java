package com.electroshop.config;

import com.electroshop.model.SystemLogSource;
import com.electroshop.service.SystemLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Times every request and records the failures.
 *
 * <p>Feeds {@link ApiMetricsRegistry} on the hot path and {@link SystemLogService} only
 * when something goes wrong. That split is the whole design: counting is free and
 * happens for every request, persisting is expensive and happens for the handful that
 * matter.</p>
 *
 * <p>Ordered highest so the measurement brackets the entire chain, including security.
 * A filter that runs after authentication would report an endpoint as fast while the
 * token verification in front of it was the slow part.</p>
 *
 * <p><b>Nothing here may throw.</b> A monitoring filter that fails takes every request
 * with it, so the recording is wrapped and its own failures are swallowed. Losing a
 * measurement is a nuisance; losing the request is an outage caused by the instrument.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiMetricsFilter extends OncePerRequestFilter {

    private final ApiMetricsRegistry registry;
    private final SystemLogService systemLogService;

    public ApiMetricsFilter(ApiMetricsRegistry registry, SystemLogService systemLogService) {
        this.registry = registry;
        this.systemLogService = systemLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        String endpoint = ApiMetricsRegistry.templatePath(
                request.getMethod(), request.getRequestURI());

        Exception thrown = null;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            // The exception is recorded and rethrown. Swallowing it here would turn a
            // failed request into a silently empty response, which is a far worse
            // failure mode than the original error.
            thrown = e;
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            try {
                record(endpoint, durationMs, response.getStatus(), thrown, request);
            } catch (RuntimeException ignored) {
                // Deliberately empty: see the class comment. The instrument must never
                // be the reason a request fails.
            }
        }
    }

    private void record(String endpoint,
                        long durationMs,
                        int status,
                        Exception thrown,
                        HttpServletRequest request) {
        registry.record(endpoint, durationMs, status);

        if (thrown != null) {
            systemLogService.recordException(
                    SystemLogSource.API,
                    "HTTP_" + (status >= 500 ? status : 500),
                    thrown.getClass().getSimpleName() + ": " + safeMessage(thrown),
                    endpoint,
                    status,
                    durationMs,
                    thrown);
            return;
        }

        if (status >= 500) {
            systemLogService.recordError(
                    SystemLogSource.API,
                    "HTTP_" + status,
                    "Cerere eșuată cu status " + status,
                    endpoint,
                    status,
                    durationMs,
                    null);
        }
    }

    /**
     * The exception message, bounded.
     *
     * <p>A driver or a recursive failure can produce a message of unreasonable length,
     * and this value ends up in a database column. Truncating here rather than at the
     * column keeps the log readable instead of merely storable.</p>
     */
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "fără mesaj";
        }
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }

    /**
     * Skips the health endpoint.
     *
     * <p>The hosting platform polls it every few seconds. Counting those would drown the
     * real traffic in the averages and make the busiest endpoint on the dashboard a
     * probe nobody cares about.</p>
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.endsWith("/health") || uri.endsWith("/actuator/health"));
    }
}
