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
 * A purchase = a stock intake from a supplier (intrare de marfă).
 * Recording a purchase increases product stock and counts as an expense
 * in the primary accounting report.
 */
@Entity
@Table(name = "purchases", indexes = {
        @Index(name = "idx_purchase_supplier", columnList = "supplier_id"),
        @Index(name = "idx_purchase_date", columnList = "purchase_date")
})
@Getter
@Setter
@NoArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate = LocalDate.now();

    /**
     * Numărul facturii <b>furnizorului</b>, aşa cum apare pe hârtia primită.
     *
     * <p>Text liber, şi rămâne text liber. Documentul acesta îl emite furnizorul,
     * cu seria şi numerotarea lui; magazinul doar îl înregistrează. Dacă
     * aplicaţia ar genera aici un număr propriu, ar fabrica documentul altei
     * firme — ceea ce, la un control, este exact ce se caută. Numerotarea proprie
     * a magazinului este cea a NIR-ului, de mai jos.</p>
     */
    @Column(length = 60)
    private String invoiceNumber;

    // ---- Nota de intrare-recepţie ---------------------------------------
    //
    // Documentul intern pe care magazinul îl emite legitim la primirea mărfii.
    // Serie şi contor proprii, complet separate de cele ale facturilor de
    // vânzare: sunt documente diferite, cu destinatari diferiţi, iar amestecarea
    // lor în aceeaşi numerotare face ambele registre ilizibile.

    @Column(name = "reception_series", length = 16)
    private String receptionSeries;

    @Column(name = "reception_number")
    private Integer receptionNumber;

    @Column(name = "reception_issued_at")
    private LocalDate receptionIssuedAt;

    // ---- Instantaneul furnizorului --------------------------------------
    //
    // Aceeaşi regulă ca la facturi: un furnizor redenumit peste un an nu are
    // voie să schimbe conţinutul unui document deja emis. Legătura către
    // Supplier rămâne pentru rapoarte; textul tipărit vine de aici.

    @Column(name = "supplier_name", length = 200)
    private String supplierName;

    @Column(name = "supplier_tax_id", length = 60)
    private String supplierTaxId;

    // ---- Fişierul care a produs recepţia --------------------------------

    @Column(name = "source_file_name", length = 300)
    private String sourceFileName;

    /**
     * Amprenta SHA-256 a conţinutului fişierului importat.
     *
     * <p>Unică, iar unicitatea este impusă de bază, nu doar verificată în cod.
     * Dublul import este accidentul cel mai frecvent la intrările de marfă şi
     * singurul care se manifestă fără nicio eroare: cineva încarcă acelaşi
     * fişier a doua oară şi stocul se dublează tăcut. O verificare în cod se
     * poate pierde între două cereri simultane; constrângerea nu.</p>
     *
     * <p>Rămâne {@code null} pentru achiziţiile introduse manual, iar MySQL
     * permite oricâte valori nule într-un index unic — deci constrângerea nu le
     * încurcă.</p>
     */
    @Column(name = "source_file_hash", length = 64, unique = true)
    private String sourceFileHash;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setPurchase(this);
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(PurchaseItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Eticheta NIR-ului, sau {@code null} pentru achiziţiile introduse manual,
     * care nu au primit niciodată un număr de recepţie.
     */
    public String getReceptionNumberLabel() {
        if (receptionSeries == null || receptionNumber == null) {
            return null;
        }
        return receptionSeries + " " + receptionNumber;
    }

    /** Adevărat când achiziţia a fost produsă dintr-un fişier importat. */
    public boolean isFromImport() {
        return sourceFileHash != null;
    }
}
