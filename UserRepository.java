package com.electroshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Checkout payload sent by the frontend cart.
 */
public record OrderRequest(
        @Size(max = 300)
        String shippingAddress,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<Item> items
) {
    public record Item(
            @NotNull(message = "productId is required")
            Long productId,

            @NotNull
            @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity
    ) {
    }
}
