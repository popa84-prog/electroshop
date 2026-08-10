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
        LocalDateTime createdAt,

        /**
         * Eticheta notei de intrare-recepţie, sau {@code null} pentru
         * achiziţiile introduse manual.
         *
         * <p>Interfaţa o foloseşte ca să decidă dacă arată butonul de descărcare
         * a NIR-ului. Numărul acesta este al magazinului şi nu trebuie confundat
         * cu {@code invoiceNumber}, care este al furnizorului.</p>
         */
        String receptionNumber,
        LocalDate receptionIssuedAt,

        /** Fişierul din care a rezultat recepţia, când există unul. */
        String sourceFileName
) {
    public static PurchaseDto from(Purchase p) {
        return new PurchaseDto(
                p.getId(),
                p.getSupplier().getId(),
                // Instantaneul, cu revenire la rândul viu pentru achiziţiile
                // create înainte ca denumirea să fie copiată. Un furnizor
                // redenumit nu are voie să schimbe un document deja emis.
                p.getSupplierName() != null ? p.getSupplierName() : p.getSupplier().getName(),
                p.getPurchaseDate(),
                p.getInvoiceNumber(),
                p.getTotalAmount(),
                p.getNotes(),
                p.getItems().stream().map(PurchaseItemDto::from).collect(Collectors.toList()),
                p.getCreatedAt(),
                p.getReceptionNumberLabel(),
                p.getReceptionIssuedAt(),
                p.getSourceFileName());
    }
}
