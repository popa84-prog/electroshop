package com.electroshop.dto;

import java.time.LocalDateTime;

/**
 * One note, reminder or internal task belonging to an administrator.
 *
 * <p>Used by {@code /api/admin/tools} for both reading and writing. The same shape
 * serves all three tools because the three differ only in which optional fields they
 * use, and a separate DTO per tool would triple the surface to express a one-column
 * difference.</p>
 *
 * <p>{@code id} is null on create and present on update, which is the only signal the
 * endpoint needs to tell the two apart.</p>
 *
 * @param id        database id, null when creating
 * @param kind      {@code NOTE}, {@code REMINDER} or {@code TASK}
 * @param title     optional short heading
 * @param content   the body
 * @param dueAt     when a reminder fires or a task is due, null for a plain note
 * @param done      whether a task is finished
 * @param priority  1 low, 2 normal, 3 high; meaningful for tasks
 * @param linkTo    an admin route this item points at, validated to stay inside
 *                  {@code /admin/} so a stored value can never send an operator off-site
 * @param overdue   computed on the server: whether {@code dueAt} has passed and the item
 *                  is not done. Derived here rather than in the browser because a
 *                  client clock that is wrong would mark items overdue that are not
 * @param createdAt when it was written
 * @param updatedAt when it was last changed
 */
public record AdminNoteDto(
        Long id,
        String kind,
        String title,
        String content,
        LocalDateTime dueAt,
        boolean done,
        int priority,
        String linkTo,
        boolean overdue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
