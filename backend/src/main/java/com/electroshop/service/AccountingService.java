package com.electroshop.service;

import com.electroshop.dto.AccountingReportDto;
import com.electroshop.model.Order;
import com.electroshop.model.OrderStatus;
import com.electroshop.model.Purchase;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.PurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountingService {

    private final OrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;

    public AccountingService(OrderRepository orderRepository, PurchaseRepository purchaseRepository) {
        this.orderRepository = orderRepository;
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional(readOnly = true)
    public AccountingReportDto getReport(LocalDate from, LocalDate to) {
        if (from == null) {
            from = LocalDate.now().withDayOfMonth(1);
        }
        if (to == null) {
            to = LocalDate.now();
        }
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        // ---- Sales (revenue) from non-cancelled orders in range ----
        Map<LocalDate, BigDecimal> salesByDay = new LinkedHashMap<>();
        BigDecimal salesTotal = BigDecimal.ZERO;
        long salesCount = 0;
        for (Order o : orderRepository.findAll()) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getCreatedAt() == null) {
                continue;
            }
            LocalDate d = o.getCreatedAt().toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            BigDecimal amt = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            salesTotal = salesTotal.add(amt);
            salesCount++;
            salesByDay.merge(d, amt, BigDecimal::add);
        }

        // ---- Purchases (expenses) in range ----
        Map<LocalDate, BigDecimal> purchasesByDay = new LinkedHashMap<>();
        BigDecimal purchasesTotal = BigDecimal.ZERO;
        long purchasesCount = 0;
        List<Purchase> purchases = purchaseRepository.findByPurchaseDateBetween(from, to);
        for (Purchase p : purchases) {
            BigDecimal amt = p.getTotalAmount() == null ? BigDecimal.ZERO : p.getTotalAmount();
            purchasesTotal = purchasesTotal.add(amt);
            purchasesCount++;
            purchasesByDay.merge(p.getPurchaseDate(), amt, BigDecimal::add);
        }

        BigDecimal profit = salesTotal.subtract(purchasesTotal);
        BigDecimal margin = salesTotal.compareTo(BigDecimal.ZERO) > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(salesTotal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ---- Daily breakdown across the whole range ----
        List<AccountingReportDto.DailyPoint> byDay = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.add(new AccountingReportDto.DailyPoint(
                    d.toString(),
                    salesByDay.getOrDefault(d, BigDecimal.ZERO),
                    purchasesByDay.getOrDefault(d, BigDecimal.ZERO)));
        }

        return new AccountingReportDto(from, to, salesTotal, salesCount,
                purchasesTotal, purchasesCount, profit, margin, byDay);
    }
}
