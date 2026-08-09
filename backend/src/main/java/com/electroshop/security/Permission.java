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
    OFFERS_MANAGE,
    /**
     * Reads the business metrics the dashboard is built on: stock value, potential
     * profit, profit breakdown, financial overview, inventory health, order
     * efficiency, customer insights and product performance.
     *
     * <p>Deliberately separate from {@link #DASHBOARD_VIEW}. That permission opens
     * the page; this one exposes purchase prices, margins and per-customer buying
     * patterns. An Editor who may look at order counts has no business reading what
     * the company pays its suppliers, and before this permission existed the two
     * were the same decision.</p>
     */
    METRICS_VIEW,
    /**
     * Reads infrastructure state: API latency and error counters, cron job results,
     * persisted operational logs, and the backup section.
     *
     * <p>Admin only. Operational logs carry endpoint paths, stack traces and driver
     * messages — the exact material an attacker uses to map a system — so the
     * audience for them is the smallest one that can still fix an outage.</p>
     */
    SYSTEM_MONITOR,
    /**
     * Reads campaign performance: impressions, clicks, conversions and cost per
     * acquisition.
     *
     * <p>Distinct from {@link #OFFERS_MANAGE}, which creates and edits offers.
     * Judging a campaign and running one are different jobs, and the first does not
     * require the ability to change what is on the storefront.</p>
     */
    MARKETING_VIEW,
    /**
     * Uses the personal productivity tools: notes, reminders and internal tasks.
     *
     * <p>Every row these create is owned by, and visible only to, the administrator
     * who wrote it, so the permission grants access to one's own workspace and never
     * to anyone else's.</p>
     */
    TOOLS_USE
}
