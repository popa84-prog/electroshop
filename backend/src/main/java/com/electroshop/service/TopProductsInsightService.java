package com.electroshop.service;

import com.electroshop.dto.TopProductsInsightDto;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The best-selling products, with the commercial context that makes the ranking
 * actionable.
 *
 * <p>Task 6.</p>
 *
 * <h2>Three rankings, because "top" means three different things</h2>
 *
 * <p>The requirement asks for comparative charts of revenue, unit sales and profit, and
 * those three produce genuinely different lists. The product that moves the most units
 * is usually the cheapest one. The product that earns the most profit is frequently not
 * in the top ten by revenue at all — high margin on moderate volume beats thin margin on
 * high volume, and only the profit ranking shows it. Collapsing them into a single "top
 * products" list picks one definition and silently discards the other two, which is how
 * a shop ends up optimising for turnover it does not profit from.</p>
 *
 * <h2>Critical stock appears inside the ranking</h2>
 *
 * <p>A best-seller about to run out is the most expensive problem on the dashboard, and
 * it is invisible when the sales ranking and the stock panel are read separately. Every
 * row therefore carries its stock level and days of cover, and the severity is computed
 * from velocity rather than from a bare quantity: twenty units of something selling
 * fifteen a week is a fire, and twenty units of something selling one a month is fine.
 * A fixed threshold cannot tell those apart.</p>
 */
@Service
public class TopProductsInsightService {

    /** How many products each ranking returns. */
    private static final int RANK_LIMIT = 10;

    /** How many promotion candidates the rules engine produces. */
    private static final int PROMOTE_LIMIT = 8;

    /** How many days of sales the per-row sparkline covers. */
    private static final int SPARK_DAYS = 14;

    /** Days of cover at or below this is critical: a reorder would not arrive in time. */
    private static final int COVER_DANGER_DAYS = 14;

    /** Days of cover at or below this is worth watching. */
    private static final int COVER_WARNING_DAYS = 30;

    /** Margin at or above this counts as strong when judging a promotion candidate. */
    private static final double STRONG_MARGIN_PCT = 25.0;

    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public TopProductsInsightService(OrderItemRepository orderItemRepository,
                                     ProductRepository productRepository) {
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    /**
     * The three rankings and the promotion candidates.
     *
     * @param range    the window to report on
     * @param category restrict to one category, or {@code null} for all
     * @param brand    restrict to one brand, or {@code null} for all
     */
    public TopProductsInsightDto insights(MetricRange range, String category, String brand) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);
        long windowDays = Math.max(1, range.lengthInDays(now));

        // Stock levels for every active product, read once. Each ranking needs them and
        // asking per row would issue thirty round trips for three lists of ten.
        Map<Long, Integer> stock = new HashMap<>();
        Map<Long, BigDecimal[]> prices = new HashMap<>();
        for (Object[] row : productRepository.findActiveForAnalysis()) {
            Long id = ((Number) row[0]).longValue();
            stock.put(id, ((Number) row[7]).intValue());
            prices.put(id, new BigDecimal[]{(BigDecimal) row[5], (BigDecimal) row[6]});
        }

