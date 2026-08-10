package com.electroshop.dto;

import java.math.BigDecimal;

/**
 * Totalurile perioadei filtrate, afişate deasupra listei de facturi.
 *
 * <p>Calculate în baza de date peste tot setul filtrat, nu prin adunarea paginii
 * curente. Un total care se schimbă când operatorul trece la pagina a doua nu
 * este un total, iar încărcarea tuturor documentelor în memorie doar ca să fie
 * adunate ar deveni costisitoare exact în lunile cu multe facturi — adică exact
 * atunci când cifra este cerută cel mai des.</p>
 *
 * <p>Stornările intră în sumă cu valorile lor negative, deci {@code totalGross}
 * este cifra netă efectiv facturată în perioadă, nu suma brută a documentelor
 * emise. Aceasta este cifra care interesează, pentru că o factură stornată
 * integral nu a produs niciun venit.</p>
 */
public record InvoiceSummaryDto(
        long documentCount,
        BigDecimal totalNet,
        BigDecimal totalVat,
        BigDecimal totalGross,
        String currency
) {
    public static InvoiceSummaryDto of(long count, BigDecimal net, BigDecimal vat, BigDecimal gross) {
        return new InvoiceSummaryDto(
                count,
                net == null ? BigDecimal.ZERO : net,
                vat == null ? BigDecimal.ZERO : vat,
                gross == null ? BigDecimal.ZERO : gross,
                "RON"
        );
    }
}
