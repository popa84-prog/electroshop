package com.electroshop.service;

import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singura cale prin care marfa se întoarce în stoc.
 *
 * <h2>Problema pe care o rezolvă</h2>
 *
 * <p>După adăugarea modulului de facturare există două acțiuni distincte care
 * readuc produsele în stoc: anularea comenzii, care exista dintotdeauna în
 * {@code OrderService.updateStatus}, și stornarea facturii. Un operator care
 * storneaza factura și trece apoi comanda pe {@code CANCELLED} face o secvență
 * absolut firească — și, fără o evidență comună, ar adăuga cantitățile de două
 * ori. Rezultatul nu ar fi o eroare vizibilă, ci stoc pentru marfă care nu
 * există fizic, plus o valoare de inventar, un profit potențial și un indicator
 * de stoc critic false toate deodată.</p>
 *
 * <p>Interzicerea uneia dintre căi nu este o soluție: ambele sunt legitime și
 * apar în ordini diferite în practică. Soluția este ca restituirea să fie
 * <b>idempotentă la nivel de linie de comandă</b>.</p>
 *
 * <h2>Regula</h2>
 *
 * <p>Fiecare {@link OrderItem} ține în {@code restockedQuantity} câte bucăți
 * s-au întors deja. Orice cerere de restituire calculează, pe fiecare linie:</p>
 *
 * <pre>
 *   disponibil   = quantity - restockedQuantity
 *   de_restituit = min(disponibil, cerut)
 * </pre>
 *
 * <p>adaugă {@code de_restituit} în stocul produsului și incrementează contorul
 * cu aceeași valoare. Când {@code disponibil} este zero, apelul nu face nimic și
 * raportează zero — nu aruncă excepție, pentru că a doua cerere nu este o
 * greșeală a operatorului, ci consecința normală a două acțiuni corecte.</p>
 *
 * <p>Consecințele sunt exact cele dorite. Storno total urmat de anulare:
 * a doua operație găsește totul deja restituit și nu mișcă nimic. Anulare urmată
 * de storno: identic. Storno parțial de două bucăți dintr-o linie de cinci,
 * urmat de anulare: se restituie două, apoi restul de trei. Suma restituită nu
 * poate depăși niciodată cantitatea vândută, indiferent de ordine sau de câte
 * ori se repetă acțiunile.</p>
 *
 * <h2>Ce nu face acest serviciu</h2>
 *
 * <p>Nu schimbă statutul comenzii și nu emite niciun document. Restituirea
 * stocului este o operație de gestiune; cine o cere decide separat ce se
 * întâmplă cu comanda și cu factura. Amestecarea lor ar face imposibilă
 * stornarea unei facturi fără să se atingă starea logistică a comenzii, iar cele
 * două chiar sunt independente: o factură greșit emisă se stornează fără ca
 * marfa să se fi mișcat vreodată.</p>
 */
@Service
public class OrderRestockService {

    /**
     * Sub această valoare, dar peste zero, se trimite notificarea de stoc mic.
     * Aceeași constantă ca în {@code OrderService} și {@code ProductService}:
     * un prag diferit pe fiecare cale ar face notificarea să apară sau să
     * lipsească în funcție de ce buton a fost apăsat.
     */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public OrderRestockService(ProductRepository productRepository,
                               AuditService auditService,
                               NotificationService notificationService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Restituie tot ce a mai rămas nerestituit din comandă.
     *
     * <p>Folosit la anularea comenzii, unde nu există o selecție de linii:
     * comanda întreagă se întoarce, mai puțin ce s-a întors deja printr-o
     * stornare anterioară.</p>
     *
     * @param order  comanda ale cărei linii se restituie
     * @param reason textul care ajunge în jurnalul de audit
     * @return câte bucăți s-au adăugat efectiv în stoc, pe toate liniile
     */
    @Transactional
    public int restockAll(Order order, String reason) {
        if (order == null || order.getItems() == null) {
            return 0;
        }
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            requested.put(item.getId(), item.remainingToRestock());
        }
        return restock(order, requested, reason);
    }

