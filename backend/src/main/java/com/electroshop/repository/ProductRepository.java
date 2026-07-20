package com.electroshop.repository;

import com.electroshop.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT p FROM Product p
            WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                   OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:category IS NULL OR p.category = :category)
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("category") String category,
                         Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();

    /** Distinct [category, subcategory] pairs, used to build the category tree. */
    @Query("""
            SELECT DISTINCT p.category, p.subcategory FROM Product p
            WHERE p.category IS NOT NULL
            ORDER BY p.category, p.subcategory
            """)
    List<Object[]> findCategorySubcategoryPairs();

    Optional<Product> findFirstByNameIgnoreCase(String name);

    Optional<Product> findFirstBySku(String sku);
}
