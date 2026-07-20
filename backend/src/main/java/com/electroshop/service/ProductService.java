package com.electroshop.service;

import com.electroshop.dto.ProductDto;
import com.electroshop.dto.ProductRequest;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
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
        return ProductDto.from(findEntity(id));
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
        productRepository.delete(p);
        auditService.log("PRODUCT_DELETED", "Product", id, name);
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
