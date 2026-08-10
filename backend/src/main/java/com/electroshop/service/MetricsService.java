package com.electroshop.service;

import com.electroshop.dto.BusinessBannerDto;
import com.electroshop.dto.DeltaDto;
import com.electroshop.dto.ProfitPotentialDto;
import com.electroshop.dto.StockValueDto;
import com.electroshop.model.Product;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The four business figures behind the dashboard banner, and the two endpoints that
 * expose them individually.
 *
 * <p>Tasks 9, 10 and 11. The banner replaces the old "Users / Products / Orders /
 * Revenue" cards, which counted rows. How many products exist is a fact about the
 * database; how much capital they represent, what margin they carry and what they sold
 * this month are facts about the company, and only the second kind supports a
 * decision.</p>
 *
 * <h2>Why every figure travels with its coverage</h2>
 *
 * <p>The catalogue permits a product to have no purchase price. Every formula here
 * needs one. The tempting shortcut is to treat a missing cost as zero, which produces
 * a stock value that is too low, a potential profit equal to the entire selling price,
 * and a margin approaching 100% — the single most flattering possible error, and one
 * that looks completely plausible on a card.</p>
 *
 * <p>So products without a cost are excluded from the arithmetic and counted
 * separately. {@code productsWithoutCost} is returned with every figure and the banner
 * renders it as a warning strip. An operator reading a 42% margin needs to know whether
 * it covers the whole catalogue or four fifths of it, and that is not a detail to be
 * discovered later.</p>
 *
 * <h2>Why the four figures come from one call</h2>
 *
 * <p>The banner renders as a unit. Four separate requests would let the cards arrive at
 * different times and, worse, be computed against a catalogue that changed between
 * them — a stock value from one instant beside a margin from another, which do not
 * reconcile. One method, one moment.</p>
 */
@Service
public class MetricsService {

    /** Currency every amount in this service is expressed in. */
    static final String CURRENCY = "RON";

    /** How many below-cost products the potential-profit card lists. */
    private static final int NEGATIVE_MARGIN_LIMIT = 10;

