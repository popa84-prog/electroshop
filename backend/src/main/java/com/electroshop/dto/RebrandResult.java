package com.electroshop.dto;

import java.util.List;

/**
 * Report produced by a {@code brand} column backfill run.
 *
 * <p>The same shape is returned for a preview ({@code dryRun = true}) and for an
 * applied run, so the admin UI renders one table in both cases and the only difference
 * the operator sees is the wording of the confirmation.</p>
 *
 * @param dryRun    {@code true} when nothing was written to the database
 * @param mode      the selection mode the run used
 * @param scanned   how many products were examined
 * @param changed   how many products received a different brand value
 * @param filled    how many of those went from no brand at all to a real brand
 * @param corrected how many had a wrong brand replaced by a different real brand
 * @param cleared   how many had a provably wrong brand removed without a replacement,
 *                  because the product name names no manufacturer the table knows
 * @param changes   one entry per changed product, in scan order
 */
public record RebrandResult(
        boolean dryRun,
        String mode,
        int scanned,
        int changed,
        int filled,
        int corrected,
        int cleared,
        List<Change> changes
) {

    /**
     * A single proposed or applied repair.
     *
     * @param id       product id
     * @param name     product name the brand was derived from
     * @param oldBrand value stored before the run, or {@code "—"} when the column was empty
     * @param newBrand value the run assigns, or {@code "—"} when the run removes it
     * @param reason   why this product was selected, in Romanian, for the report
     */
    public record Change(
            Long id,
            String name,
            String oldBrand,
            String newBrand,
            String reason
    ) {}
}
