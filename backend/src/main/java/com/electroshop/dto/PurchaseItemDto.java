package com.electroshop.dto;

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
    public static PurchaseItemDto from(PurchaseItem item) {
        return new PurchaseItemDto(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPurchasePrice(),
                item.getSubtotal());
    }
}
