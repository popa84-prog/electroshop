package com.electroshop.service;

import com.electroshop.config.ApiMetricsRegistry;
import com.electroshop.dto.HealthStatusDto;
import com.electroshop.dto.SystemLogsDto;
import com.electroshop.model.SystemLogEntry;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Live health of the running instance.
 *
 * <p>Tasks 2 and 8. Turns the raw counters in {@link ApiMetricsRegistry} into the
 * figures the "Health Status" card and the system panel display.</p>
 *
 * <h2>The database check is a real round trip</h2>
 *
 * <p>Asking the connection pool whether it is healthy tells you what the pool believes,
 * which is not the same question. A pool can hold connections to a database that has
 * stopped answering, and it will report those connections as available until something
 * tries to use one. So the check executes a trivial statement and times it. That costs a
 * few milliseconds per dashboard load and is the only way the answer means anything.</p>
 *
 * <h2>Status is decided here, not in the browser</h2>
 *
 * <p>UP, DEGRADED and DOWN come from thresholds applied on the server, so the badge, the
 * ordering and any future alerting all read the same rule. A frontend deciding "amber
 * above 500ms" is a rule that exists in one place and is forgotten in every other.</p>
 */
@Service
public class HealthMetricsService {

    /** Availability below this is degraded. */
    private static final double DEGRADED_AVAILABILITY_PCT = 99.0;

    /** Availability below this is down. */
    private static final double DOWN_AVAILABILITY_PCT = 90.0;

    /** A 95th percentile above this is degraded, in milliseconds. */
    private static final double DEGRADED_P95_MS = 1_500;

    /** An endpoint slower than this at the 95th percentile is flagged serious. */
    private static final double ENDPOINT_DANGER_MS = 3_000;

    /** An endpoint slower than this is flagged as worth a look. */
    private static final double ENDPOINT_WARNING_MS = 1_000;

    /** How many endpoints the slow list returns. */
    private static final int SLOW_LIMIT = 10;

    /** How many recent failures the card shows. */
    private static final int RECENT_ERROR_LIMIT = 5;

    /** How long the database probe may take before it counts as a failure. */
    private static final int DB_PROBE_TIMEOUT_SECONDS = 3;

    private final ApiMetricsRegistry registry;
    private final SystemLogService systemLogService;
    private final DataSource dataSource;

    public HealthMetricsService(ApiMetricsRegistry registry,
                                SystemLogService systemLogService,
                                DataSource dataSource) {
        this.registry = registry;
        this.systemLogService = systemLogService;
        this.dataSource = dataSource;
    }

    /** A full health snapshot. */
    public HealthStatusDto health() {
        LocalDateTime now = LocalDateTime.now();

        Double availability = registry.availabilityPct();
        Double avgLatency = registry.averageLatencyMs();
        Double p95 = registry.overallP95();

        DbProbe db = probeDatabase();

        long total = registry.totalRequests();
        long failed = registry.failedRequests();
        Double errorRate = total == 0 ? null : round2(failed * 100.0 / total);

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        return new HealthStatusDto(
                statusFor(availability, p95, db.up()),
                registry.startedAt(),
                registry.uptimeSeconds(),
                round2(availability),
                round2(avgLatency),
                round2(p95),
                total,
                failed,
                errorRate,
                db.up() ? "UP" : "DOWN",
                round2(db.latencyMs()),
                usedMb,
                maxMb,
                slowestEndpoints(),
                recentErrors(),
                now
        );
    }

    /** Just the uptime block, for the operational-logs panel. */
    public SystemLogsDto.Uptime uptime() {
        return new SystemLogsDto.Uptime(
                registry.startedAt(),
                registry.uptimeSeconds(),
                registry.totalRequests(),
                registry.failedRequests(),
                round2(registry.availabilityPct()),
                round2(registry.averageLatencyMs()),
                round2(registry.overallP95())
        );
    }

    /**
     * Overall status from availability, latency and the database.
     *
     * <p>A database that does not answer is DOWN regardless of how the request counters
     * look, because those counters describe requests that were served before the
     * database stopped and say nothing about the next one.</p>
     *
     * <p>An instance that has served nothing is UP rather than DEGRADED. No traffic is
     * not a fault, and a freshly restarted instance showing amber would train operators
     * to ignore amber.</p>
     */
    private static String statusFor(Double availability, Double p95, boolean dbUp) {
        if (!dbUp) {
            return "DOWN";
        }
        if (availability == null) {
            return "UP";
        }
        if (availability < DOWN_AVAILABILITY_PCT) {
            return "DOWN";
        }
        if (availability < DEGRADED_AVAILABILITY_PCT
                || (p95 != null && p95 > DEGRADED_P95_MS)) {
            return "DEGRADED";
        }
        return "UP";
    }

    /**
     * Executes a trivial statement and times it.
     *
     * <p>Never throws. This method exists to report a failure, so throwing one would
     * take down the very panel that is meant to display it.</p>
     */
    private DbProbe probeDatabase() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(DB_PROBE_TIMEOUT_SECONDS)) {
                return new DbProbe(false, null);
            }
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(DB_PROBE_TIMEOUT_SECONDS);
                statement.execute("SELECT 1");
            }
            return new DbProbe(true, (System.nanoTime() - started) / 1_000_000.0);
        } catch (SQLException | RuntimeException e) {
            return new DbProbe(false, null);
        }
    }

    private List<HealthStatusDto.EndpointStat> slowestEndpoints() {
        List<ApiMetricsRegistry.Snapshot> snapshots = registry.snapshots();
        int limit = Math.min(SLOW_LIMIT, snapshots.size());

        List<HealthStatusDto.EndpointStat> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            ApiMetricsRegistry.Snapshot s = snapshots.get(i);
            out.add(new HealthStatusDto.EndpointStat(
                    s.endpoint(),
                    s.requests(),
                    round2(s.avgMs()),
                    round2(s.p95Ms()),
                    s.maxMs(),
                    s.errors(),
                    endpointSeverity(s.p95Ms(), s.errors())
            ));
        }
        return out;
    }

    private static String endpointSeverity(Double p95, long errors) {
        if (errors > 0 || (p95 != null && p95 > ENDPOINT_DANGER_MS)) {
            return "DANGER";
        }
        if (p95 != null && p95 > ENDPOINT_WARNING_MS) {
            return "WARNING";
        }
        return "INFO";
    }

    private List<HealthStatusDto.RecentError> recentErrors() {
        List<SystemLogEntry> entries = systemLogService.recentErrors(RECENT_ERROR_LIMIT);
        List<HealthStatusDto.RecentError> out = new ArrayList<>(entries.size());
        for (SystemLogEntry e : entries) {
            out.add(new HealthStatusDto.RecentError(
                    e.getCode(), e.getMessage(), e.getContext(), e.getCreatedAt()));
        }
        return out;
    }

    private static Double round2(Double v) {
        return v == null ? null : BigDecimal.valueOf(v)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Whether the database answered, and how quickly. */
    private record DbProbe(boolean up, Double latencyMs) {}
}
