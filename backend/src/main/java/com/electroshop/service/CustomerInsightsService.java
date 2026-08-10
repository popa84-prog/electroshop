package com.electroshop.service;

import com.electroshop.dto.CustomerInsightsDto;
import com.electroshop.dto.DeltaDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.model.User;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is buying, how often, and how much.
 *
 * <p>Task 16.</p>
 *
 * <h2>"New" is judged against all of history, not against the window</h2>
 *
 * <p>This is the decision the whole panel turns on. A customer who bought last year and
 * bought again this month is a returning customer, even though a seven-day window is
 * seeing them for the first time. Deciding novelty from inside the window would relabel
 * the entire loyal base as new every time somebody narrows the range — which is exactly
 * backwards, because it would report the healthiest possible acquisition figures for a
 * business acquiring nobody. Each customer's first-ever order date is therefore looked
 * up across the complete order history and compared against the window boundary.</p>
 *
 * <h2>Segments are computed from what the data supports</h2>
 *
 * <p>Two facts are available per customer: how many times they ordered and how much
 * they spent. The four segments come from those two and nothing else, so every customer
 * lands in exactly one and the boundaries are returned in the response rather than
 * living as constants on both sides of the wire. A segment defined by something the
 * system does not record would be a segment nobody could reproduce.</p>
 *
 * <h2>Guest orders are excluded</h2>
 *
 * <p>An order with no account cannot be counted as new or returning, cannot belong to a
 * segment, and cannot appear in a frequency distribution. Including it under a
 * placeholder identity would merge every anonymous buyer into one enormous fictional
 * customer.</p>
 */
@Service
public class CustomerInsightsService {

    /** How many customers the top table lists. */
    private static final int TOP_LIMIT = 20;

    /** A customer at or above this many orders and this much spend is VIP. */
    private static final int VIP_MIN_ORDERS = 5;
    private static final BigDecimal VIP_MIN_REVENUE = BigDecimal.valueOf(5000);

    /** A customer at or above this many orders is loyal. */
    private static final int LOYAL_MIN_ORDERS = 3;

    /** A customer at or above this many orders is occasional; below it, one-time. */
    private static final int OCCASIONAL_MIN_ORDERS = 2;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public CustomerInsightsService(OrderRepository orderRepository,
                                   UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    /** The whole panel for a window, optionally narrowed to one customer type. */
    public CustomerInsightsDto insights(MetricRange range, String type) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        List<Customer> current = readCustomers(from, to);
        List<Customer> previous = readCustomers(range.previousFrom(now), range.previousTo(now));

        // The type filter is applied after novelty is decided, never before. Filtering
        // to "returning" first and then asking who is new would produce zero every time.
        List<Customer> filtered = applyTypeFilter(current, type);

        long newCustomers = filtered.stream().filter(Customer::isNew).count();
        long returning = filtered.size() - newCustomers;

        BigDecimal revenue = BigDecimal.ZERO;
        long orders = 0;
        for (Customer c : filtered) {
            revenue = revenue.add(c.revenue());
            orders += c.orders();
        }

        BigDecimal avgBasket = orders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);

        BigDecimal prevRevenue = BigDecimal.ZERO;
        long prevOrders = 0;
        for (Customer c : previous) {
            prevRevenue = prevRevenue.add(c.revenue());
            prevOrders += c.orders();
        }
        BigDecimal prevAvgBasket = prevOrders == 0
                ? BigDecimal.ZERO
                : prevRevenue.divide(BigDecimal.valueOf(prevOrders), 2, RoundingMode.HALF_UP);

        Double repeatRate = filtered.isEmpty()
                ? null
                : percent(returning, filtered.size());
        long prevReturning = previous.stream().filter(c -> !c.isNew()).count();
        Double prevRepeatRate = previous.isEmpty()
                ? null
                : percent(prevReturning, previous.size());

