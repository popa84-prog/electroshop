/**
 * XXII — what a panel shows when it has nothing to show.
 *
 * ## "No data" and "nothing happened" are different messages
 *
 * This is the whole reason the component takes a `reason` rather than a single
 * string. A marketing panel with no impressions yet and a marketing panel
 * reporting zero conversions look identical if both render an empty chart, and
 * an operator will read the second meaning into the first — concluding a
 * campaign failed when it simply has not been measured.
 *
 * `reason="collecting"` says measurement started recently and names the date.
 * `reason="empty"` says the period genuinely had no activity. `reason="filtered"`
 * says the filter excluded everything, which is the one an operator fixes by
 * widening the filter rather than by worrying.
 */

const VARIANTS = {
  empty: {
    glyph: '○',
    title: 'Nu există date în perioada selectată',
    tone: 'text-[color:var(--xx-ink-dim)]',
  },
  collecting: {
    glyph: '◔',
    title: 'Colectarea datelor este în curs',
    tone: 'text-[#7fb0ff]',
  },
  filtered: {
    glyph: '⊘',
    title: 'Niciun rezultat pentru filtrele curente',
    tone: 'text-[color:var(--xx-ink-dim)]',
  },
  permission: {
    glyph: '⊗',
    title: 'Nu ai permisiunea necesară',
    tone: 'text-[color:var(--xx-ink-dim)]',
  },
};

export default function EmptyState({
  reason = 'empty',
  title = null,
  description = null,
  since = null,
  action = null,
  compact = false,
  className = '',
}) {
  const variant = VARIANTS[reason] || VARIANTS.empty;

  // The collection-start date is the point of the "collecting" variant, so it is
  // built into the description rather than left to each caller to remember.
  const body =
    description
    ?? (reason === 'collecting' && since
      ? `Măsurarea a început pe ${new Date(since).toLocaleDateString('ro-RO')}. `
        + 'Cifrele devin relevante pe măsură ce se acumulează trafic.'
      : null);

  return (
    <div
      className={`flex flex-col items-center justify-center rounded-xl border border-dashed
        border-[rgba(255,255,255,0.12)] text-center ${compact ? 'gap-1.5 p-4' : 'gap-2 p-8'} ${className}`}
    >
      <span className={`text-2xl ${variant.tone}`} aria-hidden="true">
        {variant.glyph}
      </span>
      <p className={`font-medium text-[color:var(--xx-ink)] ${compact ? 'text-xs' : 'text-sm'}`}>
        {title ?? variant.title}
      </p>
      {body ? (
        <p className="max-w-sm text-xs leading-relaxed text-[color:var(--xx-ink-dim)]">{body}</p>
      ) : null}
      {action}
    </div>
  );
}
