import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  DashCard,
  DEFAULT_RANGES,
  EmptyState,
  RangeSwitch,
  SeverityBadge,
  XX_SERIES_MAGENTA,
} from '../../../components/xxii';
import analyticsService from '../../../api/analyticsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * The best-selling products, with commercial context. Task 6.
 *
 * ## Three rankings, switched locally
 *
 * Revenue, units and profit produce genuinely different lists — the product that
 * moves the most units is usually the cheapest, and the one that earns the most
 * profit is frequently not in the top ten by revenue at all. All three arrive in
 * one response, so switching is instant and, more importantly, the three are
 * computed from the same moment. Fetching each on demand would let them describe
 * different windows.
 *
 * ## Critical stock appears in the ranking
 *
 * A best-seller about to run out is the most expensive problem on the dashboard,
 * and it is invisible if the sales list and the stock panel are read separately.
 * The severity is computed from velocity rather than a fixed quantity, because
 * twenty units of a fast seller and twenty of a slow one are opposite situations.
 */
export default function TopProductsPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('top-products', '30d', DEFAULT_RANGES);
  const [metric, setMetric] = useState('byRevenue');
  const [category, setCategory] = useState('');
  const [brand, setBrand] = useState('');

  const { data, loading, error, reload } = usePanelData(
    (signal) => analyticsService.topProducts({ range, category, brand }, signal),
    [range, category, brand]
  );

  const metrics = [
    { key: 'byRevenue', label: 'Venit' },
    { key: 'byUnits', label: 'Bucăți' },
    { key: 'byProfit', label: 'Profit' },
  ];

  const rows = data?.[metric] || [];
  const promote = data?.promote || [];

  return (
    <DashCard
      title={title}
      subtitle="Cele mai vândute produse, cu starea stocului alături"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_MAGENTA}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
    >
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <div role="radiogroup" aria-label="Clasament după"
             className="inline-flex rounded-lg border border-[rgba(255,255,255,0.12)] p-0.5">
          {metrics.map((item) => (
            <button
              key={item.key}
              type="button"
              role="radio"
              aria-checked={metric === item.key}
              onClick={() => setMetric(item.key)}
              className={`rounded-md px-2 py-1 text-[11px] font-semibold transition-all duration-xx ${
                metric === item.key
                  ? 'bg-[rgba(208,50,184,0.18)] text-[#f07fdc]'
                  : 'text-[color:var(--xx-ink-dim)] hover:text-[color:var(--xx-ink)]'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>

        <Select label="Categorie" value={category} onChange={setCategory}
                options={data?.categories || []} />
        <Select label="Marcă" value={brand} onChange={setBrand} options={data?.brands || []} />
      </div>

      {!loading && rows.length === 0 ? (
        <EmptyState reason={category || brand ? 'filtered' : 'empty'} compact={compact} />
      ) : (
        <ol className="xx-no-scrollbar max-h-64 space-y-1 overflow-y-auto pr-1">
          {rows.map((product, index) => (
            <li key={product.productId ?? index}>
              <Link
                to={`/admin/products?id=${product.productId}`}
                className="flex items-center gap-2.5 rounded-lg px-1.5 py-1.5 transition-colors
                  duration-xx hover:bg-[rgba(255,255,255,0.04)]"
              >
                <span className="w-4 shrink-0 text-right text-[10px] tabular-nums
                  text-[color:var(--xx-ink-dim)]">
                  {index + 1}
                </span>

                {product.imageUrl ? (
                  <img
                    src={product.imageUrl}
                    alt=""
                    loading="lazy"
                    className="h-8 w-8 shrink-0 rounded-lg border border-[rgba(255,255,255,0.1)]
                      object-cover"
                  />
                ) : (
                  <span className="h-8 w-8 shrink-0 rounded-lg border
                    border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)]" aria-hidden="true" />
                )}

                <span className="min-w-0 flex-1">
                  <span className="block truncate text-xs text-[color:var(--xx-ink)]">
                    {product.name}
                  </span>
                  <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5
                    text-[10px] text-[color:var(--xx-ink-dim)]">
                    <span>{product.units} buc.</span>
                    <span>{money(product.revenue, data?.currency)}</span>
                    {product.marginPct !== null && product.marginPct !== undefined ? (
                      <span>{product.marginPct.toFixed(0)}% marjă</span>
                    ) : null}
                    <span>{product.stockQuantity} pe stoc</span>
                  </span>
                </span>

                <span className="shrink-0">
                  <SeverityBadge
                    level={product.stockSeverity}
                    label={
                      product.daysOfCover === null || product.daysOfCover === undefined
                        ? 'fără rotație'
                        : `${product.daysOfCover.toFixed(0)} z`
                    }
                    compact
                  />
                </span>
              </Link>
            </li>
          ))}
        </ol>
      )}

      {promote.length > 0 ? (
        <div className="mt-4 border-t border-[rgba(255,255,255,0.08)] pt-3">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Produse care ar trebui promovate
          </p>
          <ul className="space-y-1.5">
            {promote.slice(0, 3).map((item) => (
              <li key={item.productId} className="rounded-lg border border-[rgba(255,255,255,0.1)]
                bg-[rgba(255,255,255,0.03)] px-2.5 py-2">
                <Link
                  to={`/admin/products?id=${item.productId}`}
                  className="block truncate text-xs font-medium text-[color:var(--xx-ink)]
                    transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
                >
                  {item.headline}: {item.name}
                </Link>
                {/* The figures behind the suggestion, so it can be judged rather
                    than trusted. */}
                <p className="mt-0.5 text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
                  {item.rationale}
                </p>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </DashCard>
  );
}

function Select({ label, value, onChange, options }) {
  if (!options.length) return null;
  return (
    <label className="inline-flex items-center gap-1.5 text-[11px] text-[color:var(--xx-ink-dim)]">
      {label}
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-lg border border-[rgba(255,255,255,0.12)] bg-[rgba(9,10,26,0.9)]
          px-2 py-1 text-[11px] text-[color:var(--xx-ink)] focus:border-[color:var(--xx-cyan)]
          focus:outline-none"
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

function money(value, currency = 'RON') {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '—';
  return `${numeric.toLocaleString('ro-RO', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })} ${currency}`;
}
