import { useState } from 'react';
import {
  Bar,
  BarChart,
  Cell,
  CartesianGrid,
  Legend,
  Pie,
  PieChart,
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
  XXChartDefs,
  XX_SERIES,
  XX_SERIES_GREEN,
  xxAxisProps,
  xxBarCursor,
  xxGridProps,
  xxLegendProps,
} from '../../../components/xxii';
import metricsService from '../../../api/metricsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Where the profit comes from: category, brand and product. Task 12.
 *
 * ## Three charts, one request
 *
 * The three views answer the same question at three granularities and are read
 * together, so they arrive together and are computed from one moment. Fetching
 * each on demand would let the category chart describe a window the brand chart
 * has already left.
 *
 * ## The donut says how much it folded away
 *
 * A brand chart with a hundred and eighty slices communicates nothing, so the
 * tail is summed into "Altele". The count of folded brands is printed under the
 * chart, because "Altele" covering three brands and "Altele" covering ninety are
 * different pictures and the slice looks identical either way.
 *
 * ## Realised profit, not potential
 *
 * Every figure comes from what was actually sold, at the prices and costs
 * captured on the order line at the time. It does not move when somebody edits a
 * price today.
 */
export default function ProfitBreakdownPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('profit-breakdown', '30d', DEFAULT_RANGES);
  const [category, setCategory] = useState('');
  const [brand, setBrand] = useState('');

  const { data, loading, error, reload } = usePanelData(
    (signal) => metricsService.profitBreakdown({ range, category, brand }, signal),
    [range, category, brand]
  );

  const hasData = (data?.byCategory?.length || 0) > 0;

  return (
    <DashCard
      title={title}
      subtitle="Profit realizat pe categorie, marcă și produs"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_GREEN}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
      footer={
        data ? (
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
            <span className="text-[color:var(--xx-ink-dim)]">
              Profit total{' '}
              <strong className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                {money(data.totalProfit, data.currency)}
              </strong>
              {data.marginPct !== null && data.marginPct !== undefined
                ? ` · marjă ${data.marginPct.toFixed(1)}%`
                : ''}
            </span>
            {data.itemsWithoutCost > 0 ? (
              <span className="text-[10px] text-[#e0bd4a]">
                {data.itemsWithoutCost} linii fără cost înregistrat, excluse din profit
              </span>
            ) : null}
          </div>
        ) : null
      }
    >
      {(data?.byCategory?.length || data?.byBrand?.length) ? (
        <div className="mb-3 flex flex-wrap gap-2">
          <FilterSelect
            label="Categorie"
            value={category}
            onChange={setCategory}
            options={(data?.byCategory || []).map((s) => s.label)}
          />
          <FilterSelect
            label="Marcă"
            value={brand}
            onChange={setBrand}
            options={(data?.byBrand || []).map((s) => s.label).filter((l) => l !== 'Altele')}
          />
          {(category || brand) ? (
            <button
              type="button"
              onClick={() => {
                setCategory('');
                setBrand('');
              }}
              className="rounded-lg border border-[rgba(255,255,255,0.14)] px-2 py-1 text-[11px]
                text-[color:var(--xx-ink-dim)] transition-colors duration-xx
                hover:text-[color:var(--xx-ink)]"
            >
              Golește filtrele
            </button>
          ) : null}
        </div>
      ) : null}

      {!loading && !hasData ? (
        <EmptyState reason={category || brand ? 'filtered' : 'empty'} compact={compact} />
      ) : (
        <div className="grid gap-5 xl:grid-cols-3">
          <ChartBlock title="Profit pe categorie">
            <BarChart
              data={(data?.byCategory || []).map((s) => ({ ...s, value: Number(s.profit ?? 0) }))}
              margin={{ top: 4, right: 4, bottom: 0, left: -20 }}
            >
              <XXChartDefs />
              <CartesianGrid {...xxGridProps} />
              <XAxis dataKey="label" {...xxAxisProps} interval={0} angle={-25} textAnchor="end"
                     height={54} />
              <YAxis {...xxAxisProps} width={58} />
              <Tooltip
                cursor={xxBarCursor}
                content={
                  <AdvancedTooltip
                    currency={data?.currency}
                    formats={{ default: 'currency' }}
                    totals={{ value: data?.totalProfit }}
                  />
                }
              />
              <Bar dataKey="value" name="Profit" radius={[4, 4, 0, 0]}>
                {(data?.byCategory || []).map((slice, index) => (
                  <Cell key={slice.label} fill={XX_SERIES[index % XX_SERIES.length]} />
                ))}
              </Bar>
            </BarChart>
          </ChartBlock>

          <ChartBlock
            title="Profit pe marcă"
            note={
              data?.brandsAggregated > 0
                ? `„Altele” cuprinde ${data.brandsAggregated} mărci`
                : null
            }
          >
            <PieChart>
              <Tooltip
                content={
                  <AdvancedTooltip
                    currency={data?.currency}
                    formats={{ default: 'currency' }}
                    totals={{ value: data?.totalProfit }}
                  />
                }
              />
              <Legend {...xxLegendProps} />
              <Pie
                data={(data?.byBrand || []).map((s) => ({ ...s, value: Number(s.profit ?? 0) }))}
                dataKey="value"
                nameKey="label"
                innerRadius="52%"
                outerRadius="80%"
                paddingAngle={2}
                // A 2px gap between slices in the surface colour, so adjacent
                // segments stay separable without relying on hue alone.
                stroke="#0a0b1e"
                strokeWidth={2}
              >
                {(data?.byBrand || []).map((slice, index) => (
                  <Cell key={slice.label} fill={XX_SERIES[index % XX_SERIES.length]} />
                ))}
              </Pie>
            </PieChart>
          </ChartBlock>

          <div className="min-w-0">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
              text-[color:var(--xx-ink-dim)]">
              Top 10 produse după profit
            </p>
            <ol className="space-y-1.5">
              {(data?.topProducts || []).map((product, index) => (
                <li key={product.productId ?? index}>
                  <Link
                    to={`/admin/products?id=${product.productId}`}
                    className="flex items-center gap-2 rounded-lg px-1.5 py-1 transition-colors
                      duration-xx hover:bg-[rgba(255,255,255,0.04)]"
                  >
                    <span className="w-4 shrink-0 text-right text-[10px] tabular-nums
                      text-[color:var(--xx-ink-dim)]">
                      {index + 1}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-xs text-[color:var(--xx-ink)]">
                        {product.name}
                      </span>
                      {/* The bar is a proportion, drawn against the top row so the
                          ranking is readable at a glance without an axis. */}
                      <span
                        className="mt-0.5 block h-1 rounded-full bg-[rgba(31,172,121,0.75)]"
                        style={{
                          width: `${barWidth(product.profit, data?.topProducts?.[0]?.profit)}%`,
                        }}
                        aria-hidden="true"
                      />
                    </span>
                    <span className="shrink-0 text-right">
                      <span className="block text-xs font-semibold tabular-nums
                        text-[color:var(--xx-ink)]">
                        {money(product.profit, data?.currency)}
                      </span>
                      {product.marginPct !== null && product.marginPct !== undefined ? (
                        <span className="block text-[10px] text-[color:var(--xx-ink-dim)]">
                          {product.marginPct.toFixed(1)}% marjă
                        </span>
                      ) : null}
                    </span>
                  </Link>
                </li>
              ))}
            </ol>
          </div>
        </div>
      )}
    </DashCard>
  );
}

