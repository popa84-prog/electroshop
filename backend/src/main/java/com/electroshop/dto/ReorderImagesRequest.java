package com.electroshop.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for reordering a product's image gallery (drag & drop in the
 * admin UI). Must contain the full, ordered list of image ids belonging to
 * the product — {@link com.electroshop.service.ProductService#reorderImages}
 * rejects a partial or mismatched list.
 */
public record ReorderImagesRequest(
        @NotEmpty(message = "Lista de imagini nu poate fi goală.") List<Long> imageIds
) {
}
