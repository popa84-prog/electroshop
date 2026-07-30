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

export const statusColor = (status) => {
  switch (status) {
    case 'PENDING':
      return 'bg-amber-100 text-amber-800';
    case 'PAID':
      return 'bg-blue-100 text-blue-800';
    case 'SHIPPED':
      return 'bg-indigo-100 text-indigo-800';
    case 'DELIVERED':
      return 'bg-green-100 text-green-800';
    case 'CANCELLED':
      return 'bg-red-100 text-red-800';
    default:
      return 'bg-slate-100 text-slate-800';
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
