package com.electroshop.service;

import com.electroshop.dto.ProductDto;
import com.electroshop.dto.ProductRequest;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Product;
import com.electroshop.model.ProductImage;
import com.electroshop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class ProductService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final CloudinaryService cloudinaryService;

    public ProductService(ProductRepository productRepository, AuditService auditService,
                          CloudinaryService cloudinaryService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.cloudinaryService = cloudinaryService;
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> list(String search, String category, String subcategory, String brand,
                                 BigDecimal minPrice, BigDecimal maxPrice, boolean inStock,
                                 Pageable pageable) {
        return productRepository.search(
                blankToNull(search), blankToNull(category), blankToNull(subcategory),
                blankToNull(brand), minPrice, maxPrice, inStock, pageable
        ).map(ProductDto::from);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        return ProductDto.detail(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return productRepository.findAllCategories();
    }

    @Transactional(readOnly = true)
    public List<String> getBrands() {
        return productRepository.findAllBrands();
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getCategoryTree() {
        Map<String, List<String>> tree = new LinkedHashMap<>();
        for (Object[] pair : productRepository.findCategorySubcategoryPairs()) {
            String cat = (String) pair[0];
            String sub = (String) pair[1];
            List<String> subs = tree.computeIfAbsent(cat, k -> new ArrayList<>());
            if (sub != null && !sub.isBlank() && !subs.contains(sub)) {
                subs.add(sub);
            }
        }
        return tree;
    }

    public ProductDto create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_CREATED", "Product", saved.getId(),
                saved.getName() + " · stoc " + saved.getStockQuantity());
        return ProductDto.from(saved);
    }

    public ProductDto update(Long id, ProductRequest req) {
        Product p = findEntity(id);
        Integer oldStock = p.getStockQuantity();
        apply(p, req);
        Product saved = productRepository.save(p);
        String details = saved.getName();
        if (oldStock != null && !oldStock.equals(saved.getStockQuantity())) {
            details += " · stoc " + oldStock + " → " + saved.getStockQuantity();
        }
        auditService.log("PRODUCT_UPDATED", "Product", saved.getId(), details);
        return ProductDto.from(saved);
    }

    public ProductDto updateImage(Long id, String imageUrl) {
        Product p = findEntity(id);
        p.setImageUrl(imageUrl);
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_UPDATED", "Product", saved.getId(), saved.getName());
        return ProductDto.from(saved);
    }

    public void delete(Long id) {
        Product p = findEntity(id);
        String name = p.getName();
        // Remove hosted assets first, then the row (cascade drops the image rows).
        for (ProductImage img : p.getImages()) {
            cloudinaryService.delete(img.getPublicId());
        }
        productRepository.delete(p);
        auditService.log("PRODUCT_DELETED", "Product", id, name);
    }

    // ==============================================================
    //  Product image gallery (Cloudinary-hosted) — feature #5
    // ==============================================================

    /** Uploads one or more images to Cloudinary and attaches them to the product. */
    public ProductDto addImages(Long id, MultipartFile[] files) {
        Product p = findEntity(id);
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Nu ai selectat nicio imagine.");
        }
        int nextPos = p.getImages().stream().mapToInt(ProductImage::getPosition).max().orElse(-1) + 1;
        int added = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateImage(file);
            CloudinaryService.UploadResult res =
                    cloudinaryService.upload(file, "electroshop/products/" + id);
            ProductImage img = new ProductImage(p, res.url(), res.publicId(), nextPos++);
            // First image on a product with no cover becomes the primary/cover.
            if (p.getImages().isEmpty() && !hasPrimary(p)) {
                img.setPrimary(true);
                p.setImageUrl(res.url());
            }
            p.getImages().add(img);
            added++;
        }
        if (added == 0) {
            throw new IllegalArgumentException("Fișierele trimise sunt goale.");
        }
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_ADDED", "Product", saved.getId(),
                saved.getName() + " · " + added + " imagine(i)");
        return ProductDto.detail(saved);
    }

    /** Deletes one image (from Cloudinary + DB), promoting a new cover if needed. */
    public ProductDto deleteImage(Long id, Long imageId) {
        Product p = findEntity(id);
        ProductImage target = p.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
        boolean wasPrimary = target.isPrimary();
        cloudinaryService.delete(target.getPublicId());
        p.getImages().remove(target); // orphanRemoval deletes the row
        if (wasPrimary) {
            ProductImage next = p.getImages().stream().findFirst().orElse(null);
            if (next != null) {
                next.setPrimary(true);
                p.setImageUrl(next.getUrl());
            } else {
                p.setImageUrl(null);
            }
        }
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_DELETED", "Product", saved.getId(), saved.getName());
        return ProductDto.detail(saved);
    }

    /** Marks one image as the primary/cover. */
    public ProductDto setPrimaryImage(Long id, Long imageId) {
        Product p = findEntity(id);
        ProductImage target = p.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
        for (ProductImage img : p.getImages()) {
            img.setPrimary(img == target);
        }
        p.setImageUrl(target.getUrl());
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_PRIMARY", "Product", saved.getId(), saved.getName());
        return ProductDto.detail(saved);
    }

    private boolean hasPrimary(Product p) {
        return p.getImages().stream().anyMatch(ProductImage::isPrimary);
    }

    private void validateImage(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || !ALLOWED_IMAGE_TYPES.contains(type.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Format neacceptat: " + type + ". Sunt permise doar JPG, PNG și WebP.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Imaginea depășește limita de 5 MB.");
        }
    }

    public Product findEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setStockQuantity(req.stockQuantity());
        p.setCategory(req.category());
        p.setSubcategory(req.subcategory());
        p.setBrand(req.brand());
        p.setPurchasePrice(req.purchasePrice());
        p.setSku(req.sku());
        if (req.imageUrl() != null) {
            p.setImageUrl(req.imageUrl());
        }
    }
}
