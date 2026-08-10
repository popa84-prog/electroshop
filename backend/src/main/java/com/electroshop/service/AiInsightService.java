package com.electroshop.service;

import com.electroshop.dto.AiInsightsDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automated suggestions and order-pattern analysis for administrators.
 *
 * <p>Task 7.</p>
 *
 * <h2>What "AI" means here, stated plainly</h2>
 *
 * <p>A deterministic rules engine over the store's own data. No language model is
 * involved, none is configured, and the response says {@code source: RULES} so nobody
 * has to guess.</p>
 *
 * <p>This is a deliberate trade and it is the better one for a business panel. A rules
 * engine can attach the figures that produced each suggestion, which means an operator
 * can disagree with it on evidence: "it says sales fell 60%, but that was the week we
 * were out of stock" is a conversation that improves the shop. A generated sentence
 * offers nothing to check, and a suggestion nobody can check is one people stop reading
 * after the first time it is confidently wrong. Every entry below therefore carries a
 * {@code rationale} containing real numbers.</p>
 *
 * <h2>Suggestions are ranked by money, not by rule order</h2>
 *
 * <p>A panel that lists whatever the first rule found teaches operators to read the top
 * two entries and ignore the rest. Where a rule can quantify its impact honestly it
 * does, and the list is sorted by that. Where it cannot, {@code impact} is null rather
 * than a plausible-looking estimate — a made-up number would sort itself to the top.</p>
 */
@Service
public class AiInsightService {

    /** How many suggestions the panel returns. */
    private static final int SUGGESTION_LIMIT = 12;

    /** Units below this in the window counts as low sales for a stocked product. */
    private static final long LOW_SALES_UNITS = 2;

    /** Stock above this with no movement counts as excess. */
    private static final int EXCESS_STOCK_UNITS = 40;

    /** Margin below this is flagged as a risk. */
    private static final double THIN_MARGIN_PCT = 8.0;

    /** Margin at or above this makes a slow-moving product a promotion candidate. */
    private static final double STRONG_MARGIN_PCT = 25.0;

    /** Days of cover at or below this is an urgent restock. */
    private static final double URGENT_COVER_DAYS = 10.0;

    private static final String[] WEEKDAY_RO = {
            "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"
    };

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public AiInsightService(ProductRepository productRepository,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /** Suggestions and patterns for a window. */
    @Transactional(readOnly = true)
    public AiInsightsDto insights(MetricRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);
        long windowDays = Math.max(1, range.lengthInDays(now));

        Map<Long, Long> units = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldPerProduct(from, to)) {
            units.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        List<AiInsightsDto.Suggestion> suggestions =
                buildSuggestions(units, windowDays);

        // Highest quantified impact first; unquantified suggestions keep their relative
        // order behind the quantified ones rather than being dropped, because "cannot
        // be priced" is not the same as "does not matter".
        suggestions.sort((a, b) -> {
            BigDecimal ia = a.impact();
            BigDecimal ib = b.impact();
            if (ia == null && ib == null) {
                return 0;
            }
            if (ia == null) {
                return 1;
            }
            if (ib == null) {
                return -1;
            }
            return ib.compareTo(ia);
        });

        if (suggestions.size() > SUGGESTION_LIMIT) {
            suggestions = new ArrayList<>(suggestions.subList(0, SUGGESTION_LIMIT));
        }

        return new AiInsightsDto(
                suggestions,
                orderPatterns(from, to),
                now.toString(),
                "RULES",
                MetricsService.CURRENCY,
                range.info(now)
        );
    }

