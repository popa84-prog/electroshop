package com.electroshop.controller;

import com.electroshop.dto.AdminProductDto;
import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.BulkIdsRequest;
import com.electroshop.dto.CategoryStatDto;
import com.electroshop.dto.CompanyPublicDto;
import com.electroshop.dto.OfferPublicDto;
import com.electroshop.dto.PageResponse;
import com.electroshop.dto.ProductDto;
import com.electroshop.dto.ProductImportResult;
import com.electroshop.dto.ProductRequest;
import com.electroshop.dto.RebrandResult;
import com.electroshop.dto.RecategorizeResult;
import com.electroshop.dto.ReorderImagesRequest;
import com.electroshop.dto.SellProductRequest;
import com.electroshop.service.CompanySettingsService;
import com.electroshop.service.FileStorageService;
import com.electroshop.service.OfferService;
import com.electroshop.service.OrderService;
import com.electroshop.service.ProductBrandBackfillService;
import com.electroshop.service.ProductImportService;
import com.electroshop.service.ProductRecategorizeService;
import com.electroshop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final ProductImportService productImportService;
    private final ProductRecategorizeService productRecategorizeService;
    private final ProductBrandBackfillService productBrandBackfillService;
    private final CompanySettingsService companySettingsService;
    private final OrderService orderService;
    private final OfferService offerService;

    public ProductController(ProductService productService, FileStorageService fileStorageService,
                             ProductImportService productImportService,
                             ProductRecategorizeService productRecategorizeService,
                             ProductBrandBackfillService productBrandBackfillService,
                             CompanySettingsService companySettingsService,
                             OrderService orderService,
                             OfferService offerService) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
        this.productImportService = productImportService;
        this.productRecategorizeService = productRecategorizeService;
        this.productBrandBackfillService = productBrandBackfillService;
        this.companySettingsService = companySettingsService;
        this.orderService = orderService;
        this.offerService = offerService;
    }

    /** Public company contact details for the storefront footer (feature #1). */
    @GetMapping("/company-info")
    public ResponseEntity<ApiResponse<CompanyPublicDto>> companyInfo() {
        return ResponseEntity.ok(ApiResponse.ok(
                CompanyPublicDto.from(companySettingsService.getEntity())));
    }

    /**
     * Ofertele afișabile chiar acum într-o anumită zonă a magazinului.
     * {@code placement} acceptă {@code HOME_PROMO} (modulul mare de promoție)
     * sau {@code BENEFIT_BAR} (banda de patru cartonașe). Serviciul filtrează
     * după fereastra de timp și aplică limitele de layout, deci interfața poate
     * randa direct ce primește, fără verificări suplimentare.
     */
    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<List<OfferPublicDto>>> offers(
            @RequestParam(defaultValue = "HOME_PROMO") String placement) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.livePublic(placement)));
    }

    // ---- Public ----

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductDto>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Page<ProductDto> result = productService.list(
                search, category, subcategory, brand, minPrice, maxPrice, inStock,
                PageRequest.of(page, size, sort));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getCategories()));
    }

    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<String>>> brands() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getBrands()));
    }

    /**
     * The most populated categories, largest first. The storefront home page
     * renders its category tiles from this, so they always match the real
     * catalogue instead of a hardcoded list that drifts out of date.
     */
    @GetMapping("/top-categories")
    public ResponseEntity<ApiResponse<List<CategoryStatDto>>> topCategories(
            @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getTopCategories(limit)));
    }

    @GetMapping("/category-tree")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> categoryTree() {
        return ResponseEntity.ok(ApiResponse.ok(productService.getCategoryTree()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }

    // ---- Admin (secured in SecurityConfig + @PreAuthorize) ----

    @PostMapping
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> update(@PathVariable Long id,
                                                          @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product updated", productService.update(id, request)));
    }

    /**
     * A product with order or purchase history cannot be hard-deleted without
     * corrupting past invoices and goods-in entries, so {@link ProductService#delete}
     * deactivates it instead in that case — the response message reflects
     * whichever actually happened rather than always claiming a deletion.
     * The {@code deleted} flag in the response body carries the same fact in
     * machine-readable form, so the admin UI can offer the explicit
     * force-delete override (see {@link #forceDelete(Long)}) exactly when a
     * deactivation just happened, without parsing the localized message text.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.has('PRODUCTS_DELETE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable Long id) {
        boolean deleted = productService.delete(id);
        String message = deleted
                ? "Produs șters"
                : "Produsul are comenzi sau achiziții înregistrate — a fost dezactivat în loc să fie șters, "
                        + "pentru a păstra istoricul comenzilor și al contabilității intact.";
        return ResponseEntity.ok(ApiResponse.ok(message, Map.of("deleted", deleted)));
    }

    /**
     * Permanently removes a product from the catalogue while preserving every
     * order/purchase line item that ever referenced it — see
     * {@link ProductService#forceDeleteWithHistory(Long)} for exactly what is
     * kept (everything relevant to accounting) versus what is lost (the
     * product's own catalogue presence and its live link from those lines).
     * Unlike {@link #delete(Long)}, this never falls back to deactivation — it
     * is the explicit "yes, remove this product from the catalogue for good"
     * override, so it is gated behind {@code PRODUCTS_FORCE_DELETE} rather
     * than {@code PRODUCTS_DELETE}: by default only the Admin role holds it
     * (see {@link com.electroshop.security.RolePermissions}), because a
     * Manager who can normally delete products should not, by default, also
     * be able to permanently erase one from the catalogue.
     */
    @DeleteMapping("/{id}/force")
    @PreAuthorize("@permissionService.has('PRODUCTS_FORCE_DELETE')")
    public ResponseEntity<ApiResponse<Object>> forceDelete(@PathVariable Long id) {
        ProductService.ForceDeleteOutcome outcome = productService.forceDeleteWithHistory(id);
        String message = "Produs șters definitiv din catalog"
                + (outcome.orderItemsPreserved() + outcome.purchaseItemsPreserved() > 0
                        ? ". " + outcome.orderItemsPreserved() + " linie(i) de comandă și "
                                + outcome.purchaseItemsPreserved() + " linie(i) de achiziție au fost păstrate "
                                + "neschimbate, pentru contabilitate și istoricul profitului."
                        : ".");
        return ResponseEntity.ok(ApiResponse.ok(message, null));
    }

    /**
     * Batch counterpart of {@link #forceDelete(Long)} — used after a
     * {@link #bulkDelete} response reports products that were deactivated
     * because of sales history, when the operator explicitly chooses to
     * remove them anyway, permanently, from the catalogue. Their historical
     * order/purchase lines are preserved, not removed — see
     * {@link ProductService#forceDeleteWithHistory(Long)}.
     */
    @PostMapping("/bulk-force-delete")
    @PreAuthorize("@permissionService.has('PRODUCTS_FORCE_DELETE')")
    public ResponseEntity<ApiResponse<ProductService.BulkForceDeleteResult>> bulkForceDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        ProductService.BulkForceDeleteResult result = productService.forceDeleteBulk(request.getIds());
        String message = result.deleted()
                + (result.deleted() == 1 ? " produs șters definitiv din catalog" : " produse șterse definitiv din catalog")
                + (result.orderItemsPreserved() + result.purchaseItemsPreserved() > 0
                        ? ". " + result.orderItemsPreserved() + " linii de comandă și "
                                + result.purchaseItemsPreserved() + " linii de achiziție au fost păstrate "
                                + "neschimbate, pentru contabilitate."
                        : ".");
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    /** Hides the product from the public storefront without deleting it (feature #5). */
    @PostMapping("/{id}/activate")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<AdminProductDto>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Produs activat", productService.setActive(id, true)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<AdminProductDto>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Produs dezactivat", productService.setActive(id, false)));
    }

    /**
     * Feature #10 — "VÂNDUT": registers a quick in-store sale for this product.
     * Decrements stock, creates a completed order (feeds the dashboard's revenue/
     * order stats automatically) and returns the fresh {@link AdminProductDto} so
     * the products table updates instantly without a full page reload.
     */
    @PostMapping("/{id}/sell")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<AdminProductDto>> sell(@PathVariable Long id,
                                                             @Valid @RequestBody SellProductRequest request) {
        orderService.sellProduct(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Vânzare înregistrată cu succes!", productService.adminGet(id)));
    }

    /**
     * Removes several products in one call.
     * <p>
     * Modelled as POST rather than DELETE deliberately: the operation is not
     * idempotent from the caller's point of view (the response reports how many
     * rows were actually removed) and request bodies on DELETE are not handled
     * consistently by proxies and HTTP clients.
     * <p>
     * Products with order or purchase history are deactivated rather than
     * removed (see {@link ProductService#deleteBulk}) — the summary message
     * names both outcomes so a batch that mixes fresh, never-sold products
     * with previously-sold ones reads as the partial success it is, not as a
     * plain deletion count that quietly excludes what really happened.
     */
    @PostMapping("/bulk-delete")
    @PreAuthorize("@permissionService.has('PRODUCTS_DELETE')")
    public ResponseEntity<ApiResponse<ProductService.BulkDeleteResult>> bulkDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        ProductService.BulkDeleteResult result = productService.deleteBulk(request.getIds());
        List<String> parts = new ArrayList<>();
        if (result.deleted() > 0) {
            parts.add(result.deleted() == 1 ? "1 produs șters" : result.deleted() + " produse șterse");
        }
        if (!result.deactivated().isEmpty()) {
            parts.add(result.deactivated().size() == 1
                    ? "1 produs dezactivat (are comenzi/achiziții înregistrate)"
                    : result.deactivated().size() + " produse dezactivate (au comenzi/achiziții înregistrate)");
        }
        String message = parts.isEmpty() ? "Niciun produs nu a fost modificat" : String.join(", ", parts);
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    /**
     * Activates several products in one call — the batch-selection toolbar's
     * "Activează selectate" action. An id that no longer exists is reported as
     * skipped rather than failing the whole batch, exactly like bulk-delete.
     */
    @PostMapping("/bulk-activate")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductService.BulkActivateResult>> bulkActivate(
            @Valid @RequestBody BulkIdsRequest request) {
        ProductService.BulkActivateResult result = productService.setActiveBulk(request.getIds(), true);
        String message = result.updated() == 1
                ? "1 produs activat"
                : result.updated() + " produse activate";
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    /** Deactivates several products in one call — the batch-selection toolbar's counterpart action. */
    @PostMapping("/bulk-deactivate")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductService.BulkActivateResult>> bulkDeactivate(
            @Valid @RequestBody BulkIdsRequest request) {
        ProductService.BulkActivateResult result = productService.setActiveBulk(request.getIds(), false);
        String message = result.updated() == 1
                ? "1 produs dezactivat"
                : result.updated() + " produse dezactivate";
        return ResponseEntity.ok(ApiResponse.ok(message, result));
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> uploadImage(@PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        String url = fileStorageService.store(file);
        return ResponseEntity.ok(ApiResponse.ok("Image uploaded", productService.updateImage(id, url)));
    }

    // ---- Cloudinary image gallery (feature #5) ----

    /** Upload one or more product images (JPG/PNG/WebP) to Cloudinary. */
    @PostMapping("/{id}/images")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> uploadImages(@PathVariable Long id,
                                                               @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.ok(ApiResponse.ok("Imagini încărcate", productService.addImages(id, files)));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> deleteImage(@PathVariable Long id,
                                                              @PathVariable Long imageId) {
        return ResponseEntity.ok(ApiResponse.ok("Imagine ștearsă", productService.deleteImage(id, imageId)));
    }

    @PutMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> setPrimaryImage(@PathVariable Long id,
                                                                  @PathVariable Long imageId) {
        return ResponseEntity.ok(ApiResponse.ok("Imagine principală setată",
                productService.setPrimaryImage(id, imageId)));
    }

    /** Reorders the image gallery (drag & drop) — body carries the full new order. */
    @PutMapping("/{id}/images/reorder")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductDto>> reorderImages(@PathVariable Long id,
                                                                 @Valid @RequestBody ReorderImagesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ordinea imaginilor a fost salvată",
                productService.reorderImages(id, request.imageIds())));
    }

    /**
     * Import products from an .xlsx file. With dryRun=true (default) nothing is
     * written — it returns a validation report. With dryRun=false the valid rows
     * are created/updated.
     */
    @PostMapping("/import")
    @PreAuthorize("@permissionService.has('PRODUCTS_IMPORT')")
    public ResponseEntity<ApiResponse<ProductImportResult>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
            @RequestParam(name = "restock", defaultValue = "false") boolean restock) {
        ProductImportResult result = productImportService.importFromExcel(file, dryRun, restock);
        String msg = dryRun
                ? (restock ? "Previzualizare intrare marfă" : "Previzualizare import")
                : (restock ? "Intrare marfă finalizată" : "Import finalizat");
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }

    /**
     * Surgical purchase-price sync: updates ONLY purchase_price on existing
     * products (matched by name). Does not create, delete, or change any other
     * field. Use dryRun=true first for a preview. Admin only.
     */
    @PostMapping("/sync-purchase-prices")
    @PreAuthorize("@permissionService.has('PRODUCTS_IMPORT')")
    public ResponseEntity<ApiResponse<ProductImportResult>> syncPurchasePrices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        ProductImportResult result = productImportService.syncPurchasePrices(file, dryRun);
        String msg = dryRun ? "Previzualizare sincronizare prețuri achiziție"
                            : "Prețuri de achiziție actualizate";
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }

    /**
     * Repairs the category / subcategory columns of products that are already in the
     * database, using the same rule table the import uses.
     *
     * <p>{@code mode} selects how aggressive the run is:</p>
     * <ul>
     *   <li>{@code PLACEHOLDER} — only products whose stored category or subcategory
     *       is unusable (empty, {@code "0"}, {@code "-"}, {@code "N/A"}, a bare
     *       number, or a condition word such as {@code "Folosit"}).</li>
     *   <li>{@code INCONSISTENT} (default) — the above, plus pairs where the
     *       subcategory sits under the wrong parent category or either value is
     *       spelled in a non-canonical form.</li>
     *   <li>{@code ALL} — re-derives both columns from the product name for every
     *       product, overwriting manual classifications.</li>
     * </ul>
     *
     * <p>With {@code dryRun=true} (the default) nothing is written and the response
     * is the exact change list an applied run would produce.</p>
     */
    @PostMapping("/recategorize")
    @PreAuthorize("@permissionService.has('PRODUCTS_IMPORT')")
    public ResponseEntity<ApiResponse<RecategorizeResult>> recategorize(
            @RequestParam(name = "mode", defaultValue = "INCONSISTENT") String mode,
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        ProductRecategorizeService.Mode selected = ProductRecategorizeService.Mode.parse(mode);
        RecategorizeResult result = productRecategorizeService.run(selected, dryRun);
        String msg = dryRun
                ? "Previzualizare recategorizare: " + result.changed() + " produse de corectat"
                : "Recategorizare finalizată: " + result.changed() + " produse actualizate";
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }

    /**
     * Repairs the {@code brand} column of products that are already in the database,
     * using the same whole-word resolver the import now uses.
     *
     * <p>{@code mode} selects how aggressive the run is:</p>
     * <ul>
     *   <li>{@code MISSING} — only products with no usable brand at all: an empty
     *       column, or a junk sentinel such as {@code "0"}, {@code "-"} or
     *       {@code "N/A"} typed in place of a blank cell. A product that already
     *       carries a real-looking brand is never touched, even a wrong one.</li>
     *   <li>{@code WRONG} (default) — the above, plus every value that is provably
     *       wrong: a brand that does not appear in the product's own name as a whole
     *       word (the {@code "Ring"} extracted from {@code "Behringer"}, the
     *       {@code "HP"} extracted from the model number {@code HP2564}), and a brand
     *       that appears only inside a compatibility list ({@code "pentru Apple
     *       Watch"}). Casing is normalised in this mode too, so {@code LOGITECH} and
     *       {@code Logitech} stop being two entries in the storefront filter.</li>
     *   <li>{@code ALL} — re-derives the brand from the product name for every
     *       product. This is the mode to run after the brand table itself has been
     *       extended; it keeps a stored brand the table does not recognise when the
     *       name introduces that brand before the one the table found.</li>
     * </ul>
     *
     * <p>With {@code dryRun=true} (the default) nothing is written and the response is
     * the exact change list an applied run would produce.</p>
     */
    @PostMapping("/rebrand")
    @PreAuthorize("@permissionService.has('PRODUCTS_IMPORT')")
    public ResponseEntity<ApiResponse<RebrandResult>> rebrand(
            @RequestParam(name = "mode", defaultValue = "WRONG") String mode,
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        ProductBrandBackfillService.Mode selected = ProductBrandBackfillService.Mode.parse(mode);
        RebrandResult result = productBrandBackfillService.run(selected, dryRun);
        String msg = dryRun
                ? "Previzualizare mărci: " + result.changed() + " produse de corectat"
                : "Mărci actualizate: " + result.changed() + " produse modificate";
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }
}
