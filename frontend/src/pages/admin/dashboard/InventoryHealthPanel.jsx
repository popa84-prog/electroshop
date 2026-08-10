import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  DashCard,
  DataTable,
  EmptyState,
  SeverityBadge,
  XX_SERIES_AMBER,
} from '../../../components/xxii';
import analyticsService from '../../../api/analyticsService';
import usePanelData from '../../../hooks/usePanelData';

/**
 * Inventory health. Task 13.
 *
 * ## Four tabs, not one merged list
 *
 * Critical stock, overstock, out-of-stock and restock suggestions need different
 * actions on different timescales. A single list sorted by quantity would put
 * the out-of-stock rows at the top of a long tail of nearly-out rows and lose
 * the distinction entirely — which is the distinction that decides whether
 * somebody places an order today or notes it for next week.
 *
 * ## Days of cover sits beside every quantity
 *
 * A hundred units means opposite things for a product selling thirty a week and
 * one selling two a year. The threshold from the requirement is applied, and the
 * velocity figure beside it is what lets the two be told apart.
 *
 * ## Restock suggestions show their arithmetic
 *
 * Each one prints the sales, the velocity, the cover and the assumed lead time
 * that produced it, so an operator who knows a product is seasonal can override
 * it on the evidence rather than on suspicion.
 */
export default function InventoryHealthPanel({ compact, title, dragHandle, onHide }) {
  const [tab, setTab] = useState('restock');

  const { data, loading, error, reload } = usePanelData(
    (signal) => analyticsService.inventoryHealth(signal),
    []
  );

  const summary = data?.summary;
  const thresholds = data?.thresholds;

  const tabs = [
    { key: 'restock', label: 'De aprovizionat', count: summary?.restockCount, tone: 'DANGER' },
    { key: 'critical', label: 'Stoc critic', count: summary?.criticalCount, tone: 'DANGER' },
    { key: 'outOfStock', label: 'Fără stoc', count: summary?.outOfStockCount, tone: 'WARNING' },
    { key: 'overstocked', label: 'Supra-stoc', count: summary?.overstockedCount, tone: 'INFO' },
  ];

  return (
    <DashCard
      title={title}
      subtitle={
        thresholds
          ? `Critic sub ${thresholds.criticalBelow} buc., supra-stoc peste `
            + `${thresholds.overstockAbove} buc.; rotația se calculează pe `
            + `${thresholds.velocityWindowDays} de zile`
          : null
      }
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_AMBER}
      footer={
        summary ? (
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs
            text-[color:var(--xx-ink-dim)]">
            <span>
              Capital în stoc{' '}
              <strong className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                {money(summary.totalStockValue, data.currency)}
              </strong>
            </span>
            <span>
              din care blocat în supra-stoc{' '}
              <strong className="font-semibold tabular-nums text-[#e0bd4a]">
                {money(summary.overstockedValue, data.currency)}
              </strong>
            </span>
            {summary.productsWithoutCost > 0 ? (
              <span className="text-[10px]">
                {summary.productsWithoutCost} produse fără cost înregistrat — valorile de mai
                sus le exclud
              </span>
            ) : null}
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
            className={`inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-[11px]
              font-medium transition-all duration-xx ${
                tab === item.key
                  ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-cyan)]'
                  : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-dim)] hover:text-[color:var(--xx-ink)]'
              }`}
          >
            {item.label}
            {item.count !== undefined && item.count !== null ? (
              <span className="tabular-nums opacity-70">{item.count}</span>
            ) : null}
          </button>
        ))}
      </div>

      {tab === 'restock' ? (
        <RestockList items={data?.restock || []} currency={data?.currency} loading={loading}
                     compact={compact} />
      ) : (
        <StockTable
          rows={data?.[tab] || []}
          currency={data?.currency}
          loading={loading}
          compact={compact}
        />
      )}
    </DashCard>
  );
}

function RestockList({ items, currency, loading, compact }) {
  if (!loading && items.length === 0) {
    return (
      <EmptyState
        reason="empty"
        title="Nicio recomandare de aprovizionare"
        description="Toate produsele care se vând au stoc pentru perioada țintă."
        compact={compact}
      />
    );
  }

  return (
    <ul className="xx-no-scrollbar max-h-72 space-y-2 overflow-y-auto pr-1">
      {items.map((item) => (
        <li
          key={item.productId}
          className="rounded-xl border border-[rgba(255,255,255,0.1)]
            bg-[rgba(255,255,255,0.03)] p-2.5"
        >
          <div className="flex items-start justify-between gap-2">
            <Link
              to={`/admin/products?id=${item.productId}`}
              className="min-w-0 flex-1 text-sm font-medium text-[color:var(--xx-ink)]
                transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
            >
              <span className="line-clamp-2">{item.name}</span>
            </Link>
            <SeverityBadge level={item.urgency} compact />
          </div>

          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
            <span className="text-[color:var(--xx-ink-dim)]">
              Comandă{' '}
              <strong className="font-semibold tabular-nums text-[color:var(--xx-cyan)]">
                {item.suggestedUnits} buc.
              </strong>
            </span>
            {item.estimatedCost !== null && item.estimatedCost !== undefined ? (
              <span className="text-[color:var(--xx-ink-dim)]">
                ≈ {money(item.estimatedCost, currency)}
              </span>
            ) : null}
            {item.supplierName ? (
              <span className="text-[color:var(--xx-ink-dim)]">de la {item.supplierName}</span>
            ) : null}
          </div>

          {/* The arithmetic is printed under every suggestion. A recommendation
              an operator cannot check is one they stop reading the first time it
              is wrong about a seasonal product. */}
          <p className="mt-1 text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
            {item.rationale}
          </p>
        </li>
      ))}
    </ul>
  );
}

function StockTable({ rows, currency, loading, compact }) {
  if (!loading && rows.length === 0) {
    return <EmptyState reason="empty" compact={compact} />;
  }

  return (
    <DataTable
      compact={compact}
      searchable
      searchPlaceholder="Caută produs…"
      maxHeight="18rem"
      rowKey="productId"
      rows={rows}
      columns={[
        {
          key: 'name',
          label: 'Produs',
          render: (row) => (
            <Link
              to={`/admin/products?id=${row.productId}`}
              className="block max-w-[16rem] truncate transition-colors duration-xx
                hover:text-[color:var(--xx-cyan)]"
              title={row.name}
            >
              {row.name}
            </Link>
          ),
        },
        { key: 'stockQuantity', label: 'Stoc', align: 'right' },
        {
          key: 'unitsSold30d',
          label: 'Vândute 30z',
          align: 'right',
        },
        {
          key: 'daysOfCover',
          label: 'Acoperire',
          align: 'right',
          render: (row) =>
            // Null means the product has not moved at all, which is not the same
            // as "lasts forever" — the division has no answer and the table says
            // so rather than printing a very large number.
            row.daysOfCover === null || row.daysOfCover === undefined
              ? <span className="text-[color:var(--xx-ink-dim)]">fără rotație</span>
              : `${row.daysOfCover.toFixed(0)} z`,
        },
        {
          key: 'stockValue',
          label: 'Valoare',
          align: 'right',
          render: (row) =>
            row.stockValue === null || row.stockValue === undefined
              ? '—'
              : money(row.stockValue, currency),
        },
        {
          key: 'severity',
          label: 'Stare',
          sortable: false,
          render: (row) => <SeverityBadge level={row.severity} compact />,
        },
      ]}
    />
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
