package com.electroshop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body for the "VÂNDUT" quick-sale popup (feature #10). {@code unitPrice} is an
 * editable snapshot for this sale only — it never rewrites the product's
 * catalogue price (that stays gated behind PRODUCTS_PRICE, see ProductService).
 */
public record SellProductRequest(
        @NotNull(message = "Cantitatea este obligatorie")
        @Min(value = 1, message = "Cantitatea trebuie să fie cel puțin 1")
        Integer quantity,

        @NotNull(message = "Prețul este obligatoriu")
        @DecimalMin(value = "0.0", inclusive = false, message = "Prețul trebuie să fie pozitiv")
        BigDecimal unitPrice
) {
}
