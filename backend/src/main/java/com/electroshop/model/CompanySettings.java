package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Company / billing details used as the header of generated PDF invoices
 * (feature #9). Stored as a single editable row so the owner can fill it in and
 * update it at any time from the Admin panel — no data is hardcoded.
 */
@Entity
@Table(name = "company_settings")
@Getter
@Setter
@NoArgsConstructor
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Identitate firmă ----
    @Column(length = 200)
    private String legalName;      // Denumire legală (ex: ELECTROSHOP SRL)

    @Column(length = 40)
    private String cui;            // CUI / CIF (ex: RO12345678)

    @Column(length = 60)
    private String regCom;         // Nr. Reg. Com. (ex: J40/1234/2020)

    // ---- Adresă ----
    @Column(length = 250)
    private String address;        // Stradă, nr.
    @Column(length = 100)
    private String city;           // Oraș / localitate
    @Column(length = 100)
    private String county;         // Județ
    @Column(length = 80)
    private String country;        // Țară
    @Column(length = 20)
    private String postalCode;

    // ---- Bancă ----
    @Column(length = 40)
    private String iban;
    @Column(length = 120)
    private String bankName;

    // ---- Contact ----
    @Column(length = 40)
    private String phone;
    @Column(length = 120)
    private String email;
    @Column(length = 120)
    private String website;

    // ---- TVA & facturare ----
    @Column(nullable = false)
    private boolean vatPayer = true;               // Plătitor de TVA

    @Column(precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("19.00"); // Cota TVA %

    @Column(length = 12)
    private String invoiceSeries = "ELS";          // Seria facturii

    @Column(nullable = false)
    private Integer invoiceNextNumber = 1;         // Următorul număr de factură

    // ---- Extra ----
    @Column(length = 500)
    private String logoUrl;                         // Logo opțional pe factură
    @Column(columnDefinition = "TEXT")
    private String invoiceNotes;                     // Mențiuni pe factură

    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
