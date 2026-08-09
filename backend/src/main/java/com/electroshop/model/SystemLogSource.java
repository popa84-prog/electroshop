package com.electroshop.model;

/**
 * Which part of the system produced a {@link SystemLogEntry}.
 *
 * <p>The operational-logs panel is organised by source rather than by severity,
 * because the four sources fail for unrelated reasons and are fixed by unrelated
 * actions. A burst of {@link #DB} errors means the database is unreachable; a burst
 * of {@link #API} errors with the database healthy means a specific endpoint is
 * broken. Grouping both under "errors" would hide exactly the distinction the
 * operator needs.</p>
 */
public enum SystemLogSource {

    /** An HTTP request that ended in a server-side failure. */
    API,

    /** A scheduled job that threw, or that reported a failure result. */
    CRON,

    /** A data-access failure: connection, constraint, timeout, deadlock. */
    DB,

    /** Authentication and authorisation failures worth an operator's attention. */
    AUTH,

    /** Anything raised deliberately by application code that fits no other source. */
    APP
}
