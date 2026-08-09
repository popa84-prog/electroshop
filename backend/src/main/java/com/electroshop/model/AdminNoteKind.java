package com.electroshop.model;

/**
 * Which of the three productivity tools an {@link AdminNote} row belongs to.
 *
 * <p>Quick notes, reminders and internal tasks are the same record wearing three
 * labels: a piece of text owned by an admin, optionally due at a time, optionally
 * finished. Splitting them into three tables would triple the schema, the repositories
 * and the endpoints to express a difference that is one column wide.</p>
 */
public enum AdminNoteKind {

    /** Free text with no due date and no completion state. */
    NOTE,

    /** Text with a due moment; surfaces in the panel when the moment arrives. */
    REMINDER,

    /** Text that is done or not done, with an optional due date and priority. */
    TASK
}
