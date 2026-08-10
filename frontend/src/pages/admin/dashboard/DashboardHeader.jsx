import { Link } from 'react-router-dom';

/**
 * The dashboard's header: title and quick shortcuts. Task 1.
 *
 * The requirement names three destinations — Produse, Comenzi, Promoții — and
 * they are the three an operator leaves the dashboard for. The shortcuts are
 * therefore a fixed row rather than something derived: this is the top of the
 * page, and a set of buttons that changes position between visits is a set of
 * buttons nobody develops muscle memory for. (The productivity panel does derive
 * its shortcuts from usage, which is the right behaviour for a list further down
 * the page that the operator reads rather than aims at.)
 *
 * Each shortcut is filtered by permission, so an Editor is not shown a link to a
 * page that will refuse them.
 */
export default function DashboardHeader({ hasPermission, compact = false, actions = null }) {
  const shortcuts = [
    {
      to: '/admin/products',
      label: 'Produse',
      permission: 'PRODUCTS_VIEW',
      icon: <BoxIcon />,
      accent: '#2e7bff',
    },
    {
      to: '/admin/orders',
      label: 'Comenzi',
      permission: 'ORDERS_VIEW',
      icon: <CartIcon />,
      accent: '#d032b8',
    },
    {
      to: '/admin/offers',
      label: 'Promoții',
      permission: 'OFFERS_MANAGE',
      icon: <TagIcon />,
      accent: '#b08c09',
    },
  ].filter((item) => !item.permission || hasPermission(item.permission));

  return (
    <header className={`flex flex-wrap items-end justify-between gap-3 ${compact ? 'mb-4' : 'mb-6'}`}>
      <div className="min-w-0">
        <p className="xx-eyebrow mb-1">Control Center</p>
        <h1 className={`font-display font-semibold text-[color:var(--xx-ink)] ${
          compact ? 'text-xl' : 'text-2xl sm:text-3xl'
        }`}>
          Panou de bord
        </h1>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <nav aria-label="Scurtături" className="flex flex-wrap items-center gap-2">
          {shortcuts.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className="group inline-flex items-center gap-2 rounded-lg border
                border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.03)] px-3 py-2
                text-sm font-medium text-[color:var(--xx-ink-dim)] transition-all duration-xx
                ease-xx hover:border-[rgba(255,255,255,0.28)] hover:text-[color:var(--xx-ink)]
                focus:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--xx-cyan)]"
            >
              <span
                className="transition-colors duration-xx"
                style={{ color: item.accent }}
                aria-hidden="true"
              >
                {item.icon}
              </span>
              {item.label}
            </Link>
          ))}
        </nav>

        {actions}
      </div>
    </header>
  );
}

function BoxIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 8l9-4 9 4-9 4-9-4z" />
      <path d="M3 8v8l9 4 9-4V8" />
    </svg>
  );
}

function CartIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="19" r="1.3" />
      <circle cx="17" cy="19" r="1.3" />
      <path d="M3 4h2l2.2 11.2a2 2 0 002 1.6h7.6a2 2 0 002-1.6L21 8H6" />
    </svg>
  );
}

function TagIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 12V4h8l9 9-8 8-9-9z" />
      <circle cx="7.5" cy="7.5" r="1.3" />
    </svg>
  );
}
