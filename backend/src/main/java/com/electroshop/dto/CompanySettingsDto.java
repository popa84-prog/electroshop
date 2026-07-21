package com.electroshop.dto;

import com.electroshop.model.CompanySettings;

import java.math.BigDecimal;

/**
 * Editable company / billing profile used both to display the current values in
 * the Admin panel and to receive updates. All fields are optional so the owner
 * can fill them in progressively.
 */
public record CompanySettingsDto(
        Long id,
        String legalName,
        String cui,
        String regCom,
        String address,
        String city,
        String county,
        String country,
        String postalCode,
        String iban,
        String bankName,
        String phone,
        String email,
        String website,
        boolean vatPayer,
        BigDecimal vatRate,
        String invoiceSeries,
        Integer invoiceNextNumber,
        String logoUrl,
        String invoiceNotes
) {
    public static CompanySettingsDto from(CompanySettings c) {
        return new CompanySettingsDto(
                c.getId(),
                c.getLegalName(),
                c.getCui(),
                c.getRegCom(),
                c.getAddress(),
                c.getCity(),
                c.getCounty(),
                c.getCountry(),
                c.getPostalCode(),
                c.getIban(),
                c.getBankName(),
                c.getPhone(),
                c.getEmail(),
                c.getWebsite(),
                c.isVatPayer(),
                c.getVatRate(),
                c.getInvoiceSeries(),
                c.getInvoiceNextNumber(),
                c.getLogoUrl(),
                c.getInvoiceNotes()
        );
    }
}
