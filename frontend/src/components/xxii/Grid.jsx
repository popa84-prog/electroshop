/**
 * XXII — TASK 9 (modular 12-column grid, spacing 24–32px).
 *
 * The layout contract for the whole site:
 *
 *   mobile   →  1 column   (each module is full width and airy)
 *   tablet   →  6 columns
 *   desktop  → 12 columns  (the modular grid from the brief)
 *   TV       →  6 columns  (fewer, larger modules — legible across a room)
 *
 * `Grid12` owns the tracks; `Module` owns a cell's span. They are separate
 * components because a module must be movable between grids without carrying
 * grid definitions with it — that is what makes sections rearrangeable, which
 * is the whole point of TASK 9.
 *
 * Spans are looked up in a static map rather than interpolated into a class
 * string. Tailwind extracts class names by scanning source text, so a computed
 * `col-span-${n}` would never be generated. Every span that exists in the map
 * exists in the compiled stylesheet.
 */

const GAPS = {
  sm: 'gap-4',
  md: 'gap-6', // 24px — the brief's lower bound
  lg: 'gap-6 lg:gap-8', // 32px on desktop — the upper bound
};

/** Desktop spans (out of 12). Mobile is always full width. */
const SPANS = {
  1: 'lg:col-span-1',
  2: 'lg:col-span-2',
  3: 'lg:col-span-3',
  4: 'lg:col-span-4',
  5: 'lg:col-span-5',
  6: 'lg:col-span-6',
  7: 'lg:col-span-7',
  8: 'lg:col-span-8',
  9: 'lg:col-span-9',
  10: 'lg:col-span-10',
  11: 'lg:col-span-11',
  12: 'lg:col-span-12',
};

/** Tablet spans (out of 6). */
const SPANS_SM = {
  1: 'sm:col-span-1',
  2: 'sm:col-span-2',
  3: 'sm:col-span-3',
  4: 'sm:col-span-4',
  5: 'sm:col-span-5',
  6: 'sm:col-span-6',
};

/** TV spans (out of 6) — a 12-col desktop span halves onto the TV grid. */
const SPANS_TV = {
  1: 'tv:col-span-1',
  2: 'tv:col-span-2',
  3: 'tv:col-span-3',
  4: 'tv:col-span-4',
  5: 'tv:col-span-5',
  6: 'tv:col-span-6',
};

const STARTS = {
  1: 'lg:col-start-1',
  2: 'lg:col-start-2',
  3: 'lg:col-start-3',
  4: 'lg:col-start-4',
  5: 'lg:col-start-5',
  6: 'lg:col-start-6',
  7: 'lg:col-start-7',
  8: 'lg:col-start-8',
  9: 'lg:col-start-9',
  10: 'lg:col-start-10',
  11: 'lg:col-start-11',
  12: 'lg:col-start-12',
};

export function Grid12({ as: Tag = 'div', gap = 'md', className = '', children, ...rest }) {
  return (
    <Tag
      className={`grid grid-cols-1 sm:grid-cols-6 lg:grid-cols-12 tv:grid-cols-6 ${
        GAPS[gap] || GAPS.md
      } ${className}`}
      {...rest}
    >
      {children}
    </Tag>
  );
}

/**
 * One cell of the modular grid.
 *
 * `span` is the desktop span out of 12. `spanSm` defaults to the tablet
 * equivalent (half, rounded up, clamped to 6) and `spanTv` to the TV
 * equivalent, so callers normally pass a single number and still get sensible
 * behaviour on all four breakpoints.
 */
export function Module({
  as: Tag = 'div',
  span = 12,
  spanSm,
  spanTv,
  start,
  className = '',
  children,
  ...rest
}) {
  const desktop = SPANS[span] || SPANS[12];
  const tabletSpan = spanSm ?? Math.min(6, Math.max(1, Math.ceil(span / 2)));
  const tvSpan = spanTv ?? Math.min(6, Math.max(1, Math.ceil(span / 2)));

  const classes = [
    'col-span-1',
    SPANS_SM[tabletSpan] || SPANS_SM[6],
    desktop,
    SPANS_TV[tvSpan] || SPANS_TV[6],
    start ? STARTS[start] : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <Tag className={classes} {...rest}>
      {children}
    </Tag>
  );
}

/**
 * The page shell. Every route renders inside one of these so the site keeps a
 * single max-width and a single horizontal rhythm; `wide` releases the cap for
 * dashboards and tables that genuinely need the room.
 */
export function Section({ as: Tag = 'section', wide = false, tight = false, className = '', children, ...rest }) {
  return (
    <Tag
      className={`mx-auto w-full px-4 sm:px-6 ${wide ? 'max-w-[1920px]' : 'max-w-[1680px]'} ${
        tight ? 'py-6' : 'py-10 sm:py-14'
      } ${className}`}
      {...rest}
    >
      {children}
    </Tag>
  );
}

export default Grid12;
