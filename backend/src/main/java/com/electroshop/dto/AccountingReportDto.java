package com.electroshop.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Primary accounting summary for a date range.
 * <p>
 * {@code profit} is the REAL gross margin on goods actually sold in the range:
 * for every item on every non-cancelled order placed in the range, sale price
 * minus that item's acquisition/purchase-price snapshot, summed and multiplied
 * by quantity ({@code salesTotal - cogsTotal}). This mirrors the per-product
 * profit convention already shown in the admin Products table
 * ({@code price - purchasePrice}).
 * <p>
 * {@code purchasesTotal}/{@code purchasesCount} remain a SEPARATE, purely
 * informational cash-basis figure: money paid to suppliers to restock
 * inventory during the range, regardless of whether that stock was sold in
 * the same range. They are NOT subtracted into {@code profit} — mixing the two
 * bases is exactly the bug this DTO's previous shape produced (a report with
 * zero supplier purchases in range showed 100% margin on everything sold,
 * even though the goods sold obviously had a real acquisition cost).
 */
public record AccountingReportDto(
        LocalDate from,
        LocalDate to,
        BigDecimal salesTotal,            // venituri din vânzări (comenzi necanceled din perioadă)
        long salesCount,
        BigDecimal purchasesTotal,        // cheltuieli cash cu marfa — intrări de la furnizori în perioadă (informativ)
        long purchasesCount,
        BigDecimal cogsTotal,             // costul de achiziție al produselor efectiv vândute în perioadă
        long itemsWithUnknownCost,        // bucăți vândute al căror preț de achiziție e necunoscut (exclus din cogsTotal)
        BigDecimal profit,                // salesTotal - cogsTotal (marja reală de profit)
        BigDecimal marginPercent,         // profit / salesTotal * 100
        List<DailyPoint> byDay
) {
    public record DailyPoint(String date, BigDecimal sales, BigDecimal purchases) {
    }
}
