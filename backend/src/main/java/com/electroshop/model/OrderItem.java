package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_orderitem_order", columnList = "order_id"),
        @Index(name = "idx_orderitem_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Snapshot of the sale price at purchase time
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Snapshot of the product's acquisition/purchase price at the moment this
    // item was sold — used to compute real gross margin (COGS) in the accounting
    // report, without being retroactively distorted if the product's current
    // purchase price is edited later. Nullable because: (a) it is only populated
    // going forward from the release that introduced it — order items created
    // before that carry a null value here and the accounting report falls back
    // to the product's CURRENT purchase price for those; and (b) a product's
    // acquisition price can itself be unset (unknown) at the time of sale.
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    public BigDecimal getSubtotal() {
        if (unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
