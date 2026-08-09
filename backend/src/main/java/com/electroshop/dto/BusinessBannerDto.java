package com.electroshop.dto;

import java.math.BigDecimal;

/**
 * The four business figures that replace the old "Users / Products / Orders / Revenue"
 * cards at the top of the dashboard.
 *
 * <p>Answers {@code GET /api/metrics/banner}. The four old cards counted rows; these
 * four measure the business. How many products exist is a fact about the database. How
 * much capital they represent, what margin they carry, and what they sold this month
 * are facts about the company.</p>
 *
 * <p><b>Why one endpoint and not four.</b> The banner renders as a unit. Four separate
 * requests would let the cards arrive at different times and, worse, be computed
 * against a catalogue that changed between them — a stock value from one instant beside
 * a margin from another, which do not reconcile. One query set, one moment.</p>
 *
 * @param stockValue      capital tied up in inventory, at cost
 * @param profitPotential margin the inventory would yield at list price
 * @param monthSales      revenue booked in the current calendar month
 * @param averageMargin   margin as a percentage of retail value
 * @param currency        ISO code the amounts are expressed in
 * @param dataQuality     what the figures could not see
 */
public record BusinessBannerDto(
        Metric stockValue,
        Metric profitPotential,
        Metric monthSales,
        Metric averageMargin,
        String currency,
        DataQuality dataQuality
) {

    /**
     * One card's worth of data.
     *
     * @param value  the figure itself
     * @param unit   {@code "CURRENCY"} or {@code "PERCENT"} — the card formats on this
     *               rather than guessing from the magnitude
     * @param delta  movement against the previous comparable period
     * @param series a short sparkline history, oldest first, or an empty list when the
     *               metric has no meaningful history
     */
    public record Metric(
            BigDecimal value,
            String unit,
            DeltaDto delta,
            java.util.List<BigDecimal> series
    ) {}

    /**
     * How much of the catalogue the figures above could actually see.
     *
     * <p>A margin computed over the 80% of products that have a purchase price is a
     * margin for those products, not for the business. The banner shows this as a
     * warning strip when {@code productsWithoutCost} is above zero, because an
     * incomplete cost column produces an optimistic margin and the operator has to
     * know which of the two they are reading.</p>
     *
     * @param totalActiveProducts  active products in the catalogue
     * @param productsWithoutCost  how many of them have no purchase price
     * @param coveragePct          share of active products the metrics covered
     */
    public record DataQuality(
            long totalActiveProducts,
            long productsWithoutCost,
            double coveragePct
    ) {}
}
