package com.electroshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Live performance of the running instance.
 *
 * <p>Answers {@code GET /api/system/health-status} and backs the dashboard's "Health
 * Status" card.</p>
 *
 * <p>Everything here is counted in memory by {@code ApiMetricsFilter} as requests pass
 * through. Nothing is written to the database on the hot path: a monitoring system that
 * writes a row per request makes the database the bottleneck it exists to watch, and it
 * fails hardest at exactly the moment the database is already in trouble. Only failures
 * are persisted, and they live in {@link SystemLogsDto}.</p>
 *
 * <p>The consequence is that these counters reset when the process restarts, which is
 * why {@code startedAt} is reported alongside them. A 100% availability figure covering
 * four minutes of uptime is not the same claim as one covering four weeks, and the card
 * shows both numbers so it cannot be mistaken for the other.</p>
 *
 * @param status          {@code UP}, {@code DEGRADED} or {@code DOWN}, decided from the
 *                        thresholds below rather than left for the card to infer
 * @param startedAt       when the current process started
 * @param uptimeSeconds   how long it has been running
 * @param availabilityPct successful requests as a percentage of all requests
 * @param avgLatencyMs    mean response time across all measured endpoints
 * @param p95LatencyMs    95th percentile response time
 * @param requestsTotal   requests served since start
 * @param requestsFailed  requests that ended in a server error
 * @param errorRatePct    failed requests as a percentage of all requests
 * @param dbStatus        {@code UP} or {@code DOWN}, from a real round trip to the
 *                        database rather than from the connection pool's opinion of
 *                        itself
 * @param dbLatencyMs     how long that round trip took
 * @param memoryUsedMb    heap in use
 * @param memoryMaxMb     heap ceiling
 * @param slowest         the endpoints with the worst 95th percentile, slowest first
 * @param recentErrors    the last few failures, so the card can show what broke without
 *                        a second request
 * @param checkedAt       when this snapshot was taken
 */
public record HealthStatusDto(
        String status,
        LocalDateTime startedAt,
        long uptimeSeconds,
        Double availabilityPct,
        Double avgLatencyMs,
        Double p95LatencyMs,
        long requestsTotal,
        long requestsFailed,
        Double errorRatePct,
        String dbStatus,
        Double dbLatencyMs,
        long memoryUsedMb,
        long memoryMaxMb,
        List<EndpointStat> slowest,
        List<RecentError> recentErrors,
        LocalDateTime checkedAt
) {

    /**
     * Performance of one endpoint.
     *
     * @param endpoint  method and path template, for example {@code GET /api/products}
     * @param requests  how many times it was called
     * @param avgMs     mean duration
     * @param p95Ms     95th percentile duration
     * @param maxMs     worst observed duration
     * @param errors    how many calls failed
     * @param severity  {@code DANGER}, {@code WARNING} or {@code INFO}
     */
    public record EndpointStat(
            String endpoint,
            long requests,
            Double avgMs,
            Double p95Ms,
            Long maxMs,
            long errors,
            String severity
    ) {}

    /**
     * A recent failure, summarised.
     *
     * @param code      the error code
     * @param message   the summary line
     * @param context   the path or job that produced it
     * @param createdAt when it happened
     */
    public record RecentError(String code, String message, String context, LocalDateTime createdAt) {}
}
