package com.electroshop.repository;

import com.electroshop.model.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    // ---- Restock recommendations (task 13) -------------------------------

    /**
     * Which supplier last delivered each product, and when.
     *
     * <p>Returns {@code [productId, supplierId, supplierName, lastPurchaseDate,
     * lastUnitPrice]}. A restock recommendation that names the supplier saves the
     * operator a lookup; one that does not is a suggestion to go and find out
     * something the system already knows.</p>
     *
     * <p>The row picked is the one with the most recent purchase date. Grouping and
     * taking a maximum would give the latest date but not the supplier attached to it,
     * so the query orders instead and the caller keeps the first row per product —
     * which is correct because the ordering puts the newest purchase first.</p>
     */
    @Query("""
            SELECT pi.product.id, p.supplier.id, p.supplier.name, p.purchaseDate, pi.unitPurchasePrice
            FROM PurchaseItem pi
            JOIN pi.purchase p
            WHERE pi.product IS NOT NULL
              AND p.supplier IS NOT NULL
            ORDER BY pi.product.id ASC, p.purchaseDate DESC
            """)
    List<Object[]> lastSupplierPerProductRaw();

    /**
     * The same data with one row per product, newest purchase kept.
     *
     * <p>A default method rather than a second query: SQL's "greatest row per group"
     * needs either a window function or a correlated subquery, and both are heavier to
     * read and to verify than deduplicating an already-ordered list. The purchase
     * history is small — one row per goods-in line, not per sale — so the whole list
     * is cheap to walk.</p>
     */
    default List<Object[]> lastSupplierPerProduct() {
        List<Object[]> raw = lastSupplierPerProductRaw();
        List<Object[]> out = new java.util.ArrayList<>();
        Long lastProduct = null;
        for (Object[] row : raw) {
            Long productId = row[0] == null ? null : ((Number) row[0]).longValue();
            if (productId == null || productId.equals(lastProduct)) {
                continue;
            }
            lastProduct = productId;
            out.add(row);
        }
        return out;
    }
}
