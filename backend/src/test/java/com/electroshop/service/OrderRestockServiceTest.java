package com.electroshop.service;

import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixează regula care împiedică marfa să se întoarcă de două ori în stoc.
 *
 * <p>De când există stornarea, produsele pot reveni pe două căi independente:
 * anularea comenzii și emiterea unui storno. Ambele sunt legitime, iar operatorii
 * le folosesc în ambele ordini. Fără un contor comun, secvența „stornez factura,
 * apoi anulez comanda" ar aduna cantitățile de două ori — fără nicio eroare
 * vizibilă, dar cu stoc pentru marfă care nu există fizic și cu valoarea de
 * inventar, profitul potențial și indicatorul de stoc critic false toate
 * deodată.</p>
 *
 * <p>Testele de mai jos parcurg sistematic combinațiile: totală urmată de totală,
 * parțială urmată de totală, în ambele ordini, plus cazurile de margine. Ce
 * verifică toate, împreună, este o singură invariantă: <b>suma restituită nu
 * poate depăși niciodată cantitatea vândută</b>, indiferent de ordinea sau de
 * numărul apelurilor.</p>
 */
class OrderRestockServiceTest {

    private ProductRepository productRepository;
    private AuditService auditService;
    private NotificationService notificationService;
    private OrderRestockService service;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        auditService = Mockito.mock(AuditService.class);
        notificationService = Mockito.mock(NotificationService.class);
        service = new OrderRestockService(productRepository, auditService, notificationService);
    }

    // ---- Cazul de bază ---------------------------------------------------

    @Test
    void restituireaTotalaAdaugaExactCantitateaVanduta() {
        Product p = product(1L, "Casti", 10);
        Order order = orderWith(item(100L, p, 3));

        int restocked = service.restockAll(order, "test");

        assertEquals(3, restocked, "trebuie restituite exact cele 3 bucăți vândute");
        assertEquals(13, p.getStockQuantity().intValue(), "stocul trebuie să crească de la 10 la 13");
    }

    @Test
    void adouaRestituireTotalaNuMaiAdaugaNimic() {
        Product p = product(1L, "Casti", 10);
        Order order = orderWith(item(100L, p, 3));

        service.restockAll(order, "prima");
        int second = service.restockAll(order, "a doua");

        assertEquals(0, second, "a doua restituire nu mai are ce adăuga");
        assertEquals(13, p.getStockQuantity().intValue(), "stocul rămâne 13, nu devine 16");
    }

    // ---- Cele două ordini care produceau dubla restituire ----------------

    @Test
    void stornoTotalUrmatDeAnulareRestituieOSingutaData() {
        Product p = product(1L, "Tastatura", 5);
        OrderItem it = item(100L, p, 4);
        Order order = orderWith(it);

        // Stornarea cere explicit cantitățile.
        int fromStorno = service.restock(order, Map.of(100L, 4), "storno");
        // Anularea cere tot ce a rămas.
        int fromCancel = service.restockAll(order, "anulare");

        assertEquals(4, fromStorno);
        assertEquals(0, fromCancel, "anularea de după storno nu mai are ce restitui");
        assertEquals(9, p.getStockQuantity().intValue(), "5 + 4, nu 5 + 4 + 4");
    }

    @Test
    void anulareUrmataDeStornoTotalRestituieOSingutaData() {
        Product p = product(1L, "Tastatura", 5);
        Order order = orderWith(item(100L, p, 4));

        int fromCancel = service.restockAll(order, "anulare");
        int fromStorno = service.restock(order, Map.of(100L, 4), "storno");

        assertEquals(4, fromCancel);
        assertEquals(0, fromStorno, "stornarea de după anulare nu mai are ce restitui");
        assertEquals(9, p.getStockQuantity().intValue());
    }

    // ---- Parțial ---------------------------------------------------------

    @Test
    void stornoPartialRestituieExactCatSeCereApoiAnulareaAduceRestul() {
        Product p = product(1L, "Mouse", 0);
        Order order = orderWith(item(100L, p, 5));

        int partial = service.restock(order, Map.of(100L, 2), "storno parțial");
        assertEquals(2, partial);
        assertEquals(2, p.getStockQuantity().intValue());

        int rest = service.restockAll(order, "anulare");
        assertEquals(3, rest, "au rămas 3 din cele 5");
        assertEquals(5, p.getStockQuantity().intValue(), "în total exact cele 5 vândute");
    }

    @Test
    void mailMulteStornariPartialeNuDepasescCantitateaVanduta() {
        Product p = product(1L, "Cablu", 0);
        Order order = orderWith(item(100L, p, 5));

        service.restock(order, Map.of(100L, 2), "prima");
        service.restock(order, Map.of(100L, 2), "a doua");
        service.restock(order, Map.of(100L, 2), "a treia");

        assertEquals(5, p.getStockQuantity().intValue(),
                "a treia cerere putea adăuga doar 1, nu 2: 2+2+1 = 5");
    }

    @Test
    void cerereaCarePlafoneazaPesteDisponibilRestituieDoarDisponibilul() {
        Product p = product(1L, "Boxa", 0);
        Order order = orderWith(item(100L, p, 3));

        int restocked = service.restock(order, Map.of(100L, 99), "cerere exagerată");

        assertEquals(3, restocked, "plafonat la cantitatea vândută");
        assertEquals(3, p.getStockQuantity().intValue());
    }

    // ---- Mai multe linii -------------------------------------------------

    @Test
    void liniileSeTrateazaIndependent() {
        Product a = product(1L, "Produs A", 10);
        Product b = product(2L, "Produs B", 20);
        Order order = orderWith(item(100L, a, 2), item(200L, b, 7));

        Map<Long, Integer> plan = new LinkedHashMap<>();
        plan.put(100L, 2);
        // linia b nu apare în cerere

        int restocked = service.restock(order, plan, "storno pe o singură linie");

        assertEquals(2, restocked);
        assertEquals(12, a.getStockQuantity().intValue(), "A s-a restituit");
        assertEquals(20, b.getStockQuantity().intValue(), "B nu a fost cerut, deci rămâne neatins");

        int rest = service.restockAll(order, "anulare");
        assertEquals(7, rest, "anularea aduce doar linia B");
        assertEquals(12, a.getStockQuantity().intValue());
        assertEquals(27, b.getStockQuantity().intValue());
    }

    // ---- Margini ---------------------------------------------------------

    @Test
    void produsulStersDefinitivAvanseazaContorulFaraSaAtingaCatalogul() {
        // Linia rămâne pentru contabilitate, dar nu mai există niciun rând în
        // catalog în care să se adune ceva. Contorul trebuie totuși să avanseze:
        // altfel fiecare anulare ulterioară ar reîncerca la nesfârșit o operație
        // care nu are cum să reușească.
        OrderItem orphan = item(100L, null, 4);
        Order order = orderWith(orphan);

        int first = service.restockAll(order, "anulare");
        int second = service.restockAll(order, "a doua anulare");

        assertEquals(0, first, "nu s-a adăugat nimic în stoc, produsul nu mai există");
        assertEquals(0, second);
        assertEquals(0, orphan.remainingToRestock(),
                "contorul a avansat, deci linia nu mai este reîncercată");
    }

    @Test
    void cantitatileZeroSauNegativeSuntIgnorate() {
        Product p = product(1L, "Hub", 8);
        Order order = orderWith(item(100L, p, 3));

        assertEquals(0, service.restock(order, Map.of(100L, 0), "zero"));
        assertEquals(0, service.restock(order, Map.of(100L, -5), "negativ"));
        assertEquals(8, p.getStockQuantity().intValue(), "stocul nu s-a mișcat");
    }

    @Test
    void oCerereGoalaSauNulaNuFaceNimic() {
        Product p = product(1L, "Docking", 4);
        Order order = orderWith(item(100L, p, 2));

        assertEquals(0, service.restock(order, new LinkedHashMap<>(), "gol"));
        assertEquals(0, service.restock(order, null, "null"));
        assertEquals(0, service.restock(null, Map.of(100L, 1), "comandă null"));
        assertEquals(4, p.getStockQuantity().intValue());
    }

    @Test
    void oLinieNecunoscutaInCerereEsteIgnorata() {
        Product p = product(1L, "Monitor", 6);
        Order order = orderWith(item(100L, p, 2));

        int restocked = service.restock(order, Map.of(999L, 5), "linie inexistentă");

        assertEquals(0, restocked);
        assertEquals(6, p.getStockQuantity().intValue());
    }

    // ---- Previzualizarea -------------------------------------------------

    @Test
    void previzualizareaSpuneCatSarRestituiFaraSaSchimbeNimic() {
        Product p = product(1L, "Router", 1);
        Order order = orderWith(item(100L, p, 6));

        assertEquals(6, service.previewRemaining(order));
        assertEquals(1, p.getStockQuantity().intValue(), "previzualizarea nu atinge stocul");

        service.restock(order, Map.of(100L, 4), "parțial");

        assertEquals(2, service.previewRemaining(order), "au rămas 2");
        assertTrue(service.restockedByItem(order).get(100L).intValue() == 4,
                "evidența pe linie arată 4 restituite");
    }

    // ---- Ajutătoare ------------------------------------------------------

    private static Product product(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setStockQuantity(stock);
        return p;
    }

    private static OrderItem item(Long id, Product product, int quantity) {
        OrderItem it = new OrderItem();
        it.setId(id);
        it.setProduct(product);
        it.setQuantity(quantity);
        it.setRestockedQuantity(0);
        return it;
    }

    private static Order orderWith(OrderItem... items) {
        Order order = new Order();
        order.setId(7L);
        for (OrderItem it : items) {
            order.addItem(it);
        }
        return order;
    }
}
