package com.electroshop.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the classifier's behaviour on the exact defects that motivated the
 * rewrite.
 *
 * <p>The previous implementation matched keywords with an unbounded
 * {@code String.contains} over the whole product name and stopped at the first
 * rule that matched. That produced a specific, reproducible family of errors:
 * "MOTOROLA" contains "moto", so a phone was filed under Auto &amp; Moto;
 * "AMOLED" contains "oled", so a phone was filed under Televizoare; "Casting"
 * contains "casti", so a screen-mirroring kit was filed under headphones;
 * "HR Monitor" contains "monitor", so headphones were filed under Monitoare.
 * Each assertion below is one of those cases, so the defect cannot silently come
 * back through a future rule edit.</p>
 */
class ProductCategorizerTest {

    private final ProductCategorizer categorizer = new ProductCategorizer();

    private void assertCategorized(String name, String category, String subcategory) {
        ProductCategorizer.Categorization result = categorizer.categorize(name);
        assertEquals(category, result.category(), "categorie greșită pentru: " + name);
        assertEquals(subcategory, result.subcategory(), "subcategorie greșită pentru: " + name);
    }

    // ---------------------------------------------------------------------
    // Substring traps: a keyword that happens to sit inside a longer word
    // ---------------------------------------------------------------------

    @Test
    void motorolaIsAPhoneNotAMotorcyclePart() {
        assertCategorized("Telefon folosit Motorola Moto g56 5G, 256GB",
                "Telefoane", "Telefoane");
    }

    @Test
    void amoledIsAScreenSpecNotAnOledTelevision() {
        assertCategorized("Telefon Doogee S200X, 6.72\" AMOLED, 256GB",
                "Telefoane", "Telefoane");
    }

    @Test
    void hrMonitorOnHeadphonesDoesNotMakeThemAMonitor() {
        assertCategorized("Casti Sennheiser Momentum Sport, Bluetooth, HR Monitor",
                "Audio", "Casti");
    }

    @Test
    void sleepMonitorIsAWearableNotAMonitor() {
        assertCategorized("Monitorizarea somnului Garmin Index Sleep Monitor",
                "Wearables", "Smartwatch & Ceasuri");
    }

    @Test
    void aRealMonitorStillClassifiesAsAMonitor() {
        assertCategorized("Monitor Gaming LED Samsung Odyssey G5, 27\"",
                "Monitoare", "Monitoare");
    }

    // ---------------------------------------------------------------------
    // Head-noun weighting: the first noun names the product, the rest is detail
    // ---------------------------------------------------------------------

    @Test
    void aKeyboardCaseIsACaseNotAKeyboard() {
        assertCategorized("Husa tastatura WIWU iPad Pro 13 inch Keyboard Case",
                "Accesorii", "Huse & Folii");
    }

    @Test
    void aCameraSoldWithAKitLensIsStillACamera() {
        assertCategorized("Aparat Foto Mirrorless Sony Alpha A6100 Kit cu Obiectiv E PZ 16-50mm",
                "Foto & Video", "Aparate foto");
    }

    /**
     * A trailing "lipsă curele/încărcător" note describes what is missing from the
     * box. Those accessory nouns must not pull the product into Accesorii, which is
     * why they are positional ({@code notHead}) rather than absolute negatives.
     */
    @Test
    void aSmartwatchMissingItsStrapIsStillASmartwatch() {
        assertCategorized("Smartwatch AMAZFIT Active 2, GPS, Android/iOS resigilat lipsa curele/incarcator",
                "Wearables", "Smartwatch & Ceasuri");
    }

    // ---------------------------------------------------------------------
    // Camera family: four different rules compete for the word "camera"
    // ---------------------------------------------------------------------

    @Test
    void surveillanceCameraGoesToSmartHome() {
        assertCategorized("Camera supraveghere TP-Link Tapo C200",
                "Smart Home", "Camere supraveghere");
    }

    @Test
    void djiMicIsAMicrophoneNotADrone() {
        assertCategorized("DJI MIC Mini Wireless Microphone",
                "Audio", "Microfoane");
    }

    // ---------------------------------------------------------------------
    // Ordinary products must keep classifying correctly
    // ---------------------------------------------------------------------

