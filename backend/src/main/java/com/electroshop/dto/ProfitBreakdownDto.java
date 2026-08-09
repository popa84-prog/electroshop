package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Where the profit comes from: by category, by brand, and by individual product.
 *
 * <p>Answers {@code GET /api/metrics/profit-breakdown}, which backs three charts — a
 * bar chart per category, a donut per brand, and a horizontal bar of the top ten
 * products.</p>
 *
 * <p><b>Realised profit, not potential profit.</b> The figures here come from
 * {@code OrderItem}, where {@code unitPrice} and {@code costPrice} were both captured
 * at the moment of sale. That is what the business actually earned. Computing it from
 * today's product prices instead would rewrite history every time someone edits a
 * price, and a report that changes retroactively is not a report.</p>
 *
 * <p><b>The donut is capped.</b> A brand chart with 180 slices communicates nothing, so
 * brands beyond the top slices are summed into a single "Altele" entry. The cap is
 * disclosed in {@code brandsAggregated} rather than applied silently, because a reader
 * needs to know whether "Altele" is three brands or ninety.</p>
 *
 * @param byCategory       profit per category, largest first
 * @param byBrand          profit per brand, largest first, with a tail entry
 * @param topProducts      the ten most profitable products in the window
 * @param totalProfit      profit across the whole window, before any capping
 * @param totalRevenue     revenue across the whole window
 * @param marginPct        {@code totalProfit / totalRevenue}, as a percentage
 * @param currency         ISO code the amounts are expressed in
 * @param range            the resolved window, echoed back so the chart can label itself
 * @param brandsAggregated how many brands were folded into the tail entry
 * @param itemsWithoutCost order lines skipped because no cost price was recorded on them
 */
public record ProfitBreakdownDto(
        List<Slice> byCategory,
        List<Slice> byBrand,
        List<ProductProfit> topProducts,
        BigDecimal totalProfit,
        BigDecimal totalRevenue,
        Double marginPct,
        String currency,
        RangeInfoDto range,
        int brandsAggregated,
        long itemsWithoutCost
) {

    /**
     * One segment of a breakdown.
     *
     * @param label     category or brand name; {@code "Fără categorie"} / {@code "Altele"}
     *                  for the unassigned and tail buckets
     * @param profit    profit attributed to the segment
     * @param revenue   revenue attributed to the segment
     * @param sharePct  the segment's share of total profit, so the tooltip can show
     *                  both the exact figure and the proportion without the frontend
     *                  recomputing it against a possibly capped total
     * @param marginPct the segment's own margin, which is what makes a small segment
     *                  interesting: low revenue at high margin is a growth candidate
     * @param units     units sold in the segment
     */
    public record Slice(
            String label,
            BigDecimal profit,
            BigDecimal revenue,
            Double sharePct,
            Double marginPct,
            long units
    ) {}

    /**
     * One product in the top-ten bar.
     *
     * @param productId database id, so the bar links to the product editor
     * @param name      product name at the time of the report
     * @param imageUrl  thumbnail, may be null
     * @param brand     brand as recorded on the product
     * @param category  category as recorded on the product
     * @param profit    realised profit in the window
     * @param revenue   realised revenue in the window
     * @param units     units sold in the window
     * @param marginPct the product's realised margin
     */
    public record ProductProfit(
            Long productId,
            String name,
            String imageUrl,
            String brand,
            String category,
            BigDecimal profit,
            BigDecimal revenue,
            long units,
            Double marginPct
    ) {}
}
