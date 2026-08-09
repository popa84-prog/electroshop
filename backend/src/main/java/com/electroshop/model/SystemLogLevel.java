package com.electroshop.model;

/**
 * How much attention a {@link SystemLogEntry} deserves.
 *
 * <p>Three levels, not five. A level nobody filters on is a level that only makes the
 * filter harder to use, and the operational panel has exactly three questions: is
 * something broken right now, is something degrading, and what happened. Debug-level
 * noise belongs in the application log stream, not in a table an operator reads.</p>
 */
public enum SystemLogLevel {

    /** Something failed and a user or a job was affected. */
    ERROR,

    /** Something recovered, degraded, or is heading toward failure. */
    WARN,

    /** A notable event that did not fail: startup, backup completed, job finished. */
    INFO
}
