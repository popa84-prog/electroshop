package com.electroshop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the brand resolver's behaviour on the exact defects that motivated it.
 *
 * <p>Every product name below is taken from the live catalogue, and every expectation
 * is the value the brand column should have held instead of the one it did. The two
 * defect shapes are tested separately because they fail for different reasons and are
 * caught by different mechanisms: substring artefacts die on whole-word tokenisation,
 * compatibility mentions die on first-match-wins and marker suppression.</p>
 */
class ProductBrandResolverTest {

    private final ProductBrandResolver resolver = new ProductBrandResolver();

    private void assertBrand(String expected, String name) {
        assertEquals(expected, resolver.resolve(name), "marcă greșită pentru: " + name);
    }

    // ---------------------------------------------------------------------
    // Shape (a): the stored brand is not a word in the name at all
    // ---------------------------------------------------------------------

    /**
     * The whole reason this class exists. "Behringer" contains "ring", "Steering"
     * contains "ring", "In-Ear Monitoring" contains "ring", and the sheet's substring
     * matcher filed all three under the Amazon doorbell company. One token is either
     * equal to "ring" or it is not, and none of these are.
     */
    @Test
    void ringIsNeverExtractedFromTheMiddleOfALongerWord() {
        assertBrand("Behringer", "Behringer UT300 ultr tremolo");
        assertBrand("Behringer", "Behringer DJX900USB mixer DJ 5 canale");
        assertBrand("Öhlins", "Ohlins line Sd 008 Universal Steering Damper");
        assertBrand("Sennheiser", "Casti Sennheiser IE 100 PRO In-Ear Monitoring");
    }

    /**
     * "HP2564" is one token and is not "hp". A model number never names a manufacturer.
     */
    @Test
    void aModelNumberIsNotAManufacturer() {
        assertBrand("Ecowitt", "Statie meteo Ecowitt HP2564 Wittboy Pro");
        assertBrand("UGREEN", "Casti Stereo Wireless UGREEN Studio Pro HiTune Max5");
        assertBrand("CarpodGo", "CarpodGo T3 Pro Portable 60fps Wireless CarPlay");
    }

    /** A lens line resolves to the company that makes it. */
    @Test
    void anAliasResolvesToItsParentBrand() {
        assertBrand("Nikon", "Obiectiv Foto Mirrorless Nikkor Z 26mm f/2.8");
        assertBrand("Panasonic", "Aparat foto Lumix DC-S5M2");
        assertBrand("Focusrite", "Interfata audio Scarlett Solo 4th Gen");
        assertBrand("Apple", "Telefon iPhone 15 Pro Max 256GB");
        assertBrand("Xiaomi", "Telefon Redmi Note 13 Pro 5G");
    }

    // ---------------------------------------------------------------------
    // Shape (b): the brand is a real word in the name, but names another device
    // ---------------------------------------------------------------------

    /**
     * The maker is introduced before the compatibility list, so scanning left to right
     * never reaches the second brand. These four names all ended up carrying the brand
     * of the camera or phone they merely fit.
     */
    @Test
    void theBrandNamedFirstIsTheMakerNotTheOneItFits() {
        assertBrand("Sigma", "Obiectiv Foto Mirrorless Sigma 10 18mm f/2.8 DC DN pentru Sony E");
        assertBrand("Sigma", "Obiectiv Sigma 90mm f/2.8 DG DN Contemporary pentru Sony E");
        assertBrand("Viltrox", "Obiectiv Viltrox 85mm Cadru complet pentru Sony E");
        assertBrand("Belkin", "Incarcator Belkin BoostCharge Pro 3 in 1 pentru Apple Watch");
        assertBrand("ATOTO", "Navigatie auto Android ATOTO A6 Performance cu Apple CarPlay");
        assertBrand("SmallRig", "Kit SmallRig pentru iPhone 15 Pro Max");
        assertBrand("TCL", "Televizor TCL 55 inch cu Google TV");
    }

    /**
     * When no maker precedes the compatibility list, the marker itself must suppress the
     * match. Returning null is the correct answer: the flash is made by a company this
     * table does not know, and "Nikon" would be a lie on the storefront filter.
     */
    @Test
    void aCompatibilityMentionWithNoMakerBeforeItYieldsNoBrand() {
        assertNull(resolver.resolve("Flash VK750II TTL Camera Flash Speedlite for Nikon D7200"));
        assertNull(resolver.resolve("Obiectiv Benoison 85mm f1.8 Portrait Lens for Sony E Mount"));
    }

    /**
     * The other half of the suppression rule, and the reason it uses a bridge test
     * rather than "any marker earlier in the name". "pentru gaming" states what the
     * product is for, not what it attaches to, and "gaming" is neither a connector nor a
     * device noun — so the marker's reach stops there and Logitech survives.
     */
    @Test
    void aMarkerDoesNotReachAcrossAWordThatDescribesTheUse() {
        assertBrand("Logitech", "Casti gaming pentru PC, driver 50mm, Logitech G435");
        assertBrand("Razer", "Mouse wireless pentru gaming competitiv Razer Viper V3");
    }

    // ---------------------------------------------------------------------
    // Names whose brand sits far from the start and is still correct
    // ---------------------------------------------------------------------

