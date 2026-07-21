package com.electroshop.service;

import com.electroshop.dto.CompanySettingsDto;
import com.electroshop.model.CompanySettings;
import com.electroshop.repository.CompanySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Manages the single {@link CompanySettings} row (feature #9). The row is created
 * lazily with sensible Romanian defaults the first time it is accessed, so the
 * Admin panel always has something to edit.
 */
@Service
@Transactional
public class CompanySettingsService {

    private final CompanySettingsRepository repository;
    private final AuditService auditService;

    public CompanySettingsService(CompanySettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /** Returns the settings entity, creating a default one on first use. */
    public CompanySettings getEntity() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            CompanySettings c = new CompanySettings();
            c.setCountry("România");
            c.setVatPayer(true);
            c.setVatRate(new BigDecimal("19.00"));
            c.setInvoiceSeries("ELS");
            c.setInvoiceNextNumber(1);
            return repository.save(c);
        });
    }

    @Transactional(readOnly = true)
    public CompanySettingsDto get() {
        return repository.findAll().stream().findFirst()
                .map(CompanySettingsDto::from)
                .orElseGet(() -> CompanySettingsDto.from(defaultTransient()));
    }

    public CompanySettingsDto update(CompanySettingsDto d) {
        CompanySettings c = getEntity();
        c.setLegalName(trim(d.legalName()));
        c.setCui(trim(d.cui()));
        c.setRegCom(trim(d.regCom()));
        c.setAddress(trim(d.address()));
        c.setCity(trim(d.city()));
        c.setCounty(trim(d.county()));
        c.setCountry(trim(d.country()));
        c.setPostalCode(trim(d.postalCode()));
        c.setIban(trim(d.iban()));
        c.setBankName(trim(d.bankName()));
        c.setPhone(trim(d.phone()));
        c.setEmail(trim(d.email()));
        c.setWebsite(trim(d.website()));
        c.setVatPayer(d.vatPayer());
        if (d.vatRate() != null) {
            c.setVatRate(d.vatRate());
        }
        if (d.invoiceSeries() != null && !d.invoiceSeries().isBlank()) {
            c.setInvoiceSeries(d.invoiceSeries().trim());
        }
        if (d.invoiceNextNumber() != null && d.invoiceNextNumber() >= 1) {
            c.setInvoiceNextNumber(d.invoiceNextNumber());
        }
        c.setLogoUrl(trim(d.logoUrl()));
        c.setInvoiceNotes(d.invoiceNotes());
        CompanySettings saved = repository.save(c);
        auditService.log("COMPANY_SETTINGS_UPDATED", "CompanySettings", saved.getId(),
                saved.getLegalName() != null ? saved.getLegalName() : "date firmă");
        return CompanySettingsDto.from(saved);
    }

    private CompanySettings defaultTransient() {
        CompanySettings c = new CompanySettings();
        c.setCountry("România");
        c.setVatPayer(true);
        c.setVatRate(new BigDecimal("19.00"));
        c.setInvoiceSeries("ELS");
        c.setInvoiceNextNumber(1);
        return c;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
