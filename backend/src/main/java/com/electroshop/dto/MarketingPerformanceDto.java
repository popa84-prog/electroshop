package com.electroshop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * How the promotional campaigns are performing.
 *
 * <p>Answers {@code GET /api/marketing/performance}.</p>
 *
 * <p><b>This panel starts empty and that is the honest result.</b> Click-through rate,
 * conversion rate and cost per acquisition are ratios over counted interactions, and
 * the catalogue has never recorded an impression or a click. Collection begins the day
 * this ships; there is no retroactive history to reconstruct, because the events did
 * not happen anywhere they could be recovered from.</p>
 *
 * <p>{@code collectingSince} therefore carries real weight. A campaign showing 0%
 * conversion and a campaign that has not been measured yet are different facts, and an
 * operator acts differently on each — the first is a campaign to kill, the second is a
 * campaign to wait on. The panel prints the collection start date rather than letting
 * an unmeasured window read as a failed one. Generating plausible figures so the charts
 * look populated was considered and rejected: a business panel that invents numbers is
 * worse than one that admits it has none.</p>
 *
 * @param campaigns       one row per offer, ordered by conversions then impressions
 * @param conversions     conversions per bucket, oldest first
 * @param clickThrough    click-through rate per bucket, as a percentage, oldest first
 * @param costPerAcq      cost per acquisition per bucket, oldest first
 * @param evolution       impressions as the primary value and clicks as the secondary,
 *                        per bucket, so the funnel's two upper stages share one chart
 * @param totalImpressions impressions across the window
 * @param totalClicks     clicks across the window
 * @param totalConversions conversions across the window
 * @param totalRevenue    revenue attributed to campaigns in the window
 * @param totalCost       summed campaign cost across the window
 * @param ctrPct          clicks over impressions, as a percentage
 * @param conversionPct   conversions over clicks, as a percentage
 * @param costPerAcquisition total cost over conversions, null when nothing converted
 * @param roasPct         revenue over cost, as a percentage, null without recorded cost
 * @param ctrDelta        click-through rate against the preceding window
 * @param conversionDelta conversion rate against the preceding window
 * @param collectingSince when the first interaction was recorded, or null when nothing
 *                        has been recorded at all
 * @param currency        ISO code the amounts are expressed in
 * @param range           the resolved window
 */
public record MarketingPerformanceDto(
        List<Campaign> campaigns,
        List<SeriesPointDto> conversions,
        List<SeriesPointDto> clickThrough,
        List<SeriesPointDto> costPerAcq,
        List<SeriesPointDto> evolution,
        long totalImpressions,
        long totalClicks,
        long totalConversions,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        Double ctrPct,
        Double conversionPct,
        BigDecimal costPerAcquisition,
        Double roasPct,
        DeltaDto ctrDelta,
        DeltaDto conversionDelta,
        LocalDateTime collectingSince,
        String currency,
        RangeInfoDto range
) {

    /**
     * One campaign's line in the table.
     *
     * @param offerId       database id, so the row links to the offer editor
     * @param title         campaign title
     * @param placement     where on the storefront it appears
     * @param status        {@code ACTIVE}, {@code SCHEDULED}, {@code ENDED} or
     *                      {@code INACTIVE} — derived from the offer's own dates and
     *                      flag, so the table cannot disagree with the offers page
     * @param startsAt      when it opens, may be null
     * @param endsAt        when it closes, may be null
     * @param impressions   times it was shown in the window
     * @param clicks        times it was activated
     * @param conversions   orders attributed to it
     * @param revenue       revenue attributed to it
     * @param cost          what it cost to run, null when no cost was recorded
     * @param ctrPct        clicks over impressions, null when it was never shown
     * @param conversionPct conversions over clicks, null when it was never clicked
     * @param costPerAcquisition cost over conversions, null without cost or conversions
     * @param roasPct       revenue over cost, null without recorded cost
     * @param verdict       {@code STRONG}, {@code OK}, {@code WEAK} or {@code NO_DATA} —
     *                      decided on the server so the table's badges and its ordering
     *                      come from one rule, and so {@code NO_DATA} is never coloured
     *                      like a failure
     */
    public record Campaign(
            Long offerId,
            String title,
            String placement,
            String status,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            long impressions,
            long clicks,
            long conversions,
            BigDecimal revenue,
            BigDecimal cost,
            Double ctrPct,
            Double conversionPct,
            BigDecimal costPerAcquisition,
            Double roasPct,
            String verdict
    ) {}
}
