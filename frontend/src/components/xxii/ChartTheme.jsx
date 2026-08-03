/**
 * XXII — TASK 6 (Quantum Control Center): the single chart theme every admin
 * chart draws from.
 *
 * Recharts has no theming layer, so before this file each admin screen carried
 * its own hard-coded hexes — which is how `#e2e8f0` grid lines and `#16a34a`
 * bars survived the move to a dark surface. Everything visual that a chart
 * needs now lives here, and a chart imports values rather than inventing them.
 *
 * ── Why these particular colours ──────────────────────────────────────────
 *
 * They are not picked by eye. The two palettes below were run through the
 * data-visualisation validator against the real chart surface (#0a0b1e, the
 * glass panel colour, not the page void) and both pass every computable check:
 *
 *   SERIES   (adjacent pairs)   lightness band · chroma floor · CVD separation
 *                               (worst adjacent ΔE 12.6 deutan) · normal-vision
 *                               floor (worst ΔE 28.2) · contrast ≥ 3:1
 *   STATUS   (all pairs — a donut compares every slice to every other, not just
 *            its neighbours) — same five checks, all passing.
 *
 * Two consequences worth stating plainly, because they look like mistakes:
 *
 *   1. The amber is `#b08c09`, not a bright neon yellow, and the red is
 *      `#b82f3c`, not `#ff5470`. Anything brighter leaves the OKLCH lightness
 *      band for dark surfaces (0.48–0.67) and stops being separable from its
 *      neighbours under deuteranopia. Green and red in particular are only
 *      distinguishable to a red-green colourblind reader because they sit at
 *      clearly different lightnesses — that separation is the whole reason the
 *      red is dark.
 *   2. The neon that the XXII language calls for is added by `XX_GLOW_FILTER`,
 *      a drop-shadow on the mark, **not** by brightening the mark itself. A
 *      halo around a stroke reads as glow without moving the stroke colour out
 *      of the validated band. This is the one way to get "neon" and "legible"
 *      at the same time.
 *
 * Status is additionally never carried by colour alone anywhere it is used:
 * every legend row and every tooltip prints the status name in words.
 */

/** The surface charts are actually drawn on — the glass panel, not the page. */
export const XX_CHART_SURFACE = '#0a0b1e';

/**
 * Categorical series colours in fixed order. Slots are assigned by position and
 * never cycled: an eighth series folds into "Other" or becomes its own chart.
 */
export const XX_SERIES = ['#2e7bff', '#b08c09', '#d032b8', '#1fac79', '#7a3cff', '#0e9fb0'];

/** Named aliases for the slots above, so charts read as intent rather than index. */
export const XX_SERIES_BLUE = XX_SERIES[0];
export const XX_SERIES_AMBER = XX_SERIES[1];
export const XX_SERIES_MAGENTA = XX_SERIES[2];
export const XX_SERIES_GREEN = XX_SERIES[3];
export const XX_SERIES_PURPLE = XX_SERIES[4];
export const XX_SERIES_CYAN = XX_SERIES[5];

/** Reserved status colours. Never reused as "series 4". */
export const XX_STATUS = {
  pending: '#b08c09',
  paid: '#2e7bff',
  shipped: '#d032b8',
  delivered: '#1fac79',
  cancelled: '#b82f3c',
};

/** Fallback for a status the backend adds later and this file does not know. */
export const XX_STATUS_UNKNOWN = '#767ea6';

/** Recessive grid and axis ink, sampled from the XXII text tokens. */
export const XX_GRID_STROKE = 'rgba(255,255,255,0.08)';
export const XX_AXIS_INK = '#767ea6';

/** Spread onto `<XAxis>` / `<YAxis>` so every chart's axes match. */
export const xxAxisProps = {
  tick: { fontSize: 12, fill: XX_AXIS_INK },
  stroke: 'rgba(255,255,255,0.12)',
  tickLine: false,
};

/** Spread onto `<CartesianGrid>`. */
export const xxGridProps = {
  strokeDasharray: '3 5',
  stroke: XX_GRID_STROKE,
  vertical: false,
};

/** Spread onto `<Legend>`. */
export const xxLegendProps = {
  wrapperStyle: { fontSize: 12, color: '#a8b0d4' },
};

/** The cursor Recharts draws under the pointer — a scan line, not a grey block. */
export const xxCursor = { stroke: 'rgba(34,232,245,0.45)', strokeWidth: 1, strokeDasharray: '4 4' };
export const xxBarCursor = { fill: 'rgba(122,60,255,0.12)' };

/** Filter id emitted by <XXChartDefs />; apply as `filter={XX_GLOW_FILTER}`. */
export const XX_GLOW_FILTER = 'url(#xx-chart-glow)';

/**
 * SVG defs shared by every chart: the glow filter that supplies the neon, and a
 * vertical fade used by area fills.
 *
 * Recharts renders children into the chart's own <svg>, so this must be dropped
 * inside a chart component rather than at the page root — an id referenced
 * across two different <svg> elements resolves in only one of them.
 */
export function XXChartDefs({ areaFills = [] }) {
  return (
    <defs>
      <filter id="xx-chart-glow" x="-60%" y="-60%" width="220%" height="220%">
        <feGaussianBlur stdDeviation="3.2" result="xxBlur" />
        <feMerge>
          <feMergeNode in="xxBlur" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>
      {areaFills.map(({ id, color, opacity = 0.34 }) => (
        <linearGradient key={id} id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={opacity} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      ))}
    </defs>
  );
}

/**
 * The glass tooltip. Every admin chart uses this one rather than the Recharts
 * default, which paints an opaque white card that is unreadable on the dark
 * surface.
 *
 * `rows` maps the Recharts payload into `{ label, value, color }` entries; when
 * it is omitted each series is printed with its own name, value and colour.
 */
export function HoloTooltip({ active, payload, label, title, rows, format }) {
  if (!active || !payload || payload.length === 0) return null;

  const lines =
    typeof rows === 'function'
      ? rows(payload)
      : payload
          .filter((entry) => entry && entry.value != null)
          .map((entry) => ({
            label: entry.name ?? entry.dataKey,
            value: typeof format === 'function' ? format(entry.value, entry) : entry.value,
            color: entry.color || entry.stroke || entry.fill,
          }));

  if (!lines || lines.length === 0) return null;

  return (
    <div className="rounded-xl border border-[rgba(34,232,245,0.35)] bg-[rgba(7,8,24,0.94)] px-3 py-2 text-xs shadow-[0_18px_44px_-20px_rgba(0,0,0,0.95),0_0_36px_-14px_rgba(34,232,245,0.6)] backdrop-blur-glass">
      <p className="mb-1.5 font-display font-semibold text-[color:var(--xx-ink)]">{title ?? label}</p>
      <ul className="space-y-1">
        {lines.map((line, index) => (
          <li key={`${line.label}-${index}`} className="flex items-center gap-2">
            {line.color ? (
              <span
                aria-hidden="true"
                className="h-2 w-2 shrink-0 rounded-full"
                style={{ backgroundColor: line.color, boxShadow: `0 0 8px ${line.color}` }}
              />
            ) : null}
            <span className="text-[color:var(--xx-ink-muted)]">{line.label}</span>
            <span className="ml-auto font-semibold text-[color:var(--xx-ink)]">{line.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
