package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * O poziție de pe factură, cu denumirea și prețul îngheţate la emitere.
 *
 * <p><b>{@code productName} și {@code sku} sunt copii, nu referințe.</b>
 * Legătura {@code product_id} este păstrată ca să se poată restitui stocul și ca
 * rapoartele să poată grupa pe produs, dar textul tipărit vine din coloanele de
 * aici. Un produs redenumit după emitere nu schimbă factura, iar un produs șters
 * definitiv o lasă întreagă — motiv pentru care {@code product_id} este
 * nullable.</p>
 *
 * <h2>Cum se descompune prețul</h2>
 *
 * <p>În catalog și în comenzi, prețul unitar este prețul de raft, cu TVA
 * inclus. Factura trebuie să arate baza și TVA-ul separat, deci descompune
 * înapoi: {@code baza = brut / (1 + cota/100)} şi {@code tva = brut - baza}. A
 * trata prețul de raft ca bază de impozitare ar umfla fiecare document cu exact
 * cota de TVA, iar totalul facturii nu ar mai corespunde sumei încasate de la
 * client.</p>
 *
 * <p>Rotunjirea se face pe linie, la două zecimale. Totalurile documentului sunt
 * sumele acestor valori deja rotunjite, nu recalculări din totalul brut: liniile
 * sunt ce se tipărește, iar cine adună coloana de pe hârtie trebuie să obțină
 * exact totalul de pe hârtie.</p>
 *
 * <h2>Cantitățile negative de pe storno</h2>
 *
 * <p>Pe un document de tip {@link InvoiceType#STORNO} cantitatea și toate cele
 * trei valori sunt negative. Nu este o convenție de afișare: însumarea tuturor
 * documentelor unei comenzi dă direct soldul rămas de facturat, fără nicio
 * ramificaţie după tipul documentului.</p>
 */
@Entity
@Table(name = "invoice_lines", indexes = {
        @Index(name = "idx_invoiceline_invoice", columnList = "invoice_id"),
        @Index(name = "idx_invoiceline_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /**
     * Produsul, dacă mai există în catalog. Nullable din acelaşi motiv pentru
     * care {@code OrderItem.product} este nullable: ştergerea definitivă a unui
     * produs nu are voie să distrugă documente deja emise.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * Linia de comandă din care provine poziția.
     *
     * <p>Ținută ca {@code Long}, nu ca asociere: serviciul de restituire a
     * stocului are nevoie doar de identificator ca să știe pe care contor de
     * cantitate restituită să opereze, iar o asociere completă ar aduce cu ea o
     * dependență circulară între factură și comandă la ștergere.</p>
     */
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "product_name", nullable = false, length = 300)
    private String productName;

    @Column(length = 80)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    /** Prețul unitar cu TVA inclus, aşa cum a fost vândut. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "line_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineNet = BigDecimal.ZERO;

    @Column(name = "line_vat", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineVat = BigDecimal.ZERO;

    @Column(name = "line_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineGross = BigDecimal.ZERO;

    /**
     * Câte bucăți din această linie au fost deja stornate.
     *
     * <p>Există doar pe liniile facturilor obişnuite. Este contorul care face
     * imposibilă stornarea aceleiaşi bucăți de două ori: o cerere nouă poate
     * storna cel mult {@code quantity - stornoedQuantity}.</p>
     */
    @Column(name = "stornoed_quantity", nullable = false)
    private Integer stornoedQuantity = 0;

    /**
     * Cât se mai poate storna din linie.
     */
    public int remainingToStorno() {
        int q = quantity == null ? 0 : quantity;
        int s = stornoedQuantity == null ? 0 : stornoedQuantity;
        int left = q - s;
        return Math.max(left, 0);
    }

    /**
     * Adevărat când linia a fost creditată integral.
     */
    public boolean isFullyStornoed() {
        return remainingToStorno() == 0;
    }
}
