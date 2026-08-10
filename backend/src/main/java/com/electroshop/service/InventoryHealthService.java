package com.electroshop.service;

import com.electroshop.dto.InventoryHealthDto;
import com.electroshop.model.Product;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.PurchaseItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The state of the inventory, split into the four situations that need action.
 *
 * <p>Task 13.</p>
 *
 * <h2>Why overstock is judged by movement, not by quantity</h2>
 *
 * <p>The requirement names a threshold: more than a hundred units is overstocked. That
 * threshold alone cannot distinguish two opposite situations. A hundred units of
 * something that sells thirty a week is three weeks of cover and completely healthy; a
 * hundred units of something that sold twice this year is capital that will still be
 * sitting there next Christmas. The threshold is applied as asked, and
 * {@code daysOfCover} is reported beside every row so the two cases are visibly
 * different. The list is ordered by capital tied up rather than by quantity, because
 * that is the number that decides whether a row is worth an afternoon.</p>
 *
 * <h2>Why out-of-stock and critical are separate lists</h2>
 *
 * <p>They need different actions on different timescales. A product at two units will
 * disappoint a customer next week; a product at zero is disappointing them right now
 * and has been for however long nobody noticed. Merging them into one list sorted by
 * quantity would bury the second at the top of a long tail of the first.</p>
 *
 * <h2>How a restock quantity is arrived at</h2>
 *
 * <p>Velocity over the last {@link #VELOCITY_WINDOW_DAYS} days, projected forward to a
 * {@link #COVER_TARGET_DAYS} target, minus what is already on hand. Every input is
 * returned with the recommendation so it can be argued with: an operator who knows a
 * product is seasonal can see that the engine did not, and override it on the evidence
 * rather than on suspicion.</p>
 */
@Service
public class InventoryHealthService {

    /** Stock strictly below this is critical. The figure the requirement names. */
    static final int CRITICAL_BELOW = 5;

    /** Stock strictly above this is overstocked. The figure the requirement names. */
    static final int OVERSTOCK_ABOVE = 100;

    /** How many days of sales the velocity is averaged over. */
    static final int VELOCITY_WINDOW_DAYS = 30;

    /** How many days of cover a restock recommendation aims to restore. */
    static final int COVER_TARGET_DAYS = 45;

    /** Assumed supplier lead time, used to decide how urgent a restock is. */
    static final int LEAD_TIME_DAYS = 14;

    /** How many rows each of the four sections returns. */
    private static final int SECTION_LIMIT = 50;

    /** How many restock recommendations are produced. */
    private static final int RESTOCK_LIMIT = 25;

    private static final String DANGER = "DANGER";
    private static final String WARNING = "WARNING";
    private static final String INFO = "INFO";

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final PurchaseItemRepository purchaseItemRepository;

    public InventoryHealthService(ProductRepository productRepository,
                                  OrderItemRepository orderItemRepository,
                                  PurchaseItemRepository purchaseItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.purchaseItemRepository = purchaseItemRepository;
    }

    /** The whole panel in one response. */
    public InventoryHealthDto health() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime velocityFrom = now.minusDays(VELOCITY_WINDOW_DAYS);

        // One query gives every product's recent movement. Asking per product would
        // issue one round trip per row of every table on the panel.
        Map<Long, Long> sold = new HashMap<>();
        for (Object[] row : orderItemRepository.unitsSoldPerProduct(velocityFrom, now)) {
            sold.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        List<InventoryHealthDto.InventoryItem> critical = toItems(
                productRepository.findCriticalStock(CRITICAL_BELOW, PageRequest.of(0, SECTION_LIMIT)),
                sold);

        List<InventoryHealthDto.InventoryItem> overstocked = toItems(
                productRepository.findOverstocked(OVERSTOCK_ABOVE, PageRequest.of(0, SECTION_LIMIT)),
                sold);

        List<Product> outOfStockProducts =
                productRepository.findOutOfStock(PageRequest.of(0, SECTION_LIMIT));
        List<InventoryHealthDto.InventoryItem> outOfStock = toItems(outOfStockProducts, sold);

        List<InventoryHealthDto.RestockSuggestion> restock = restockSuggestions(sold);

        InventoryHealthDto.Summary summary = new InventoryHealthDto.Summary(
                productRepository.countByActiveTrue(),
                productRepository.countCriticalStock(CRITICAL_BELOW),
                productRepository.countOverstocked(OVERSTOCK_ABOVE),
                productRepository.countOutOfStock(),
                restock.size(),
                MetricsService.scale(productRepository.sumStockValue()),
                MetricsService.scale(productRepository.sumOverstockedValue(OVERSTOCK_ABOVE)),
                productRepository.countActiveWithoutCost()
        );

        return new InventoryHealthDto(
                critical,
                overstocked,
                outOfStock,
                restock,
                summary,
                new InventoryHealthDto.Thresholds(
                        CRITICAL_BELOW, OVERSTOCK_ABOVE,
                        COVER_TARGET_DAYS, LEAD_TIME_DAYS, VELOCITY_WINDOW_DAYS),
                MetricsService.CURRENCY
        );
    }

    private List<InventoryHealthDto.InventoryItem> toItems(List<Product> products,
                                                          Map<Long, Long> sold) {
        List<InventoryHealthDto.InventoryItem> out = new ArrayList<>(products.size());
        for (Product p : products) {
            long units = sold.getOrDefault(p.getId(), 0L);
            Double cover = daysOfCover(p.getStockQuantity(), units);
            BigDecimal stockValue = p.getPurchasePrice() == null
                    ? null
                    : MetricsService.scale(
                            p.getPurchasePrice().multiply(BigDecimal.valueOf(p.getStockQuantity())));

            out.add(new InventoryHealthDto.InventoryItem(
                    p.getId(),
                    p.getName(),
                    p.getImageUrl(),
                    p.getSku(),
                    p.getBrand(),
                    p.getCategory(),
                    p.getStockQuantity(),
                    MetricsService.scale(p.getPrice()),
                    p.getPurchasePrice() == null ? null : MetricsService.scale(p.getPurchasePrice()),
                    stockValue,
                    units,
                    cover,
                    severityFor(p.getStockQuantity(), cover)
            ));
        }
        return out;
    }

    /**
     * How urgent one row is.
     *
     * <p>Severity is decided here rather than in the browser so that the same situation
     * is coloured identically in all four tables, and so the ordering and the colour
     * come from one rule instead of two that can disagree.</p>
     */
    private static String severityFor(int stock, Double cover) {
        if (stock == 0) {
            return DANGER;
        }
        if (cover != null && cover <= LEAD_TIME_DAYS) {
            // It will run out before a replacement could realistically arrive.
            return DANGER;
        }
        if (stock < CRITICAL_BELOW) {
            return WARNING;
        }
        if (stock > OVERSTOCK_ABOVE) {
            // Overstock is a warning only when the goods are genuinely not moving.
            // Deep stock on a fast seller is inventory management, not a problem.
            return cover != null && cover > COVER_TARGET_DAYS * 2 ? WARNING : INFO;
        }
        return INFO;
    }

    /**
     * Days of stock remaining at the recent rate, or null when nothing has moved.
     *
     * <p>Null rather than infinity. A product that sold nothing has no meaningful days
     * of cover — the division has no answer — and reporting a very large number would
     * sort it beside genuinely well-stocked products instead of flagging it as
     * unmeasured.</p>
     */
    private static Double daysOfCover(int stock, long unitsInWindow) {
        if (unitsInWindow <= 0) {
            return null;
        }
        double perDay = (double) unitsInWindow / VELOCITY_WINDOW_DAYS;
        return BigDecimal.valueOf(stock / perDay)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Concrete restock recommendations, most urgent first.
     *
     * <p>Only products that actually move are considered. Recommending a restock for
     * something that sold nothing in a month is how an automatic system talks an
     * operator into buying more of what is already not selling.</p>
     */
    private List<InventoryHealthDto.RestockSuggestion> restockSuggestions(Map<Long, Long> sold) {
        List<Object[]> rows = productRepository.findActiveForAnalysis();

        // Which supplier last delivered each product, so the recommendation names one
        // instead of leaving the operator to look it up.
        Map<Long, Object[]> lastSupplier = new HashMap<>();
        for (Object[] row : purchaseItemRepository.lastSupplierPerProduct()) {
            lastSupplier.put(((Number) row[0]).longValue(), row);
        }

        record Candidate(InventoryHealthDto.RestockSuggestion suggestion, double cover) {}
        List<Candidate> candidates = new ArrayList<>();

        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            long units = sold.getOrDefault(id, 0L);
            if (units <= 0) {
                continue;
            }

            int stock = ((Number) row[7]).intValue();
            double perDay = (double) units / VELOCITY_WINDOW_DAYS;
            double cover = stock / perDay;

            // Enough cover for the target period: nothing to do.
            if (cover >= COVER_TARGET_DAYS) {
                continue;
            }

            int suggested = (int) Math.ceil(perDay * COVER_TARGET_DAYS) - stock;
            if (suggested <= 0) {
                continue;
            }

            BigDecimal purchasePrice = (BigDecimal) row[6];
            BigDecimal estimatedCost = purchasePrice == null
                    ? null
                    : MetricsService.scale(purchasePrice.multiply(BigDecimal.valueOf(suggested)));

            Object[] supplier = lastSupplier.get(id);
            String supplierName = supplier == null ? null : (String) supplier[2];
            Long supplierId = supplier == null || supplier[1] == null
                    ? null
                    : ((Number) supplier[1]).longValue();

            String urgency = cover <= LEAD_TIME_DAYS ? DANGER
                    : cover <= COVER_TARGET_DAYS ? WARNING : INFO;

            String rationale = String.format(
                    "S-au vândut %d bucăți în ultimele %d de zile (%.2f/zi). "
                            + "Stocul de %d bucăți acoperă %.1f zile, sub ținta de %d de zile. "
                            + "Termenul de aprovizionare presupus este de %d zile.",
                    units, VELOCITY_WINDOW_DAYS, perDay, stock, cover,
                    COVER_TARGET_DAYS, LEAD_TIME_DAYS);

            candidates.add(new Candidate(new InventoryHealthDto.RestockSuggestion(
                    id,
                    (String) row[1],
                    (String) row[2],
                    stock,
                    units,
                    round1(perDay),
                    round1(cover),
                    suggested,
                    estimatedCost,
                    supplierName,
                    supplierId,
                    urgency,
                    rationale
            ), cover));
        }

        // Least cover first: the product that runs out soonest is the one to order today.
        candidates.sort((a, b) -> Double.compare(a.cover(), b.cover()));

        List<InventoryHealthDto.RestockSuggestion> out = new ArrayList<>(RESTOCK_LIMIT);
        for (int i = 0; i < Math.min(RESTOCK_LIMIT, candidates.size()); i++) {
            out.add(candidates.get(i).suggestion());
        }
        return out;
    }

    private static Double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
