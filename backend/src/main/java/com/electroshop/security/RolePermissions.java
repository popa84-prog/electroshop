package com.electroshop.security;

import com.electroshop.model.RoleName;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for "which role can do what". Admin has every
 * permission — including {@link Permission#PRODUCTS_FORCE_DELETE}, granted
 * here only via {@code EnumSet.allOf} and deliberately absent from every
 * other role's explicit list below, so a newly added permission is
 * Admin-only by default unless a role is expanded to include it by name;
 * Manager runs day-to-day operations (products, stock, orders, pricing,
 * suppliers/purchases, audit) but not user accounts, company settings, or
 * permanently erasing accounting history; Editor can edit product content
 * but not delete, price, import, or manage orders/users/settings. Plain
 * ROLE_USER (storefront customers) has no admin-panel permissions.
 */
public final class RolePermissions {

    private static final Map<RoleName, Set<Permission>> MATRIX = new EnumMap<>(RoleName.class);

    static {
        MATRIX.put(RoleName.ROLE_ADMIN, EnumSet.allOf(Permission.class));

        MATRIX.put(RoleName.ROLE_MANAGER, EnumSet.of(
                Permission.DASHBOARD_VIEW,
                Permission.PRODUCTS_VIEW, Permission.PRODUCTS_MANAGE,
                Permission.PRODUCTS_PRICE, Permission.PRODUCTS_DELETE, Permission.PRODUCTS_IMPORT,
                Permission.ORDERS_VIEW, Permission.ORDERS_MANAGE,
                Permission.AUDIT_VIEW, Permission.AUDIT_EXPORT,
                Permission.SUPPLIERS_MANAGE, Permission.PURCHASES_MANAGE,
                Permission.OFFERS_MANAGE,
                // A Manager runs the commercial side of the business, so the metrics
                // that describe it — margin, inventory health, order efficiency,
                // campaign results — are part of the job. SYSTEM_MONITOR is not:
                // infrastructure logs stay with Admin.
                Permission.METRICS_VIEW, Permission.MARKETING_VIEW, Permission.TOOLS_USE,
                // Invoicing follows the same line. Issuing an invoice is part of
                // running the commercial side, so the Manager does it. Issuing a
                // credit note reverses a figure that has already been reported,
                // and unless explicitly disabled it moves stock — that one stays
                // with Admin.
                Permission.INVOICE_VIEW, Permission.INVOICE_ISSUE
        ));

        MATRIX.put(RoleName.ROLE_EDITOR, EnumSet.of(
                Permission.DASHBOARD_VIEW,
                Permission.PRODUCTS_VIEW, Permission.PRODUCTS_MANAGE,
                Permission.ORDERS_VIEW,
                Permission.AUDIT_VIEW,
                // Notes and reminders are the Editor's own workspace and expose no
                // business data. METRICS_VIEW is withheld: an Editor writes product
                // content and has no need to see purchase prices or margins.
                Permission.TOOLS_USE
                // No invoicing at all, not even INVOICE_VIEW. The register carries
                // customer fiscal identifiers and the full billing history, which
                // is a different circle of trust from product content.
        ));

        MATRIX.put(RoleName.ROLE_USER, Collections.emptySet());
    }

    private RolePermissions() {
    }

    public static Set<Permission> of(RoleName role) {
        return MATRIX.getOrDefault(role, Collections.emptySet());
    }
}
