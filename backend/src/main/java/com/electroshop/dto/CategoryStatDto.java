package com.electroshop.dto;

/**
 * A category together with the number of products it currently holds.
 * Used by the storefront to render the "most popular categories" tiles from
 * live catalogue data rather than from a hardcoded list.
 *
 * @param name         the category name exactly as stored on the products
 * @param productCount how many products are filed under that category
 */
public record CategoryStatDto(String name, long productCount) {
}
