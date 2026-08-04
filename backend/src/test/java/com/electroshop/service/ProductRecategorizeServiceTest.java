package com.electroshop.service;

import com.electroshop.dto.RecategorizeResult;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the backfill that repairs category / subcategory on products already
 * stored in the database.
 *
 * <p>The fixture reproduces the real damage found in the catalogue: a category
 * literally named {@code "0"} next to an auto-filled subcategory, the product
 * condition {@code "Folosit"} typed into the category column, both columns empty,
 * a known subcategory filed under the wrong parent, and a custom subcategory the
 * shop owner typed deliberately. Each mode is asserted on that same fixture, so
 * the difference between the three modes is explicit rather than implied.</p>
 */
class ProductRecategorizeServiceTest {

    private ProductRepository productRepository;
    private AuditService auditService;
    private ProductRecategorizeService service;
    private List<Product> rows;

    private static Product product(long id, String name, String category, String subcategory) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setSubcategory(subcategory);
        return p;
    }

    @BeforeEach
    void setUp() {
        rows = new ArrayList<>(List.of(
                // 1: junk category next to the generic fallback subcategory — the exact
                //    "0 / Gadgeturi" pattern the broken import produced.
                product(1L, "Casti Sennheiser Momentum Sport, Bluetooth, HR Monitor", "0", "Gadgeturi"),
                // 2: product condition typed into the category column, subcategory empty.
                product(2L, "Telefon folosit Motorola Moto g56 5G, 256GB", "Folosit", ""),
                // 4: both columns meaningful but contradictory — Smartwatch under Audio.
                product(4L, "Smartwatch AMAZFIT Active 2, GPS, Android/iOS", "Audio", "Smartwatch & Ceasuri"),
                // 5: already correct, must never be reported as a change.
                product(5L, "Laptop ASUS Vivobook 15", "Laptopuri", "Laptopuri"),
                // 6: correct pair that the current rules would spell differently — only
                //    the ALL mode is allowed to touch it.
                product(6L, "Boxa portabila JBL Charge 5", "Audio", "Boxe & Sisteme audio"),
                // 7: deliberate custom subcategory outside the taxonomy, junk category.
                product(7L, "Produs cu subcategorie personalizata", "-", "Vitrina magazin"),
                // 9: unclassifiable name — must land on the fallback and be counted.
                product(9L, "Ceva total neinteligibil zzz qqq", "0", "0")
        ));

        // 3: both columns empty.
        rows.add(product(3L, "Aparat Foto Mirrorless Sony Alpha A6100 Kit cu Obiectiv E PZ 16-50mm",
                null, null));
        // 8: already correct and already canonical.
        rows.add(product(8L, "Camera supraveghere TP-Link Tapo C200", "Smart Home", "Camere supraveghere"));

        productRepository = mock(ProductRepository.class);
        auditService = mock(AuditService.class);
        when(productRepository.findAll()).thenReturn(rows);
        service = new ProductRecategorizeService(productRepository, new ProductCategorizer(), auditService);
    }

    private RecategorizeResult.Change changeFor(RecategorizeResult result, long id) {
        for (RecategorizeResult.Change change : result.changes()) {
            if (change.id() == id) {
                return change;
            }
        }
        return null;
    }

    private Product row(long id) {
        for (Product p : rows) {
            if (p.getId() == id) {
                return p;
            }
        }
        throw new IllegalStateException("produs inexistent în fixture: " + id);
    }

    // ---------------------------------------------------------------------
    // Dry run
    // ---------------------------------------------------------------------

    @Test
    void dryRunWritesNothing() {
        RecategorizeResult result =
                service.run(ProductRecategorizeService.Mode.INCONSISTENT, true);

        assertTrue(result.dryRun());
        assertEquals(9, result.scanned());
        verify(productRepository, never()).saveAll(any());
        assertEquals("0", row(1L).getCategory());
        assertEquals("Gadgeturi", row(1L).getSubcategory());
    }

    // ---------------------------------------------------------------------
    // PLACEHOLDER mode
    // ---------------------------------------------------------------------

    @Test
    void placeholderModeRepairsOnlyUnidentifiedProducts() {
        RecategorizeResult result =
                service.run(ProductRecategorizeService.Mode.PLACEHOLDER, true);

        assertEquals("PLACEHOLDER", result.mode());
        assertEquals(5, result.changed());

        // The junk category is replaced, and the generic subcategory next to it is
        // treated as "unclassified" rather than as evidence, so the product finally
        // reaches its real bucket instead of staying under Diverse electronice.
        RecategorizeResult.Change headphones = changeFor(result, 1L);
        assertNotNull(headphones);
        assertEquals("Audio", headphones.newCategory());
        assertEquals("Casti", headphones.newSubcategory());

        RecategorizeResult.Change phone = changeFor(result, 2L);
        assertNotNull(phone);
        assertEquals("Telefoane", phone.newCategory());
        assertEquals("Telefoane", phone.newSubcategory());

        RecategorizeResult.Change camera = changeFor(result, 3L);
        assertNotNull(camera);
        assertEquals("Foto & Video", camera.newCategory());
        assertEquals("Aparate foto", camera.newSubcategory());

        // A custom subcategory is preserved; only the junk category is replaced.
        RecategorizeResult.Change custom = changeFor(result, 7L);
        assertNotNull(custom);
        assertEquals("Vitrina magazin", custom.newSubcategory());

        // Contradictory but meaningful pairs are NOT this mode's business.
        assertEquals(null, changeFor(result, 4L));
        // Neither are correct pairs, whatever their spelling.
        assertEquals(null, changeFor(result, 5L));
        assertEquals(null, changeFor(result, 6L));
        assertEquals(null, changeFor(result, 8L));
    }

    @Test
    void unresolvedCountsOnlyProductsThatEndOnTheFallbackPair() {
        RecategorizeResult result =
                service.run(ProductRecategorizeService.Mode.PLACEHOLDER, true);

        assertEquals(1, result.unresolved());
        RecategorizeResult.Change gibberish = changeFor(result, 9L);
        assertNotNull(gibberish);
        assertEquals(ProductCategorizer.DEFAULT_CATEGORY, gibberish.newCategory());
        assertEquals(ProductCategorizer.DEFAULT_SUBCATEGORY, gibberish.newSubcategory());
    }

    // ---------------------------------------------------------------------
    // INCONSISTENT mode
    // ---------------------------------------------------------------------

    @Test
    void inconsistentModeAlsoRepairsWrongParentCategories() {
        RecategorizeResult result =
                service.run(ProductRecategorizeService.Mode.INCONSISTENT, true);

        assertEquals("INCONSISTENT", result.mode());
        assertEquals(6, result.changed());

        RecategorizeResult.Change watch = changeFor(result, 4L);
        assertNotNull(watch);
        assertEquals("Wearables", watch.newCategory());
        assertEquals("Smartwatch & Ceasuri", watch.newSubcategory());
        assertTrue(watch.reason().contains("nu este părintele"));

        // Product names are not re-read in this mode, so a coherent manual pair
        // survives even when the rule table would spell it differently.
        assertEquals(null, changeFor(result, 6L));
        assertEquals(null, changeFor(result, 5L));
        assertEquals(null, changeFor(result, 8L));
    }

    @Test
    void inconsistentModeIsTheDefaultForUnknownInput() {
        assertEquals(ProductRecategorizeService.Mode.INCONSISTENT,
                ProductRecategorizeService.Mode.parse(null));
        assertEquals(ProductRecategorizeService.Mode.INCONSISTENT,
                ProductRecategorizeService.Mode.parse("   "));
        assertEquals(ProductRecategorizeService.Mode.INCONSISTENT,
                ProductRecategorizeService.Mode.parse("ceva-necunoscut"));
        assertEquals(ProductRecategorizeService.Mode.ALL,
                ProductRecategorizeService.Mode.parse("all"));
        assertEquals(ProductRecategorizeService.Mode.PLACEHOLDER,
                ProductRecategorizeService.Mode.parse("  placeholder  "));
    }

    // ---------------------------------------------------------------------
    // ALL mode
    // ---------------------------------------------------------------------

    @Test
    void allModeRederivesEveryProductFromItsName() {
        RecategorizeResult result = service.run(ProductRecategorizeService.Mode.ALL, true);

        assertEquals("ALL", result.mode());
        assertEquals(7, result.changed());

        // The only mode that re-spells a coherent manual pair.
        RecategorizeResult.Change speaker = changeFor(result, 6L);
        assertNotNull(speaker);
        assertEquals("Audio", speaker.newCategory());
        assertEquals("Boxe & Soundbar", speaker.newSubcategory());

        // And the only mode that discards a custom subcategory.
        RecategorizeResult.Change custom = changeFor(result, 7L);
        assertNotNull(custom);
        assertEquals(ProductCategorizer.DEFAULT_SUBCATEGORY, custom.newSubcategory());

        // Products the rules already agree with stay out of the report.
        assertEquals(null, changeFor(result, 5L));
        assertEquals(null, changeFor(result, 8L));
    }

    // ---------------------------------------------------------------------
    // Apply
    // ---------------------------------------------------------------------

    @Test
    void applyPersistsExactlyWhatThePreviewAnnounced() {
        RecategorizeResult preview =
                service.run(ProductRecategorizeService.Mode.INCONSISTENT, true);
        RecategorizeResult applied =
                service.run(ProductRecategorizeService.Mode.INCONSISTENT, false);

        assertEquals(preview.changed(), applied.changed());
        assertEquals(preview.unresolved(), applied.unresolved());
        assertEquals(preview.changes().size(), applied.changes().size());
        assertTrue(applied.dryRun() == false);

        verify(productRepository).saveAll(any());
        verify(auditService).log(org.mockito.ArgumentMatchers.eq("PRODUCT_RECATEGORIZE"),
                org.mockito.ArgumentMatchers.eq("Product"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString());

        assertEquals("Audio", row(1L).getCategory());
        assertEquals("Casti", row(1L).getSubcategory());
        assertEquals("Wearables", row(4L).getCategory());
        assertEquals("Telefoane", row(2L).getCategory());
        assertEquals("Foto & Video", row(3L).getCategory());
        assertEquals("Vitrina magazin", row(7L).getSubcategory());
    }

    @Test
    void runningTwiceChangesNothingTheSecondTime() {
        for (ProductRecategorizeService.Mode mode : ProductRecategorizeService.Mode.values()) {
            setUp();
            service.run(mode, false);
            RecategorizeResult second = service.run(mode, true);
            assertEquals(0, second.changed(),
                    "modul " + mode + " nu este idempotent");
        }
    }
}
