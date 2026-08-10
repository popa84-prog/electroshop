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
    /**
     * The one authority that puts goods on the shelf. Recording a purchase here
     * and importing a delivery from a spreadsheet are two paths to the same
     * effect; routing both through this service is what keeps a single delivery
     * from being counted twice.
     */
    private final StockIntakeService stockIntakeService;

    public PurchaseService(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository,
                           ProductRepository productRepository, StockIntakeService stockIntakeService) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.stockIntakeService = stockIntakeService;
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

            // Stock intake, through the single authority.
            //
            // Two things changed here. The obvious one is that stock is no longer
            // written inline, so a delivery imported from a spreadsheet and a
            // purchase recorded by hand cannot both apply the same quantity.
            //
            // The quieter one is that the product's cost now moves. This method
            // used to add stock and never touch purchasePrice, so buying at a new
            // cost through this screen left the product carrying the old one —
            // margin and potential profit then reported against a cost that no
            // longer matched the goods on the shelf. The intake service applies
            // the weighted average, exactly as the spreadsheet import always did.
            stockIntakeService.intake(product, item.quantity(), item.unitPurchasePrice());

            PurchaseItem pi = new PurchaseItem();
            pi.setProduct(product);
            // Snapshot the product's name at intake time — see PurchaseItem.productName.
            pi.setProductName(product.getName());
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
            // A force-deleted product (see ProductService#forceDeleteWithHistory) has
            // no live row left to adjust — the line itself is kept intact for
            // accounting, but there is nothing in the catalogue to reverse stock on.
            if (p == null) {
                continue;
            }
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
