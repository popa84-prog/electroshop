package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_items", indexes = {
        @Index(name = "idx_purchaseitem_purchase", columnList = "purchase_id"),
        @Index(name = "idx_purchaseitem_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    // Nullable — mirrors OrderItem.product exactly (see its javadoc): lets
    // ProductService#forceDeleteWithHistory permanently remove a product from the
    // catalogue while this goods-in line survives, product_id set to NULL, instead
    // of either the database refusing the deletion or the row being destroyed
    // along with it.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    // Snapshot of the product's name at intake time — mirrors OrderItem.productName.
    // Populated at every PurchaseItem creation site in PurchaseService; rows created
    // before this field existed carry null here until a force-delete backfills it.
    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    // Purchase (cost) price per unit at intake time
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPurchasePrice;

    public BigDecimal getSubtotal() {
        if (unitPurchasePrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPurchasePrice.multiply(BigDecimal.valueOf(quantity));
    }
}
