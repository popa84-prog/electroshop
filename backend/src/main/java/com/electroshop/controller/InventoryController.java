package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.InventoryHealthDto;
import com.electroshop.service.InventoryHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory health.
 *
 * <p>Task 13. Full path is {@code /api/inventory/health}.</p>
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryHealthService inventoryHealthService;

    public InventoryController(InventoryHealthService inventoryHealthService) {
        this.inventoryHealthService = inventoryHealthService;
    }

    /**
     * Critical stock, overstock, out-of-stock and restock recommendations.
     *
     * <p>{@code GET /api/inventory/health}</p>
     *
     * <p>No range parameter. Inventory health is a statement about right now — what is
     * about to run out, what is sitting unsold — and a historical window would answer a
     * question nobody asked. The velocity that drives the recommendations is averaged
     * over a fixed recent window whose length is returned in {@code thresholds}, so the
     * figure is reproducible without being configurable into meaninglessness.</p>
     */
    @GetMapping("/health")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<InventoryHealthDto>> health() {
        return ResponseEntity.ok(ApiResponse.ok(inventoryHealthService.health()));
    }
}
