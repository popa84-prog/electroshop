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
     * Every order line that ever referenced this product, each with its
     * owning {@code Order} reachable via {@code getOrder()}. Used only by the
     * explicit, irreversible force-delete path — removing each returned item
     * from its order's item list (and recalculating that order's total) is
     * how the caller physically deletes these rows via JPA's orphan-removal
     * cascade, instead of leaving them as dangling references.
     */
    List<OrderItem> findByProductId(Long productId);
}
