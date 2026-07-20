package com.electroshop.dto;

import com.electroshop.model.Purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record PurchaseDto(
        Long id,
        Long supplierId,
        String supplierName,
        LocalDate purchaseDate,
        String invoiceNumber,
        BigDecimal totalAmount,
        String notes,
        List<PurchaseItemDto> items,
        LocalDateTime createdAt
) {
    public static PurchaseDto from(Purchase p) {
        return new PurchaseDto(
                p.getId(),
                p.getSupplier().getId(),
                p.getSupplier().getName(),
                p.getPurchaseDate(),
                p.getInvoiceNumber(),
                p.getTotalAmount(),
                p.getNotes(),
                p.getItems().stream().map(PurchaseItemDto::from).collect(Collectors.toList()),
                p.getCreatedAt());
    }
}
