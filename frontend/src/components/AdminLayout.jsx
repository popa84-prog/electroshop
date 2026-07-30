import { NavLink, Outlet } from 'react-router-dom';
import { AdminChromeContext, adminTabs } from './AdminNav';

/**
 * Chrome shared by every admin screen.
 *
 * The section menu lives in a rail pinned to the right-hand side of the page and
 * stays on screen while the content scrolls, so switching between Dashboard,
 * Produse, Utilizatori and the rest never requires going back to a landing page.
 * Below the large breakpoint the rail becomes a horizontally scrollable strip
 * above the content, because a fixed side column would leave too little room for
 * the data tables on a phone.
 */
export default function AdminLayout() {
  const linkClass = ({ isActive }) =>
    `flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition ${
      isActive
        ? 'bg-brand-600 text-white shadow-sm'
        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`;

  const mobileLinkClass = ({ isActive }) =>
    `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition ${
      isActive ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'
    }`;

  return (
    <AdminChromeContext.Provider value={true}>
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        {/* Content first in the DOM so keyboard and screen-reader users reach it
            before the ten navigation links. */}
        <div className="order-2 min-w-0 flex-1 lg:order-1">
          <Outlet />
        </div>

        {/* Persistent side rail — large screens */}
        <aside className="order-1 hidden lg:order-2 lg:block lg:w-52 lg:shrink-0">
          <nav
            aria-label="Secțiuni administrare"
            className="sticky top-20 rounded-xl border border-slate-200 bg-white p-2 shadow-sm"
          >
            <p className="px-3 pb-2 pt-1 text-xs font-semibold uppercase tracking-wide text-slate-400">
              Administrare
            </p>
            <div className="max-h-[calc(100vh-8rem)] space-y-0.5 overflow-y-auto">
              {adminTabs.map((t) => (
                <NavLink key={t.to} to={t.to} end={t.end} className={linkClass}>
                  <span aria-hidden="true">{t.icon}</span>
                  <span>{t.label}</span>
                </NavLink>
              ))}
            </div>
          </nav>
        </aside>

        {/* Scrollable strip — small screens */}
        <nav
          aria-label="Secțiuni administrare"
          className="order-1 -mx-4 overflow-x-auto border-b border-slate-200 px-4 pb-3 lg:hidden"
        >
          <div className="flex gap-2">
            {adminTabs.map((t) => (
              <NavLink key={t.to} to={t.to} end={t.end} className={mobileLinkClass}>
                <span aria-hidden="true" className="mr-1">
                  {t.icon}
                </span>
                {t.label}
              </NavLink>
            ))}
          </div>
        </nav>
      </div>
    </AdminChromeContext.Provider>
  );
}
