package com.electroshop.repository;

import com.electroshop.model.SystemLogEntry;
import com.electroshop.model.SystemLogLevel;
import com.electroshop.model.SystemLogSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLogEntry, Long> {

    /**
     * The operational log table, with every filter optional.
     *
     * <p>Pass {@code null} to leave a dimension unfiltered. The text search covers the
     * message, the code and the context in one predicate, because an operator hunting
     * an incident knows a fragment of one of the three and does not know which.</p>
     *
     * <p>The search term is bound as a parameter, never concatenated, so a value
     * containing quotes or SQL keywords is data and cannot become syntax.</p>
     */
    @Query("""
            SELECT l FROM SystemLogEntry l
            WHERE (:source IS NULL OR l.source = :source)
              AND (:level IS NULL OR l.level = :level)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt < :to)
              AND (:q IS NULL
                   OR LOWER(l.message) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(l.code) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(l.context) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY l.createdAt DESC
            """)
    Page<SystemLogEntry> search(@Param("source") SystemLogSource source,
                                @Param("level") SystemLogLevel level,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to,
                                @Param("q") String q,
                                Pageable pageable);

    /**
     * The same filter without paging, for the CSV export.
     *
     * <p>A separate method rather than an unbounded page: the export caps its own row
     * count, and a caller that accidentally passes {@code Pageable.unpaged()} to the
     * paged query would stream the entire table into memory.</p>
     */
    @Query("""
            SELECT l FROM SystemLogEntry l
            WHERE (:source IS NULL OR l.source = :source)
              AND (:level IS NULL OR l.level = :level)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt < :to)
              AND (:q IS NULL
                   OR LOWER(l.message) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(l.code) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(l.context) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY l.createdAt DESC
            """)
    List<SystemLogEntry> searchForExport(@Param("source") SystemLogSource source,
                                         @Param("level") SystemLogLevel level,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("q") String q,
                                         Pageable pageable);

    /**
     * Counts per source and level inside a window, for the panel's summary tiles.
     *
     * <p>Returns {@code [source, level, count]}. One query fills every tile, so the
     * summary cannot disagree with itself across separate round trips.</p>
     */
    @Query("""
            SELECT l.source, l.level, COUNT(l)
            FROM SystemLogEntry l
            WHERE l.createdAt >= :from
              AND l.createdAt < :to
            GROUP BY l.source, l.level
            """)
    List<Object[]> countsBySourceAndLevel(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * Daily error counts, for the incident sparkline.
     *
     * <p>Returns {@code [year, month, day, count]}. Numeric date parts rather than a
     * dialect-specific formatting function, so the query behaves identically on H2 in
     * tests and on MySQL in production.</p>
     */
    @Query("""
            SELECT YEAR(l.createdAt), MONTH(l.createdAt), DAY(l.createdAt), COUNT(l)
            FROM SystemLogEntry l
            WHERE l.level = com.electroshop.model.SystemLogLevel.ERROR
              AND l.createdAt >= :from
              AND l.createdAt < :to
            GROUP BY YEAR(l.createdAt), MONTH(l.createdAt), DAY(l.createdAt)
            ORDER BY YEAR(l.createdAt), MONTH(l.createdAt), DAY(l.createdAt)
            """)
    List<Object[]> dailyErrorCounts(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * The most frequent error codes inside a window, worst first.
     *
     * <p>Returns {@code [code, source, count, most recent occurrence]}. A ranked list
     * turns a wall of individual failures into the two or three problems actually
     * worth fixing.</p>
     */
    @Query("""
            SELECT l.code, l.source, COUNT(l), MAX(l.createdAt)
            FROM SystemLogEntry l
            WHERE l.level = com.electroshop.model.SystemLogLevel.ERROR
              AND l.createdAt >= :from
              AND l.createdAt < :to
            GROUP BY l.code, l.source
            ORDER BY COUNT(l) DESC
            """)
    List<Object[]> topErrorCodes(@Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);

    /**
     * Deletes entries older than a cut-off and reports how many went.
     *
     * <p>A monitoring table that grows without bound eventually becomes the outage it
     * was installed to detect, so retention is enforced rather than recommended.</p>
     */
    @Modifying
    @Query("DELETE FROM SystemLogEntry l WHERE l.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    /** The earliest entry, so the panel can state when collection began. */
    @Query("SELECT MIN(l.createdAt) FROM SystemLogEntry l")
    LocalDateTime earliestEntry();
}
