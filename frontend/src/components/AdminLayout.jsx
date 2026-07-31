import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { AdminChromeContext, Icon, adminGroups, filterNavForRoles } from './AdminNav';
import { useAuth } from '../context/AuthContext';
import ErrorBoundary from './ErrorBoundary';
import NotificationBell from './NotificationBell';

/**
 * Chrome shared by every admin screen.
 *
 * The section menu lives in a rail pinned to the left-hand side of the page and
 * stays on screen while the content scrolls, so switching between Dashboard,
 * Produse, Utilizatori and the rest never requires going back to a landing page.
 * Below the large breakpoint the rail becomes a horizontally scrollable strip
 * above the content, because a fixed side column would leave too little room for
 * the data tables on a phone.
 *
 * The rail is pushed down so its top edge lines up with the first content panel
 * (the products table, the statistics cards, and so on) rather than with the
 * page title. That offset cannot be a fixed value: every admin page has a
 * different header — some carry a subtitle, some a toolbar — so it is measured
 * from the rendered page instead of guessed.
 *
 * The rail's sections (Catalog, Vânzări, Financiar, Sistem) are individually
 * collapsible. Which ones are open is remembered in localStorage so it survives
 * navigating between pages and reopening the site later.
 */

/** Ignore absurd measurements rather than pushing the rail off the screen. */
const MAX_RAIL_OFFSET = 400;

/** How long after a page opens the offset keeps being re-read, and how often. */
const SETTLE_WINDOW_MS = 8000;
const SETTLE_TICK_MS = 200;

/** Where the open/closed state of each rail section is remembered between visits. */
const GROUPS_STORAGE_KEY = 'es_admin_nav_groups';

/** Every group starts open the first time an admin ever loads the panel. */
function defaultOpenGroups() {
  return Object.fromEntries(adminGroups.map((g) => [g.key, true]));
}

/** Reads the saved open/closed state, filling in any group that isn't in it yet. */
function readStoredGroups() {
  const defaults = defaultOpenGroups();
  if (typeof window === 'undefined') return defaults;
  try {
    const saved = JSON.parse(window.localStorage.getItem(GROUPS_STORAGE_KEY));
    return saved && typeof saved === 'object' ? { ...defaults, ...saved } : defaults;
  } catch {
    return defaults;
  }
}

