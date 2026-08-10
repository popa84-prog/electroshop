import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  DashCard,
  DEFAULT_RANGES,
  EmptyState,
  RangeSwitch,
  SeverityBadge,
  XX_SERIES_CYAN,
} from '../../../components/xxii';
import analyticsService from '../../../api/analyticsService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Rising, declining and stagnant products. Task 18.
 *
 * ## Both the percentage and the absolute movement are shown
 *
 * A product that went from two units to six grew 200%; one that went from four
 * hundred to four hundred and forty grew 10% and sold seventy times more. The
 * ranking uses absolute units for exactly that reason, and both figures appear
 * on every row so a large percentage over a tiny base is visibly that.
 *
 * The backend withholds the percentage entirely below a minimum baseline, and
 * the panel prints what that floor is — a reader who does not know a product was
 * excluded will assume it did not move.
 *
 * ## Stagnant means "had stock and sold nothing"
 *
 * A product with no sales because it is out of stock is an inventory failure and
 * belongs in the inventory panel, where somebody can order more. Listing it here
 * would send an operator to discount something they cannot ship.
 */
export default function ProductPerformancePanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('product-performance', '30d', DEFAULT_RANGES);
  const [tab, setTab] = useState('rising');

  const { data, loading, error, reload } = usePanelData(
    (signal) => analyticsService.productPerformance(range, signal),
    [range]
  );

  const tabs = [
    { key: 'rising', label: 'În creștere', count: data?.rising?.length },
    { key: 'declining', label: 'În scădere', count: data?.declining?.length },
    { key: 'stagnant', label: 'Stagnante', count: data?.stagnant?.length },
    { key: 'recommendations', label: 'Recomandări', count: data?.recommendations?.length },
  ];

  return (
    <DashCard
      title={title}
      subtitle={
        data
          ? `Comparație cu perioada precedentă; procentul se raportează doar peste `
            + `${data.minVolumeForTrend} bucăți în perioada de referință`
          : null
      }
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_CYAN}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
      footer={
        data ? (
          <p className="text-xs text-[color:var(--xx-ink-dim)]">
            {data.productsAnalysed} produse cu suficient istoric pentru a fi evaluate
          </p>
        ) : null
      }
    >
      <div className="mb-3 flex flex-wrap gap-1.5">
        {tabs.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            aria-pressed={tab === item.key}
            className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1
              text-[11px] font-medium transition-all duration-xx ${
                tab === item.key
                  ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-cyan)]'
                  : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-dim)] hover:text-[color:var(--xx-ink)]'
              }`}
          >
            {item.label}
            {item.count ? <span className="tabular-nums opacity-70">{item.count}</span> : null}
          </button>
        ))}
      </div>

      {tab === 'recommendations' ? (
        <RecommendationList items={data?.recommendations || []} loading={loading}
                            compact={compact} currency={data?.currency} />
      ) : (
        <TrendList rows={data?.[tab] || []} loading={loading} compact={compact}
                   currency={data?.currency} kind={tab} />
      )}
    </DashCard>
  );
}

function TrendList({ rows, loading, compact, currency, kind }) {
  if (!loading && rows.length === 0) {
    const message = {
      rising: 'Niciun produs în creștere în perioada selectată.',
      declining: 'Niciun produs în scădere semnificativă.',
      stagnant: 'Toate produsele cu stoc au înregistrat vânzări.',
    }[kind];
    return <EmptyState reason="empty" title={message} compact={compact} />;
  }

  return (
    <ul className="xx-no-scrollbar max-h-64 space-y-1 overflow-y-auto pr-1">
      {rows.map((row) => (
        <li key={row.productId}>
          <Link
            to={`/admin/products?id=${row.productId}`}
            className="flex items-center gap-2.5 rounded-lg px-1.5 py-1.5 transition-colors
              duration-xx hover:bg-[rgba(255,255,255,0.04)]"
          >
            <span className="min-w-0 flex-1">
              <span className="block truncate text-xs text-[color:var(--xx-ink)]">{row.name}</span>
              <span className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[10px]
                text-[color:var(--xx-ink-dim)]">
                <span>
                  {row.unitsPrevious} → {row.unitsCurrent} buc.
                </span>
                {/* The absolute movement is always shown; the percentage only
                    when the baseline was large enough to make it meaningful. */}
                <span className={row.unitsDelta >= 0 ? 'text-[#4fd3a0]' : 'text-[#ff8a97]'}>
                  {row.unitsDelta >= 0 ? '+' : ''}
                  {row.unitsDelta}
                </span>
                {row.changePct !== null && row.changePct !== undefined ? (
                  <span className={row.changePct >= 0 ? 'text-[#4fd3a0]' : 'text-[#ff8a97]'}>
                    ({row.changePct >= 0 ? '+' : ''}
                    {row.changePct.toFixed(0)}%)
                  </span>
                ) : (
                  <span className="opacity-70">bază prea mică pentru procent</span>
                )}
                <span>{row.stockQuantity} pe stoc</span>
              </span>
            </span>

            <span className="shrink-0 text-right">
              <span className="block text-xs font-semibold tabular-nums text-[color:var(--xx-ink)]">
                {money(row.revenueCurrent, currency)}
              </span>
              <SeverityBadge level={row.severity} compact />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

function RecommendationList({ items, loading, compact, currency }) {
  if (!loading && items.length === 0) {
    return (
      <EmptyState
        reason="empty"
        title="Nicio recomandare"
        description="Niciun produs nu îndeplinește condițiile regulilor de promovare sau
          reaprovizionare în perioada analizată."
        compact={compact}
      />
    );
  }

  const actionLabels = {
    RESTOCK: 'Reaprovizionare',
    REVIEW_PRICE: 'Revizuire preț',
    PROMOTE: 'Promovare',
    DISCOUNT: 'Reducere',
    BUNDLE: 'Pachet',
  };

  return (
    <ul className="xx-no-scrollbar max-h-64 space-y-2 overflow-y-auto pr-1">
      {items.map((item) => (
        <li
          key={`${item.action}-${item.productId}`}
          className="rounded-xl border border-[rgba(255,255,255,0.1)]
            bg-[rgba(255,255,255,0.03)] p-2.5"
        >
          <div className="flex items-start justify-between gap-2">
            <Link
              to={`/admin/products?id=${item.productId}`}
              className="min-w-0 text-xs font-medium text-[color:var(--xx-ink)]
                transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
            >
              <span className="line-clamp-2">{item.name}</span>
            </Link>
            <span className="shrink-0 rounded-full border border-[rgba(255,255,255,0.16)]
              px-2 py-0.5 text-[10px] text-[color:var(--xx-ink-dim)]">
              {actionLabels[item.action] || item.action}
            </span>
          </div>

          <p className="mt-1 text-[11px] font-medium text-[color:var(--xx-ink)]">
            {item.headline}
          </p>
          <p className="mt-0.5 text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
            {item.rationale}
          </p>

          <div className="mt-1.5 flex items-center gap-2">
            <SeverityBadge level={item.confidence} compact />
            {/* Impact is absent when the rule cannot quantify one honestly. An
                estimate invented to fill the field would sort itself to the top
                of a list ordered by money. */}
            {item.impact !== null && item.impact !== undefined ? (
              <span className="text-[10px] text-[color:var(--xx-ink-dim)]">
                impact estimat {money(item.impact, currency)}
              </span>
            ) : null}
          </div>
        </li>
      ))}
    </ul>
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
