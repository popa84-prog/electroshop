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
        List<LowStockProduct> lowStockProducts
) {
    public record StatusCount(String status, long count) {
    }

    public record TopProduct(Long productId, String name, String imageUrl, long unitsSold,
                              BigDecimal revenue, Double trendPct, List<Long> dailyUnits) {
    }

    public record SalesPoint(String date, BigDecimal amount) {
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
}
