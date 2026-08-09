/**
 * XXII — a coloured status badge. Tasks 13, 18 and 19.
 *
 * ## Severity is decided by the server, rendered by this
 *
 * Every panel that shows a severity receives it as a string from the backend.
 * That is deliberate: the rule that says "fourteen days of cover is critical"
 * belongs beside the calculation that produced the fourteen, not in a component
 * that would then hold a second copy of every threshold in the system.
 *
 * ## Colour is never alone
 *
 * Each badge carries a glyph and a word alongside its colour. A red pill and a
 * green pill are the same pill to a red-green colourblind reader, to a printed
 * page, and to forced-colours mode — and this component is used on exactly the
 * tables where the distinction decides what somebody orders.
 */

const TONES = {
  DANGER: {
    label: 'Critic',
    glyph: '▲',
    className: 'border-[rgba(184,47,60,0.5)] bg-[rgba(184,47,60,0.14)] text-[#ff8a97]',
  },
  WARNING: {
    label: 'Atenție',
    glyph: '●',
    className: 'border-[rgba(176,140,9,0.5)] bg-[rgba(176,140,9,0.14)] text-[#e0bd4a]',
  },
  INFO: {
    label: 'Normal',
    glyph: '■',
    className: 'border-[rgba(255,255,255,0.16)] bg-[rgba(255,255,255,0.05)] text-[color:var(--xx-ink-dim)]',
  },
  SUCCESS: {
    label: 'Bun',
    glyph: '▼',
    className: 'border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.14)] text-[#4fd3a0]',
  },
  NO_DATA: {
    label: 'Fără date',
    glyph: '○',
    className: 'border-[rgba(46,123,255,0.4)] bg-[rgba(46,123,255,0.1)] text-[#7fb0ff]',
  },
};

/** Verdict codes from the marketing panel map onto the same visual language. */
const ALIASES = {
  STRONG: 'SUCCESS',
  OK: 'INFO',
  WEAK: 'DANGER',
  ERROR: 'DANGER',
  WARN: 'WARNING',
  UP: 'SUCCESS',
  DEGRADED: 'WARNING',
  DOWN: 'DANGER',
  HIGH: 'SUCCESS',
  MEDIUM: 'WARNING',
  LOW: 'INFO',
};

export default function SeverityBadge({ level, label = null, compact = false, className = '' }) {
  const key = ALIASES[level] || level || 'INFO';
  const tone = TONES[key] || TONES.INFO;
  const text = label ?? tone.label;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border font-medium ${tone.className} ${
        compact ? 'px-1.5 py-0.5 text-[10px]' : 'px-2 py-0.5 text-xs'
      } ${className}`}
    >
      <span aria-hidden="true">{tone.glyph}</span>
      <span>{text}</span>
    </span>
  );
}
