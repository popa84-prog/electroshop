import { useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
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
  SeverityBadge,
  XXChartDefs,
  XX_SERIES_PURPLE,
  xxAxisProps,
  xxBarCursor,
  xxGridProps,
} from '../../../components/xxii';
import aiService from '../../../api/aiService';
import usePanelData from '../../../hooks/usePanelData';
import useMetricRange from '../../../hooks/useMetricRange';

/**
 * Automated suggestions and order-pattern analysis. Task 7.
 *
 * ## The panel says what produced its suggestions
 *
 * `source` reads RULES, and the footer prints it. A deterministic rules engine
 * over the store's own data and a sentence written by a language model have
 * different failure modes, and an operator deciding whether to act needs to know
 * which one they are reading.
 *
 * That is also why every suggestion carries its figures. A rules engine can
 * attach the numbers that produced it, which means "it says sales fell 60%, but
 * that was the week we were out of stock" is a conversation that improves the
 * shop. A generated sentence offers nothing to check, and a suggestion nobody
 * can check is one people stop reading after the first time it is confidently
 * wrong.
 */
export default function AiAssistantPanel({ compact, title, dragHandle, onHide }) {
  const [range, setRange] = useMetricRange('ai-assistant', '30d', DEFAULT_RANGES);
  const [dismissed, setDismissed] = useState(() => new Set());

  const { data, loading, error, reload } = usePanelData(
    (signal) => aiService.insights(range, signal),
    [range]
  );

  const suggestions = (data?.suggestions || []).filter((s) => !dismissed.has(s.id));
  const patterns = data?.orderPatterns;

  const hours = (patterns?.byHour || []).map((point) => ({
    label: point.label,
    value: Number(point.value ?? 0),
  }));

  return (
    <DashCard
      title={title}
      subtitle="Sugestii generate din datele magazinului, fiecare cu cifrele care au produs-o"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_PURPLE}
      toolbar={<RangeSwitch value={range} onChange={setRange} options={DEFAULT_RANGES} />}
      footer={
        data ? (
          <p className="text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
            Sursă:{' '}
            {data.source === 'RULES'
              ? 'motor determinist de reguli peste datele magazinului. Nu este implicat niciun '
                + 'model de limbaj — fiecare sugestie poate fi verificată pe cifrele afișate.'
              : data.source}
          </p>
        ) : null
      }
    >
      <div className="grid gap-4 xl:grid-cols-2">
        <div className="min-w-0">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Sugestii
          </p>

          {!loading && suggestions.length === 0 ? (
            <EmptyState
              reason="empty"
              title="Nicio sugestie"
              description="Nicio regulă nu a găsit o situație care să merite atenție în perioada
                analizată."
              compact
            />
          ) : (
            <ul className="xx-no-scrollbar max-h-72 space-y-2 overflow-y-auto pr-1">
              {suggestions.map((suggestion) => (
                <li
                  key={suggestion.id}
                  className="rounded-xl border border-[rgba(255,255,255,0.1)]
                    bg-[rgba(255,255,255,0.03)] p-2.5"
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="min-w-0 text-xs font-medium text-[color:var(--xx-ink)]">
                      {suggestion.headline}
                    </p>
                    <div className="flex shrink-0 items-center gap-1">
                      <SeverityBadge level={suggestion.severity} compact />
                      {/* Dismissal is local to this session and this browser.
                          Persisting it would need a server-side per-admin
                          dismissal store; hiding a row until the next reload is
                          enough for triage and does not silently bury a problem
                          that is still there tomorrow. */}
                      <button
                        type="button"
                        onClick={() =>
                          setDismissed((prev) => new Set(prev).add(suggestion.id))
                        }
                        aria-label="Ascunde sugestia"
                        className="text-[color:var(--xx-ink-dim)] transition-colors duration-xx
                          hover:text-[color:var(--xx-ink)]"
                      >
                        ✕
                      </button>
                    </div>
                  </div>

                  <p className="mt-1 text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
                    {suggestion.rationale}
                  </p>

                  <div className="mt-1.5 flex flex-wrap items-center gap-2">
                    {suggestion.linkTo ? (
                      <Link
                        to={suggestion.linkTo}
                        className="rounded-lg border border-[rgba(255,255,255,0.16)] px-2 py-0.5
                          text-[10px] text-[color:var(--xx-cyan)] transition-colors duration-xx
                          hover:border-[color:var(--xx-cyan)]"
                      >
                        {suggestion.actionLabel || 'Deschide'}
                      </Link>
                    ) : null}
                    {suggestion.impact !== null && suggestion.impact !== undefined ? (
                      <span className="text-[10px] text-[color:var(--xx-ink-dim)]">
                        impact estimat {money(suggestion.impact, data?.currency)}
                      </span>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="min-w-0">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Tipare de comandă
          </p>

          <div className="h-36">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={hours} margin={{ top: 4, right: 4, bottom: 0, left: -26 }}>
                <XXChartDefs />
                <CartesianGrid {...xxGridProps} />
                <XAxis dataKey="label" {...xxAxisProps} interval={3} />
                <YAxis {...xxAxisProps} width={34} allowDecimals={false} />
                <Tooltip cursor={xxBarCursor}
                         content={<AdvancedTooltip formats={{ default: 'number' }} />} />
                <Bar dataKey="value" name="Comenzi" fill={XX_SERIES_PURPLE}
                     radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {patterns?.observations?.length ? (
            <ul className="mt-2 space-y-1">
              {patterns.observations.map((observation) => (
                <li
                  key={observation}
                  className="flex gap-1.5 text-[11px] leading-relaxed
                    text-[color:var(--xx-ink-dim)]"
                >
                  <span aria-hidden="true" className="text-[color:var(--xx-cyan)]">›</span>
                  <span>{observation}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </div>
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
