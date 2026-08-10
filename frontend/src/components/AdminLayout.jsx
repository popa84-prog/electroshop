import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { AdminChromeContext, Icon, adminGroups, filterNavForRoles } from './AdminNav';
import { useAuth } from '../context/AuthContext';
import AdminSidebar from './AdminSidebar';
import ErrorBoundary from './ErrorBoundary';
import NotificationBell from './NotificationBell';
import { Breadcrumbs } from './xxii';
import useBreadcrumbs from '../hooks/useBreadcrumbs';

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
  // TASK 3 — the trail is derived from the route, so every admin page gets one
  // without having to declare it.
  const crumbs = useBreadcrumbs();

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

  // The desktop rail's link styling moved into AdminSidebar together with the
  // rail itself. The mobile strip keeps its own, because a horizontally
  // scrolling chip and a vertical rail row are different shapes and sharing one
  // class string between them was already a compromise.
  const mobileLinkClass = ({ isActive }) =>
    `flex items-center gap-1.5 whitespace-nowrap rounded-lg border px-3 py-2 text-sm font-medium transition-all duration-xx ease-xx ${
      isActive
        ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-ink)] shadow-[0_0_28px_-10px_rgba(34,232,245,0.8)]'
        : 'border-[rgba(255,255,255,0.1)] text-[color:var(--xx-ink-muted)] hover:border-[rgba(122,60,255,0.5)] hover:text-[color:var(--xx-ink)]'
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
          {/* Above the page, not inside it: every admin screen gets the trail
              without each one having to render it, and the position stays
              identical from page to page — which is the only reason a
              breadcrumb is useful at all. */}
          {crumbs.length > 1 ? <Breadcrumbs items={crumbs} className="mb-3" /> : null}
          <ErrorBoundary key={location.pathname}>
            <Outlet />
          </ErrorBoundary>
        </div>

        {/* Persistent side rail — large screens, left-hand column.
            TASK 3: collapsible to icons, with favourites and global search. The
            markup moved into its own component because the rail now carries
            four concerns and the layout file was already doing measurement,
            group state and mobile navigation. */}
        <AdminSidebar
          dashboardItem={dashboardItem}
          groups={groups}
          openGroups={openGroups}
          onToggleGroup={toggleGroup}
          railOffset={railOffset}
        />

        {/* Scrollable strip — small screens */}
        <nav
          aria-label="Secțiuni administrare"
          className="order-1 -mx-4 flex items-center gap-2 border-b border-[rgba(255,255,255,0.1)] px-4 pb-3 lg:hidden"
        >
          <div className="xx-no-scrollbar flex flex-1 gap-2 overflow-x-auto">
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
