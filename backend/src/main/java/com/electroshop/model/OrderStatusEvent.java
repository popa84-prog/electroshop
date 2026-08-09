package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One transition of an order from one status to another.
 *
 * <p>An {@link Order} carries only {@code createdAt} and {@code updatedAt}, which is
 * enough to know when it was placed and when it was last touched, and not enough to
 * answer any question about how long a stage took. "Average processing time" is the
 * gap between the order arriving and the order being paid; "average delivery time" is
 * the gap between it shipping and it being delivered. Neither gap exists anywhere in
 * the schema until the transitions themselves are recorded, so this table records
 * them.</p>
 *
 * <p>The row is written by {@link com.electroshop.service.OrderService} on every status
 * change, including the first one, so an order's history is a complete chain from
 * {@code null -> PENDING} to whatever its final state is. Keeping the {@code fromStatus}
 * as well as the {@code toStatus} makes the chain self-describing: a gap in the data —
 * an order whose status was changed by a direct database edit, for instance — is
 * visible as a break in the chain rather than silently averaged into the result.</p>
 *
 * <p>History is append-only. Nothing in the application updates or deletes a row here,
 * because a metric computed from a mutable history is not a metric.</p>
 */
@Entity
@Table(
        name = "order_status_events",
        indexes = {
                @Index(name = "idx_ose_order_created", columnList = "order_id, createdAt"),
                @Index(name = "idx_ose_to_created", columnList = "toStatus, createdAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OrderStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order this transition belongs to.
     *
     * <p>Lazy on purpose: the efficiency report aggregates hundreds of thousands of
     * these rows and never needs the order behind any of them.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Denormalised copy of {@code order.id}.
     *
     * <p>The aggregation queries group by order without loading it, and a JPQL
     * {@code GROUP BY e.order.id} forces a join to the orders table for no benefit.
     * This column lets the report read the event table alone.</p>
     */
    @Column(name = "order_ref", nullable = false)
    private Long orderRef;

    /** The status the order left. {@code null} for the very first event of an order. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus fromStatus;

    /** The status the order entered. Never null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OrderStatus toStatus;

    /**
     * Email of the operator who made the change, or {@code null} when the transition
     * came from the customer placing the order rather than from the admin panel.
     */
    @Column(length = 120)
    private String actor;

    /**
     * Free-text reason, used by returns and cancellations.
     *
     * <p>A return rate is a number; a return rate with reasons attached is an
     * instruction about what to fix. The column is optional because most transitions
     * have nothing to explain.</p>
     */
    @Column(length = 300)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OrderStatusEvent(Order order, OrderStatus fromStatus, OrderStatus toStatus, String actor, String reason) {
        this.order = order;
        this.orderRef = order != null ? order.getId() : null;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.orderRef == null && this.order != null) {
            this.orderRef = this.order.getId();
        }
    }
}
