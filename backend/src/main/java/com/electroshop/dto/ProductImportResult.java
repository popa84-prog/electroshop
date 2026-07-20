package com.electroshop.dto;

import java.util.List;

/**
 * Outcome of an Excel product import. When dryRun is true nothing is written —
 * the report just tells the admin what would happen and what needs fixing.
 */
public record ProductImportResult(
        boolean dryRun,
        int totalRows,
        int validCount,
        int createdCount,
        int updatedCount,
        List<RowError> errors,
        List<String> warnings
) {
    public record RowError(int row, String message) {}
}
