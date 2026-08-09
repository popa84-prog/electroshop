package com.electroshop.dto;

import java.time.LocalDateTime;

/**
 * The time window a report actually used, echoed back with the report.
 *
 * <p>Every analytics endpoint takes a range parameter such as {@code 7d} or {@code 12m}
 * and resolves it against the server clock. The frontend must not re-derive those
 * boundaries from its own clock: a browser in another timezone, or one whose clock has
 * drifted, would label the chart with days that do not match the data plotted on it.
 * The backend states the window it used and the chart labels itself from that.</p>
 *
 * <p>{@code dataAvailableFrom} exists for the panels built on newly collected data —
 * order status history, offer events, system logs. When a report covers twelve months
 * but collection began three weeks ago, the first eleven months are not a period of
 * zero activity, they are a period with no measurement. The panel says so instead of
 * plotting a flat line at zero.</p>
 *
 * @param code              the range as requested: {@code 24h}, {@code 7d}, {@code 30d},
 *                          {@code 3m}, {@code 6m}, {@code 12m}
 * @param from              inclusive start of the window, server time
 * @param to                exclusive end of the window, server time
 * @param bucket            how the series is grouped: {@code HOUR}, {@code DAY} or
 *                          {@code MONTH}
 * @param previousFrom      inclusive start of the immediately preceding window of equal
 *                          length, which is what every {@link DeltaDto} compares against
 * @param previousTo        exclusive end of that preceding window
 * @param dataAvailableFrom the earliest moment for which this report has any data at
 *                          all, or {@code null} when the underlying data has always
 *                          existed
 */
public record RangeInfoDto(
        String code,
        LocalDateTime from,
        LocalDateTime to,
        String bucket,
        LocalDateTime previousFrom,
        LocalDateTime previousTo,
        LocalDateTime dataAvailableFrom
) {
}
