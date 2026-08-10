package com.electroshop.service;

import com.electroshop.dto.ProductPerformanceDto;
import com.electroshop.dto.SeriesPointDto;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which products are rising, which are falling, and which have stopped moving.
 *
 * <p>Task 18.</p>
 *
 * <h2>Growth is measured against a product's own past, and requires a floor</h2>
 *
 * <p>A product that went from two units to six grew by 200%. A product that went from
 * four hundred to four hundred and forty grew by 10% and sold seventy times more. A
 * ranking by percentage alone puts the first at the top of "fastest growing" and buries
 * the second, which means the panel's headline list is populated entirely by noise from
 * the tail of the catalogue.</p>
 *
 * <p>Two things prevent that. A percentage is only reported when the baseline period had
 * at least {@link #MIN_BASELINE_UNITS} units, and every row carries the absolute unit
 * change beside the percentage so the two can be read together. The floor is returned in
 * the response as {@code minVolumeForTrend} rather than left implicit, because a reader
 * who does not know a product was excluded will assume it did not move.</p>
 *
 * <h2>Stagnant means "could sell and did not"</h2>
 *
 * <p>A product with no sales because it is out of stock is an inventory failure, not a
 * demand failure, and it belongs in the inventory panel where somebody can order more.
 * Listing it here as a demand problem would send an operator to discount something they
 * cannot even ship. Stagnation is therefore restricted to products that had stock
 * available and still sold nothing.</p>
 */
@Service
public class ProductPerformanceService {

    /** Baseline units below which no percentage change is reported. */
    static final long MIN_BASELINE_UNITS = 3;

    /** How many rows each of the three trend sections returns. */
    private static final int SECTION_LIMIT = 15;

    /** How many recommendations the rules engine produces here. */
    private static final int RECOMMENDATION_LIMIT = 10;

    /** Margin at or above this is considered strong enough to be worth promoting. */
    private static final double STRONG_MARGIN_PCT = 25.0;

    /** A decline steeper than this is flagged as serious. */
    private static final double SEVERE_DECLINE_PCT = -40.0;

    /** Growth above this is flagged as a success. */
    private static final double STRONG_GROWTH_PCT = 40.0;

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductPerformanceService(ProductRepository productRepository,
                                     OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /** The whole panel for a window. */
    public ProductPerformanceDto performance(MetricRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = range.from(now);
        LocalDateTime to = range.to(now);

        Map<Long, Long> current = unitsPerProduct(from, to);
        Map<Long, Long> previous = unitsPerProduct(range.previousFrom(now), range.previousTo(now));

        // Revenue and profit for the window, keyed by product, so a trend row can show
        // what the movement is worth rather than only that it happened.
        Map<Long, BigDecimal[]> money = new HashMap<>();
        for (Object[] row : orderItemRepository.moneyPerProduct(from, to)) {
            money.put(((Number) row[0]).longValue(), new BigDecimal[]{
                    ProfitAnalyticsService.dec(row[1]),
                    ProfitAnalyticsService.dec(row[2])
            });
        }

        List<ProductPerformanceDto.ProductTrend> rising = new ArrayList<>();
        List<ProductPerformanceDto.ProductTrend> declining = new ArrayList<>();
        List<ProductPerformanceDto.ProductTrend> stagnant = new ArrayList<>();
        Map<String, Long> categoryMovement = new LinkedHashMap<>();

        long analysed = 0;

        for (Object[] row : productRepository.findActiveForAnalysis()) {
            Long id = ((Number) row[0]).longValue();
            String name = (String) row[1];
            String imageUrl = (String) row[2];
            String brand = (String) row[3];
            String category = (String) row[4];
            BigDecimal price = (BigDecimal) row[5];
            BigDecimal purchasePrice = (BigDecimal) row[6];
            int stock = ((Number) row[7]).intValue();

            long unitsNow = current.getOrDefault(id, 0L);
            long unitsPrev = previous.getOrDefault(id, 0L);
            long delta = unitsNow - unitsPrev;

            String categoryKey = category == null || category.isBlank() ? "Fără categorie" : category;
            categoryMovement.merge(categoryKey, delta, Long::sum);

            // A percentage is only trustworthy over a baseline with some substance.
            Double changePct = unitsPrev >= MIN_BASELINE_UNITS
                    ? round2((double) delta * 100 / unitsPrev)
                    : null;

            BigDecimal[] m = money.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            Double cover = daysOfCover(stock, unitsNow, range.lengthInDays(now));

            ProductPerformanceDto.ProductTrend trend = new ProductPerformanceDto.ProductTrend(
                    id, name, imageUrl, brand, category,
                    unitsNow, unitsPrev, delta, changePct,
                    MetricsService.scale(m[0]), MetricsService.scale(m[1]),
                    stock, cover,
                    severityFor(changePct, delta, unitsNow, stock));

            if (unitsNow == 0 && stock > 0) {
                // Had stock, sold nothing: a demand problem, which is this panel's
                // subject. Out-of-stock products are deliberately not here.
                stagnant.add(trend);
            } else if (delta > 0 && unitsPrev > 0) {
                rising.add(trend);
                analysed++;
            } else if (delta < 0 && unitsPrev >= MIN_BASELINE_UNITS) {
                declining.add(trend);
                analysed++;
            } else if (unitsNow > 0) {
                analysed++;
            }
        }

        // Rising is ranked by absolute units gained, not by percentage, for the reason
        // in the class comment. The percentage is displayed; it does not decide order.
        rising.sort((a, b) -> Long.compare(b.unitsDelta(), a.unitsDelta()));
        declining.sort((a, b) -> Long.compare(a.unitsDelta(), b.unitsDelta()));
        // Stagnant is ranked by capital at risk: the biggest pile of unsold stock first.
        stagnant.sort((a, b) -> Integer.compare(b.stockQuantity(), a.stockQuantity()));

        return new ProductPerformanceDto(
                cap(rising),
                cap(declining),
                cap(stagnant),
                recommendations(rising, declining, stagnant),
                categorySeries(categoryMovement),
                analysed,
                MIN_BASELINE_UNITS,
                MetricsService.CURRENCY,
                range.info(now)
        );
    }

    private Map<Long, Long> unitsPerProduct(LocalDateTime from, LocalDateTime to) {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldPerProduct(from, to)) {
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return out;
    }

    private static List<ProductPerformanceDto.ProductTrend> cap(
            List<ProductPerformanceDto.ProductTrend> list) {
        return list.size() > SECTION_LIMIT
                ? new ArrayList<>(list.subList(0, SECTION_LIMIT))
                : list;
    }

    /**
     * Net unit movement per category, worst first.
     *
     * <p>Signed on purpose. A category chart of gross volume shows which categories are
     * big; a chart of net change shows which ones are moving, and only the second one
     * says anything an operator did not already know.</p>
     */
    private static List<SeriesPointDto> categorySeries(Map<String, Long> movement) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(movement.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<SeriesPointDto> out = new ArrayList<>(entries.size());
        for (Map.Entry<String, Long> entry : entries) {
            out.add(SeriesPointDto.of(entry.getKey(),
                    BigDecimal.valueOf(entry.getValue()), entry.getValue()));
        }
        return out;
    }

    private static Double daysOfCover(int stock, long unitsInWindow, long windowDays) {
        if (unitsInWindow <= 0 || windowDays <= 0) {
            return null;
        }
        double perDay = (double) unitsInWindow / windowDays;
        return round2(stock / perDay);
    }

    private static String severityFor(Double changePct, long delta, long unitsNow, int stock) {
        if (unitsNow == 0 && stock > 0) {
            return "WARNING";
        }
        if (changePct != null && changePct <= SEVERE_DECLINE_PCT) {
            return "DANGER";
        }
        if (changePct != null && changePct >= STRONG_GROWTH_PCT) {
            return "SUCCESS";
        }
        if (delta < 0) {
            return "WARNING";
        }
        return "INFO";
    }

    /**
     * Promotion and pricing recommendations, each with the figures behind it.
     *
     * <p>Deterministic rules over the store's own data, not a generated opinion. Every
     * entry states the numbers that produced it, so an operator can disagree on
     * evidence — which is the property that decides whether a suggestion panel gets
     * read a second time.</p>
     */
    private List<ProductPerformanceDto.Recommendation> recommendations(
            List<ProductPerformanceDto.ProductTrend> rising,
            List<ProductPerformanceDto.ProductTrend> declining,
            List<ProductPerformanceDto.ProductTrend> stagnant) {

        List<ProductPerformanceDto.Recommendation> out = new ArrayList<>();

        // A rising product about to run out is the most expensive thing on the panel:
        // demand is proven and the shelf is emptying.
        for (ProductPerformanceDto.ProductTrend t : rising) {
            if (out.size() >= RECOMMENDATION_LIMIT) {
                break;
            }
            if (t.daysOfCover() != null && t.daysOfCover() < 21) {
                out.add(new ProductPerformanceDto.Recommendation(
                        t.productId(), t.name(), t.imageUrl(),
                        "RESTOCK",
                        "Cerere în creștere, stoc pe terminate",
                        String.format(
                                "Vânzările au crescut de la %d la %d bucăți (%+d). "
                                        + "Stocul de %d bucăți mai acoperă %.1f zile.",
                                t.unitsPrevious(), t.unitsCurrent(), t.unitsDelta(),
                                t.stockQuantity(), t.daysOfCover()),
                        t.unitsPrevious() >= MIN_BASELINE_UNITS ? "HIGH" : "MEDIUM",
                        t.profitCurrent()));
            }
        }

        // A product falling steeply is either priced wrong or being outcompeted. The
        // rule does not claim to know which; it says the decline is real and how large.
        for (ProductPerformanceDto.ProductTrend t : declining) {
            if (out.size() >= RECOMMENDATION_LIMIT) {
                break;
            }
            if (t.changePct() != null && t.changePct() <= SEVERE_DECLINE_PCT) {
                out.add(new ProductPerformanceDto.Recommendation(
                        t.productId(), t.name(), t.imageUrl(),
                        "REVIEW_PRICE",
                        "Scădere accentuată a vânzărilor",
                        String.format(
                                "Vânzările au scăzut de la %d la %d bucăți (%.1f%%). "
                                        + "Au rămas %d bucăți pe stoc.",
                                t.unitsPrevious(), t.unitsCurrent(), t.changePct(),
                                t.stockQuantity()),
                        "MEDIUM",
                        null));
            }
        }

        // Stock that had every chance to sell and did not.
        for (ProductPerformanceDto.ProductTrend t : stagnant) {
            if (out.size() >= RECOMMENDATION_LIMIT) {
                break;
            }
            out.add(new ProductPerformanceDto.Recommendation(
                    t.productId(), t.name(), t.imageUrl(),
                    "PROMOTE",
                    "Produs disponibil, dar fără vânzări",
                    String.format(
                            "Nu s-a vândut nicio bucată în perioada analizată, deși "
                                    + "existau %d bucăți pe stoc.",
                            t.stockQuantity()),
                    "MEDIUM",
                    null));
        }

        return out;
    }

    private static Double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
