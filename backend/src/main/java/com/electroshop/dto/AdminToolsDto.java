package com.electroshop.dto;

import java.util.List;

/**
 * The administrator's personal workspace: notes, reminders, tasks and shortcuts.
 *
 * <p>Answers {@code GET /api/admin/tools}.</p>
 *
 * <p>One request returns all four modules. They are small, they are always displayed
 * together, and four separate calls to fetch a handful of rows each would cost more in
 * round trips than the data is worth.</p>
 *
 * <p><b>Shortcuts are computed, not stored.</b> The requirement asks for shortcuts to
 * frequent actions, and "frequent" is a fact the system already knows: the audit log
 * records what this administrator actually does. The list is therefore derived from
 * their own recent actions, filtered by the permissions they hold, rather than being a
 * fixed menu that is the same for everyone and right for no one. A new administrator
 * with no history gets a sensible default set instead of an empty panel.</p>
 *
 * @param notes        free-text notes, newest first
 * @param reminders    reminders, soonest due first
 * @param tasks        internal tasks, highest priority then oldest first
 * @param shortcuts    quick actions, most useful first
 * @param openTaskCount how many tasks are still open, for the panel's badge
 * @param dueReminderCount how many reminders have come due and are not dismissed
 * @param limits       the caps the endpoint enforces, so the interface can disable
 *                     controls at the limit rather than letting a save fail
 */
public record AdminToolsDto(
        List<AdminNoteDto> notes,
        List<AdminNoteDto> reminders,
        List<AdminNoteDto> tasks,
        List<Shortcut> shortcuts,
        long openTaskCount,
        long dueReminderCount,
        Limits limits
) {

    /**
     * One quick action.
     *
     * @param key       stable identifier
     * @param label     Romanian display name
     * @param icon      icon name for the interface
     * @param linkTo    the admin route it opens
     * @param useCount  how many times this administrator performed the underlying action
     *                  recently, which is why it earned a place; zero for entries from
     *                  the default set
     * @param fromUsage whether it was derived from this administrator's own history or
     *                  came from the defaults
     */
    public record Shortcut(
            String key,
            String label,
            String icon,
            String linkTo,
            long useCount,
            boolean fromUsage
    ) {}

    /**
     * The caps the endpoint enforces.
     *
     * @param maxNotes         largest number of notes one administrator may keep
     * @param maxReminders     largest number of reminders
     * @param maxTasks         largest number of tasks
     * @param maxContentLength longest body accepted, in characters
     */
    public record Limits(
            int maxNotes,
            int maxReminders,
            int maxTasks,
            int maxContentLength
    ) {}
}
