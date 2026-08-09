package com.electroshop.repository;

import com.electroshop.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Filtered activity feed. All three filters are optional — pass {@code null}
     * to leave a dimension unfiltered. Backs both the system-wide "Jurnal de
     * activitate" page (action filter) and the per-product history popup
     * (entityType="Product" + entityId).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("entityId") Long entityId,
                          Pageable pageable);

    // ---- Activity panel (task 5) -----------------------------------------

    /**
     * The activity feed, windowed, optionally narrowed by actor, with free-text search.
     *
     * <p>The search covers the action code, the entity type and the detail text in one
     * predicate, because an operator hunting a change remembers a fragment of one of the
     * three and does not remember which. The term is bound as a parameter, never
     * concatenated, so a value containing quotes is data and cannot become syntax.</p>
     *
     * <p>There is deliberately no category parameter. The category is derived from the
     * action and entity type by {@code ActivityFeedService}, and expressing that
     * derivation a second time as SQL {@code LIKE} clauses would put one rule in two
     * languages, where it would drift the first time an action code is added.</p>
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.createdAt >= :from
              AND a.createdAt < :to
              AND (:actor IS NULL OR a.actor = :actor)
              AND (:q IS NULL
                   OR LOWER(a.action) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.details) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> searchFeed(@Param("from") java.time.LocalDateTime from,
                              @Param("to") java.time.LocalDateTime to,
                              @Param("actor") String actor,
                              @Param("q") String q,
                              Pageable pageable);

    /**
     * Action and entity-type pairs with their counts, for the category badges.
     *
     * <p>Returns {@code [action, entityType, count]}. Grouping in the database and
     * classifying the handful of distinct pairs in Java is far cheaper than reading
     * every row to count them, and keeps the classification in one function.</p>
     */
    @Query("""
            SELECT a.action, a.entityType, COUNT(a)
            FROM AuditLog a
            WHERE a.createdAt >= :from AND a.createdAt < :to
            GROUP BY a.action, a.entityType
            """)
    List<Object[]> actionEntityCounts(@Param("from") java.time.LocalDateTime from,
                                      @Param("to") java.time.LocalDateTime to);

    /** Everyone who acted inside a window, for the actor filter. */
    @Query("""
            SELECT DISTINCT a.actor FROM AuditLog a
            WHERE a.createdAt >= :from AND a.createdAt < :to AND a.actor IS NOT NULL
            ORDER BY a.actor
            """)
    List<String> distinctActorsBetween(@Param("from") java.time.LocalDateTime from,
                                       @Param("to") java.time.LocalDateTime to);

    /**
     * How often one person performed each action recently: {@code [action, count]}.
     *
     * <p>Backs the productivity panel's shortcuts, which are derived from what this
     * administrator actually does rather than from a fixed menu that is the same for
     * everyone and right for no one.</p>
     */
    @Query("""
            SELECT a.action, COUNT(a) FROM AuditLog a
            WHERE a.actor = :actor AND a.createdAt >= :since
            GROUP BY a.action
            ORDER BY COUNT(a) DESC
            """)
    List<Object[]> actionCountsForActor(@Param("actor") String actor,
                                        @Param("since") java.time.LocalDateTime since);
}
