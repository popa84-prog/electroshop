package com.electroshop.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Results of the sidebar's global search across products, orders and users.
 *
 * <p>Answers {@code GET /api/admin/search?q=…}.</p>
 *
 * <p><b>Results are grouped, never merged into one ranked list.</b> A product, an order
 * and a user are not comparable, and any scoring that put them in a single column would
 * be inventing a relevance relationship between things that have none. Grouping lets the
 * operator's eye go straight to the section they meant.</p>
 *
 * <p><b>Every group is filtered by the caller's permissions before it is filled.</b> An
 * Editor searching a customer's surname must not learn that the account exists from the
 * result count, so a group the caller cannot view is absent rather than empty — the two
 * are distinguishable, and the difference leaks exactly the fact the permission
 * withholds.</p>
 *
 * @param query      the term that was searched, echoed back so a late response can be
 *                   discarded when the operator has already typed something else
 * @param products   matching products, empty when none match or absent when not permitted
 * @param orders     matching orders, same rule
 * @param users      matching users, same rule
 * @param totalHits  how many results were found across the permitted groups
 * @param truncated  whether more results exist than were returned
 * @param tookMs     how long the search took, so a slow index is visible rather than
 *                   experienced as an unexplained pause
 */
public record GlobalSearchDto(
        String query,
        List<ProductHit> products,
        List<OrderHit> orders,
        List<UserHit> users,
        int totalHits,
        boolean truncated,
        long tookMs
) {

    /**
     * A matching product.
     *
     * @param id            database id
     * @param name          product name
     * @param imageUrl      thumbnail, may be null
     * @param sku           stock keeping unit, may be null
     * @param brand         brand as recorded
     * @param price         current selling price
     * @param stockQuantity units on hand, so a search result already answers the
     *                      question that usually follows finding the product
     * @param active        whether it is published
     * @param linkTo        the admin route that opens it
     */
    public record ProductHit(
            Long id,
            String name,
            String imageUrl,
            String sku,
            String brand,
            BigDecimal price,
            int stockQuantity,
            boolean active,
            String linkTo
    ) {}

    /**
     * A matching order.
     *
     * @param id            database id
     * @param customerEmail who placed it
     * @param status        where it stands
     * @param totalAmount   order value
     * @param placedAt      when it arrived, as an ISO string
     * @param linkTo        the admin route that opens it
     */
    public record OrderHit(
            Long id,
            String customerEmail,
            String status,
            BigDecimal totalAmount,
            String placedAt,
            String linkTo
    ) {}

    /**
     * A matching user.
     *
     * @param id       database id
     * @param email    account email
     * @param fullName display name, may be null
     * @param roles    the roles held
     * @param enabled  whether the account is active
     * @param linkTo   the admin route that opens it
     */
    public record UserHit(
            Long id,
            String email,
            String fullName,
            List<String> roles,
            boolean enabled,
            String linkTo
    ) {}
}
