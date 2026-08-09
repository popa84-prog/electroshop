package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The state of the inventory, split into the four situations that need action.
 *
 * <p>Answers {@code GET /api/inventory/health}.</p>
 *
 * <p>The four sections answer four different questions and are deliberately not merged
 * into one sorted list. Critical stock is about to cost a sale. Overstock has already
 * cost the capital. Out-of-stock has stopped selling. Restock recommendations are the
 * subset of the first three where the data supports a specific action. A single list
 * sorted by quantity would interleave them and lose the distinction that makes each
 * one actionable.</p>
 *
 * <p><b>Overstock is judged by movement, not by quantity alone.</b> A hundred units of
 * something that sells thirty a week is three weeks of cover; a hundred units of
 * something that sold twice this year is dead capital. The threshold in the
 * requirement is applied, and the sales velocity is reported beside it so the two
 * cases can be told apart.</p>
 *
 * @param critical      products at or below the critical threshold, scarcest first
 * @param overstocked   products above the overstock threshold, most capital first
 * @param outOfStock    products with zero units, highest recent demand first
 * @param restock       specific restock recommendations, most urgent first
 * @param summary       the counters behind the panel's badges
 * @param thresholds    the limits used, echoed so the panel can label them
 * @param currency      ISO code the amounts are expressed in
 */
public record InventoryHealthDto(
        List<InventoryItem> critical,
        List<InventoryItem> overstocked,
        List<InventoryItem> outOfStock,
        List<RestockSuggestion> restock,
        Summary summary,
        Thresholds thresholds,
        String currency
) {

    /**
     * One product as it appears in an inventory table.
     *
     * @param productId       database id
     * @param name            product name
     * @param imageUrl        thumbnail, may be null
     * @param sku             stock keeping unit, may be null
     * @param brand           brand as recorded
     * @param category        category as recorded
     * @param stockQuantity   units on hand
     * @param price           current selling price
     * @param purchasePrice   recorded cost, null when the product has none
     * @param stockValue      {@code purchasePrice × stockQuantity}, null without a cost
     * @param unitsSold30d    units sold in the last thirty days — the movement figure
     *                        that separates healthy depth from dead capital
     * @param daysOfCover     how many days the current stock lasts at the current rate,
     *                        or null when the product has not moved at all and the
     *                        division has no meaning
     * @param severity        {@code DANGER}, {@code WARNING} or {@code INFO}, decided by
     *                        the backend so every table in the panel colours the same
     *                        situation the same way
     */
    public record InventoryItem(
            Long productId,
            String name,
            String imageUrl,
            String sku,
            String brand,
            String category,
            int stockQuantity,
            BigDecimal price,
            BigDecimal purchasePrice,
            BigDecimal stockValue,
            long unitsSold30d,
            Double daysOfCover,
            String severity
    ) {}

    /**
     * A concrete restock recommendation.
     *
     * <p>Every field that could be an opinion is instead a computed quantity with its
     * inputs attached, so an operator can disagree with the recommendation on the
     * evidence rather than on trust.</p>
     *
     * @param productId        database id
     * @param name             product name
     * @param imageUrl         thumbnail, may be null
     * @param stockQuantity    units on hand right now
     * @param unitsSold30d     units sold in the last thirty days
     * @param dailyVelocity    average units per day over that window
     * @param daysOfCover      days of stock remaining at that velocity
     * @param suggestedUnits   how many units to order to reach the cover target
     * @param estimatedCost    {@code suggestedUnits × purchasePrice}, null without a cost
     * @param supplierName     the supplier of the most recent purchase of this product,
     *                         null when it has never been purchased through the system
     * @param supplierId       that supplier's id, null in the same case
     * @param urgency          {@code DANGER} when stock runs out inside the lead-time
     *                         window, {@code WARNING} when it runs out inside the cover
     *                         target, {@code INFO} otherwise
     * @param rationale        one sentence in Romanian stating the numbers that produced
     *                         the recommendation
     */
    public record RestockSuggestion(
            Long productId,
            String name,
            String imageUrl,
            int stockQuantity,
            long unitsSold30d,
            Double dailyVelocity,
            Double daysOfCover,
            int suggestedUnits,
            BigDecimal estimatedCost,
            String supplierName,
            Long supplierId,
            String urgency,
            String rationale
    ) {}

    /**
     * The counters the panel shows as badges above the tables.
     *
     * @param totalActiveProducts   active products in the catalogue
     * @param criticalCount         how many are at or below the critical threshold
     * @param overstockedCount      how many are above the overstock threshold
     * @param outOfStockCount       how many have no units at all
     * @param restockCount          how many carry a recommendation
     * @param totalStockValue       capital tied up across the whole catalogue
     * @param overstockedValue      capital tied up in overstocked products specifically,
     *                              which is the number that justifies acting on them
     * @param productsWithoutCost   products with no purchase price, so the value figures
     *                              can be read with the right caution
     */
    public record Summary(
            long totalActiveProducts,
            long criticalCount,
            long overstockedCount,
            long outOfStockCount,
            long restockCount,
            BigDecimal totalStockValue,
            BigDecimal overstockedValue,
            long productsWithoutCost
    ) {}

    /**
     * The limits the report applied.
     *
     * <p>Returned rather than assumed by the frontend, so the table header can say
     * "sub 5 bucăți" without a constant duplicated on both sides that can drift apart.</p>
     *
     * @param criticalBelow      stock strictly below this counts as critical
     * @param overstockAbove     stock strictly above this counts as overstocked
     * @param coverTargetDays    how many days of stock a restock recommendation aims for
     * @param leadTimeDays       assumed supplier lead time, used to decide urgency
     * @param velocityWindowDays how many days of sales the velocity is averaged over
     */
    public record Thresholds(
            int criticalBelow,
            int overstockAbove,
            int coverTargetDays,
            int leadTimeDays,
            int velocityWindowDays
    ) {}
}
