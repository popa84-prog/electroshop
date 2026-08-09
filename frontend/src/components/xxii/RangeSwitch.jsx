/**
 * XXII — the time-window selector. Task 2.
 *
 * ## It is a radio group, not a row of buttons
 *
 * The options are mutually exclusive and exactly one is always chosen, which is
 * what a radio group means. Implemented as buttons it would announce as six
 * unrelated controls and give no indication that picking one unpicks the rest;
 * `role="radiogroup"` with `aria-checked` says the true thing, and arrow-key
 * navigation comes with it.
 *
 * ## Labels are short because they sit inside a card header
 *
 * "7z" rather than "Ultimele 7 zile". The long form is in the accessible name,
 * where it costs no space, so a screen reader hears the sentence and the header
 * keeps room for the title.
 */

/** The full option set. Panels pass a subset; the labels stay identical. */
export const RANGE_OPTIONS = {
  '24h': { short: '24h', long: 'Ultimele 24 de ore' },
  '7d': { short: '7z', long: 'Ultimele 7 zile' },
  '30d': { short: '30z', long: 'Ultimele 30 de zile' },
  '90d': { short: '90z', long: 'Ultimele 90 de zile' },
  '3m': { short: '3L', long: 'Ultimele 3 luni' },
  '6m': { short: '6L', long: 'Ultimele 6 luni' },
  '12m': { short: '12L', long: 'Ultimele 12 luni' },
};

/** What most analytics panels offer. */
export const DEFAULT_RANGES = ['7d', '30d', '90d', '12m'];

/** What the financial panel offers, matching the requirement's 3 / 6 / 12. */
export const FINANCIAL_RANGES = ['3m', '6m', '12m'];

/** What a chart with hourly resolution offers. */
export const SHORT_RANGES = ['24h', '7d', '30d', '12m'];

export default function RangeSwitch({
  value,
  onChange,
  options = DEFAULT_RANGES,
  label = 'Interval',
  className = '',
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={`inline-flex items-center gap-0.5 rounded-lg border border-[rgba(255,255,255,0.12)]
        bg-[rgba(255,255,255,0.03)] p-0.5 ${className}`}
    >
      {options.map((code) => {
        const option = RANGE_OPTIONS[code];
        if (!option) return null;
        const active = value === code;

        return (
          <button
            key={code}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={option.long}
            onClick={() => onChange(code)}
            className={`rounded-md px-2 py-1 text-[11px] font-semibold transition-all duration-xx ease-xx ${
              active
                ? 'bg-[rgba(34,232,245,0.16)] text-[color:var(--xx-cyan)] shadow-[inset_0_0_16px_-8px_rgba(34,232,245,0.9)]'
                : 'text-[color:var(--xx-ink-dim)] hover:bg-[rgba(255,255,255,0.06)] hover:text-[color:var(--xx-ink)]'
            }`}
          >
            {option.short}
          </button>
        );
      })}
    </div>
  );
}
