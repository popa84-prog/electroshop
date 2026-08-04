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

    // Nullable (and optional = true) so a product can be permanently removed from
    // the catalogue — via ProductService#forceDeleteWithHistory — without deleting
    // this row: the historical line survives with product_id set to NULL instead
    // of the database refusing the deletion outright over the foreign key, or the
    // application deleting the line and silently corrupting past invoices and the
    // accounting report's gross-margin figure. See productName below for how the
    // line keeps displaying correctly once this link is gone.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    // Snapshot of the product's name at sale time. Always populated going forward
    // from the release that introduced it (set alongside setProduct(...) at every
    // OrderItem creation site in OrderService); order items created before that
    // carry a null value here. It exists so the line still shows a real product
    // name after a force-delete nulls out `product` above — OrderItemDto prefers
    // the live product's current name when the link is intact (so a rename shows
    // up on old orders too, matching pre-existing behavior) and falls back to this
    // snapshot only once the product is gone. Backfilled from the product's name
    // at the moment of force-delete for any pre-existing row that never had it set.
    @Column(name = "product_name", length = 255)
    private String productName;

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
