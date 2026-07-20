package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email of the admin/user who performed the action. */
    @Column(length = 120)
    private String actor;

    /** e.g. PRODUCT_CREATED, STOCK_CHANGED, IMAGE_DELETED, ORDER_CREATED. */
    @Column(length = 60)
    private String action;

    @Column(length = 60)
    private String entityType;

    private Long entityId;

    @Column(length = 500)
    private String details;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
