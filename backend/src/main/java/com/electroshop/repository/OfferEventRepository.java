package com.electroshop.repository;

import com.electroshop.model.OfferEvent;
import com.electroshop.model.OfferEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read access to the offer interaction funnel.
 *
 * <p>Every campaign metric is a ratio over counts from this table. The queries return
 * the counts already grouped, so the service divides numbers rather than iterating
 * rows.</p>
 */
public interface OfferEventRepository extends JpaRepository<OfferEvent, Long> {

    /**
     * Funnel totals per offer inside a window.
     *
     * <p>Returns {@code [offerRef, type, count, summed order value]}. One query
     * produces every number the campaign table needs — impressions, clicks,
     * conversions and attributed revenue — instead of three round trips per campaign.
     * The summed value is null for impression and click rows, which carry no revenue.</p>
     */
    @Query("""
            SELECT e.offerRef, e.type, COUNT(e), SUM(e.orderValue)
            FROM OfferEvent e
            WHERE e.createdAt >= :from
              AND e.createdAt < :to
            GROUP BY e.offerRef, e.type
            """)
    List<Object[]> funnelTotals(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * Daily counts of one event type across all offers, for the evolution chart.
     *
     * <p>Returns {@code [year, month, day, count]}. Numeric date parts rather than a
     * dialect-specific formatting function, so the query means the same thing on H2
     * in tests and on MySQL in production.</p>
     */
    @Query("""
            SELECT YEAR(e.createdAt), MONTH(e.createdAt), DAY(e.createdAt), COUNT(e)
            FROM OfferEvent e
            WHERE e.type = :type
              AND e.createdAt >= :from
              AND e.createdAt < :to
            GROUP BY YEAR(e.createdAt), MONTH(e.createdAt), DAY(e.createdAt)
            ORDER BY YEAR(e.createdAt), MONTH(e.createdAt), DAY(e.createdAt)
            """)
    List<Object[]> dailyCounts(@Param("type") OfferEventType type,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /** Total events of one type for one offer inside a window. */
    @Query("""
            SELECT COUNT(e)
            FROM OfferEvent e
            WHERE e.offerRef = :offerRef
              AND e.type = :type
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    long countForOffer(@Param("offerRef") Long offerRef,
                       @Param("type") OfferEventType type,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);

    /** Revenue attributed to one offer inside a window; null when it converted nothing. */
    @Query("""
            SELECT SUM(e.orderValue)
            FROM OfferEvent e
            WHERE e.offerRef = :offerRef
              AND e.type = com.electroshop.model.OfferEventType.CONVERSION
              AND e.createdAt >= :from
              AND e.createdAt < :to
            """)
    BigDecimal revenueForOffer(@Param("offerRef") Long offerRef,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * Whether this session already produced this event for this offer today.
     *
     * <p>Guards impression de-duplication: a visitor who reloads a page five times has
     * seen the offer, and counting five impressions would depress every rate whose
     * denominator they form.</p>
     */
    @Query("""
            SELECT COUNT(e) > 0
            FROM OfferEvent e
            WHERE e.offerRef = :offerRef
              AND e.type = :type
              AND e.sessionHash = :sessionHash
              AND e.createdAt >= :since
            """)
    boolean existsRecent(@Param("offerRef") Long offerRef,
                         @Param("type") OfferEventType type,
                         @Param("sessionHash") String sessionHash,
                         @Param("since") LocalDateTime since);

    /**
     * The most recent click by a session, used for last-click conversion attribution.
     *
     * <p>Ordered newest first; the caller takes the head. A visitor who clicked three
     * campaigns before buying is credited to the one that actually preceded the
     * purchase.</p>
     */
    @Query("""
            SELECT e
            FROM OfferEvent e
            WHERE e.sessionHash = :sessionHash
              AND e.type = com.electroshop.model.OfferEventType.CLICK
              AND e.createdAt >= :since
            ORDER BY e.createdAt DESC
            """)
    List<OfferEvent> recentClicksBySession(@Param("sessionHash") String sessionHash,
                                           @Param("since") LocalDateTime since);

    /** The earliest event on record, so the panel can state when collection began. */
    @Query("SELECT MIN(e.createdAt) FROM OfferEvent e")
    LocalDateTime earliestEvent();
}
