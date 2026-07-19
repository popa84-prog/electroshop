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

// Product images can be absolute URLs or backend-relative (/uploads/..)
// Backend-relative images are served by the API, so prefix with the API base.
const API_BASE = import.meta.env.VITE_API_URL || '/api';
export const resolveImage = (url) => {
  if (!url) return 'https://placehold.co/600x400?text=No+Image';
  if (url.startsWith('http')) return url;
  return `${API_BASE}${url}`;
};
