// Shared between the standalone "Jurnal de activitate" page and the
// per-product history section in the product preview popup (feature #5),
// so both surfaces describe the same audit actions the same way.

export const ACTION_STYLE = {
    PRODUCT_CREATED: 'bg-green-100 text-green-800',
    PRODUCT_UPDATED: 'bg-blue-100 text-blue-800',
    PRODUCT_PRICE_CHANGED: 'bg-purple-100 text-purple-800',
    PRODUCT_STOCK_CHANGED: 'bg-cyan-100 text-cyan-800',
    PRODUCT_ACTIVATED: 'bg-green-100 text-green-800',
    PRODUCT_DEACTIVATED: 'bg-slate-200 text-slate-700',
    PRODUCT_IMAGE_UPDATED: 'bg-indigo-100 text-indigo-800',
    PRODUCT_IMAGE_ADDED: 'bg-indigo-100 text-indigo-800',
    PRODUCT_IMAGE_DELETED: 'bg-red-100 text-red-800',
    PRODUCT_IMAGE_PRIMARY: 'bg-indigo-100 text-indigo-800',
    PRODUCT_IMAGE_REORDERED: 'bg-indigo-100 text-indigo-800',
    PRODUCT_DELETED: 'bg-red-100 text-red-800',
    PRODUCTS_BULK_DELETED: 'bg-red-100 text-red-800',
    ORDER_CREATED: 'bg-green-100 text-green-800',
    ORDER_STATUS_CHANGED: 'bg-amber-100 text-amber-800',
    ORDER_DELETED: 'bg-red-100 text-red-800',
    COMPANY_SETTINGS_UPDATED: 'bg-blue-100 text-blue-800',
    // Feature #6 — security events
    ACCOUNT_LOCKED: 'bg-red-100 text-red-800',
    TWO_FACTOR_ENABLED: 'bg-green-100 text-green-800',
    TWO_FACTOR_DISABLED: 'bg-slate-200 text-slate-700',
    // Feature #10 — quick in-store sale ("VÂNDUT")
    PRODUCT_SOLD: 'bg-green-100 text-green-800',
};

export const ACTION_LABELS = {
    PRODUCT_CREATED: 'Produs creat',
    PRODUCT_UPDATED: 'Produs actualizat',
    PRODUCT_PRICE_CHANGED: 'Preț modificat',
    PRODUCT_STOCK_CHANGED: 'Stoc modificat',
    PRODUCT_ACTIVATED: 'Produs activat',
    PRODUCT_DEACTIVATED: 'Produs dezactivat',
    PRODUCT_IMAGE_ADDED: 'Imagine adăugată',
    PRODUCT_IMAGE_UPDATED: 'Imagine actualizată',
    PRODUCT_IMAGE_DELETED: 'Imagine ștearsă',
    PRODUCT_IMAGE_PRIMARY: 'Imagine principală setată',
    PRODUCT_IMAGE_REORDERED: 'Imagini reordonate',
    PRODUCT_DELETED: 'Produs șters',
    PRODUCTS_BULK_DELETED: 'Produse șterse (grup)',
    ORDER_CREATED: 'Comandă creată',
    ORDER_STATUS_CHANGED: 'Status comandă schimbat',
    ORDER_DELETED: 'Comandă ștearsă',
    COMPANY_SETTINGS_UPDATED: 'Date firmă actualizate',
    // Feature #6 — security events
    ACCOUNT_LOCKED: 'Cont blocat (brute-force)',
    TWO_FACTOR_ENABLED: '2FA activată',
    TWO_FACTOR_DISABLED: '2FA dezactivată',
    // Feature #10 — quick in-store sale ("VÂNDUT")
    PRODUCT_SOLD: 'Vânzare directă (VÂNDUT)',
};

export const ACTION_OPTIONS = Object.keys(ACTION_LABELS).sort((a, b) =>
    ACTION_LABELS[a].localeCompare(ACTION_LABELS[b], 'ro')
                                                              );
