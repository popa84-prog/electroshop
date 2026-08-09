package com.electroshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The operational log, grouped the way an operator reads it.
 *
 * <p>Answers {@code GET /api/system/logs}.</p>
 *
 * <p>The panel is organised by source rather than by severity, because the four sources
 * fail for unrelated reasons and are fixed by unrelated actions. A burst of database
 * errors means the database is unreachable. A burst of API errors with the database
 * healthy means one endpoint is broken. Filed together under "errors" the two look
 * identical, and the distinction is the whole diagnosis.</p>
 *
 * <p>{@code topErrors} exists because a wall of individual failures is not information.
 * One broken endpoint hit two thousand times produces two thousand rows and one
 * problem, and the ranked list is what turns the former into the latter.</p>
 *
 * @param entries        the filtered page of log rows, newest first
 * @param page           zero-based page index
 * @param size           page size
 * @param totalElements  how many rows match the filter
 * @param totalPages     how many pages that is
 * @param counts         per-source, per-level counters for the summary tiles
 * @param dailyErrors    error count per day, oldest first, for the incident sparkline
 * @param topErrors      the most frequent error codes in the window, worst first
 * @param uptime         availability computed from the API metrics registry
 * @param collectingSince when the first entry was written, or null when the table is
 *                       empty — the same distinction the marketing panel makes between
 *                       "no failures" and "no measurement"
 * @param range          the resolved window
 */
public record SystemLogsDto(
        List<LogEntry> entries,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<SourceCount> counts,
        List<SeriesPointDto> dailyErrors,
        List<ErrorGroup> topErrors,
        Uptime uptime,
        LocalDateTime collectingSince,
        RangeInfoDto range
) {

    /**
     * One row of the log table.
     *
     * @param id         database id
     * @param source     {@code API}, {@code CRON}, {@code DB}, {@code AUTH}, {@code APP}
     * @param level      {@code ERROR}, {@code WARN} or {@code INFO}
     * @param code       stable machine-readable label, such as {@code HTTP_500}
     * @param message    human-readable summary
     * @param context    the path, job name or query that produced it, without query
     *                   parameters — a monitoring table is exactly where a token in a
     *                   query string would be read by people with no need for it
     * @param statusCode HTTP status for API entries, null elsewhere
     * @param durationMs how long the failing operation took, null when unknown
     * @param detail     truncated stack trace or driver detail
     * @param createdAt  when it happened
     */
    public record LogEntry(
            Long id,
            String source,
            String level,
            String code,
            String message,
            String context,
            Integer statusCode,
            Long durationMs,
            String detail,
            LocalDateTime createdAt
    ) {}

    /**
     * Counters for one source.
     *
     * @param source the source
     * @param errors how many ERROR entries it produced in the window
     * @param warns  how many WARN entries
     * @param infos  how many INFO entries
     */
    public record SourceCount(String source, long errors, long warns, long infos) {}

    /**
     * One recurring failure, with its frequency.
     *
     * @param code       the error code
     * @param source     where it comes from
     * @param count      how many times it occurred in the window
     * @param lastSeenAt the most recent occurrence, which is what says whether this is
     *                   an active incident or a scar from last week
     */
    public record ErrorGroup(String code, String source, long count, LocalDateTime lastSeenAt) {}

    /**
     * Availability, computed from request counters rather than claimed.
     *
     * @param startedAt        when the current process started
     * @param uptimeSeconds    how long it has been running
     * @param totalRequests    requests served since start
     * @param failedRequests   requests that ended in a server error
     * @param availabilityPct  successful requests as a percentage of all requests
     * @param avgLatencyMs     mean response time since start
     * @param p95LatencyMs     95th percentile response time — the figure that shows the
     *                         slow tail a mean hides
     */
    public record Uptime(
            LocalDateTime startedAt,
            long uptimeSeconds,
            long totalRequests,
            long failedRequests,
            Double availabilityPct,
            Double avgLatencyMs,
            Double p95LatencyMs
    ) {}
}
