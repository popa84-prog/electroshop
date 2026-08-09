package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The profit the current inventory would produce if it all sold at list price.
 *
 * <p>Answers {@code GET /api/metrics/profit-potential}. The headline figure is
 * {@code SUM((price − purchasePrice) × stockQuantity)} over active products that have
 * a purchase price.</p>
 *
 * <p><b>The negative-margin list is part of the answer, not a decoration.</b> A single
 * summed number hides products priced below what they cost, because their negative
 * contribution is absorbed by the profitable ones. Those products are the most
 * actionable thing on the whole dashboard — every unit sold loses money — so they are
 * listed explicitly rather than left for someone to discover.</p>
 *
 * @param totalPotential      summed margin across everything in stock with a known cost
 * @param currency            ISO code the amounts are expressed in
 * @param productsCounted     how many products contributed
 * @param productsWithoutCost how many active products in stock were skipped for having
 *                            no purchase price
 * @param negativeMargin      products whose selling price is below their purchase
 *                            price, worst first
 * @param delta               the same figure one period earlier, for the trend badge
 */
public record ProfitPotentialDto(
        BigDecimal totalPotential,
        String currency,
        long productsCounted,
        long productsWithoutCost,
        List<NegativeMarginProduct> negativeMargin,
        DeltaDto delta
) {

    /**
     * A product that loses money on every unit sold.
     *
     * @param productId     database id, so the card can link straight to the editor
     * @param name          product name
     * @param price         current selling price
     * @param purchasePrice recorded cost
     * @param stockQuantity how many units are exposed to the loss
     * @param lossPerUnit   {@code purchasePrice − price}, always positive here
     * @param totalLoss     {@code lossPerUnit × stockQuantity} — the exposure, which is
     *                      what decides whether this is an annoyance or an emergency
     */
    public record NegativeMarginProduct(
            Long productId,
            String name,
            BigDecimal price,
            BigDecimal purchasePrice,
            int stockQuantity,
            BigDecimal lossPerUnit,
            BigDecimal totalLoss
    ) {}
}
