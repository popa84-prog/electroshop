package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A persisted operational event: an API failure, a cron failure, a database failure,
 * or a notable success.
 *
 * <p>The application already logs to the console, which is enough while someone is
 * watching the console and useless afterwards — a container restart takes the history
 * with it. The operational panel needs to answer "what broke overnight", so the events
 * have to outlive the process that produced them.</p>
 *
 * <p>This is deliberately not a general-purpose log sink. Writing every request into
 * the database would make the database the bottleneck it is supposed to be monitoring.
 * Only failures and a small set of named milestones are persisted; throughput and
 * latency live in {@link com.electroshop.config.ApiMetricsRegistry}, in memory, where
 * counting is free.</p>
 *
 * <p><b>Retention.</b> Rows older than the retention window are deleted by a scheduled
 * job in {@link com.electroshop.service.SystemLogService}. A monitoring table that
 * grows without bound eventually becomes the outage.</p>
 */
@Entity
@Table(
        name = "system_log_entries",
        indexes = {
                @Index(name = "idx_sle_source_level_created", columnList = "source, level, createdAt"),
                @Index(name = "idx_sle_created", columnList = "createdAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SystemLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SystemLogSource source;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private SystemLogLevel level;

    /**
     * Short machine-readable label, for example {@code HTTP_500}, {@code CRON_FAILED}
     * or {@code DB_TIMEOUT}. The panel groups by this, so it stays a stable code
     * rather than a sentence.
     */
    @Column(length = 80, nullable = false)
    private String code;

    /** Human-readable summary shown in the table row. */
    @Column(length = 500, nullable = false)
    private String message;

    /**
     * The HTTP path, job name or query that produced the entry.
     *
     * <p>Stored without query parameters. A query string can carry a token or a
     * customer identifier, and a monitoring table is exactly the place where such a
     * value would be read by people who have no need for it.</p>
     */
    @Column(length = 300)
    private String context;

    /** HTTP status for {@link SystemLogSource#API} entries; null elsewhere. */
    private Integer statusCode;

    /** How long the failing operation took, in milliseconds, when it is known. */
    private Long durationMs;

    /**
     * Truncated stack trace or driver detail.
     *
     * <p>Bounded on write. An unbounded column filled by a recursive failure is a way
     * to run the disk out of space during precisely the incident the table exists to
     * document.</p>
     */
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SystemLogEntry(SystemLogSource source, SystemLogLevel level, String code, String message) {
        this.source = source;
        this.level = level;
        this.code = code;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
