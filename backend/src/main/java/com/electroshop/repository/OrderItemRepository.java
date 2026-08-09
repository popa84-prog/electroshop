package com.electroshop.repository;

import com.electroshop.model.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Whether any order — past or present — ever included this product.
     * Checked before a hard delete: an order row's {@code product_id} foreign
     * key must never dangle, so a product with sales history cannot be
     * physically removed without corrupting every invoice that references it.
     */
    boolean existsByProductId(Long productId);

    /**
     * Every order line that ever referenced this product. Used only by the
     * explicit, irreversible force-delete path
     * ({@code ProductService#forceDeleteWithHistory}) — each returned item has
     * its {@code product} link set to {@code null} and is otherwise left
     * untouched (quantity, prices, and the {@code productName} snapshot all
     * survive), so the accounting trail stays intact even after the product
     * row itself is gone.
     */
    List<OrderItem> findByProductId(Long productId);

    // ---- Realised profit (tasks 12, 14, 18) ------------------------------
    //
    // Every figure below comes from unitPrice and costPrice as they were
    // captured at the moment of sale. That is what the business actually
    // earned. Computing profit from today's product prices instead would
    // rewrite history every time somebody edits a price, and a report that
    // changes retroactively is not a report.
    //
    // Cancelled orders are excluded everywhere. A cancelled order produced no
    // revenue and no profit, and including it would credit the business with
    // money it never received.
    //
    // Lines with no costPrice are excluded from profit but still counted, so
    // the caller can disclose how much of the window it could not see. A
    // missing cost silently treated as zero would report the full selling
    // price as profit — the single most flattering possible error.

    /**
     * Revenue and profit inside a window: {@code [revenue, profit, units, lines]}.
     *
     * <p>One query rather than four, so the four numbers describe the same set of
     * rows. Separate queries would let a concurrent order land between them and
     * produce a revenue and a profit that do not reconcile.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0),
                   COUNT(i)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
            """)
    List<Object[]> totalsInWindow(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /** Order lines in a window with no recorded cost, excluded from every profit figure. */
    @Query("""
            SELECT COUNT(i) FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NULL
            """)
    long countWithoutCostInWindow(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /**
     * Revenue and profit per product category inside a window.
     *
     * <p>Returns {@code [category, revenue, profit, units]}. The category is read from
     * the product rather than copied onto the line, so re-categorising a product
     * moves its whole history — which is the behaviour a category report wants: the
     * question "how does Audio perform" means today's definition of Audio.</p>
     */
    @Query("""
            SELECT COALESCE(i.product.category, 'Fără categorie'),
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
              AND i.product IS NOT NULL
            GROUP BY COALESCE(i.product.category, 'Fără categorie')
            ORDER BY SUM((i.unitPrice - i.costPrice) * i.quantity) DESC
            """)
    List<Object[]> profitByCategory(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /** Revenue and profit per brand inside a window: {@code [brand, revenue, profit, units]}. */
    @Query("""
            SELECT COALESCE(i.product.brand, 'Fără brand'),
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
              AND i.product IS NOT NULL
            GROUP BY COALESCE(i.product.brand, 'Fără brand')
            ORDER BY SUM((i.unitPrice - i.costPrice) * i.quantity) DESC
            """)
    List<Object[]> profitByBrand(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /**
     * The most profitable products in a window.
     *
     * <p>Returns {@code [productId, name, imageUrl, brand, category, revenue, profit,
     * units]}. The name comes from the product where it still exists; the line's own
     * {@code productName} snapshot is the fallback for products that were deleted, so
     * a report covering last quarter does not develop blank rows when someone tidies
     * the catalogue.</p>
     */
    @Query("""
            SELECT i.product.id,
                   COALESCE(i.product.name, i.productName),
                   i.product.imageUrl,
                   i.product.brand,
                   i.product.category,
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
              AND i.product IS NOT NULL
            GROUP BY i.product.id, i.product.name, i.productName,
                     i.product.imageUrl, i.product.brand, i.product.category
            ORDER BY SUM((i.unitPrice - i.costPrice) * i.quantity) DESC
            """)
    List<Object[]> topProductsByProfit(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       Pageable pageable);

    /** The same shape ordered by revenue, for the second ranking on the top-products card. */
    @Query("""
            SELECT i.product.id,
                   COALESCE(i.product.name, i.productName),
                   i.product.imageUrl,
                   i.product.brand,
                   i.product.category,
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.product IS NOT NULL
            GROUP BY i.product.id, i.product.name, i.productName,
                     i.product.imageUrl, i.product.brand, i.product.category
            ORDER BY SUM(i.unitPrice * i.quantity) DESC
            """)
    List<Object[]> topProductsByRevenue(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        Pageable pageable);

    /** The same shape ordered by units sold, for the third ranking. */
    @Query("""
            SELECT i.product.id,
                   COALESCE(i.product.name, i.productName),
                   i.product.imageUrl,
                   i.product.brand,
                   i.product.category,
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.product IS NOT NULL
            GROUP BY i.product.id, i.product.name, i.productName,
                     i.product.imageUrl, i.product.brand, i.product.category
            ORDER BY SUM(i.quantity) DESC
            """)
    List<Object[]> topProductsByUnits(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);

    /**
     * Units sold per product inside a window: {@code [productId, units]}.
     *
     * <p>Backs stock velocity, days of cover and the rising/declining comparison.
     * Deliberately unpaged: the caller needs every product that moved, and capping it
     * would silently exclude the tail of the catalogue from the very analysis that is
     * looking for products that stopped selling.</p>
     */
    @Query("""
            SELECT i.product.id, COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.product IS NOT NULL
            GROUP BY i.product.id
            """)
    List<Object[]> unitsSoldPerProduct(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /**
     * Revenue and profit per product inside a window: {@code [productId, revenue, profit]}.
     *
     * <p>Unpaged for the same reason as {@link #unitsSoldPerProduct}: the performance
     * report needs every product that sold, and the alternative — reusing
     * {@code topProductsByProfit} with an enormous page size — asks the database to
     * sort the whole result set for an ordering nobody reads. One row per product that
     * moved is a bounded amount of data; sorting it is not.</p>
     */
    @Query("""
            SELECT i.product.id,
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
              AND i.product IS NOT NULL
            GROUP BY i.product.id
            """)
    List<Object[]> moneyPerProduct(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    /**
     * Daily units for a specific set of products: {@code [productId, year, month, day, units]}.
     *
     * <p>Backs the per-row sparklines on the top-products card. Restricted to the ids
     * actually being displayed, because the sparkline is a decoration on thirty rows
     * and pulling daily history for the whole catalogue to draw it would cost far more
     * than the card is worth.</p>
     */
    @Query("""
            SELECT i.product.id,
                   YEAR(i.order.createdAt), MONTH(i.order.createdAt), DAY(i.order.createdAt),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.product.id IN :productIds
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
            GROUP BY i.product.id,
                     YEAR(i.order.createdAt), MONTH(i.order.createdAt), DAY(i.order.createdAt)
            ORDER BY i.product.id,
                     YEAR(i.order.createdAt), MONTH(i.order.createdAt), DAY(i.order.createdAt)
            """)
    List<Object[]> unitsSoldPerProductPerDay(@Param("productIds") List<Long> productIds,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * Revenue, profit and cost of goods sold per calendar month.
     *
     * <p>Returns {@code [year, month, revenue, profit, cogs]}.</p>
     *
     * <p>Grouped by the numeric year and month rather than by a formatted string.
     * {@code DATE_FORMAT} is MySQL vocabulary and the test suite runs on H2, so a
     * query written that way passes review and fails the moment it is tested. HQL's
     * {@code YEAR()} and {@code MONTH()} are portable, and the caller assembles the
     * {@code yyyy-MM} label from the two integers — which also removes any chance of
     * the label and the grouping key disagreeing, since only one of them exists in
     * the database.</p>
     */
    @Query("""
            SELECT YEAR(i.order.createdAt), MONTH(i.order.createdAt),
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.costPrice * i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
            GROUP BY YEAR(i.order.createdAt), MONTH(i.order.createdAt)
            ORDER BY YEAR(i.order.createdAt), MONTH(i.order.createdAt)
            """)
    List<Object[]> monthlyTotals(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /**
     * The same totals per day, for windows shorter than a few months.
     *
     * <p>Returns {@code [year, month, day, revenue, profit, cogs]}.</p>
     */
    @Query("""
            SELECT YEAR(i.order.createdAt), MONTH(i.order.createdAt), DAY(i.order.createdAt),
                   COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.costPrice * i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.costPrice IS NOT NULL
            GROUP BY YEAR(i.order.createdAt), MONTH(i.order.createdAt), DAY(i.order.createdAt)
            ORDER BY YEAR(i.order.createdAt), MONTH(i.order.createdAt), DAY(i.order.createdAt)
            """)
    List<Object[]> dailyTotals(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * Line count per order in a window: {@code [orderId, lines]}.
     *
     * <p>The average is computed by the caller rather than by the database. A JPQL
     * subquery in the {@code FROM} clause is a Hibernate 6 feature that behaves
     * differently across dialects, and averaging a few thousand integers in Java is
     * not the cost worth taking that risk for.</p>
     */
    @Query("""
            SELECT i.order.id, COUNT(i)
            FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
            GROUP BY i.order.id
            """)
    List<Object[]> linesPerOrder(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    /** Distinct categories that actually sold in a window, for the filter dropdown. */
    @Query("""
            SELECT DISTINCT i.product.category FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.product.category IS NOT NULL
            ORDER BY i.product.category
            """)
    List<String> soldCategories(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /** Distinct brands that actually sold in a window, for the filter dropdown. */
    @Query("""
            SELECT DISTINCT i.product.brand FROM OrderItem i
            WHERE i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
              AND i.product.brand IS NOT NULL
            ORDER BY i.product.brand
            """)
    List<String> soldBrands(@Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /** Total revenue and profit for one product in a window: {@code [revenue, profit, units]}. */
    @Query("""
            SELECT COALESCE(SUM(i.unitPrice * i.quantity), 0),
                   COALESCE(SUM((i.unitPrice - i.costPrice) * i.quantity), 0),
                   COALESCE(SUM(i.quantity), 0)
            FROM OrderItem i
            WHERE i.product.id = :productId
              AND i.order.status <> com.electroshop.model.OrderStatus.CANCELLED
              AND i.order.createdAt >= :from
              AND i.order.createdAt < :to
            """)
    List<Object[]> totalsForProduct(@Param("productId") Long productId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * Total revenue attributed to a set of orders, for campaign conversion values.
     */
    @Query("""
            SELECT COALESCE(SUM(i.unitPrice * i.quantity), 0)
            FROM OrderItem i
            WHERE i.order.id IN :orderIds
            """)
    BigDecimal revenueForOrders(@Param("orderIds") List<Long> orderIds);
}
