package com.electroshop.dto;

import com.electroshop.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public product view. Deliberately does NOT expose purchasePrice or any
 * acquisition figures — those are admin-only and must never reach buyers.
 *
 * <p>{@code images} is populated only by {@link #detail(Product)} (single-product
 * views / admin image management). The list factory {@link #from(Product)} leaves
 * it empty to avoid an N+1 query when mapping a page of products.</p>
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
        List<ImageDto> images,
        LocalDateTime createdAt
) {
    /** A single gallery image, as exposed to clients. */
    public record ImageDto(Long id, String url, boolean primary) {}

    /** Lightweight view for lists — no gallery (uses imageUrl for the card). */
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
                List.of(),
                p.getCreatedAt()
        );
    }

    /** Full view including the image gallery. Must be called inside a transaction. */
    public static ProductDto detail(Product p) {
        List<ImageDto> imgs = p.getImages().stream()
                .map(i -> new ImageDto(i.getId(), i.getUrl(), i.isPrimary()))
                .toList();
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
                imgs,
                p.getCreatedAt()
        );
    }
}
