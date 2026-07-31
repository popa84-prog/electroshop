package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Whether an administrator has approved this account. New self-registered
     * accounts start as {@code false} (pending) and cannot log in until approved.
     * Nullable on purpose: pre-existing accounts get a NULL column when Hibernate
     * adds it, and SchemaFixer grandfathers those NULLs to TRUE on startup so no
     * existing user is locked out.
     */
    @Column
    private Boolean approved = false;

    // --- Last successful login tracking (quick view; full history in login_events) ---
    private LocalDateTime lastLoginAt;

    @Column(length = 45)
    private String lastLoginIp;

    @Column(length = 120)
    private String lastLoginLocation;

    // --- Brute-force protection (feature #6) ---
    /** Consecutive failed login attempts since the last success or unlock. */
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    /** While in the future, login is blocked even with the correct password. */
    private LocalDateTime lockedUntil;

    // --- Two-factor authentication for Admin accounts (feature #6) ---
    private boolean twoFactorEnabled = false;

    /** Active TOTP secret (Base32), only set once 2FA is confirmed. Never exposed via DTOs. */
    @Column(length = 64)
    private String twoFactorSecret;

    /** Secret generated during /2fa/setup, awaiting confirmation via /2fa/confirm. */
    @Column(length = 64)
    private String twoFactorPendingSecret;

    // --- Refresh-token security (feature #6) ---
    /**
     * Bumped on demand (logout-all / "revoke sessions"). Every issued access
     * and refresh token embeds the version it was minted with; a mismatch
     * against the user's current value makes the token invalid even though it
     * has not expired yet.
     */
    @Column(nullable = false)
    private int tokenVersion = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Order> orders = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }
}
