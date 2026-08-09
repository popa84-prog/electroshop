/**
 * XXII — the modern icon set for the dashboard. Tasks 1 and 9.
 *
 * ## Why this is a local set rather than the `lucide-react` package
 *
 * The requirement names Lucide or HeroIcons. This ships the Lucide *design* —
 * its grid, its stroke weight, its cap and join style — without the dependency,
 * and the reason is a constraint the codebase already documented before this
 * work started. `AdminNav.jsx` carries a hand-drawn set with the note that "this
 * project has no working package install in some environments", and that turned
 * out to be exactly right: in the environment this was built in, `npm install`
 * answers 403 for every package, so a dependency could not be installed, could
 * not be verified, and could not be tested before deployment.
 *
 * The trade is worth stating precisely. Adding `lucide-react` would give a
 * thousand icons instead of twenty-eight and let a future card pick one without
 * touching this file. Not adding it means the frontend build has no new
 * failure mode on the path that every other admin feature depends on — and a
 * failed install does not degrade the dashboard, it removes the entire admin
 * panel. Twenty-eight icons cover every card built here; an unbuildable panel
 * covers none.
 *
 * ## The drawing conventions, so a new icon matches without guessing
 *
 * Lucide's own specification: a 24×24 viewBox, 2px stroke, round caps and round
 * joins, no fills, geometry kept on whole or half pixels. Every path below
 * follows it, which is why these sit beside the existing `AdminNav` glyphs
 * without looking like a second set.
 *
 * Size and colour come from the caller — `className` sets both, `currentColor`
 * inherits the text colour — so an icon never carries its own palette. That is
 * what lets the same glyph read correctly on a card accent, in a muted footnote
 * and inside a danger badge.
 */

/** Every icon, keyed by name. Each entry is the inner geometry of a 24×24 box. */
const PATHS = {
  // ---- Business ----
  box: <><path d="M3 8l9-4 9 4-9 4-9-4z" /><path d="M3 8v8l9 4 9-4V8" /><path d="M12 12v9" /></>,
  cart: <><circle cx="9" cy="19" r="1.4" /><circle cx="17" cy="19" r="1.4" /><path d="M3 4h2l2.2 11.2a2 2 0 002 1.6h7.6a2 2 0 002-1.6L21 8H6" /></>,
  coins: <><circle cx="9" cy="10" r="5.5" /><circle cx="15" cy="15" r="5.5" /></>,
  percent: <><path d="M19 5L5 19" /><circle cx="7.5" cy="7.5" r="2.5" /><circle cx="16.5" cy="16.5" r="2.5" /></>,
  tag: <><path d="M3 12V4h8l9 9-8 8-9-9z" /><circle cx="7.5" cy="7.5" r="1.3" /></>,
  truck: <><rect x="2" y="8" width="11" height="8" rx="1" /><path d="M13 11h4l3 3v2h-7" /><circle cx="7" cy="18.2" r="1.6" /><circle cx="17" cy="18.2" r="1.6" /></>,

  // ---- Charts and trends ----
  trendingUp: <><path d="M3 17l6-6 4 4 8-8" /><path d="M15 7h6v6" /></>,
  trendingDown: <><path d="M3 7l6 6 4-4 8 8" /><path d="M15 17h6v-6" /></>,
  barChart: <><path d="M4 19V10M10 19V5M16 19v-7M21 19H3" /></>,
  pieChart: <><path d="M12 3a9 9 0 109 9h-9z" /><path d="M12 3v9h9" opacity="0.55" /></>,
  activity: <><path d="M3 12h4l3 8 4-16 3 8h4" /></>,

  // ---- Inventory and state ----
  alert: <><path d="M12 4l9 15.5H3z" /><path d="M12 10v4" /><path d="M12 17.2v.1" /></>,
  check: <><path d="M4.5 12.5l5 5 10-11" /></>,
  clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  refresh: <><path d="M3.5 12a8.5 8.5 0 112.6 6.1" /><path d="M3 20v-5h5" /></>,
  package: <><path d="M3 7l9-4 9 4v10l-9 4-9-4z" /><path d="M3 7l9 4 9-4" /><path d="M12 11v10" /></>,

  // ---- People ----
  users: <><circle cx="9" cy="8" r="3.2" /><path d="M3.3 19c.7-3.4 3-5 5.7-5s5 1.6 5.7 5" /><circle cx="17.5" cy="9" r="2.6" /><path d="M15.6 14c2.5.4 4 1.9 4.6 5" /></>,
  user: <><circle cx="12" cy="8" r="3.5" /><path d="M5 20c.8-4 3.6-6 7-6s6.2 2 7 6" /></>,

  // ---- System ----
  server: <><rect x="3" y="4" width="18" height="7" rx="1.5" /><rect x="3" y="13" width="18" height="7" rx="1.5" /><path d="M7 7.5v.1M7 16.5v.1" /></>,
  cpu: <><rect x="7" y="7" width="10" height="10" rx="1.5" /><path d="M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4" /></>,
  database: <><ellipse cx="12" cy="6" rx="8" ry="3" /><path d="M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6" /><path d="M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6" /></>,
  shield: <><path d="M12 3l8 3v6c0 5-3.4 8-8 9-4.6-1-8-4-8-9V6z" /></>,
  bell: <><path d="M18 9a6 6 0 10-12 0c0 5-2 6-2 6h16s-2-1-2-6" /><path d="M10.5 20a2 2 0 003 0" /></>,

  // ---- Interface ----
  search: <><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" /></>,
  filter: <><path d="M4 5h16l-6 7v6l-4 2v-8z" /></>,
  download: <><path d="M12 3v12" /><path d="M8 11l4 4 4-4" /><path d="M4 19h16" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M12 2.5v3M12 18.5v3M21.5 12h-3M5.5 12h-3M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1M18.7 18.7l-2.1-2.1M7.4 7.4L5.3 5.3" /></>,
  sparkles: <><path d="M12 3l1.8 4.2L18 9l-4.2 1.8L12 15l-1.8-4.2L6 9l4.2-1.8z" /><path d="M18.5 15.5l.9 2.1 2.1.9-2.1.9-.9 2.1-.9-2.1-2.1-.9 2.1-.9z" /></>,
};

/** Names a caller can pass, for anyone adding a card. */
export const lucideIconNames = Object.keys(PATHS);

/**
 * Renders one icon.
 *
 * @param {string} name one of {@link lucideIconNames}
 * @param {string} className sets both size and colour
 */
export default function LucideIcon({ name, className = 'h-4 w-4', title = null, ...rest }) {
  const geometry = PATHS[name];

  // An unknown name renders nothing rather than a placeholder glyph. A question
  // mark in the middle of a card looks like a state the card is reporting, and
  // somebody would try to interpret it; empty space reads as a missing icon,
  // which is what it is.
  if (!geometry) return null;

  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      // Decorative unless the caller gives it a title. Almost every icon here
      // sits beside its own label, and announcing both means hearing the same
      // word twice.
      aria-hidden={title ? undefined : 'true'}
      role={title ? 'img' : undefined}
      {...rest}
    >
      {title ? <title>{title}</title> : null}
      {geometry}
    </svg>
  );
}
