package com.electroshop.model;

/**
 * The lifecycle of an order.
 *
 * <p>The five original values describe a sale that goes forward. {@link #RETURNED} was
 * added because the order-efficiency report has to answer "what is the return rate",
 * and a return recorded as a cancellation is a different commercial event filed under
 * the wrong name: a cancellation costs a sale, a return costs a sale plus the shipping
 * both ways plus the handling of goods that came back. Averaging the two hides the
 * more expensive one.</p>
 *
 * <p>{@link #RETURNED} is reachable only from {@link #DELIVERED}. Goods that never
 * arrived cannot come back, so an order cancelled before delivery stays
 * {@link #CANCELLED}.</p>
 */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    /**
     * The customer sent delivered goods back.
     *
     * <p>Terminal. The reason is recorded on the
     * {@link OrderStatusEvent} that produced the transition, so the report can rank
     * why returns happen instead of only counting that they did.</p>
     */
    RETURNED
}
