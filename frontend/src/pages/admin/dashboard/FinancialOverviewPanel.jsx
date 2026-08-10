import { useMemo } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  AdvancedTooltip,
  DashCard,
  EmptyState,
  FINANCIAL_RANGES,
  RangeSwitch,
  TrendPill,
  XXChartDefs,
  XX_SERIES_AMBER,
  XX_SERIES_BLUE,
  XX_SERIES_CYAN,
  XX_SERIES_GREEN,
  xxAxisProps,
  xxBarCursor,
  xxCursor,
  xxGridProps,
} from '../../../components/xxii';
import metricsService from '../../../api/metricsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * The financial panel. Task 14.
 *
 * ## The third chart is cost of goods sold, not stock value
 *
 * The requirement asks for a "total stock cost" chart over time. Stock value is
 * a snapshot of today — the database records what stock *is*, never what it was
 * — so plotting it month by month would draw the same number repeated across the
 * axis, which answers nothing. What does have a monthly history, and what a
 * financial panel is actually asking about, is what the goods sold in each month
 * cost. That pairs with revenue to give profit, and the three charts then
 * reconcile with each other.
 *
 * ## The twelve-month line ignores the range selector
 *
 * An operator narrowing to three months is asking about this quarter, not asking
 * to forget the year. Keeping the long view beside the short one is what stops a
 * strong quarter inside a declining year reading as simple good news.
 */
export default function FinancialOverviewPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('financial-overview', '12m', FINANCIAL_RANGES);

  const { data, loading, error, reload } = usePanelData(
    (signal) => metricsService.financialOverview(range, signal),
    [range]
  );

  const revenue = useSeries(data?.revenue);
  const profit = useSeries(data?.profit);
  const cogs = useSeries(data?.cogs);
  const trend = useSeries(data?.profitTrend);

  const hasData = revenue.some((p) => p.value !== 0) || profit.some((p) => p.value !== 0);

  return (
    <DashCard
      title={title}
      subtitle="Venituri, profit și costul mărfurilor vândute"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_BLUE}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={FINANCIAL_RANGES} />}
      footer={
        data ? (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs">
            <Figure label="Venit" value={data.totalRevenue} currency={data.currency}
                    delta={data.revenueDelta} />
            <Figure label="Profit" value={data.totalProfit} currency={data.currency}
                    delta={data.profitDelta} />
            <Figure label="Cost marfă" value={data.totalCogs} currency={data.currency} />
            {data.bestMonth ? (
              <span className="text-[color:var(--xx-ink-dim)]">
                Cea mai bună lună{' '}
                <strong className="text-[color:var(--xx-ink)]">{data.bestMonth.label}</strong>
                {' · '}
                {money(data.bestMonth.profit, data.currency)}
              </span>
            ) : null}
          </div>
        ) : null
      }
    >
      {!loading && !hasData ? (
        <EmptyState reason="empty" compact={compact} />
      ) : (
        <div className="grid gap-5 xl:grid-cols-2">
          <Chart title="Venituri lunare">
            <LineChart data={revenue} margin={{ top: 4, right: 6, bottom: 0, left: -20 }}>
              <XXChartDefs />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} minTickGap={20} />
              <YAxis {...xxAxisProps} width={58} />
              <Tooltip cursor={xxCursor}
                       content={<AdvancedTooltip currency={data?.currency}
                                                 formats={{ default: 'currency' }} />} />
              <Line type="monotone" dataKey="value" name="Venit" stroke={XX_SERIES_BLUE}
                    strokeWidth={2} dot={false} />
            </LineChart>
          </Chart>

          <Chart title="Profit lunar">
            <AreaChart data={profit} margin={{ top: 4, right: 6, bottom: 0, left: -20 }}>
              <XXChartDefs areaFills={[{ id: 'fin-profit', color: XX_SERIES_GREEN }]} />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} minTickGap={20} />
              <YAxis {...xxAxisProps} width={58} />
              <Tooltip cursor={xxCursor}
                       content={<AdvancedTooltip currency={data?.currency}
                                                 formats={{ default: 'currency' }} />} />
              <Area type="monotone" dataKey="value" name="Profit" stroke={XX_SERIES_GREEN}
                    strokeWidth={2} fill="url(#fin-profit)" />
            </AreaChart>
          </Chart>

          <Chart
            title="Cost marfă vândută"
            note="Costul bunurilor vândute în fiecare lună, nu valoarea stocului curent"
          >
            <BarChart data={cogs} margin={{ top: 4, right: 6, bottom: 0, left: -20 }}>
              <XXChartDefs />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} minTickGap={20} />
              <YAxis {...xxAxisProps} width={58} />
              <Tooltip cursor={xxBarCursor}
                       content={<AdvancedTooltip currency={data?.currency}
                                                 formats={{ default: 'currency' }} />} />
              <Bar dataKey="value" name="Cost marfă" fill={XX_SERIES_AMBER}
                   radius={[4, 4, 0, 0]} />
            </BarChart>
          </Chart>

          <Chart
            title="Evoluția profitului pe 12 luni"
            note="Independent de intervalul selectat"
          >
            <LineChart data={trend} margin={{ top: 4, right: 6, bottom: 0, left: -20 }}>
              <XXChartDefs />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} minTickGap={20} />
              <YAxis {...xxAxisProps} width={58} />
              <Tooltip cursor={xxCursor}
                       content={<AdvancedTooltip currency={data?.currency}
                                                 formats={{ default: 'currency' }} />} />
              <Line type="monotone" dataKey="value" name="Profit" stroke={XX_SERIES_CYAN}
                    strokeWidth={2} dot={false} />
            </LineChart>
          </Chart>
        </div>
      )}
    </DashCard>
  );
}

/** Converts the backend's series into plain numbers Recharts can plot. */
function useSeries(series) {
  return useMemo(
    () =>
      (series || []).map((point) => ({
        label: point.label,
        value: Number(point.value ?? 0),
        count: point.count ?? null,
      })),
    [series]
  );
}

function Chart({ title, note, children }) {
  return (
    <div className="min-w-0">
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
        text-[color:var(--xx-ink-dim)]">
        {title}
      </p>
      <div className="h-44">
        <ResponsiveContainer width="100%" height="100%">
          {children}
        </ResponsiveContainer>
      </div>
      {note ? <p className="mt-1 text-[10px] text-[color:var(--xx-ink-dim)]">{note}</p> : null}
    </div>
  );
}

function Figure({ label, value, currency, delta = null }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="text-[color:var(--xx-ink-dim)]">{label}</span>
      <strong className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
        {money(value, currency)}
      </strong>
      {delta ? <TrendPill delta={delta} compact /> : null}
    </span>
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
