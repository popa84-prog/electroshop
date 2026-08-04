package com.electroshop.dto;

import com.electroshop.model.OrderItem;
import com.electroshop.model.Product;

import java.math.BigDecimal;

public record OrderItemDto(
        Long id,
        Long productId,
        String productName,
        String imageUrl,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    /**
     * {@code productId} and {@code imageUrl} are null, and {@code productName}
     * falls back to {@link OrderItem#getProductName()}, exactly when the product
     * behind this line was permanently removed from the catalogue via
     * {@code ProductService#forceDeleteWithHistory} — the line itself (quantity,
     * prices, subtotal) is always complete and unaffected either way, which is
     * the entire point of that feature: accounting and profit history survive
     * a product's removal from the catalogue intact.
     */
    public static OrderItemDto from(OrderItem item) {
        Product product = item.getProduct();
        String name = product != null
                ? product.getName()
                : (item.getProductName() != null ? item.getProductName() : "Produs șters din catalog");
        return new OrderItemDto(
                item.getId(),
                product != null ? product.getId() : null,
                name,
                product != null ? product.getImageUrl() : null,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
