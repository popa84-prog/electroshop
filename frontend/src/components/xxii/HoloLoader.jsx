/**
 * XXII — TASK 8 (load animations: materialize + scan line).
 *
 * Two distinct loading affordances live here, because a page skeleton and an
 * inline spinner solve different problems:
 *
 *   - `HoloLoader`   — an inline indicator: a rotating neon ring with a core
 *                      that pulses. Used inside buttons, table cells and any
 *                      place where the surrounding layout already exists.
 *   - `HoloSkeleton` — a placeholder block that occupies the exact space the
 *                      real content will take, swept by a scan line. Used while
 *                      a whole panel is still fetching, so the layout does not
 *                      jump when the data lands.
 *
 * Both are `role="status"` with an accessible label, so a screen reader
 * announces that work is in progress instead of reading an empty region.
 */

const SIZES = {
  sm: 'h-4 w-4 border-[1.5px]',
  md: 'h-6 w-6 border-2',
  lg: 'h-10 w-10 border-2',
  xl: 'h-16 w-16 border-[3px]',
};

export default function HoloLoader({ size = 'md', label = 'Se încarcă', className = '', inline = false }) {
  const ring = SIZES[size] || SIZES.md;

  return (
    <span
      role="status"
      aria-live="polite"
      className={`${inline ? 'inline-flex' : 'flex'} items-center justify-center gap-3 ${className}`}
    >
      <span className="relative inline-flex">
        {/* Outer ring — transparent on three sides so the rotation reads. */}
        <span
          className={`${ring} inline-block animate-spin rounded-full border-solid border-[rgba(255,255,255,0.10)] border-t-[color:var(--xx-cyan)] border-r-[color:var(--xx-blue)]`}
          style={{ animationDuration: '820ms' }}
        />
        {/* Core — a soft pulsing dot at the centre of the ring. */}
        <span
          aria-hidden="true"
          className="absolute left-1/2 top-1/2 h-1 w-1 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[color:var(--xx-cyan)] animate-xx-pulse-glow"
        />
      </span>
      <span className="sr-only">{label}</span>
    </span>
  );
}

/**
 * A placeholder surface. `lines` renders stacked bars of decreasing width to
 * imitate a paragraph; `block` renders one solid area of the given height.
 */
export function HoloSkeleton({ lines = 0, height = '10rem', className = '', label = 'Se încarcă' }) {
  if (lines > 0) {
    return (
      <div role="status" aria-live="polite" className={`space-y-2.5 ${className}`}>
        {Array.from({ length: lines }).map((_, index) => (
          <span
            key={index}
            aria-hidden="true"
            className="xx-scanning block h-3 rounded-full bg-[rgba(255,255,255,0.06)]"
            style={{ width: `${100 - index * 12}%` }}
          />
        ))}
        <span className="sr-only">{label}</span>
      </div>
    );
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className={`xx-scanning rounded-[1.25rem] border border-[rgba(255,255,255,0.10)] bg-[rgba(255,255,255,0.04)] ${className}`}
      style={{ height }}
    >
      <span className="sr-only">{label}</span>
    </div>
  );
}

/**
 * The full-panel loading state: a skeleton grid sized to a card list. Used by
 * Home, catalog and the admin dashboard so all three fetch with the same shape.
 */
export function HoloGridSkeleton({ count = 4, className = '' }) {
  return (
    <div className={`grid gap-6 sm:grid-cols-2 lg:grid-cols-4 ${className}`}>
      {Array.from({ length: count }).map((_, index) => (
        <HoloSkeleton key={index} height="17rem" />
      ))}
    </div>
  );
}
