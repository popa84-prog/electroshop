package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One named setting belonging to one administrator.
 *
 * <p>Three separate requirements need per-admin persistence: the dashboard layout
 * (which cards are where, which are hidden), the sidebar favourites, and the
 * compact/expanded view mode. Each could have its own table with its own columns, its
 * own repository and its own migration. None of them earns one: they are all a single
 * value belonging to a single admin, read as a whole and written as a whole, and none
 * of them is ever queried by its contents.</p>
 *
 * <p>So they share this table, keyed by {@code (adminId, prefKey)}. The value is JSON
 * because the layout is a list of objects and the favourites are a list of strings —
 * shapes that a column-per-field design would have to flatten and re-assemble on every
 * read. The keys in use are declared as constants below rather than typed at each call
 * site, so the set of settings is enumerable from one place.</p>
 *
 * <p><b>Why the value is not validated as a schema.</b> The layout the frontend saves
 * is the layout the frontend reads back; the backend never interprets it. Validating
 * its internal shape here would couple a database table to a UI decision and would have
 * to change every time a card is added. The backend enforces what it must: the value is
 * well-formed JSON and is bounded in size.</p>
 */
@Entity
@Table(
        name = "admin_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_admin_pref", columnNames = {"admin_id", "pref_key"}),
        indexes = @Index(name = "idx_ap_admin", columnList = "admin_id")
)
@Getter
@Setter
@NoArgsConstructor
public class AdminPreference {

    /** Dashboard card order, spans and hidden flags. Written by the layout editor. */
    public static final String KEY_DASHBOARD_LAYOUT = "dashboard.layout";

    /** Sidebar favourites: the admin routes pinned to the top of the rail. */
    public static final String KEY_SIDEBAR_FAVORITES = "sidebar.favorites";

    /** Whether the dashboard renders compact or expanded. */
    public static final String KEY_DASHBOARD_DENSITY = "dashboard.density";

    /** Whether the navigation rail is collapsed to icons only. */
    public static final String KEY_SIDEBAR_COLLAPSED = "sidebar.collapsed";

    /**
     * Largest value accepted, in characters.
     *
     * <p>A preference is a handful of identifiers and integers. Anything approaching
     * this size is a bug or an attempt to use the settings table as storage, and both
     * are better rejected at the boundary than discovered when the column overflows.</p>
     */
    public static final int MAX_VALUE_LENGTH = 16_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The owning administrator's user id.
     *
     * <p>Stored as a plain column rather than a {@code @ManyToOne}: preferences are
     * always loaded by the id of the already-authenticated caller, so the association
     * would add a join to every read and buy nothing.</p>
     */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "pref_key", length = 60, nullable = false)
    private String prefKey;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    public AdminPreference(Long adminId, String prefKey, String value) {
        this.adminId = adminId;
        this.prefKey = prefKey;
        this.value = value;
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
