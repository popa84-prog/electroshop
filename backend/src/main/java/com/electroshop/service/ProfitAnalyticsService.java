package com.electroshop.service;

import com.electroshop.dto.ProfitBreakdownDto;
import com.electroshop.repository.OrderItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the profit actually comes from: by category, by brand, and by product.
 *
 * <p>Task 12. Backs three charts on one card — a bar chart per category, a donut per
 * brand, and a horizontal bar of the ten most profitable products.</p>
 *
 * <h2>Realised profit, not potential profit</h2>
 *
 * <p>Every figure here comes from {@code OrderItem}, where {@code unitPrice} and
 * {@code costPrice} were both captured at the moment of sale. That is what the business
 * earned. The alternative — computing profit from today's product prices — would
 * rewrite last quarter's results every time somebody edits a price, and a report that
 * changes retroactively cannot be reconciled against anything.</p>
 *
 * <h2>Why the donut is capped and says so</h2>
 *
 * <p>A brand chart with a hundred and eighty slices communicates nothing; the slices
 * become invisible and the legend becomes a wall. Brands beyond the top ones are summed
 * into a single tail entry. The cap is disclosed as {@code brandsAggregated} rather than
 * applied silently, because "Altele" covering three brands and "Altele" covering ninety
 * are different pictures and the reader cannot tell them apart from the chart.</p>
 *
 * <h2>Shares are computed against the true total</h2>
 *
 * <p>Each slice's percentage is its share of the whole window's profit, computed before
 * any capping. Computing shares against the sum of the displayed slices would make the
 * visible ones add to 100% and quietly overstate every one of them.</p>
 */
@Service
public class ProfitAnalyticsService {

    /** How many brand slices the donut draws before folding the rest into a tail. */
    private static final int BRAND_SLICE_LIMIT = 8;

    /** How many category bars the chart draws. Categories are few; this is a safety cap. */
    private static final int CATEGORY_LIMIT = 12;

    /** The requirement asks for a top ten. */
    private static final int TOP_PRODUCT_LIMIT = 10;

    /** Label for the folded remainder of the brand donut. */
    private static final String TAIL_LABEL = "Altele";

    private final OrderItemRepository orderItemRepository;

    public ProfitAnalyticsService(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * The full breakdown for a window, optionally narrowed to one category or brand.
     *
     * <p>The filters are applied after aggregation rather than inside the queries. The
     * catalogue produces at most a few hundred category and brand rows per window, and
     * filtering them in memory keeps three parameterised query variants from becoming
     * six. If either dimension ever grows past that, the filter moves into the query;
     * it does not need to today, and a query built for a size the data does not have is
     * a query nobody can verify.</p>
     *
     * @param range    the window to report on
     * @param category restrict to one category, or {@code null} for all
     * @param brand    restrict to one brand, or {@code null} for all
     */
    public ProfitBreakdownDto breakdown(MetricRange range, String category, String brand) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        List<Object[]> categoryRows = orderItemRepository.profitByCategory(from, to);
        List<Object[]> brandRows = orderItemRepository.profitByBrand(from, to);

        if (category != null && !category.isBlank()) {
            categoryRows = categoryRows.stream()
                    .filter(r -> category.equalsIgnoreCase(String.valueOf(r[0])))
                    .toList();
        }
        if (brand != null && !brand.isBlank()) {
            brandRows = brandRows.stream()
                    .filter(r -> brand.equalsIgnoreCase(String.valueOf(r[0])))
                    .toList();
        }

        // The window totals come from the category rows rather than from a separate
        // query. Two queries could disagree if an order lands between them, and a card
        // whose slices do not add up to its own total is a card nobody trusts again.
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Object[] row : categoryRows) {
            totalRevenue = totalRevenue.add(dec(row[1]));
            totalProfit = totalProfit.add(dec(row[2]));
        }

        List<ProfitBreakdownDto.Slice> byCategory =
                slices(categoryRows, totalProfit, CATEGORY_LIMIT, false).slices();
        Folded brandFold = slices(brandRows, totalProfit, BRAND_SLICE_LIMIT, true);

        List<ProfitBreakdownDto.ProductProfit> top = topProducts(from, to, category, brand);

        Double marginPct = totalRevenue.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalProfit.multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                        .doubleValue();

