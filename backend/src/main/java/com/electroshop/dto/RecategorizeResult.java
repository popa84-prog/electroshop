package com.electroshop.dto;

import java.util.List;

/**
 * Report produced by a category / subcategory backfill run.
 *
 * <p>The same shape is returned for a preview ({@code dryRun = true}) and for an
 * applied run, so the admin UI renders one table in both cases and the only
 * difference the operator sees is the wording of the confirmation.</p>
 *
 * @param dryRun        {@code true} when nothing was written to the database
 * @param mode          the selection mode the run used
 * @param scanned       how many products were examined
 * @param changed       how many products received a new category / subcategory pair
 * @param unresolved    how many products the classifier could not identify and that
 *                      therefore landed on the generic fallback pair
 * @param changes       one entry per changed product, in scan order
 */
public record RecategorizeResult(
        boolean dryRun,
        String mode,
        int scanned,
        int changed,
        int unresolved,
        List<Change> changes
) {

    /**
     * A single proposed or applied repair.
     *
     * @param id             product id
     * @param name           product name the classification was derived from
     * @param oldCategory    category stored before the run
     * @param oldSubcategory subcategory stored before the run
     * @param newCategory    category the run assigns
     * @param newSubcategory subcategory the run assigns
     * @param reason         why this product was selected, in Romanian, for the report
     */
    public record Change(
            Long id,
            String name,
            String oldCategory,
            String oldSubcategory,
            String newCategory,
            String newSubcategory,
            String reason
    ) {}
}