    @Test
    void everydayProductsLandInTheirObviousBucket() {
        assertCategorized("Laptop ASUS Vivobook 15", "Laptopuri", "Laptopuri");
        assertCategorized("Boxa portabila JBL Charge 5", "Audio", "Boxe & Soundbar");
        assertCategorized("Intercom moto SENA 50S Dual Pack", "Auto & Moto", "Auto & Moto");
        assertCategorized("Statie de calcat cu abur Philips PerfectCare",
                "Electrocasnice", "Electrocasnice");
    }

    @Test
    void anUnrecognisableNameFallsBackInsteadOfGuessing() {
        assertCategorized("Kit ViewSonic Screen Casting",
                ProductCategorizer.DEFAULT_CATEGORY, ProductCategorizer.DEFAULT_SUBCATEGORY);
        assertCategorized("qqq zzz www",
                ProductCategorizer.DEFAULT_CATEGORY, ProductCategorizer.DEFAULT_SUBCATEGORY);
    }

    // ---------------------------------------------------------------------
    // Placeholder detection — the guard that stopped "0" from being saved
    // ---------------------------------------------------------------------

    @Test
    void placeholderRecognisesEveryJunkSentinelSeenInRealSheets() {
        assertTrue(ProductCategorizer.isPlaceholder(null));
        assertTrue(ProductCategorizer.isPlaceholder(""));
        assertTrue(ProductCategorizer.isPlaceholder("   "));
        assertTrue(ProductCategorizer.isPlaceholder("0"));
        assertTrue(ProductCategorizer.isPlaceholder("0.0"));
        assertTrue(ProductCategorizer.isPlaceholder("-"));
        assertTrue(ProductCategorizer.isPlaceholder("N/A"));
        assertTrue(ProductCategorizer.isPlaceholder("n/a"));
        assertTrue(ProductCategorizer.isPlaceholder("null"));
        assertTrue(ProductCategorizer.isPlaceholder("Folosit"));
        assertTrue(ProductCategorizer.isPlaceholder("folosit"));
        assertTrue(ProductCategorizer.isPlaceholder("Resigilat"));
    }

    @Test
    void placeholderAcceptsRealCategoryNames() {
        assertFalse(ProductCategorizer.isPlaceholder("Audio"));
        assertFalse(ProductCategorizer.isPlaceholder("Foto & Video"));
        assertFalse(ProductCategorizer.isPlaceholder("Diverse electronice"));
    }

    // ---------------------------------------------------------------------
    // Taxonomy: the rule table is the single source of truth for both columns
    // ---------------------------------------------------------------------

    @Test
    void everySubcategoryResolvesToExactlyOneParentCategory() {
        Map<String, java.util.List<String>> taxonomy = categorizer.taxonomy();
        assertFalse(taxonomy.isEmpty());
        for (Map.Entry<String, java.util.List<String>> entry : taxonomy.entrySet()) {
            for (String subcategory : entry.getValue()) {
                assertEquals(entry.getKey(), categorizer.canonicalCategoryFor(subcategory),
                        "subcategoria „" + subcategory + "” nu se întoarce la categoria ei");
            }
        }
    }

    @Test
    void canonicalLookupsAreCaseAndWhitespaceInsensitive() {
        assertEquals("Audio", categorizer.canonicalCategory("  audio "));
        assertEquals("Casti", categorizer.canonicalSubcategory("CASTI"));
        assertEquals("Audio", categorizer.canonicalCategoryFor(" casti "));
    }

    @Test
    void unknownNamesReturnNullInsteadOfGuessing() {
        assertNull(categorizer.canonicalCategory("Vitrina magazin"));
        assertNull(categorizer.canonicalSubcategory("Vitrina magazin"));
        assertNull(categorizer.canonicalCategoryFor("Vitrina magazin"));
        assertNull(categorizer.canonicalCategory(null));
        assertNull(categorizer.canonicalSubcategory(null));
    }

    @Test
    void theFallbackPairIsItselfPartOfTheTaxonomy() {
        assertNotNull(categorizer.canonicalCategory(ProductCategorizer.DEFAULT_CATEGORY));
        assertEquals(ProductCategorizer.DEFAULT_CATEGORY,
                categorizer.canonicalCategoryFor(ProductCategorizer.DEFAULT_SUBCATEGORY));
    }
}
