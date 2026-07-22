package com.electroshop.dto;

import com.electroshop.model.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ADMIN-ONLY product view. Unlike {@link ProductDto}, this exposes the
 * acquisition price plus the derived unit profit and margin. It must only ever
 * be returned from /admin/** endpoints (ROLE_ADMIN).
 */
public record AdminProductDto(
        Long id,
        String name,
        String description,
        BigDecimal price,           // selling price
        BigDecimal purchasePrice,   // acquisition price (admin only)
        BigDecimal profit,          // price - purchasePrice (per unit)
        BigDecimal marginPercent,   // profit / price * 100
        Integer stockQuantity,
        String category,
        String subcategory,
        String brand,
        String sku,
        String imageUrl,
        List<ProductDto.ImageDto> images,
        LocalDateTime createdAt
) {
    /** Lightweight view for the admin table — no gallery (avoids N+1). */
    public static AdminProductDto from(Product p) {
        return build(p, List.of());
    }

    /** Full view including the image gallery (single-product / edit). */
    public static AdminProductDto detail(Product p) {
        List<ProductDto.ImageDto> imgs = p.getImages().stream()
                .map(i -> new ProductDto.ImageDto(i.getId(), i.getUrl(), i.isPrimary()))
                .toList();
        return build(p, imgs);
    }

    private static AdminProductDto build(Product p, List<ProductDto.ImageDto> imgs) {
        BigDecimal price = p.getPrice();
        BigDecimal purchase = p.getPurchasePrice();
        BigDecimal profit = null;
        BigDecimal margin = null;
        if (purchase != null && price != null) {
            profit = price.subtract(purchase);
            if (price.signum() > 0) {
                margin = profit.multiply(BigDecimal.valueOf(100))
                        .divide(price, 1, RoundingMode.HALF_UP);
            }
        }
        return new AdminProductDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                price,
                purchase,
                profit,
                margin,
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
