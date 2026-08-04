package com.electroshop.service;

import com.electroshop.dto.RecategorizeResult;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Repairs the {@code category} / {@code subcategory} columns of products that are
 * already in the database.
 *
 * <p>{@link ProductImportService} fills both columns from the product name whenever
 * the spreadsheet does not carry a usable value, but that guard only runs at import
 * time. Rows that were created before the guard existed — or that were imported
 * while the guard still accepted junk sentinels such as the literal string
 * {@code "0"} or the product condition {@code "Folosit"} as a genuine category —
 * keep whatever was stored back then. This service is the backfill that brings the
 * existing catalogue in line with the current rule table in
 * {@link ProductCategorizer}.</p>
 *
 * <p>Every run is available as a preview first ({@code dryRun = true}), which reads
 * the catalogue, computes the full change list and writes nothing. The operator
 * reviews that list in the admin UI and only then repeats the call with
 * {@code dryRun = false} to persist exactly the same decisions — the classification
 * is deterministic, so the applied run cannot diverge from the preview.</p>
 */
@Service
public class ProductRecategorizeService {

    /**
     * How many individual changes travel back to the browser. The counters in
     * {@link RecategorizeResult} always cover the whole run; only the per-product
     * detail list is capped, so a catalogue of any size produces a response the
     * admin UI can render without stalling. The UI detects truncation by comparing
     * {@code changed} against {@code changes.size()}.
     */
    private static final int MAX_REPORTED_CHANGES = 1000;

    /**
     * Which products a run touches.
     */
    public enum Mode {

        /**
         * Only products whose stored category or subcategory does not actually
         * identify the product: empty, {@code "0"}, {@code "-"}, {@code "N/A"}, a
         * bare number, a condition word such as {@code "Folosit"}, or the generic
         * fallback pair {@code "Diverse electronice" / "Gadgeturi"} that earlier
         * runs wrote when they could not tell what the product was. A product that
         * already carries a meaningful pair is never touched, even when the
         * classifier would have chosen differently.
         */
        PLACEHOLDER,

        /**
         * Everything {@link #PLACEHOLDER} covers, plus products whose two columns
         * contradict each other or the taxonomy: a known subcategory filed under a
         * category that does not own it, or either value spelled differently from
         * the canonical form declared by the rule table. Product names are not
         * re-read for these — only the stored pair is repaired — so a deliberate
         * manual classification survives as long as it is internally consistent.
         */
        INCONSISTENT,

        /**
         * Re-derives both columns from the product name for every product. This is
         * the mode to use after the rule table itself has been corrected, and the
         * only mode that can overwrite a classification the rules disagree with even
         * when the stored pair is internally coherent.
         *
         * <p>It is also the only mode that repairs the damage the previous
         * substring-matching classifier left behind, because that classifier always
         * wrote a <em>matching</em> pair: a phone whose name contains "AMOLED" was
         * stored as {@code Televizoare / Televizoare}, which no consistency check can
         * flag — the subcategory really does belong to that category. Only the
         * product name reveals the error.</p>
         *
         * <p>The one thing it will not do is trade information for ignorance: when the
         * rules recognise nothing in a product's name, a meaningful stored value is
         * kept rather than overwritten with the miscellaneous fallback.</p>
         */
        ALL;

