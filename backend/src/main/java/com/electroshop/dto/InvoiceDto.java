package com.electroshop.dto;

import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un document fiscal, aşa cum îl consumă interfaţa.
 *
 * <p>Există în două forme, produse de cele două fabrici de mai jos. Lista de
 * facturi foloseşte {@link #summary(Invoice)}, care lasă poziţiile goale: pagina
 * afişează doar antetul fiecărui rând, iar încărcarea tuturor liniilor pentru
 * douăzeci de documente ar transporta date pe care nimeni nu le priveşte.
 * Dialogul de stornare şi vizualizarea unui document folosesc
 * {@link #full(Invoice)}, unde liniile sunt exact ce se editează.</p>
 */
public record InvoiceDto(
        Long id,
        String series,
        Integer number,
        String documentNumber,
        LocalDate issuedAt,
        String type,
        String status,
        Long orderId,
        Long originalInvoiceId,
        String originalDocumentNumber,

        String sellerName,
        String sellerCui,

        String buyerName,
        String buyerEmail,
        String buyerAddress,
        String buyerCui,

        boolean vatPayer,
        BigDecimal vatRate,
        BigDecimal totalNet,
        BigDecimal totalVat,
        BigDecimal totalGross,
        String currency,

        String cancelReason,
        LocalDateTime cancelledAt,
        String cancelledBy,
        String notes,

        /**
         * Adevărat când mai există cel puţin o bucată de stornat. Interfaţa
         * decide din el dacă arată butonul de stornare, în loc să repete
         * raţionamentul pe statut şi pe linii.
         */
        boolean stornable,

        List<InvoiceLineDto> lines
) {

    /** Varianta pentru listă: fără poziţii. */
    public static InvoiceDto summary(Invoice inv) {
        return build(inv, Collections.emptyList());
    }

    /** Varianta completă: cu poziţii. */
    public static InvoiceDto full(Invoice inv) {
        List<InvoiceLineDto> lines = new ArrayList<>();
        if (inv.getLines() != null) {
            for (InvoiceLine line : inv.getLines()) {
                lines.add(InvoiceLineDto.from(line));
            }
        }
        return build(inv, lines);
    }

    private static InvoiceDto build(Invoice inv, List<InvoiceLineDto> lines) {
        Invoice original = inv.getOriginalInvoice();
        return new InvoiceDto(
                inv.getId(),
                inv.getSeries(),
                inv.getNumber(),
                inv.getDocumentNumber(),
                inv.getIssuedAt(),
                inv.getType() == null ? null : inv.getType().name(),
                inv.getStatus() == null ? null : inv.getStatus().name(),
                inv.getOrder() == null ? null : inv.getOrder().getId(),
                original == null ? null : original.getId(),
                original == null ? null : original.getDocumentNumber(),

                inv.getSellerName(),
                inv.getSellerCui(),

                inv.getBuyerName(),
                inv.getBuyerEmail(),
                inv.getBuyerAddress(),
                inv.getBuyerCui(),

                inv.isVatPayer(),
                inv.getVatRate(),
                inv.getTotalNet(),
                inv.getTotalVat(),
                inv.getTotalGross(),
                inv.getCurrency(),

                inv.getCancelReason(),
                inv.getCancelledAt(),
                inv.getCancelledBy(),
                inv.getNotes(),

                stornable(inv),
                lines
        );
    }

    /**
     * Se mai poate storna ceva din document?
     *
     * <p>Judecat din tip şi statut, <b>nu</b> prin parcurgerea liniilor. Cele
     * două sunt echivalente, pentru că statutul este el însuşi calculat din
     * linii de {@code InvoiceCancellationService.recomputeStatus}: o factură
     * ajunge {@code CANCELLED} exact când fiecare linie a fost stornată
     * integral. Diferenţa este de cost — varianta pe linii ar forţa încărcarea
     * poziţiilor pentru fiecare rând din listă, adică douăzeci de interogări
     * suplimentare pe pagină, pentru un răspuns pe care statutul îl conţine
     * deja.</p>
     *
     * <p>Un storno nu se stornează niciodată: dacă a fost emis greşit, se emite
     * o factură nouă pentru ce a rămas de facturat.</p>
     */
    private static boolean stornable(Invoice inv) {
        if (inv.getType() != InvoiceType.INVOICE) {
            return false;
        }
        return inv.getStatus() == InvoiceStatus.ISSUED
                || inv.getStatus() == InvoiceStatus.PARTIALLY_STORNOED;
    }
}