    /**
     * Runs every rule over the active catalogue.
     *
     * <p>One pass over the products, all rules applied per row. Running each rule as its
     * own pass would read the catalogue six times to produce one list.</p>
     */
    private List<AiInsightsDto.Suggestion> buildSuggestions(Map<Long, Long> units,
                                                            long windowDays) {
        List<AiInsightsDto.Suggestion> out = new ArrayList<>();

        for (Object[] row : productRepository.findActiveForAnalysis()) {
            Long id = ((Number) row[0]).longValue();
            String name = (String) row[1];
            BigDecimal price = (BigDecimal) row[5];
            BigDecimal cost = (BigDecimal) row[6];
            int stock = ((Number) row[7]).intValue();
            long sold = units.getOrDefault(id, 0L);

            Double marginPct = marginOf(price, cost);
            Double cover = coverDays(stock, sold, windowDays);

            // --- Priced below cost -------------------------------------------------
            // The most serious rule, and the only one whose impact is exact rather than
            // estimated: every unit sold loses a known amount.
            if (cost != null && price != null && price.compareTo(cost) < 0) {
                BigDecimal lossPerUnit = cost.subtract(price);
                BigDecimal exposure = lossPerUnit.multiply(BigDecimal.valueOf(stock));
                out.add(new AiInsightsDto.Suggestion(
                        "PRICE_BELOW_COST-" + id,
                        "PRICE_BELOW_COST",
                        "DANGER",
                        "Preț sub costul de achiziție: " + name,
                        String.format(
                                "Prețul de vânzare este %s, iar costul de achiziție este %s. "
                                        + "Fiecare bucată vândută pierde %s %s, iar pe stoc sunt "
                                        + "%d bucăți.",
                                plain(price), plain(cost), plain(lossPerUnit),
                                MetricsService.CURRENCY, stock),
                        MetricsService.scale(exposure),
                        "HIGH",
                        List.of(id),
                        "/admin/products?id=" + id,
                        "Corectează prețul"));
                continue;
            }

            // --- Urgent restock ----------------------------------------------------
            if (cover != null && cover <= URGENT_COVER_DAYS && sold > 0) {
                BigDecimal marginPerUnit = cost == null || price == null
                        ? null
                        : price.subtract(cost);
                // Impact is the margin on the units that would otherwise not be sold
                // over the next month at the current rate. An estimate, and labelled as
                // one by the confidence field rather than presented as certainty.
                BigDecimal impact = marginPerUnit == null
                        ? null
                        : MetricsService.scale(marginPerUnit
                                .multiply(BigDecimal.valueOf(sold))
                                .multiply(BigDecimal.valueOf(30))
                                .divide(BigDecimal.valueOf(windowDays), 2, RoundingMode.HALF_UP));
                out.add(new AiInsightsDto.Suggestion(
                        "RESTOCK_URGENT-" + id,
                        "RESTOCK_URGENT",
                        "DANGER",
                        "Stoc pe terminate: " + name,
                        String.format(
                                "S-au vândut %d bucăți în perioada analizată. Stocul de %d "
                                        + "bucăți mai acoperă aproximativ %.1f zile.",
                                sold, stock, cover),
                        impact,
                        sold >= 5 ? "HIGH" : "MEDIUM",
                        List.of(id),
                        "/admin/purchases",
                        "Creează recepție"));
                continue;
            }

            // --- Excess stock, no movement ------------------------------------------
            if (stock >= EXCESS_STOCK_UNITS && sold == 0) {
                BigDecimal tiedUp = cost == null
                        ? null
                        : MetricsService.scale(cost.multiply(BigDecimal.valueOf(stock)));
                out.add(new AiInsightsDto.Suggestion(
                        "EXCESS_STOCK-" + id,
                        "EXCESS_STOCK",
                        "WARNING",
                        "Stoc mare fără vânzări: " + name,
                        String.format(
                                "Sunt %d bucăți pe stoc și nu s-a vândut niciuna în perioada "
                                        + "analizată.%s",
                                stock,
                                tiedUp == null ? ""
                                        : String.format(" Capital imobilizat: %s %s.",
                                        plain(tiedUp), MetricsService.CURRENCY)),
                        tiedUp,
                        "HIGH",
                        List.of(id),
                        "/admin/offers",
                        "Creează promoție"));
                continue;
            }

            // --- Promotion opportunity ----------------------------------------------
            if (marginPct != null && marginPct >= STRONG_MARGIN_PCT
                    && sold <= LOW_SALES_UNITS && stock > 0) {
                out.add(new AiInsightsDto.Suggestion(
                        "PROMO_OPPORTUNITY-" + id,
                        "PROMO_OPPORTUNITY",
                        "INFO",
                        "Marjă bună, vânzări mici: " + name,
                        String.format(
                                "Marja este de %.1f%%, dar s-au vândut doar %d bucăți în "
                                        + "perioada analizată, deși sunt %d pe stoc.",
                                marginPct, sold, stock),
                        null,
                        "MEDIUM",
                        List.of(id),
                        "/admin/offers",
                        "Creează promoție"));
                continue;
            }

            // --- Thin margin on a product that does sell -----------------------------
            if (marginPct != null && marginPct < THIN_MARGIN_PCT && sold > 0) {
                out.add(new AiInsightsDto.Suggestion(
                        "MARGIN_RISK-" + id,
                        "MARGIN_RISK",
                        "WARNING",
                        "Marjă subțire pe un produs care se vinde: " + name,
                        String.format(
                                "Marja este de doar %.1f%%, iar produsul s-a vândut în %d "
                                        + "bucăți. Volumul amplifică efectul unei marje mici.",
                                marginPct, sold),
                        null,
                        "MEDIUM",
                        List.of(id),
                        "/admin/products?id=" + id,
                        "Revizuiește prețul"));
                continue;
            }

            // --- Low sales on a stocked product --------------------------------------
            if (sold <= LOW_SALES_UNITS && stock > 0 && stock < EXCESS_STOCK_UNITS) {
                out.add(new AiInsightsDto.Suggestion(
                        "LOW_SALES-" + id,
                        "LOW_SALES",
                        "INFO",
                        "Vânzări scăzute: " + name,
                        String.format(
                                "S-au vândut %d bucăți în perioada analizată, cu %d bucăți "
                                        + "disponibile pe stoc.",
                                sold, stock),
                        null,
                        "LOW",
                        List.of(id),
                        "/admin/products?id=" + id,
                        null));
            }
        }

        return out;
    }

