package com.electroshop.service;

import com.electroshop.dto.RebrandResult;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Repairs the {@code brand} column of products that are already in the database.
 *
 * <p>{@link ProductImportService} now derives a brand from the product name whenever
 * the spreadsheet does not carry a usable value, but that guard only runs at import
 * time. Rows created before it existed keep whatever the supplier sheet contained —
 * including the values its own substring matching invented: "Ring" for a Behringer
 * mixer, for an Öhlins steering damper and for Sennheiser in-ear monitors; "HP" for an
 * Ecowitt weather station whose model number happens to read HP2564. This service is
 * the backfill that brings the existing catalogue in line with
 * {@link ProductBrandResolver}.</p>
 *
 * <p>Every run is available as a preview first ({@code dryRun = true}), which reads the
 * catalogue, computes the full change list and writes nothing. The operator reviews
 * that list in the admin UI and only then repeats the call with {@code dryRun = false}
 * to persist exactly the same decisions — the resolution is deterministic, so the
 * applied run cannot diverge from the preview.</p>
 */
@Service
public class ProductBrandBackfillService {

    /**
     * How many individual changes travel back to the browser. The counters in
     * {@link RebrandResult} always cover the whole run; only the per-product detail
     * list is capped, so a catalogue of any size produces a response the admin UI can
     * render without stalling. The UI detects truncation by comparing {@code changed}
     * against {@code changes.size()}.
     */
    private static final int MAX_REPORTED_CHANGES = 1000;

    /** What the report prints in place of an absent brand. */
    private static final String EMPTY_DISPLAY = "—";

    /**
     * Which products a run touches.
     */
    public enum Mode {

        /**
         * Only products with no usable brand at all: an empty column, or a junk
         * sentinel such as {@code "0"}, {@code "-"}, {@code "N/A"} or a condition word
         * that a supplier sheet typed in place of a blank cell. A product that already
         * carries a real-looking brand is never touched, even a wrong one. This is the
         * mode to run on a fresh import when the existing values are trusted.
         */
        MISSING,

        /**
         * Everything {@link #MISSING} covers, plus the values that are provably wrong:
         * a brand that does not appear in the product's own name as a whole word, and a
         * brand that appears only inside a compatibility list. Casing is normalised in
         * this mode too, so {@code LOGITECH} and {@code Logitech} stop being two
         * separate entries in the storefront's brand filter.
         *
         * <p>"Provably wrong" is a deliberately narrow test, and it is what makes this
         * mode safe to run without reviewing every row. A brand the resolver has never
         * heard of is left alone as long as the name contains it: an unknown maker is a
         * gap in the table, not an error in the data.</p>
         */
        WRONG,

        /**
         * Re-derives the brand from the product name for every product. This is the
         * mode to use after the brand table itself has been extended, and the only mode
         * that can replace a brand which is present in the name but is not the one the
         * name introduces first.
         *
         * <p>It applies the same safety net as the category backfill: it will not trade
         * information for ignorance. When the table recognises nothing in a name, a
         * meaningful stored brand is kept rather than erased — and when the stored
         * brand appears in the name <em>earlier</em> than the one the table recognises,
         * the stored value wins, because a product name introduces its own maker before
         * it names anything else. That is how a brand the table has yet to learn
         * survives a full re-derivation.</p>
         */
        ALL;

