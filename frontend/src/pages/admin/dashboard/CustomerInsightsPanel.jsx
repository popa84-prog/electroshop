import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Link } from 'react-router-dom';
import {
  AdvancedTooltip,
  DashCard,
  DEFAULT_RANGES,
  EmptyState,
  RangeSwitch,
  TrendPill,
  XXChartDefs,
  XX_SERIES,
  XX_SERIES_BLUE,
  XX_SERIES_GREEN,
  XX_SERIES_PURPLE,
  xxAxisProps,
  xxBarCursor,
  xxCursor,
  xxGridProps,
  xxLegendProps,
} from '../../../components/xxii';
import analyticsService from '../../../api/analyticsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Customer analysis. Task 16.
 *
 * ## "New" is judged against all of history
 *
 * A buyer from last year who returns this month is a returning customer, even
 * though a seven-day window sees them for the first time. The backend decides
 * this from the customer's first-ever order, not from the window — deciding it
 * from inside the window would relabel the whole loyal base as new every time
 * somebody narrows the range, reporting perfect acquisition for a business
 * acquiring nobody. The subtitle says so, because the figure is otherwise
 * indistinguishable from the naive one.
 *
 * ## Every segment prints its own definition
 *
 * A segment nobody can reproduce is a segment nobody should act on, so the rule
 * that placed customers in it travels with the count.
 */
