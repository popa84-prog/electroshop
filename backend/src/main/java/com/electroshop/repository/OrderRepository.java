package com.electroshop.repository;

import com.electroshop.model.Order;
import com.electroshop.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status <> 'CANCELLED'")
    BigDecimal calculateTotalRevenue();

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query("""
            SELECT oi.product.id, oi.product.name, oi.product.imageUrl, SUM(oi.quantity),
                   SUM(oi.unitPrice * oi.quantity)
            FROM OrderItem oi
            WHERE oi.order.status <> 'CANCELLED'
            GROUP BY oi.product.id, oi.product.name, oi.product.imageUrl
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

    /** All orders (any status) per calendar day — feeds the "Comenzi" stat-card trend. */
    @Query(value = """
            SELECT DATE(o.created_at) AS d, COUNT(*)
            FROM orders o
            GROUP BY DATE(o.created_at)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countOrdersByDay();

    /**
     * Units sold per product per day, for the given product ids from {@code from} onward.
     * Backs each top-product row's 7-day sparkline and its trend percentage.
     */
    @Query(value = """
            SELECT oi.product_id, DATE(o.created_at) AS d, SUM(oi.quantity)
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.status <> 'CANCELLED'
              AND oi.product_id IN (:productIds)
              AND o.created_at >= :from
            GROUP BY oi.product_id, DATE(o.created_at)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> findDailyUnitsForProducts(@org.springframework.data.repository.query.Param("productIds") List<Long> productIds,
                                              @org.springframework.data.repository.query.Param("from") LocalDateTime from);
}
