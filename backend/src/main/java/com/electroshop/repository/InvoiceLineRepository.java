package com.electroshop.repository;

import com.electroshop.model.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Accesul la pozițiile de factură, pentru ștergerea definitivă a unui produs.
 *
 * <p>Repository-ul acesta a lipsit până acum, iar lipsa lui a fost cauza directă
 * a erorii pe care o raporta baza de date la ștergerea definitivă:</p>
 *
 * <pre>
 * Cannot delete or update a parent row: a foreign key constraint fails
 * (`railway`.`invoice_lines`, CONSTRAINT `FKm2jo8loc0ps5q6qtlx4nx6o3e`
 *  FOREIGN KEY (`product_id`) REFERENCES `products` (`id`))
 * </pre>
 *
 * <p>Ștergerea definitivă deconectează fiecare linie de istoric de produs, în loc
 * s-o șteargă. Când a fost scrisă, istoricul însemna două tabele:
 * {@code order_items} și {@code purchase_items}. Modulul de facturare a adăugat
 * un al treilea, {@code invoice_lines}, cu aceeași cheie străină către
 * {@code products} — dar rutina de ștergere nu a fost extinsă, așa că lăsa în
 * urmă exact rândurile care împiedicau ștergerea. Baza de date refuza corect o
 * operație incompletă.</p>
 *
 * <p>Nu există un {@code deleteByProductId} aici, deliberat. O factură emisă este
 * un document fiscal: pozițiile ei nu se șterg niciodată pentru că un produs a
 * dispărut din catalog. Se deconectează, iar denumirea tipărită rămâne în
 * {@code product_name}, care este o copie făcută la emitere, nu o referință.</p>
 */
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    /**
     * Dacă produsul apare pe vreo factură emisă.
     *
     * <p>Verificat înainte de o ștergere obișnuită, din același motiv pentru care
     * se verifică {@link OrderItemRepository#existsByProductId(Long)}: un produs
     * cu documente emise nu se șterge, se dezactivează. În practică orice linie
     * de factură provine dintr-o linie de comandă, deci verificarea comenzilor
     * ar acoperi de obicei și acest caz — dar „de obicei" nu este o garanție pe
     * care merită să se sprijine o operație ireversibilă, iar costul acestei
     * verificări este un index pe {@code product_id} care există deja.</p>
     */
    boolean existsByProductId(Long productId);

    /**
     * Toate pozițiile de factură care au referit vreodată acest produs.
     *
     * <p>Folosit exclusiv pe calea de ștergere definitivă. Fiecare poziție
     * returnată primește {@code product = null} și rămâne în rest neatinsă:
     * cantitate, preț unitar, bază, TVA, total și contorul de stornare rămân
     * exact cum erau. Factura tipărită nu se schimbă cu nimic.</p>
     */
    List<InvoiceLine> findByProductId(Long productId);
}
