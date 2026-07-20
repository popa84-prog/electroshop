package com.electroshop.service;

import com.electroshop.dto.PurchaseDto;
import com.electroshop.dto.PurchaseRequest;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.*;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.PurchaseRepository;
import com.electroshop.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PurchaseService(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository,
                           ProductRepository productRepository) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    /**
     * Records a stock intake: creates the purchase and increases each product's stock.
     */
    public PurchaseDto create(PurchaseRequest req) {
        Supplier supplier = supplierRepository.findById(req.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.supplierId()));

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setPurchaseDate(req.purchaseDate() != null ? req.purchaseDate() : LocalDate.now());
        purchase.setInvoiceNumber(req.invoiceNumber());
        purchase.setNotes(req.notes());

        for (PurchaseRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", item.productId()));

            // Stock intake → increase stock
            product.setStockQuantity(product.getStockQuantity() + item.quantity());

            PurchaseItem pi = new PurchaseItem();
            pi.setProduct(product);
            pi.setQuantity(item.quantity());
            pi.setUnitPurchasePrice(item.unitPurchasePrice());
            purchase.addItem(pi);
        }

        purchase.recalculateTotal();
        return PurchaseDto.from(purchaseRepository.save(purchase));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseDto> list(Long supplierId, Pageable pageable) {
        Page<Purchase> page = (supplierId != null)
                ? purchaseRepository.findBySupplierId(supplierId, pageable)
                : purchaseRepository.findAll(pageable);
        return page.map(PurchaseDto::from);
    }

    @Transactional(readOnly = true)
    public PurchaseDto getById(Long id) {
        return PurchaseDto.from(findEntity(id));
    }

    /**
     * Deleting a purchase reverses its stock intake (clamped at 0).
     */
    public void delete(Long id) {
        Purchase purchase = findEntity(id);
        for (PurchaseItem item : purchase.getItems()) {
            Product p = item.getProduct();
            int reversed = p.getStockQuantity() - item.getQuantity();
            p.setStockQuantity(Math.max(0, reversed));
        }
        purchaseRepository.delete(purchase);
    }

    private Purchase findEntity(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
    }
}
