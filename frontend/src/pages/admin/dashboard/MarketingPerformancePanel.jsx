import {
  CartesianGrid,
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
  DataTable,
  DEFAULT_RANGES,
  EmptyState,
  RangeSwitch,
  SeverityBadge,
  TrendPill,
  XXChartDefs,
  XX_SERIES_AMBER,
  XX_SERIES_BLUE,
  XX_SERIES_MAGENTA,
  xxAxisProps,
  xxCursor,
  xxGridProps,
  xxLegendProps,
} from '../../../components/xxii';
import analyticsService from '../../../api/analyticsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Campaign performance. Task 17.
 *
 * ## This panel starts empty, and says why
 *
 * Click-through and conversion rates are ratios over counted interactions, and
 * nothing was counting them before this shipped. There is no history to
 * reconstruct — the events did not happen anywhere that kept them.
 *
 * So when `collectingSince` is absent the panel says measurement has not started
 * rather than drawing zeroes. A campaign showing 0% conversion and a campaign
 * nobody has measured are different facts and an operator acts differently on
 * each: the first is a campaign to stop, the second is one to wait on. Charts
 * full of plausible invented numbers would have looked better and been worse.
 */
export default function MarketingPerformancePanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('marketing-performance', '30d', DEFAULT_RANGES);

  const { data, loading, error, reload } = usePanelData(
    (signal) => analyticsService.marketingPerformance(range, signal),
    [range]
  );

  const collecting = Boolean(data?.collectingSince);
  const hasTraffic = (data?.totalImpressions ?? 0) > 0;

  const funnel = (data?.evolution || []).map((point, index) => ({
    label: point.label,
    impresii: Number(point.value ?? 0),
    clickuri: Number(point.secondary ?? 0),
    conversii: Number(data?.conversions?.[index]?.value ?? 0),
  }));

  return (
    <DashCard
      title={title}
      subtitle="Impresii, click-uri și conversii pe campanie"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_MAGENTA}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
      footer={
        hasTraffic ? (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs">
            <Stat label="CTR" value={data.ctrPct} suffix="%" delta={data.ctrDelta} />
            <Stat label="Conversie" value={data.conversionPct} suffix="%"
                  delta={data.conversionDelta} />
            <Stat label="Cost/achiziție" value={data.costPerAcquisition}
                  currency={data.currency}
                  missing="fără cost înregistrat" />
            <span className="text-[color:var(--xx-ink-dim)]">
              {data.totalImpressions} impresii · {data.totalClicks} click-uri ·{' '}
              {data.totalConversions} conversii
            </span>
          </div>
        ) : null
      }
    >
      {!loading && !collecting ? (
        <EmptyState
          reason="collecting"
          title="Măsurarea campaniilor nu a început încă"
          description="Impresiile și click-urile se înregistrează din momentul în care
            magazinul afișează prima ofertă instrumentată. Nu există istoric retroactiv de
            reconstruit, iar cifrele devin relevante pe măsură ce se acumulează trafic."
          compact={compact}
        />
      ) : !loading && !hasTraffic ? (
        <EmptyState
          reason="collecting"
          since={data.collectingSince}
          title="Nicio impresie în perioada selectată"
          compact={compact}
        />
      ) : (
        <>
          <div className="h-44">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={funnel} margin={{ top: 4, right: 6, bottom: 0, left: -24 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} minTickGap={24} />
                <YAxis {...xxAxisProps} width={44} allowDecimals={false} />
                <Tooltip cursor={xxCursor}
                         content={<AdvancedTooltip formats={{ default: 'number' }} />} />
                <Legend {...xxLegendProps} />
                <Line type="monotone" dataKey="impresii" name="Impresii" stroke={XX_SERIES_BLUE}
                      strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="clickuri" name="Click-uri"
                      stroke={XX_SERIES_AMBER} strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="conversii" name="Conversii"
                      stroke={XX_SERIES_MAGENTA} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div className="mt-4">
            <DataTable
              compact
              maxHeight="16rem"
              rowKey="offerId"
              rows={data?.campaigns || []}
              emptyMessage="Nicio campanie definită."
              columns={[
                {
                  key: 'title',
                  label: 'Campanie',
                  render: (row) => (
                    <Link
                      to={`/admin/offers?id=${row.offerId}`}
                      className="block max-w-[14rem] truncate transition-colors duration-xx
                        hover:text-[color:var(--xx-cyan)]"
                      title={row.title}
                    >
                      {row.title}
                    </Link>
                  ),
                },
                { key: 'status', label: 'Stare' },
                { key: 'impressions', label: 'Impresii', align: 'right' },
                { key: 'clicks', label: 'Click-uri', align: 'right' },
                { key: 'conversions', label: 'Conversii', align: 'right' },
                {
                  key: 'ctrPct',
                  label: 'CTR',
                  align: 'right',
                  render: (row) => percent(row.ctrPct),
                },
                {
                  key: 'costPerAcquisition',
                  label: 'Cost/achiz.',
                  align: 'right',
                  render: (row) =>
                    row.costPerAcquisition === null || row.costPerAcquisition === undefined
                      ? <span className="text-[color:var(--xx-ink-dim)]">—</span>
                      : money(row.costPerAcquisition, data?.currency),
                },
                {
                  key: 'verdict',
                  label: 'Verdict',
                  sortable: false,
                  // NO_DATA is a distinct badge, not a bad score. A campaign
                  // nobody has clicked twenty times has not failed; it has not
                  // been tested, and colouring it red would kill it early.
                  render: (row) => <SeverityBadge level={row.verdict} compact />,
                },
              ]}
            />
          </div>

          {data?.collectingSince ? (
            <p className="mt-2 text-[10px] text-[color:var(--xx-ink-dim)]">
              Colectarea a început pe{' '}
              {new Date(data.collectingSince).toLocaleDateString('ro-RO')}.
            </p>
          ) : null}
        </>
      )}
    </DashCard>
  );
}

function Stat({ label, value, suffix = '', currency = null, delta = null, missing = '—' }) {
  const numeric = Number(value);
  const has = value !== null && value !== undefined && Number.isFinite(numeric);

  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="text-[color:var(--xx-ink-dim)]">{label}</span>
      <strong className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
        {has
          ? `${numeric.toLocaleString('ro-RO', { maximumFractionDigits: 2 })}${
              suffix || (currency ? ` ${currency}` : '')
            }`
          : missing}
      </strong>
      {delta && has ? <TrendPill delta={delta} compact /> : null}
    </span>
  );
}

function percent(value) {
  if (value === null || value === undefined) {
    return <span className="text-[color:var(--xx-ink-dim)]">—</span>;
  }
  return `${Number(value).toFixed(2)}%`;
}

function money(value, currency = 'RON') {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '—';
  return `${numeric.toLocaleString('ro-RO', { maximumFractionDigits: 2 })} ${currency}`;
}
