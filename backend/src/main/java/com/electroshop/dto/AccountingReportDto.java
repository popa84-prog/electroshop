package com.electroshop.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Primary accounting summary for a date range:
 * revenue from sales, expenses from purchases, and the resulting balance.
 */
public record AccountingReportDto(
        LocalDate from,
        LocalDate to,
        BigDecimal salesTotal,      // venituri din vânzări
        long salesCount,
        BigDecimal purchasesTotal,  // cheltuieli cu marfa (cumpărări)
        long purchasesCount,
        BigDecimal profit,          // salesTotal - purchasesTotal
        BigDecimal marginPercent,   // profit / salesTotal * 100
        List<DailyPoint> byDay
) {
    public record DailyPoint(String date, BigDecimal sales, BigDecimal purchases) {
    }
}