    /** How many days of history the banner sparklines cover. */
    private static final int SPARK_DAYS = 14;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public MetricsService(ProductRepository productRepository,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // =====================================================================
    //  TASK 10 — stock value
    // =====================================================================

    /**
     * Capital tied up in inventory, at cost.
     *
     * <p>{@code SUM(purchasePrice × stockQuantity)} over active products that have
     * both a purchase price and stock on hand.</p>
     *
     * <p><b>There is no historical comparison for this figure.</b> Stock value is a
     * snapshot: the database records what stock is, never what it was, so "stock value
     * last month" cannot be recovered from anything stored. The delta is therefore
     * built against the same figure with no previous value, which makes
     * {@link DeltaDto#changePct()} null and the card shows no trend arrow rather than
     * a fabricated one. Reconstructing a past stock value by replaying orders backwards
     * was considered and rejected: it would ignore every purchase, write-off and manual
     * correction, and would produce a confident number that is wrong in a way nobody
     * could audit.</p>
     */
    public StockValueDto stockValue() {
        BigDecimal total = nz(productRepository.sumStockValue());
        return new StockValueDto(
                scale(total),
                CURRENCY,
                productRepository.countActiveInStockWithCost(),
                productRepository.sumUnitsWithCost(),
                productRepository.countActiveInStockWithoutCost(),
                productRepository.sumUnitsWithoutCost(),
                DeltaDto.higherIsBetter(scale(total), null)
        );
    }

    // =====================================================================
    //  TASK 11 — potential profit
    // =====================================================================

    /**
     * The profit the current inventory would produce if it all sold at list price.
     *
     * <p>{@code SUM((price − purchasePrice) × stockQuantity)} over the same population
     * as {@link #stockValue()}.</p>
     *
     * <p>The below-cost products are listed rather than merely summed. A single total
     * absorbs them into the profitable ones, and a product that loses money on every
     * unit sold is the most actionable row on the entire dashboard — it is losing money
     * right now, at a rate proportional to how well it sells.</p>
     */
    public ProfitPotentialDto profitPotential() {
        BigDecimal total = nz(productRepository.sumProfitPotential());

        List<Product> below = productRepository.findNegativeMargin(PageRequest.of(0, NEGATIVE_MARGIN_LIMIT));
        List<ProfitPotentialDto.NegativeMarginProduct> negative = new ArrayList<>(below.size());
        for (Product p : below) {
            BigDecimal lossPerUnit = p.getPurchasePrice().subtract(p.getPrice());
            BigDecimal totalLoss = lossPerUnit.multiply(BigDecimal.valueOf(p.getStockQuantity()));
            negative.add(new ProfitPotentialDto.NegativeMarginProduct(
                    p.getId(),
                    p.getName(),
                    scale(p.getPrice()),
                    scale(p.getPurchasePrice()),
                    p.getStockQuantity(),
                    scale(lossPerUnit),
                    scale(totalLoss)
            ));
        }

        return new ProfitPotentialDto(
                scale(total),
                CURRENCY,
                productRepository.countActiveInStockWithCost(),
                productRepository.countActiveInStockWithoutCost(),
                negative,
                DeltaDto.higherIsBetter(scale(total), null)
        );
    }

    // =====================================================================
    //  TASK 9 — the banner
    // =====================================================================

    /**
     * All four banner figures, computed from one consistent view of the data.
     *
     * <p>Two of the four have a real comparison and two do not, and the difference is
     * a property of the data rather than an oversight. Month sales compare against the
     * previous calendar month, which is stored and exact. Stock value and potential
     * profit are snapshots with no history. Average margin is derived from the two
     * snapshots and inherits their limitation.</p>
     *
     * <p>Rather than invent baselines for the three, the cards that have no comparison
     * simply show none. A trend arrow that points somewhere for no reason is worse than
     * no arrow: it invites a decision.</p>
     */
    public BusinessBannerDto banner() {
        LocalDateTime now = LocalDateTime.now();

        BigDecimal stockValue = scale(nz(productRepository.sumStockValue()));
        BigDecimal potential = scale(nz(productRepository.sumProfitPotential()));
        BigDecimal retail = nz(productRepository.sumRetailValueOfCostedStock());

        // Margin over retail value, not over cost. "Marjă medie" in commerce means
        // margin as a share of the selling price; margin over cost is markup, a
        // different and always larger number. Using the wrong one would inflate every
        // figure on this card by a factor that grows with the margin itself.
        BigDecimal marginPct = retail.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : potential.multiply(BigDecimal.valueOf(100))
                        .divide(retail, 2, RoundingMode.HALF_UP);

        LocalDate today = now.toLocalDate();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime prevMonthStart = monthStart.minusMonths(1);

        BigDecimal monthSales = scale(nz(orderRepository.sumRevenueBetween(monthStart, now)));
        BigDecimal prevMonthSales = scale(nz(orderRepository.sumRevenueBetween(prevMonthStart, monthStart)));

        long activeProducts = productRepository.countByActiveTrue();
        long withoutCost = productRepository.countActiveWithoutCost();
        double coverage = activeProducts == 0
                ? 100.0
                : BigDecimal.valueOf(activeProducts - withoutCost)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(activeProducts), 1, RoundingMode.HALF_UP)
                        .doubleValue();

        List<BigDecimal> salesSpark = dailyRevenueSeries(now, SPARK_DAYS);

        return new BusinessBannerDto(
                new BusinessBannerDto.Metric(stockValue, "CURRENCY",
                        DeltaDto.higherIsBetter(stockValue, null), List.of()),
                new BusinessBannerDto.Metric(potential, "CURRENCY",
                        DeltaDto.higherIsBetter(potential, null), List.of()),
                new BusinessBannerDto.Metric(monthSales, "CURRENCY",
                        DeltaDto.higherIsBetter(monthSales, prevMonthSales), salesSpark),
                new BusinessBannerDto.Metric(marginPct, "PERCENT",
                        DeltaDto.higherIsBetter(marginPct, null), List.of()),
                CURRENCY,
                new BusinessBannerDto.DataQuality(activeProducts, withoutCost, coverage)
        );
    }

    /**
     * Daily revenue for the last {@code days} days, oldest first, gaps filled with zero.
     *
     * <p>Filled rather than sparse. A sparkline drawn from only the days that had sales
     * compresses a quiet week into a short segment and turns a genuine slump into what
     * looks like steady trading.</p>
     */
    private List<BigDecimal> dailyRevenueSeries(LocalDateTime now, int days) {
        LocalDate start = now.toLocalDate().minusDays(days - 1L);
        List<Object[]> rows = orderRepository.revenueByDayBetween(start.atStartOfDay(), now);

        java.util.Map<LocalDate, BigDecimal> byDay = new java.util.HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = LocalDate.of(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            byDay.put(day, scale(nz((BigDecimal) row[3])));
        }

        List<BigDecimal> series = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            series.add(byDay.getOrDefault(start.plusDays(i), BigDecimal.ZERO));
        }
        return series;
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Two decimals, half-up. Money is rounded once, at the boundary, never mid-sum. */
    static BigDecimal scale(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}
