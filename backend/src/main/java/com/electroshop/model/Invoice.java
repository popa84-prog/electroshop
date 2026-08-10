package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un document fiscal emis: factură sau storno.
 *
 * <h2>De ce este entitate separată și nu trei coloane pe comandă</h2>
 *
 * <p>Până acum factura era exact asta — {@code invoice_series},
 * {@code invoice_number} și {@code invoice_issued_at} pe {@code orders} — iar
 * PDF-ul se construia la fiecare descărcare din comanda vie. Consecința este că
 * documentul se rescria retroactiv: redenumeai un produs, îi schimbai prețul,
 * mutai sediul firmei sau modificai cota de TVA, și factura trimisă clientului
 * acum șase luni se retipărea altfel. Cele două exemplare, al cumpărătorului și
 * al magazinului, încetau să coincidă, iar diferența nu se putea explica.</p>
 *
 * <p><b>Factura este un instantaneu.</b> Toate câmpurile de mai jos sunt copii
 * făcute la emitere: denumirea și datele fiscale ale vânzătorului, cele ale
 * cumpărătorului, cota de TVA, regimul de plătitor, și fiecare linie cu
 * denumirea și prețul de atunci. Legătura către {@link Order} rămâne pentru
 * trasabilitate, dar nu mai este sursa a nimic din ce se tipărește. O modificare
 * ulterioară în catalog sau în setările firmei nu are cum să atingă un document
 * deja emis.</p>
 *
 * <h2>Numerotarea</h2>
 *
 * <p>Constrângerea de unicitate pe perechea serie plus număr este apărarea
 * reală împotriva dublei numerotări. Verificarea în cod se poate pierde între
 * două cereri concurente; constrângerea din bază nu. Dacă două emiteri simultane
 * ajung la același număr, a doua eșuează la commit în loc să producă un al
 * doilea document cu numărul primului.</p>
 *
 * <p>Facturile și stornările împart același contor, conform deciziei luate
 * pentru acest magazin. {@link InvoiceType} distinge documentele; numerotarea nu
 * face nicio diferență între ele.</p>
 */
@Entity
@Table(
        name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_series_number",
                columnNames = {"series", "number"}),
        indexes = {
                @Index(name = "idx_invoice_order", columnList = "order_id"),
                @Index(name = "idx_invoice_issued", columnList = "issued_at"),
                @Index(name = "idx_invoice_status", columnList = "status"),
                @Index(name = "idx_invoice_original", columnList = "original_invoice_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Identificarea documentului -------------------------------------

    @Column(nullable = false, length = 16)
    private String series;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceType type = InvoiceType.INVOICE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    /**
     * Comanda din care s-a emis documentul.
     *
     * <p>{@code LAZY} plus nullable: o comandă ștearsă nu trebuie să ia cu ea
     * factura. Documentul fiscal supraviețuiește obiectului comercial care l-a
     * generat, pentru că numărul lui a fost deja raportat.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * Doar pe documentele de tip {@link InvoiceType#STORNO}: factura corectată.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private Invoice originalInvoice;

    // ---- Instantaneul vânzătorului --------------------------------------

    @Column(name = "seller_name", length = 200)
    private String sellerName;

    @Column(name = "seller_cui", length = 40)
    private String sellerCui;

    @Column(name = "seller_reg_com", length = 60)
    private String sellerRegCom;

    @Column(name = "seller_address", length = 400)
    private String sellerAddress;

    @Column(name = "seller_iban", length = 60)
    private String sellerIban;

    @Column(name = "seller_bank", length = 120)
    private String sellerBank;

    // ---- Instantaneul cumpărătorului ------------------------------------

    @Column(name = "buyer_name", length = 200)
    private String buyerName;

    @Column(name = "buyer_email", length = 200)
    private String buyerEmail;

    @Column(name = "buyer_address", length = 400)
    private String buyerAddress;

    /**
     * Datele fiscale ale cumpărătorului, completate doar pentru persoane
     * juridice. Magazinul nu le cere la înregistrare, deci rămân goale pentru
     * clienții persoane fizice, iar factura nu tipărește rânduri goale.
     */
    @Column(name = "buyer_cui", length = 40)
    private String buyerCui;

    @Column(name = "buyer_reg_com", length = 60)
    private String buyerRegCom;

    // ---- Regimul fiscal la momentul emiterii ----------------------------

    @Column(name = "vat_payer", nullable = false)
    private boolean vatPayer = true;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    // ---- Totaluri --------------------------------------------------------
    //
    // Sume ale liniilor, nu recalculări din totalul general. Rotunjirea pe
    // linie și rotunjirea pe total dau rezultate care diferă cu bani, iar
    // documentul tipărește liniile: dacă totalul nu este suma lor exactă,
    // diferența se vede cu ochiul liber pe hârtie.

    @Column(name = "total_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_vat", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalVat = BigDecimal.ZERO;

    @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "RON";

    // ---- Stornare --------------------------------------------------------

    /**
     * Motivul stornării, obligatoriu la emiterea unui storno.
     *
     * <p>Se completează pe documentul de storno, nu pe factura originală. Pe
     * originală se poate ajunge la mai multe stornări parțiale, fiecare cu
     * motivul ei, iar un singur câmp nu le-ar putea ține pe toate.</p>
     */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Cine a cerut stornarea. Text, nu referință către {@code users}: contul
     * poate fi șters, iar documentul trebuie să rămână lizibil.
     */
    @Column(name = "cancelled_by", length = 200)
    private String cancelledBy;

    // ---- Restul ----------------------------------------------------------

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (issuedAt == null) {
            issuedAt = LocalDate.now();
        }
    }

    public void addLine(InvoiceLine line) {
        lines.add(line);
        line.setInvoice(this);
    }

    /**
     * Eticheta documentului, aşa cum apare pe hârtie şi în listă.
     */
    public String getDocumentNumber() {
        return series + " " + number;
    }

    /**
     * Recalculează cele trei totaluri din liniile curente.
     *
     * <p>Apelat după ce liniile sunt complete. Sumele se iau din linii tocmai
     * pentru ca totalul tipărit să fie identic cu adunarea coloanei de pe
     * factură.</p>
     */
    public void recalculateTotals() {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            net = net.add(line.getLineNet() == null ? BigDecimal.ZERO : line.getLineNet());
            vat = vat.add(line.getLineVat() == null ? BigDecimal.ZERO : line.getLineVat());
            gross = gross.add(line.getLineGross() == null ? BigDecimal.ZERO : line.getLineGross());
        }
        this.totalNet = net;
        this.totalVat = vat;
        this.totalGross = gross;
    }
}
