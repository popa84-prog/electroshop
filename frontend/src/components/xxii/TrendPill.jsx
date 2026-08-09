/**
 * XXII — a period-over-period change badge. Task 2.
 *
 * ## Direction is not the same as sign
 *
 * A rise in revenue and a rise in the return rate are both positive numbers and
 * only one of them is good news. The backend decides which, and sends
 * `improving` alongside the percentage; this component colours from that flag,
 * never from the sign. A dashboard that paints a growing return rate green is
 * worse than one with no colour at all, because it is confidently wrong in the
 * direction an operator acts on.
 *
 * ## Growth from zero has no percentage
 *
 * When the previous period was zero the change is undefined — not infinite, not
 * a hundred percent. The badge reads "nou" instead of a figure, which is the
 * true statement.
 *
 * ## Colour is never the only signal
 *
 * Every badge carries an arrow glyph and a text label, so the direction survives
 * greyscale printing, forced-colours mode, and the roughly one in twelve men who
 * cannot separate the red from the green.
 *
 * @param {{current: number, previous: number, changePct: number|null,
 *          improving: boolean}} delta the backend's DeltaDto
 * @param {string} suffix appended to the comparison line, e.g. "față de luna trecută"
 */
export default function TrendPill({ delta, suffix = '', compact = false, className = '' }) {
  if (!delta) {
    return null;
  }

  const { changePct, improving } = delta;
  const isNew = changePct === null || changePct === undefined;
  const flat = !isNew && Math.abs(changePct) < 0.05;

  // Three states, three treatments. "Flat" is deliberately neutral rather than
  // being rounded into a direction: reporting +0.02% as growth is technically
  // true and practically noise.
  const tone = isNew
    ? 'border-[rgba(46,123,255,0.4)] bg-[rgba(46,123,255,0.12)] text-[#7fb0ff]'
    : flat
    ? 'border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] text-[color:var(--xx-ink-dim)]'
    : improving
    ? 'border-[rgba(31,172,121,0.4)] bg-[rgba(31,172,121,0.12)] text-[#4fd3a0]'
    : 'border-[rgba(184,47,60,0.45)] bg-[rgba(184,47,60,0.12)] text-[#ff8a97]';

  const glyph = isNew ? '★' : flat ? '→' : changePct > 0 ? '↑' : '↓';

  const text = isNew
    ? 'nou'
    : flat
    ? 'neschimbat'
    : `${changePct > 0 ? '+' : ''}${changePct.toFixed(1)}%`;

  const label = isNew
    ? 'Fără perioadă anterioară pentru comparație'
    : `${text} ${suffix || 'față de perioada anterioară'}`;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border font-medium ${tone} ${
        compact ? 'px-1.5 py-0.5 text-[10px]' : 'px-2 py-0.5 text-xs'
      } ${className}`}
      title={label}
    >
      <span aria-hidden="true">{glyph}</span>
      <span aria-hidden="true">{text}</span>
      <span className="sr-only">{label}</span>
    </span>
  );
}
