// Frontend mirror of backend RolePermissions.java (feature #6 — granular
// per-role permissions). This is a UX convenience ONLY: it hides controls the
// current user can't use so the admin panel doesn't dangle dead buttons in
// front of them. The backend re-checks every one of these on every request
// (@PreAuthorize("@permissionService.has('...')")) and is the real gate —
// nothing here should ever be treated as a security boundary by itself.

export const ROLE_PERMISSIONS = {
    ROLE_ADMIN: null, // null = every permission
    ROLE_MANAGER: new Set([
          'DASHBOARD_VIEW',
          'PRODUCTS_VIEW', 'PRODUCTS_MANAGE', 'PRODUCTS_PRICE', 'PRODUCTS_DELETE', 'PRODUCTS_IMPORT',
          'ORDERS_VIEW', 'ORDERS_MANAGE',
          'AUDIT_VIEW', 'AUDIT_EXPORT',
          'SUPPLIERS_MANAGE', 'PURCHASES_MANAGE',
        ]),
    ROLE_EDITOR: new Set([
          'DASHBOARD_VIEW',
          'PRODUCTS_VIEW', 'PRODUCTS_MANAGE',
          'ORDERS_VIEW',
          'AUDIT_VIEW',
        ]),
    ROLE_USER: new Set(),
};

/** True if any of the given roles grants the permission. */
export function rolesHave(roles, permission) {
    if (!roles || roles.length === 0) return false;
    return roles.some((r) => {
          const grants = ROLE_PERMISSIONS[r];
          if (grants === undefined) return false;
          return grants === null || grants.has(permission); // null (Admin) = everything
    });
}

export const ROLE_LABELS = {
    ROLE_ADMIN: 'Admin',
    ROLE_MANAGER: 'Manager',
    ROLE_EDITOR: 'Editor',
    ROLE_USER: 'Utilizator',
};

export const ROLE_BADGE_STYLE = {
    ROLE_ADMIN: 'bg-purple-100 text-purple-800',
    ROLE_MANAGER: 'bg-blue-100 text-blue-800',
    ROLE_EDITOR: 'bg-cyan-100 text-cyan-800',
    ROLE_USER: 'bg-slate-100 text-slate-700',
};
