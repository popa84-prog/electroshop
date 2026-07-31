package com.electroshop.controller;

import com.electroshop.dto.AdminProductDto;
import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.BulkIdsRequest;
import com.electroshop.dto.CategoryStatDto;
import com.electroshop.dto.CompanyPublicDto;
import com.electroshop.dto.PageResponse;
import com.electroshop.dto.ProductDto;
import com.electroshop.dto.ProductImportResult;
import com.electroshop.dto.ProductRequest;
import com.electroshop.dto.ReorderImagesRequest;
import com.electroshop.dto.SellProductRequest;
import com.electroshop.service.CompanySettingsService;
import com.electroshop.service.FileStorageService;
import com.electroshop.service.OrderService;
import com.electroshop.service.ProductImportService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final ProductImportService productImportService;
    private final CompanySettingsService companySettingsService;
    private final OrderService orderService;

    public ProductController(ProductService productService, FileStorageService fileStorageService,
                             ProductImportService productImportService,
                             CompanySettingsService companySettingsService,
                             OrderService orderService) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
        this.productImportService = productImportService;
        this.companySettingsService = companySettingsService;
        this.orderService = orderService;
    }

    /** Public company contact details for the storefront footer (feature #1). */
    @GetMapping("/company-info")
    public ResponseEntity<ApiResponse<CompanyPublicDto>> companyInfo() {
        return ResponseEntity.ok(ApiResponse.ok(
                CompanyPublicDto.from(companySettingsService.getEntity())));
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

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.has('PRODUCTS_DELETE')")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
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
     */
    @PostMapping("/bulk-delete")
    @PreAuthorize("@permissionService.has('PRODUCTS_DELETE')")
    public ResponseEntity<ApiResponse<ProductService.BulkDeleteResult>> bulkDelete(
            @Valid @RequestBody BulkIdsRequest request) {
        ProductService.BulkDeleteResult result = productService.deleteBulk(request.getIds());
        String message = result.deleted() == 1
                ? "1 produs șters"
                : result.deleted() + " produse șterse";
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
}
