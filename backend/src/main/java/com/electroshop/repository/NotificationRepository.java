package com.electroshop.repository;

import com.electroshop.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
              SELECT n FROM Notification n
                       WHERE (:type IS NULL OR n.type = :type)
                         AND (:unreadOnly = FALSE OR n.read = FALSE)
                       ORDER BY n.createdAt DESC
                       """)
      Page<Notification> search(@Param("type") String type,
                                                              @Param("unreadOnly") boolean unreadOnly,
                                                              Pageable pageable);

    long countByReadFalse();

    /** Dedup guard: don't re-notify about the same still-open condition on the same entity. */
    boolean existsByTypeAndEntityIdAndReadFalse(String type, Long entityId);
}