        Map<Long, Long> unitsInWindow = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldPerProduct(from, to)) {
            unitsInWindow.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        List<Object[]> revenueRows = fetch(orderItemRepository.topProductsByRevenue(from, to,
                pageFor(category, brand)), category, brand);
        List<Object[]> unitRows = fetch(orderItemRepository.topProductsByUnits(from, to,
                pageFor(category, brand)), category, brand);
        List<Object[]> profitRows = fetch(orderItemRepository.topProductsByProfit(from, to,
                pageFor(category, brand)), category, brand);

        // Window totals come from the item aggregate, so each row's share is a share of
        // the real total rather than of the ten rows displayed.
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        long totalUnits = 0;
        List<Object[]> totals = orderItemRepository.totalsInWindow(from, to);
        if (!totals.isEmpty() && totals.get(0) != null) {
            Object[] t = totals.get(0);
            totalRevenue = ProfitAnalyticsService.dec(t[0]);
            totalProfit = ProfitAnalyticsService.dec(t[1]);
            totalUnits = ProfitAnalyticsService.num(t[2]);
        }

        // Sparkline data for every product about to be displayed, in one query.
        List<Long> displayed = new ArrayList<>();
        collectIds(revenueRows, displayed);
        collectIds(unitRows, displayed);
        collectIds(profitRows, displayed);
        Map<Long, List<Long>> sparks = sparklines(displayed, now);

        return new TopProductsInsightDto(
                toProducts(revenueRows, stock, unitsInWindow, sparks, totalRevenue, windowDays),
                toProducts(unitRows, stock, unitsInWindow, sparks, totalRevenue, windowDays),
                toProducts(profitRows, stock, unitsInWindow, sparks, totalRevenue, windowDays),
                promotionCandidates(profitRows, stock, prices, unitsInWindow, windowDays),
                orderItemRepository.soldCategories(from, to),
                orderItemRepository.soldBrands(from, to),
                MetricsService.scale(totalRevenue),
                totalUnits,
                MetricsService.scale(totalProfit),
                MetricsService.CURRENCY,
                range.info(now)
        );
    }

    /**
     * Over-fetches when a filter is active.
     *
     * <p>The database ranks the whole catalogue; the filter is applied afterwards in
     * memory. Fetching exactly ten and then filtering would return two rows for a
     * narrow brand, so the page is widened when a filter is present and left tight when
     * it is not.</p>
     */
    private static PageRequest pageFor(String category, String brand) {
        boolean filtered = notBlank(category) || notBlank(brand);
        return PageRequest.of(0, filtered ? RANK_LIMIT * 30 : RANK_LIMIT);
    }

    private static List<Object[]> fetch(List<Object[]> rows, String category, String brand) {
        if (!notBlank(category) && !notBlank(brand)) {
            return rows.size() > RANK_LIMIT ? rows.subList(0, RANK_LIMIT) : rows;
        }
        List<Object[]> out = new ArrayList<>(RANK_LIMIT);
        for (Object[] row : rows) {
            String rowBrand = row[3] == null ? null : String.valueOf(row[3]);
            String rowCategory = row[4] == null ? null : String.valueOf(row[4]);
            if (notBlank(category) && !category.equalsIgnoreCase(rowCategory)) {
                continue;
            }
            if (notBlank(brand) && !brand.equalsIgnoreCase(rowBrand)) {
                continue;
            }
            out.add(row);
            if (out.size() == RANK_LIMIT) {
                break;
            }
        }
        return out;
    }

    private static void collectIds(List<Object[]> rows, List<Long> into) {
        for (Object[] row : rows) {
            if (row[0] != null) {
                Long id = ((Number) row[0]).longValue();
                if (!into.contains(id)) {
                    into.add(id);
                }
            }
        }
    }

    /** Daily units for each displayed product, filled with zeroes so the spark is even. */
    private Map<Long, List<Long>> sparklines(List<Long> productIds, LocalDateTime now) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        java.time.LocalDate start = now.toLocalDate().minusDays(SPARK_DAYS - 1L);

        Map<Long, Map<String, Long>> raw = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldPerProductPerDay(
                productIds, start.atStartOfDay(), now)) {
            Long id = ((Number) row[0]).longValue();
            String day = String.format("%04d-%02d-%02d",
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue());
            raw.computeIfAbsent(id, k -> new HashMap<>()).put(day, ((Number) row[4]).longValue());
        }

        Map<Long, List<Long>> out = new HashMap<>();
        for (Long id : productIds) {
            Map<String, Long> byDay = raw.getOrDefault(id, Map.of());
            List<Long> series = new ArrayList<>(SPARK_DAYS);
            for (int i = 0; i < SPARK_DAYS; i++) {
                series.add(byDay.getOrDefault(start.plusDays(i).toString(), 0L));
            }
            out.put(id, series);
        }
        return out;
    }

    private List<TopProductsInsightDto.TopProduct> toProducts(List<Object[]> rows,
                                                              Map<Long, Integer> stock,
                                                              Map<Long, Long> unitsInWindow,
                                                              Map<Long, List<Long>> sparks,
                                                              BigDecimal totalRevenue,
                                                              long windowDays) {
        List<TopProductsInsightDto.TopProduct> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long id = row[0] == null ? null : ((Number) row[0]).longValue();
            BigDecimal revenue = ProfitAnalyticsService.dec(row[5]);
            BigDecimal profit = ProfitAnalyticsService.dec(row[6]);
            long units = ProfitAnalyticsService.num(row[7]);

            int onHand = id == null ? 0 : stock.getOrDefault(id, 0);
            long sold = id == null ? units : unitsInWindow.getOrDefault(id, units);
            Double cover = coverDays(onHand, sold, windowDays);

            out.add(new TopProductsInsightDto.TopProduct(
                    id,
                    row[1] == null ? "—" : String.valueOf(row[1]),
                    row[2] == null ? null : String.valueOf(row[2]),
                    row[3] == null ? null : String.valueOf(row[3]),
                    row[4] == null ? null : String.valueOf(row[4]),
                    units,
                    MetricsService.scale(revenue),
                    MetricsService.scale(profit),
                    margin(profit, revenue),
                    share(revenue, totalRevenue),
                    onHand,
                    cover,
                    stockSeverity(onHand, cover),
                    null,
                    id == null ? List.of() : sparks.getOrDefault(id, List.of())
            ));
        }
        return out;
    }

    /**
     * Which products deserve a promotion, with the reason attached.
     *
     * <p>Three shapes qualify, and each is a different commercial situation rather than
     * three phrasings of "sells well". A high-margin product with modest volume is
     * money left on the table. Deep stock at a healthy margin is capital that a
     * discount would release at a profit. A strong seller with plenty of stock is the
     * safest thing to put in front of more people.</p>
     */
    private List<TopProductsInsightDto.PromotionCandidate> promotionCandidates(
            List<Object[]> profitRows,
            Map<Long, Integer> stock,
            Map<Long, BigDecimal[]> prices,
            Map<Long, Long> unitsInWindow,
            long windowDays) {

        List<TopProductsInsightDto.PromotionCandidate> out = new ArrayList<>();

        for (Object[] row : profitRows) {
            if (out.size() >= PROMOTE_LIMIT || row[0] == null) {
                continue;
            }
            Long id = ((Number) row[0]).longValue();
            String name = row[1] == null ? "—" : String.valueOf(row[1]);
            String image = row[2] == null ? null : String.valueOf(row[2]);

            BigDecimal revenue = ProfitAnalyticsService.dec(row[5]);
            BigDecimal profit = ProfitAnalyticsService.dec(row[6]);
            long units = ProfitAnalyticsService.num(row[7]);
            Double marginPct = margin(profit, revenue);
            int onHand = stock.getOrDefault(id, 0);
            Double cover = coverDays(onHand, units, windowDays);

            if (marginPct == null || marginPct < STRONG_MARGIN_PCT) {
                continue;
            }

            // A product with no stock cannot be promoted, however good its margin.
            // Advertising something the shop cannot ship costs goodwill, not just a
            // sale.
            if (onHand <= 0) {
                continue;
            }

            if (cover != null && cover > 120) {
                out.add(new TopProductsInsightDto.PromotionCandidate(
                        id, name, image,
                        "OVERSTOCKED_GOOD_MARGIN",
                        "Stoc adânc, marjă bună",
                        String.format(
                                "Marjă de %.1f%% și %d bucăți pe stoc, adică peste %.0f de zile "
                                        + "de acoperire la ritmul actual. O promoție eliberează "
                                        + "capital fără să atingă profitabilitatea.",
                                marginPct, onHand, cover),
                        marginPct, onHand, units));
            } else if (units <= 5) {
                out.add(new TopProductsInsightDto.PromotionCandidate(
                        id, name, image,
                        "HIGH_MARGIN_LOW_VOLUME",
                        "Marjă mare, volum mic",
                        String.format(
                                "Marjă de %.1f%%, dar doar %d bucăți vândute în perioada "
                                        + "analizată. Fiecare bucată în plus contează mai mult "
                                        + "decât la un produs cu marjă subțire.",
                                marginPct, units),
                        marginPct, onHand, units));
            } else {
                out.add(new TopProductsInsightDto.PromotionCandidate(
                        id, name, image,
                        "STRONG_SELLER_UNPROMOTED",
                        "Vânzător puternic, stoc suficient",
                        String.format(
                                "%d bucăți vândute la o marjă de %.1f%%, cu %d bucăți rămase "
                                        + "pe stoc. Cererea este dovedită și marfa există.",
                                units, marginPct, onHand),
                        marginPct, onHand, units));
            }
        }
        return out;
    }

    /**
     * Days of stock remaining at the rate observed in the window.
     *
     * <p>Null when nothing sold. Reporting a very large number instead would sort a
     * dead product beside a well-stocked one.</p>
     */
    private static Double coverDays(int stock, long units, long windowDays) {
        if (units <= 0 || windowDays <= 0) {
            return null;
        }
        double perDay = (double) units / windowDays;
        return BigDecimal.valueOf(stock / perDay).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * How urgent a stock level is, judged by how fast it is emptying.
     *
     * <p>Velocity rather than a fixed quantity, because the same twenty units mean
     * opposite things for a fast and a slow product, and a threshold cannot tell them
     * apart.</p>
     */
    private static String stockSeverity(int stock, Double cover) {
        if (stock <= 0) {
            return "DANGER";
        }
        if (cover == null) {
            return "INFO";
        }
        if (cover <= COVER_DANGER_DAYS) {
            return "DANGER";
        }
        if (cover <= COVER_WARNING_DAYS) {
            return "WARNING";
        }
        return "INFO";
    }

    private static Double margin(BigDecimal profit, BigDecimal revenue) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return profit.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP)
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
