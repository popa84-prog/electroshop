package com.electroshop.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A measure compared against the same measure one period earlier.
 *
 * <p>Requirement: "tooltips with details, for example variation against last week".
 * Every such tooltip needs three things — what the value is now, what it was, and how
 * far it moved — and the third one is where a naive implementation goes wrong. The
 * percentage is computed here, once, so that every panel treats the awkward cases the
 * same way.</p>
 *
 * <p><b>Growth from zero has no percentage.</b> Revenue that goes from 0 to 5 000 has
 * not grown by any number of percent; the denominator does not exist. Reporting
 * {@code +100%} understates it, {@code +Infinity} is not a number a person reads, and
 * {@code 0%} claims nothing happened. {@code changePct} is {@code null} in that case
 * and the frontend shows "nou" instead of a figure.</p>
 *
 * @param current    the value for the period being reported
 * @param previous   the value for the immediately preceding period of equal length
 * @param changePct  signed percentage change, rounded to one decimal, or {@code null}
 *                   when the previous period was zero
 * @param improving  whether the movement is good news. Not the same as positive: a
 *                   rise in the return rate and a rise in revenue are both increases
 *                   and only one of them is an improvement, so the direction is
 *                   decided by the metric that produced the delta rather than by the
 *                   sign
 */
public record DeltaDto(
        BigDecimal current,
        BigDecimal previous,
        Double changePct,
        boolean improving
) {

    /** A delta where a larger value is better: revenue, profit, conversions. */
    public static DeltaDto higherIsBetter(BigDecimal current, BigDecimal previous) {
        Double pct = percentChange(current, previous);
        boolean better = pct == null ? signum(current) > 0 : pct >= 0;
        return new DeltaDto(nz(current), nz(previous), pct, better);
    }

    /** A delta where a smaller value is better: return rate, error count, latency. */
    public static DeltaDto lowerIsBetter(BigDecimal current, BigDecimal previous) {
        Double pct = percentChange(current, previous);
        boolean better = pct == null ? signum(current) <= 0 : pct <= 0;
        return new DeltaDto(nz(current), nz(previous), pct, better);
    }

    /**
     * Signed percentage change, or {@code null} when the base is zero or absent.
     *
     * <p>Rounded to one decimal at the end rather than during, so a chain of small
     * values does not accumulate rounding into the reported figure.</p>
     */
    public static Double percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal cur = nz(current);
        return cur.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int signum(BigDecimal v) {
        return nz(v).signum();
    }
}
