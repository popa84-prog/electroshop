import { useEffect, useMemo, useState } from 'react';

/**
 * XXII — TASK 2 (promotions with a holographic timer, animated like an SF
 * digital clock).
 *
 * A countdown rendered as four segment blocks. Three details make it read as an
 * instrument rather than as text:
 *
 *   1. Each digit pair sits in its own glass cell with a monospaced face, so
 *      the block never changes width as the numbers tick (`tabular-nums` plus a
 *      fixed cell width).
 *   2. The colons blink on a one-second cycle, out of phase with nothing else
 *      on the page, which is what makes a clock feel live.
 *   3. Behind each cell a faint duplicate of the digit is drawn at low opacity
 *      — the "ghost segment" of a real seven-segment display.
 *
 * The tick is a single one-second interval, cleared on unmount. It stops itself
 * once the target passes so an expired promotion does not keep re-rendering the
 * page forever.
 */

function remainingFrom(target) {
  const ms = new Date(target).getTime() - Date.now();
  if (!Number.isFinite(ms) || ms <= 0) {
    return { total: 0, days: 0, hours: 0, minutes: 0, seconds: 0 };
  }
  const totalSeconds = Math.floor(ms / 1000);
  return {
    total: ms,
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  };
}

const pad = (n) => String(n).padStart(2, '0');

function Segment({ value, unit }) {
  const text = pad(value);

  return (
    <div className="flex flex-col items-center gap-1.5">
      <span className="relative grid h-14 w-14 place-items-center overflow-hidden rounded-xl border border-[rgba(34,232,245,0.28)] bg-[rgba(9,11,28,0.6)] shadow-[0_0_28px_-8px_rgba(34,232,245,0.55)] backdrop-blur-glass sm:h-16 sm:w-16">
        {/* Ghost segments — the unlit "8" behind every digit on a real display. */}
        <span
          aria-hidden="true"
          className="absolute inset-0 grid place-items-center font-mono text-2xl font-bold text-white/[0.06] sm:text-3xl"
        >
          88
        </span>
        <span className="relative font-mono text-2xl font-bold tabular-nums text-[color:var(--xx-cyan)] sm:text-3xl">
          {text}
        </span>
        {/* Horizontal split line, as on a flip-clock card. */}
        <span aria-hidden="true" className="absolute inset-x-0 top-1/2 h-px bg-black/40" />
      </span>
      <span className="text-[0.6rem] font-semibold uppercase tracking-[0.18em] xx-ink-dim">{unit}</span>
    </div>
  );
}

function Colon() {
  return (
    <span aria-hidden="true" className="animate-xx-blink pb-6 font-mono text-2xl font-bold text-[color:var(--xx-cyan)]">
      :
    </span>
  );
}

export default function HoloTimer({ target, label, expiredLabel = 'Ofertă încheiată', className = '', onExpire }) {
  const [left, setLeft] = useState(() => remainingFrom(target));

  useEffect(() => {
    setLeft(remainingFrom(target));

    const id = window.setInterval(() => {
      const next = remainingFrom(target);
      setLeft(next);
      if (next.total <= 0) {
        window.clearInterval(id);
        if (onExpire) onExpire();
      }
    }, 1000);

    return () => window.clearInterval(id);
    // `onExpire` is intentionally excluded: an inline arrow passed by the parent
    // would restart the interval on every parent render and reset the clock.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  const expired = left.total <= 0;

  // A single readable string for assistive technology — four separate live
  // regions ticking every second would be unusable with a screen reader.
  const ariaText = useMemo(() => {
    if (expired) return expiredLabel;
    return `${left.days} zile, ${left.hours} ore, ${left.minutes} minute rămase`;
  }, [expired, expiredLabel, left.days, left.hours, left.minutes]);

  if (expired) {
    return (
      <div className={`inline-flex items-center gap-2 ${className}`}>
        <span className="badge badge-magenta">{expiredLabel}</span>
      </div>
    );
  }

  return (
    <div className={className}>
      {label ? <p className="mb-2 xx-eyebrow">{label}</p> : null}
      <div className="flex items-start gap-1.5 sm:gap-2" role="timer" aria-live="off" aria-label={ariaText}>
        <Segment value={left.days} unit="zile" />
        <Colon />
        <Segment value={left.hours} unit="ore" />
        <Colon />
        <Segment value={left.minutes} unit="min" />
        <Colon />
        <Segment value={left.seconds} unit="sec" />
      </div>
      <span className="sr-only" aria-live="polite">
        {ariaText}
      </span>
    </div>
  );
}
