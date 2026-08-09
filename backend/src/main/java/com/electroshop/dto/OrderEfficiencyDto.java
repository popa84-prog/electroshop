package com.electroshop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * How well orders are being processed.
 *
 * <p>Answers {@code GET /api/orders/efficiency}. Four KPIs — average processing time,
 * average delivery time, return rate and cancellation rate — plus the series behind
 * them and a per-order detail table.</p>
 *
 * <p><b>The two durations are measured, not inferred.</b> Processing time is the gap
 * between an order arriving and being paid; delivery time is the gap between shipping
 * and delivery. Neither is derivable from {@code Order}, which keeps only a creation
 * and a last-touched timestamp, so both come from {@code OrderStatusEvent} rows written
 * as each transition happens.</p>
 *
 * <p>That has a consequence the panel states plainly: orders placed before this
 * measurement existed have no transition history and are excluded from the duration
 * KPIs. {@code ordersWithHistory} against {@code ordersInWindow} shows how much of the
 * window is actually being measured, and {@code range.dataAvailableFrom} says when
 * measurement began. An average computed over four of nine hundred orders is not
 * wrong, but it is not the business either, and the reader has to be able to tell.</p>
 *
 * <p><b>Rates use every order in the window.</b> Cancellation and return rates are
 * counted against all orders placed in the window, whether or not they have transition
 * history, because the final status is stored on the order itself. Only the durations
 * depend on the new data.</p>
 *
 * @param avgProcessingHours average hours from order placed to paid, null when nothing
 *                           in the window has that pair of events
 * @param avgDeliveryHours   average hours from shipped to delivered, null likewise
 * @param returnRatePct      returned orders as a percentage of orders in the window
 * @param cancelRatePct      cancelled orders as a percentage of orders in the window
 * @param processingDelta    processing time against the preceding window; lower is better
 * @param deliveryDelta      delivery time against the preceding window; lower is better
 * @param returnRateDelta    return rate against the preceding window; lower is better
 * @param cancelRateDelta    cancellation rate against the preceding window; lower is better
 * @param processingSeries   average processing hours per bucket, oldest first
 * @param deliverySeries     average delivery hours per bucket, oldest first
 * @param volumeSeries       orders placed per bucket, oldest first
 * @param statusMix          how many orders ended in each status inside the window
 * @param returnReasons      why returns happened, most frequent first
 * @param slowest            the orders that took longest, for the detail table
 * @param ordersInWindow     orders placed inside the window
 * @param ordersWithHistory  how many of those have transition events recorded
 * @param range              the resolved window
 */
public record OrderEfficiencyDto(
        Double avgProcessingHours,
        Double avgDeliveryHours,
        Double returnRatePct,
        Double cancelRatePct,
        DeltaDto processingDelta,
        DeltaDto deliveryDelta,
        DeltaDto returnRateDelta,
        DeltaDto cancelRateDelta,
        List<SeriesPointDto> processingSeries,
        List<SeriesPointDto> deliverySeries,
        List<SeriesPointDto> volumeSeries,
        List<StatusCount> statusMix,
        List<ReasonCount> returnReasons,
        List<OrderDetail> slowest,
        long ordersInWindow,
        long ordersWithHistory,
        RangeInfoDto range
) {

    /**
     * How many orders ended in one status.
     *
     * @param status   the order status
     * @param count    how many orders
     * @param sharePct that status's share of the window
     */
    public record StatusCount(String status, long count, Double sharePct) {}

    /**
     * Why a set of returns happened.
     *
     * @param reason   the reason recorded on the transition
     * @param count    how many returns gave it
     * @param sharePct its share of all returns with a recorded reason
     */
    public record ReasonCount(String reason, long count, Double sharePct) {}

    /**
     * One order in the detail table.
     *
     * @param orderId          database id, so the row links to the order page
     * @param customerEmail    who placed it
     * @param status           where it ended up
     * @param totalAmount      order value
     * @param placedAt         when it arrived
     * @param paidAt           when it was paid, null when it never was
     * @param shippedAt        when it shipped, null when it never did
     * @param deliveredAt      when it arrived at the customer, null when it did not
     * @param processingHours  hours from placed to paid, null when unmeasurable
     * @param deliveryHours    hours from shipped to delivered, null when unmeasurable
     * @param flag             {@code DANGER} when a stage took more than three times the
     *                         window average, {@code WARNING} above twice, {@code INFO}
     *                         otherwise — computed on the server so the table's colours
     *                         and its sort order come from the same rule
     */
    public record OrderDetail(
            Long orderId,
            String customerEmail,
            String status,
            BigDecimal totalAmount,
            LocalDateTime placedAt,
            LocalDateTime paidAt,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            Double processingHours,
            Double deliveryHours,
            String flag
    ) {}
}
