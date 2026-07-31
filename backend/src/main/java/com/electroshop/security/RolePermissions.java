package com.electroshop.security;

import com.electroshop.model.RoleName;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for "which role can do what". Admin has every
   * permission; Manager runs day-to-day operations (products, stock, orders,
                                                      * pricing, suppliers/purchases, audit) but not user accounts or company
   * settings; Editor can edit product content but not delete, price, import,
   * or manage orders/users/settings. Plain ROLE_USER (storefront customers)
   * has no admin-panel permissions.
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
                            Permission.SUPPLIERS_MANAGE, Permission.PURCHASES_MANAGE
                    ));

          MATRIX.put(RoleName.ROLE_EDITOR, EnumSet.of(
                            Permission.DASHBOARD_VIEW,
                            Permission.PRODUCTS_VIEW, Permission.PRODUCTS_MANAGE,
                            Permission.ORDERS_VIEW,
                            Permission.AUDIT_VIEW
                    ));

          MATRIX.put(RoleName.ROLE_USER, Collections.emptySet());
    }

    private RolePermissions() {
    }

    public static Set<Permission> of(RoleName role) {
              return MATRIX.getOrDefault(role, Collections.emptySet());
    }
}
