package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One interaction between a visitor and a promotional {@link Offer}.
 *
 * <p>Click-through rate, conversion rate and cost per acquisition are ratios over
 * counted events. The catalogue stores offers but has never stored what visitors did
 * with them, so every one of those numbers is unavailable until this table starts
 * filling. Nothing here is derived or estimated: a row exists because something
 * happened.</p>
 *
 * <p>Consequently the marketing panel is empty on the day this ships and becomes
 * meaningful as traffic accumulates. The panel states the date collection began rather
 * than presenting an empty window as a zero result, because a campaign that shows 0%
 * conversion and a campaign that has not been measured yet are different facts and an
 * operator will act differently on each.</p>
 *
 * <p><b>Privacy.</b> No visitor identity is stored. The session hash is a one-way
 * digest supplied by the frontend, used only to avoid counting the same visitor's
 * impression twice within a page view and to attribute a conversion to a prior click.
 * It is not reversible and is not joined to {@link User}.</p>
 */
@Entity
@Table(
        name = "offer_events",
        indexes = {
                @Index(name = "idx_oe_offer_type_created", columnList = "offer_id, type, createdAt"),
                @Index(name = "idx_oe_created", columnList = "createdAt"),
                @Index(name = "idx_oe_session", columnList = "sessionHash")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OfferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    /**
     * Denormalised copy of {@code offer.id}, so the funnel query reads one table.
     */
    @Column(name = "offer_ref", nullable = false)
    private Long offerRef;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OfferEventType type;

    /**
     * Opaque per-session digest supplied by the browser. Never reversible, never
     * linked to an account. Used for de-duplication and last-click attribution only.
     */
    @Column(length = 64)
    private String sessionHash;

    /**
     * The order a {@link OfferEventType#CONVERSION} produced.
     *
     * <p>Null for impressions and clicks. Present for conversions, so the revenue
     * credited to a campaign can be traced back to real orders instead of being
     * asserted by the report.</p>
     */
    @Column(name = "order_ref")
    private Long orderRef;

    /**
     * Order value at the moment of conversion.
     *
     * <p>Copied rather than joined, for the same reason {@link OrderItem} copies its
     * cost price: an order edited later must not silently rewrite the historical
     * performance of a campaign that has already been reported on.</p>
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal orderValue;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OfferEvent(Offer offer, OfferEventType type, String sessionHash) {
        this.offer = offer;
        this.offerRef = offer != null ? offer.getId() : null;
        this.type = type;
        this.sessionHash = sessionHash;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.offerRef == null && this.offer != null) {
            this.offerRef = this.offer.getId();
        }
    }
}
