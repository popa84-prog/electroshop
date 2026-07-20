package com.electroshop.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150)
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
        BigDecimal price,

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock cannot be negative")
        Integer stockQuantity,

        @Size(max = 80)
        String category,

        @Size(max = 80)
        String subcategory,

        @Size(max = 80)
        String brand,

        // Admin-only acquisition price (optional on manual create/edit).
        @DecimalMin(value = "0.0", message = "Purchase price cannot be negative")
        BigDecimal purchasePrice,

        @Size(max = 60)
        String sku,

        @Size(max = 500)
        String imageUrl
) {
}
