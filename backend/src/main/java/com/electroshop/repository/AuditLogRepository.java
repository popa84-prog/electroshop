package com.electroshop.repository;

import com.electroshop.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
