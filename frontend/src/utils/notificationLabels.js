// Shared between the notification bell dropdown and the full notification
// center page (feature #8 — notificări automate), so both surfaces describe
// each notification type the same way.

export const TYPE_STYLE = {
    NEW_ORDER: 'bg-green-100 text-green-800',
    LOW_STOCK: 'bg-amber-100 text-amber-800',
    NO_IMAGE: 'bg-indigo-100 text-indigo-800',
    PRODUCT_INACTIVE: 'bg-slate-200 text-slate-700',
    ACCOUNT_LOCKED: 'bg-red-100 text-red-800',
};

export const TYPE_LABELS = {
    NEW_ORDER: 'Comandă nouă',
    LOW_STOCK: 'Stoc redus',
    NO_IMAGE: 'Fără imagine',
    PRODUCT_INACTIVE: 'Produs inactiv',
    ACCOUNT_LOCKED: 'Cont blocat',
};

// Small hand-drawn glyph per type — mirrors AdminNav's inline-SVG icon pattern
// so the notification list doesn't need a new icon dependency.
export const TYPE_ICON = {
    NEW_ORDER: '🛒',
    LOW_STOCK: '📉',
    NO_IMAGE: '🖼️',
    PRODUCT_INACTIVE: '⏸️',
    ACCOUNT_LOCKED: '🔒',
};

export const TYPE_OPTIONS = Object.keys(TYPE_LABELS);
