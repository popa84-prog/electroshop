package com.electroshop.repository;

import com.electroshop.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT p FROM Product p
            WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:category IS NULL OR p.category = :category)
              AND (:subcategory IS NULL OR p.subcategory = :subcategory)
              AND (:brand IS NULL OR p.brand = :brand)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:inStock = FALSE OR p.stockQuantity > 0)
              AND (:outOfStock = FALSE OR p.stockQuantity = 0)
              AND (:lowStockAtMost IS NULL OR (p.stockQuantity <= :lowStockAtMost AND p.stockQuantity > 0))
              AND (:noImage = FALSE OR p.imageUrl IS NULL OR p.imageUrl = '')
              AND (:activeStatus IS NULL OR p.active = :activeStatus)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("category") String category,
                         @Param("subcategory") String subcategory,
                         @Param("brand") String brand,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("inStock") boolean inStock,
                         @Param("outOfStock") boolean outOfStock,
                         @Param("lowStockAtMost") Integer lowStockAtMost,
                         @Param("noImage") boolean noImage,
                         // Tri-state: null = both active & inactive, TRUE = active
                         // only, FALSE = inactive only (feature: filtru status
                         // Active/Dezactivate). A plain boolean couldn't express
                         // "inactive only" alongside "don't filter at all".
                         @Param("activeStatus") Boolean activeStatus,
                         Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    @Query("SELECT DISTINCT p.brand FROM Product p WHERE p.brand IS NOT NULL ORDER BY p.brand")
    List<String> findAllBrands();

    /**
     * Category names together with how many products each one holds, most populated
     * first. Backs the "most popular categories" tiles on the storefront home page,
     * so those tiles always reflect the real catalogue instead of hardcoded names.
     * Returns Object[]{String category, Long productCount}.
     */
    @Query("""
            SELECT p.category, COUNT(p) FROM Product p
            WHERE p.category IS NOT NULL AND TRIM(p.category) <> ''
            GROUP BY p.category
            ORDER BY COUNT(p) DESC, p.category ASC
            """)
    List<Object[]> findCategoryCounts(Pageable pageable);

    /** Distinct [category, subcategory] pairs, used to build the category tree. */
    @Query("""
            SELECT DISTINCT p.category, p.subcategory FROM Product p
            WHERE p.category IS NOT NULL
            ORDER BY p.category, p.subcategory
            """)
    List<Object[]> findCategorySubcategoryPairs();

    Optional<Product> findFirstByNameIgnoreCase(String name);

    Optional<Product> findFirstBySku(String sku);

    /** New products per calendar day — feeds the "Produse" stat-card trend on the dashboard. */
    @Query(value = """
            SELECT DATE(created_at) AS d, COUNT(*)
            FROM products
            GROUP BY DATE(created_at)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countCreatedByDay();

    /** The five lowest-stock products at or below {@code threshold}, emptiest first. */
    List<Product> findTop5ByStockQuantityLessThanEqualOrderByStockQuantityAsc(int threshold);

    // ---- Feature #8 (automatic notifications) — full sweeps, not capped like the Top5 above ----

    /** Every active product with 0 &lt; stock &lt; threshold — feeds the low-stock notification sweep. */
    @Query("SELECT p FROM Product p WHERE p.active = TRUE AND p.stockQuantity > 0 AND p.stockQuantity < :threshold")
    List<Product> findLowStockActive(@Param("threshold") int threshold);

    /** Every active product with no image set — feeds the "produs fără imagini" notification sweep. */
    @Query("SELECT p FROM Product p WHERE p.active = TRUE AND (p.imageUrl IS NULL OR p.imageUrl = '')")
    List<Product> findActiveWithNoImage();

    /** Every inactive product — feeds the "produs inactiv" notification sweep. */
    List<Product> findByActiveFalse();

    // ---- Dashboard metrics (tasks 9-13) ----------------------------------
    //
    // Everything below aggregates in the database and returns scalars or
    // projections. The catalogue is small today and these could all be done by
    // loading products into memory and summing there; they are written this way
    // because the reports have to stay correct and fast at a hundred times the
    // current size, and rewriting them under load is not the moment to find out
    // whether the aggregation was right.

    /**
     * Capital tied up in stock, at cost: {@code SUM(purchasePrice × stockQuantity)}.
     *
     * <p>Only active products with a recorded purchase price and stock on hand
     * contribute. Products with no purchase price are not treated as costing zero —
     * that would report a confident total that silently excludes part of the
     * catalogue — they are excluded here and counted separately by
     * {@link #countActiveInStockWithoutCost()} so the gap travels with the figure.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(p.purchasePrice * p.stockQuantity), 0)
            FROM Product p
            WHERE p.active = TRUE
              AND p.purchasePrice IS NOT NULL
              AND p.stockQuantity > 0
            """)
    BigDecimal sumStockValue();

    /**
     * Margin the current inventory would yield at list price:
     * {@code SUM((price − purchasePrice) × stockQuantity)}.
     *
     * <p>Products priced below cost contribute a negative amount, which is correct:
     * they genuinely reduce the potential profit of the inventory. They are also
     * listed individually by {@link #findNegativeMargin(Pageable)}, because a summed
     * total absorbs them into the profitable products and hides the most actionable
     * fact on the dashboard.</p>
     */
    @Query("""
            SELECT COALESCE(SUM((p.price - p.purchasePrice) * p.stockQuantity), 0)
            FROM Product p
            WHERE p.active = TRUE
              AND p.purchasePrice IS NOT NULL
              AND p.stockQuantity > 0
            """)
    BigDecimal sumProfitPotential();

    /**
     * Retail value of the stock: {@code SUM(price × stockQuantity)}.
     *
     * <p>The denominator of the average margin. Restricted to the same products as
     * the numerator — active, in stock, with a known cost — so the ratio is computed
     * over one population rather than two, which is how a margin percentage ends up
     * above 100.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(p.price * p.stockQuantity), 0)
            FROM Product p
            WHERE p.active = TRUE
              AND p.purchasePrice IS NOT NULL
              AND p.stockQuantity > 0
            """)
    BigDecimal sumRetailValueOfCostedStock();

    /** Active products in stock that contribute to the metrics above. */
    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.active = TRUE AND p.purchasePrice IS NOT NULL AND p.stockQuantity > 0
            """)
    long countActiveInStockWithCost();

    /** Units those products represent, so the figures can be read per unit as well. */
    @Query("""
            SELECT COALESCE(SUM(p.stockQuantity), 0) FROM Product p
            WHERE p.active = TRUE AND p.purchasePrice IS NOT NULL AND p.stockQuantity > 0
            """)
    long sumUnitsWithCost();

    /**
     * Active products in stock with no purchase price — the size of the blind spot.
     */
    @Query("""
            SELECT COUNT(p) FROM Product p
            WHERE p.active = TRUE AND p.purchasePrice IS NULL AND p.stockQuantity > 0
            """)
    long countActiveInStockWithoutCost();

    /** Units the blind spot represents, in goods rather than in rows. */
    @Query("""
            SELECT COALESCE(SUM(p.stockQuantity), 0) FROM Product p
            WHERE p.active = TRUE AND p.purchasePrice IS NULL AND p.stockQuantity > 0
            """)
    long sumUnitsWithoutCost();

    /** Every active product, whether in stock or not — the coverage denominator. */
    long countByActiveTrue();

    /** Active products with no purchase price at all, in stock or not. */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = TRUE AND p.purchasePrice IS NULL")
    long countActiveWithoutCost();

    /**
     * Products whose selling price is below what they cost, largest exposure first.
     *
     * <p>Ordered by total loss rather than by loss per unit: a product losing one leu
     * across four hundred units in stock costs more than one losing forty across
     * three, and the exposure is what decides whether this is an annoyance or an
     * emergency.</p>
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = TRUE
              AND p.purchasePrice IS NOT NULL
              AND p.price < p.purchasePrice
            ORDER BY (p.purchasePrice - p.price) * p.stockQuantity DESC
            """)
    List<Product> findNegativeMargin(Pageable pageable);

    /** Active products at or below a stock threshold, emptiest first. */
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = TRUE AND p.stockQuantity > 0 AND p.stockQuantity < :threshold
            ORDER BY p.stockQuantity ASC
            """)
    List<Product> findCriticalStock(@Param("threshold") int threshold, Pageable pageable);

    /**
     * Active products above a stock threshold, most capital tied up first.
     *
     * <p>Ordered by value rather than by quantity. Two hundred units of a cheap
     * accessory and twenty units of an expensive one are both "overstocked"; only one
     * of them is worth acting on this week.</p>
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = TRUE AND p.stockQuantity > :threshold
            ORDER BY COALESCE(p.purchasePrice, 0) * p.stockQuantity DESC
            """)
    List<Product> findOverstocked(@Param("threshold") int threshold, Pageable pageable);

    /** Active products with nothing on hand. */
    @Query("SELECT p FROM Product p WHERE p.active = TRUE AND p.stockQuantity = 0 ORDER BY p.updatedAt DESC")
    List<Product> findOutOfStock(Pageable pageable);

    /** How many active products sit at or below a stock threshold. */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = TRUE AND p.stockQuantity > 0 AND p.stockQuantity < :threshold")
    long countCriticalStock(@Param("threshold") int threshold);

    /** How many active products sit above a stock threshold. */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = TRUE AND p.stockQuantity > :threshold")
    long countOverstocked(@Param("threshold") int threshold);

    /** How many active products have nothing on hand. */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = TRUE AND p.stockQuantity = 0")
    long countOutOfStock();

    /** Capital tied up in products above a stock threshold — the cost of overstocking. */
    @Query("""
            SELECT COALESCE(SUM(p.purchasePrice * p.stockQuantity), 0)
            FROM Product p
            WHERE p.active = TRUE AND p.purchasePrice IS NOT NULL AND p.stockQuantity > :threshold
            """)
    BigDecimal sumOverstockedValue(@Param("threshold") int threshold);

    /**
     * Active products with a known cost and stock, for the rules engine.
     *
     * <p>Returns {@code [id, name, imageUrl, brand, category, price, purchasePrice,
     * stockQuantity]}. A projection rather than the entity because the engine reads
     * eight scalars and never touches the image collection, which the entity would
     * bring along.</p>
     */
    @Query("""
            SELECT p.id, p.name, p.imageUrl, p.brand, p.category,
                   p.price, p.purchasePrice, p.stockQuantity
            FROM Product p
            WHERE p.active = TRUE
            """)
    List<Object[]> findActiveForAnalysis();

    /**
     * Global search over name, SKU and brand.
     *
     * <p>The term is bound as a parameter and the wildcards are added by the query, so
     * an operator typing a percent sign searches for a percent sign rather than for
     * everything.</p>
     *
     * <p>Active products come first. A search that surfaces a discontinued item above
     * the one currently on sale sends the operator to the wrong record, and the wrong
     * record looks entirely plausible.</p>
     */
    @Query("""
            SELECT p FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY p.active DESC, p.name ASC
            """)
    List<Product> searchForGlobal(@Param("q") String q, Pageable pageable);
}
