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
        // Feature #7 (performance): a resized (300×300) variant of imageUrl for card
        // grids — avoids the storefront loading full-size originals just to shrink
        // them in CSS. Falls back to imageUrl unchanged for non-Cloudinary URLs.
        String imageThumbUrl,
        List<ImageDto> images,
        LocalDateTime createdAt
) {
    /**
     * A single gallery image, as exposed to clients. {@code thumbnailUrl} (300×300)
     * and {@code fhdUrl} (max 1920px wide) are derived on the fly from {@code url}
     * via Cloudinary URL transformations — no extra storage or upload cost.
     * {@code width}/{@code height}/{@code format}/{@code bytes} are null for images
     * uploaded before this metadata was captured.
     */
    public record ImageDto(
            Long id,
            String url,
            String thumbnailUrl,
            String fhdUrl,
            boolean primary,
            int position,
            Integer width,
            Integer height,
            String format,
            Long bytes
    ) {}

    private static ImageDto toImageDto(com.electroshop.model.ProductImage i) {
        return new ImageDto(
                i.getId(),
                i.getUrl(),
                com.electroshop.service.CloudinaryService.thumbnailUrl(i.getUrl()),
                com.electroshop.service.CloudinaryService.fhdUrl(i.getUrl()),
                i.isPrimary(),
                i.getPosition(),
                i.getWidth(),
                i.getHeight(),
                i.getFormat(),
                i.getBytes()
        );
    }

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
                com.electroshop.service.CloudinaryService.thumbnailUrl(p.getImageUrl()),
                List.of(),
                p.getCreatedAt()
        );
    }

    /** Full view including the image gallery. Must be called inside a transaction. */
    public static ProductDto detail(Product p) {
        List<ImageDto> imgs = p.getImages().stream()
                .sorted(java.util.Comparator.comparingInt(com.electroshop.model.ProductImage::getPosition))
                .map(ProductDto::toImageDto)
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
                com.electroshop.service.CloudinaryService.thumbnailUrl(p.getImageUrl()),
                imgs,
                p.getCreatedAt()
        );
    }
}
