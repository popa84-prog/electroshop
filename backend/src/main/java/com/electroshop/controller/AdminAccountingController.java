package com.electroshop.controller;

import com.electroshop.dto.*;
import com.electroshop.service.AccountingService;
import com.electroshop.service.PurchaseService;
import com.electroshop.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Admin endpoints for primary accounting: suppliers, purchases (stock intake),
 * and the sales-vs-purchases report. Secured to ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountingController {

    private final SupplierService supplierService;
    private final PurchaseService purchaseService;
    private final AccountingService accountingService;

    public AdminAccountingController(SupplierService supplierService, PurchaseService purchaseService,
                                     AccountingService accountingService) {
        this.supplierService = supplierService;
        this.purchaseService = purchaseService;
        this.accountingService = accountingService;
    }

    // ---------- Suppliers ----------

    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<PageResponse<SupplierDto>>> listSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<SupplierDto> result = supplierService.list(search,
                PageRequest.of(page, size, Sort.by("name").ascending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<SupplierDto>> getSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getById(id)));
    }

    @PostMapping("/suppliers")
    public ResponseEntity<ApiResponse<SupplierDto>> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Supplier created", supplierService.create(request)));
    }

    @PutMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<SupplierDto>> updateSupplier(@PathVariable Long id,
                                                                   @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Supplier updated", supplierService.update(id, request)));
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteSupplier(@PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Supplier deleted", null));
    }

    // ---------- Purchases (stock intake) ----------

    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseDto>>> listPurchases(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<PurchaseDto> result = purchaseService.list(supplierId,
                PageRequest.of(page, size, Sort.by("purchaseDate").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/purchases/{id}")
    public ResponseEntity<ApiResponse<PurchaseDto>> getPurchase(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.getById(id)));
    }

    @PostMapping("/purchases")
    public ResponseEntity<ApiResponse<PurchaseDto>> createPurchase(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Purchase recorded", purchaseService.create(request)));
    }

    @DeleteMapping("/purchases/{id}")
    public ResponseEntity<ApiResponse<Object>> deletePurchase(@PathVariable Long id) {
        purchaseService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Purchase deleted", null));
    }

    // ---------- Accounting report ----------

    @GetMapping("/accounting/report")
    public ResponseEntity<ApiResponse<AccountingReportDto>> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getReport(from, to)));
    }
}
