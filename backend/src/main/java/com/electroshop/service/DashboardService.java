package com.electroshop.service;

import com.electroshop.dto.DashboardStatsDto;
import com.electroshop.model.Product;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    /** How many trailing days each stat card's sparkline covers. */
    private static final int TREND_DAYS = 14;

    /** How many products the "Top produse vândute" panel shows. */
    private static final int TOP_PRODUCTS_LIMIT = 5;

    /** Stock at or below this is called out on the "Stoc scăzut" panel. */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public DashboardService(UserRepository userRepository, ProductRepository productRepository,
                            OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto getStats() {
        long totalUsers = userRepository.count();
        long totalProducts = productRepository.count();
        long totalOrders = orderRepository.count();
        BigDecimal revenue = orderRepository.calculateTotalRevenue();
        if (revenue == null) {
            revenue = BigDecimal.ZERO;
        }

        List<DashboardStatsDto.StatusCount> byStatus = new ArrayList<>();
        for (Object[] row : orderRepository.countByStatus()) {
            byStatus.add(new DashboardStatsDto.StatusCount(row[0].toString(), ((Number) row[1]).longValue()));
        }

        List<Object[]> salesRows = orderRepository.findSalesByDay();
        List<DashboardStatsDto.SalesPoint> salesByDay = new ArrayList<>();
        for (Object[] row : salesRows) {
            salesByDay.add(new DashboardStatsDto.SalesPoint(
                    row[0].toString(),
                    row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString())));
        }

        DashboardStatsDto.Trend usersTrend = buildTrend(userRepository.countSignupsByDay());
        DashboardStatsDto.Trend productsTrend = buildTrend(productRepository.countCreatedByDay());
        DashboardStatsDto.Trend ordersTrend = buildTrend(orderRepository.countOrdersByDay());
        DashboardStatsDto.Trend revenueTrend = buildTrend(salesRows);

        List<DashboardStatsDto.TopProduct> topProducts = buildTopProducts();

        List<DashboardStatsDto.LowStockProduct> lowStock = new ArrayList<>();
        for (Product p : productRepository.findTop5ByStockQuantityLessThanEqualOrderByStockQuantityAsc(LOW_STOCK_THRESHOLD)) {
            lowStock.add(new DashboardStatsDto.LowStockProduct(
                    p.getId(), p.getName(), p.getImageUrl(), p.getStockQuantity()));
        }

        return new DashboardStatsDto(totalUsers, totalProducts, totalOrders, revenue,
                usersTrend, productsTrend, ordersTrend, revenueTrend,
                byStatus, topProducts, salesByDay, lowStock);
    }

    /**
     * The top sellers together with, per product, a 7-day units-sold sparkline and
     * the percentage change of the last 7 days over the 7 before that. A single
     * extra query (grouped by product + day) covers every top product at once
     * rather than one query per row.
     */
    private List<DashboardStatsDto.TopProduct> buildTopProducts() {
        List<Object[]> topRows = orderRepository.findTopProducts(PageRequest.of(0, TOP_PRODUCTS_LIMIT));
        if (topRows.isEmpty()) {
            return List.of();
        }

        List<Long> ids = new ArrayList<>();
        for (Object[] row : topRows) {
            ids.add(((Number) row[0]).longValue());
        }

        LocalDate start = LocalDate.now().minusDays(TREND_DAYS - 1);
        Map<Long, double[]> perProductDaily = new HashMap<>();
        for (Long id : ids) {
            perProductDaily.put(id, new double[TREND_DAYS]);
        }
        for (Object[] row : orderRepository.findDailyUnitsForProducts(ids, start.atStartOfDay())) {
            Long productId = ((Number) row[0]).longValue();
            LocalDate day = LocalDate.parse(row[1].toString());
            int idx = (int) ChronoUnit.DAYS.between(start, day);
            double[] series = perProductDaily.get(productId);
            if (series != null && idx >= 0 && idx < TREND_DAYS) {
                series[idx] = ((Number) row[2]).doubleValue();
            }
        }

        List<DashboardStatsDto.TopProduct> result = new ArrayList<>();
        for (Object[] row : topRows) {
            Long id = ((Number) row[0]).longValue();
            String name = (String) row[1];
            String imageUrl = (String) row[2];
            long unitsSold = ((Number) row[3]).longValue();
            BigDecimal itemRevenue = (BigDecimal) row[4];

            double[] series = perProductDaily.getOrDefault(id, new double[TREND_DAYS]);
            double last7 = 0;
            double prev7 = 0;
            List<Long> dailyUnits = new ArrayList<>();
            for (int i = 0; i < TREND_DAYS; i++) {
                if (i >= TREND_DAYS - 7) {
                    last7 += series[i];
                    dailyUnits.add(Math.round(series[i]));
                } else {
                    prev7 += series[i];
                }
            }
            Double trendPct = changePct(last7, prev7);

            result.add(new DashboardStatsDto.TopProduct(id, name, imageUrl, unitsSold, itemRevenue, trendPct, dailyUnits));
        }
        return result;
    }

    /** Turns sparse {@code {date, value}} rows into a complete, zero-filled trailing daily series. */
    private DashboardStatsDto.Trend buildTrend(List<Object[]> rows) {
        LocalDate start = LocalDate.now().minusDays(TREND_DAYS - 1);
        double[] series = new double[TREND_DAYS];
        for (Object[] row : rows) {
            LocalDate day = LocalDate.parse(row[0].toString());
            int idx = (int) ChronoUnit.DAYS.between(start, day);
            if (idx >= 0 && idx < TREND_DAYS) {
                series[idx] += row[1] == null ? 0.0 : ((Number) row[1]).doubleValue();
            }
        }

        List<Double> seriesList = new ArrayList<>();
        double last7 = 0;
        double prev7 = 0;
        for (int i = 0; i < TREND_DAYS; i++) {
            seriesList.add(series[i]);
            if (i >= TREND_DAYS - 7) {
                last7 += series[i];
            } else {
                prev7 += series[i];
            }
        }
        return new DashboardStatsDto.Trend(seriesList, changePct(last7, prev7));
    }

    private static Double changePct(double last7, double prev7) {
        double pct = prev7 == 0 ? (last7 == 0 ? 0.0 : 100.0) : ((last7 - prev7) / prev7) * 100.0;
        return Math.round(pct * 10.0) / 10.0;
    }
}
