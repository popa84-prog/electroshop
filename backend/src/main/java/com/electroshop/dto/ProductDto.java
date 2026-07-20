package com.electroshop.dto;

import com.electroshop.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Public product view. Deliberately does NOT expose purchasePrice or any
 * acquisition figures — those are admin-only and must never reach buyers.
 */
public record ProductDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String category,
        String subcategory,
        String brand,
        String sku,
        String imageUrl,
        LocalDateTime createdAt
) {
    public static ProductDto from(Product p) {
        return new ProductDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStockQuantity(),
                p.getCategory(),
                p.getSubcategory(),
                p.getBrand(),
                p.getSku(),
                p.getImageUrl(),
                p.getCreatedAt()
        );
    }
}
