package com.electroshop.repository;

import com.electroshop.model.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {

    /**
     * Whether any goods-in entry ever recorded a purchase of this product.
     * Checked before a hard delete for the same reason as
     * {@link com.electroshop.repository.OrderItemRepository#existsByProductId(Long)}:
     * the accounting trail must stay intact.
     */
    boolean existsByProductId(Long productId);

    /**
     * Every purchase line that ever referenced this product. Used only by the
     * explicit, irreversible force-delete path — mirrors
     * {@link com.electroshop.repository.OrderItemRepository#findByProductId(Long)}:
     * each returned item has its {@code product} link set to {@code null} and
     * is otherwise preserved unchanged.
     */
    List<PurchaseItem> findByProductId(Long productId);
}
