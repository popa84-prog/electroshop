import { useMemo } from 'react';
import {
  Area,
  ComposedChart,
  CartesianGrid,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  AdvancedTooltip,
  DashCard,
  EmptyState,
  SeverityBadge,
  XXChartDefs,
  XX_SERIES_PURPLE,
  XX_SERIES_BLUE,
  xxAxisProps,
  xxCursor,
  xxGridProps,
} from '../../../components/xxii';
import metricsService from '../../../api/metricsService';
import usePanelData from '../../../hooks/usePanelData';

/**
 * The sales forecast. Task 2.
 *
 * ## The interval is drawn, not just returned
 *
 * The backend sends a lower and an upper bound with every projected point, and
 * the chart fills the band between them. A bare projection line invites being
 * read as a promise; a band that is visibly wider than the value it surrounds
 * says what the model actually knows, which on a few weeks of retail data is
 * frequently "not much".
 *
 * ## History and forecast are one continuous series
 *
 * They share an axis and meet at the boundary, marked by a reference line. Two
 * separate charts would put a gap where the most interesting comparison is —
 * whether the projection continues the recent trend or breaks from it.
 *
 * ## When there is not enough history, the card says what it is waiting for
 *
 * It does not draw a flat line at zero, which would be indistinguishable from a
 * forecast of no sales.
 */
export default function PredictiveSalesPanel({ compact, title, dragHandle, onHide }) {
  const { data, loading, error, reload } = usePanelData(
    (signal) => metricsService.predictiveSales(14, signal),
    []
  );

  // History and forecast are concatenated into one array. The forecast rows
  // carry `band` as a [lower, upper] pair, which is how Recharts draws an area
  // between two values rather than from zero.
  const points = useMemo(() => {
    if (!data?.sufficient) return [];

    const history = (data.history || []).map((p) => ({
      label: p.label,
      actual: Number(p.value ?? 0),
      forecast: null,
      band: null,
    }));

    const forecast = (data.forecast || []).map((p) => ({
      label: p.label,
      actual: null,
      forecast: Number(p.value ?? 0),
      band: [Number(p.lower ?? 0), Number(p.upper ?? 0)],
    }));

    // The last actual point is duplicated as the first forecast point so the two
    // lines touch. Without it there is a one-day gap that reads as missing data.
    if (history.length && forecast.length) {
      forecast.unshift({
        label: history[history.length - 1].label,
        actual: null,
        forecast: history[history.length - 1].actual,
        band: [history[history.length - 1].actual, history[history.length - 1].actual],
      });
    }

    return [...history, ...forecast];
  }, [data]);

  const boundary = data?.history?.length
    ? data.history[data.history.length - 1].label
    : null;

  return (
    <DashCard
      title={title}
      subtitle="Proiecție pe 14 zile, cu interval de încredere"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_PURPLE}
      toolbar={data ? <SeverityBadge level={data.confidence} compact /> : null}
      footer={
        data?.sufficient ? (
          <div className="space-y-1.5">
            <div className="flex flex-wrap items-baseline justify-between gap-2 text-xs">
              <span className="text-[color:var(--xx-ink-dim)]">
                Estimat următoarele {data.horizonDays} zile
              </span>
              <span className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                {money(data.forecastTotal, data.currency)}
              </span>
            </div>
            <p className="text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
              Interval {money(data.forecastLower, data.currency)} –{' '}
              {money(data.forecastUpper, data.currency)}
              {data.expectedChangePct !== null && data.expectedChangePct !== undefined ? (
                <>
                  {' · '}
                  <span
                    className={data.expectedChangePct >= 0 ? 'text-[#4fd3a0]' : 'text-[#ff8a97]'}
                  >
                    {data.expectedChangePct >= 0 ? '+' : ''}
                    {data.expectedChangePct.toFixed(1)}%
                  </span>{' '}
                  față de perioada echivalentă precedentă
                </>
              ) : null}
            </p>
            {/* The method is printed rather than implied. A projection whose
                derivation is invisible gets read as authoritative, and this one
                is a trend line with a weekday index — useful, and not a model. */}
            <p className="text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)] opacity-80">
              {data.method}
            </p>
          </div>
        ) : null
      }
    >
      {!loading && !data?.sufficient ? (
        <EmptyState
          reason="collecting"
          title="Istoric insuficient pentru o prognoză"
          description={`Sunt necesare cel puțin ${data?.minHistoryDays ?? 21} de zile de comenzi. `
            + `În acest moment există ${data?.historyDays ?? 0}. O linie ajustată pe mai puțin `
            + 'ar fi aritmetică, nu predicție.'}
          compact={compact}
        />
      ) : (
        <div className={compact ? 'h-40' : 'h-56'}>
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={points} margin={{ top: 6, right: 6, bottom: 0, left: -18 }}>
              <XXChartDefs areaFills={[{ id: 'forecast-band', color: XX_SERIES_PURPLE }]} />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} minTickGap={28} />
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
              <Area
                dataKey="band"
                name="Interval"
                stroke="none"
                fill="url(#forecast-band)"
                fillOpacity={0.35}
                isAnimationActive={false}
              />
              <Line
                type="monotone"
                dataKey="actual"
                name="Realizat"
                stroke={XX_SERIES_BLUE}
                strokeWidth={2}
                dot={false}
                connectNulls={false}
              />
              <Line
                type="monotone"
                dataKey="forecast"
                name="Estimat"
                stroke={XX_SERIES_PURPLE}
                strokeWidth={2}
                // Dashed, because a projection drawn identically to measured data
                // is a projection somebody will quote as a measurement.
                strokeDasharray="5 4"
                dot={false}
                connectNulls={false}
              />
              {boundary ? (
                <ReferenceLine
                  x={boundary}
                  stroke="rgba(255,255,255,0.25)"
                  strokeDasharray="3 3"
                  label={{
                    value: 'azi',
                    position: 'insideTopRight',
                    fill: 'rgba(255,255,255,0.45)',
                    fontSize: 10,
                  }}
                />
              ) : null}
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
    </DashCard>
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
