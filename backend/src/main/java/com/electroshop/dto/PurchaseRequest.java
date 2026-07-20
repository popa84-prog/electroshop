package com.electroshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload to record a stock intake (intrare de marfă) from a supplier.
 */
public record PurchaseRequest(
        @NotNull(message = "supplierId is required")
        Long supplierId,

        LocalDate purchaseDate,

        @Size(max = 60)
        String invoiceNumber,

        String notes,

        @NotEmpty(message = "A purchase must contain at least one item")
        @Valid
        List<Item> items
) {
    public record Item(
            @NotNull(message = "productId is required")
            Long productId,

            @NotNull
            @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity,

            @NotNull(message = "Unit purchase price is required")
            @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
            BigDecimal unitPurchasePrice
    ) {
    }
}
