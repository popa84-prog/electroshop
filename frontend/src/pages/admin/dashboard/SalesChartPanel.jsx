import { useMemo } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  AdvancedTooltip,
  DashCard,
  EmptyState,
  RangeSwitch,
  SHORT_RANGES,
  XXChartDefs,
  XX_SERIES_BLUE,
  XX_SERIES_GREEN,
  XX_GLOW_FILTER,
  xxAxisProps,
  xxCursor,
  xxGridProps,
  xxLegendProps,
} from '../../../components/xxii';
import metricsService from '../../../api/metricsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Revenue and profit over time. Task 2.
 *
 * ## Revenue and profit share one axis, on purpose
 *
 * Both are money in the same currency, so one scale compares them honestly and
 * the gap between the lines *is* the cost of goods sold — the most useful thing
 * this chart shows. A second axis scaled to profit would make a 4% margin look
 * like it tracks revenue closely, which is the single most misleading thing a
 * dual-axis chart does.
 *
 * ## The area is profit, the line is revenue
 *
 * Profit is filled because it is the quantity being accumulated; revenue is a
 * line above it. Filling both would hide the smaller one behind the larger, and
 * profit is always the smaller.
 */
export default function SalesChartPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('sales-chart', '30d', SHORT_RANGES);

  const { data, loading, error, reload } = usePanelData(
    (signal) => metricsService.financialOverview(range === '24h' ? '30d' : range, signal),
    [range]
  );

  // Revenue and profit arrive as separate series covering identical labels, so
  // they are zipped into one row per bucket. Recharts needs one array of
  // objects; feeding it two arrays would require two charts.
  const points = useMemo(() => {
    if (!data?.revenue) return [];
    return data.revenue.map((point, index) => ({
      label: point.label,
      revenue: Number(point.value ?? 0),
      profit: Number(data.profit?.[index]?.value ?? 0),
      count: point.count ?? null,
    }));
  }, [data]);

  const hasData = points.some((p) => p.revenue !== 0 || p.profit !== 0);

  return (
    <DashCard
      title={title}
      subtitle="Venit și profit realizat, pe intervalul selectat"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_BLUE}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={SHORT_RANGES} />}
      footer={
        data ? (
          <dl className="grid grid-cols-3 gap-2 text-xs">
            <Summary label="Venit" value={data.totalRevenue} currency={data.currency} />
            <Summary label="Profit" value={data.totalProfit} currency={data.currency} />
            <Summary
              label="Marjă"
              value={data.marginPct}
              suffix="%"
              missing="marjă indisponibilă"
            />
          </dl>
        ) : null
      }
    >
      {!loading && !hasData ? (
        <EmptyState reason="empty" compact={compact} />
      ) : (
        <div className={compact ? 'h-48' : 'h-64'}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={points} margin={{ top: 6, right: 6, bottom: 0, left: -18 }}>
              <XXChartDefs areaFills={[{ id: 'sales-profit', color: XX_SERIES_GREEN }]} />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} minTickGap={24} />
              <YAxis {...xxAxisProps} width={62} />
              <Tooltip
                cursor={xxCursor}
                content={
                  <AdvancedTooltip
                    currency={data?.currency || 'RON'}
                    formats={{ default: 'currency' }}
                  />
                }
              />
              {/* Two series, so a legend is present. Below two it would name one
                  thing the title already names. */}
              <Legend {...xxLegendProps} />
              <Area
                type="monotone"
                dataKey="profit"
                name="Profit"
                stroke={XX_SERIES_GREEN}
                strokeWidth={2}
                fill="url(#sales-profit)"
                filter={XX_GLOW_FILTER}
              />
              <Line
                type="monotone"
                dataKey="revenue"
                name="Venit"
                stroke={XX_SERIES_BLUE}
                strokeWidth={2}
                dot={false}
                filter={XX_GLOW_FILTER}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </DashCard>
  );
}

function Summary({ label, value, currency, suffix = '', missing = '—' }) {
  const numeric = Number(value);
  const has = value !== null && value !== undefined && Number.isFinite(numeric);

  return (
    <div className="min-w-0">
      <dt className="truncate text-[10px] uppercase tracking-[0.1em] text-[color:var(--xx-ink-dim)]">
        {label}
      </dt>
      <dd className="truncate font-semibold tabular-nums text-[color:var(--xx-ink)]">
        {has
          ? `${numeric.toLocaleString('ro-RO', {
              minimumFractionDigits: suffix ? 1 : 2,
              maximumFractionDigits: suffix ? 1 : 2,
            })}${suffix || (currency ? ` ${currency}` : '')}`
          : missing}
      </dd>
    </div>
  );
}
