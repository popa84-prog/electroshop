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

/**
 * XXII — role chips as glass, ordered by privilege so the accent itself reads
 * as a ladder: violet (total access) → blue → cyan → neutral grey. Privilege is
 * additionally always printed in words via `ROLE_LABELS`, so an operator never
 * has to infer "this one is an admin" from a hue.
 */
export const ROLE_BADGE_STYLE = {
  ROLE_ADMIN: 'border border-[rgba(122,60,255,0.45)] bg-[rgba(122,60,255,0.18)] text-[#d5c2ff]',
  ROLE_MANAGER: 'border border-[rgba(46,123,255,0.45)] bg-[rgba(46,123,255,0.16)] text-[#b7d0ff]',
  ROLE_EDITOR: 'border border-[rgba(34,232,245,0.45)] bg-[rgba(34,232,245,0.14)] text-[#a5f0f8]',
  ROLE_USER: 'border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.06)] text-[#a8b0d4]',
};
