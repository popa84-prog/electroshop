package com.electroshop.dto;

import java.time.LocalDate;

/**
 * Datele care însoțesc un fișier Excel importat ca intrare de marfă.
 *
 * <p>Niciunul dintre aceste câmpuri nu se află în fișierul de produse, și nici
 * nu ar avea ce căuta acolo. Numărul și data facturii sunt tipărite pe hârtia
 * primită de la furnizor; furnizorul însuși este ales din lista magazinului,
 * pentru că o livrare vine de la unul singur. Fișierul rămâne exact ce este —
 * lista mărfii — iar contextul comercial vine din dialog.</p>
 *
 * @param supplierId            furnizorul de la care a venit livrarea
 * @param supplierInvoiceNumber numărul facturii furnizorului, aşa cum apare pe ea
 * @param invoiceDate           data facturii furnizorului
 * @param receptionDate         data recepţiei fizice; implicit astăzi
 * @param notes                 menţiuni interne, opţionale
 */
public record GoodsReceiptRequest(
        Long supplierId,
        String supplierInvoiceNumber,
        LocalDate invoiceDate,
        LocalDate receptionDate,
        String notes
) {
}
