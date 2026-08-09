package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A note, a reminder or an internal task belonging to one administrator.
 *
 * <p>The three tools the productivity panel offers differ by {@link AdminNoteKind} and
 * by which optional columns they use; everything else about them is identical. A note
 * uses {@code content} alone. A reminder adds {@code dueAt}. A task adds {@code done}
 * and {@code priority}. One table serves all three, and a new tool that is also
 * "text owned by an admin" costs one enum value instead of one migration.</p>
 *
 * <p><b>Ownership is enforced, not assumed.</b> Every read and every write goes through
 * {@code adminId} matched against the authenticated caller. Two administrators sharing
 * a panel must not see each other's notes, and the only reliable place to guarantee
 * that is the query, not the interface.</p>
 */
@Entity
@Table(
        name = "admin_notes",
        indexes = {
                @Index(name = "idx_an_admin_kind", columnList = "admin_id, kind, done"),
                @Index(name = "idx_an_due", columnList = "dueAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AdminNote {

    /** Longest note accepted. Generous for a note, far short of a document. */
    public static final int MAX_CONTENT_LENGTH = 4_000;

    /** Lowest and highest task priority. Three levels: 1 low, 2 normal, 3 high. */
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private AdminNoteKind kind = AdminNoteKind.NOTE;

    /** Optional short heading. A note is often only its body, so this may be null. */
    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** When a reminder fires or a task is due. Null for a plain note. */
    private LocalDateTime dueAt;

    /**
     * Whether a task is finished.
     *
     * <p>Not null even for notes and reminders, where it stays {@code false} and is
     * ignored, because a nullable boolean produces three states for a two-state
     * question and every query then has to say so.</p>
     */
    @Column(nullable = false)
    private boolean done = false;

    /** 1 low, 2 normal, 3 high. Meaningful for {@link AdminNoteKind#TASK}. */
    @Column(nullable = false)
    private int priority = 2;

    /**
     * Optional admin route this item points at, for example {@code /admin/products}.
     *
     * <p>Turns a note about a product into one click instead of a search. Validated
     * to start with {@code /admin/} on write, so a stored value can never send an
     * operator off-site.</p>
     */
    @Column(length = 200)
    private String linkTo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    public AdminNote(Long adminId, AdminNoteKind kind, String title, String content) {
        this.adminId = adminId;
        this.kind = kind;
        this.title = title;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
