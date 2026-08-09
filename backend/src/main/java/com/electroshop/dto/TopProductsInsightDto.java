package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The best-selling products, with the commercial context that makes the ranking
 * actionable.
 *
 * <p>Answers {@code GET /api/products/top-insights}.</p>
 *
 * <p><b>Three rankings, not one.</b> The requirement asks for comparative charts of
 * revenue, unit sales and profit, and those three produce genuinely different lists.
 * The product that sells the most units is often the cheapest; the product that earns
 * the most profit is often not in the top ten by revenue. Collapsing them into a single
 * "top products" list would pick one definition of "top" and hide the other two, so all
 * three are returned and the card lets the operator switch between them.</p>
 *
 * <p><b>Critical stock is shown in the list itself.</b> A best-seller about to run out
 * is the most expensive problem on the dashboard, and it is invisible if the sales
 * ranking and the stock panel are read separately. Every row carries its stock level and
 * days of cover, so the two facts arrive together.</p>
 *
 * @param byRevenue      the top products by revenue, largest first
 * @param byUnits        the top products by units sold, largest first
 * @param byProfit       the top products by realised profit, largest first
 * @param promote        products the rules engine recommends promoting
 * @param categories     the distinct categories present in the window, for the filter
 * @param brands         the distinct brands present in the window, for the filter
 * @param totalRevenue   revenue across the whole window, so each row's share is readable
 * @param totalUnits     units across the whole window
 * @param totalProfit    realised profit across the whole window
 * @param currency       ISO code the amounts are expressed in
 * @param range          the resolved window
 */
public record TopProductsInsightDto(
        List<TopProduct> byRevenue,
        List<TopProduct> byUnits,
        List<TopProduct> byProfit,
        List<PromotionCandidate> promote,
        List<String> categories,
        List<String> brands,
        BigDecimal totalRevenue,
        long totalUnits,
        BigDecimal totalProfit,
        String currency,
        RangeInfoDto range
) {

    /**
     * One product in a ranking.
     *
     * @param productId     database id
     * @param name          product name
     * @param imageUrl      thumbnail, may be null
     * @param brand         brand as recorded
     * @param category      category as recorded
     * @param units         units sold in the window
     * @param revenue       revenue in the window
     * @param profit        realised profit in the window
     * @param marginPct     realised margin
     * @param revenueSharePct this product's share of window revenue
     * @param stockQuantity units on hand now
     * @param daysOfCover   days of stock at the current rate, null when it has not moved
     * @param stockSeverity {@code DANGER} when the stock runs out before restocking is
     *                      realistic, {@code WARNING} when it is close, {@code INFO}
     *                      otherwise — the "critical stock" indicator the requirement
     *                      asks for, computed from velocity rather than from a bare
     *                      quantity threshold
     * @param trendPct      change in units against the preceding window, null when the
     *                      baseline was too small to trust
     * @param dailyUnits    a short per-day series for the row's sparkline, oldest first
     */
    public record TopProduct(
            Long productId,
            String name,
            String imageUrl,
            String brand,
            String category,
            long units,
            BigDecimal revenue,
            BigDecimal profit,
            Double marginPct,
            Double revenueSharePct,
            int stockQuantity,
            Double daysOfCover,
            String stockSeverity,
            Double trendPct,
            List<Long> dailyUnits
    ) {}

    /**
     * A product the rules engine says is worth promoting, with its evidence.
     *
     * @param productId  database id
     * @param name       product name
     * @param imageUrl   thumbnail, may be null
     * @param reason     stable code: {@code HIGH_MARGIN_LOW_VOLUME},
     *                   {@code RISING_TREND}, {@code OVERSTOCKED_GOOD_MARGIN},
     *                   {@code STRONG_SELLER_UNPROMOTED}
     * @param headline   one short line in Romanian
     * @param rationale  the numbers behind it, in Romanian
     * @param marginPct  the product's margin, which is usually why it was selected
     * @param stockQuantity units on hand
     * @param units      units sold in the window
     */
    public record PromotionCandidate(
            Long productId,
            String name,
            String imageUrl,
            String reason,
            String headline,
            String rationale,
            Double marginPct,
            int stockQuantity,
            long units
    ) {}
}
