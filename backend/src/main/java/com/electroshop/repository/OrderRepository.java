package com.electroshop.repository;

import com.electroshop.model.Order;
import com.electroshop.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status <> 'CANCELLED'")
    BigDecimal calculateTotalRevenue();

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query("""
            SELECT oi.product.id, oi.product.name, SUM(oi.quantity),
                   SUM(oi.unitPrice * oi.quantity)
            FROM OrderItem oi
            WHERE oi.order.status <> 'CANCELLED'
            GROUP BY oi.product.id, oi.product.name
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> findTopProducts(Pageable pageable);

    @Query(value = """
            SELECT DATE(o.created_at) AS d, COALESCE(SUM(o.total_amount), 0)
            FROM orders o
            WHERE o.status <> 'CANCELLED'
            GROUP BY DATE(o.created_at)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> findSalesByDay();
}
