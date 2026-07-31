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
    PRODUCTS_IMPORT,
    ORDERS_VIEW,
    ORDERS_MANAGE,
    AUDIT_VIEW,
    AUDIT_EXPORT,
    USERS_MANAGE,
    SETTINGS_MANAGE,
    SUPPLIERS_MANAGE,
    PURCHASES_MANAGE,
    ACCOUNTING_VIEW
}
