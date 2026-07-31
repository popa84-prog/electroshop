package com.electroshop.model;

/**
 * Admin-panel roles, from least to most privileged: EDITOR can edit product
 * content, MANAGER can also manage stock/orders/pricing/deletions, ADMIN has
 * full access (users, settings, everything). ROLE_USER is the plain
 * storefront customer role and has no admin-panel permissions at all.
 *
 * The actual permission grants per role live in {@link com.electroshop.security.RolePermissions}
 * so the "who can do what" matrix has a single source of truth.
 */
public enum RoleName {
    ROLE_USER,
    ROLE_EDITOR,
    ROLE_MANAGER,
    ROLE_ADMIN
}
