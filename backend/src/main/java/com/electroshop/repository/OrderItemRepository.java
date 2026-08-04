package com.electroshop.repository;

import com.electroshop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Whether any order — past or present — ever included this product.
     * Checked before a hard delete: an order row's {@code product_id} foreign
     * key must never dangle, so a product with sales history cannot be
     * physically removed without corrupting every invoice that references it.
     */
    boolean existsByProductId(Long productId);

    /**
     * Every order line that ever referenced this product. Used only by the
     * explicit, irreversible force-delete path
     * ({@code ProductService#forceDeleteWithHistory}) — each returned item has
     * its {@code product} link set to {@code null} and is otherwise left
     * untouched (quantity, prices, and the {@code productName} snapshot all
     * survive), so the accounting trail stays intact even after the product
     * row itself is gone.
     */
    List<OrderItem> findByProductId(Long productId);
}
