package com.electroshop.repository;

import com.electroshop.model.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    /**
     * Whether any goods-in entry ever recorded a purchase of this product.
     * Checked before a hard delete for the same reason as
     * {@link com.electroshop.repository.OrderItemRepository#existsByProductId(Long)}:
     * the accounting trail must stay intact.
     */
    boolean existsByProductId(Long productId);
}
