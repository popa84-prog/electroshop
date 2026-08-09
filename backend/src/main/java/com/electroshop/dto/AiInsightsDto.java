package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Automated suggestions and pattern analysis for administrators.
 *
 * <p>Answers {@code GET /api/admin/ai/insights}.</p>
 *
 * <p><b>How these are produced, stated plainly.</b> The project has no language-model
 * provider configured and none is introduced here. Every insight below comes from a
 * deterministic rules engine running over the store's own data: products whose sales
 * fell against their own baseline, stock that has stopped moving while capital sits in
 * it, margins below a threshold, order volume concentrated in particular hours.</p>
 *
 * <p>That is a deliberate trade and it is the better one. A rules engine can attach the
 * numbers that produced each suggestion, which means an operator can disagree with it
 * on evidence. A generated sentence cannot, and a suggestion nobody can check is a
 * suggestion people stop reading after the first time it is wrong. Every entry therefore
 * carries {@code rationale} with real figures in it, and {@code source} says which
 * engine produced it, so the day a model is wired in the distinction stays visible
 * rather than being quietly blended in.</p>
 *
 * @param suggestions   ranked suggestions, highest impact first
 * @param orderPatterns what the order history shows about when and how people buy
 * @param generatedAt   when the analysis ran, as an ISO string
 * @param source        {@code RULES} or the name of the configured generator
 * @param currency      ISO code the amounts are expressed in
 * @param range         the resolved window
 */
public record AiInsightsDto(
        List<Suggestion> suggestions,
        OrderPatterns orderPatterns,
        String generatedAt,
        String source,
        String currency,
        RangeInfoDto range
) {

    /**
     * One suggestion with its evidence.
     *
     * @param id         stable identifier, so a dismissed suggestion stays dismissed
     * @param kind       stable code: {@code LOW_SALES}, {@code EXCESS_STOCK},
     *                   {@code PROMO_OPPORTUNITY}, {@code MARGIN_RISK},
     *                   {@code RESTOCK_URGENT}, {@code PRICE_BELOW_COST}
     * @param severity   {@code DANGER}, {@code WARNING}, {@code INFO} or {@code SUCCESS}
     * @param headline   one short line in Romanian
     * @param rationale  the figures that produced it, in Romanian
     * @param impact     estimated monthly effect in currency, null when the rule cannot
     *                   quantify one honestly rather than plausibly
     * @param confidence {@code HIGH}, {@code MEDIUM} or {@code LOW}, set by how much
     *                   history the rule had
     * @param productIds the products the suggestion concerns, empty when it is general
     * @param linkTo     the admin route that acts on it, null when there is none
     * @param actionLabel what the button says, null when there is no single action
     */
    public record Suggestion(
            String id,
            String kind,
            String severity,
            String headline,
            String rationale,
            BigDecimal impact,
            String confidence,
            List<Long> productIds,
            String linkTo,
            String actionLabel
    ) {}

    /**
     * What the order history reveals about buying behaviour.
     *
     * @param byHour        orders per hour of day, 00 through 23
     * @param byWeekday     orders per weekday, Monday first
     * @param peakHour      the busiest hour, as {@code HH:00}
     * @param peakWeekday   the busiest weekday, in Romanian
     * @param quietHour     the quietest hour with any activity at all
     * @param avgBasket     average order value across the window
     * @param avgItemsPerOrder average line count per order
     * @param newCustomerPct share of window orders placed by first-time buyers
     * @param observations  plain-Romanian statements about the patterns above, each one
     *                      backed by the figures already present in this record
     */
    public record OrderPatterns(
            List<SeriesPointDto> byHour,
            List<SeriesPointDto> byWeekday,
            String peakHour,
            String peakWeekday,
            String quietHour,
            BigDecimal avgBasket,
            Double avgItemsPerOrder,
            Double newCustomerPct,
            List<String> observations
    ) {}
}
