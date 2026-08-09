package com.electroshop.repository;

import com.electroshop.model.AdminNote;
import com.electroshop.model.AdminNoteKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Notes, reminders and internal tasks, always scoped to their owner.
 *
 * <p>As with {@link AdminPreferenceRepository}, no method can reach a row without the
 * owner's id. {@link #findByIdAndAdminId} exists instead of the inherited
 * {@code findById} so that update and delete paths cannot load another
 * administrator's note by guessing a number.</p>
 */
public interface AdminNoteRepository extends JpaRepository<AdminNote, Long> {

    /** Owner-scoped lookup, used before every update and delete. */
    Optional<AdminNote> findByIdAndAdminId(Long id, Long adminId);

    /** Everything one administrator owns, newest first. */
    List<AdminNote> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    /** One tool's items: notes, reminders or tasks. */
    List<AdminNote> findByAdminIdAndKindOrderByCreatedAtDesc(Long adminId, AdminNoteKind kind);

    /**
     * Open tasks, highest priority first and oldest first inside a priority.
     *
     * <p>Ordering by priority alone would let a high-priority item added this morning
     * bury one added last week; ordering by age alone ignores the priority the
     * operator set. Both keys together produce the list a person would write by
     * hand.</p>
     */
    @Query("""
            SELECT n FROM AdminNote n
            WHERE n.adminId = :adminId
              AND n.kind = com.electroshop.model.AdminNoteKind.TASK
              AND n.done = false
            ORDER BY n.priority DESC, n.createdAt ASC
            """)
    List<AdminNote> openTasks(@Param("adminId") Long adminId);

    /**
     * Reminders that have come due and are not yet dismissed.
     *
     * <p>Due-first ordering puts the most overdue at the top, which is where an
     * operator looks.</p>
     */
    @Query("""
            SELECT n FROM AdminNote n
            WHERE n.adminId = :adminId
              AND n.kind = com.electroshop.model.AdminNoteKind.REMINDER
              AND n.done = false
              AND n.dueAt IS NOT NULL
              AND n.dueAt <= :now
            ORDER BY n.dueAt ASC
            """)
    List<AdminNote> dueReminders(@Param("adminId") Long adminId, @Param("now") LocalDateTime now);

    /** How many tasks are still open, for the panel's badge. */
    long countByAdminIdAndKindAndDoneFalse(Long adminId, AdminNoteKind kind);

    /** Removes everything an administrator owns, used when the account is deleted. */
    void deleteByAdminId(Long adminId);
}
