package com.electroshop.dto;

import java.math.BigDecimal;

/**
 * One point on a time series.
 *
 * <p>Every chart in the new dashboard — revenue, profit, conversions, error counts,
 * order volume — plots the same shape: a label on the x axis and one or two values on
 * the y axis. Giving each panel its own point type would produce a dozen near-identical
 * records and a dozen near-identical frontend adapters, so they all speak this one.</p>
 *
 * @param label     the x-axis value, already formatted by the backend
 *                  ({@code 2026-08-08} for a day, {@code 2026-08} for a month,
 *                  {@code 14:00} for an hour)
 * @param value     the primary measure
 * @param secondary an optional second measure drawn on the same axis — profit beside
 *                  revenue, clicks beside impressions. {@code null} when the chart has
 *                  a single series
 * @param count     an optional integer companion, such as how many orders produced the
 *                  value. Charts show it in the tooltip; it is never plotted against a
 *                  second y-axis, because a dual-axis chart makes two unrelated scales
 *                  look comparable
 */
public record SeriesPointDto(
        String label,
        BigDecimal value,
        BigDecimal secondary,
        Long count
) {

    /** A point with a single measure. */
    public static SeriesPointDto of(String label, BigDecimal value) {
        return new SeriesPointDto(label, value, null, null);
    }

    /** A point with a measure and its companion count. */
    public static SeriesPointDto of(String label, BigDecimal value, Long count) {
        return new SeriesPointDto(label, value, null, count);
    }

    /** A point with two measures drawn together. */
    public static SeriesPointDto of(String label, BigDecimal value, BigDecimal secondary, Long count) {
        return new SeriesPointDto(label, value, secondary, count);
    }
}
