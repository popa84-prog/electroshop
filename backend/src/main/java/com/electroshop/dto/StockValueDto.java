package com.electroshop.dto;

import java.math.BigDecimal;

/**
 * The capital tied up in inventory, at cost.
 *
 * <p>Answers {@code GET /api/metrics/stock-value}. The headline figure is
 * {@code SUM(purchasePrice × stockQuantity)} over active products.</p>
 *
 * <p><b>Why the response is not a single number.</b> A catalogue where some products
 * have no purchase price produces a stock value that is correct for what it covers and
 * silently understates everything else. Returning only the sum would let an operator
 * read an authoritative-looking figure without knowing that a fifth of the catalogue
 * is missing from it. {@code productsWithoutCost} and {@code unitsWithoutCost} make the
 * gap part of the answer, and the card shows them as a warning beside the value.</p>
 *
 * @param totalValue         summed cost of everything in stock that has a purchase price
 * @param currency           ISO code the amounts are expressed in
 * @param productsCounted    how many distinct products contributed to the total
 * @param unitsCounted       how many physical units those products represent
 * @param productsWithoutCost how many active products in stock were skipped for having
 *                           no purchase price recorded
 * @param unitsWithoutCost   how many units those skipped products represent — the size
 *                           of the blind spot, in goods rather than in rows
 * @param delta              the same figure one period earlier, for the trend badge
 */
public record StockValueDto(
        BigDecimal totalValue,
        String currency,
        long productsCounted,
        long unitsCounted,
        long productsWithoutCost,
        long unitsWithoutCost,
        DeltaDto delta
) {
}
