export const formatPrice = (value) =>
  new Intl.NumberFormat('ro-RO', { style: 'currency', currency: 'RON' }).format(Number(value || 0));

export const formatDate = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('ro-RO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

/**
 * XXII — order-status presentation, in one place.
 *
 * The hues are the validated status palette from `components/xxii/ChartTheme`
 * (pending #b08c09, paid #2e7bff, shipped #d032b8, delivered #1fac79,
 * cancelled #b82f3c) rendered as a glass chip: the hue at 14% for the fill, at
 * 42% for the edge, and a light tint of the same hue for the ink so the text
 * itself clears contrast against the dark surface.
 *
 * Shipped is magenta rather than the obvious indigo because indigo against the
 * blue of PAID is the pair a deuteranopic operator cannot separate — on an
 * order table, mistaking "plătită" for "expediată" is a shipping error, not a
 * cosmetic one. The hex values are duplicated rather than imported so this
 * plain utility module stays free of a component dependency; the source of
 * truth and its justification live in ChartTheme.jsx.
 */
export const statusColor = (status) => {
  switch (status) {
    case 'PENDING':
      return 'border border-[rgba(176,140,9,0.42)] bg-[rgba(176,140,9,0.16)] text-[#f0d089]';
    case 'PAID':
      return 'border border-[rgba(46,123,255,0.42)] bg-[rgba(46,123,255,0.16)] text-[#b7d0ff]';
    case 'SHIPPED':
      return 'border border-[rgba(208,50,184,0.42)] bg-[rgba(208,50,184,0.16)] text-[#f5b6e6]';
    case 'DELIVERED':
      return 'border border-[rgba(31,172,121,0.42)] bg-[rgba(31,172,121,0.16)] text-[#93e9c4]';
    case 'CANCELLED':
      return 'border border-[rgba(184,47,60,0.42)] bg-[rgba(184,47,60,0.16)] text-[#ffb3bd]';
    default:
      return 'border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.06)] text-[#a8b0d4]';
  }
};

/** Romanian status names — status is never carried by colour alone. */
export const STATUS_LABELS = {
  PENDING: 'În așteptare',
  PAID: 'Plătită',
  SHIPPED: 'Expediată',
  DELIVERED: 'Livrată',
  CANCELLED: 'Anulată',
};

export const statusLabel = (status) => STATUS_LABELS[status] || status;

/**
 * A glyph per status, so the chip survives greyscale, forced-colors mode and
 * a black-and-white printout of an order list.
 */
export const STATUS_GLYPHS = {
  PENDING: '◷',
  PAID: '◆',
  SHIPPED: '➤',
  DELIVERED: '✓',
  CANCELLED: '✕',
};

export const statusGlyph = (status) => STATUS_GLYPHS[status] || '■';

/** Maps an order status onto a `NeonBadge` tone. */
export const statusTone = (status) => {
  switch (status) {
    case 'PENDING':
      return 'warning';
    case 'PAID':
      return 'neon';
    case 'SHIPPED':
      return 'magenta';
    case 'DELIVERED':
      return 'good';
    case 'CANCELLED':
      return 'critical';
    default:
      return 'neutral';
  }
};

// Turns a timestamp into "acum 5 minute" / "acum 3 ore" / "acum 2 zile" style text,
// falling back to the absolute date once it's further away than that reads naturally.
export const formatRelative = (value) => {
  if (!value) return '-';
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return '-';
  const diffMs = Date.now() - then;
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diffMs < minute) return 'acum câteva secunde';
  if (diffMs < hour) {
    const m = Math.round(diffMs / minute);
    return `acum ${m} ${m === 1 ? 'minut' : 'minute'}`;
  }
  if (diffMs < day) {
    const h = Math.round(diffMs / hour);
    return `acum ${h} ${h === 1 ? 'oră' : 'ore'}`;
  }
  if (diffMs < 7 * day) {
    const d = Math.round(diffMs / day);
    return `acum ${d} ${d === 1 ? 'zi' : 'zile'}`;
  }
  return formatDate(value);
};

// Product images can be absolute URLs or backend-relative (/uploads/..)
// Backend-relative images are served by the API, so prefix with the API base.
const API_BASE = import.meta.env.VITE_API_URL || '/api';
export const resolveImage = (url) => {
  if (!url) return 'https://placehold.co/600x400?text=No+Image';
  if (url.startsWith('http')) return url;
  return `${API_BASE}${url}`;
};
