package com.electroshop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body for the multi-product "VÂNDUT" quick-sale cart. An admin can add
 * several distinct products — each with its own quantity and price, e.g.
 * "3 of this, 1 of that" — and finalize them as ONE order in a single call,
 * instead of the single-product {@link SellProductRequest} creating one
 * order per product.
 */
public record SellBatchRequest(
        @NotEmpty(message = "Adaugă cel puțin un produs în vânzare.")
        @Valid
        List<Line> items
) {
    /** One line of the sale cart — mirrors {@link SellProductRequest} plus the product it refers to. */
    public record Line(
            @NotNull(message = "Produsul este obligatoriu")
            Long productId,

            @NotNull(message = "Cantitatea este obligatorie")
            @Min(value = 1, message = "Cantitatea trebuie să fie cel puțin 1")
            Integer quantity,

            @NotNull(message = "Prețul este obligatoriu")
            @DecimalMin(value = "0.0", inclusive = false, message = "Prețul trebuie să fie pozitiv")
            BigDecimal unitPrice
    ) {
    }
}
