package com.electroshop.service;

import com.electroshop.dto.DeltaDto;
import com.electroshop.dto.OrderEfficiencyDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.model.Order;
import com.electroshop.model.OrderStatus;
import com.electroshop.model.OrderStatusEvent;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.OrderStatusEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How well orders are being processed.
 *
 * <p>Task 15. Four KPIs — average processing time, average delivery time, return rate
 * and cancellation rate — with the series behind them and a per-order detail table.</p>
 *
 * <h2>Two of the four KPIs did not exist before this</h2>
 *
 * <p>{@code Order} keeps a creation timestamp and a last-touched timestamp. Neither
 * says when an order went from placed to paid, or from shipped to delivered, and those
 * two gaps are exactly what the duration KPIs measure. The transitions are now recorded
 * as they happen, in {@link OrderStatusEvent}.</p>
 *
 * <p>Which has an unavoidable consequence, and the panel states it rather than hiding
 * it: orders placed before the recording started have no history and are excluded from
 * the duration averages. {@code ordersWithHistory} against {@code ordersInWindow} shows
 * how much of the window is actually measured. An average over four of nine hundred
 * orders is not wrong, but it is not the business either, and a reader who cannot tell
 * the difference will act on it as though it were.</p>
 *
 * <h2>The two rates cover everything, immediately</h2>
 *
 * <p>Cancellation and return rates are counted from the order's own final status, which
 * has always been stored. They are complete from the first day and do not depend on the
 * new data at all. Only the durations wait for history to accumulate.</p>
 *
 * <h2>Rates are counted against orders placed, not orders resolved</h2>
 *
 * <p>A return rate that divides returns by "orders that reached a terminal state" moves
 * whenever the pending queue drains, which makes it look like performance changed when
 * only the backlog did. Dividing by orders placed in the window gives a figure that
 * moves only when the underlying rate does.</p>
 */
@Service
public class OrderEfficiencyService {

    /** How many orders the detail table returns. */
    private static final int DETAIL_LIMIT = 50;

    /** How many return reasons the breakdown lists. */
    private static final int REASON_LIMIT = 10;

    /** A stage above this multiple of the window average is flagged as serious. */
    private static final double DANGER_MULTIPLE = 3.0;

    /** A stage above this multiple is flagged as worth a look. */
    private static final double WARNING_MULTIPLE = 2.0;

    private final OrderRepository orderRepository;
    private final OrderStatusEventRepository eventRepository;

