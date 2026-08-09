package com.electroshop.config;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory counters for API throughput, latency and failures.
 *
 * <p>Tasks 2, 8 and 19 all need to answer "how is the API doing right now". The obvious
 * implementation is a row per request in the database, and it is the wrong one: it makes
 * the database the bottleneck it is supposed to be monitoring, and it fails hardest at
 * exactly the moment the database is already in trouble — which is the moment the
 * monitoring matters. Counting in memory costs an atomic increment per request and
 * cannot take the application down with it.</p>
 *
 * <p>The trade is that these counters reset when the process restarts. That is disclosed
 * rather than papered over: {@code startedAt} travels with every reading, so a 100%
 * availability figure covering four minutes of uptime cannot be mistaken for one
 * covering four weeks. Failures — the events worth keeping — are persisted separately by
 * {@code SystemLogService}, so nothing that matters is lost to a restart.</p>
 *
 * <h2>Why latency is a bounded reservoir rather than a running average</h2>
 *
 * <p>A mean hides the tail, and the tail is where users notice. The 95th percentile
 * needs the distribution, so a bounded sample of recent durations is kept per endpoint.
 * Bounded matters: an unbounded list on a busy endpoint is a memory leak that grows
 * fastest under the load it is meant to describe.</p>
 *
 * <h2>Why the endpoint key is a template, not a path</h2>
 *
 * <p>{@code /api/products/41} and {@code /api/products/9327} are the same endpoint with
 * different arguments. Keying on the raw path would create one entry per product ever
 * requested and turn this map into an unbounded cache of URLs. Numeric segments are
 * therefore folded to a placeholder before use.</p>
 */
@Component
public class ApiMetricsRegistry {

    /** How many recent durations are retained per endpoint for percentile estimation. */
    private static final int SAMPLE_SIZE = 256;

    /** Largest number of distinct endpoints tracked, as a guard against key explosion. */
    private static final int MAX_ENDPOINTS = 400;

    private final LocalDateTime startedAt = LocalDateTime.now();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();

    private final Map<String, EndpointStats> endpoints = new ConcurrentHashMap<>();

    /**
     * Records one completed request.
     *
     * @param endpoint method plus templated path, for example {@code GET /api/products}
     * @param durationMs how long it took
     * @param status the HTTP status returned
     */
    public void record(String endpoint, long durationMs, int status) {
        totalRequests.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);

        // Only 5xx counts as a failure. A 404 or a 403 is the API working correctly and
        // saying no; folding those into an error rate would make a bot probing for
        // admin URLs look like an outage.
        boolean failed = status >= 500;
        if (failed) {
            failedRequests.incrementAndGet();
        }

        if (endpoints.size() >= MAX_ENDPOINTS && !endpoints.containsKey(endpoint)) {
            // The map is full and this is a new key. Dropping the sample is better than
            // growing without bound: the aggregate counters above still see the request,
            // so the totals stay correct and only the per-endpoint breakdown is capped.
            return;
        }
        endpoints.computeIfAbsent(endpoint, EndpointStats::new).record(durationMs, failed);
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public long uptimeSeconds() {
        return java.time.Duration.between(startedAt, LocalDateTime.now()).getSeconds();
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long failedRequests() {
        return failedRequests.get();
    }

    /** Mean response time across every measured request, or null before the first one. */
    public Double averageLatencyMs() {
        long n = totalRequests.get();
        return n == 0 ? null : (double) totalDurationMs.get() / n;
    }

    /**
     * Successful requests as a percentage of all requests.
     *
     * <p>Null before the first request rather than 100%. An instance that has served
     * nothing has not proved anything, and reporting perfect availability for it is the
     * kind of green light that gets trusted exactly once.</p>
     */
    public Double availabilityPct() {
        long n = totalRequests.get();
        if (n == 0) {
            return null;
        }
        return (n - failedRequests.get()) * 100.0 / n;
    }

    /** The 95th percentile across the pooled samples of every endpoint. */
    public Double overallP95() {
        List<Long> pooled = new ArrayList<>();
        for (EndpointStats stats : endpoints.values()) {
            pooled.addAll(stats.snapshot());
        }
        return percentile(pooled, 95);
    }

    /** Per-endpoint statistics, slowest 95th percentile first. */
    public List<Snapshot> snapshots() {
        List<Snapshot> out = new ArrayList<>(endpoints.size());
        for (EndpointStats stats : endpoints.values()) {
            List<Long> sample = stats.snapshot();
            out.add(new Snapshot(
                    stats.endpoint,
                    stats.count.get(),
                    stats.count.get() == 0 ? null : (double) stats.totalMs.get() / stats.count.get(),
                    percentile(sample, 95),
                    stats.maxMs.get() == 0 ? null : stats.maxMs.get(),
                    stats.errors.get()
            ));
        }
        out.sort(Comparator.comparingDouble(
                (Snapshot s) -> s.p95Ms() == null ? -1 : s.p95Ms()).reversed());
        return out;
    }

    /** Clears every counter. Used only by tests; never called by the application. */
    public void reset() {
        totalRequests.set(0);
        failedRequests.set(0);
        totalDurationMs.set(0);
        endpoints.clear();
    }

    /**
     * The p-th percentile of a sample, or null when the sample is empty.
     *
     * <p>Nearest-rank, computed on a copy. Sorting the live list would corrupt the
     * reservoir that another thread is writing into.</p>
     */
    static Double percentile(List<Long> values, int p) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return (double) sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    /**
     * Folds a request path into an endpoint template.
     *
     * <p>Numeric segments become {@code {id}} so every product, order and user shares
     * one key. Without this the registry would accumulate one entry per resource ever
     * requested, which is both useless as a summary and unbounded as a data structure.</p>
     */
    public static String templatePath(String method, String path) {
        if (path == null || path.isEmpty()) {
            return method + " /";
        }
        StringBuilder sb = new StringBuilder(path.length());
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()) {
                continue;
            }
            sb.append('/');
            sb.append(isNumeric(segment) ? "{id}" : segment);
        }
        return method + (sb.length() == 0 ? " /" : " " + sb);
    }

    private static boolean isNumeric(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    /** A read-only view of one endpoint's statistics. */
    public record Snapshot(
            String endpoint,
            long requests,
            Double avgMs,
            Double p95Ms,
            Long maxMs,
            long errors
    ) {}

    /** Mutable counters for one endpoint. */
    private static final class EndpointStats {

        private final String endpoint;
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();

        /**
         * Recent durations, capped at {@link #SAMPLE_SIZE}.
         *
         * <p>Guarded by its own monitor rather than made lock-free. The critical section
         * is two array operations on a 256-element list; a lock-free ring buffer would
         * be more code to review for a contention level this never reaches.</p>
         */
        private final List<Long> samples = new ArrayList<>(SAMPLE_SIZE);

        private EndpointStats(String endpoint) {
            this.endpoint = endpoint;
        }

        void record(long durationMs, boolean failed) {
            count.incrementAndGet();
            totalMs.addAndGet(durationMs);
            maxMs.accumulateAndGet(durationMs, Math::max);
            if (failed) {
                errors.incrementAndGet();
            }
            synchronized (samples) {
                if (samples.size() >= SAMPLE_SIZE) {
                    samples.remove(0);
                }
                samples.add(durationMs);
            }
        }

        List<Long> snapshot() {
            synchronized (samples) {
                return new ArrayList<>(samples);
            }
        }
    }
}
