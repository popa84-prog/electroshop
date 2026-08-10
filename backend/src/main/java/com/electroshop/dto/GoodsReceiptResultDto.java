package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce s-a întâmplat — sau ce s-ar întâmpla — la importul unei intrări de marfă.
 *
 * <p>Aceeaşi formă în previzualizare şi la execuţia reală, cu {@code dryRun}
 * spunând care dintre ele este. Un rezultat de previzualizare care ar arăta
 * altfel decât cel real ar face previzualizarea inutilă exact în cazurile în
 * care contează, adică atunci când cele două nu coincid.</p>
 *
 * @param dryRun          adevărat când nimic nu a fost scris
 * @param supplierName    furnizorul livrării
 * @param receptionNumber eticheta NIR-ului emis, sau cel care s-ar emite
 * @param purchaseId      achiziţia creată, {@code null} în previzualizare
 * @param totalRows       câte rânduri neblanke a avut fişierul
 * @param productsCreated câte produse noi au intrat în catalog
 * @param productsRestocked câte produse existente au primit marfă
 * @param unitsReceived   câte bucăţi au intrat în total
 * @param totalValue      valoarea recepţiei, la preţ de achiziţie
 * @param lines           detaliul pe produs
 * @param warnings        observaţii care nu opresc importul
 */
public record GoodsReceiptResultDto(
        boolean dryRun,
        String supplierName,
        String receptionNumber,
        Long purchaseId,
        int totalRows,
        int productsCreated,
        int productsRestocked,
        int unitsReceived,
        BigDecimal totalValue,
        List<Line> lines,
        List<String> warnings
) {

    /**
     * O poziţie a recepţiei.
     *
     * <p>{@code costBefore} şi {@code costAfter} sunt incluse pentru că media
     * ponderată este exact lucrul pe care operatorul nu îl poate verifica din
     * cap. O intrare la un preţ mult diferit de cel anterior mută costul mediu,
     * iar consecinţa se vede în marjă abia peste săptămâni. Afişate una lângă
     * alta, la momentul importului, mutarea devine vizibilă imediat.</p>
     *
     * @param productName  denumirea produsului
     * @param isNew        adevărat pentru produsele create acum
     * @param quantity     câte bucăţi intră
     * @param unitCost     costul unitar din fişier
     * @param lineValue    valoarea liniei
     * @param stockBefore  stocul dinaintea intrării
     * @param stockAfter   stocul de după
     * @param costBefore   costul mediu dinainte
     * @param costAfter    costul mediu de după
     */
    public record Line(
            String productName,
            boolean isNew,
            int quantity,
            BigDecimal unitCost,
            BigDecimal lineValue,
            int stockBefore,
            int stockAfter,
            BigDecimal costBefore,
            BigDecimal costAfter
    ) {
    }
}
