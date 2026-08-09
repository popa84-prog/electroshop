package com.electroshop.model;

/**
 * What a visitor did with a promotional offer.
 *
 * <p>The three values form the funnel the marketing panel reports on, in order:
 * an offer is shown, some visitors click it, some of those buy. Every rate the panel
 * computes is a ratio between two adjacent stages of this funnel, so the enum is the
 * whole vocabulary the metric needs.</p>
 */
public enum OfferEventType {

    /**
     * The offer was rendered in front of a visitor.
     *
     * <p>Recorded once per offer per page view, not once per pixel scrolled into
     * view: an impression counted on scroll would inflate with every user who
     * scrolls up and down, and a click-through rate whose denominator inflates is
     * a rate that drifts down for no reason.</p>
     */
    IMPRESSION,

    /** The visitor activated the offer's call to action. */
    CLICK,

    /**
     * An order was placed by a visitor who had clicked this offer.
     *
     * <p>Attribution is last-click within the session, which is the only model the
     * available data supports honestly. The conversion row carries the order id so
     * the revenue behind the number is auditable rather than asserted.</p>
     */
    CONVERSION
}
