package com.electroshop.repository;

import com.electroshop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Whether any order — past or present — ever included this product.
     * Checked before a hard delete: an order row's {@code product_id} foreign
     * key must never dangle, so a product with sales history cannot be
     * physically removed without corrupting every invoice that references it.
     */
    boolean existsByProductId(Long productId);
}
