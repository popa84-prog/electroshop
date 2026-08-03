package com.electroshop.security;

/**
 * Fine-grained admin-panel permissions (feature #6: "permisiuni granular
 * controlate"). Controllers/services check these via {@link PermissionService}
 * instead of hard-coding {@code hasRole('ADMIN')} everywhere, so which role can
 * do what is defined in exactly one place: {@link RolePermissions}.
 */
public enum Permission {
    DASHBOARD_VIEW,
    PRODUCTS_VIEW,
    PRODUCTS_MANAGE,
    PRODUCTS_PRICE,
    PRODUCTS_DELETE,
    /**
     * Permanently removes a product together with every order/purchase line
     * item that ever referenced it — deliberately distinct from and stronger
     * than {@link #PRODUCTS_DELETE}. {@code PRODUCTS_DELETE} always keeps
     * historical invoices intact (it deactivates instead of deleting a
     * product with sales history); this permission is the explicit override
     * that accepts corrupting that history. Granted to Admin only — see
     * {@link RolePermissions} — so a Manager who can delete products day to
     * day cannot, by default, also erase accounting history.
     */
    PRODUCTS_FORCE_DELETE,
    PRODUCTS_IMPORT,
    ORDERS_VIEW,
    ORDERS_MANAGE,
    AUDIT_VIEW,
    AUDIT_EXPORT,
    USERS_MANAGE,
    SETTINGS_MANAGE,
    SUPPLIERS_MANAGE,
    PURCHASES_MANAGE,
    ACCOUNTING_VIEW,
    OFFERS_MANAGE
}
