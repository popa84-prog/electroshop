import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Builds the breadcrumb trail from the current admin route. Task 3.
 *
 * ## Labels come from a registry, not from the URL
 *
 * Turning `/admin/login-events` into "Login events" by splitting on hyphens
 * produces English words in a Romanian interface, and gets steadily worse as
 * routes get shorter. The map below is the same vocabulary the navigation rail
 * uses, so a section's name appears identically in both places and changing it
 * is one edit.
 *
 * ## An unknown segment is still shown
 *
 * A route with no entry renders its raw segment rather than being dropped. A
 * trail that silently omits a level is worse than one with an ugly label: the
 * operator loses the sense of where they are, which is the entire purpose.
 */

/** Section names, matching the navigation rail. */
const SEGMENT_LABELS = {
  admin: 'Administrare',
  products: 'Produse',
  orders: 'Comenzi',
  users: 'Utilizatori',
  suppliers: 'Furnizori',
  offers: 'Promoții',
  purchases: 'Recepții',
  accounting: 'Contabilitate',
  audit: 'Jurnal de activitate',
  settings: 'Setări',
  'login-events': 'Jurnal de conectări',
  notifications: 'Notificări',
  dashboard: 'Panou de bord',
};

export default function useBreadcrumbs(extra = []) {
  const location = useLocation();

  return useMemo(() => {
    const segments = location.pathname.split('/').filter(Boolean);

    const trail = segments.map((segment, index) => {
      const to = `/${segments.slice(0, index + 1).join('/')}`;
      return {
        to,
        label: SEGMENT_LABELS[segment] || decodeURIComponent(segment),
      };
    });

    // The admin root has no page of its own, so its crumb points at the
    // dashboard rather than at a route that would redirect.
    if (trail.length && trail[0].to === '/admin') {
      trail[0] = { to: '/admin', label: SEGMENT_LABELS.admin };
    }

    // Extra crumbs describe state the URL does not carry — an open product, a
    // selected order. They are appended rather than inferred, because only the
    // page knows what it currently has loaded.
    return [...trail, ...extra];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, JSON.stringify(extra)]);
}
