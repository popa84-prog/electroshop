import { NavLink } from 'react-router-dom';

const tabs = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/products', label: 'Produse' },
  { to: '/admin/users', label: 'Utilizatori' },
  { to: '/admin/orders', label: 'Comenzi' },
  { to: '/admin/suppliers', label: 'Furnizori' },
  { to: '/admin/purchases', label: 'Cumpărări' },
  { to: '/admin/accounting', label: 'Contabilitate' },
];

export default function AdminNav() {
  return (
    <div className="mb-6 flex flex-wrap gap-2 border-b border-slate-200 pb-3">
      {tabs.map((t) => (
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
