import { createContext, useContext } from 'react';
import { NavLink } from 'react-router-dom';

/**
 * Small hand-drawn icon set (outline style, 24x24, single stroke) so the admin
 * chrome does not depend on an icon package — this project has no working
 * package install in some environments, so every icon here is inline SVG.
 */
const ICONS = {
  dashboard: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M4 19V10M10 19V5M16 19v-7M21 19H3" />
    </svg>
  ),
  box: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M3 8l9-4 9 4-9 4-9-4z" />
      <path d="M3 8v8l9 4 9-4V8" />
      <path d="M12 12v9" />
    </svg>
  ),
  truck: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <rect x="2" y="8" width="11" height="8" rx="1" />
      <path d="M13 11h4l3 3v2h-7" />
      <circle cx="7" cy="18.2" r="1.6" />
      <circle cx="17" cy="18.2" r="1.6" />
    </svg>
  ),
  cart: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <circle cx="9" cy="19" r="1.4" />
      <circle cx="17" cy="19" r="1.4" />
      <path d="M3 4h2l2.2 11.2a2 2 0 0 0 2 1.6h7.6a2 2 0 0 0 2-1.6L21 8H6" />
    </svg>
  ),
  bag: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M6 8h12l-1 12a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L6 8z" />
      <path d="M9 8V6a3 3 0 0 1 6 0v2" />
    </svg>
  ),
  coins: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <circle cx="9" cy="10" r="5.5" />
      <circle cx="15" cy="15" r="5.5" />
    </svg>
  ),
  users: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.3 19c.7-3.4 3-5 5.7-5s5 1.6 5.7 5" />
      <circle cx="17.5" cy="9" r="2.6" />
      <path d="M15.6 14c2.5.4 4 1.9 4.6 5" />
    </svg>
  ),
  globe: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17" />
      <path d="M12 3.5c2.6 2.4 4 5.4 4 8.5s-1.4 6.1-4 8.5c-2.6-2.4-4-5.4-4-8.5s1.4-6.1 4-8.5z" />
    </svg>
  ),
  document: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M7 3h7l4 4v14H7z" />
      <path d="M14 3v4h4" />
      <path d="M9.5 12.5h5M9.5 16h5" />
    </svg>
  ),
  gear: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <circle cx="12" cy="12" r="3.1" />
      <path d="M12 3v2.4M12 18.6V21M21 12h-2.4M5.4 12H3M18.4 5.6l-1.7 1.7M7.3 16.7l-1.7 1.7M18.4 18.4l-1.7-1.7M7.3 7.3 5.6 5.6" />
    </svg>
  ),
  tag: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M3 12 12 3h6a3 3 0 0 1 3 3v6l-9 9a2 2 0 0 1-2.8 0L3 14.8a2 2 0 0 1 0-2.8z" />
      <circle cx="15.5" cy="8.5" r="1.3" />
    </svg>
  ),
  trend: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M3 17l6-6 4 4 8-8" />
      <path d="M15 7h6v6" />
    </svg>
  ),
  banknote: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <rect x="2.3" y="6" width="19.4" height="12" rx="2" />
      <circle cx="12" cy="12" r="2.6" />
      <path d="M6 9h.01M18 15h.01" />
    </svg>
  ),
  chevron: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M9 6l6 6-6 6" />
    </svg>
  ),
};

/** Renders one of the icons above by name; falls back to a generic box so a typo never crashes the page. */
export function Icon({ name, className }) {
  const Shape = ICONS[name] || ICONS.box;
  return <Shape className={className} aria-hidden="true" focusable="false" />;
}

/**
 * Single source of truth for the admin sections, grouped the way the sidebar
 * displays them. Dashboard sits above the groups, ungrouped, since it is the
 * one screen every admin lands on first.
 *
 * `adminTabs` (a flat list) is derived from this below and kept exported
 * unchanged so anything that only needs "every section in order" — the
 * mobile strip, the legacy fallback bar — doesn't need to know about groups.
 */
export const adminDashboardItem = { to: '/admin', label: 'Dashboard', icon: 'dashboard', end: true };

export const adminGroups = [
  {
    key: 'catalog',
    label: 'Catalog',
    icon: 'tag',
    items: [
      { to: '/admin/products', label: 'Produse', icon: 'box' },
      { to: '/admin/suppliers', label: 'Furnizori', icon: 'truck' },
    ],
  },
  {
    key: 'sales',
    label: 'Vânzări',
    icon: 'trend',
    items: [
      { to: '/admin/orders', label: 'Comenzi', icon: 'cart' },
      { to: '/admin/purchases', label: 'Cumpărări', icon: 'bag' },
    ],
  },
  {
    key: 'financial',
    label: 'Financiar',
    icon: 'banknote',
    items: [{ to: '/admin/accounting', label: 'Contabilitate', icon: 'coins' }],
  },
  {
    key: 'system',
    label: 'Sistem',
    icon: 'gear',
    items: [
      { to: '/admin/users', label: 'Utilizatori', icon: 'users' },
      { to: '/admin/login-events', label: 'Conectări', icon: 'globe' },
      { to: '/admin/audit', label: 'Jurnal', icon: 'document' },
      { to: '/admin/settings', label: 'Date firmă', icon: 'gear' },
    ],
  },
];

export const adminTabs = [adminDashboardItem, ...adminGroups.flatMap((g) => g.items)];

/**
 * True while an admin page is rendered inside AdminLayout, which already draws
 * the permanent navigation rail.
 *
 * Every admin page still renders <AdminNav /> at the top of its markup. Rather
 * than editing all of them, the component reads this flag and renders nothing
 * when the surrounding layout is already showing the navigation — which avoids
 * a duplicated menu while keeping the pages usable on their own.
 */
export const AdminChromeContext = createContext(false);

export default function AdminNav() {
  const handledByLayout = useContext(AdminChromeContext);
  if (handledByLayout) return null;

  return (
    <div className="mb-6 flex flex-wrap gap-2 border-b border-slate-200 pb-3">
      {adminTabs.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          end={t.end}
          className={({ isActive }) =>
            `flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium ${
              isActive ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`
          }
        >
          <Icon name={t.icon} className="h-4 w-4 shrink-0" />
          {t.label}
        </NavLink>
      ))}
    </div>
  );
}
