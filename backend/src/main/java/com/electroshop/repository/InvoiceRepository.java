package com.electroshop.repository;

import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Accesul la documentele fiscale emise.
 *
 * <p>Interogările de listare fac {@code JOIN FETCH} pe linii acolo unde
 * apelantul chiar are nevoie de ele, și le lasă leneșe unde nu. Lista din
 * interfață afișează doar antetul fiecărui document — număr, dată, client,
 * total, statut — deci ar fi risipă să încarce toate pozițiile; descărcarea
 * PDF-ului, în schimb, are nevoie de tot, iar fără fetch ar produce o
 * interogare suplimentară pentru fiecare linie.</p>
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Documentul complet, cu linii, pentru tipărire.
     */
    @Query("""
            SELECT DISTINCT i FROM Invoice i
            LEFT JOIN FETCH i.lines
            WHERE i.id = :id
            """)
    Optional<Invoice> findWithLines(@Param("id") Long id);

    /**
     * Toate documentele emise pentru o comandă, cele mai noi întâi.
     *
     * <p>Include atât factura, cât și stornările ei. Suma totalurilor din
     * această listă este soldul facturat al comenzii, fără nicio ramificație
     * după tipul documentului — de asta au stornările valori negative.</p>
     */
    @Query("""
            SELECT DISTINCT i FROM Invoice i
            LEFT JOIN FETCH i.lines
            WHERE i.order.id = :orderId
            ORDER BY i.number DESC
            """)
    List<Invoice> findByOrderIdWithLines(@Param("orderId") Long orderId);

    /**
     * Există deja un document de tipul dat pentru comandă?
     *
     * <p>Folosit ca să nu se emită din greșeală o a doua factură pentru aceeași
     * comandă, și de migrarea documentelor vechi, care trebuie să poată rula de
     * două ori fără să dubleze nimic.</p>
     */
    boolean existsByOrderIdAndType(Long orderId, InvoiceType type);

    /**
     * Documentul cu seria și numărul date, dacă există.
     */
    Optional<Invoice> findBySeriesAndNumber(String series, Integer number);

    /**
     * Cel mai mare număr folosit până acum în serie.
     *
     * <p>Nu este sursa numerotării — aceea rămâne contorul din setările firmei —
     * ci plasa de siguranță care detectează dacă un document a fost creat direct
     * în bază, ocolind aplicația, și contorul a rămas în urmă.</p>
     */
    @Query("SELECT MAX(i.number) FROM Invoice i WHERE i.series = :series")
    Integer maxNumberInSeries(@Param("series") String series);

    /**
     * Lista filtrată pentru pagina de facturi.
     *
     * <p>Fiecare filtru este opțional și se dezactivează când parametrul este
     * {@code null}, ceea ce evită construirea dinamică a interogării. Căutarea
     * textuală acoperă numărul documentului, numele și adresa de e-mail ale
     * cumpărătorului, pentru că acestea sunt cele trei lucruri după care un
     * operator caută o factură când clientul sună.</p>
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE (:type IS NULL OR i.type = :type)
              AND (:status IS NULL OR i.status = :status)
              AND (:from IS NULL OR i.issuedAt >= :from)
              AND (:to IS NULL OR i.issuedAt <= :to)
              AND (:q IS NULL OR LOWER(i.buyerName) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(i.buyerEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR CAST(i.number AS string) LIKE CONCAT('%', :q, '%'))
            ORDER BY i.issuedAt DESC, i.number DESC
            """)
    Page<Invoice> search(@Param("type") InvoiceType type,
                         @Param("status") InvoiceStatus status,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("q") String q,
                         Pageable pageable);

    /**
     * Totalurile perioadei: {@code [numar_documente, net, tva, brut]}.
     *
     * <p>Calculate în bază, nu prin adunarea paginii curente. Un total care se
     * schimbă când operatorul trece la pagina a doua nu este un total, iar
     * încărcarea tuturor documentelor doar ca să le adune ar deveni costisitoare
     * exact în lunile cu multe facturi.</p>
     */
    @Query("""
            SELECT COUNT(i), COALESCE(SUM(i.totalNet), 0),
                   COALESCE(SUM(i.totalVat), 0), COALESCE(SUM(i.totalGross), 0)
            FROM Invoice i
            WHERE (:type IS NULL OR i.type = :type)
              AND (:status IS NULL OR i.status = :status)
              AND (:from IS NULL OR i.issuedAt >= :from)
              AND (:to IS NULL OR i.issuedAt <= :to)
              AND (:q IS NULL OR LOWER(i.buyerName) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(i.buyerEmail) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR CAST(i.number AS string) LIKE CONCAT('%', :q, '%'))
            """)
    List<Object[]> totalsFor(@Param("type") InvoiceType type,
                             @Param("status") InvoiceStatus status,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("q") String q);

    /**
     * Comenzile care au serie și număr de factură dar niciun rând în
     * {@code invoices}. Folosit o singură dată, de migrarea documentelor emise
     * înainte ca factura să devină entitate.
     */
    @Query("""
            SELECT o.id FROM Order o
            WHERE o.invoiceNumber IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.order.id = o.id)
            ORDER BY o.invoiceNumber
            """)
    List<Long> orderIdsMissingInvoice();
}
