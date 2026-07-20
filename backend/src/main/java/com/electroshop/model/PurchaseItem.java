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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

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
