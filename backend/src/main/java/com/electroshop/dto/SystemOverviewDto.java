package com.electroshop.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The system panel: API health, scheduled jobs, notifications and backup state.
 *
 * <p>Answers {@code GET /api/system/overview}.</p>
 *
 * <p><b>On Backup &amp; Restore.</b> The export half is implemented: the panel can
 * trigger a logical export of the catalogue, the orders and the audit trail through the
 * services that already produce those files, and it reports when each was last taken.
 * The restore half is deliberately not exposed. Restoring a database is irreversible and
 * destroys everything written since the snapshot; putting that behind a button in a web
 * panel means a single mis-click, a stale tab, or one stolen session ends the business's
 * records. The panel shows the procedure and the state, and the restore itself stays
 * where it belongs — with a person on the infrastructure, deliberately.</p>
 *
 * @param health        live performance of the running instance
 * @param jobs          the scheduled jobs and their last outcomes
 * @param notifications recent system notifications
 * @param webhooks      recent outbound webhook attempts
 * @param backup        export state and instructions
 * @param checkedAt     when this snapshot was taken
 */
public record SystemOverviewDto(
        HealthStatusDto health,
        List<JobStatus> jobs,
        List<NotificationSummary> notifications,
        List<WebhookLog> webhooks,
        BackupInfo backup,
        LocalDateTime checkedAt
) {

    /**
     * One scheduled job.
     *
     * @param name        the job's method name
     * @param description what it does, in Romanian
     * @param schedule    its cron expression or fixed delay, as configured
     * @param lastRunAt   when it last ran, null when it has not run since startup
     * @param lastStatus  {@code OK}, {@code FAILED} or {@code NEVER_RUN}
     * @param lastDurationMs how long the last run took
     * @param lastError   the failure message when the last run failed
     * @param runsTotal   how many times it has run since startup
     * @param failuresTotal how many of those failed
     * @param nextRunAt   the next scheduled moment, null when it cannot be computed
     */
    public record JobStatus(
            String name,
            String description,
            String schedule,
            LocalDateTime lastRunAt,
            String lastStatus,
            Long lastDurationMs,
            String lastError,
            long runsTotal,
            long failuresTotal,
            LocalDateTime nextRunAt
    ) {}

    /**
     * A recent system notification.
     *
     * @param id        database id
     * @param type      the notification type code
     * @param title     its title
     * @param message   its body
     * @param read      whether anyone has read it
     * @param createdAt when it was raised
     */
    public record NotificationSummary(
            Long id,
            String type,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt
    ) {}

    /**
     * One outbound webhook attempt.
     *
     * @param id          log entry id
     * @param event       what triggered it
     * @param target      the destination host and path, without credentials or query
     *                    string — a log an operator reads is not a place to leak a
     *                    signing secret
     * @param statusCode  the HTTP status returned, null when the call never completed
     * @param durationMs  how long it took
     * @param success     whether it succeeded
     * @param error       the failure message when it did not
     * @param createdAt   when it was attempted
     */
    public record WebhookLog(
            Long id,
            String event,
            String target,
            Integer statusCode,
            Long durationMs,
            boolean success,
            String error,
            LocalDateTime createdAt
    ) {}

    /**
     * Backup state.
     *
     * @param exports          what can be exported and when each was last taken
     * @param restoreAvailable always false; present so the frontend renders the section
     *                         from the server's answer rather than from a hard-coded
     *                         assumption that would silently diverge if this ever changes
     * @param restoreNote      the procedure, in Romanian, and why it is not a button
     */
    public record BackupInfo(
            List<ExportTarget> exports,
            boolean restoreAvailable,
            String restoreNote
    ) {}

    /**
     * One thing that can be exported.
     *
     * @param key         stable code: {@code PRODUCTS}, {@code ORDERS}, {@code AUDIT}
     * @param label       Romanian display name
     * @param rowCount    how many rows the export would contain right now
     * @param lastExportAt when it was last exported, null when never
     * @param endpoint    the path that produces the file
     */
    public record ExportTarget(
            String key,
            String label,
            long rowCount,
            LocalDateTime lastExportAt,
            String endpoint
    ) {}
}