        /** Parses the query-string value; unknown or missing input selects {@link #INCONSISTENT}. */
        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return INCONSISTENT;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            return INCONSISTENT;
        }
    }

    private final ProductRepository productRepository;
    private final ProductCategorizer categorizer;
    private final AuditService auditService;

    public ProductRecategorizeService(ProductRepository productRepository,
                                      ProductCategorizer categorizer,
                                      AuditService auditService) {
        this.productRepository = productRepository;
        this.categorizer = categorizer;
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
    public RecategorizeResult run(Mode mode, boolean dryRun) {
        List<Product> products = productRepository.findAll();
        List<RecategorizeResult.Change> changes = new ArrayList<>();
        List<Product> modified = new ArrayList<>();

        int changed = 0;
        int unresolved = 0;

        for (Product product : products) {
            String name = product.getName();
            if (name == null || name.isBlank()) {
                // Nothing to classify from and nothing safe to guess — a nameless
                // row is left exactly as it is and reported by omission.
                continue;
            }

            String oldCategory = product.getCategory();
            String oldSubcategory = product.getSubcategory();

            Resolution resolution = resolve(mode, name, oldCategory, oldSubcategory);
            if (resolution == null) {
                continue;
            }
            if (resolution.category().equals(oldCategory)
                    && resolution.subcategory().equals(oldSubcategory)) {
                // The rules agree with what is already stored: not a change.
                continue;
            }

            changed++;
            if (ProductCategorizer.DEFAULT_CATEGORY.equals(resolution.category())
                    && ProductCategorizer.DEFAULT_SUBCATEGORY.equals(resolution.subcategory())) {
                unresolved++;
            }
            if (changes.size() < MAX_REPORTED_CHANGES) {
                changes.add(new RecategorizeResult.Change(
                        product.getId(),
                        name,
                        display(oldCategory),
                        display(oldSubcategory),
                        resolution.category(),
                        resolution.subcategory(),
                        resolution.reason()));
            }

            if (!dryRun) {
                product.setCategory(resolution.category());
                product.setSubcategory(resolution.subcategory());
                modified.add(product);
            }
        }

        if (!dryRun && !modified.isEmpty()) {
            productRepository.saveAll(modified);
            auditService.log("PRODUCT_RECATEGORIZE", "Product", null,
                    "Mod: " + mode.name() + " — " + modified.size()
                            + " produse reclasificate din " + products.size() + " scanate ("
                            + unresolved + " neidentificate).");
        }

        return new RecategorizeResult(dryRun, mode.name(), products.size(), changed, unresolved, changes);
    }

    /** The pair a product should end up with, plus the Romanian explanation shown in the report. */
    private record Resolution(String category, String subcategory, String reason) {}

    /**
     * Decides what this product's pair should become, or {@code null} when the
     * selected mode does not touch it.
     */
    private Resolution resolve(Mode mode, String name, String category, String subcategory) {
        boolean categoryMissing = unusable(category, ProductCategorizer.DEFAULT_CATEGORY);
        boolean subcategoryMissing = unusable(subcategory, ProductCategorizer.DEFAULT_SUBCATEGORY);

        if (mode == Mode.ALL) {
            ProductCategorizer.Categorization auto = categorizer.categorize(name);

            if (!ProductCategorizer.DEFAULT_SUBCATEGORY.equals(auto.subcategory())) {
                // The rules recognise the product: in this mode they win outright.
                return new Resolution(auto.category(), auto.subcategory(),
                        "Reclasificare completă din denumirea produsului");
            }

            // The rules recognise nothing in this name. Replacing a meaningful stored
            // value with the miscellaneous fallback would destroy information and add
            // none, so whatever is still meaningful stays. This is what keeps a
            // deliberate label such as "Vitrina magazin" alive through a full
            // re-derivation, without granting stored values any authority on the
            // products the rules can actually identify.
            String keptSubcategory = subcategoryMissing ? auto.subcategory() : subcategory.trim();
            String keptCategory = categoryMissing
                    ? firstNonNull(parentOf(keptSubcategory), auto.category())
                    : category.trim();
            return new Resolution(keptCategory, keptSubcategory,
                    "Denumirea nu identifică produsul — valorile utilizabile au fost păstrate");
        }

        if (categoryMissing || subcategoryMissing) {
            return repairMissing(name, category, subcategory, categoryMissing, subcategoryMissing);
        }

        if (mode == Mode.INCONSISTENT) {
            return repairInconsistent(category, subcategory);
        }

        // PLACEHOLDER mode and both values are usable — leave the product alone.
        return null;
    }

    /**
     * Whether a stored value carries no information about what the product is.
     *
     * <p>Two different kinds of value qualify. The first is a junk sentinel that a
     * supplier sheet used in place of an empty cell — {@code "0"}, {@code "-"},
     * {@code "N/A"}, a bare number, a condition word — which
     * {@link ProductCategorizer#isPlaceholder(String)} recognises. The second is the
     * generic fallback the classifier itself writes when it cannot identify a
     * product. That fallback is a statement of ignorance, not a classification, so
     * trusting it would permanently freeze a product in the miscellaneous bucket:
     * the catalogue is full of rows reading {@code "0" / "Gadgeturi"}, where the
     * subcategory was auto-filled with the fallback while the category stayed junk.
     * Reading it as evidence would keep such a product under
     * {@code "Diverse electronice"} forever, which is exactly the defect this
     * backfill exists to remove. Re-deriving is safe: when the name still yields
     * nothing, the classifier returns the same generic pair and no change is
     * recorded.</p>
     *
     * @param value    the stored value
     * @param fallback the generic value for this column
     */
    private boolean unusable(String value, String fallback) {
        if (ProductCategorizer.isPlaceholder(value)) {
            return true;
        }
        return fallback.equalsIgnoreCase(value.trim());
    }

    /**
     * Fills in whichever of the two columns carries no information, keeping the
     * other one when it can be trusted.
     */
    private Resolution repairMissing(String name, String category, String subcategory,
                                     boolean categoryMissing, boolean subcategoryMissing) {
        ProductCategorizer.Categorization auto = categorizer.categorize(name);

        if (categoryMissing && subcategoryMissing) {
            return new Resolution(auto.category(), auto.subcategory(),
                    "Nici categoria, nici subcategoria nu identificau produsul („" + display(category)
                            + "” / „" + display(subcategory) + "”) — ambele recalculate din denumire");
        }

        if (subcategoryMissing) {
            // The stored category survives only when it is the taxonomy parent of the
            // subcategory the name points to. When it is not, the two would contradict
            // each other the moment the subcategory is written, so the name wins for
            // both columns.
            String storedCategory = categorizer.canonicalCategory(category);
            String owner = categorizer.canonicalCategoryFor(auto.subcategory());
            if (storedCategory != null && storedCategory.equals(owner)) {
                return new Resolution(storedCategory, auto.subcategory(),
                        "Subcategoria „" + display(subcategory)
                                + "” nu identifica produsul — recalculată din denumire");
            }
            return new Resolution(auto.category(), auto.subcategory(),
                    "Subcategoria „" + display(subcategory)
                            + "” nu identifica produsul, iar categoria „" + display(category)
                            + "” nu corespunde denumirii — ambele recalculate");
        }

        // Only the category is unusable.
        String canonicalSubcategory = categorizer.canonicalSubcategory(subcategory);

        if (canonicalSubcategory == null) {
            // The subcategory is not in the rule table, so no classifier produced it:
            // a human typed it. It is kept verbatim and only the category is filled in.
            return new Resolution(auto.category(), subcategory.trim(),
                    "Categoria „" + display(category)
                            + "” nu identifica produsul — recalculată din denumire, subcategoria "
                            + "personalizată „" + subcategory.trim() + "” a fost păstrată");
        }

        // The subcategory IS in the rule table, which means it was almost certainly
        // written by an earlier import rather than chosen by hand — a row that a
        // human had curated would not have been left with "0" in the category column
        // next to it. Trusting it is what kept a Motorola phone under Auto & Moto and
        // a mirrorless body under Obiective: the broken classifier wrote the
        // subcategory, the import wrote junk into the category, and reading the
        // subcategory back as evidence would make that error permanent. The name is
        // the only independent source, so it decides.
        if (!ProductCategorizer.DEFAULT_SUBCATEGORY.equals(auto.subcategory())
                && !auto.subcategory().equals(canonicalSubcategory)) {
            return new Resolution(auto.category(), auto.subcategory(),
                    "Categoria „" + display(category) + "” nu identifica produsul, iar subcategoria „"
                            + canonicalSubcategory + "” contrazice denumirea — ambele recalculate");
        }

        // Either the name agrees with the stored subcategory, or the name identifies
        // nothing at all. In both cases the stored subcategory is the best available
        // information and its parent follows from the taxonomy.
        String owner = categorizer.canonicalCategoryFor(canonicalSubcategory);
        return new Resolution(owner, canonicalSubcategory,
                "Categoria „" + display(category)
                        + "” nu identifica produsul — dedusă din subcategoria „"
                        + canonicalSubcategory + "”");
    }

    /** Returns the first non-{@code null} of the two values. */
    private String firstNonNull(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    /**
     * The taxonomy parent of a subcategory, or {@code null} when the subcategory is
     * not part of the rule table and therefore has no declared parent.
     */
    private String parentOf(String subcategory) {
        String canonical = categorizer.canonicalSubcategory(subcategory);
        return canonical == null ? null : categorizer.canonicalCategoryFor(canonical);
    }

    /**
     * Repairs a pair where both values are present but disagree with the taxonomy —
     * wrong parent category, or a non-canonical spelling that would split the
     * storefront facets into near-duplicate entries.
     */
    private Resolution repairInconsistent(String category, String subcategory) {
        String canonicalSubcategory = categorizer.canonicalSubcategory(subcategory);

        if (canonicalSubcategory == null) {
            // Custom subcategory typed by the shop owner: kept verbatim. Only the
            // parent category is snapped, and only when it is itself a known one.
            String canonicalCategory = categorizer.canonicalCategory(category);
            if (canonicalCategory == null || canonicalCategory.equals(category)) {
                return null;
            }
            return new Resolution(canonicalCategory, subcategory,
                    "Denumirea categoriei „" + category + "” nu este forma canonică");
        }

        String owner = categorizer.canonicalCategoryFor(canonicalSubcategory);
        if (owner.equals(category) && canonicalSubcategory.equals(subcategory)) {
            return null;
        }
        if (owner.equals(category)) {
            return new Resolution(owner, canonicalSubcategory,
                    "Denumirea subcategoriei „" + subcategory + "” nu este forma canonică");
        }
        return new Resolution(owner, canonicalSubcategory,
                "Categoria „" + display(category) + "” nu este părintele subcategoriei „"
                        + canonicalSubcategory + "”");
    }

    /** Renders a stored value for the report, turning {@code null} into a visible marker. */
    private String display(String value) {
        if (value == null) {
            return "(gol)";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "(gol)" : trimmed;
    }
}
