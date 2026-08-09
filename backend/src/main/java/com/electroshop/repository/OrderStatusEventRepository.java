package com.electroshop.repository;

import com.electroshop.model.OrderStatus;
import com.electroshop.model.OrderStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read access to the order status history.
 *
 * <p>Every query here aggregates in the database and returns projections rather than
 * entities. The efficiency report covers every order in a window, and materialising
 * that many {@link OrderStatusEvent} objects to compute four averages would spend the
 * whole request budget on garbage collection.</p>
 */
public interface OrderStatusEventRepository extends JpaRepository<OrderStatusEvent, Long> {

    /**
     * The first moment each order entered a given status, within a window.
     *
     * <p>Returns {@code [orderRef, earliest timestamp]} rows. The earliest occurrence
     * is the one that matters: an order that is shipped, returned and shipped again
     * was first shipped once, and averaging the second shipment into "time to ship"
     * would report the exception as if it were the process.</p>
     */
    @Query("""
            SELECT e.orderRef, MIN(e.createdAt)
            FROM OrderStatusEvent e
            WHERE e.toStatus = :status
              AND e.createdAt >= :from
              AND e.createdAt < :to
            GROUP BY e.orderRef
            """)
    List<Object[]> firstEntryPerOrder(@Param("status") OrderStatus status,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /**
     * How many distinct orders reached a given status inside a window.
     *
     * <p>Distinct on purpose. An order bounced between two statuses several times
     * still counts once toward a rate, otherwise a single troublesome order can push
     * a cancellation rate above 100%.</p>
     */
    @Query("""
            SELECT COUNT(DISTINCT e.orderRef)
            FROM OrderStatusEvent e
            WHERE e.toStatus = :status
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    long countOrdersReaching(@Param("status") OrderStatus status,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    /**
     * Daily counts of orders reaching a status, for the comparison charts.
     *
     * <p>Returns {@code [year, month, day, count]}.</p>
     *
     * <p>Grouped by numeric date parts rather than by a database date-formatting
     * function. {@code DATE()} and {@code DATE_FORMAT()} are dialect vocabulary and
     * the test suite runs on H2 while production runs on MySQL; HQL's {@code YEAR()},
     * {@code MONTH()} and {@code DAY()} are portable and mean the same thing on both.
     * The caller assembles the label, so there is exactly one place where a date
     * becomes a string.</p>
     */
    @Query("""
            SELECT YEAR(e.createdAt), MONTH(e.createdAt), DAY(e.createdAt),
                   COUNT(DISTINCT e.orderRef)
            FROM OrderStatusEvent e
            WHERE e.toStatus = :status
              AND e.createdAt >= :from
              AND e.createdAt < :to
            GROUP BY YEAR(e.createdAt), MONTH(e.createdAt), DAY(e.createdAt)
            ORDER BY YEAR(e.createdAt), MONTH(e.createdAt), DAY(e.createdAt)
            """)
    List<Object[]> dailyCountsReaching(@Param("status") OrderStatus status,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /** Full history of one order, oldest first, for the per-order detail row. */
    List<OrderStatusEvent> findByOrderRefOrderByCreatedAtAsc(Long orderRef);

    /** History of several orders at once, so the detail table loads in one query. */
    List<OrderStatusEvent> findByOrderRefInOrderByOrderRefAscCreatedAtAsc(List<Long> orderRefs);

    /**
     * Reasons given for transitions into a status, most frequent first.
     *
     * <p>Returns {@code [reason, count]}. Rows with no reason are excluded rather than
     * grouped under an empty label, because "no reason recorded" is a data-entry fact
     * and not a category of return.</p>
     */
    @Query("""
            SELECT e.reason, COUNT(e)
            FROM OrderStatusEvent e
            WHERE e.toStatus = :status
              AND e.reason IS NOT NULL
              AND e.createdAt >= :from
              AND e.createdAt < :to
            GROUP BY e.reason
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> reasonBreakdown(@Param("status") OrderStatus status,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    /**
     * The earliest event on record.
     *
     * <p>The panel states when collection began. Without this the first weeks would
     * read as a period of perfect performance rather than as a period with no data.</p>
     */
    @Query("SELECT MIN(e.createdAt) FROM OrderStatusEvent e")
    LocalDateTime earliestEvent();
}
