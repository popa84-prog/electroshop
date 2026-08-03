import { useEffect, useRef, useState } from 'react';

/**
 * XXII — TASK 6 (statistics cards: animated refresh effect, neon border).
 *
 * A metric tile with three behaviours that a plain number in a box does not
 * have:
 *
 *   1. The value counts up from zero on first mount, so a dashboard populating
 *      itself reads as instruments spinning up rather than text appearing.
 *   2. When the value *changes* after mount (a refresh landed), the tile runs a
 *      scan sweep and re-counts from the previous number to the new one. That
 *      is the "animated refresh effect" from the brief: the user sees which
 *      panels actually changed instead of hunting for the difference.
 *   3. The trend is stated with an arrow glyph *and* a sign, never with colour
 *      alone, so it survives greyscale and colour-blindness.
 *
 * The count-up honours `prefers-reduced-motion` by jumping straight to the
 * final value — a number that animates is decoration, and decoration is exactly
 * what that preference asks us to drop.
 */

const TONES = {
  blue: {
    ring: 'hover:border-[rgba(46,123,255,0.45)] hover:shadow-glow-blue',
    accent: 'var(--xx-blue)',
    bar: 'from-[#2e7bff] to-[#7a3cff]',
  },
  purple: {
    ring: 'hover:border-[rgba(122,60,255,0.45)] hover:shadow-glow-purple',
    accent: 'var(--xx-purple)',
    bar: 'from-[#7a3cff] to-[#ff3dcb]',
  },
  aqua: {
    ring: 'hover:border-[rgba(34,232,245,0.45)] hover:shadow-glow-aqua',
    accent: 'var(--xx-cyan)',
    bar: 'from-[#22e8f5] to-[#2e7bff]',
  },
  good: {
    ring: 'hover:border-[rgba(110,247,168,0.45)]',
    accent: 'var(--xx-lime)',
    bar: 'from-[#6ef7a8] to-[#22e8f5]',
  },
  critical: {
    ring: 'hover:border-[rgba(255,84,112,0.45)]',
    accent: 'var(--xx-red)',
    bar: 'from-[#ff5470] to-[#ff3dcb]',
  },
  warning: {
    ring: 'hover:border-[rgba(255,194,75,0.45)]',
    accent: 'var(--xx-amber)',
    bar: 'from-[#ffc24b] to-[#ff5470]',
  },
};

const COUNT_MS = 620;

function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false;
}

/**
 * Animates from `from` to `to` over COUNT_MS using an ease-out curve, and
 * returns the current intermediate number. Non-numeric targets short-circuit:
 * a tile showing a string label must not be forced through arithmetic.
 */
function useCountUp(target) {
  const numeric = typeof target === 'number' && Number.isFinite(target);
  const [display, setDisplay] = useState(numeric ? 0 : target);
  const fromRef = useRef(0);
  const frameRef = useRef(0);

  useEffect(() => {
    if (!numeric) {
      setDisplay(target);
      return undefined;
    }

    if (prefersReducedMotion()) {
      fromRef.current = target;
      setDisplay(target);
      return undefined;
    }

    const from = fromRef.current;
    const delta = target - from;
    if (delta === 0) {
      setDisplay(target);
      return undefined;
    }

    const start = performance.now();

    const step = (now) => {
      const elapsed = now - start;
      const t = Math.min(1, elapsed / COUNT_MS);
      // easeOutCubic — fast at the start, settles gently on the final digit.
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplay(from + delta * eased);
      if (t < 1) {
        frameRef.current = requestAnimationFrame(step);
      } else {
        fromRef.current = target;
      }
    };

    frameRef.current = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frameRef.current);
  }, [target, numeric]);

  return display;
}

export default function StatTile({
  label,
  value,
  format,
  hint,
  icon = null,
  tone = 'blue',
  trend = null, // number: positive = up, negative = down
  trendLabel,
  progress = null, // 0..1 — draws a gradient meter under the value
  footer = null, // free slot under the meter — a sparkline, a link, a note
  className = '',
  ...rest
}) {
  const palette = TONES[tone] || TONES.blue;
  const animated = useCountUp(value);
  const [flash, setFlash] = useState(false);
  const mounted = useRef(false);

  // Refresh sweep — skipped on the very first render so a page load does not
  // fire every tile's "something changed" signal at once.
  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      return undefined;
    }
    setFlash(true);
    const timer = window.setTimeout(() => setFlash(false), 1400);
    return () => window.clearTimeout(timer);
  }, [value]);

  const rendered =
    typeof value === 'number' && Number.isFinite(value)
      ? format
        ? format(animated)
        : Math.round(animated).toLocaleString('ro-RO')
      : value;

  const trendUp = typeof trend === 'number' && trend > 0;
  const trendDown = typeof trend === 'number' && trend < 0;

  return (
    <div
      className={`card group relative overflow-hidden p-5 transition-all duration-xx ease-xx ${palette.ring} ${
        flash ? 'xx-scanning' : ''
      } ${className}`}
      {...rest}
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-[0.68rem] font-semibold uppercase tracking-[0.16em] xx-ink-dim">{label}</p>
        {icon ? (
          <span
            aria-hidden="true"
            className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.05)] transition-transform duration-xx ease-xx group-hover:scale-110"
            style={{ color: palette.accent }}
          >
            {icon}
          </span>
        ) : null}
      </div>

      <p
        className="mt-3 font-display text-3xl font-bold tabular-nums tracking-tight text-[color:var(--xx-ink)] tv:text-4xl"
        style={{ textShadow: `0 0 26px ${palette.accent}55` }}
      >
        {rendered}
      </p>

      {typeof trend === 'number' ? (
        <p className="mt-2 flex items-center gap-1.5 text-xs font-semibold">
          <span aria-hidden="true" style={{ color: trendUp ? 'var(--xx-lime)' : trendDown ? 'var(--xx-red)' : 'var(--xx-ink-dim)' }}>
            {trendUp ? '▲' : trendDown ? '▼' : '■'}
          </span>
          <span style={{ color: trendUp ? 'var(--xx-lime)' : trendDown ? 'var(--xx-red)' : 'var(--xx-ink-dim)' }}>
            {trendUp ? '+' : ''}
            {trend.toLocaleString('ro-RO', { maximumFractionDigits: 1 })}%
          </span>
          {trendLabel ? <span className="xx-ink-dim font-normal">{trendLabel}</span> : null}
        </p>
      ) : null}

      {hint ? <p className="mt-2 text-xs xx-ink-dim">{hint}</p> : null}

      {typeof progress === 'number' ? (
        <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-[rgba(255,255,255,0.08)]">
          <div
            className={`h-full rounded-full bg-gradient-to-r ${palette.bar} transition-[width] duration-700 ease-xx`}
            style={{ width: `${Math.max(0, Math.min(1, progress)) * 100}%` }}
          />
        </div>
      ) : null}

      {footer ? <div className="mt-3">{footer}</div> : null}
    </div>
  );
}
