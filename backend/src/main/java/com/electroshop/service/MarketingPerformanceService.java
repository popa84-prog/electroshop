package com.electroshop.service;

import com.electroshop.dto.DeltaDto;
import com.electroshop.dto.MarketingPerformanceDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.model.Offer;
import com.electroshop.model.OfferEvent;
import com.electroshop.model.OfferEventType;
import com.electroshop.repository.OfferEventRepository;
import com.electroshop.repository.OfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Campaign performance, and the tracking that makes it possible.
 *
 * <p>Task 17.</p>
 *
 * <h2>This panel starts empty, and that is the correct result</h2>
 *
 * <p>Click-through rate, conversion rate and cost per acquisition are ratios over
 * counted interactions. The catalogue has never recorded an impression or a click, and
 * there is nowhere those events could be recovered from — they did not happen anywhere
 * that persisted them. Collection begins the day this ships.</p>
 *
 * <p>So {@code collectingSince} carries real weight in the response. A campaign showing
 * 0% conversion and a campaign that has not been measured yet are different facts, and
 * an operator acts differently on each: the first is a campaign to stop, the second is
 * one to wait on. Generating plausible figures so the charts look populated was
 * considered and rejected. A business panel that invents numbers is worse than one that
 * admits it has none, because the invented one gets acted on.</p>
 *
 * <h2>Attribution is last-click within a session</h2>
 *
 * <p>It is the only model the available data honestly supports. A visitor who clicked
 * three campaigns before buying is credited to the one that immediately preceded the
 * purchase, and the conversion row carries the order id so the revenue behind the figure
 * can be traced rather than trusted.</p>
 *
 * <h2>Impressions are de-duplicated</h2>
 *
 * <p>A visitor who reloads a page five times has seen the offer once. Counting five
 * would inflate the denominator of every rate the funnel produces, so the same session
 * cannot register a second impression for the same offer inside
 * {@link #IMPRESSION_DEDUP_HOURS}.</p>
 */
@Service
public class MarketingPerformanceService {

    /** How long a session's impression of one offer suppresses further impressions. */
    private static final int IMPRESSION_DEDUP_HOURS = 6;

    /** How far back a click may be to receive credit for a conversion. */
    private static final int ATTRIBUTION_WINDOW_HOURS = 24;

    /** Conversion rate at or above this counts as a strong campaign. */
    private static final double STRONG_CONVERSION_PCT = 3.0;

    /** Conversion rate below this, with enough clicks to judge, counts as weak. */
    private static final double WEAK_CONVERSION_PCT = 0.5;

    /** Clicks below this and a campaign is not judged at all. */
    private static final long MIN_CLICKS_TO_JUDGE = 20;

    /** Longest session hash accepted, matching the column. */
    private static final int MAX_SESSION_HASH = 64;

    private final OfferRepository offerRepository;
    private final OfferEventRepository eventRepository;

    public MarketingPerformanceService(OfferRepository offerRepository,
                                       OfferEventRepository eventRepository) {
        this.offerRepository = offerRepository;
        this.eventRepository = eventRepository;
    }

    // =====================================================================
    //  Tracking
    // =====================================================================

    /**
     * Records that an offer was shown to a visitor.
     *
     * <p>Silently ignores a repeat inside the de-duplication window rather than
     * reporting an error. The storefront fires this on render and has no way to know
     * whether this visitor has already seen the offer today; making that its problem
     * would push session bookkeeping into the browser.</p>
     */
    @Transactional
    public void trackImpression(Long offerId, String sessionHash) {
        String session = sanitise(sessionHash);
        if (offerId == null || session == null) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(IMPRESSION_DEDUP_HOURS);
        if (eventRepository.existsRecent(offerId, OfferEventType.IMPRESSION, session, since)) {
            return;
        }
        offerRepository.findById(offerId).ifPresent(offer ->
                eventRepository.save(new OfferEvent(offer, OfferEventType.IMPRESSION, session)));
    }

    /** Records that a visitor activated an offer's call to action. */
    @Transactional
    public void trackClick(Long offerId, String sessionHash) {
        String session = sanitise(sessionHash);
        if (offerId == null || session == null) {
            return;
        }
        offerRepository.findById(offerId).ifPresent(offer ->
                eventRepository.save(new OfferEvent(offer, OfferEventType.CLICK, session)));
    }

    /**
     * Credits an order to the campaign the visitor last clicked.
     *
     * <p>Called when an order is placed. If the session clicked nothing inside the
     * attribution window, nothing is recorded — an unattributed sale is a real
     * category, and assigning it to whichever campaign happens to be running would
     * manufacture a performance figure out of a coincidence.</p>
     */
    @Transactional
    public void trackConversion(String sessionHash, Long orderId, BigDecimal orderValue) {
        String session = sanitise(sessionHash);
        if (session == null || orderId == null) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(ATTRIBUTION_WINDOW_HOURS);
        List<OfferEvent> clicks = eventRepository.recentClicksBySession(session, since);
        if (clicks.isEmpty()) {
            return;
        }
        OfferEvent lastClick = clicks.get(0);
        OfferEvent conversion = new OfferEvent(
                lastClick.getOffer(), OfferEventType.CONVERSION, session);
        conversion.setOrderRef(orderId);
        conversion.setOrderValue(orderValue);
        eventRepository.save(conversion);
    }

    /**
     * Bounds and validates a session hash before it reaches the database.
     *
     * <p>The value arrives from a browser and is stored, so it is untrusted input by
     * definition. Anything not a plain hex-ish token of reasonable length is dropped:
     * this column exists for de-duplication, and a caller sending arbitrary content
     * would be using it as free storage.</p>
     */
    private static String sanitise(String sessionHash) {
        if (sessionHash == null) {
            return null;
        }
        String trimmed = sessionHash.trim();
        if (trimmed.length() < 8 || trimmed.length() > MAX_SESSION_HASH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return null;
            }
        }
        return trimmed;
    }

    // =====================================================================
    //  Reporting
    // =====================================================================

    /** The whole panel for a window. */
    @Transactional(readOnly = true)
    public MarketingPerformanceDto performance(MetricRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        // One query gives every campaign's whole funnel. Three queries per campaign
        // would be dozens of round trips for a table of ten rows.
        Map<Long, Funnel> funnels = new HashMap<>();
        for (Object[] row : eventRepository.funnelTotals(from, to)) {
            Long offerId = ((Number) row[0]).longValue();
            OfferEventType type = (OfferEventType) row[1];
            long count = ((Number) row[2]).longValue();
            BigDecimal revenue = row[3] == null ? BigDecimal.ZERO : (BigDecimal) row[3];
            funnels.computeIfAbsent(offerId, k -> new Funnel()).add(type, count, revenue);
        }

        List<Offer> offers = offerRepository.findAll();
        List<MarketingPerformanceDto.Campaign> campaigns = new ArrayList<>(offers.size());

        long totalImpressions = 0;
        long totalClicks = 0;
        long totalConversions = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Offer offer : offers) {
            Funnel f = funnels.getOrDefault(offer.getId(), Funnel.EMPTY);

            totalImpressions += f.impressions;
            totalClicks += f.clicks;
            totalConversions += f.conversions;
            totalRevenue = totalRevenue.add(f.revenue);

            BigDecimal cost = offer.getCampaignCost();
            if (cost != null) {
                totalCost = totalCost.add(cost);
            }

            Double ctr = ratio(f.clicks, f.impressions);
            Double conversionPct = ratio(f.conversions, f.clicks);
            BigDecimal cpa = cost == null || f.conversions == 0
                    ? null
                    : cost.divide(BigDecimal.valueOf(f.conversions), 2, RoundingMode.HALF_UP);
            Double roas = cost == null || cost.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : f.revenue.multiply(BigDecimal.valueOf(100))
                            .divide(cost, 2, RoundingMode.HALF_UP)
                            .doubleValue();

            campaigns.add(new MarketingPerformanceDto.Campaign(
                    offer.getId(),
                    offer.getTitle(),
                    offer.getPlacement() == null ? null : offer.getPlacement().name(),
                    statusOf(offer, now),
                    offer.getStartsAt(),
                    offer.getEndsAt(),
                    f.impressions,
                    f.clicks,
                    f.conversions,
                    MetricsService.scale(f.revenue),
                    cost == null ? null : MetricsService.scale(cost),
                    ctr,
                    conversionPct,
                    cpa,
                    roas,
                    verdictOf(f, conversionPct)
            ));
        }

        // Best performers first; unmeasured campaigns sink to the bottom rather than
        // appearing among the failures, which is where a zero would put them.
        campaigns.sort((a, b) -> {
            int byConversions = Long.compare(b.conversions(), a.conversions());
            return byConversions != 0
                    ? byConversions
                    : Long.compare(b.impressions(), a.impressions());
        });

        Funnel previous = windowFunnel(range.previousFrom(now), range.previousTo(now));

        return new MarketingPerformanceDto(
                campaigns,
                dailySeries(range, now, from, to, OfferEventType.CONVERSION),
                ctrSeries(range, now, from, to),
                cpaSeries(range, now, from, to, totalCost),
                evolutionSeries(range, now, from, to),
                totalImpressions,
                totalClicks,
                totalConversions,
                MetricsService.scale(totalRevenue),
                MetricsService.scale(totalCost),
                ratio(totalClicks, totalImpressions),
                ratio(totalConversions, totalClicks),
                totalConversions == 0 || totalCost.compareTo(BigDecimal.ZERO) == 0
                        ? null
                        : totalCost.divide(BigDecimal.valueOf(totalConversions), 2, RoundingMode.HALF_UP),
                totalCost.compareTo(BigDecimal.ZERO) == 0
                        ? null
                        : totalRevenue.multiply(BigDecimal.valueOf(100))
                                .divide(totalCost, 2, RoundingMode.HALF_UP)
                                .doubleValue(),
                DeltaDto.higherIsBetter(
                        pct(ratio(totalClicks, totalImpressions)),
                        pct(ratio(previous.clicks, previous.impressions))),
                DeltaDto.higherIsBetter(
                        pct(ratio(totalConversions, totalClicks)),
                        pct(ratio(previous.conversions, previous.clicks))),
                eventRepository.earliestEvent(),
                MetricsService.CURRENCY,
                range.info(now, eventRepository.earliestEvent())
        );
    }

    private Funnel windowFunnel(LocalDateTime from, LocalDateTime to) {
        Funnel total = new Funnel();
        for (Object[] row : eventRepository.funnelTotals(from, to)) {
            total.add((OfferEventType) row[1],
                    ((Number) row[2]).longValue(),
                    row[3] == null ? BigDecimal.ZERO : (BigDecimal) row[3]);
        }
        return total;
    }

    private List<SeriesPointDto> dailySeries(MetricRange range,
                                             LocalDateTime now,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             OfferEventType type) {
        Map<String, Long> counts = dailyCounts(from, to, type);
        List<SeriesPointDto> out = new ArrayList<>();
        for (String label : dayLabels(from, now)) {
            long value = counts.getOrDefault(label, 0L);
            out.add(SeriesPointDto.of(label, BigDecimal.valueOf(value), value));
        }
        return out;
    }

    /** Click-through rate per day. Days with no impressions carry null, not zero. */
    private List<SeriesPointDto> ctrSeries(MetricRange range,
                                           LocalDateTime now,
                                           LocalDateTime from,
                                           LocalDateTime to) {
        Map<String, Long> impressions = dailyCounts(from, to, OfferEventType.IMPRESSION);
        Map<String, Long> clicks = dailyCounts(from, to, OfferEventType.CLICK);

        List<SeriesPointDto> out = new ArrayList<>();
        for (String label : dayLabels(from, now)) {
            long shown = impressions.getOrDefault(label, 0L);
            long clicked = clicks.getOrDefault(label, 0L);
            // No impressions means no rate. Zero would draw the line to the floor on
            // every quiet day and make a genuine drop indistinguishable from silence.
            BigDecimal value = shown == 0
                    ? null
                    : BigDecimal.valueOf(clicked * 100.0 / shown).setScale(2, RoundingMode.HALF_UP);
            out.add(new SeriesPointDto(label, value, null, shown));
        }
        return out;
    }

    /**
     * Cost per acquisition per day.
     *
     * <p>The daily cost is the window's total cost spread evenly across its days. That
     * is an assumption and it is stated here rather than presented as a measurement: the
     * offers table records a campaign's total cost, not a daily spend schedule, so a
     * per-day figure cannot be derived any other way. Days with no conversions carry
     * null.</p>
     */
    private List<SeriesPointDto> cpaSeries(MetricRange range,
                                           LocalDateTime now,
                                           LocalDateTime from,
                                           LocalDateTime to,
                                           BigDecimal totalCost) {
        Map<String, Long> conversions = dailyCounts(from, to, OfferEventType.CONVERSION);
        List<String> labels = dayLabels(from, now);

        BigDecimal dailyCost = labels.isEmpty() || totalCost.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalCost.divide(BigDecimal.valueOf(labels.size()), 4, RoundingMode.HALF_UP);

        List<SeriesPointDto> out = new ArrayList<>(labels.size());
        for (String label : labels) {
            long converted = conversions.getOrDefault(label, 0L);
            BigDecimal value = converted == 0 || dailyCost.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : dailyCost.divide(BigDecimal.valueOf(converted), 2, RoundingMode.HALF_UP);
            out.add(new SeriesPointDto(label, value, null, converted));
        }
        return out;
    }

    /** Impressions and clicks on one chart, so the top of the funnel reads as a pair. */
    private List<SeriesPointDto> evolutionSeries(MetricRange range,
                                                 LocalDateTime now,
                                                 LocalDateTime from,
                                                 LocalDateTime to) {
        Map<String, Long> impressions = dailyCounts(from, to, OfferEventType.IMPRESSION);
        Map<String, Long> clicks = dailyCounts(from, to, OfferEventType.CLICK);

        List<SeriesPointDto> out = new ArrayList<>();
        for (String label : dayLabels(from, now)) {
            long shown = impressions.getOrDefault(label, 0L);
            long clicked = clicks.getOrDefault(label, 0L);
            out.add(SeriesPointDto.of(label,
                    BigDecimal.valueOf(shown), BigDecimal.valueOf(clicked), shown + clicked));
        }
        return out;
    }

    private Map<String, Long> dailyCounts(LocalDateTime from, LocalDateTime to, OfferEventType type) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : eventRepository.dailyCounts(type, from, to)) {
            out.put(String.format("%04d-%02d-%02d",
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()),
                    ((Number) row[3]).longValue());
        }
        return out;
    }

    private static List<String> dayLabels(LocalDateTime from, LocalDateTime now) {
        List<String> labels = new ArrayList<>();
        LocalDate cursor = from.toLocalDate();
        LocalDate end = now.toLocalDate();
        while (!cursor.isAfter(end)) {
            labels.add(cursor.toString());
            cursor = cursor.plusDays(1);
        }
        return labels;
    }

    /** A campaign's state, derived from its own dates so it matches the offers page. */
    private static String statusOf(Offer offer, LocalDateTime now) {
        if (!offer.isActive()) {
            return "INACTIVE";
        }
        if (offer.getStartsAt() != null && offer.getStartsAt().isAfter(now)) {
            return "SCHEDULED";
        }
        if (offer.getEndsAt() != null && offer.getEndsAt().isBefore(now)) {
            return "ENDED";
        }
        return "ACTIVE";
    }

    /**
     * How a campaign is doing, or that it cannot be judged yet.
     *
     * <p>{@code NO_DATA} is a distinct verdict rather than a bad score, and the card
     * colours it neutrally. A campaign nobody has clicked twenty times has not failed;
     * it has not been tested.</p>
     */
    private static String verdictOf(Funnel f, Double conversionPct) {
        if (f.impressions == 0) {
            return "NO_DATA";
        }
        if (f.clicks < MIN_CLICKS_TO_JUDGE) {
            return "NO_DATA";
        }
        if (conversionPct == null) {
            return "NO_DATA";
        }
        if (conversionPct >= STRONG_CONVERSION_PCT) {
            return "STRONG";
        }
        if (conversionPct < WEAK_CONVERSION_PCT) {
            return "WEAK";
        }
        return "OK";
    }

    private static Double ratio(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static BigDecimal pct(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** One campaign's funnel totals. */
    private static final class Funnel {

        static final Funnel EMPTY = new Funnel();

        private long impressions;
        private long clicks;
        private long conversions;
        private BigDecimal revenue = BigDecimal.ZERO;

        void add(OfferEventType type, long count, BigDecimal value) {
            switch (type) {
                case IMPRESSION -> impressions += count;
                case CLICK -> clicks += count;
                case CONVERSION -> {
                    conversions += count;
                    revenue = revenue.add(value == null ? BigDecimal.ZERO : value);
                }
            }
        }
    }
}
