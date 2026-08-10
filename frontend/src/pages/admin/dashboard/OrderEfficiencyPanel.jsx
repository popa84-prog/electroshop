import {
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
import { Link } from 'react-router-dom';
import {
  AdvancedTooltip,
  DashCard,
  DataTable,
  DEFAULT_RANGES,
  EmptyState,
  RangeSwitch,
  SeverityBadge,
  TrendPill,
  XXChartDefs,
  XX_SERIES_AMBER,
  XX_SERIES_BLUE,
  xxAxisProps,
  xxBarCursor,
  xxCursor,
  xxGridProps,
} from '../../../components/xxii';
import analyticsService from '../../../api/analyticsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Order processing efficiency. Task 15.
 *
 * ## Coverage is stated, because two KPIs depend on new data
 *
 * Average processing and delivery time are measured from status transitions,
 * which only started being recorded when this shipped. Orders placed before then
 * have no history and are excluded from those averages. The coverage line says
 * how many of the window's orders are actually measured — an average over four
 * of nine hundred is not wrong, but it is not the business either, and without
 * the ratio a reader cannot tell which they are looking at.
 *
 * The two rate KPIs do not have this problem: they come from the order's final
 * status, which has always been stored, and are complete from the first day.
 *
 * ## Lower is better for every KPI here
 *
 * The trend badges invert accordingly. A dashboard that paints a rising delivery
 * time green because the number went up is worse than one with no badge at all.
 */
export default function OrderEfficiencyPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('order-efficiency', '30d', DEFAULT_RANGES);

  const { data, loading, error, reload } = usePanelData(
    (signal) => analyticsService.orderEfficiency(range, signal),
    [range]
  );

  const measured = data?.ordersWithHistory ?? 0;
  const total = data?.ordersInWindow ?? 0;
  const partialCoverage = total > 0 && measured < total;

  const kpis = [
    {
      label: 'Timp mediu procesare',
      value: data?.avgProcessingHours,
      unit: 'h',
      delta: data?.processingDelta,
      needsHistory: true,
    },
    {
      label: 'Timp mediu livrare',
      value: data?.avgDeliveryHours,
      unit: 'h',
      delta: data?.deliveryDelta,
      needsHistory: true,
    },
    {
      label: 'Rata retururi',
      value: data?.returnRatePct,
      unit: '%',
      delta: data?.returnRateDelta,
      needsHistory: false,
    },
    {
      label: 'Rata anulări',
      value: data?.cancelRatePct,
      unit: '%',
      delta: data?.cancelRateDelta,
      needsHistory: false,
    },
  ];

  return (
    <DashCard
      title={title}
      subtitle="Durata etapelor și rata de retur, pe intervalul selectat"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_BLUE}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
    >
      <div className="grid grid-cols-2 gap-2 xl:grid-cols-4">
        {kpis.map((kpi) => (
          <div
            key={kpi.label}
            className="rounded-xl border border-[rgba(255,255,255,0.1)]
              bg-[rgba(255,255,255,0.03)] p-2.5"
          >
            <p className="text-[10px] uppercase tracking-[0.1em] text-[color:var(--xx-ink-dim)]">
              {kpi.label}
            </p>
            <p className="mt-1 font-display text-lg font-semibold tabular-nums
              text-[color:var(--xx-ink)]">
              {kpi.value === null || kpi.value === undefined ? (
                <span className="text-sm font-normal text-[color:var(--xx-ink-dim)]">
                  {kpi.needsHistory ? 'nemăsurat încă' : '—'}
                </span>
              ) : (
                `${Number(kpi.value).toLocaleString('ro-RO', { maximumFractionDigits: 1 })}${kpi.unit}`
              )}
            </p>
            {kpi.delta && kpi.value !== null && kpi.value !== undefined ? (
              <TrendPill delta={kpi.delta} compact className="mt-1" />
            ) : null}
          </div>
        ))}
      </div>

      {partialCoverage ? (
        <p className="mt-2 rounded-lg border border-[rgba(46,123,255,0.35)]
          bg-[rgba(46,123,255,0.08)] px-2.5 py-1.5 text-[10px] leading-relaxed
          text-[color:var(--xx-ink-dim)]">
          Duratele se calculează din {measured} din cele {total} comenzi ale perioadei.
          Comenzile plasate înainte de începerea înregistrării tranzițiilor nu au istoric de
          etape și sunt excluse; ratele de mai sus acoperă însă toate comenzile.
        </p>
      ) : null}

      {!loading && total === 0 ? (
        <EmptyState reason="empty" compact={compact} className="mt-3" />
      ) : (
        <>
          <div className="mt-4 grid gap-4 xl:grid-cols-2">
            <Chart title="Durata etapelor (ore)">
              <LineChart data={mergeSeries(data)} margin={{ top: 4, right: 6, bottom: 0, left: -22 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} minTickGap={24} />
                <YAxis {...xxAxisProps} width={48} />
                <Tooltip cursor={xxCursor}
                         content={<AdvancedTooltip formats={{ default: 'hours' }} />} />
                <Line type="monotone" dataKey="processing" name="Procesare"
                      stroke={XX_SERIES_BLUE} strokeWidth={2} dot={false} connectNulls={false} />
                <Line type="monotone" dataKey="delivery" name="Livrare"
                      stroke={XX_SERIES_AMBER} strokeWidth={2} dot={false} connectNulls={false} />
              </LineChart>
            </Chart>

            <Chart title="Volum comenzi">
              <BarChart data={(data?.volumeSeries || []).map(toPoint)}
                        margin={{ top: 4, right: 6, bottom: 0, left: -22 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} minTickGap={24} />
                <YAxis {...xxAxisProps} width={44} allowDecimals={false} />
                <Tooltip cursor={xxBarCursor}
                         content={<AdvancedTooltip formats={{ default: 'number' }} />} />
                <Bar dataKey="value" name="Comenzi" fill={XX_SERIES_BLUE} radius={[4, 4, 0, 0]} />
              </BarChart>
            </Chart>
          </div>

          {data?.returnReasons?.length ? (
            <div className="mt-4">
              <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
                text-[color:var(--xx-ink-dim)]">
                Motive de retur
              </p>
              <ul className="flex flex-wrap gap-1.5">
                {data.returnReasons.map((reason) => (
                  <li
                    key={reason.reason}
                    className="rounded-full border border-[rgba(255,255,255,0.14)]
                      bg-[rgba(255,255,255,0.04)] px-2 py-0.5 text-[11px]
                      text-[color:var(--xx-ink-dim)]"
                  >
                    {reason.reason}
                    <span className="ml-1 tabular-nums text-[color:var(--xx-ink)]">
                      {reason.count}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          <div className="mt-4">
            <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
              text-[color:var(--xx-ink-dim)]">
              Comenzile cu cea mai lungă procesare
            </p>
            <DataTable
              compact
              maxHeight="16rem"
              rowKey="orderId"
              rows={data?.slowest || []}
              emptyMessage="Nicio comandă cu etape măsurate în perioada selectată."
              columns={[
                {
                  key: 'orderId',
                  label: 'Comandă',
                  render: (row) => (
                    <Link
                      to={`/admin/orders?id=${row.orderId}`}
                      className="transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
                    >
                      #{row.orderId}
                    </Link>
                  ),
                },
                {
                  key: 'customerEmail',
                  label: 'Client',
                  render: (row) => (
                    <span className="block max-w-[12rem] truncate" title={row.customerEmail}>
                      {row.customerEmail}
                    </span>
                  ),
                },
                { key: 'status', label: 'Status' },
                {
                  key: 'processingHours',
                  label: 'Procesare',
                  align: 'right',
                  render: (row) => hours(row.processingHours),
                },
                {
                  key: 'deliveryHours',
                  label: 'Livrare',
                  align: 'right',
                  render: (row) => hours(row.deliveryHours),
                },
                {
                  key: 'flag',
                  label: '',
                  sortable: false,
                  render: (row) => <SeverityBadge level={row.flag} compact />,
                },
              ]}
            />
          </div>
        </>
      )}
    </DashCard>
  );
}

/**
 * Zips the two duration series into one array.
 *
 * They cover identical buckets, and Recharts needs one array of objects to draw
 * two lines on shared axes.
 */
function mergeSeries(data) {
  if (!data?.processingSeries) return [];
  return data.processingSeries.map((point, index) => ({
    label: point.label,
    processing: point.value === null || point.value === undefined ? null : Number(point.value),
    delivery: numberOrNull(data.deliverySeries?.[index]?.value),
    count: point.count ?? null,
  }));
}

function toPoint(point) {
  return { label: point.label, value: Number(point.value ?? 0), count: point.count ?? null };
}

function numberOrNull(value) {
  return value === null || value === undefined ? null : Number(value);
}

function hours(value) {
  // Null is "not measured", which is not the same as zero hours. Rendering it as
  // a dash keeps an unmeasured stage from reading as instant processing.
  if (value === null || value === undefined) {
    return <span className="text-[color:var(--xx-ink-dim)]">—</span>;
  }
  return `${Number(value).toLocaleString('ro-RO', { maximumFractionDigits: 1 })} h`;
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
