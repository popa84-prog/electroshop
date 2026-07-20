package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.PageResponse;
import com.electroshop.dto.ProductDto;
import com.electroshop.dto.ProductImportResult;
import com.electroshop.dto.ProductRequest;
import com.electroshop.service.FileStorageService;
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

    public ProductController(ProductService productService, FileStorageService fileStorageService,
                             ProductImportService productImportService) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
        this.productImportService = productImportService;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Product created", productService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> update(@PathVariable Long id,
                                                          @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Product updated", productService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted", null));
    }

    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductDto>> uploadImage(@PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        String url = fileStorageService.store(file);
        return ResponseEntity.ok(ApiResponse.ok("Image uploaded", productService.updateImage(id, url)));
    }

    /**
     * Import products from an .xlsx file. With dryRun=true (default) nothing is
     * written — it returns a validation report. With dryRun=false the valid rows
     * are created/updated.
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductImportResult>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        ProductImportResult result = productImportService.importFromExcel(file, dryRun);
        String msg = dryRun ? "Previzualizare import" : "Import finalizat";
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }
}
