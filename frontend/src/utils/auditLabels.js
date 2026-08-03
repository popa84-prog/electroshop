// Shared between the standalone "Jurnal de activitate" page and the
// per-product history section in the product preview popup (feature #5),
// so both surfaces describe the same audit actions the same way.
//
// XXII — TASK 1 / TASK 6. `ACTION_STYLE` previously carried light-mode Tailwind
// pairs, four families of which (`bg-purple-*`, `bg-cyan-*`, `bg-indigo-*` and
// their text partners) fall outside the compatibility layer in index.css — on
// the dark surface those badges rendered as pale blocks with dark text. Every
// value is now an explicit glass token: translucent fill, matching neon edge,
// ink already verified against the deep-space surface.
//
// The hue set was reduced from seven to four deliberately. Running the four
// candidate inks through the palette validator against the #0a0b1e surface:
//
//   normal-vision separation  worst pair #ffd27a↔#7ee9bd  ΔE 15.8   PASS
//   contrast vs surface       all four >= 3:1                       PASS
//   CVD separation            worst pair #ff8fa8↔#7ee9bd ΔE 7.3     WARN (deutan)
//
// Cyan was cut from this set: against #7ee9bd it scored ΔE 8.6 for *normal*
// vision, below the 15 floor — a full-colour reader cannot reliably tell those
// two badges apart. Cyan also carries interactive meaning everywhere else in
// the XXII system, so spending it on a passive status badge would blunt that.
//
// The remaining red↔green CVD warning sits in the 6–8 band, which is permitted
// only alongside a secondary encoding. That condition is met by construction:
// every badge renders its written label and a geometric icon, so the colour is
// reinforcement and never the carrier of the meaning.

/** Green — something was brought into existence or turned on. */
const GOOD = 'border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.14)] text-[#7ee9bd]';
/** Amber — something moved between states and may need watching. */
const WARN = 'border-[rgba(255,186,80,0.45)] bg-[rgba(255,186,80,0.14)] text-[#ffd27a]';
/** Red — something was destroyed or blocked. */
const CRIT = 'border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.14)] text-[#ff8fa8]';
/** Violet — something was edited in place. */
const EDIT = 'border-[rgba(122,60,255,0.5)] bg-[rgba(122,60,255,0.16)] text-[#b795ff]';
/** Neutral — something was turned off, without loss. */
const MUTE = 'border-[rgba(255,255,255,0.16)] bg-[rgba(255,255,255,0.07)] text-[#c9d4ff]';

export const ACTION_STYLE = {
  PRODUCT_CREATED: GOOD,
  PRODUCT_UPDATED: EDIT,
  PRODUCT_PRICE_CHANGED: EDIT,
  PRODUCT_STOCK_CHANGED: EDIT,
  PRODUCT_ACTIVATED: GOOD,
  PRODUCT_DEACTIVATED: MUTE,
  PRODUCT_IMAGE_UPDATED: EDIT,
  PRODUCT_IMAGE_ADDED: EDIT,
  PRODUCT_IMAGE_DELETED: CRIT,
  PRODUCT_IMAGE_PRIMARY: EDIT,
  PRODUCT_IMAGE_REORDERED: EDIT,
  PRODUCT_DELETED: CRIT,
  PRODUCTS_BULK_DELETED: CRIT,
  ORDER_CREATED: GOOD,
  ORDER_STATUS_CHANGED: WARN,
  ORDER_DELETED: CRIT,
  COMPANY_SETTINGS_UPDATED: EDIT,
  // Feature #6 — security events
  ACCOUNT_LOCKED: CRIT,
  TWO_FACTOR_ENABLED: GOOD,
  TWO_FACTOR_DISABLED: MUTE,
  // Feature #10 — quick in-store sale ("VÂNDUT")
  PRODUCT_SOLD: GOOD,
};

/** Fallback for an action the backend emits before the frontend knows it. */
export const ACTION_STYLE_FALLBACK = MUTE;

/**
 * GeoIcon name per action — the secondary encoding that makes the red↔green
 * CVD warning above safe. See components/xxii/GeoIcon.jsx for the full set.
 */
export const ACTION_ICON = {
  PRODUCT_CREATED: 'sparkle',
  PRODUCT_UPDATED: 'gear',
  PRODUCT_PRICE_CHANGED: 'coins',
  PRODUCT_STOCK_CHANGED: 'box',
  PRODUCT_ACTIVATED: 'bolt',
  PRODUCT_DEACTIVATED: 'clock',
  PRODUCT_IMAGE_UPDATED: 'layers',
  PRODUCT_IMAGE_ADDED: 'layers',
  PRODUCT_IMAGE_DELETED: 'trash',
  PRODUCT_IMAGE_PRIMARY: 'star',
  PRODUCT_IMAGE_REORDERED: 'refresh',
  PRODUCT_DELETED: 'trash',
  PRODUCTS_BULK_DELETED: 'trash',
  ORDER_CREATED: 'cart',
  ORDER_STATUS_CHANGED: 'truck',
  ORDER_DELETED: 'trash',
  COMPANY_SETTINGS_UPDATED: 'document',
  // Feature #6 — security events
  ACCOUNT_LOCKED: 'shield',
  TWO_FACTOR_ENABLED: 'shield',
  TWO_FACTOR_DISABLED: 'shield',
  // Feature #10 — quick in-store sale ("VÂNDUT")
  PRODUCT_SOLD: 'coins',
};

export const ACTION_ICON_FALLBACK = 'pulse';

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
