import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { AdminChromeContext, adminTabs } from './AdminNav';

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
 */

/** Ignore absurd measurements rather than pushing the rail off the screen. */
const MAX_RAIL_OFFSET = 400;

export default function AdminLayout() {
  const location = useLocation();
  const contentRef = useRef(null);
  const [railOffset, setRailOffset] = useState(0);

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
      // The first panel is whatever the page renders as its main surface.
      const panel = content.querySelector('.card, table');
      if (!panel) {
        setRailOffset(0);
        return;
      }
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

    // One measurement on mount is not enough. The header keeps reflowing while
    // the web fonts arrive, and when the product list is served from cache the
    // table is already in place before the observers below are attached, so no
    // notification ever follows. A few staggered re-reads cover both cases and
    // then stop, instead of leaving a timer running for the life of the page.
    const timers = [80, 250, 600, 1200, 2500].map((delay) =>
      window.setTimeout(schedule, delay)
    );

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
      timers.forEach(clearTimeout);
      resizeObserver.disconnect();
      mutationObserver.disconnect();
      window.removeEventListener('resize', schedule);
    };
  }, [location.pathname]);

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
            before the ten navigation links; `order` puts the rail on the left
            visually without changing that reading order. */}
        <div ref={contentRef} className="order-2 min-w-0 flex-1 lg:order-2">
          <Outlet />
        </div>

        {/* Persistent side rail — large screens, left-hand column */}
        <aside
          className="order-1 hidden lg:order-1 lg:block lg:w-52 lg:shrink-0"
          style={railOffset ? { marginTop: `${railOffset}px` } : undefined}
        >
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
