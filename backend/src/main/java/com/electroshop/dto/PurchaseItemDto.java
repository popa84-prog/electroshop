package com.electroshop.dto;

import com.electroshop.model.Product;
import com.electroshop.model.PurchaseItem;

import java.math.BigDecimal;

public record PurchaseItemDto(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPurchasePrice,
        BigDecimal subtotal
) {
    /**
     * Mirrors {@link OrderItemDto#from(com.electroshop.model.OrderItem)}: once the
     * product behind this line has been permanently removed from the catalogue,
     * {@code productId} is null and {@code productName} falls back to the
     * snapshot on {@link PurchaseItem#getProductName()} — the line's quantity and
     * price are always complete regardless.
     */
    public static PurchaseItemDto from(PurchaseItem item) {
        Product product = item.getProduct();
        String name = product != null
                ? product.getName()
                : (item.getProductName() != null ? item.getProductName() : "Produs șters din catalog");
        return new PurchaseItemDto(
                item.getId(),
                product != null ? product.getId() : null,
                name,
                item.getQuantity(),
                item.getUnitPurchasePrice(),
                item.getSubtotal());
    }
}
