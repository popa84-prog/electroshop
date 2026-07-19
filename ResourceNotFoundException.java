package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsDto(
        long totalUsers,
        long totalProducts,
        long totalOrders,
        BigDecimal totalRevenue,
        List<StatusCount> ordersByStatus,
        List<TopProduct> topProducts,
        List<SalesPoint> salesByDay
) {
    public record StatusCount(String status, long count) {
    }

    public record TopProduct(Long productId, String name, long unitsSold, BigDecimal revenue) {
    }

    public record SalesPoint(String date, BigDecimal amount) {
    }
}
