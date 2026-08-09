import { XX_SERIES } from './ChartTheme';

/**
 * XXII — a chart tooltip that shows the comparison, not just the value. Task 2.
 *
 * ## What the requirement is actually asking for
 *
 * "Tooltips with details, for example variation against last week." The value
 * under the cursor is already visible on the axis; what a tooltip adds is
 * context. This one shows the value, the same point one period earlier where the
 * series carries it, the difference, and the share of the total — the four
 * things somebody hovers a chart to find out.
 *
 * ## Growth from zero has no percentage
 *
 * When the previous value was zero the change is undefined. The tooltip prints
 * "nou" rather than a figure, for the same reason `TrendPill` does: +100% and
 * ∞ are both wrong, and 0% is a lie.
 *
 * ## Formatting is per series, not per chart
 *
 * A chart can carry a currency line and a percentage line at once — conversion
 * rate over impressions, margin over revenue. The formatter is resolved per
 * entry from the series config, so neither is rendered in the other's units.
 */
export default function AdvancedTooltip({
  active,
  payload,
  label,
  currency = 'RON',
  formats = {},
  totals = null,
  comparisonLabel = 'perioada anterioară',
}) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }

  const format = (value, kind) => {
    if (value === null || value === undefined) return '—';
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return '—';

    if (kind === 'currency') {
      return `${numeric.toLocaleString('ro-RO', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })} ${currency}`;
    }
    if (kind === 'percent') {
      return `${numeric.toLocaleString('ro-RO', { maximumFractionDigits: 1 })}%`;
    }
    if (kind === 'hours') {
      return `${numeric.toLocaleString('ro-RO', { maximumFractionDigits: 1 })} h`;
    }
    return numeric.toLocaleString('ro-RO', { maximumFractionDigits: 2 });
  };

  return (
    <div
      className="min-w-[11rem] rounded-xl border border-[rgba(255,255,255,0.16)]
        bg-[rgba(9,10,26,0.97)] p-3 shadow-[0_24px_60px_-28px_rgba(0,0,0,0.95)]
        backdrop-blur-glass-lg"
    >
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
        text-[color:var(--xx-ink-dim)]">
        {label}
      </p>

      <ul className="space-y-1.5">
        {payload.map((entry, index) => {
          const kind = formats[entry.dataKey] || formats.default || 'number';
          const previous = entry.payload?.[`${entry.dataKey}Previous`];
          const total = totals?.[entry.dataKey];

          const hasComparison = previous !== null && previous !== undefined;
          const changePct = hasComparison && Number(previous) !== 0
            ? ((Number(entry.value) - Number(previous)) / Math.abs(Number(previous))) * 100
            : null;

          const share = total ? (Number(entry.value) / Number(total)) * 100 : null;

          return (
            <li key={entry.dataKey ?? index} className="text-xs">
              <div className="flex items-center justify-between gap-3">
                <span className="flex min-w-0 items-center gap-1.5">
                  <span
                    className="h-2 w-2 shrink-0 rounded-full"
                    style={{ background: entry.color || XX_SERIES[index % XX_SERIES.length] }}
                    aria-hidden="true"
                  />
                  {/* The series name is text, never carried by the swatch alone:
                      a tooltip that identifies its lines only by colour is
                      unreadable to a colourblind reader on a multi-series
                      chart. */}
                  <span className="truncate text-[color:var(--xx-ink-dim)]">
                    {entry.name || entry.dataKey}
                  </span>
                </span>
                <span className="shrink-0 font-semibold tabular-nums text-[color:var(--xx-ink)]">
                  {format(entry.value, kind)}
                </span>
              </div>

              {hasComparison ? (
                <p className="mt-0.5 pl-3.5 text-[10px] text-[color:var(--xx-ink-dim)]">
                  {format(previous, kind)} în {comparisonLabel}
                  {' · '}
                  {changePct === null ? (
                    <span className="text-[#7fb0ff]">nou</span>
                  ) : (
                    <span className={changePct >= 0 ? 'text-[#4fd3a0]' : 'text-[#ff8a97]'}>
                      {changePct >= 0 ? '+' : ''}
                      {changePct.toFixed(1)}%
                    </span>
                  )}
                </p>
              ) : null}

              {share !== null && Number.isFinite(share) ? (
                <p className="mt-0.5 pl-3.5 text-[10px] text-[color:var(--xx-ink-dim)]">
                  {share.toFixed(1)}% din total
                </p>
              ) : null}
            </li>
          );
        })}
      </ul>

      {/* The count is the sample size behind the point. An average computed from
          two orders and one computed from two hundred look identical on a line,
          and only one of them means anything. */}
      {payload[0]?.payload?.count !== undefined && payload[0].payload.count !== null ? (
        <p className="mt-2 border-t border-[rgba(255,255,255,0.1)] pt-1.5 text-[10px]
          text-[color:var(--xx-ink-dim)]">
          {payload[0].payload.count} înregistrări
        </p>
      ) : null}
    </div>
  );
}