function ChartBlock({ title, note, children }) {
  return (
    <div className="min-w-0">
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
        text-[color:var(--xx-ink-dim)]">
        {title}
      </p>
      <div className="h-52">
        <ResponsiveContainer width="100%" height="100%">
          {children}
        </ResponsiveContainer>
      </div>
      {note ? (
        <p className="mt-1 text-[10px] text-[color:var(--xx-ink-dim)]">{note}</p>
      ) : null}
    </div>
  );
}

function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="inline-flex items-center gap-1.5 text-[11px] text-[color:var(--xx-ink-dim)]">
      {label}
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-[rgba(255,255,255,0.12)] bg-[rgba(9,10,26,0.9)]
          px-2 py-1 text-[11px] text-[color:var(--xx-ink)] transition-colors duration-xx
          focus:border-[color:var(--xx-cyan)] focus:outline-none"
      >
        <option value="">Toate</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function barWidth(value, max) {
  const v = Number(value);
  const m = Number(max);
  if (!Number.isFinite(v) || !Number.isFinite(m) || m <= 0) return 0;
  // Floored at 2% so a small but non-zero profit is still visible as a mark
  // rather than vanishing into the row.
  return Math.max(2, Math.min(100, (v / m) * 100));
}

function money(value, currency = 'RON') {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '—';
  return `${numeric.toLocaleString('ro-RO', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })} ${currency}`;
}
