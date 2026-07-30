package com.electroshop.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for batch operations that address several rows by primary key.
 * <p>
 * The upper bound is a deliberate safety measure: a single accidental call can
 * never wipe an unbounded portion of the catalogue, and each request stays
 * small enough to run inside one transaction without exhausting memory.
 */
public class BulkIdsRequest {

    /** Maximum number of identifiers accepted in a single batch request. */
    public static final int MAX_IDS = 500;

    @NotEmpty(message = "Lista de identificatori nu poate fi goală.")
    @Size(max = MAX_IDS, message = "Poți trimite cel mult " + MAX_IDS + " identificatori într-o singură cerere.")
    private List<Long> ids;

    public BulkIdsRequest() {
    }

    public BulkIdsRequest(List<Long> ids) {
        this.ids = ids;
    }

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
