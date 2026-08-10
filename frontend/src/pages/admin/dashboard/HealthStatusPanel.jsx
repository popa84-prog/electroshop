import { useEffect } from 'react';
import { DashCard, SeverityBadge, XX_SERIES_GREEN } from '../../../components/xxii';
import systemService from '../../../api/systemService';
import usePanelData from '../../../hooks/usePanelData';

/**
 * Live performance of the running instance. Task 2.
 *
 * ## Uptime is printed beside availability
 *
 * The counters live in memory and reset when the process restarts, so a 100%
 * availability figure over four minutes and one over four weeks are very
 * different claims that look identical as a number. Both appear, always.
 *
 * ## The panel polls, at a rate matched to what it shows
 *
 * Health is the one card whose value decays: a snapshot from ten minutes ago
 * says nothing about whether the API is responding now. It refreshes on a timer,
 * slowly enough that it costs nothing and often enough to be worth looking at.
 */
export default function HealthStatusPanel({ compact, title, dragHandle, onHide }) {
  const { data, loading, error, reload } = usePanelData(
    (signal) => systemService.healthStatus(signal),
    []
  );

  // A plain interval rather than a polling library. One card, one timer, and
  // `reload` is stable across renders so the effect does not re-arm every frame.
  useIntervalReload(reload, 30_000);

  return (
    <DashCard
      title={title}
      subtitle="Disponibilitate, latență și erori ale instanței curente"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_GREEN}
      toolbar={data ? <SeverityBadge level={data.status} compact /> : null}
      footer={
        data ? (
          <p className="text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
            Contoarele se resetează la repornirea procesului. Instanța rulează de{' '}
            {formatUptime(data.uptimeSeconds)}, deci cifrele de mai sus acoperă exact acest
            interval.
          </p>
        ) : null
      }
    >
      <dl className="grid grid-cols-2 gap-2">
        <Metric
          label="Disponibilitate"
          value={data?.availabilityPct}
          suffix="%"
          missing="fără trafic"
        />
        <Metric label="Latență medie" value={data?.avgLatencyMs} suffix=" ms" />
        <Metric label="Latență p95" value={data?.p95LatencyMs} suffix=" ms" />
        <Metric label="Rată erori" value={data?.errorRatePct} suffix="%" missing="0" />
      </dl>

      <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
        <div className="rounded-lg border border-[rgba(255,255,255,0.1)]
          bg-[rgba(255,255,255,0.03)] px-2.5 py-1.5">
          <p className="text-[color:var(--xx-ink-dim)]">Bază de date</p>
          <p className="mt-0.5 flex items-center gap-1.5">
            <SeverityBadge level={data?.dbStatus === 'UP' ? 'SUCCESS' : 'DANGER'}
                           label={data?.dbStatus === 'UP' ? 'Disponibilă' : 'Indisponibilă'}
                           compact />
            {data?.dbLatencyMs !== null && data?.dbLatencyMs !== undefined ? (
              <span className="tabular-nums text-[color:var(--xx-ink-dim)]">
                {data.dbLatencyMs.toFixed(0)} ms
              </span>
            ) : null}
          </p>
        </div>

        <div className="rounded-lg border border-[rgba(255,255,255,0.1)]
          bg-[rgba(255,255,255,0.03)] px-2.5 py-1.5">
          <p className="text-[color:var(--xx-ink-dim)]">Memorie</p>
          <p className="mt-0.5 tabular-nums text-[color:var(--xx-ink)]">
            {data ? `${data.memoryUsedMb} / ${data.memoryMaxMb} MB` : '—'}
          </p>
        </div>
      </div>

      {data?.slowest?.length ? (
        <div className="mt-3">
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Cele mai lente endpointuri
          </p>
          <ul className="space-y-0.5">
            {data.slowest.slice(0, 5).map((endpoint) => (
              <li
                key={endpoint.endpoint}
                className="flex items-center justify-between gap-2 text-[11px]"
              >
                <span className="min-w-0 truncate font-mono text-[10px]
                  text-[color:var(--xx-ink-dim)]" title={endpoint.endpoint}>
                  {endpoint.endpoint}
                </span>
                <span className="flex shrink-0 items-center gap-1.5">
                  <span className="tabular-nums text-[color:var(--xx-ink)]">
                    {endpoint.p95Ms === null || endpoint.p95Ms === undefined
                      ? '—'
                      : `${endpoint.p95Ms.toFixed(0)} ms`}
                  </span>
                  <SeverityBadge level={endpoint.severity} compact />
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {data?.recentErrors?.length ? (
        <div className="mt-3 border-t border-[rgba(255,255,255,0.08)] pt-2">
          <p className="mb-1 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Ultimele erori
          </p>
          <ul className="space-y-1">
            {data.recentErrors.map((entry, index) => (
              <li key={`${entry.code}-${index}`} className="text-[10px] leading-relaxed">
                <span className="text-[#ff8a97]">{entry.code}</span>{' '}
                <span className="text-[color:var(--xx-ink-dim)]">{entry.message}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </DashCard>
  );
}

function Metric({ label, value, suffix = '', missing = '—' }) {
  const numeric = Number(value);
  const has = value !== null && value !== undefined && Number.isFinite(numeric);

  return (
    <div className="rounded-lg border border-[rgba(255,255,255,0.1)]
      bg-[rgba(255,255,255,0.03)] px-2.5 py-1.5">
      <dt className="text-[10px] uppercase tracking-[0.1em] text-[color:var(--xx-ink-dim)]">
        {label}
      </dt>
      <dd className="mt-0.5 font-display text-base font-semibold tabular-nums
        text-[color:var(--xx-ink)]">
        {has ? (
          `${numeric.toLocaleString('ro-RO', { maximumFractionDigits: 1 })}${suffix}`
        ) : (
          <span className="text-xs font-normal text-[color:var(--xx-ink-dim)]">{missing}</span>
        )}
      </dd>
    </div>
  );
}

/**
 * Calls `reload` on a timer, cleaning up on unmount.
 *
 * `reload` is stable across renders — `usePanelData` returns it from a
 * `useCallback` with no dependencies — so the effect arms one interval and keeps
 * it, rather than tearing down and re-creating a timer on every render.
 */
function useIntervalReload(reload, intervalMs) {
  useEffect(() => {
    const id = setInterval(reload, intervalMs);
    return () => clearInterval(id);
  }, [reload, intervalMs]);
}

function formatUptime(seconds) {
  const total = Number(seconds);
  if (!Number.isFinite(total) || total <= 0) return 'mai puțin de un minut';

  const days = Math.floor(total / 86400);
  const hoursPart = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);

  if (days > 0) return `${days} z ${hoursPart} h`;
  if (hoursPart > 0) return `${hoursPart} h ${minutes} min`;
  return `${minutes} min`;
}
