import { createContext, useContext } from 'react';
import { NavLink } from 'react-router-dom';

/**
 * Single source of truth for the admin sections. Both the persistent side rail
 * (AdminLayout) and the legacy inline tab bar below read from this list, so a
 * new section only ever has to be declared once.
 */
export const adminTabs = [
  { to: '/admin', label: 'Dashboard', icon: '📊', end: true },
  { to: '/admin/products', label: 'Produse', icon: '📦' },
  { to: '/admin/users', label: 'Utilizatori', icon: '👥' },
  { to: '/admin/orders', label: 'Comenzi', icon: '🧾' },
  { to: '/admin/suppliers', label: 'Furnizori', icon: '🏭' },
  { to: '/admin/purchases', label: 'Cumpărări', icon: '🛒' },
  { to: '/admin/accounting', label: 'Contabilitate', icon: '💰' },
  { to: '/admin/audit', label: 'Jurnal', icon: '📝' },
  { to: '/admin/login-events', label: 'Conectări', icon: '🌐' },
  { to: '/admin/settings', label: 'Date firmă', icon: '⚙️' },
];

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
            `rounded-lg px-4 py-2 text-sm font-medium ${
              isActive ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`
          }
        >
          {t.label}
        </NavLink>
      ))}
    </div>
  );
}
