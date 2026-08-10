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

    // ---- Dashboard analytics (tasks 9, 14, 15, 16) -----------------------
    //
    // Cancelled orders are excluded from every revenue figure below. A
    // cancelled order produced no money, and including it would credit the
    // business with revenue it never received. Returned orders ARE included:
    // the sale happened, the money moved, and a return is a separate cost that
    // the efficiency report accounts for on its own terms — netting it out of
    // revenue here would hide it from both reports at once.
    //
    // Dates are grouped by numeric year/month/day rather than by DATE() or
    // DATE_FORMAT(). Those are dialect vocabulary; the test suite runs on H2
    // and production runs on MySQL, so a query written in either dialect's
    // terms passes review and fails on the other.

    /** Revenue booked inside a window, cancelled orders excluded. */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND o.createdAt >= :from
              AND o.createdAt < :to
            """)
    BigDecimal sumRevenueBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                 @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** Orders placed inside a window, whatever their outcome. */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :from AND o.createdAt < :to")
    long countPlacedBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                            @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** Orders placed inside a window that ended in a given status. */
    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt < :to
            """)
    long countByStatusBetween(@org.springframework.data.repository.query.Param("status") OrderStatus status,
                              @org.springframework.data.repository.query.Param("from") LocalDateTime from,
                              @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** How orders inside a window ended: {@code [status, count]}. */
    @Query("""
            SELECT o.status, COUNT(o) FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.status
            """)
    List<Object[]> countByStatusInWindow(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                         @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** Revenue per day inside a window: {@code [year, month, day, revenue, orders]}. */
    @Query("""
            SELECT YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt),
                   COALESCE(SUM(o.totalAmount), 0), COUNT(o)
            FROM Order o
            WHERE o.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND o.createdAt >= :from
              AND o.createdAt < :to
            GROUP BY YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt)
            ORDER BY YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt)
            """)
    List<Object[]> revenueByDayBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                       @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** Revenue per month inside a window: {@code [year, month, revenue, orders]}. */
    @Query("""
            SELECT YEAR(o.createdAt), MONTH(o.createdAt),
                   COALESCE(SUM(o.totalAmount), 0), COUNT(o)
            FROM Order o
            WHERE o.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND o.createdAt >= :from
              AND o.createdAt < :to
            GROUP BY YEAR(o.createdAt), MONTH(o.createdAt)
            ORDER BY YEAR(o.createdAt), MONTH(o.createdAt)
            """)
    List<Object[]> revenueByMonthBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                         @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** Orders per hour of day inside a window: {@code [hour, count, revenue]}. */
    @Query("""
            SELECT HOUR(o.createdAt), COUNT(o), COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND o.createdAt >= :from
              AND o.createdAt < :to
            GROUP BY HOUR(o.createdAt)
            ORDER BY HOUR(o.createdAt)
            """)
    List<Object[]> countByHourBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                      @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /**
     * Per-customer totals inside a window: {@code [userId, orders, revenue, firstInWindow, lastInWindow]}.
     *
     * <p>Backs the customer segments, the top-customer table and the average basket.
     * Guest orders — those with no account — are excluded, because "customer" here
     * means an identity that can be counted as new or returning, and an anonymous
     * order cannot be either.</p>
     */
    @Query("""
            SELECT o.user.id, COUNT(o), COALESCE(SUM(o.totalAmount), 0),
                   MIN(o.createdAt), MAX(o.createdAt)
            FROM Order o
            WHERE o.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND o.createdAt >= :from
              AND o.createdAt < :to
              AND o.user IS NOT NULL
            GROUP BY o.user.id
            """)
    List<Object[]> customerTotalsBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                         @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /**
     * Each customer's first ever order date: {@code [userId, firstOrderAt]}.
     *
     * <p>Deliberately unbounded by any window. "New customer" has to be judged against
     * all of history: a buyer from last year who returns this month is a returning
     * customer, and deciding that from inside a seven-day window would relabel the
     * entire loyal base as new every time somebody narrows the range — reporting the
     * healthiest possible acquisition figures for a business acquiring nobody.</p>
     */
    @Query("""
            SELECT o.user.id, MIN(o.createdAt) FROM Order o
            WHERE o.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND o.user IS NOT NULL
              AND o.user.id IN :userIds
            GROUP BY o.user.id
            """)
    List<Object[]> firstOrderDates(@org.springframework.data.repository.query.Param("userIds") List<Long> userIds);

    /**
     * Orders in a window, newest first, for the efficiency detail table.
     *
     * <p><b>The customer is fetched with the order, deliberately.</b> {@code Order.user}
     * is mapped {@code LAZY}, and the detail table prints the customer's e-mail on every
     * row. Without the join the rows come back holding uninitialised proxies; the
     * service then reads them after the repository call has already closed its session
     * and the panel fails outright with <em>could not initialize proxy — no Session</em>.
     * Even inside an open session the plain query would be wrong in a quieter way: fifty
     * rows would issue fifty extra selects, one per customer.</p>
     *
     * <p>A {@code LEFT} join rather than an inner one. The column is {@code NOT NULL} in
     * the mapping, but an inner join would silently drop any row where that invariant is
     * not met in the data, and a detail table that omits orders without saying so is
     * worse than one that prints a dash.</p>
     */
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.user
            WHERE o.createdAt >= :from AND o.createdAt < :to
            ORDER BY o.createdAt DESC
            """)
    List<Order> findPlacedBetween(@org.springframework.data.repository.query.Param("from") LocalDateTime from,
                                  @org.springframework.data.repository.query.Param("to") LocalDateTime to,
                                  Pageable pageable);

    /** The earliest order on record, so a report can state how far its data reaches. */
    @Query("SELECT MIN(o.createdAt) FROM Order o")
    LocalDateTime earliestOrder();

    /**
     * Global search over the customer's email, name and the shipping address.
     *
     * <p>Newest first, because an operator searching a customer almost always wants the
     * order they are currently being asked about rather than one from last year.</p>
     */
    @Query("""
            SELECT o FROM Order o
            WHERE (o.user IS NOT NULL AND (
                     LOWER(o.user.email) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(o.user.fullName) LIKE LOWER(CONCAT('%', :q, '%'))))
               OR LOWER(o.shippingAddress) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(o.invoiceSeries) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY o.createdAt DESC
            """)
    List<Order> searchForGlobal(@org.springframework.data.repository.query.Param("q") String q,
                                Pageable pageable);
}