    /**
     * Restituie cantități anume, pe linii anume.
     *
     * <p>Folosit la stornare, totală sau parțială. Cheia hărții este
     * identificatorul liniei de comandă; valoarea este cât se cere. Ce depășește
     * disponibilul se ignoră tăcut, pentru că apelantul a validat deja cererea
     * față de cantitățile facturate, iar aici plafonarea are rolul de plasă de
     * siguranță, nu de validare.</p>
     *
     * @param order          comanda ale cărei linii se restituie
     * @param quantityByItem cât se cere pentru fiecare linie de comandă
     * @param reason         textul care ajunge în jurnalul de audit
     * @return câte bucăți s-au adăugat efectiv în stoc
     */
    @Transactional
    public int restock(Order order, Map<Long, Integer> quantityByItem, String reason) {
        if (order == null || order.getItems() == null || quantityByItem == null
                || quantityByItem.isEmpty()) {
            return 0;
        }

        int totalRestocked = 0;
        Map<String, Integer> perProduct = new LinkedHashMap<>();

        for (OrderItem item : order.getItems()) {
            Integer asked = quantityByItem.get(item.getId());
            if (asked == null || asked <= 0) {
                continue;
            }

            int available = item.remainingToRestock();
            if (available <= 0) {
                // Deja restituit integral pe cealaltă cale. Nu este o eroare.
                continue;
            }

            int toRestock = Math.min(available, asked);

            Product product = item.getProduct();
            if (product == null) {
                // Produs șters definitiv prin ProductService#forceDeleteWithHistory:
                // linia de comandă se păstrează pentru contabilitate, dar nu mai
                // există niciun rând în catalog în care să se adune ceva. Contorul
                // avansează totuși, altfel fiecare anulare ulterioară ar reîncerca
                // la nesfârșit o operație care nu are cum să reușească.
                item.setRestockedQuantity(item.getRestockedQuantity() + toRestock);
                continue;
            }

            int oldStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            int newStock = oldStock + toRestock;
            product.setStockQuantity(newStock);
            productRepository.save(product);

            item.setRestockedQuantity(
                    (item.getRestockedQuantity() == null ? 0 : item.getRestockedQuantity()) + toRestock);

            totalRestocked += toRestock;
            perProduct.merge(product.getName(), toRestock, Integer::sum);

            // Simetric cu vânzarea: acolo o scădere sub prag trimite notificarea
            // de stoc mic. Aici o creștere peste prag nu trimite nimic — nu
            // există nimic de anunțat când marfa se întoarce — dar produsul care
            // rămâne sub prag chiar și după restituire merită semnalat, pentru că
            // restituirea a fost tot ce mai era de așteptat.
            boolean stillLow = newStock > 0 && newStock < LOW_STOCK_THRESHOLD;
            if (stillLow && oldStock == 0) {
                notificationService.notifyLowStock(product);
            }
        }

        if (totalRestocked > 0) {
            auditService.log("STOCK_RESTOCKED", "Order", order.getId(),
                    describe(perProduct, totalRestocked, reason));
        }

        return totalRestocked;
    }

    /**
     * Cât s-ar restitui dacă s-ar cere acum tot ce a rămas, fără să se schimbe
     * nimic. Folosit de interfață ca să spună operatorului, înainte să confirme,
     * câte bucăți se vor întoarce efectiv.
     */
    public int previewRemaining(Order order) {
        if (order == null || order.getItems() == null) {
            return 0;
        }
        int sum = 0;
        for (OrderItem item : order.getItems()) {
            sum += item.remainingToRestock();
        }
        return sum;
    }

    /**
     * Cât s-a restituit deja din fiecare linie, pentru afișare.
     */
    public Map<Long, Integer> restockedByItem(Order order) {
        Map<Long, Integer> out = new HashMap<>();
        if (order == null || order.getItems() == null) {
            return out;
        }
        for (OrderItem item : order.getItems()) {
            out.put(item.getId(),
                    item.getRestockedQuantity() == null ? 0 : item.getRestockedQuantity());
        }
        return out;
    }

    private static String describe(Map<String, Integer> perProduct, int total, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("Restituit în stoc ").append(total).append(" buc.");
        if (!perProduct.isEmpty()) {
            sb.append(" · ");
            boolean first = true;
            for (Map.Entry<String, Integer> e : perProduct.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(e.getKey()).append(" ×").append(e.getValue());
                first = false;
            }
        }
        if (reason != null && !reason.isBlank()) {
            sb.append(" · motiv: ").append(reason.trim());
        }
        return sb.toString();
    }
}
