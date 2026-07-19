package com.electroshop.service;

import com.electroshop.dto.DashboardStatsDto;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

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

        List<DashboardStatsDto.StatusCount> byStatus = new ArrayList<>();
        for (Object[] row : orderRepository.countByStatus()) {
            byStatus.add(new DashboardStatsDto.StatusCount(row[0].toString(), ((Number) row[1]).longValue()));
        }

        List<DashboardStatsDto.TopProduct> topProducts = new ArrayList<>();
        for (Object[] row : orderRepository.findTopProducts(PageRequest.of(0, 5))) {
            topProducts.add(new DashboardStatsDto.TopProduct(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    (BigDecimal) row[3]));
        }

        List<DashboardStatsDto.SalesPoint> salesByDay = new ArrayList<>();
        for (Object[] row : orderRepository.findSalesByDay()) {
            salesByDay.add(new DashboardStatsDto.SalesPoint(
                    row[0].toString(),
                    row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString())));
        }

        return new DashboardStatsDto(totalUsers, totalProducts, totalOrders,
                revenue == null ? BigDecimal.ZERO : revenue,
                byStatus, topProducts, salesByDay);
    }
}
