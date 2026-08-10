import { useCallback, useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { Icon } from './AdminNav';
import { SearchField } from './xxii';
import NotificationBell from './NotificationBell';
import useFavorites from '../hooks/useFavorites';
import useGlobalSearch from '../hooks/useGlobalSearch';

/**
 * The admin navigation rail. Task 3.
 *
 * Collapsible to icons, with pinned favourites, global search and the existing
 * grouped sections.
 *
 * ## Icon-only mode keeps the labels available
 *
 * Collapsing hides the text visually, but every link keeps an accessible name
 * and a title attribute. A rail of unlabelled glyphs is a memory test; keeping
 * the names in the accessibility tree means a screen reader still announces
 * "Produse" and a hovering mouse still gets a tooltip. The collapse is a space
 * decision, not an information decision.
 *
 * ## Favourites sit above the sections, not inside them
 *
 * The point of pinning is to skip the hierarchy. A favourite filed back under
 * its own group would be exactly as far away as the original.
 *
 * ## Pins are on hover and on focus
 *
 * The pin control appears when a row is hovered — a permanently visible pin on
 * every row would double the rail's visual weight for a feature used a handful
 * of times. `focus-within` is what keeps it reachable by keyboard; hover alone
 * would make pinning mouse-only.
 */

/** Where the collapsed state is remembered between visits. */
const COLLAPSE_KEY = 'es_admin_rail_collapsed';

export default function AdminSidebar({
  dashboardItem,
  groups,
  openGroups,
  onToggleGroup,
  railOffset,
}) {
  const [collapsed, setCollapsed] = useState(() => {
    if (typeof window === 'undefined') return false;
    try {
      return window.localStorage.getItem(COLLAPSE_KEY) === '1';
    } catch {
      return false;
    }
  });

  const favorites = useFavorites();
  const search = useGlobalSearch();

  const toggleCollapsed = useCallback(() => {
    setCollapsed((previous) => {
      const next = !previous;
      try {
        window.localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0');
      } catch {
        // Private browsing: the toggle still works this visit.
      }
      return next;
    });
  }, []);

  // Collapsing while the search panel is open would leave a floating result list
  // attached to a field that is no longer there.
  useEffect(() => {
    if (collapsed) search.setOpen(false);
  }, [collapsed, search]);

  const allItems = [
    ...(dashboardItem ? [dashboardItem] : []),
    ...groups.flatMap((group) => group.items),
  ];

  const favoriteItems = favorites.items
    .map((favorite) => allItems.find((item) => item.to === favorite.route))
    .filter(Boolean);

  return (
    <aside
      className={`order-1 hidden lg:order-1 lg:block lg:shrink-0 transition-[width] duration-xxslow
        ease-xx ${collapsed ? 'lg:w-16' : 'lg:w-56'}`}
      style={railOffset ? { marginTop: `${railOffset}px` } : undefined}
    >
      <nav
        aria-label="Secțiuni administrare"
        className="sticky top-20 rounded-[1.25rem] border border-[rgba(255,255,255,0.12)]
          bg-[rgba(9,10,26,0.72)] p-2 shadow-[0_28px_70px_-32px_rgba(0,0,0,0.95),0_0_48px_-18px_rgba(122,60,255,0.55)]
          backdrop-blur-glass-lg"
      >
        <div className={`flex items-center gap-1 pb-2 pt-1 ${collapsed ? 'flex-col px-0' : 'px-3'}`}>
          {!collapsed ? <p className="xx-eyebrow mb-0 flex-1">Control Center</p> : null}
          <NotificationBell />
          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={collapsed ? 'Extinde meniul' : 'Restrânge meniul'}
            aria-pressed={collapsed}
            className="grid h-7 w-7 shrink-0 place-items-center rounded-lg
              text-[color:var(--xx-ink-dim)] transition-colors duration-xx
              hover:text-[color:var(--xx-cyan)]"
          >
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
                 strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"
                 aria-hidden="true">
              <path d={collapsed ? 'M9 6l6 6-6 6' : 'M15 6l-6 6 6 6'} />
            </svg>
          </button>
        </div>

        <div className={collapsed ? 'px-0.5 pb-2' : 'px-1 pb-2'}>
          <SearchField
            collapsed={collapsed}
            query={search.query}
            onQueryChange={search.setQuery}
            results={search.results}
            loading={search.loading}
            open={search.open}
            onOpenChange={search.setOpen}
            onClear={search.clear}
          />
        </div>

        <div className="xx-no-scrollbar max-h-[calc(100vh-11rem)] space-y-1 overflow-y-auto pb-1">
          {favoriteItems.length > 0 ? (
            <>
              {!collapsed ? (
                <p className="px-2.5 pb-1 pt-1 text-[10px] font-semibold uppercase
                  tracking-[0.16em] text-[color:var(--xx-ink-dim)]">
                  Favorite
                </p>
              ) : null}
              {favoriteItems.map((item) => (
                <RailLink
                  key={`fav-${item.to}`}
                  item={item}
                  collapsed={collapsed}
                  favorites={favorites}
                  pinned
                />
              ))}
              <div className="my-1 border-t border-[rgba(255,255,255,0.1)]" />
            </>
          ) : null}

          {dashboardItem ? (
            <>
              <RailLink item={dashboardItem} collapsed={collapsed} favorites={favorites} />
              <div className="my-1 border-t border-[rgba(255,255,255,0.1)]" />
            </>
          ) : null}

          {groups.map((group) => {
            const open = openGroups[group.key] !== false;
            const panelId = `admin-group-${group.key}`;

            // Collapsed, the group headers become separators: a chevron and a
            // label in a 64-pixel rail is unreadable, and the section names are
            // organisational rather than navigational.
            if (collapsed) {
              return (
                <div key={group.key} className="space-y-0.5 py-0.5">
                  <div className="mx-2 border-t border-[rgba(255,255,255,0.08)]" />
                  {group.items.map((item) => (
                    <RailLink key={item.to} item={item} collapsed favorites={favorites} />
                  ))}
                </div>
              );
            }

            return (
              <div key={group.key}>
                <button
                  type="button"
                  onClick={() => onToggleGroup(group.key)}
                  aria-expanded={open}
                  aria-controls={panelId}
                  className="flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-xs
                    font-semibold uppercase tracking-[0.16em] text-[color:var(--xx-ink-dim)]
                    transition-colors duration-xx hover:bg-[rgba(255,255,255,0.05)]
                    hover:text-[color:var(--xx-ink)]"
                >
                  <Icon name={group.icon} className="h-4 w-4 shrink-0 text-[color:var(--xx-purple)]" />
                  <span className="flex-1 text-left">{group.label}</span>
                  <Icon
                    name="chevron"
                    className={`h-3.5 w-3.5 shrink-0 transition-transform duration-xx ease-xx ${
                      open ? 'rotate-90' : ''
                    }`}
                  />
                </button>
                <div
                  id={panelId}
                  className={`grid transition-all duration-xxslow ease-xx ${
                    open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                  }`}
                >
                  <div className="overflow-hidden">
                    <div className="space-y-0.5 py-0.5 pl-1">
                      {group.items.map((item) => (
                        <RailLink key={item.to} item={item} collapsed={false}
                                  favorites={favorites} />
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {favorites.error ? (
          <p className="px-2 pt-1 text-[10px] text-[#ff8a97]">{favorites.error}</p>
        ) : null}
      </nav>
    </aside>
  );
}

/**
 * One navigation row, with its pin control.
 *
 * The pin is a sibling of the link rather than a child: nesting a button inside
 * an anchor is invalid HTML, and browsers resolve it by making the pin activate
 * the link — which would navigate away every time somebody tried to pin
 * something.
 */
function RailLink({ item, collapsed, favorites, pinned = false }) {
  const isFavorite = favorites.isFavorite(item.to);

  const linkClass = ({ isActive }) =>
    `flex min-w-0 flex-1 items-center gap-2.5 rounded-lg border-l-2 py-2 pr-2 text-sm
     font-medium transition-all duration-xx ease-xx ${collapsed ? 'justify-center pl-1.5' : 'pl-2.5'} ${
      isActive
        ? 'border-[color:var(--xx-cyan)] bg-[rgba(34,232,245,0.1)] text-[color:var(--xx-ink)] shadow-[inset_0_0_24px_-10px_rgba(34,232,245,0.65)]'
        : 'border-transparent text-[color:var(--xx-ink-muted)] hover:border-[rgba(122,60,255,0.55)] hover:bg-[rgba(255,255,255,0.05)] hover:text-[color:var(--xx-ink)]'
    }`;

  return (
    <div className="group/row flex items-center focus-within:bg-[rgba(255,255,255,0.03)]
      rounded-lg">
      <NavLink to={item.to} end={item.end} className={linkClass} title={collapsed ? item.label : undefined}>
        <Icon name={item.icon} className="h-4 w-4 shrink-0" />
        {/* Collapsed, the label leaves the layout but stays in the accessibility
            tree. A rail of unlabelled glyphs is a memory test for sighted users
            and unusable for everyone else. */}
        <span className={collapsed ? 'sr-only' : 'truncate'}>{item.label}</span>
      </NavLink>

      {!collapsed ? (
        <button
          type="button"
          onClick={() => favorites.toggle(item.to, item.label, item.icon)}
          disabled={!isFavorite && favorites.full}
          aria-pressed={isFavorite}
          aria-label={isFavorite ? `Elimină ${item.label} din favorite` : `Fixează ${item.label}`}
          title={
            !isFavorite && favorites.full
              ? `Ai atins limita de ${favorites.max} favorite`
              : isFavorite
              ? 'Elimină din favorite'
              : 'Fixează în favorite'
          }
          className={`mr-1 grid h-6 w-6 shrink-0 place-items-center rounded transition-all
            duration-xx focus:outline-none focus-visible:ring-1
            focus-visible:ring-[color:var(--xx-cyan)] disabled:opacity-30 ${
              isFavorite || pinned
                ? 'text-[color:var(--xx-cyan)] opacity-100'
                : 'text-[color:var(--xx-ink-dim)] opacity-0 group-hover/row:opacity-100 focus-visible:opacity-100'
            }`}
        >
          <svg viewBox="0 0 24 24" className="h-3.5 w-3.5"
               fill={isFavorite ? 'currentColor' : 'none'} stroke="currentColor"
               strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 3.6l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z" />
          </svg>
        </button>
      ) : null}
    </div>
  );
}
