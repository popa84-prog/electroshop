package com.electroshop.dto;

import com.electroshop.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String category,
        String brand,
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
                p.getBrand(),
                p.getImageUrl(),
                p.getCreatedAt()
        );
    }
}
