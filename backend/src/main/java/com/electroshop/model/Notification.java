package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Feature #8 — Notificări automate. One row per notification shown in the
 * admin notification center: low stock, product without an image, product
 * deactivated, new order, and (feature #6) account locked / suspicious
 * activity. Event-driven notifications (new order, product deactivated) are
 * created once at the moment they happen; threshold-based ones (low stock, no
 * image) are kept in sync by a periodic sweep — see NotificationService.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_type", columnList = "type"),
        @Index(name = "idx_notifications_read", columnList = "isRead"),
        @Index(name = "idx_notifications_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LOW_STOCK, NO_IMAGE, PRODUCT_INACTIVE, NEW_ORDER, ACCOUNT_LOCKED, ... */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String message;

    @Column(length = 60)
    private String entityType;

    private Long entityId;

    @Column(name = "isRead", nullable = false)
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
