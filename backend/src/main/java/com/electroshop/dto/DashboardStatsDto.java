package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsDto(
        long totalUsers,
        long totalProducts,
        long totalOrders,
        BigDecimal totalRevenue,
        Trend usersTrend,
        Trend productsTrend,
        Trend ordersTrend,
        Trend revenueTrend,
        List<StatusCount> ordersByStatus,
        List<TopProduct> topProducts,
        List<SalesPoint> salesByDay,
        List<LowStockProduct> lowStockProducts,
        // Feature #9 — "venit lunar + comparație cu luna precedentă", separate from
        // revenueTrend (which is a rolling 7-vs-7-day window, not calendar months).
        MonthlyRevenue monthlyRevenue,
        // Feature #9 — "grafic evoluție comenzi": full order-count history per day,
        // same shape as salesByDay so the frontend can reuse the same day/month/year
        // aggregation it already has for the revenue chart.
        List<CountPoint> ordersByDay
) {
    public record StatusCount(String status, long count) {
    }

    public record TopProduct(Long productId, String name, String imageUrl, long unitsSold,
                              BigDecimal revenue, Double trendPct, List<Long> dailyUnits) {
    }

    public record SalesPoint(String date, BigDecimal amount) {
    }

    public record CountPoint(String date, long count) {
    }

    /**
     * A daily series for the last {@code TREND_DAYS} days (oldest first) plus the
     * percentage change of the most recent 7 days over the 7 days before that.
     * Backs both the stat-card sparklines and their "+N% vs last week" badge.
     */
    public record Trend(List<Double> series, Double changePct) {
    }

    public record LowStockProduct(Long productId, String name, String imageUrl, int stockQuantity) {
    }

    /** Current calendar month's revenue vs the previous calendar month, with % change. */
    public record MonthlyRevenue(BigDecimal current, BigDecimal previous, Double changePct) {
    }
}
