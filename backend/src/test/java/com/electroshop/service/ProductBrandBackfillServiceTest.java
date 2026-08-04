package com.electroshop.service;

import com.electroshop.dto.RebrandResult;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the backfill that repairs the brand column on products already stored in
 * the database.
 *
 * <p>The fixture reproduces the real damage found in the catalogue: "Ring" extracted
 * from the middle of "Behringer", "HP" extracted from the model number "HP2564", a
 * compatibility mention promoted to the maker's slot, a junk sentinel typed into the
 * brand column, an empty column, an inconsistently-cased brand, and a maker the table
 * has never heard of. Each mode is asserted on that same fixture, so the difference
 * between the three modes is explicit rather than implied.</p>
 */
public class ProductBrandBackfillServiceTest {

    private ProductRepository productRepository;
    private AuditService auditService;
    private ProductBrandBackfillService service;
    private List<Product> rows;

    private static Product product(long id, String name, String brand) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setBrand(brand);
        return p;
    }

    @BeforeEach
    void setUp() {
        rows = new ArrayList<>(List.of(
                // 1: "Ring" carved out of "Behringer" — the defect in its purest form.
                product(1L, "Behringer DJX900USB mixer DJ 5 canale", "Ring"),
                // 2: "HP" carved out of the model number HP2564.
                product(2L, "Statie meteo Ecowitt HP2564 Wittboy Pro", "HP"),
                // 3: the brand of the camera the flash fits, not of the flash.
                product(3L, "Flash VK750II TTL Camera Flash Speedlite for Nikon D7200", "Nikon"),
                // 4: compatibility mention, but a real maker is named earlier.
                product(4L, "Incarcator Belkin BoostCharge Pro 3 in 1 pentru Apple Watch", "Apple"),
                // 5: empty column, brand plainly present in the name.
                product(5L, "Casti audio in ear Jabra Elite 85t", ""),
                // 6: junk sentinel typed in place of a blank cell.
                product(6L, "Suport universal metalic reglabil", "0"),
                // 7: correct brand, inconsistent casing.
                product(7L, "Mouse LOGITECH MX Master 3S", "LOGITECH"),
                // 8: a maker the table does not know, correctly stored — must survive.
                product(8L, "Aparat de tuns Kemei KM-2600 profesional", "Kemei"),
                // 9: already correct in every respect — must never be reported.
                product(9L, "Casti wireless cu anulare zgomot Bose QuietComfort 45", "Bose")
        ));

        productRepository = mock(ProductRepository.class);
        auditService = mock(AuditService.class);
        when(productRepository.findAll()).thenReturn(rows);
        service = new ProductBrandBackfillService(
                productRepository, new ProductBrandResolver(), auditService);
    }

    private Product row(long id) {
        for (Product p : rows) {
            if (p.getId() == id) {
                return p;
            }
        }
        throw new AssertionError("nu există produsul " + id);
    }

    private RebrandResult.Change change(RebrandResult result, long id) {
        for (RebrandResult.Change c : result.changes()) {
            if (c.id() == id) {
                return c;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // MISSING
    // ---------------------------------------------------------------------

    /**
     * The conservative mode fills gaps and removes junk, and touches nothing that
     * already looks like a brand — not even the values that are demonstrably wrong.
     */
    @Test
    void missingModeOnlyFillsGapsAndRemovesJunk() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.MISSING, false);

        assertEquals(2, result.changed());
        assertEquals(1, result.filled());
        assertEquals(0, result.corrected());
        assertEquals(1, result.cleared());

        assertEquals("Jabra", row(5L).getBrand());
        assertNull(row(6L).getBrand());

        // Wrong values are left strictly alone in this mode.
        assertEquals("Ring", row(1L).getBrand());
        assertEquals("HP", row(2L).getBrand());
        assertEquals("LOGITECH", row(7L).getBrand());
    }

    // ---------------------------------------------------------------------
    // WRONG
    // ---------------------------------------------------------------------

    /**
     * The default mode repairs everything it can prove is wrong: substring artefacts,
     * compatibility mentions, junk sentinels and inconsistent spelling.
     */
    @Test
    void wrongModeRepairsEveryProvablyIncorrectValue() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.WRONG, false);

        assertEquals("Behringer", row(1L).getBrand());
        assertEquals("Ecowitt", row(2L).getBrand());
        assertEquals("Belkin", row(4L).getBrand());
        assertEquals("Jabra", row(5L).getBrand());
        assertEquals("Logitech", row(7L).getBrand());
        assertNull(row(6L).getBrand());

        // The flash's maker is unknown to the table, so the wrong value goes and
        // nothing takes its place. An empty brand is honest; "Nikon" was not.
        assertNull(row(3L).getBrand());

        // Four wrong values replaced (1, 2, 4, 7), one gap filled (5), two removed
        // without a replacement (3, 6). Nothing else in the fixture was touched.
        assertEquals(7, result.changed());
        assertEquals(4, result.corrected());
        assertEquals(1, result.filled());
        assertEquals(2, result.cleared());
    }

    /**
     * The narrowness of "provably wrong" is the whole safety argument for running this
     * mode unattended: a maker the table has never heard of is left untouched, and a
     * correct value is never reported as a change.
     */
    @Test
    void wrongModeLeavesUnknownButValidBrandsAlone() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.WRONG, false);

        assertEquals("Kemei", row(8L).getBrand());
        assertEquals("Bose", row(9L).getBrand());
        assertNull(change(result, 8L));
        assertNull(change(result, 9L));
    }

    /** Every repair carries the reason it was made, for the report the operator reads. */
    @Test
    void everyChangeExplainsItself() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.WRONG, true);
        for (RebrandResult.Change c : result.changes()) {
            assertNotNull(c.reason());
            assertTrue(c.reason().length() > 10, "motiv prea scurt: " + c.reason());
            assertNotNull(c.oldBrand());
            assertNotNull(c.newBrand());
        }
        assertEquals("Ring", change(result, 1L).oldBrand());
        assertEquals("Behringer", change(result, 1L).newBrand());
        assertEquals("—", change(result, 3L).newBrand());
        assertEquals("—", change(result, 5L).oldBrand());
    }

    // ---------------------------------------------------------------------
    // ALL
    // ---------------------------------------------------------------------

    /**
     * Full re-derivation reaches the same conclusions on this fixture, because the
     * resolver is deterministic and every stored value here is either provably wrong or
     * exactly what the resolver would have chosen. The mode matters when the table has
     * been extended, not when the data has.
     */
    @Test
    void allModeRederivesEveryProductAndKeepsWhatItCannotRecognise() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.ALL, false);

        assertEquals("Behringer", row(1L).getBrand());
        assertEquals("Ecowitt", row(2L).getBrand());
        assertNull(row(3L).getBrand());
        assertEquals("Belkin", row(4L).getBrand());
        assertEquals("Jabra", row(5L).getBrand());
        assertNull(row(6L).getBrand());
        assertEquals("Logitech", row(7L).getBrand());
        assertEquals("Bose", row(9L).getBrand());

        // The safety net: the table does not know Kemei, the name does, so it stays.
        assertEquals("Kemei", row(8L).getBrand());
        assertNull(change(result, 8L));
    }

    /**
     * The second safety net. The name introduces an unknown maker before a brand the
     * table recognises, so the stored value wins — otherwise every product mentioning a
     * component supplier would be relabelled with that supplier's name.
     */
    @Test
    void anUnknownBrandNamedBeforeAKnownOneSurvivesFullRederivation() {
        Product p = product(20L, "Obiectiv Benoison 85mm cu adaptor Sony E", "Benoison");
        rows.clear();
        rows.add(p);

        service.run(ProductBrandBackfillService.Mode.ALL, false);

        assertEquals("Benoison", p.getBrand());
    }

    // ---------------------------------------------------------------------
    // Preview / apply contract
    // ---------------------------------------------------------------------

    @Test
    void dryRunWritesNothing() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.ALL, true);

        assertTrue(result.dryRun());
        assertTrue(result.changed() > 0);
        assertEquals("Ring", row(1L).getBrand());
        assertEquals("HP", row(2L).getBrand());
        verify(productRepository, never()).saveAll(any());
    }

    /**
     * The operator approves a preview and expects exactly that to happen. The resolver
     * is deterministic, so the applied run must reproduce the preview row for row.
     */
    @Test
    void applyPersistsExactlyWhatThePreviewAnnounced() {
        RebrandResult preview = service.run(ProductBrandBackfillService.Mode.WRONG, true);
        RebrandResult applied = service.run(ProductBrandBackfillService.Mode.WRONG, false);

        assertEquals(preview.changed(), applied.changed());
        assertEquals(preview.filled(), applied.filled());
        assertEquals(preview.corrected(), applied.corrected());
        assertEquals(preview.cleared(), applied.cleared());
        assertEquals(preview.changes().size(), applied.changes().size());
        for (int i = 0; i < preview.changes().size(); i++) {
            assertEquals(preview.changes().get(i).id(), applied.changes().get(i).id());
            assertEquals(preview.changes().get(i).newBrand(), applied.changes().get(i).newBrand());
        }
        verify(productRepository).saveAll(any());
    }

    /** A repaired catalogue is a fixed point: running again proposes nothing. */
    @Test
    void runningTwiceChangesNothingTheSecondTime() {
        service.run(ProductBrandBackfillService.Mode.ALL, false);
        RebrandResult second = service.run(ProductBrandBackfillService.Mode.ALL, false);

        assertEquals(0, second.changed());
        assertTrue(second.changes().isEmpty());
    }

    @Test
    void scannedCountsTheWholeCatalogueNotJustTheChanges() {
        RebrandResult result = service.run(ProductBrandBackfillService.Mode.WRONG, true);
        assertEquals(rows.size(), result.scanned());
        assertTrue(result.changed() < result.scanned());
    }

    @Test
    void unknownModeInputSelectsTheDefault() {
        assertEquals(ProductBrandBackfillService.Mode.WRONG,
                ProductBrandBackfillService.Mode.parse(null));
        assertEquals(ProductBrandBackfillService.Mode.WRONG,
                ProductBrandBackfillService.Mode.parse("  "));
        assertEquals(ProductBrandBackfillService.Mode.WRONG,
                ProductBrandBackfillService.Mode.parse("ceva"));
        assertEquals(ProductBrandBackfillService.Mode.ALL,
                ProductBrandBackfillService.Mode.parse(" all "));
        assertEquals(ProductBrandBackfillService.Mode.MISSING,
                ProductBrandBackfillService.Mode.parse("missing"));
    }

    /** A row without a name has nothing to derive from and must be left untouched. */
    @Test
    void aNamelessRowIsNeverTouched() {
        Product nameless = product(30L, "", "Ring");
        rows.clear();
        rows.add(nameless);

        RebrandResult result = service.run(ProductBrandBackfillService.Mode.ALL, false);

        assertEquals(0, result.changed());
        assertEquals("Ring", nameless.getBrand());
    }
}
