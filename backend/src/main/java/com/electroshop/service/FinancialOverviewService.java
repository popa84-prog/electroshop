package com.electroshop.service;

import com.electroshop.dto.DeltaDto;
import com.electroshop.dto.FinancialOverviewDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The financial picture over three, six or twelve months.
 *
 * <p>Task 14. Four charts: monthly revenue as a line, monthly profit as an area,
 * monthly cost of goods sold as bars, and a twelve-month profit trajectory.</p>
 *
 * <h2>Cost of goods sold, not stock value</h2>
 *
 * <p>The requirement names a "total stock cost" chart. Plotting the current inventory
 * value month by month would draw the same number repeated across the axis, because
 * stock value is a snapshot of today and has no history — the database records what
 * stock is, never what it was. What does have a month-by-month history, and what a
 * financial panel is actually asking for, is the cost of the goods that were sold in
 * each month. That is summed from {@code OrderItem.costPrice}, it pairs with revenue to
 * give profit, and unlike a repeated snapshot it answers a question.</p>
 *
 * <h2>Every month is present, including the empty ones</h2>
 *
 * <p>The series are built against {@link MetricRange#bucketLabels(LocalDateTime)} and
 * filled with zeroes where the database returned nothing. A chart that omits quiet
 * months compresses a bad summer into a short gap and turns a decline into a plateau —
 * the single most misleading thing a revenue chart can do.</p>
 *
 * <h2>The twelve-month trajectory is independent of the selected range</h2>
 *
 * <p>An operator who narrows to three months is asking a question about this quarter,
 * not asking to forget the year. The long view stays available beside the short one, so
 * a strong quarter inside a declining year cannot read as simple good news.</p>
 */
@Service
public class FinancialOverviewService {

    /** How many months the independent trajectory line always covers. */
    private static final int TREND_MONTHS = 12;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public FinancialOverviewService(OrderRepository orderRepository,
                                    OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * The full overview for a window.
     *
     * @param range one of {@code 3m}, {@code 6m} or {@code 12m}; other codes are
     *              accepted and simply produce their own window
     */
    public FinancialOverviewDto overview(MetricRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        List<String> labels = range.bucketLabels(now);
        Map<String, Totals> byBucket = readTotals(range, from, to);

        List<SeriesPointDto> revenue = new ArrayList<>(labels.size());
        List<SeriesPointDto> profit = new ArrayList<>(labels.size());
        List<SeriesPointDto> cogs = new ArrayList<>(labels.size());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;

        FinancialOverviewDto.MonthSummary best = null;
        FinancialOverviewDto.MonthSummary worst = null;
        BigDecimal bestProfit = null;
        BigDecimal worstProfit = null;

        for (String label : labels) {
            Totals t = byBucket.getOrDefault(label, Totals.EMPTY);

            revenue.add(SeriesPointDto.of(label, MetricsService.scale(t.revenue()), t.orders()));
            profit.add(SeriesPointDto.of(label, MetricsService.scale(t.profit()), t.orders()));
            cogs.add(SeriesPointDto.of(label, MetricsService.scale(t.cogs()), t.orders()));

            totalRevenue = totalRevenue.add(t.revenue());
            totalProfit = totalProfit.add(t.profit());
            totalCogs = totalCogs.add(t.cogs());

            // Empty buckets are excluded from the best/worst comparison. A month with
            // no trading is not the worst month of the year in any sense an operator
            // means; it is a month that has not happened yet, or one the shop was
            // closed. Including it would make "worst month" report the future.
            if (t.orders() > 0) {
                if (bestProfit == null || t.profit().compareTo(bestProfit) > 0) {
                    bestProfit = t.profit();
                    best = summary(label, t);
                }
                if (worstProfit == null || t.profit().compareTo(worstProfit) < 0) {
                    worstProfit = t.profit();
                    worst = summary(label, t);
                }
            }
        }

        // The comparison window is the immediately preceding stretch of equal length,
        // so the delta compares like with like.
        LocalDateTime prevFrom = range.previousFrom(now);
        LocalDateTime prevTo = range.previousTo(now);
        Totals previous = windowTotals(prevFrom, prevTo);

        Double marginPct = totalRevenue.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalProfit.multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                        .doubleValue();

        return new FinancialOverviewDto(
                revenue,
                profit,
                cogs,
                twelveMonthTrend(now),
                MetricsService.scale(totalRevenue),
                MetricsService.scale(totalProfit),
                MetricsService.scale(totalCogs),
                marginPct,
                DeltaDto.higherIsBetter(MetricsService.scale(totalRevenue),
                        MetricsService.scale(previous.revenue())),
                DeltaDto.higherIsBetter(MetricsService.scale(totalProfit),
                        MetricsService.scale(previous.profit())),
                best,
                worst,
                MetricsService.CURRENCY,
                range.info(now, orderRepository.earliestOrder()),
                orderRepository.countPlacedBetween(from, to),
                orderItemRepository.countWithoutCostInWindow(from, to)
        );
    }

    /**
     * Reads revenue, profit and cost per bucket, keyed by the bucket's label.
     *
     * <p>Two queries, joined on the label. The item query gives revenue, profit and
     * cost but counts lines rather than orders; the order query gives the order count.
     * Deriving the order count from the item rows would count an order once per line
     * and report a five-line order as five orders.</p>
     */
    private Map<String, Totals> readTotals(MetricRange range, LocalDateTime from, LocalDateTime to) {
        Map<String, Totals> out = new LinkedHashMap<>();

        boolean monthly = range.bucket() == MetricRange.Bucket.MONTH;

        List<Object[]> itemRows = monthly
                ? orderItemRepository.monthlyTotals(from, to)
                : orderItemRepository.dailyTotals(from, to);

        for (Object[] row : itemRows) {
            String label = monthly
                    ? monthLabel(num(row[0]), num(row[1]))
                    : dayLabel(num(row[0]), num(row[1]), num(row[2]));
            int offset = monthly ? 2 : 3;
            out.put(label, new Totals(
                    ProfitAnalyticsService.dec(row[offset]),
                    ProfitAnalyticsService.dec(row[offset + 1]),
                    ProfitAnalyticsService.dec(row[offset + 2]),
                    0L
            ));
        }

        List<Object[]> orderRows = monthly
                ? orderRepository.revenueByMonthBetween(from, to)
                : orderRepository.revenueByDayBetween(from, to);

        for (Object[] row : orderRows) {
            String label = monthly
                    ? monthLabel(num(row[0]), num(row[1]))
                    : dayLabel(num(row[0]), num(row[1]), num(row[2]));
            long orders = monthly ? num(row[3]) : num(row[4]);
            Totals existing = out.get(label);
            if (existing == null) {
                // Orders exist in this bucket but no costed line does — every line was
                // missing a cost price. Revenue is real and is reported; profit stays
                // zero rather than being invented from an unknown cost.
                BigDecimal rev = monthly
                        ? ProfitAnalyticsService.dec(row[2])
                        : ProfitAnalyticsService.dec(row[3]);
                out.put(label, new Totals(rev, BigDecimal.ZERO, BigDecimal.ZERO, orders));
            } else {
                out.put(label, existing.withOrders(orders));
            }
        }

        return out;
    }

    /** Totals across an arbitrary window, for the period-over-period comparison. */
    private Totals windowTotals(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = orderItemRepository.totalsInWindow(from, to);
        if (rows.isEmpty() || rows.get(0) == null) {
            return Totals.EMPTY;
        }
        Object[] r = rows.get(0);
        BigDecimal revenue = ProfitAnalyticsService.dec(r[0]);
        BigDecimal profit = ProfitAnalyticsService.dec(r[1]);
        return new Totals(revenue, profit, revenue.subtract(profit), 0L);
    }

    /** The twelve-month profit line, always the same window regardless of the selection. */
    private List<SeriesPointDto> twelveMonthTrend(LocalDateTime now) {
        LocalDateTime from = now.minusMonths(TREND_MONTHS);
        List<Object[]> rows = orderItemRepository.monthlyTotals(from, now);

        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : rows) {
            byMonth.put(monthLabel(num(row[0]), num(row[1])), ProfitAnalyticsService.dec(row[3]));
        }

        List<SeriesPointDto> out = new ArrayList<>(TREND_MONTHS + 1);
        LocalDate cursor = now.toLocalDate().withDayOfMonth(1).minusMonths(TREND_MONTHS);
        LocalDate end = now.toLocalDate().withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            String label = monthLabel(cursor.getYear(), cursor.getMonthValue());
            out.add(SeriesPointDto.of(label,
                    MetricsService.scale(byMonth.getOrDefault(label, BigDecimal.ZERO))));
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    private FinancialOverviewDto.MonthSummary summary(String label, Totals t) {
        Double margin = t.revenue().compareTo(BigDecimal.ZERO) == 0
                ? null
                : t.profit().multiply(BigDecimal.valueOf(100))
                        .divide(t.revenue(), 2, RoundingMode.HALF_UP)
                        .doubleValue();
        return new FinancialOverviewDto.MonthSummary(
                label,
                MetricsService.scale(t.revenue()),
                MetricsService.scale(t.profit()),
                margin,
                t.orders()
        );
    }

    private static String monthLabel(long year, long month) {
        return String.format("%04d-%02d", year, month);
    }

    private static String dayLabel(long year, long month, long day) {
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    private static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** Revenue, profit, cost of goods sold and order count for one bucket. */
    private record Totals(BigDecimal revenue, BigDecimal profit, BigDecimal cogs, long orders) {

        static final Totals EMPTY =
                new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);

        Totals withOrders(long n) {
            return new Totals(revenue, profit, cogs, n);
        }
    }
}
