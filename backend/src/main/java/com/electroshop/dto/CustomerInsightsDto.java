package com.electroshop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Who is buying, how often, and how much.
 *
 * <p>Answers {@code GET /api/customers/insights}.</p>
 *
 * <p><b>"New" is defined against all of history, not against the window.</b> A customer
 * who bought last year and bought again this month is a returning customer, even though
 * this month is the first time the selected window has seen them. Judging novelty from
 * inside the window would relabel the entire loyal base as new every time someone
 * narrows the range to seven days, which is exactly backwards: a short window would
 * report the healthiest possible acquisition figures for a business acquiring nobody.
 * The first order date is therefore looked up across the whole order history.</p>
 *
 * <p><b>Segments are computed, not configured.</b> The four segments come from the two
 * facts the data actually supports — how many times someone ordered and how much they
 * spent — so each customer lands in exactly one and the boundaries are stated in the
 * response instead of living as constants on both sides of the wire.</p>
 *
 * @param newVsReturning     per bucket: new customers as the primary value, returning
 *                           as the secondary, oldest first
 * @param orderFrequency     distribution of customers by how many orders they placed
 * @param basketSeries       average basket value per bucket, oldest first
 * @param segments           the four segments with their populations and revenue
 * @param topCustomers       the highest-spending customers in the window
 * @param newCustomers       distinct customers whose first ever order falls in the window
 * @param returningCustomers distinct customers in the window who had ordered before
 * @param totalCustomers     distinct customers who ordered in the window
 * @param avgBasket          average order value across the window
 * @param avgOrdersPerCustomer average number of orders per active customer
 * @param repeatRatePct      returning customers as a percentage of active customers
 * @param avgBasketDelta     average basket against the preceding window
 * @param repeatRateDelta    repeat rate against the preceding window
 * @param currency           ISO code the amounts are expressed in
 * @param range              the resolved window
 */
public record CustomerInsightsDto(
        List<SeriesPointDto> newVsReturning,
        List<FrequencyBucket> orderFrequency,
        List<SeriesPointDto> basketSeries,
        List<Segment> segments,
        List<TopCustomer> topCustomers,
        long newCustomers,
        long returningCustomers,
        long totalCustomers,
        BigDecimal avgBasket,
        Double avgOrdersPerCustomer,
        Double repeatRatePct,
        DeltaDto avgBasketDelta,
        DeltaDto repeatRateDelta,
        String currency,
        RangeInfoDto range
) {

    /**
     * How many customers placed a given number of orders.
     *
     * @param label     {@code "1"}, {@code "2"}, {@code "3-5"}, {@code "6-10"}, {@code "10+"}
     * @param customers how many customers fall in the bucket
     * @param revenue   revenue those customers produced, which is what turns the
     *                  distribution into a decision: a small bucket carrying a large
     *                  share of revenue is the one worth protecting
     * @param sharePct  the bucket's share of active customers
     */
    public record FrequencyBucket(String label, long customers, BigDecimal revenue, Double sharePct) {}

    /**
     * One customer segment.
     *
     * @param key         stable identifier: {@code VIP}, {@code LOYAL}, {@code OCCASIONAL},
     *                    {@code ONE_TIME}
     * @param label       Romanian display name
     * @param definition  the rule that put customers here, in plain Romanian, so the
     *                    segment can be argued with rather than merely accepted
     * @param customers   how many customers are in it
     * @param revenue     revenue the segment produced in the window
     * @param avgBasket   the segment's average order value
     * @param sharePct    the segment's share of active customers
     * @param revenuePct  the segment's share of window revenue
     */
    public record Segment(
            String key,
            String label,
            String definition,
            long customers,
            BigDecimal revenue,
            BigDecimal avgBasket,
            Double sharePct,
            Double revenuePct
    ) {}

    /**
     * One high-value customer.
     *
     * @param userId      database id, so the row links to the user page
     * @param email       account email
     * @param fullName    display name, may be null
     * @param orders      orders placed in the window
     * @param revenue     revenue produced in the window
     * @param avgBasket   average order value in the window
     * @param firstOrderAt when they first ever ordered, which is what distinguishes a
     *                    long-standing account from a new arrival spending heavily
     * @param lastOrderAt when they last ordered
     * @param segment     which segment they fall into
     */
    public record TopCustomer(
            Long userId,
            String email,
            String fullName,
            long orders,
            BigDecimal revenue,
            BigDecimal avgBasket,
            LocalDateTime firstOrderAt,
            LocalDateTime lastOrderAt,
            String segment
    ) {}
}
