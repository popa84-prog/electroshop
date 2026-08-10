import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  DashCard,
  DEFAULT_RANGES,
  EmptyState,
  ExportButton,
  RangeSwitch,
  XX_SERIES_BLUE,
} from '../../../components/xxii';
import dashboardConfigService from '../../../api/dashboardConfigService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';
import { useAuth } from '../../../context/AuthContext';

/**
 * The recent-activity panel. Task 5.
 *
 * ## Rows expand rather than navigate
 *
 * Clicking a row reveals who did what and, where the audit text carries them,
 * the field-level before/after pairs. Navigating instead would lose the operator's
 * place in a list they are scanning, and most rows are read rather than acted on.
 *
 * ## An empty change list is honest, not a gap
 *
 * The audit log stores free text, some of which parses into structured changes
 * and much of which does not. Where it does not, the raw detail is shown and the
 * change list stays empty. Inferring a diff from a sentence would produce
 * confident, specific, invented claims about what somebody changed — in the one
 * place in the application that exists so such claims can be checked.
 */
export default function ActivityPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('activity', '7d', DEFAULT_RANGES);
  const [category, setCategory] = useState('');
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState(null);

  const { hasPermission } = useAuth();

  const { data, loading, error, reload } = usePanelData(
    (signal) =>
      dashboardConfigService.activity({ range, category, q: query, page: 0, size: 40 }, signal),
    [range, category, query]
  );

  const canExport = hasPermission ? hasPermission('AUDIT_EXPORT') : false;

  return (
    <DashCard
      title={title}
      subtitle="Cine a modificat ce și când"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_BLUE}
      toolbar={
        <div className="flex items-center gap-1.5">
          <RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />
          {canExport ? (
            <ExportButton
              compact
              label="CSV"
              filename={`activitate-${new Date().toISOString().slice(0, 10)}.csv`}
              onExport={() =>
                dashboardConfigService.exportActivity({ range, category, q: query })
              }
            />
          ) : null}
        </div>
      }
    >
      <div className="mb-3 space-y-2">
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Caută în activitate…"
          aria-label="Caută în activitate"
          className="w-full rounded-lg border border-[rgba(255,255,255,0.12)]
            bg-[rgba(255,255,255,0.04)] px-3 py-1.5 text-xs text-[color:var(--xx-ink)]
            placeholder:text-[color:var(--xx-ink-dim)] focus:border-[color:var(--xx-cyan)]
            focus:outline-none"
        />

        <div className="flex flex-wrap gap-1.5">
          <FilterChip active={!category} onClick={() => setCategory('')} label="Toate" />
          {(data?.categoryCounts || [])
            .filter((item) => item.count > 0)
            .map((item) => (
              <FilterChip
                key={item.category}
                active={category === item.category}
                onClick={() => setCategory(item.category)}
                label={item.label}
                count={item.count}
              />
            ))}
        </div>
      </div>

      {!loading && (data?.entries?.length ?? 0) === 0 ? (
        <EmptyState reason={query || category ? 'filtered' : 'empty'} compact={compact} />
      ) : (
        <ul className="xx-no-scrollbar max-h-72 space-y-0.5 overflow-y-auto pr-1">
          {(data?.entries || []).map((entry) => {
            const open = expanded === entry.id;
            return (
              <li key={entry.id} className="rounded-lg border border-transparent
                transition-colors duration-xx hover:border-[rgba(255,255,255,0.08)]
                hover:bg-[rgba(255,255,255,0.03)]">
                <button
                  type="button"
                  onClick={() => setExpanded(open ? null : entry.id)}
                  aria-expanded={open}
                  className="flex w-full items-start gap-2 px-2 py-1.5 text-left"
                >
                  <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full"
                        style={{ background: categoryColor(entry.category) }}
                        aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-xs text-[color:var(--xx-ink)]">
                      {entry.actionLabel}
                      {entry.entityName ? (
                        <span className="text-[color:var(--xx-ink-dim)]"> · {entry.entityName}</span>
                      ) : null}
                    </span>
                    <span className="block truncate text-[10px] text-[color:var(--xx-ink-dim)]">
                      {entry.actor || 'sistem'} · {formatDate(entry.createdAt)}
                    </span>
                  </span>
                  <span aria-hidden="true"
                        className={`shrink-0 text-[color:var(--xx-ink-dim)] transition-transform
                          duration-xx ${open ? 'rotate-90' : ''}`}>
                    ›
                  </span>
                </button>

                {open ? (
                  <div className="border-t border-[rgba(255,255,255,0.08)] px-2 py-2 text-[11px]">
                    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                      <dt className="text-[color:var(--xx-ink-dim)]">Cine</dt>
                      <dd className="text-[color:var(--xx-ink)]">{entry.actor || 'sistem'}</dd>
                      <dt className="text-[color:var(--xx-ink-dim)]">Când</dt>
                      <dd className="text-[color:var(--xx-ink)]">{formatDate(entry.createdAt)}</dd>
                      <dt className="text-[color:var(--xx-ink-dim)]">Acțiune</dt>
                      <dd className="font-mono text-[10px] text-[color:var(--xx-ink)]">
                        {entry.action}
                      </dd>
                      {entry.entityType ? (
                        <>
                          <dt className="text-[color:var(--xx-ink-dim)]">Entitate</dt>
                          <dd className="text-[color:var(--xx-ink)]">
                            {entry.entityType}
                            {entry.entityId ? ` #${entry.entityId}` : ''}
                          </dd>
                        </>
                      ) : null}
                    </dl>

                    {entry.changes?.length ? (
                      <table className="mt-2 w-full text-left text-[10px]">
                        <thead>
                          <tr className="text-[color:var(--xx-ink-dim)]">
                            <th scope="col" className="pb-1 font-medium">Câmp</th>
                            <th scope="col" className="pb-1 font-medium">Înainte</th>
                            <th scope="col" className="pb-1 font-medium">După</th>
                          </tr>
                        </thead>
                        <tbody>
                          {entry.changes.map((change, index) => (
                            <tr key={`${change.field}-${index}`}>
                              <td className="pr-2 text-[color:var(--xx-ink-dim)]">
                                {change.field}
                              </td>
                              <td className="pr-2 text-[#ff8a97]">{change.oldValue ?? '—'}</td>
                              <td className="text-[#4fd3a0]">{change.newValue ?? '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : entry.details ? (
                      <p className="mt-2 leading-relaxed text-[color:var(--xx-ink-dim)]">
                        {entry.details}
                      </p>
                    ) : null}

                    {entry.linkTo ? (
                      <Link
                        to={entry.linkTo}
                        className="mt-2 inline-block rounded-lg border
                          border-[rgba(255,255,255,0.16)] px-2 py-0.5 text-[10px]
                          text-[color:var(--xx-cyan)] transition-colors duration-xx
                          hover:border-[color:var(--xx-cyan)]"
                      >
                        Deschide
                      </Link>
                    ) : null}
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
    </DashCard>
  );
}

function FilterChip({ active, onClick, label, count }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`inline-flex items-center gap-1 rounded-lg border px-2 py-0.5 text-[11px]
        transition-all duration-xx ${
          active
            ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-cyan)]'
            : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-dim)] hover:text-[color:var(--xx-ink)]'
        }`}
    >
      {label}
      {count !== undefined ? <span className="tabular-nums opacity-70">{count}</span> : null}
    </button>
  );
}

/** One hue per category, drawn from the validated series palette. */
function categoryColor(category) {
  return {
    PRODUCTS: '#2e7bff',
    ORDERS: '#d032b8',
    USERS: '#1fac79',
    SYSTEM: '#b08c09',
  }[category] || 'rgba(255,255,255,0.35)';
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString('ro-RO', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
}