    public OrderEfficiencyService(OrderRepository orderRepository,
                                  OrderStatusEventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * The whole panel for a window.
     *
     * <p><b>Read-only transactional on purpose.</b> The method issues around a dozen
     * queries and presents their results as one consistent picture; without a
     * surrounding transaction each repository call runs in its own session, so the
     * counts, the series and the detail table can each observe a different state of the
     * order table. The flag also keeps the persistence context from taking dirty-check
     * snapshots of every order it loads, which for a fifty-row detail table is pure
     * waste, and it pairs with the fetch join on {@code findPlacedBetween} so the
     * customer e-mail printed in that table is read from an initialised association
     * rather than a detached proxy.</p>
     */
    @Transactional(readOnly = true)
    public OrderEfficiencyDto efficiency(MetricRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        Durations current = durations(from, to);
        Durations previous = durations(range.previousFrom(now), range.previousTo(now));

        long placed = orderRepository.countPlacedBetween(from, to);
        long placedPrev = orderRepository.countPlacedBetween(
                range.previousFrom(now), range.previousTo(now));

        long returned = orderRepository.countByStatusBetween(OrderStatus.RETURNED, from, to);
        long cancelled = orderRepository.countByStatusBetween(OrderStatus.CANCELLED, from, to);
        long returnedPrev = orderRepository.countByStatusBetween(
                OrderStatus.RETURNED, range.previousFrom(now), range.previousTo(now));
        long cancelledPrev = orderRepository.countByStatusBetween(
                OrderStatus.CANCELLED, range.previousFrom(now), range.previousTo(now));

        Double returnRate = rate(returned, placed);
        Double cancelRate = rate(cancelled, placed);
        Double returnRatePrev = rate(returnedPrev, placedPrev);
        Double cancelRatePrev = rate(cancelledPrev, placedPrev);

        return new OrderEfficiencyDto(
                current.avgProcessing(),
                current.avgDelivery(),
                returnRate,
                cancelRate,
                // Faster is better, so these two deltas invert the usual sense of a
                // positive change. A dashboard that shows a green arrow because the
                // delivery time went up is worse than one that shows nothing.
                DeltaDto.lowerIsBetter(hours(current.avgProcessing()), hours(previous.avgProcessing())),
                DeltaDto.lowerIsBetter(hours(current.avgDelivery()), hours(previous.avgDelivery())),
                DeltaDto.lowerIsBetter(pct(returnRate), pct(returnRatePrev)),
                DeltaDto.lowerIsBetter(pct(cancelRate), pct(cancelRatePrev)),
                bucketSeries(range, now, current.processingByOrder(), current.paidAt()),
                bucketSeries(range, now, current.deliveryByOrder(), current.deliveredAt()),
                volumeSeries(range, now, from, to),
                statusMix(from, to, placed),
                returnReasons(from, to, returned),
                details(from, to, current),
                placed,
                current.ordersWithHistory(),
                range.info(now, eventRepository.earliestEvent())
        );
    }

    /**
     * Reads the transition history for a window and pairs the timestamps into durations.
     *
     * <p>Three queries rather than one per order. The first moment each order reached
     * each of the three relevant statuses is enough to compute both gaps, and asking
     * for it per order would issue one round trip per row.</p>
     */
    private Durations durations(LocalDateTime from, LocalDateTime to) {
        // The window is widened backwards for the "reached" queries because a delivery
        // that happened today may belong to an order placed six weeks ago. Anchoring
        // the events to the order's placement date instead would report today's
        // deliveries under a month the operator is no longer looking at.
        Map<Long, LocalDateTime> placedAt = firstEntry(OrderStatus.PENDING, from, to);
        Map<Long, LocalDateTime> paidAt = firstEntry(OrderStatus.PAID, from, to);
        Map<Long, LocalDateTime> shippedAt = firstEntry(OrderStatus.SHIPPED, from, to);
        Map<Long, LocalDateTime> deliveredAt = firstEntry(OrderStatus.DELIVERED, from, to);

        Map<Long, Double> processing = new LinkedHashMap<>();
        Map<Long, Double> delivery = new LinkedHashMap<>();

        for (Map.Entry<Long, LocalDateTime> entry : paidAt.entrySet()) {
            LocalDateTime start = placedAt.get(entry.getKey());
            if (start != null && !entry.getValue().isBefore(start)) {
                processing.put(entry.getKey(), hoursBetween(start, entry.getValue()));
            }
        }
        for (Map.Entry<Long, LocalDateTime> entry : deliveredAt.entrySet()) {
            LocalDateTime start = shippedAt.get(entry.getKey());
            if (start != null && !entry.getValue().isBefore(start)) {
                delivery.put(entry.getKey(), hoursBetween(start, entry.getValue()));
            }
        }

        java.util.Set<Long> withHistory = new java.util.HashSet<>(placedAt.keySet());
        withHistory.addAll(paidAt.keySet());
        withHistory.addAll(shippedAt.keySet());
        withHistory.addAll(deliveredAt.keySet());

        return new Durations(
                average(processing.values()),
                average(delivery.values()),
                processing,
                delivery,
                paidAt,
                shippedAt,
                deliveredAt,
                withHistory.size()
        );
    }

    private Map<Long, LocalDateTime> firstEntry(OrderStatus status,
                                                LocalDateTime from,
                                                LocalDateTime to) {
        Map<Long, LocalDateTime> out = new HashMap<>();
        for (Object[] row : eventRepository.firstEntryPerOrder(status, from, to)) {
            out.put(((Number) row[0]).longValue(), (LocalDateTime) row[1]);
        }
        return out;
    }

    /**
     * Turns per-order durations into a per-bucket average series.
     *
     * <p>An order is bucketed by when the stage <em>completed</em>, not by when the
     * order was placed. "Average delivery time in July" means deliveries that finished
     * in July; filing them under the month the order arrived would credit a slow August
     * delivery to July's performance and make a bad month look like a good one.</p>
     *
     * <p>Buckets with no measured order carry a null value rather than a zero. Zero
     * hours means an order was processed instantly; no data means nothing was measured,
     * and a chart that draws the second as the first invents a day of perfect
     * performance out of a day of silence.</p>
     *
     * @param byOrder    duration per order, in hours
     * @param completedAt when each order's stage finished, keyed the same way
     */
    private List<SeriesPointDto> bucketSeries(MetricRange range,
                                              LocalDateTime now,
                                              Map<Long, Double> byOrder,
                                              Map<Long, LocalDateTime> completedAt) {
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (String label : range.bucketLabels(now)) {
            grouped.put(label, new ArrayList<>());
        }

        for (Map.Entry<Long, Double> entry : byOrder.entrySet()) {
            LocalDateTime moment = completedAt.get(entry.getKey());
            if (moment == null) {
                continue;
            }
            List<Double> bucket = grouped.get(range.labelFor(moment));
            if (bucket != null) {
                bucket.add(entry.getValue());
            }
        }

        List<SeriesPointDto> out = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            List<Double> values = entry.getValue();
            Double avg = average(values);
            out.add(new SeriesPointDto(
                    entry.getKey(),
                    avg == null ? null : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP),
                    null,
                    (long) values.size()));
        }
        return out;
    }

    /** Orders placed per bucket. */
    private List<SeriesPointDto> volumeSeries(MetricRange range,
                                              LocalDateTime now,
                                              LocalDateTime from,
                                              LocalDateTime to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String label : range.bucketLabels(now)) {
            counts.put(label, 0L);
        }

        boolean monthly = range.bucket() == MetricRange.Bucket.MONTH;
        List<Object[]> rows = monthly
                ? orderRepository.revenueByMonthBetween(from, to)
                : orderRepository.revenueByDayBetween(from, to);

        for (Object[] row : rows) {
            String label = monthly
                    ? String.format("%04d-%02d", num(row[0]), num(row[1]))
                    : String.format("%04d-%02d-%02d", num(row[0]), num(row[1]), num(row[2]));
            long orders = monthly ? num(row[3]) : num(row[4]);
            if (counts.containsKey(label)) {
                counts.put(label, orders);
            }
        }

        List<SeriesPointDto> out = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            out.add(SeriesPointDto.of(entry.getKey(),
                    BigDecimal.valueOf(entry.getValue()), entry.getValue()));
        }
        return out;
    }

    /** How orders in the window ended up. */
    private List<OrderEfficiencyDto.StatusCount> statusMix(LocalDateTime from,
                                                           LocalDateTime to,
                                                           long placed) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orderRepository.countByStatusInWindow(from, to)) {
            counts.put((OrderStatus) row[0], ((Number) row[1]).longValue());
        }

        List<OrderEfficiencyDto.StatusCount> out = new ArrayList<>();
        // Every status is listed, including the ones with no orders, so the chart's
        // categories stay stable as the window changes. A legend that gains and loses
        // entries as the operator switches ranges is a legend nobody can compare
        // against itself.
        for (OrderStatus status : OrderStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            out.add(new OrderEfficiencyDto.StatusCount(status.name(), count, rate(count, placed)));
        }
        return out;
    }

    /** Why returns happened, most frequent first. */
    private List<OrderEfficiencyDto.ReasonCount> returnReasons(LocalDateTime from,
                                                               LocalDateTime to,
                                                               long totalReturns) {
        List<Object[]> rows = eventRepository.reasonBreakdown(OrderStatus.RETURNED, from, to);

        long withReason = 0;
        for (Object[] row : rows) {
            withReason += ((Number) row[1]).longValue();
        }

        List<OrderEfficiencyDto.ReasonCount> out = new ArrayList<>();
        for (int i = 0; i < Math.min(REASON_LIMIT, rows.size()); i++) {
            Object[] row = rows.get(i);
            long count = ((Number) row[1]).longValue();
            // The share is of returns that recorded a reason, not of all returns.
            // Dividing by all of them would make the percentages sum to less than 100
            // for a reason the reader cannot see, and invite the conclusion that some
            // returns had no cause.
            out.add(new OrderEfficiencyDto.ReasonCount(
                    String.valueOf(row[0]), count, rate(count, withReason)));
        }
        return out;
    }

    /**
     * The slowest orders in the window.
     *
     * <p>Slowest rather than newest. A table of the fifty most recent orders is a list
     * of what happened; a table of the fifty slowest is a list of what to look at.</p>
     */
    private List<OrderEfficiencyDto.OrderDetail> details(LocalDateTime from,
                                                         LocalDateTime to,
                                                         Durations d) {
        List<Order> orders = orderRepository.findPlacedBetween(
                from, to, PageRequest.of(0, DETAIL_LIMIT * 4));

        List<OrderEfficiencyDto.OrderDetail> out = new ArrayList<>(orders.size());
        for (Order o : orders) {
            Double processing = d.processingByOrder().get(o.getId());
            Double delivery = d.deliveryByOrder().get(o.getId());

            out.add(new OrderEfficiencyDto.OrderDetail(
                    o.getId(),
                    o.getUser() == null ? "—" : o.getUser().getEmail(),
                    o.getStatus() == null ? "—" : o.getStatus().name(),
                    MetricsService.scale(o.getTotalAmount()),
                    o.getCreatedAt(),
                    d.paidAt().get(o.getId()),
                    d.shippedAt().get(o.getId()),
                    d.deliveredAt().get(o.getId()),
                    processing,
                    delivery,
                    flagFor(processing, delivery, d)
            ));
        }

        // Worst first, unmeasured last. An order with no measurement is not fast; it is
        // unknown, and sorting it to the top as though it took zero hours would fill
        // the table with rows that say nothing.
        out.sort((a, b) -> Double.compare(worst(b), worst(a)));

        return out.size() > DETAIL_LIMIT ? new ArrayList<>(out.subList(0, DETAIL_LIMIT)) : out;
    }

    private static double worst(OrderEfficiencyDto.OrderDetail d) {
        double p = d.processingHours() == null ? -1 : d.processingHours();
        double v = d.deliveryHours() == null ? -1 : d.deliveryHours();
        return Math.max(p, v);
    }

    private static String flagFor(Double processing, Double delivery, Durations d) {
        double worst = 0;
        if (processing != null && d.avgProcessing() != null && d.avgProcessing() > 0) {
            worst = Math.max(worst, processing / d.avgProcessing());
        }
        if (delivery != null && d.avgDelivery() != null && d.avgDelivery() > 0) {
            worst = Math.max(worst, delivery / d.avgDelivery());
        }
        if (worst >= DANGER_MULTIPLE) {
            return "DANGER";
        }
        if (worst >= WARNING_MULTIPLE) {
            return "WARNING";
        }
        return "INFO";
    }

    private static double hoursBetween(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).toMinutes() / 60.0;
    }

    /** Mean of a collection, or null when it is empty. */
    private static Double average(java.util.Collection<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double v : values) {
            sum += v;
        }
        return BigDecimal.valueOf(sum / values.size())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** A percentage, or null when the denominator is zero. */
    private static Double rate(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static BigDecimal hours(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** Everything the duration pass produced, so the callers share one read. */
    private record Durations(
            Double avgProcessing,
            Double avgDelivery,
            Map<Long, Double> processingByOrder,
            Map<Long, Double> deliveryByOrder,
            Map<Long, LocalDateTime> paidAt,
            Map<Long, LocalDateTime> shippedAt,
            Map<Long, LocalDateTime> deliveredAt,
            long ordersWithHistory
    ) {}
}
