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

    /**
     * Returns the settings entity, creating a default one on first use.
     *
     * <p><b>Existing rows are normalised on the way out.</b> The reception
     * series and counter were added to this table after the row already
     * existed, and {@code ddl-auto=update} fills a new {@code NOT NULL} integer
     * column with 0, not with the default written on the Java field — that
     * default only applies to objects the application constructs. The row
     * therefore came back carrying series {@code null} and counter {@code 0},
     * and the first goods receipt would have been numbered zero.</p>
     *
     * <p>Repairing it here rather than in each caller means the fix applies to
     * the settings screen, to the numbering and to any future reader at once.
     * The write is conditional, so it happens exactly once.</p>
     */
    public CompanySettings getEntity() {
        CompanySettings existing = repository.findAll().stream().findFirst().orElse(null);
        if (existing != null) {
            return normalise(existing);
        }
        CompanySettings c = new CompanySettings();
        c.setCountry("România");
        c.setVatPayer(true);
        c.setVatRate(new BigDecimal("19.00"));
        c.setInvoiceSeries("ELS");
        c.setInvoiceNextNumber(1);
        c.setReceptionSeries("NIR");
        c.setReceptionNextNumber(1);
        return repository.save(c);
    }

    /**
     * Fills in values that a schema migration could not.
     *
     * <p>A counter below 1 is treated as absent rather than respected. Document
     * numbering starts at one; zero is not a document number anybody would
     * write on paper, and a series that begins at zero makes every later
     * reference look off by one.</p>
     */
    private CompanySettings normalise(CompanySettings c) {
        boolean changed = false;
        if (c.getReceptionSeries() == null || c.getReceptionSeries().isBlank()) {
            c.setReceptionSeries("NIR");
            changed = true;
        }
        if (c.getReceptionNextNumber() == null || c.getReceptionNextNumber() < 1) {
            c.setReceptionNextNumber(1);
            changed = true;
        }
        if (c.getInvoiceSeries() == null || c.getInvoiceSeries().isBlank()) {
            c.setInvoiceSeries("ELS");
            changed = true;
        }
        if (c.getInvoiceNextNumber() == null || c.getInvoiceNextNumber() < 1) {
            c.setInvoiceNextNumber(1);
            changed = true;
        }
        return changed ? repository.save(c) : c;
    }

    /**
     * The settings, as the admin screen reads them.
     *
     * <p>Deliberately routed through {@link #getEntity()} rather than reading
     * the row directly. Reading it directly is what left this endpoint
     * reporting a reception series of {@code null} and a counter of {@code 0}
     * after those columns were added to an existing table: the normalisation
     * lived in {@code getEntity()}, and this path never called it. Two readers
     * of the same row that disagree about its contents is a defect on its own,
     * independent of which one is right.</p>
     *
     * <p>Not {@code readOnly} for the same reason — the first read after a
     * schema change repairs the row, and a read-only transaction would refuse
     * the write. The repair is conditional, so it happens exactly once.</p>
     */
    @Transactional
    public CompanySettingsDto get() {
        return CompanySettingsDto.from(getEntity());
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
        // Seria si contorul notei de intrare-receptie. Aceeasi aparare ca la
        // facturi: o valoare goala sau sub 1 este ignorata, nu scrisa. Un contor
        // dus la zero printr-un camp golit din greseala ar face ca urmatoarea
        // receptie sa incerce un numar deja folosit.
        if (d.receptionSeries() != null && !d.receptionSeries().isBlank()) {
            c.setReceptionSeries(d.receptionSeries().trim());
        }
        if (d.receptionNextNumber() != null && d.receptionNextNumber() >= 1) {
            c.setReceptionNextNumber(d.receptionNextNumber());
        }
        c.setLogoUrl(trim(d.logoUrl()));
        c.setInvoiceNotes(d.invoiceNotes());
        CompanySettings saved = repository.save(c);
        auditService.log("COMPANY_SETTINGS_UPDATED", "CompanySettings", saved.getId(),
                saved.getLegalName() != null ? saved.getLegalName() : "date firmă");
        return CompanySettingsDto.from(saved);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
