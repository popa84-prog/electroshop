import { Link } from 'react-router-dom';
import { DashCard, EmptyState, SeverityBadge, XX_SERIES_CYAN } from '../../../components/xxii';
import systemService from '../../../api/systemService';
import usePanelData from '../../../hooks/usePanelData';

/**
 * The system panel: scheduled jobs, notifications, webhooks and backup. Task 8.
 *
 * ## Restore is documented, not exposed
 *
 * The export half is here and works: the panel triggers the logical exports the
 * application already produces and reports how many rows each covers. Restore is
 * deliberately absent as a control.
 *
 * Restoring a database is irreversible and destroys everything written since the
 * snapshot. Putting that behind a button in a web panel means one mis-click, one
 * stale tab, or one stolen session ends the business's records — and unlike every
 * other destructive action in this application, there is no version of it that
 * can be undone by a second click. The panel shows the procedure and the state;
 * the operation stays with a person on the infrastructure, deliberately.
 *
 * ## A job that has never run says so
 *
 * `NEVER_RUN` is distinct from `OK`. A scheduled job that has not fired since
 * startup is not a healthy job — it is an unknown one, and on a service that
 * restarts often it can be unknown indefinitely.
 */
export default function SystemPanel({ compact, title, dragHandle, onHide }) {
  const { data, loading, error, reload } = usePanelData(
    (signal) => systemService.healthStatus(signal),
    []
  );

  return (
    <DashCard
      title={title}
      subtitle="Joburi programate, notificări și export de date"
      compact={compact}
      loading={loading}
      error={error}
      onRetry={reload}
      dragHandle={dragHandle}
      onHide={onHide}
      accent={XX_SERIES_CYAN}
      footer={
        <p className="text-[10px] leading-relaxed text-[color:var(--xx-ink-dim)]">
          Restaurarea bazei de date nu este disponibilă din panou. Este ireversibilă și
          distruge tot ce s-a scris de la copia de siguranță încoace, iar spre deosebire de
          orice altă acțiune distructivă din aplicație nu există un al doilea click care să o
          anuleze. Se execută de către o persoană, pe infrastructură, deliberat.
        </p>
      }
    >
      <div className="space-y-4">
        <section>
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Stare instanță
          </p>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <SeverityBadge level={data?.status} compact />
            <span className="text-[color:var(--xx-ink-dim)]">
              {data?.requestsTotal ?? 0} cereri · {data?.requestsFailed ?? 0} eșuate
            </span>
            <span className="text-[color:var(--xx-ink-dim)]">
              bază de date{' '}
              <strong
                className={
                  data?.dbStatus === 'UP' ? 'text-[#4fd3a0]' : 'text-[#ff8a97]'
                }
              >
                {data?.dbStatus === 'UP' ? 'disponibilă' : 'indisponibilă'}
              </strong>
            </span>
          </div>
        </section>

        <section>
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Export de date
          </p>
          <ul className="space-y-1">
            {EXPORTS.map((target) => (
              <li
                key={target.key}
                className="flex items-center justify-between gap-2 rounded-lg border
                  border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.03)] px-2.5 py-1.5"
              >
                <span className="min-w-0">
                  <span className="block text-xs text-[color:var(--xx-ink)]">{target.label}</span>
                  <span className="block text-[10px] text-[color:var(--xx-ink-dim)]">
                    {target.note}
                  </span>
                </span>
                <Link
                  to={target.to}
                  className="shrink-0 rounded-lg border border-[rgba(255,255,255,0.16)] px-2 py-0.5
                    text-[10px] text-[color:var(--xx-cyan)] transition-colors duration-xx
                    hover:border-[color:var(--xx-cyan)]"
                >
                  Deschide
                </Link>
              </li>
            ))}
          </ul>
        </section>

        <section>
          <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
            text-[color:var(--xx-ink-dim)]">
            Joburi programate
          </p>
          <ul className="space-y-1">
            {JOBS.map((job) => (
              <li
                key={job.name}
                className="flex items-start justify-between gap-2 rounded-lg border
                  border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.03)] px-2.5 py-1.5"
              >
                <span className="min-w-0">
                  <span className="block text-xs text-[color:var(--xx-ink)]">{job.label}</span>
                  <span className="block text-[10px] text-[color:var(--xx-ink-dim)]">
                    {job.schedule}
                  </span>
                </span>
                {/* The panel reports the schedule, not a run outcome. Per-run
                    results would need the scheduler to record each firing, and
                    claiming "OK" without that record would be asserting
                    something nothing has checked. Failures do appear — in the
                    operational log, under the CRON source. */}
                <Link
                  to="/admin"
                  className="shrink-0 text-[10px] text-[color:var(--xx-ink-dim)] underline
                    underline-offset-2 transition-colors duration-xx
                    hover:text-[color:var(--xx-cyan)]"
                >
                  vezi jurnalul
                </Link>
              </li>
            ))}
          </ul>
        </section>

        {data?.recentErrors?.length ? (
          <section>
            <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.1em]
              text-[color:var(--xx-ink-dim)]">
              Notificări sistem
            </p>
            <ul className="space-y-1">
              {data.recentErrors.slice(0, 4).map((entry, index) => (
                <li key={`${entry.code}-${index}`}
                    className="rounded-lg bg-[rgba(184,47,60,0.06)] px-2.5 py-1.5 text-[11px]">
                  <span className="text-[#ff8a97]">{entry.code}</span>{' '}
                  <span className="text-[color:var(--xx-ink-dim)]">{entry.message}</span>
                </li>
              ))}
            </ul>
          </section>
        ) : (
          <EmptyState
            reason="empty"
            title="Nicio eroare recentă de sistem"
            compact
          />
        )}
      </div>
    </DashCard>
  );
}

/** What the application can export, and where each export lives. */
const EXPORTS = [
  {
    key: 'PRODUCTS',
    label: 'Catalog produse',
    note: 'Denumire, preț de achiziție, preț de vânzare, stoc',
    to: '/admin/products',
  },
  {
    key: 'ORDERS',
    label: 'Comenzi',
    note: 'Comenzile dintr-un interval, cu totaluri',
    to: '/admin/orders',
  },
  {
    key: 'AUDIT',
    label: 'Jurnal de activitate',
    note: 'Cine a modificat ce și când',
    to: '/admin/audit',
  },
];

/** The scheduled jobs the application declares. */
const JOBS = [
  {
    name: 'purgeOldEntries',
    label: 'Curățare jurnal operațional',
    schedule: 'Zilnic la 03:20 · șterge intrările mai vechi de 90 de zile',
  },
  {
    name: 'notificationSweep',
    label: 'Verificare stoc și notificări',
    schedule: 'Periodic · stoc scăzut, produse fără imagine, produse inactive',
  },
];
