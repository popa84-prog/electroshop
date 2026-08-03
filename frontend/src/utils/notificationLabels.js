// Shared between the notification bell dropdown and the full notification
// center page (feature #8 — notificări automate), so both surfaces describe
// each notification type the same way.
//
// XXII — TASK 1 / TASK 6. Two things changed here, and both are structural
// rather than cosmetic:
//
//   1. `TYPE_STYLE` used to carry light-mode Tailwind pairs, two of which
//      (`bg-indigo-100`, `text-indigo-800`) fall outside the compatibility
//      layer in index.css — so on the dark surface that badge rendered as a
//      near-white block with dark-blue text. The values are now explicit glass
//      tokens: a translucent fill, a matching neon edge, and ink that already
//      passes contrast against the deep-space surface.
//
//   2. `TYPE_ICON` used to be an emoji. Emoji are rendered by the operating
//      system, so the same notification list looked different on every machine
//      and could not follow the thin-line, 45° geometric iconography the brief
//      specifies. Each entry is now a `GeoIcon` name, drawn by the app itself.
//      Consumers render `<GeoIcon name={TYPE_ICON[type]} />`, not the string.

export const TYPE_STYLE = {
  NEW_ORDER:
    'border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.14)] text-[#7ee9bd]',
  LOW_STOCK:
    'border-[rgba(255,186,80,0.45)] bg-[rgba(255,186,80,0.14)] text-[#ffd27a]',
  NO_IMAGE:
    'border-[rgba(122,60,255,0.5)] bg-[rgba(122,60,255,0.16)] text-[#b795ff]',
  PRODUCT_INACTIVE:
    'border-[rgba(255,255,255,0.16)] bg-[rgba(255,255,255,0.07)] text-[#c9d4ff]',
  ACCOUNT_LOCKED:
    'border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.14)] text-[#ff8fa8]',
};

/** Fallback for a type the backend adds before the frontend knows about it. */
export const TYPE_STYLE_FALLBACK =
  'border-[rgba(255,255,255,0.16)] bg-[rgba(255,255,255,0.07)] text-[#c9d4ff]';

export const TYPE_LABELS = {
  NEW_ORDER: 'Comandă nouă',
  LOW_STOCK: 'Stoc redus',
  NO_IMAGE: 'Fără imagine',
  PRODUCT_INACTIVE: 'Produs inactiv',
  ACCOUNT_LOCKED: 'Cont blocat',
};

/** GeoIcon names — see components/xxii/GeoIcon.jsx for the full set. */
export const TYPE_ICON = {
  NEW_ORDER: 'cart',
  LOW_STOCK: 'chart',
  NO_IMAGE: 'zoom',
  PRODUCT_INACTIVE: 'clock',
  ACCOUNT_LOCKED: 'shield',
};

/** GeoIcon name used when the type is unrecognised. */
export const TYPE_ICON_FALLBACK = 'bell';

/**
 * NeonBadge tone per type, for surfaces that render a `<NeonBadge>` rather than
 * a hand-rolled span. Kept next to TYPE_STYLE so a new notification type is
 * described in exactly one place.
 */
export const TYPE_TONE = {
  NEW_ORDER: 'good',
  LOW_STOCK: 'warning',
  NO_IMAGE: 'neon',
  PRODUCT_INACTIVE: 'neutral',
  ACCOUNT_LOCKED: 'critical',
};

export const TYPE_OPTIONS = Object.keys(TYPE_LABELS);
