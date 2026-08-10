package com.electroshop.service;

import com.electroshop.dto.SeriesPointDto;
import com.electroshop.dto.SystemLogsDto;
import com.electroshop.model.SystemLogEntry;
import com.electroshop.model.SystemLogLevel;
import com.electroshop.model.SystemLogSource;
import com.electroshop.repository.SystemLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists operational failures and reads them back for the operational panel.
 *
 * <p>Task 19.</p>
 *
 * <h2>Why writing never joins the caller's transaction</h2>
 *
 * <p>Almost every call to {@link #recordError} happens because something already failed,
 * and a failure very often means the surrounding transaction is being rolled back.
 * Writing the log entry in that transaction would roll it back too — the system would
 * lose precisely the records of its own failures, and would appear healthy in exactly
 * the situations it is not. {@link Propagation#REQUIRES_NEW} gives each entry its own
 * transaction so it survives the rollback that prompted it.</p>
 *
 * <h2>Why every write is wrapped</h2>
 *
 * <p>If persisting a log entry throws — the database is down, which is a leading cause
 * of things worth logging — that exception must not replace the original failure. The
 * write is caught and dropped to the console. A monitoring system that can turn a
 * handled error into an unhandled one is worse than no monitoring.</p>
 *
 * <h2>Retention is enforced, not recommended</h2>
 *
 * <p>A monitoring table that grows without bound eventually becomes the outage it was
 * installed to detect. A nightly job deletes anything past the window.</p>
 */
@Service
public class SystemLogService {

    /** How long entries are kept. */
    static final int RETENTION_DAYS = 90;

    /** Longest detail stored, in characters. */
    private static final int MAX_DETAIL = 4_000;

    /** Longest message stored, matching the column. */
    private static final int MAX_MESSAGE = 500;

    /** Longest context stored, matching the column. */
    private static final int MAX_CONTEXT = 300;

    /** How many stack frames are kept. Enough to locate a fault, short of a novel. */
    private static final int MAX_FRAMES = 20;

    /** How many distinct error codes the ranked list returns. */
    private static final int TOP_ERROR_LIMIT = 10;

    /** Largest export, so a CSV request cannot stream the whole table into memory. */
    private static final int EXPORT_LIMIT = 10_000;

    private final SystemLogRepository repository;

    public SystemLogService(SystemLogRepository repository) {
        this.repository = repository;
    }

    // =====================================================================
    //  Writing
    // =====================================================================

    /** Records a failure. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(SystemLogSource source,
                            String code,
                            String message,
                            String context,
                            Integer statusCode,
                            Long durationMs,
                            String detail) {
        write(source, SystemLogLevel.ERROR, code, message, context, statusCode, durationMs, detail);
    }

    /** Records a failure, deriving the detail from an exception. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordException(SystemLogSource source,
                                String code,
                                String message,
                                String context,
                                Integer statusCode,
                                Long durationMs,
                                Throwable thrown) {
        write(source, SystemLogLevel.ERROR, code, message, context,
                statusCode, durationMs, stackTrace(thrown));
    }

    /** Records something that degraded but did not fail. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordWarning(SystemLogSource source, String code, String message, String context) {
        write(source, SystemLogLevel.WARN, code, message, context, null, null, null);
    }

    /** Records a notable success: a job finished, a backup completed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInfo(SystemLogSource source, String code, String message, String context) {
        write(source, SystemLogLevel.INFO, code, message, context, null, null, null);
    }

    private void write(SystemLogSource source,
                       SystemLogLevel level,
                       String code,
                       String message,
                       String context,
                       Integer statusCode,
                       Long durationMs,
                       String detail) {
        try {
            SystemLogEntry entry = new SystemLogEntry(
                    source, level,
                    truncate(code, 80),
                    truncate(message, MAX_MESSAGE));
            entry.setContext(truncate(stripQuery(context), MAX_CONTEXT));
            entry.setStatusCode(statusCode);
            entry.setDurationMs(durationMs);
            entry.setDetail(truncate(detail, MAX_DETAIL));
            repository.save(entry);
        } catch (RuntimeException e) {
            // See the class comment. The console is the fallback of last resort; it is
            // better than turning a handled failure into an unhandled one.
            System.err.println("[SystemLogService] nu s-a putut scrie intrarea de jurnal: "
                    + e.getMessage());
        }
    }

    /**
     * Removes the query string from a context value.
     *
     * <p>A query string can carry a token, a session identifier or a customer email, and
     * an operational log is exactly the place where such a value would be read by people
     * who have no need for it — and exported to CSV, and forwarded in a support thread.
     * The path alone is enough to identify the endpoint.</p>
     */
    private static String stripQuery(String context) {
        if (context == null) {
            return null;
        }
        int q = context.indexOf('?');
        return q < 0 ? context : context.substring(0, q);
    }

    /** Bounded stack trace. Unbounded text from a recursive failure is a disk-filler. */
    private static String stackTrace(Throwable thrown) {
        if (thrown == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            pw.println(thrown.getClass().getName() + ": " + thrown.getMessage());
            StackTraceElement[] frames = thrown.getStackTrace();
            for (int i = 0; i < Math.min(MAX_FRAMES, frames.length); i++) {
                pw.println("\tat " + frames[i]);
            }
            if (frames.length > MAX_FRAMES) {
                pw.println("\t… încă " + (frames.length - MAX_FRAMES) + " cadre");
            }
            Throwable cause = thrown.getCause();
            if (cause != null && cause != thrown) {
                pw.println("Cauzat de: " + cause.getClass().getName() + ": " + cause.getMessage());
            }
        }
        return sw.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    // =====================================================================
    //  Retention
    // =====================================================================

    /**
     * Deletes entries past the retention window.
     *
     * <p>Runs nightly at 03:20, well away from both the business day and the top of the
     * hour where every other scheduled job in the world starts.</p>
     */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int removed = repository.deleteOlderThan(cutoff);
        if (removed > 0) {
            recordInfo(SystemLogSource.APP, "LOG_PURGE",
                    "S-au șters " + removed + " intrări mai vechi de " + RETENTION_DAYS + " de zile",
                    "SystemLogService.purgeOldEntries");
        }
    }

    // =====================================================================
    //  Reading
    // =====================================================================

    /**
     * The operational panel for a window, filtered and paged.
     *
     * @param range  the window
     * @param source restrict to one source, or {@code null} for all
     * @param level  restrict to one level, or {@code null} for all
     * @param query  free-text search across message, code and context
     * @param page   zero-based page index
     * @param size   page size
     * @param uptime live availability figures from the metrics registry
     */
    @Transactional(readOnly = true)
    public SystemLogsDto logs(MetricRange range,
                              String source,
                              String level,
                              String query,
                              int page,
                              int size,
                              SystemLogsDto.Uptime uptime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        SystemLogSource sourceFilter = parseSource(source);
        SystemLogLevel levelFilter = parseLevel(level);
        String q = query == null || query.isBlank() ? null : query.trim();

        Page<SystemLogEntry> found = repository.search(
                sourceFilter, levelFilter, from, to, q,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size))));

        List<SystemLogsDto.LogEntry> entries = new ArrayList<>(found.getContent().size());
        for (SystemLogEntry e : found.getContent()) {
            entries.add(toDto(e));
        }

        return new SystemLogsDto(
                entries,
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages(),
                counts(from, to),
                dailyErrors(range, now, from, to),
                topErrors(from, to),
                uptime,
                repository.earliestEntry(),
                range.info(now, repository.earliestEntry())
        );
    }

    /** Rows for the CSV export, capped. */
    @Transactional(readOnly = true)
    public List<SystemLogsDto.LogEntry> forExport(MetricRange range,
                                                  String source,
                                                  String level,
                                                  String query) {
        LocalDateTime now = LocalDateTime.now();
        String q = query == null || query.isBlank() ? null : query.trim();

        List<SystemLogEntry> found = repository.searchForExport(
                parseSource(source), parseLevel(level),
                range.from(now), range.to(now), q,
                PageRequest.of(0, EXPORT_LIMIT));

        List<SystemLogsDto.LogEntry> out = new ArrayList<>(found.size());
        for (SystemLogEntry e : found) {
            out.add(toDto(e));
        }
        return out;
    }

    /** The most recent failures, for the health card. */
    @Transactional(readOnly = true)
    public List<SystemLogEntry> recentErrors(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return repository.searchForExport(
                null, SystemLogLevel.ERROR, now.minusDays(7), now, null,
                PageRequest.of(0, Math.max(1, limit)));
    }

    private static SystemLogsDto.LogEntry toDto(SystemLogEntry e) {
        return new SystemLogsDto.LogEntry(
                e.getId(),
                e.getSource() == null ? null : e.getSource().name(),
                e.getLevel() == null ? null : e.getLevel().name(),
                e.getCode(),
                e.getMessage(),
                e.getContext(),
                e.getStatusCode(),
                e.getDurationMs(),
                e.getDetail(),
                e.getCreatedAt());
    }

    /** Per-source counters, from one grouped query rather than one query per source. */
    private List<SystemLogsDto.SourceCount> counts(LocalDateTime from, LocalDateTime to) {
        Map<SystemLogSource, long[]> grouped = new EnumMap<>(SystemLogSource.class);
        for (SystemLogSource s : SystemLogSource.values()) {
            grouped.put(s, new long[3]);
        }

        for (Object[] row : repository.countsBySourceAndLevel(from, to)) {
            SystemLogSource source = (SystemLogSource) row[0];
            SystemLogLevel level = (SystemLogLevel) row[1];
            long count = ((Number) row[2]).longValue();
            long[] slot = grouped.get(source);
            if (slot == null) {
                continue;
            }
            switch (level) {
                case ERROR -> slot[0] = count;
                case WARN -> slot[1] = count;
                case INFO -> slot[2] = count;
            }
        }

        List<SystemLogsDto.SourceCount> out = new ArrayList<>(grouped.size());
        for (Map.Entry<SystemLogSource, long[]> entry : grouped.entrySet()) {
            long[] slot = entry.getValue();
            out.add(new SystemLogsDto.SourceCount(
                    entry.getKey().name(), slot[0], slot[1], slot[2]));
        }
        return out;
    }

    /** Error count per day, with quiet days present as zero. */
    private List<SeriesPointDto> dailyErrors(MetricRange range,
                                             LocalDateTime now,
                                             LocalDateTime from,
                                             LocalDateTime to) {
        Map<String, Long> byDay = new LinkedHashMap<>();
        for (Object[] row : repository.dailyErrorCounts(from, to)) {
            String label = String.format("%04d-%02d-%02d",
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue());
            byDay.put(label, ((Number) row[3]).longValue());
        }

        List<SeriesPointDto> out = new ArrayList<>();
        LocalDate cursor = from.toLocalDate();
        LocalDate end = now.toLocalDate();
        while (!cursor.isAfter(end)) {
            // A day with no failures is a real zero and belongs on the chart. Omitting
            // it would compress a good week into a short gap and make the incident that
            // followed look like a continuation rather than a change.
            String label = cursor.toString();
            long count = byDay.getOrDefault(label, 0L);
            out.add(SeriesPointDto.of(label, BigDecimal.valueOf(count), count));
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    /** The most frequent error codes, worst first. */
    private List<SystemLogsDto.ErrorGroup> topErrors(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = repository.topErrorCodes(
                from, to, PageRequest.of(0, TOP_ERROR_LIMIT));

        List<SystemLogsDto.ErrorGroup> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(new SystemLogsDto.ErrorGroup(
                    (String) row[0],
                    row[1] == null ? null : ((SystemLogSource) row[1]).name(),
                    ((Number) row[2]).longValue(),
                    (LocalDateTime) row[3]));
        }
        return out;
    }

    /**
     * Reads a source filter, treating anything unrecognised as "no filter".
     *
     * <p>The value arrives in a query string, so a stale bookmark can carry a source
     * that no longer exists. Returning everything is a better answer than an error page
     * for a monitoring panel somebody has opened because something is already wrong.</p>
     */
    private static SystemLogSource parseSource(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return SystemLogSource.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SystemLogLevel parseLevel(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return SystemLogLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
