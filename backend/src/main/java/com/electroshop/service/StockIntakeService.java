package com.electroshop.service;

import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Singura cale prin care marfa intră în stoc.
 *
 * <h2>Problema pe care o rezolvă</h2>
 *
 * <p>Înainte de acest serviciu existau două căi independente prin care
 * cantitățile creșteau, iar niciuna nu știa de cealaltă.</p>
 *
 * <p>{@code ProductImportService} scria stocul direct din fișierul Excel: un
 * produs nou primea cantitatea din coloană, iar în modul „intrare marfă"
 * cantitatea se aduna la stocul existent, cu media ponderată a costului.
 * {@code PurchaseService.create()} scria și el stocul, cu
 * {@code setStockQuantity(stoc + cantitate)} pe fiecare linie.</p>
 *
 * <p>Din momentul în care importul dintr-un Excel produce și o recepție, cele
 * două s-ar suprapune: fiecare bucată din fișier ar intra în stoc <b>de două
 * ori</b>. Nu ar apărea nicio eroare — stocul ar crește pur și simplu cu marfă
 * care nu există fizic, iar valoarea de inventar, profitul potențial și
 * indicatorul de stoc critic ar deveni false toate deodată. Este exact clasa de
 * defect pe care {@link OrderRestockService} o previne la ieșire; acesta este
 * echivalentul lui pentru intrare.</p>
 *
 * <h2>Un al doilea defect, reparat de aceeași reunificare</h2>
 *
 * <p>{@code PurchaseService.create()} adăuga stoc, dar nu atingea niciodată
 * {@code purchasePrice}. O achiziție înregistrată prin pagina Cumpărări la un
 * cost diferit lăsa produsul cu vechiul preț de achiziție, deci marja și
 * profitul potențial raportau un cost care nu mai corespundea mărfii de pe
 * raft. Importul făcea media ponderată corect; pagina de achiziții nu o făcea
 * deloc. Acum ambele trec pe aici, deci se comportă identic.</p>
 *
 * <h2>Media ponderată</h2>
 *
 * <pre>
 *   cost_nou = (stoc_vechi × cost_vechi + cantitate × cost_intrare)
 *              / (stoc_vechi + cantitate)
 * </pre>
 *
 * <p>Rotunjită la doi bani. Când stocul anterior este zero sau costul anterior
 * lipsește, se ia direct costul de intrare: nu există nimic de mediat, iar o
 * medie cu un termen inexistent ar produce o cifră inventată.</p>
 *
 * <p>Când intrarea nu vine cu un cost — cazul unei recepții pe cantitate, fără
 * valoare — costul produsului rămâne neatins. A-l trata ca zero ar trage media
 * în jos cu fiecare astfel de intrare și ar face marja să pară din ce în ce mai
 * bună, ceea ce este exact inversul adevărului.</p>
 */
@Service
public class StockIntakeService {

    private static final int SCALE = 2;

    private final ProductRepository productRepository;

    public StockIntakeService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Adaugă marfă în stoc și actualizează costul mediu.
     *
     * @param product      produsul care intră
     * @param quantity     câte bucăți; valorile zero sau negative nu fac nimic
     * @param unitCost     costul unitar de achiziție, sau {@code null} când
     *                     intrarea nu poartă valoare
     * @return rezultatul, cu stocul și costul dinainte și de după
     */
    @Transactional
    public Result intake(Product product, int quantity, BigDecimal unitCost) {
        if (product == null) {
            return Result.none();
        }

        int oldStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        BigDecimal oldCost = product.getPurchasePrice();

        if (quantity <= 0) {
            // Nu este o eroare: un fișier poate conține un rând cu cantitate
            // zero pentru o corecție de preț. Stocul nu se mișcă, dar costul
            // nici nu se mediază, pentru că nu a intrat nimic care să îl mute.
            return new Result(oldStock, oldStock, oldCost, oldCost, 0);
        }

        int newStock = oldStock + quantity;
        BigDecimal newCost = weightedAverage(oldStock, oldCost, quantity, unitCost);

        product.setStockQuantity(newStock);
        product.setPurchasePrice(newCost);
        productRepository.save(product);

        return new Result(oldStock, newStock, oldCost, newCost, quantity);
    }

    /**
     * Calculul mediei ponderate, izolat ca să poată fi verificat direct.
     *
     * <p>Public și static din același motiv pentru care descompunerea TVA din
     * facturare este: două implementări ale aceleiași formule se despart la
     * prima modificare, iar diferența dintre ele se manifestă ca un cost care
     * depinde de butonul apăsat.</p>
     *
     * @param oldStock stocul dinaintea intrării
     * @param oldCost  costul mediu dinainte, sau {@code null}
     * @param quantity cantitatea care intră, strict pozitivă
     * @param unitCost costul unitar al intrării, sau {@code null}
     * @return costul mediu de după intrare
     */
    public static BigDecimal weightedAverage(int oldStock, BigDecimal oldCost,
                                             int quantity, BigDecimal unitCost) {
        if (unitCost == null) {
            // Intrare fără valoare: costul rămâne cel dinainte. Tratarea lui ca
            // zero ar trage media în jos la fiecare astfel de recepție și ar
            // face marja să pară tot mai bună — inversul adevărului.
            return oldCost;
        }
        if (oldCost == null || oldStock <= 0) {
            // Nimic de mediat.
            return unitCost.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (quantity <= 0) {
            return oldCost;
        }

        BigDecimal existingValue = oldCost.multiply(BigDecimal.valueOf(oldStock));
        BigDecimal incomingValue = unitCost.multiply(BigDecimal.valueOf(quantity));
        int totalUnits = oldStock + quantity;

        return existingValue.add(incomingValue)
                .divide(BigDecimal.valueOf(totalUnits), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Ce s-a întâmplat la o intrare.
     *
     * @param stockBefore stocul dinainte
     * @param stockAfter  stocul de după
     * @param costBefore  costul mediu dinainte
     * @param costAfter   costul mediu de după
     * @param applied     câte bucăți au intrat efectiv
     */
    public record Result(int stockBefore, int stockAfter,
                         BigDecimal costBefore, BigDecimal costAfter,
                         int applied) {

        static Result none() {
            return new Result(0, 0, null, null, 0);
        }

        /** Adevărat când costul mediu s-a modificat. */
        public boolean costChanged() {
            if (costBefore == null) {
                return costAfter != null;
            }
            return costAfter != null && costBefore.compareTo(costAfter) != 0;
        }
    }
}
