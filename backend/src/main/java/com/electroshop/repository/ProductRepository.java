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
}