        return new CustomerInsightsDto(
                newVsReturningSeries(range, now, filtered),
                frequencyBuckets(filtered, revenue),
                basketSeries(range, now, from, to),
                segments(filtered, revenue),
                topCustomers(filtered),
                newCustomers,
                returning,
                filtered.size(),
                avgBasket,
                filtered.isEmpty() ? null : round2((double) orders / filtered.size()),
                repeatRate,
                DeltaDto.higherIsBetter(avgBasket, prevAvgBasket),
                DeltaDto.higherIsBetter(pct(repeatRate), pct(prevRepeatRate)),
                MetricsService.CURRENCY,
                range.info(now, orderRepository.earliestOrder())
        );
    }

    /**
     * Loads every customer who ordered in the window, with novelty already decided.
     *
     * <p>Two queries. The first aggregates orders per customer inside the window; the
     * second looks up those customers' first-ever order across all of history. Doing
     * the second per customer would be one round trip each, and doing it inside the
     * first would need a correlated subquery that no dialect optimises well.</p>
     */
    private List<Customer> readCustomers(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = orderRepository.customerTotalsBetween(from, to);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            ids.add(((Number) row[0]).longValue());
        }

        Map<Long, LocalDateTime> firstEver = new HashMap<>();
        for (Object[] row : orderRepository.firstOrderDates(ids)) {
            firstEver.put(((Number) row[0]).longValue(), (LocalDateTime) row[1]);
        }

        List<Customer> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            LocalDateTime first = firstEver.get(id);
            // New means their very first order falls inside this window. A missing
            // first-order date can only happen if the two queries raced, and treating
            // it as new would be the more flattering guess, so it is treated as
            // returning instead.
            boolean isNew = first != null && !first.isBefore(from);
            out.add(new Customer(
                    id,
                    ((Number) row[1]).longValue(),
                    MetricsService.scale((BigDecimal) row[2]),
                    (LocalDateTime) row[3],
                    (LocalDateTime) row[4],
                    first,
                    isNew
            ));
        }
        return out;
    }

    private List<Customer> applyTypeFilter(List<Customer> all, String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return all;
        }
        if ("NEW".equalsIgnoreCase(type)) {
            return all.stream().filter(Customer::isNew).toList();
        }
        if ("RETURNING".equalsIgnoreCase(type)) {
            return all.stream().filter(c -> !c.isNew()).toList();
        }
        // A segment key. An unrecognised value returns everything rather than nothing:
        // the filter arrives in a query string and a stale bookmark should not produce
        // an empty panel with no explanation.
        List<Customer> matching = all.stream()
                .filter(c -> segmentKey(c).equalsIgnoreCase(type))
                .toList();
        return matching.isEmpty() && !isKnownSegment(type) ? all : matching;
    }

    private static boolean isKnownSegment(String key) {
        return "VIP".equalsIgnoreCase(key) || "LOYAL".equalsIgnoreCase(key)
                || "OCCASIONAL".equalsIgnoreCase(key) || "ONE_TIME".equalsIgnoreCase(key);
    }

    /** New against returning, per bucket. */
    private List<SeriesPointDto> newVsReturningSeries(MetricRange range,
                                                      LocalDateTime now,
                                                      List<Customer> customers) {
        Map<String, long[]> grouped = new LinkedHashMap<>();
        for (String label : range.bucketLabels(now)) {
            grouped.put(label, new long[2]);
        }
        for (Customer c : customers) {
            // Bucketed by the customer's first order inside this window, which is when
            // they became visible to it.
            long[] pair = grouped.get(range.labelFor(c.firstInWindow()));
            if (pair != null) {
                pair[c.isNew() ? 0 : 1]++;
            }
        }

        List<SeriesPointDto> out = new ArrayList<>(grouped.size());
        for (Map.Entry<String, long[]> entry : grouped.entrySet()) {
            long[] pair = entry.getValue();
            out.add(SeriesPointDto.of(
                    entry.getKey(),
                    BigDecimal.valueOf(pair[0]),
                    BigDecimal.valueOf(pair[1]),
                    pair[0] + pair[1]));
        }
        return out;
    }

    /**
     * How many customers placed how many orders.
     *
     * <p>Revenue travels with each bucket, because the distribution alone does not
     * support a decision. A bucket holding 4% of customers and 40% of revenue is the
     * one to protect, and that is invisible in a customer count.</p>
     */
    private List<CustomerInsightsDto.FrequencyBucket> frequencyBuckets(List<Customer> customers,
                                                                      BigDecimal totalRevenue) {
        String[] labels = {"1", "2", "3-5", "6-10", "10+"};
        long[] counts = new long[labels.length];
        BigDecimal[] revenues = new BigDecimal[labels.length];
        java.util.Arrays.fill(revenues, BigDecimal.ZERO);

        for (Customer c : customers) {
            int idx = c.orders() == 1 ? 0
                    : c.orders() == 2 ? 1
                    : c.orders() <= 5 ? 2
                    : c.orders() <= 10 ? 3
                    : 4;
            counts[idx]++;
            revenues[idx] = revenues[idx].add(c.revenue());
        }

        List<CustomerInsightsDto.FrequencyBucket> out = new ArrayList<>(labels.length);
        for (int i = 0; i < labels.length; i++) {
            out.add(new CustomerInsightsDto.FrequencyBucket(
                    labels[i],
                    counts[i],
                    MetricsService.scale(revenues[i]),
                    customers.isEmpty() ? null : percent(counts[i], customers.size())));
        }
        return out;
    }

    /** Average order value per bucket. */
    private List<SeriesPointDto> basketSeries(MetricRange range,
                                              LocalDateTime now,
                                              LocalDateTime from,
                                              LocalDateTime to) {
        Map<String, BigDecimal[]> grouped = new LinkedHashMap<>();
        for (String label : range.bucketLabels(now)) {
            grouped.put(label, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        boolean monthly = range.bucket() == MetricRange.Bucket.MONTH;
        List<Object[]> rows = monthly
                ? orderRepository.revenueByMonthBetween(from, to)
                : orderRepository.revenueByDayBetween(from, to);

        for (Object[] row : rows) {
            String label = monthly
                    ? String.format("%04d-%02d", num(row[0]), num(row[1]))
                    : String.format("%04d-%02d-%02d", num(row[0]), num(row[1]), num(row[2]));
            BigDecimal revenue = monthly
                    ? ProfitAnalyticsService.dec(row[2])
                    : ProfitAnalyticsService.dec(row[3]);
            long orders = monthly ? num(row[3]) : num(row[4]);
            BigDecimal[] slot = grouped.get(label);
            if (slot != null) {
                slot[0] = revenue;
                slot[1] = BigDecimal.valueOf(orders);
            }
        }

        List<SeriesPointDto> out = new ArrayList<>(grouped.size());
        for (Map.Entry<String, BigDecimal[]> entry : grouped.entrySet()) {
            BigDecimal revenue = entry.getValue()[0];
            BigDecimal orders = entry.getValue()[1];
            // A bucket with no orders has no average basket. Zero would draw a crash to
            // the floor on every quiet day and make the line unreadable.
            BigDecimal avg = orders.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : revenue.divide(orders, 2, RoundingMode.HALF_UP);
            out.add(new SeriesPointDto(entry.getKey(), avg, null, orders.longValue()));
        }
        return out;
    }

    /** The four segments, with populations and revenue. */
    private List<CustomerInsightsDto.Segment> segments(List<Customer> customers,
                                                       BigDecimal totalRevenue) {
        Map<String, List<Customer>> grouped = new LinkedHashMap<>();
        grouped.put("VIP", new ArrayList<>());
        grouped.put("LOYAL", new ArrayList<>());
        grouped.put("OCCASIONAL", new ArrayList<>());
        grouped.put("ONE_TIME", new ArrayList<>());

        for (Customer c : customers) {
            grouped.get(segmentKey(c)).add(c);
        }

        Map<String, String> labels = Map.of(
                "VIP", "VIP",
                "LOYAL", "Fideli",
                "OCCASIONAL", "Ocazionali",
                "ONE_TIME", "O singură comandă");

        Map<String, String> definitions = Map.of(
                "VIP", "Cel puțin " + VIP_MIN_ORDERS + " comenzi și peste "
                        + VIP_MIN_REVENUE.toPlainString() + " " + MetricsService.CURRENCY,
                "LOYAL", "Cel puțin " + LOYAL_MIN_ORDERS + " comenzi",
                "OCCASIONAL", "Exact " + OCCASIONAL_MIN_ORDERS + " comenzi",
                "ONE_TIME", "O singură comandă în perioada analizată");

        List<CustomerInsightsDto.Segment> out = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<Customer>> entry : grouped.entrySet()) {
            List<Customer> members = entry.getValue();
            BigDecimal revenue = BigDecimal.ZERO;
            long orders = 0;
            for (Customer c : members) {
                revenue = revenue.add(c.revenue());
                orders += c.orders();
            }
            BigDecimal avgBasket = orders == 0
                    ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);

            out.add(new CustomerInsightsDto.Segment(
                    entry.getKey(),
                    labels.get(entry.getKey()),
                    definitions.get(entry.getKey()),
                    members.size(),
                    MetricsService.scale(revenue),
                    avgBasket,
                    customers.isEmpty() ? null : percent(members.size(), customers.size()),
                    share(revenue, totalRevenue)
            ));
        }
        return out;
    }

    private static String segmentKey(Customer c) {
        if (c.orders() >= VIP_MIN_ORDERS && c.revenue().compareTo(VIP_MIN_REVENUE) >= 0) {
            return "VIP";
        }
        if (c.orders() >= LOYAL_MIN_ORDERS) {
            return "LOYAL";
        }
        if (c.orders() >= OCCASIONAL_MIN_ORDERS) {
            return "OCCASIONAL";
        }
        return "ONE_TIME";
    }

    /** The highest-spending customers, with their identities resolved in one query. */
    private List<CustomerInsightsDto.TopCustomer> topCustomers(List<Customer> customers) {
        List<Customer> ranked = new ArrayList<>(customers);
        ranked.sort((a, b) -> b.revenue().compareTo(a.revenue()));
        if (ranked.size() > TOP_LIMIT) {
            ranked = ranked.subList(0, TOP_LIMIT);
        }
        if (ranked.isEmpty()) {
            return List.of();
        }

        List<Long> ids = ranked.stream().map(Customer::userId).toList();
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            users.put(u.getId(), u);
        }

        List<CustomerInsightsDto.TopCustomer> out = new ArrayList<>(ranked.size());
        for (Customer c : ranked) {
            User u = users.get(c.userId());
            BigDecimal avgBasket = c.orders() == 0
                    ? BigDecimal.ZERO
                    : c.revenue().divide(BigDecimal.valueOf(c.orders()), 2, RoundingMode.HALF_UP);
            out.add(new CustomerInsightsDto.TopCustomer(
                    c.userId(),
                    u == null ? "—" : u.getEmail(),
                    u == null ? null : u.getFullName(),
                    c.orders(),
                    c.revenue(),
                    avgBasket,
                    c.firstEver(),
                    c.lastInWindow(),
                    segmentKey(c)
            ));
        }
        return out;
    }

    private static Double percent(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double share(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal pct(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** One customer's activity inside a window, with novelty already decided. */
    private record Customer(
            Long userId,
            long orders,
            BigDecimal revenue,
            LocalDateTime firstInWindow,
            LocalDateTime lastInWindow,
            LocalDateTime firstEver,
            boolean isNew
    ) {}
}
