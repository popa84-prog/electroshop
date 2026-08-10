package com.electroshop.service;

import com.electroshop.dto.RangeInfoDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The time window every analytics endpoint works in.
 *
 * <p>Eight panels accept a range parameter and every one of them needs the same four
 * things from it: where the window starts, where it ends, how to group the series
 * inside it, and which earlier window to compare against. Deriving those separately in
 * each service is how two panels end up disagreeing about what "last 30 days" means —
 * one counting back 30 days from now, another from midnight, a third including today
 * twice.</p>
 *
 * <p><b>Windows end at the current instant, not at midnight.</b> A "last 7 days" figure
 * that stops at midnight silently discards everything sold today, which is the number an
 * operator opening the dashboard at 4pm is most interested in. The window therefore runs
 * to now, and the comparison window is the immediately preceding stretch of exactly the
 * same length so the two are like for like — comparing a part-day against a whole day is
 * what produces a dashboard that reports a collapse every morning.</p>
 *
 * <p><b>Bucket labels are produced here, not in the browser.</b> A label and the grouping
 * it describes have to come from the same clock. A frontend formatting timestamps in the
 * visitor's timezone would label a chart with days that do not match the rows plotted on
 * it, and the discrepancy is invisible until someone reconciles a total by hand.</p>
 */
public enum MetricRange {

    /** The last 24 hours, grouped by hour. */
    H24("24h", ChronoUnit.HOURS, 24, Bucket.HOUR),

    /** The last 7 days, grouped by day. */
    D7("7d", ChronoUnit.DAYS, 7, Bucket.DAY),

    /** The last 30 days, grouped by day. */
    D30("30d", ChronoUnit.DAYS, 30, Bucket.DAY),

    /** The last 90 days, grouped by day. */
    D90("90d", ChronoUnit.DAYS, 90, Bucket.DAY),

    /** The last 3 calendar months, grouped by month. */
    M3("3m", ChronoUnit.MONTHS, 3, Bucket.MONTH),

    /** The last 6 calendar months, grouped by month. */
    M6("6m", ChronoUnit.MONTHS, 6, Bucket.MONTH),

    /** The last 12 calendar months, grouped by month. */
    M12("12m", ChronoUnit.MONTHS, 12, Bucket.MONTH);

    /** How a series is grouped inside a window. */
    public enum Bucket {
        HOUR, DAY, MONTH
    }

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00", Locale.ROOT);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private final String code;
    private final ChronoUnit unit;
    private final int amount;
    private final Bucket bucket;

    MetricRange(String code, ChronoUnit unit, int amount, Bucket bucket) {
        this.code = code;
        this.unit = unit;
        this.amount = amount;
        this.bucket = bucket;
    }

    public String code() {
        return code;
    }

    public Bucket bucket() {
        return bucket;
    }

    /**
     * Reads a range code, falling back to 30 days.
     *
     * <p>An unrecognised value produces the default rather than an error. The range
     * arrives in a query string, which means a stale bookmark or a typed URL can carry
     * anything, and refusing to render a dashboard because a parameter is misspelled
     * serves nobody. The resolved code is echoed in {@link RangeInfoDto} so the
     * interface shows which window it actually got.</p>
     */
    public static MetricRange parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return D30;
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (MetricRange r : values()) {
            if (r.code.equals(needle)) {
                return r;
            }
        }
        return D30;
    }

    /**
     * Same as {@link #parse(String)} but with a caller-chosen default.
     *
     * <p>The financial panel offers 3, 6 and 12 months and defaults to 12; the sales
     * chart offers hours and days and defaults to 30. One global default would be wrong
     * for one of them.</p>
     */
    public static MetricRange parse(String raw, MetricRange fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (MetricRange r : values()) {
            if (r.code.equals(needle)) {
                return r;
            }
        }
        return fallback;
    }

    /** Exclusive end of the window: the current instant. */
    public LocalDateTime to(LocalDateTime now) {
        return now;
    }

    /** Inclusive start of the window. */
    public LocalDateTime from(LocalDateTime now) {
        return now.minus(amount, unit);
    }

    /**
     * Inclusive start of the comparison window.
     *
     * <p>Exactly one window-length earlier, so the two periods are the same length and
     * the delta between them means something.</p>
     */
    public LocalDateTime previousFrom(LocalDateTime now) {
        return now.minus(2L * amount, unit);
    }

    /** Exclusive end of the comparison window, which is the start of the current one. */
    public LocalDateTime previousTo(LocalDateTime now) {
        return from(now);
    }

    /** The window, packaged for the response. */
    public RangeInfoDto info(LocalDateTime now) {
        return info(now, null);
    }

    /**
     * The window, packaged for the response, with the moment data collection began.
     *
     * <p>{@code dataAvailableFrom} matters for the panels built on newly collected
     * data. A twelve-month report over three weeks of measurement is not eleven months
     * of zero activity; it is eleven months of no measurement, and the interface says
     * so rather than plotting a flat line at zero.</p>
     */
    public RangeInfoDto info(LocalDateTime now, LocalDateTime dataAvailableFrom) {
        return new RangeInfoDto(
                code,
                from(now),
                to(now),
                bucket.name(),
                previousFrom(now),
                previousTo(now),
                dataAvailableFrom
        );
    }

    /**
     * Every bucket label in the window, oldest first.
     *
     * <p>A series must contain every bucket, including the empty ones. A chart that
     * omits quiet days compresses them out of existence and turns a decline into a
     * plateau, so services fill their results against this list rather than returning
     * only the buckets the database had rows for.</p>
     */
    public List<String> bucketLabels(LocalDateTime now) {
        List<String> labels = new ArrayList<>();
        LocalDateTime start = from(now);
        switch (bucket) {
            case HOUR -> {
                LocalDateTime cursor = start.truncatedTo(ChronoUnit.HOURS);
                while (!cursor.isAfter(now)) {
                    labels.add(cursor.format(HOUR_LABEL));
                    cursor = cursor.plusHours(1);
                }
            }
            case DAY -> {
                LocalDate cursor = start.toLocalDate();
                LocalDate end = now.toLocalDate();
                while (!cursor.isAfter(end)) {
                    labels.add(cursor.format(DAY_LABEL));
                    cursor = cursor.plusDays(1);
                }
            }
            case MONTH -> {
                LocalDate cursor = start.toLocalDate().withDayOfMonth(1);
                LocalDate end = now.toLocalDate().withDayOfMonth(1);
                while (!cursor.isAfter(end)) {
                    labels.add(cursor.format(MONTH_LABEL));
                    cursor = cursor.plusMonths(1);
                }
            }
        }
        return labels;
    }

    /**
     * The bucket label a timestamp belongs to.
     *
     * <p>The same formatter that produced {@link #bucketLabels(LocalDateTime)}, so a row
     * can never land in a bucket the axis does not contain.</p>
     */
    public String labelFor(LocalDateTime moment) {
        return switch (bucket) {
            case HOUR -> moment.format(HOUR_LABEL);
            case DAY -> moment.format(DAY_LABEL);
            case MONTH -> moment.format(MONTH_LABEL);
        };
    }

    /** The label for a date, used when the database returns a date rather than a timestamp. */
    public String labelFor(LocalDate date) {
        return switch (bucket) {
            case HOUR, DAY -> date.format(DAY_LABEL);
            case MONTH -> date.format(MONTH_LABEL);
        };
    }

    /** How many days the window spans, used by velocity and forecast calculations. */
    public long lengthInDays(LocalDateTime now) {
        return ChronoUnit.DAYS.between(from(now), to(now));
    }
}
