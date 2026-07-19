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

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> list(String search, String category, Pageable pageable) {
        String s = (search == null || search.isBlank()) ? null : search;
        String c = (category == null || category.isBlank()) ? null : category;
        return productRepository.search(s, c, pageable).map(ProductDto::from);
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        return ProductDto.from(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return productRepository.findAllCategories();
    }

    public ProductDto create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return ProductDto.from(productRepository.save(p));
    }

    public ProductDto update(Long id, ProductRequest req) {
        Product p = findEntity(id);
        apply(p, req);
        return ProductDto.from(productRepository.save(p));
    }

    public ProductDto updateImage(Long id, String imageUrl) {
        Product p = findEntity(id);
        p.setImageUrl(imageUrl);
        return ProductDto.from(productRepository.save(p));
    }

    public void delete(Long id) {
        Product p = findEntity(id);
        productRepository.delete(p);
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
        p.setBrand(req.brand());
        if (req.imageUrl() != null) {
            p.setImageUrl(req.imageUrl());
        }
    }
}