        return new ProfitBreakdownDto(
                byCategory,
                brandFold.slices(),
                top,
                MetricsService.scale(totalProfit),
                MetricsService.scale(totalRevenue),
                marginPct,
                MetricsService.CURRENCY,
                range.info(now),
                brandFold.folded(),
                orderItemRepository.countWithoutCostInWindow(from, to)
        );
    }

    /**
     * Turns aggregation rows into chart slices, optionally folding the tail.
     *
     * @param rows      {@code [label, revenue, profit, units]}, already ordered by profit
     * @param grandTotal the window's whole profit, used for every share percentage
     * @param limit     how many slices to keep
     * @param foldTail  whether to sum the remainder into one entry or drop it
     */
    private Folded slices(List<Object[]> rows, BigDecimal grandTotal, int limit, boolean foldTail) {
        List<ProfitBreakdownDto.Slice> out = new ArrayList<>();
        int kept = Math.min(limit, rows.size());

        for (int i = 0; i < kept; i++) {
            out.add(toSlice(rows.get(i), grandTotal));
        }

        int folded = 0;
        if (foldTail && rows.size() > limit) {
            BigDecimal profit = BigDecimal.ZERO;
            BigDecimal revenue = BigDecimal.ZERO;
            long units = 0;
            for (int i = limit; i < rows.size(); i++) {
                Object[] row = rows.get(i);
                revenue = revenue.add(dec(row[1]));
                profit = profit.add(dec(row[2]));
                units += num(row[3]);
            }
            folded = rows.size() - limit;
            out.add(new ProfitBreakdownDto.Slice(
                    TAIL_LABEL,
                    MetricsService.scale(profit),
                    MetricsService.scale(revenue),
                    share(profit, grandTotal),
                    margin(profit, revenue),
                    units
            ));
        }

        return new Folded(out, folded);
    }

    private ProfitBreakdownDto.Slice toSlice(Object[] row, BigDecimal grandTotal) {
        String label = String.valueOf(row[0]);
        BigDecimal revenue = dec(row[1]);
        BigDecimal profit = dec(row[2]);
        long units = num(row[3]);
        return new ProfitBreakdownDto.Slice(
                label,
                MetricsService.scale(profit),
                MetricsService.scale(revenue),
                share(profit, grandTotal),
                margin(profit, revenue),
                units
        );
    }

    /**
     * The ten most profitable products in the window.
     *
     * <p>Filtered in memory for the same reason the slices are, and over-fetched by a
     * factor so that a filter which excludes most of the top rows still finds ten
     * matches instead of returning two.</p>
     */
    private List<ProfitBreakdownDto.ProductProfit> topProducts(LocalDateTime from,
                                                               LocalDateTime to,
                                                               String category,
                                                               String brand) {
        boolean filtered = (category != null && !category.isBlank())
                || (brand != null && !brand.isBlank());
        int fetch = filtered ? TOP_PRODUCT_LIMIT * 20 : TOP_PRODUCT_LIMIT;

        List<Object[]> rows = orderItemRepository.topProductsByProfit(
                from, to, PageRequest.of(0, fetch));

        List<ProfitBreakdownDto.ProductProfit> out = new ArrayList<>(TOP_PRODUCT_LIMIT);
        for (Object[] row : rows) {
            String rowBrand = row[3] == null ? null : String.valueOf(row[3]);
            String rowCategory = row[4] == null ? null : String.valueOf(row[4]);

            if (category != null && !category.isBlank() && !category.equalsIgnoreCase(rowCategory)) {
                continue;
            }
            if (brand != null && !brand.isBlank() && !brand.equalsIgnoreCase(rowBrand)) {
                continue;
            }

            BigDecimal revenue = dec(row[5]);
            BigDecimal profit = dec(row[6]);
            out.add(new ProfitBreakdownDto.ProductProfit(
                    row[0] == null ? null : ((Number) row[0]).longValue(),
                    row[1] == null ? "—" : String.valueOf(row[1]),
                    row[2] == null ? null : String.valueOf(row[2]),
                    rowBrand,
                    rowCategory,
                    MetricsService.scale(profit),
                    MetricsService.scale(revenue),
                    num(row[7]),
                    margin(profit, revenue)
            ));
            if (out.size() == TOP_PRODUCT_LIMIT) {
                break;
            }
        }
        return out;
    }

    /** A slice's share of the window's profit, or null when there is no profit to share. */
    private static Double share(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total.abs(), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Profit as a percentage of revenue, or null when nothing was sold. */
    private static Double margin(BigDecimal profit, BigDecimal revenue) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return profit.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    static BigDecimal dec(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return BigDecimal.valueOf(((Number) v).doubleValue());
    }

    static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    /** Slices plus how many rows were folded into the tail entry. */
    private record Folded(List<ProfitBreakdownDto.Slice> slices, int folded) {}
}
