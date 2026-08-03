/**
 * XXII — TASK 1 (geometric iconography).
 *
 * A single-stroke icon set built on three rules taken from the brief:
 *   1. Thin lines — a constant 1.4 stroke on a 24×24 canvas.
 *   2. 45° angles — every diagonal in the set is exactly 45°, never an
 *      arbitrary slope, which is what gives the family its technical read.
 *   3. Neon accents — an optional second path (`accent`) drawn in the accent
 *      colour on top of the outline, so an icon can carry a highlight without
 *      needing a second file.
 *
 * The set is inline SVG rather than an icon package: this project has no
 * reliable package install in every environment, and inline paths let the
 * accent stroke inherit a CSS variable.
 */

const STROKE = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.4,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
};

/**
 * Each entry is a function receiving the accent colour and returning the paths.
 * Splitting outline from accent keeps the neon highlight optional per usage.
 */
const SHAPES = {
  // --- Navigation & structure ---
  home: (a) => (
    <>
      <path d="M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z" {...STROKE} />
      <path d="M12 4 4 11" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  grid: (a) => (
    <>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.4" {...STROKE} />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.4" {...STROKE} />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.4" {...STROKE} />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.4" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  cart: (a) => (
    <>
      <path d="M3 4h2.2l2.3 11.2a1.8 1.8 0 0 0 1.8 1.4h7.4a1.8 1.8 0 0 0 1.8-1.4L20.5 8H6.2" {...STROKE} />
      <circle cx="9.5" cy="20" r="1.3" {...STROKE} />
      <circle cx="17" cy="20" r="1.3" {...STROKE} />
      <path d="M13 10v4M11 12h4" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  user: (a) => (
    <>
      <circle cx="12" cy="8.4" r="3.6" {...STROKE} />
      <path d="M4.8 20c.6-4 3.6-6.2 7.2-6.2S18.6 16 19.2 20" {...STROKE} />
      <path d="M8.4 4.8 12 8.4" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  search: (a) => (
    <>
      <circle cx="10.8" cy="10.8" r="6.3" {...STROKE} />
      <path d="m15.4 15.4 4.6 4.6" {...STROKE} stroke={a} strokeWidth="1.9" />
    </>
  ),
  menu: (a) => (
    <>
      <path d="M4 7h16M4 12h16M4 17h10" {...STROKE} />
      <path d="M17 17h3" {...STROKE} stroke={a} strokeWidth="1.9" />
    </>
  ),
  close: (a) => (
    <>
      <path d="M6 6 18 18" {...STROKE} stroke={a} strokeWidth="1.8" />
      <path d="M18 6 6 18" {...STROKE} />
    </>
  ),
  chevron: () => <path d="m9.5 5.5 6.5 6.5-6.5 6.5" {...STROKE} strokeWidth="1.7" />,
  arrow: (a) => (
    <>
      <path d="M4 12h15" {...STROKE} />
      <path d="m13.5 6.5 6 5.5-6 5.5" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),

  // --- Commerce ---
  box: (a) => (
    <>
      <path d="M12 3.2 20.5 7.6v8.8L12 20.8 3.5 16.4V7.6z" {...STROKE} />
      <path d="M3.5 7.6 12 12l8.5-4.4M12 12v8.8" {...STROKE} />
      <path d="M7.8 5.4 16.2 9.8" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  tag: (a) => (
    <>
      <path d="M3.6 12.4 12.4 3.6H19a1.4 1.4 0 0 1 1.4 1.4v6.6l-8.8 8.8a1.5 1.5 0 0 1-2.1 0l-5.9-5.9a1.5 1.5 0 0 1 0-2.1z" {...STROKE} />
      <circle cx="15.6" cy="8.4" r="1.4" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  truck: (a) => (
    <>
      <rect x="2.6" y="7.4" width="11" height="8.4" rx="1.3" {...STROKE} />
      <path d="M13.6 10.4h3.6l3.2 3.2v2.2h-6.8" {...STROKE} />
      <circle cx="7.2" cy="18.2" r="1.5" {...STROKE} />
      <circle cx="17.2" cy="18.2" r="1.5" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  shield: (a) => (
    <>
      <path d="M12 3.2 19.4 6v6c0 4.2-3 7.2-7.4 8.8C7.6 19.2 4.6 16.2 4.6 12V6z" {...STROKE} />
      <path d="m9 12 2.2 2.2L15.4 10" {...STROKE} stroke={a} strokeWidth="1.9" />
    </>
  ),
  bolt: (a) => (
    <>
      <path d="M13.4 3 5.8 13.4h5.2L10.6 21l7.6-10.4H13z" {...STROKE} stroke={a} strokeWidth="1.7" />
    </>
  ),
  refresh: (a) => (
    <>
      <path d="M20 12a8 8 0 1 1-2.4-5.7" {...STROKE} />
      <path d="M20 4.4V9h-4.6" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  star: (a) => (
    <path
      d="m12 3.6 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8L3.6 9.7l5.8-.8z"
      {...STROKE}
      stroke={a}
      strokeWidth="1.5"
    />
  ),
  heart: (a) => (
    <path
      d="M12 20.2 4.9 13.1a4.3 4.3 0 0 1 6.1-6.1l1 1 1-1a4.3 4.3 0 0 1 6.1 6.1z"
      {...STROKE}
      stroke={a}
      strokeWidth="1.5"
    />
  ),

  // --- Data & system ---
  chart: (a) => (
    <>
      <path d="M3.6 20.4h16.8" {...STROKE} />
      <path d="M6.4 20.4v-6M11 20.4V8.6M15.6 20.4v-8.4M20.2 20.4V4.8" {...STROKE} />
      <path d="M6.4 14.4 11 8.6l4.6 3.4 4.6-7.2" {...STROKE} stroke={a} strokeWidth="1.7" />
    </>
  ),
  pulse: (a) => (
    <>
      <path d="M3 12h4l2.4-6 4.2 12L16 12h5" {...STROKE} stroke={a} strokeWidth="1.7" />
    </>
  ),
  coins: (a) => (
    <>
      <ellipse cx="12" cy="6.6" rx="6.6" ry="2.8" {...STROKE} />
      <path d="M5.4 6.6v5.2c0 1.5 3 2.8 6.6 2.8s6.6-1.3 6.6-2.8V6.6" {...STROKE} />
      <path d="M5.4 11.8V17c0 1.6 3 2.8 6.6 2.8s6.6-1.2 6.6-2.8v-5.2" {...STROKE} stroke={a} strokeWidth="1.7" />
    </>
  ),
  cpu: (a) => (
    <>
      <rect x="7.4" y="7.4" width="9.2" height="9.2" rx="1.6" {...STROKE} />
      <path d="M10 3.6v3.8M14 3.6v3.8M10 16.6v3.8M14 16.6v3.8M3.6 10h3.8M3.6 14h3.8M16.6 10h3.8M16.6 14h3.8" {...STROKE} />
      <rect x="10.4" y="10.4" width="3.2" height="3.2" rx="0.7" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  sparkle: (a) => (
    <>
      <path d="M12 3.4 13.7 9l5.6 1.7-5.6 1.7L12 18l-1.7-5.6L4.7 10.7 10.3 9z" {...STROKE} stroke={a} strokeWidth="1.5" />
      <path d="M18.4 16.4 19.2 19l2.6.8-2.6.8-.8 2.6" {...STROKE} strokeWidth="1.2" opacity="0.65" />
    </>
  ),
  layers: (a) => (
    <>
      <path d="M12 3.4 20.6 8 12 12.6 3.4 8z" {...STROKE} />
      <path d="m3.4 12 8.6 4.6 8.6-4.6" {...STROKE} />
      <path d="m3.4 16 8.6 4.6 8.6-4.6" {...STROKE} stroke={a} strokeWidth="1.7" />
    </>
  ),
  bell: (a) => (
    <>
      <path d="M6.4 10a5.6 5.6 0 0 1 11.2 0c0 3.8 1.4 5.2 1.9 6.2H4.5c.5-1 1.9-2.4 1.9-6.2z" {...STROKE} />
      <path d="M9.8 19a2.4 2.4 0 0 0 4.4 0" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  gear: (a) => (
    <>
      <circle cx="12" cy="12" r="3" {...STROKE} />
      <path
        d="M12 3.4v2.4M12 18.2v2.4M20.6 12h-2.4M5.8 12H3.4M18.1 5.9l-1.7 1.7M7.6 16.4l-1.7 1.7M18.1 18.1l-1.7-1.7M7.6 7.6 5.9 5.9"
        {...STROKE}
      />
      <circle cx="12" cy="12" r="6.6" {...STROKE} stroke={a} strokeWidth="1.2" opacity="0.5" />
    </>
  ),
  document: (a) => (
    <>
      <path d="M6.8 3.4h7L18 7.6v13H6.8z" {...STROKE} />
      <path d="M13.8 3.4v4.2H18" {...STROKE} stroke={a} strokeWidth="1.8" />
      <path d="M9.6 12.4h5.6M9.6 15.8h5.6" {...STROKE} />
    </>
  ),
  globe: (a) => (
    <>
      <circle cx="12" cy="12" r="8.4" {...STROKE} />
      <path d="M3.6 12h16.8" {...STROKE} />
      <path d="M12 3.6c2.5 2.4 3.9 5.3 3.9 8.4s-1.4 6-3.9 8.4c-2.5-2.4-3.9-5.3-3.9-8.4S9.5 6 12 3.6z" {...STROKE} stroke={a} strokeWidth="1.5" />
    </>
  ),
  clock: (a) => (
    <>
      <circle cx="12" cy="12" r="8.4" {...STROKE} />
      <path d="M12 7v5.4l3.6 2.2" {...STROKE} stroke={a} strokeWidth="1.8" />
    </>
  ),
  check: (a) => (
    <>
      <circle cx="12" cy="12" r="8.4" {...STROKE} />
      <path d="m8.2 12.2 2.6 2.6 5-5" {...STROKE} stroke={a} strokeWidth="1.9" />
    </>
  ),
  alert: (a) => (
    <>
      <path d="M12 4.2 21 19.2H3z" {...STROKE} />
      <path d="M12 10v4" {...STROKE} stroke={a} strokeWidth="1.9" />
      <path d="M12 16.6h.01" {...STROKE} stroke={a} strokeWidth="2.1" />
    </>
  ),
  trash: (a) => (
    <>
      <path d="M4.8 6.8h14.4" {...STROKE} />
      <path d="M9.2 6.8V4.6h5.6v2.2" {...STROKE} />
      <path d="M6.6 6.8 7.6 20h8.8l1-13.2" {...STROKE} />
      <path d="M10.4 10.6v5.8M13.6 10.6v5.8" {...STROKE} stroke={a} strokeWidth="1.7" />
    </>
  ),
  zoom: (a) => (
    <>
      <circle cx="10.8" cy="10.8" r="6.3" {...STROKE} />
      <path d="M8.4 10.8h4.8M10.8 8.4v4.8" {...STROKE} stroke={a} strokeWidth="1.7" />
      <path d="m15.4 15.4 4.6 4.6" {...STROKE} strokeWidth="1.9" />
    </>
  ),
};

/**
 * @param {object} props
 * @param {string} props.name    One of the keys in SHAPES. An unknown name
 *                               renders the neutral `box` icon rather than
 *                               throwing, so a typo never blanks a screen.
 * @param {string} [props.className]
 * @param {string} [props.accent] Accent stroke colour. Defaults to the XXII cyan.
 * @param {string} [props.title]  When provided the icon is exposed to assistive
 *                                technology; otherwise it is hidden as decorative.
 */
export default function GeoIcon({ name, className = 'h-5 w-5', accent = 'var(--xx-cyan)', title, ...rest }) {
  const shape = SHAPES[name] || SHAPES.box;
  const decorative = !title;

  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      role={decorative ? undefined : 'img'}
      aria-hidden={decorative ? 'true' : undefined}
      aria-label={title}
      focusable="false"
      {...rest}
    >
      {title ? <title>{title}</title> : null}
      {shape(accent)}
    </svg>
  );
}

/** Exported so a menu can be built from the available icon names. */
export const geoIconNames = Object.keys(SHAPES);
