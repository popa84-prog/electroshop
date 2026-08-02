package com.electroshop.service;

import com.electroshop.dto.AccountingReportDto;
import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.OrderStatus;
import com.electroshop.model.Product;
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

        // ---- Sales (revenue) + real gross margin (COGS) from non-cancelled orders in range ----
        // For every item on every qualifying order, the profit contribution is
        // (unitPrice - acquisitionCost) * quantity — exactly the convention already
        // used for the per-product profit column on the admin Products table.
        // acquisitionCost prefers the OrderItem's own costPrice snapshot (the
        // product's purchase price AT THE MOMENT OF SALE); it falls back to the
        // product's CURRENT purchase price only for historical order items
        // recorded before that snapshot column existed. If neither is known the
        // item is excluded from cogsTotal and counted in itemsWithUnknownCost
        // instead of being silently treated as zero cost.
        Map<LocalDate, BigDecimal> salesByDay = new LinkedHashMap<>();
        BigDecimal salesTotal = BigDecimal.ZERO;
        BigDecimal cogsTotal = BigDecimal.ZERO;
        long salesCount = 0;
        long itemsWithUnknownCost = 0;
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

            for (OrderItem item : o.getItems()) {
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                if (qty <= 0) {
                    continue;
                }
                BigDecimal acquisitionCost = item.getCostPrice();
                if (acquisitionCost == null) {
                    Product product = item.getProduct();
                    acquisitionCost = product != null ? product.getPurchasePrice() : null;
                }
                if (acquisitionCost == null) {
                    itemsWithUnknownCost += qty;
                    continue;
                }
                cogsTotal = cogsTotal.add(acquisitionCost.multiply(BigDecimal.valueOf(qty)));
            }
        }

        // ---- Supplier purchases (cash-basis, informational only — see DTO javadoc) ----
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

        // ---- Real gross margin: sale price minus acquisition price of items actually sold ----
        BigDecimal profit = salesTotal.subtract(cogsTotal);
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
                purchasesTotal, purchasesCount, cogsTotal, itemsWithUnknownCost, profit, margin, byDay);
    }
}
