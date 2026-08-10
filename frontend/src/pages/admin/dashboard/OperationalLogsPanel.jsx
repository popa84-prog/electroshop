import { useState } from 'react';
import {
  DashCard,
  DataTable,
  DEFAULT_RANGES,
  EmptyState,
  ExportButton,
  RangeSwitch,
  SeverityBadge,
  XX_SERIES_AMBER,
} from '../../../components/xxii';
import systemService from '../../../api/systemService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * The operational log. Task 19.
 *
 * ## Organised by source, because the four fail for unrelated reasons
 *
 * A burst of database errors means the database is unreachable. A burst of API
 * errors with the database healthy means one endpoint is broken. Filed together
 * under "errors" the two look identical, and telling them apart is the whole
 * diagnosis.
 *
 * ## The ranked list comes before the table
 *
 * One broken endpoint hit two thousand times produces two thousand rows and one
 * problem. The top-errors list is what turns the former into the latter, so it
 * sits above the log rather than below it.
 */
export default function OperationalLogsPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('operational-logs', '7d', DEFAULT_RANGES);
  const [source, setSource] = useState('');
  const [level, setLevel] = useState('');
  const [query, setQuery] = useState('');

  const { data, loading, error, reload } = usePanelData(
    (signal) =>
      systemService.logs({ range, source, level, q: query, page: 0, size: 60 }, signal),
    [range, source, level, query]
  );

  const sources = ['API', 'CRON', 'DB', 'AUTH', 'APP'];
  const levels = ['ERROR', 'WARN', 'INFO'];

  return (
    <DashCard
      title={title}
      subtitle="Erori API, cron și bază de date, cu disponibilitatea instanței"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_AMBER}
      toolbar={
        <div className="flex items-center gap-1.5">
          <RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />
          <ExportButton
            compact
            label="CSV"
            filename={`jurnal-sistem-${new Date().toISOString().slice(0, 10)}.csv`}
            onExport={() => systemService.exportLogs({ range, source, level, q: query })}
          />
        </div>
      }
      footer={
        data?.uptime ? (
          <p className="text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
            {data.uptime.totalRequests} cereri servite de la pornire, dintre care{' '}
            {data.uptime.failedRequests} eșuate
            {data.uptime.availabilityPct !== null && data.uptime.availabilityPct !== undefined
              ? ` · disponibilitate ${data.uptime.availabilityPct.toFixed(2)}%`
              : ''}
            {data.collectingSince
              ? ` · jurnalul acoperă de la ${new Date(data.collectingSince)
                  .toLocaleDateString('ro-RO')}`
              : ''}
          </p>
        ) : null
      }
    >
      <div className="mb-3 grid grid-cols-2 gap-2 xl:grid-cols-5">
        {(data?.counts || []).map((count) => (
          <button
            key={count.source}
            type="button"
            onClick={() => setSource(source === count.source ? '' : count.source)}
            aria-pressed={source === count.source}
            className={`rounded-lg border px-2 py-1.5 text-left transition-all duration-xx ${
              source === count.source
                ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.1)]'
                : 'border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.03)] hover:border-[rgba(255,255,255,0.25)]'
            }`}
          >
            <span className="block text-[10px] uppercase tracking-[0.1em]
              text-[color:var(--xx-ink-dim)]">
              {count.source}
            </span>
            <span className="mt-0.5 flex items-baseline gap-1.5 text-xs tabular-nums">
              <span className={count.errors > 0 ? 'text-[#ff8a97]' : 'text-[color:var(--xx-ink)]'}>
                {count.errors}
              </span>
              {count.warns > 0 ? (
                <span className="text-[10px] text-[#e0bd4a]">{count.warns} avert.</span>
              ) : null}
            </span>
          </button>
        ))}
      </div>

      {data?.topErrors?.length ? (
        <div className="mb-3">
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Cele mai frecvente erori
          </p>
          <ul className="space-y-0.5">
            {data.topErrors.slice(0, 4).map((group) => (
              <li
                key={`${group.source}-${group.code}`}
                className="flex items-center justify-between gap-2 rounded-lg
                  bg-[rgba(184,47,60,0.06)] px-2 py-1 text-[11px]"
              >
                <span className="min-w-0 truncate font-mono text-[10px] text-[#ff8a97]">
                  {group.code}
                </span>
                <span className="shrink-0 tabular-nums text-[color:var(--xx-ink-dim)]">
                  {group.count}× · ultima {formatDate(group.lastSeenAt)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="mb-2 flex flex-wrap gap-1.5">
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Caută în jurnal…"
          aria-label="Caută în jurnal"
          className="min-w-0 flex-1 rounded-lg border border-[rgba(255,255,255,0.12)]
            bg-[rgba(255,255,255,0.04)] px-3 py-1.5 text-xs text-[color:var(--xx-ink)]
            placeholder:text-[color:var(--xx-ink-dim)] focus:border-[color:var(--xx-cyan)]
            focus:outline-none"
        />
        <Select label="Sursă" value={source} onChange={setSource} options={sources} />
        <Select label="Nivel" value={level} onChange={setLevel} options={levels} />
      </div>

      {!loading && (data?.entries?.length ?? 0) === 0 ? (
        <EmptyState
          reason={query || source || level ? 'filtered' : 'empty'}
          title={
            query || source || level
              ? 'Niciun rezultat pentru filtrele curente'
              : 'Niciun eveniment înregistrat în perioada selectată'
          }
          compact={compact}
        />
      ) : (
        <DataTable
          compact
          maxHeight="18rem"
          rowKey="id"
          rows={data?.entries || []}
          columns={[
            {
              key: 'createdAt',
              label: 'Când',
              width: '7rem',
              render: (row) => (
                <span className="whitespace-nowrap text-[10px] text-[color:var(--xx-ink-dim)]">
                  {formatDate(row.createdAt)}
                </span>
              ),
            },
            {
              key: 'level',
              label: 'Nivel',
              width: '5.5rem',
              render: (row) => <SeverityBadge level={row.level} compact />,
            },
            { key: 'source', label: 'Sursă', width: '4rem' },
            {
              key: 'code',
              label: 'Cod',
              render: (row) => (
                <span className="font-mono text-[10px]">{row.code}</span>
              ),
            },
            {
              key: 'message',
              label: 'Mesaj',
              render: (row) => (
                <span className="block max-w-[18rem] truncate" title={row.message}>
                  {row.message}
                </span>
              ),
            },
            {
              key: 'context',
              label: 'Context',
              render: (row) => (
                <span
                  className="block max-w-[12rem] truncate font-mono text-[10px]
                    text-[color:var(--xx-ink-dim)]"
                  title={row.context}
                >
                  {row.context || '—'}
                </span>
              ),
            },
          ]}
        />
      )}
    </DashCard>
  );
}

function Select({ label, value, onChange, options }) {
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