    /**
     * What the order history says about when and how people buy.
     *
     * <p>The observations are sentences assembled from the figures immediately above
     * them in the same record, so nothing in the text can contradict the charts beside
     * it.</p>
     */
    private AiInsightsDto.OrderPatterns orderPatterns(LocalDateTime from, LocalDateTime to) {
        long[] byHour = new long[24];
        BigDecimal[] revenueByHour = new BigDecimal[24];
        java.util.Arrays.fill(revenueByHour, BigDecimal.ZERO);

        for (Object[] row : orderRepository.countByHourBetween(from, to)) {
            int hour = ((Number) row[0]).intValue();
            if (hour >= 0 && hour < 24) {
                byHour[hour] = ((Number) row[1]).longValue();
                revenueByHour[hour] = ProfitAnalyticsService.dec(row[2]);
            }
        }

        long[] byWeekday = new long[7];
        for (Object[] row : orderRepository.revenueByDayBetween(from, to)) {
            java.time.LocalDate day = java.time.LocalDate.of(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            byWeekday[day.getDayOfWeek().getValue() - 1] += ((Number) row[4]).longValue();
        }

        List<SeriesPointDto> hourSeries = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            hourSeries.add(SeriesPointDto.of(
                    String.format("%02d:00", h), BigDecimal.valueOf(byHour[h]), byHour[h]));
        }

        List<SeriesPointDto> weekdaySeries = new ArrayList<>(7);
        for (int d = 0; d < 7; d++) {
            weekdaySeries.add(SeriesPointDto.of(
                    WEEKDAY_RO[d], BigDecimal.valueOf(byWeekday[d]), byWeekday[d]));
        }

        int peakHour = indexOfMax(byHour);
        int quietHour = indexOfQuietest(byHour);
        int peakWeekday = indexOfMax(byWeekday);

        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (int h = 0; h < 24; h++) {
            totalOrders += byHour[h];
            totalRevenue = totalRevenue.add(revenueByHour[h]);
        }

        BigDecimal avgBasket = totalOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        Double avgLines = averageLines(from, to);

        List<String> observations = new ArrayList<>();
        if (totalOrders == 0) {
            // No orders means no pattern. Saying so is the honest observation; the
            // alternative is a sentence about a peak hour that is simply the first
            // index of an array of zeroes.
            observations.add("Nu există comenzi în perioada analizată, deci nu se pot "
                    + "identifica tipare.");
        } else {
            observations.add(String.format(
                    "Cele mai multe comenzi sosesc în intervalul %02d:00–%02d:00 (%d comenzi).",
                    peakHour, peakHour + 1, byHour[peakHour]));
            observations.add(String.format(
                    "Ziua cea mai activă este %s, cu %d comenzi.",
                    WEEKDAY_RO[peakWeekday], byWeekday[peakWeekday]));
            observations.add(String.format(
                    "Valoarea medie a unei comenzi este %s %s.",
                    plain(avgBasket), MetricsService.CURRENCY));
            if (avgLines != null) {
                observations.add(String.format(
                        "O comandă conține în medie %.1f produse.", avgLines));
            }
        }

        return new AiInsightsDto.OrderPatterns(
                hourSeries,
                weekdaySeries,
                totalOrders == 0 ? null : String.format("%02d:00", peakHour),
                totalOrders == 0 ? null : WEEKDAY_RO[peakWeekday],
                totalOrders == 0 || quietHour < 0 ? null : String.format("%02d:00", quietHour),
                avgBasket,
                avgLines,
                null,
                observations
        );
    }

    private Double averageLines(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = orderItemRepository.linesPerOrder(from, to);
        if (rows.isEmpty()) {
            return null;
        }
        long total = 0;
        for (Object[] row : rows) {
            total += ((Number) row[1]).longValue();
        }
        return BigDecimal.valueOf((double) total / rows.size())
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static int indexOfMax(long[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    /**
     * The quietest hour that still saw activity.
     *
     * <p>Hours with no orders at all are skipped. Reporting 04:00 as the quietest hour
     * when the shop simply has no night traffic is a fact about sleep, not about the
     * business, and it would be the answer every single time.</p>
     */
    private static int indexOfQuietest(long[] values) {
        int best = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0 && (best < 0 || values[i] < values[best])) {
                best = i;
            }
        }
        return best;
    }

    private static Double marginOf(BigDecimal price, BigDecimal cost) {
        if (price == null || cost == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return price.subtract(cost)
                .multiply(BigDecimal.valueOf(100))
                .divide(price, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double coverDays(int stock, long sold, long windowDays) {
        if (sold <= 0 || windowDays <= 0) {
            return null;
        }
        double perDay = (double) sold / windowDays;
        return BigDecimal.valueOf(stock / perDay)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static String plain(BigDecimal v) {
        return v == null ? "—" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
