package com.electroshop.dto;

import com.electroshop.model.Supplier;

import java.time.LocalDateTime;

public record SupplierDto(
        Long id,
        String name,
        String contactName,
        String email,
        String phone,
        String address,
        String taxId,
        String notes,
        LocalDateTime createdAt
) {
    public static SupplierDto from(Supplier s) {
        return new SupplierDto(
                s.getId(), s.getName(), s.getContactName(), s.getEmail(),
                s.getPhone(), s.getAddress(), s.getTaxId(), s.getNotes(), s.getCreatedAt());
    }
}