        /** Parses the query-string value; unknown or missing input selects {@link #WRONG}. */
        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return WRONG;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            return WRONG;
        }
    }

    private final ProductRepository productRepository;
    private final ProductBrandResolver brandResolver;
    private final AuditService auditService;

    public ProductBrandBackfillService(ProductRepository productRepository,
                                       ProductBrandResolver brandResolver,
                                       AuditService auditService) {
        this.productRepository = productRepository;
        this.brandResolver = brandResolver;
        this.auditService = auditService;
    }

    /**
     * Runs the backfill.
     *
     * @param mode   which products to consider
     * @param dryRun {@code true} to compute the report without writing anything
     * @return the full report, identical in shape for a preview and an applied run
     */
    @Transactional
    public RebrandResult run(Mode mode, boolean dryRun) {
        List<Product> products = productRepository.findAll();
        List<RebrandResult.Change> changes = new ArrayList<>();
        List<Product> modified = new ArrayList<>();

        int changed = 0;
        int filled = 0;
        int corrected = 0;
        int cleared = 0;

        for (Product product : products) {
            String name = product.getName();
            if (name == null || name.isBlank()) {
                // Nothing to derive a brand from and nothing safe to guess — a nameless
                // row is left exactly as it is and reported by omission.
                continue;
            }

            String oldBrand = product.getBrand();
            Resolution resolution = resolve(mode, name, oldBrand);
            if (resolution == null) {
                continue;
            }
            if (same(oldBrand, resolution.brand())) {
                // The resolver agrees with what is already stored: not a change.
                continue;
            }

            changed++;
            boolean hadBrand = !ProductCategorizer.isPlaceholder(oldBrand);
            if (resolution.brand() == null) {
                cleared++;
            } else if (hadBrand) {
                corrected++;
            } else {
                filled++;
            }

            if (changes.size() < MAX_REPORTED_CHANGES) {
                changes.add(new RebrandResult.Change(
                        product.getId(),
                        name,
                        display(oldBrand),
                        display(resolution.brand()),
                        resolution.reason()));
            }

            if (!dryRun) {
                product.setBrand(resolution.brand());
                modified.add(product);
            }
        }

        if (!dryRun && !modified.isEmpty()) {
            productRepository.saveAll(modified);
            auditService.log("PRODUCT_REBRAND", "Product", null,
                    "Mod: " + mode.name() + " — " + modified.size()
                            + " mărci corectate din " + products.size() + " scanate ("
                            + filled + " completate, " + corrected + " înlocuite, "
                            + cleared + " eliminate).");
        }

        return new RebrandResult(dryRun, mode.name(), products.size(),
                changed, filled, corrected, cleared, changes);
    }

    /** The brand a product should end up with, plus the Romanian explanation for the report. */
    private record Resolution(String brand, String reason) {}

    /**
     * Decides what this product's brand should become, or {@code null} when the selected
     * mode does not touch it.
     */
    private Resolution resolve(Mode mode, String name, String stored) {
        boolean missing = ProductCategorizer.isPlaceholder(stored);

        if (mode == Mode.ALL) {
            return resolveFully(name, stored, missing);
        }

        if (missing) {
            String derived = brandResolver.resolve(name);
            if (derived == null) {
                // Nothing recognised. An empty column stays empty — that is not a
                // change. A junk sentinel is different: "0" or "N/A" sitting in the
                // brand column reaches the storefront filter as a brand named "0", so
                // it is removed even though there is nothing to put in its place.
                return blankOut(stored);
            }
            return new Resolution(derived, "Marcă lipsă, completată din denumirea produsului");
        }

        if (mode != Mode.WRONG) {
            // MISSING mode and the product already has a brand — leave it alone.
            return null;
        }

        if (brandResolver.mentionsAsOwnBrand(name, stored)) {
            // The stored value is present in the name as this product's own brand. It
            // stays; only its spelling is brought to the canonical form so the
            // storefront filter shows one entry instead of "LOGITECH" and "Logitech".
            String canonical = brandResolver.canonicalise(stored);
            if (same(stored, canonical)) {
                return null;
            }
            return new Resolution(canonical, "Scriere uniformizată a mărcii");
        }

        // The stored value is not in the name as this product's own brand: either it
        // never appears there (a substring artefact such as "Ring" from "Behringer"),
        // or it appears only as the device the product is compatible with. Both are
        // proof the value is wrong, so it is replaced — or removed when the name names
        // no manufacturer the table knows.
        String derived = brandResolver.resolve(name);
        if (derived == null) {
            return new Resolution(null,
                    "Marca stocată nu apare în denumire — valoare greșită, eliminată");
        }
        return new Resolution(derived,
                "Marca stocată nu identifică producătorul — înlocuită din denumire");
    }

    /**
     * Full re-derivation with the two safety nets that keep it from destroying
     * information it merely fails to recognise.
     */
    private Resolution resolveFully(String name, String stored, boolean missing) {
        ProductBrandResolver.Match auto = brandResolver.resolveMatch(name);

        if (auto == null) {
            if (missing) {
                return blankOut(stored);
            }
            if (brandResolver.mentionsAsOwnBrand(name, stored)) {
                // First safety net: the table recognises nothing, but the stored value
                // is in the name as this product's own brand. It is a maker the table
                // has yet to learn, not an error, so it stays — normalised in spelling.
                String canonical = brandResolver.canonicalise(stored);
                if (same(stored, canonical)) {
                    return null;
                }
                return new Resolution(canonical, "Marcă necunoscută păstrată, scriere uniformizată");
            }
            return new Resolution(null,
                    "Denumirea nu conține marca stocată — valoare greșită, eliminată");
        }

        if (!missing) {
            int storedAt = brandResolver.mentionIndex(name, stored);
            if (storedAt >= 0 && storedAt < auto.index() && !brandResolver.isKnownBrand(stored)) {
                // Second safety net: the stored brand is unknown to the table but the
                // name introduces it before the brand the table found. A product name
                // states its own maker first, so the earlier value is the real one and
                // the recognised one is a compatibility or component mention.
                String canonical = brandResolver.canonicalise(stored);
                if (same(stored, canonical)) {
                    return null;
                }
                return new Resolution(canonical, "Marcă necunoscută păstrată, scriere uniformizată");
            }
        }

        return new Resolution(auto.brand(), "Marcă rederivată complet din denumirea produsului");
    }

    /**
     * Removes a junk sentinel from the brand column, or leaves an already-empty column
     * untouched. Returning {@code null} here means "no change"; returning a
     * {@link Resolution} carrying a {@code null} brand means "erase what is there".
     */
    private Resolution blankOut(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        return new Resolution(null, "Valoare fără sens în coloana marcă, eliminată");
    }

    /** Compares two brand values the way the database will, treating blank as absent. */
    private boolean same(String left, String right) {
        String a = left == null || left.isBlank() ? null : left.trim();
        String b = right == null || right.isBlank() ? null : right.trim();
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.equals(b);
    }

    /** Renders a brand for the report, showing an em dash where there is no value. */
    private String display(String value) {
        return value == null || value.isBlank() ? EMPTY_DISPLAY : value.trim();
    }
}