export default function AdminLayout() {
  const location = useLocation();
  const contentRef = useRef(null);
  const [railOffset, setRailOffset] = useState(0);
  const [openGroups, setOpenGroups] = useState(readStoredGroups);
  const { user, hasPermission } = useAuth();
  // Feature #6: Manager/Editor only see the sections their permissions allow.
  const { dashboardItem, groups, tabs } = filterNavForRoles(user?.roles, hasPermission);

  const toggleGroup = (key) => {
    setOpenGroups((prev) => {
      const next = { ...prev, [key]: !prev[key] };
      try {
        window.localStorage.setItem(GROUPS_STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Private-browsing / storage-full: the toggle still works this visit,
        // it just won't be remembered next time.
      }
      return next;
    });
  };

  useEffect(() => {
    const content = contentRef.current;
    if (!content) return undefined;

    let frame = 0;

    const measure = () => {
      // Only the two-column layout needs the offset; the mobile strip sits
      // above the content and must stay flush with it.
      if (window.innerWidth < 1024) {
        setRailOffset(0);
        return;
      }
      // The first panel is whatever the page renders as its main surface. While
      // a page is fetching there is none, and the offset is simply left alone:
      // resetting it would make the rail jump to the top and back on every
      // reload, and the next measurement is only a tick away anyway.
      const panel = content.querySelector('.card, table');
      if (!panel) return;
      const delta = panel.getBoundingClientRect().top - content.getBoundingClientRect().top;
      setRailOffset(delta > 0 && delta < MAX_RAIL_OFFSET ? Math.round(delta) : 0);
    };

    // Measuring on the next frame collapses the burst of notifications that
    // arrives while a page settles into a single layout read.
    const schedule = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(measure);
    };

    schedule();

    // One measurement on mount is not enough, and the observers below are not
    // enough either: whether a notification arrives at all depends on when the
    // list request resolves relative to the mount, so on a warm cache the page
    // can settle without a single one firing. Re-reading on a short tick for
    // the first few seconds makes the result independent of that race. The
    // ticker stops on its own; the observers keep the rail correct afterwards.
    const settleUntil = performance.now() + SETTLE_WINDOW_MS;
    const ticker = window.setInterval(() => {
      if (performance.now() > settleUntil) {
        window.clearInterval(ticker);
        return;
      }
      schedule();
    }, SETTLE_TICK_MS);

    // Three things move the first panel: the window changing size, the header
    // reflowing as filters wrap, and — the common case — the table replacing
    // its loading placeholder once the rows arrive. The last one is a subtree
    // change rather than a resize of the content box, so it needs its own
    // observer; without it the rail would keep the offset it had while the
    // page was still empty.
    const resizeObserver = new ResizeObserver(schedule);
    resizeObserver.observe(content);
    const mutationObserver = new MutationObserver(schedule);
    mutationObserver.observe(content, { childList: true, subtree: true });
    window.addEventListener('resize', schedule);

    return () => {
      cancelAnimationFrame(frame);
      window.clearInterval(ticker);
      resizeObserver.disconnect();
      mutationObserver.disconnect();
      window.removeEventListener('resize', schedule);
    };
  }, [location.pathname]);

  // A left accent bar rather than a solid fill reads as "current section"
  // without competing with the icon or label for attention.
  const linkClass = ({ isActive }) =>
    `flex items-center gap-2.5 rounded-lg border-l-2 py-2 pl-2.5 pr-3 text-sm font-medium transition ${
      isActive
        ? 'border-brand-600 bg-brand-50 text-brand-700'
        : 'border-transparent text-slate-600 hover:border-slate-200 hover:bg-slate-50 hover:text-slate-900'
    }`;

  const mobileLinkClass = ({ isActive }) =>
    `flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition ${
      isActive ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'
    }`;

  return (
    <AdminChromeContext.Provider value={true}>
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        {/* Content first in the DOM so keyboard and screen-reader users reach it
            before the navigation links; `order` puts the rail on the left
            visually without changing that reading order. */}
        <div ref={contentRef} className="order-2 min-w-0 flex-1 lg:order-2">
          {/* Feature #7: a crash rendering one admin page must not blank the
              whole panel — the rail stays usable and the operator can navigate
              away. Keyed by pathname so switching pages clears a stale error. */}
          <ErrorBoundary key={location.pathname}>
            <Outlet />
          </ErrorBoundary>
        </div>

        {/* Persistent side rail — large screens, left-hand column */}
        <aside
          className="order-1 hidden lg:order-1 lg:block lg:w-56 lg:shrink-0"
          style={railOffset ? { marginTop: `${railOffset}px` } : undefined}
        >
          <nav
            aria-label="Secțiuni administrare"
            className="sticky top-20 rounded-xl border border-slate-200 bg-white p-2 shadow-sm"
          >
            <div className="flex items-center justify-between px-3 pb-2 pt-1">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Administrare</p>
              {/* Feature #8 — notification bell lives here since the panel has no top header bar. */}
              <NotificationBell />
            </div>
            <div className="max-h-[calc(100vh-8rem)] space-y-1 overflow-y-auto pb-1">
              {dashboardItem && (
                <>
                  <NavLink to={dashboardItem.to} end={dashboardItem.end} className={linkClass}>
                    <Icon name={dashboardItem.icon} className="h-4 w-4 shrink-0" />
                    <span>{dashboardItem.label}</span>
                  </NavLink>
                  <div className="my-1 border-t border-slate-100" />
                </>
              )}

              {groups.map((g) => {
                const open = openGroups[g.key] !== false;
                const panelId = `admin-group-${g.key}`;
                return (
                  <div key={g.key}>
                    <button
                      type="button"
                      onClick={() => toggleGroup(g.key)}
                      aria-expanded={open}
                      aria-controls={panelId}
                      className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 transition hover:bg-slate-50 hover:text-slate-700"
                    >
                      <Icon name={g.icon} className="h-4 w-4 shrink-0 text-slate-400" />
                      <span className="flex-1 text-left">{g.label}</span>
                      <Icon
                        name="chevron"
                        className={`h-3.5 w-3.5 shrink-0 text-slate-400 transition-transform duration-200 ${
                          open ? 'rotate-90' : ''
                        }`}
                      />
                    </button>
                    <div
                      id={panelId}
                      className={`grid transition-all duration-200 ease-out ${
                        open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                      }`}
                    >
                      <div className="overflow-hidden">
                        <div className="space-y-0.5 py-0.5 pl-1">
                          {g.items.map((t) => (
                            <NavLink key={t.to} to={t.to} end={t.end} className={linkClass}>
                              <Icon name={t.icon} className="h-4 w-4 shrink-0" />
                              <span>{t.label}</span>
                            </NavLink>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </nav>
        </aside>

        {/* Scrollable strip — small screens */}
        <nav
          aria-label="Secțiuni administrare"
          className="order-1 -mx-4 flex items-center gap-2 border-b border-slate-200 px-4 pb-3 lg:hidden"
        >
          <div className="flex flex-1 gap-2 overflow-x-auto">
            {tabs.map((t) => (
              <NavLink key={t.to} to={t.to} end={t.end} className={mobileLinkClass}>
                <Icon name={t.icon} className="h-4 w-4 shrink-0" />
                {t.label}
              </NavLink>
            ))}
          </div>
          <div className="shrink-0">
            <NotificationBell />
          </div>
        </nav>
      </div>
    </AdminChromeContext.Provider>
  );
}