    /**
     * Distance from the start of the name is not evidence of anything. Romanian
     * listings put a long descriptive prefix before the maker, and several correct
     * brands in the catalogue sit further right than the wrong ones above. Any rule
     * based on position alone would have broken all of these.
     */
    @Test
    void aLongDescriptivePrefixDoesNotHideTheBrand() {
        assertBrand("Jabra", "Casti audio in ear Jabra Elite 85t");
        assertBrand("Bose", "Casti wireless cu anulare zgomot Bose QuietComfort 45");
        assertBrand("Philips", "Statie de calcat cu abur Philips PerfectCare 7000");
        assertBrand("Biomaser", "Masina de tatuat semi permanent Biomaser P300");
        assertBrand("Medicube", "Dispozitiv ingrijire faciala anti rid Medicube Age R Booster");
        assertBrand("Wolfang", "Camera video sport Wolfang GA420 4K");
        assertBrand("Blink", "Camera supraveghere exterior Blink Outdoor 4");
    }

    // ---------------------------------------------------------------------
    // Ambiguity guards: trademarks that are also ordinary vocabulary
    // ---------------------------------------------------------------------

    /**
     * "Ring" names a company, a lamp and a finger measurement. Only the first is a
     * brand, and the word that follows is what tells them apart.
     */
    @Test
    void ringIsGuardedAgainstTheWordsThatChangeItsMeaning() {
        assertNull(resolver.resolve("Ring Light LED 18 inch cu trepied"));
        assertBrand("Oura", "Oura Gen3 Heritage Connected Ring Size 12");
        assertBrand("Ring", "Sonerie video Ring Video Doorbell 4");
    }

    /**
     * Vocabulary-like trademarks are only credible where a maker's name belongs. Deep
     * inside a spec dump, "orient" is a direction and "sharp" is an adjective.
     */
    @Test
    void vocabularyTrademarksAreOnlyReadNearTheHeadOfTheName() {
        assertBrand("Orient", "Ceas automatic Orient Bambino Version 4");
        assertNull(resolver.resolve("Trepied foto aluminiu, cap cu bila, reglaj fin "
                + "pentru a orient cadrul rapid"));
    }

    // ---------------------------------------------------------------------
    // Refusing to guess
    // ---------------------------------------------------------------------

    @Test
    void anUnrecognisableNameProducesNoBrandInsteadOfAGuess() {
        assertNull(resolver.resolve("qqq zzz www"));
        assertNull(resolver.resolve("Suport universal metalic reglabil"));
        assertNull(resolver.resolve(""));
        assertNull(resolver.resolve(null));
    }

    // ---------------------------------------------------------------------
    // The safety net the backfill depends on
    // ---------------------------------------------------------------------

    /**
     * A brand the table has never heard of is a gap in the table, not an error in the
     * data, and must survive inspection so the backfill leaves it alone.
     */
    @Test
    void anUnknownBrandPresentInTheNameIsAccepted() {
        assertTrue(resolver.mentionsAsOwnBrand("Aparat de tuns Kemei KM-2600 profesional", "Kemei"));
        assertTrue(resolver.mentionsAsOwnBrand("Boxa portabila Tronsmart Bang Max", "Tronsmart"));
    }

    /** The values the substring matcher invented must all fail inspection. */
    @Test
    void aBrandThatIsNotInTheNameIsRejected() {
        assertFalse(resolver.mentionsAsOwnBrand("Behringer DJX900USB mixer DJ 5 canale", "Ring"));
        assertFalse(resolver.mentionsAsOwnBrand("Statie meteo Ecowitt HP2564 Wittboy Pro", "HP"));
        assertFalse(resolver.mentionsAsOwnBrand("Kit SmallRig pentru iPhone 15 Pro Max", "iPhone"));
    }

    /** A brand present only as the device the product fits must fail inspection too. */
    @Test
    void aBrandPresentOnlyAsACompatibilityMentionIsRejected() {
        assertFalse(resolver.mentionsAsOwnBrand(
                "Flash VK750II TTL Camera Flash Speedlite for Nikon D7200", "Nikon"));
        assertFalse(resolver.mentionsAsOwnBrand(
                "Incarcator Belkin BoostCharge 2 in 1 pentru Apple Watch", "Apple"));
    }

    /**
     * A known brand is held to the table's own guards, so the safety net cannot readmit
     * a match the resolver deliberately refused.
     */
    @Test
    void aKnownBrandIsInspectedWithItsOwnGuards() {
        assertFalse(resolver.mentionsAsOwnBrand("Ring Light LED 18 inch cu trepied", "Ring"));
    }

    // ---------------------------------------------------------------------
    // Spelling
    // ---------------------------------------------------------------------

    @Test
    void casingCollapsesToOneSpellingPerBrand() {
        assertEquals("Logitech", resolver.canonicalise("LOGITECH"));
        assertEquals("Logitech", resolver.canonicalise("logitech"));
        assertEquals("Logitech", resolver.canonicalise("  Logitech  "));
        assertEquals("TP-Link", resolver.canonicalise("tp-link"));
        assertEquals("Apple", resolver.canonicalise("iPhone"));
    }

    @Test
    void anUnknownSpellingIsReturnedUnchangedApartFromTrimming() {
        assertEquals("Kemei", resolver.canonicalise(" Kemei "));
        assertNull(resolver.canonicalise("   "));
        assertNull(resolver.canonicalise(null));
    }

    @Test
    void positionIsReportedAlongsideTheBrand() {
        ProductBrandResolver.Match match = resolver.resolveMatch("Casti audio in ear Jabra Elite 85t");
        assertEquals("Jabra", match.brand());
        assertEquals(4, match.index());
    }

    @Test
    void theTableReportsWhatItKnows() {
        assertTrue(resolver.isKnownBrand("Sony"));
        assertTrue(resolver.isKnownBrand("nikkor"));
        assertFalse(resolver.isKnownBrand("Kemei"));
        assertTrue(resolver.knownBrands().contains("Behringer"));
    }
}