export default function CustomerInsightsPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('customer-insights', '30d', DEFAULT_RANGES);

  const { data, loading, error, reload } = usePanelData(
    (signal) => analyticsService.customerInsights({ range }, signal),
    [range]
  );

  const newVsReturning = (data?.newVsReturning || []).map((point) => ({
    label: point.label,
    noi: Number(point.value ?? 0),
    recurenti: Number(point.secondary ?? 0),
    count: point.count ?? null,
  }));

  const basket = (data?.basketSeries || []).map((point) => ({
    label: point.label,
    value: point.value === null || point.value === undefined ? null : Number(point.value),
    count: point.count ?? null,
  }));

  const frequency = (data?.orderFrequency || []).map((bucket) => ({
    label: bucket.label,
    value: bucket.customers,
    revenue: bucket.revenue,
  }));

  return (
    <DashCard
      title={title}
      subtitle="„Client nou” se raportează la primul ordin din întreg istoricul, nu la intervalul selectat"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_PURPLE}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
      footer={
        data ? (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs">
            <span className="text-[color:var(--xx-ink-dim)]">
              Coș mediu{' '}
              <strong className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                {money(data.avgBasket, data.currency)}
              </strong>
            </span>
            {data.avgBasketDelta ? <TrendPill delta={data.avgBasketDelta} compact /> : null}
            <span className="text-[color:var(--xx-ink-dim)]">
              Rată revenire{' '}
              <strong className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                {data.repeatRatePct === null || data.repeatRatePct === undefined
                  ? '—'
                  : `${data.repeatRatePct.toFixed(1)}%`}
              </strong>
            </span>
            {data.repeatRateDelta ? <TrendPill delta={data.repeatRateDelta} compact /> : null}
          </div>
        ) : null
      }
    >
      {!loading && (data?.totalCustomers ?? 0) === 0 ? (
        <EmptyState reason="empty" compact={compact} />
      ) : (
        <>
          <div className="grid gap-4 xl:grid-cols-2">
            <Chart title="Clienți noi vs recurenți">
              <BarChart data={newVsReturning} margin={{ top: 4, right: 6, bottom: 0, left: -24 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} minTickGap={24} />
                <YAxis {...xxAxisProps} width={40} allowDecimals={false} />
                <Tooltip cursor={xxBarCursor}
                         content={<AdvancedTooltip formats={{ default: 'number' }} />} />
                <Legend {...xxLegendProps} />
                <Bar dataKey="noi" name="Noi" stackId="c" fill={XX_SERIES_GREEN} />
                <Bar dataKey="recurenti" name="Recurenți" stackId="c" fill={XX_SERIES_BLUE}
                     radius={[4, 4, 0, 0]} />
              </BarChart>
            </Chart>

            <Chart title="Valoare medie coș">
              <LineChart data={basket} margin={{ top: 4, right: 6, bottom: 0, left: -20 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} minTickGap={24} />
                <YAxis {...xxAxisProps} width={58} />
                <Tooltip cursor={xxCursor}
                         content={<AdvancedTooltip currency={data?.currency}
                                                   formats={{ default: 'currency' }} />} />
                <Line type="monotone" dataKey="value" name="Coș mediu" stroke={XX_SERIES_PURPLE}
                      strokeWidth={2} dot={false} connectNulls={false} />
              </LineChart>
            </Chart>

            <Chart title="Frecvența cumpărăturilor">
              <BarChart data={frequency} margin={{ top: 4, right: 6, bottom: 0, left: -24 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} />
                <YAxis {...xxAxisProps} width={40} allowDecimals={false} />
                <Tooltip cursor={xxBarCursor}
                         content={<AdvancedTooltip formats={{ default: 'number' }} />} />
                <Bar dataKey="value" name="Clienți" radius={[4, 4, 0, 0]}>
                  {frequency.map((entry, index) => (
                    <Cell key={entry.label} fill={XX_SERIES[index % XX_SERIES.length]} />
                  ))}
                </Bar>
              </BarChart>
            </Chart>

            <div className="min-w-0">
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
                text-[color:var(--xx-ink-dim)]">
                Segmente de clienți
              </p>
              <ul className="space-y-1.5">
                {(data?.segments || []).map((segment, index) => (
                  <li
                    key={segment.key}
                    className="rounded-lg border border-[rgba(255,255,255,0.1)]
                      bg-[rgba(255,255,255,0.03)] px-2.5 py-1.5"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="flex min-w-0 items-center gap-1.5">
                        <span
                          className="h-2 w-2 shrink-0 rounded-full"
                          style={{ background: XX_SERIES[index % XX_SERIES.length] }}
                          aria-hidden="true"
                        />
                        <span className="truncate text-xs font-medium text-[color:var(--xx-ink)]">
                          {segment.label}
                        </span>
                      </span>
                      <span className="shrink-0 text-xs tabular-nums text-[color:var(--xx-ink)]">
                        {segment.customers}
                        {segment.sharePct !== null && segment.sharePct !== undefined ? (
                          <span className="ml-1 text-[10px] text-[color:var(--xx-ink-dim)]">
                            ({segment.sharePct.toFixed(0)}%)
                          </span>
                        ) : null}
                      </span>
                    </div>
                    {/* The rule that produced the segment, so it can be
                        reproduced rather than trusted. */}
                    <p className="mt-0.5 text-[10px] text-[color:var(--xx-ink-dim)]">
                      {segment.definition} · {money(segment.revenue, data?.currency)}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          {data?.topCustomers?.length ? (
            <div className="mt-4 border-t border-[rgba(255,255,255,0.08)] pt-3">
              <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
                text-[color:var(--xx-ink-dim)]">
                Clienți cu valoarea cea mai mare
              </p>
              <ul className="xx-no-scrollbar max-h-40 space-y-0.5 overflow-y-auto pr-1">
                {data.topCustomers.slice(0, 8).map((customer) => (
                  <li key={customer.userId}>
                    <Link
                      to={`/admin/users?id=${customer.userId}`}
                      className="flex items-center justify-between gap-2 rounded-lg px-1.5 py-1
                        text-xs transition-colors duration-xx hover:bg-[rgba(255,255,255,0.04)]"
                    >
                      <span className="min-w-0 truncate text-[color:var(--xx-ink)]">
                        {customer.fullName || customer.email}
                      </span>
                      <span className="shrink-0 tabular-nums text-[color:var(--xx-ink-dim)]">
                        {customer.orders} com. · {money(customer.revenue, data.currency)}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </>
      )}
    </DashCard>
  );
}

function Chart({ title, children }) {
  return (
    <div className="min-w-0">
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
        text-[color:var(--xx-ink-dim)]">
        {title}
      </p>
      <div className="h-40">
        <ResponsiveContainer width="100%" height="100%">
          {children}
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function money(value, currency = 'RON') {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '—';
  return `${numeric.toLocaleString('ro-RO', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })} ${currency}`;
}
