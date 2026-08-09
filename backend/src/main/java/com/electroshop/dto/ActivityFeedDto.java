package com.electroshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The recent-activity panel, with filtering, search and expandable detail.
 *
 * <p>Answers {@code GET /api/admin/activity}.</p>
 *
 * <p>The underlying {@code AuditLog} already records who did what to which entity. What
 * it does not do is group actions into the categories an operator thinks in — products,
 * orders, users, system — so this DTO adds that mapping. {@code category} is derived on
 * the server from the entity type and the action code, which means the filter buttons
 * and the rows they filter cannot drift apart the way they would if the frontend
 * classified the rows itself.</p>
 *
 * <p>{@code changes} is the "what changed" detail the requirement asks for. It is parsed
 * from the audit row's stored detail text into field-level before/after pairs where the
 * format allows, and left empty where it does not. Empty is honest: the audit log was
 * not designed to store structured diffs for every action, and inventing a diff from an
 * unstructured sentence would produce confident-looking fabrications about what someone
 * changed.</p>
 *
 * @param entries       the filtered page of activity rows, newest first
 * @param page          zero-based page index
 * @param size          page size
 * @param totalElements how many rows match the filter
 * @param totalPages    how many pages that is
 * @param categoryCounts how many rows each category holds in the current window, so the
 *                      filter buttons can show counts without a second request
 * @param actors        the distinct people who acted in the window, for the actor filter
 * @param range         the resolved window
 */
public record ActivityFeedDto(
        List<Entry> entries,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<CategoryCount> categoryCounts,
        List<String> actors,
        RangeInfoDto range
) {

    /**
     * One activity row.
     *
     * @param id          audit log id
     * @param actor       who did it, by email
     * @param action      the raw action code, kept so a support conversation can refer
     *                    to the exact value stored
     * @param actionLabel the action rendered in Romanian for display
     * @param category    {@code PRODUCTS}, {@code ORDERS}, {@code USERS},
     *                    {@code SYSTEM} or {@code OTHER}
     * @param entityType  what kind of thing was touched
     * @param entityId    which one, null when the action targets nothing specific
     * @param entityName  a readable name for it where one could be resolved
     * @param linkTo      the admin route that opens the affected entity, null when there
     *                    is none — turning an audit line into one click instead of a
     *                    search is most of what makes the panel usable
     * @param details     the stored detail text
     * @param changes     field-level before/after pairs, empty when the detail text does
     *                    not carry them
     * @param createdAt   when it happened
     */
    public record Entry(
            Long id,
            String actor,
            String action,
            String actionLabel,
            String category,
            String entityType,
            Long entityId,
            String entityName,
            String linkTo,
            String details,
            List<FieldChange> changes,
            LocalDateTime createdAt
    ) {}

    /**
     * One field that changed.
     *
     * @param field    the field name, in Romanian where a translation exists
     * @param oldValue what it was, null when the field was previously unset
     * @param newValue what it became, null when the field was cleared
     */
    public record FieldChange(String field, String oldValue, String newValue) {}

    /**
     * How many rows one category holds.
     *
     * @param category the category code
     * @param label    its Romanian display name
     * @param count    how many rows
     */
    public record CategoryCount(String category, String label, long count) {}
}
