package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Which products are rising, which are falling, and which have stopped moving.
 *
 * <p>Answers {@code GET /api/products/performance}.</p>
 *
 * <p><b>Growth is measured against the product's own past, not against other
 * products.</b> A product that went from two units to six grew by 200%; a product that
 * went from four hundred to four hundred and forty grew by 10% and sold seventy times
 * more. Ranking by percentage alone promotes noise from the tail of the catalogue, so
 * every entry carries both the percentage and the absolute movement, and the ranking
 * requires a minimum volume in the baseline period before a percentage is trusted at
 * all. {@code minVolumeForTrend} states that floor in the response.</p>
 *
 * <p><b>Stagnant is not the same as declining.</b> A product with steady sales is
 * healthy; a product with no sales in the window while its category moves is the
 * problem. Stagnation is therefore judged against the product having stock available —
 * something that cannot sell because it is out of stock is an inventory failure, not a
 * demand failure, and it belongs in the inventory panel instead.</p>
 *
 * @param rising            products whose sales grew most, strongest first
 * @param declining         products whose sales fell most, worst first
 * @param stagnant          in-stock products that sold nothing in the window
 * @param recommendations   specific promotion suggestions with the numbers behind them
 * @param categoryMovement  net unit movement per category, for the overview chart
 * @param productsAnalysed  how many products had enough history to be judged
 * @param minVolumeForTrend the baseline unit count below which a percentage change is
 *                          not reported
 * @param currency          ISO code the amounts are expressed in
 * @param range             the resolved window
 */
public record ProductPerformanceDto(
        List<ProductTrend> rising,
        List<ProductTrend> declining,
        List<ProductTrend> stagnant,
        List<Recommendation> recommendations,
        List<SeriesPointDto> categoryMovement,
        long productsAnalysed,
        long minVolumeForTrend,
        String currency,
        RangeInfoDto range
) {

    /**
     * One product's movement between the window and the window before it.
     *
     * @param productId      database id
     * @param name           product name
     * @param imageUrl       thumbnail, may be null
     * @param brand          brand as recorded
     * @param category       category as recorded
     * @param unitsCurrent   units sold in the window
     * @param unitsPrevious  units sold in the preceding window of equal length
     * @param unitsDelta     absolute difference — the figure that says whether the
     *                       percentage matters
     * @param changePct      percentage change, null when the baseline was zero or below
     *                       the minimum volume
     * @param revenueCurrent revenue in the window
     * @param profitCurrent  realised profit in the window
     * @param stockQuantity  units on hand now, so a rising product about to run out is
     *                       visible as one row rather than two panels
     * @param daysOfCover    days of stock at the current rate, null when it has not moved
     * @param severity       {@code DANGER}, {@code WARNING}, {@code INFO} or
     *                       {@code SUCCESS}, decided on the server
     */
    public record ProductTrend(
            Long productId,
            String name,
            String imageUrl,
            String brand,
            String category,
            long unitsCurrent,
            long unitsPrevious,
            long unitsDelta,
            Double changePct,
            BigDecimal revenueCurrent,
            BigDecimal profitCurrent,
            int stockQuantity,
            Double daysOfCover,
            String severity
    ) {}

    /**
     * A promotion recommendation with its evidence attached.
     *
     * <p>Produced by the rules engine in {@code AiInsightService}. Every recommendation
     * states the numbers that generated it, so it can be judged on the evidence rather
     * than accepted on authority. A suggestion an operator cannot check is a suggestion
     * an operator will eventually stop reading.</p>
     *
     * @param productId  database id
     * @param name       product name
     * @param imageUrl   thumbnail, may be null
     * @param action     stable code: {@code PROMOTE}, {@code DISCOUNT}, {@code BUNDLE},
     *                   {@code RESTOCK}, {@code REVIEW_PRICE}
     * @param headline   one short line in Romanian, shown as the card title
     * @param rationale  the numbers behind it, in Romanian
     * @param confidence {@code HIGH}, {@code MEDIUM} or {@code LOW}, set by how much
     *                   history the rule had to work with
     * @param impact     estimated monthly effect in currency, null when the rule cannot
     *                   quantify one honestly
     */
    public record Recommendation(
            Long productId,
            String name,
            String imageUrl,
            String action,
            String headline,
            String rationale,
            String confidence,
            BigDecimal impact
    ) {}
}
