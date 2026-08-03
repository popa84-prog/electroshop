import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import { showToast } from '../../components/Toast';
import { formatDate } from '../../utils/format';
import {
  ACTION_ICON,
  ACTION_ICON_FALLBACK,
  ACTION_LABELS,
  ACTION_OPTIONS,
  ACTION_STYLE,
  ACTION_STYLE_FALLBACK,
} from '../../utils/auditLabels';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../../components/xxii';

/**
 * XXII — TASK 6 / TASK 8 (Quantum Control Center: jurnalul de activitate).
 *
 * Fiecare acțiune este identificată prin trei canale simultan: eticheta scrisă,
 * pictograma geometrică și culoarea insignei. Culoarea este ultimul canal, nu
 * primul — vezi comentariul din utils/auditLabels.js pentru motivul pentru care
 * paleta a fost redusă la patru familii.
 *
 * Coloana „Data” folosește cifre monospațiate, astfel încât marcajele de timp
 * să se alinieze pe verticală și o scanare de sus în jos să detecteze imediat
 * o rafală de activitate.
 */
export default function AdminAuditLog() {
  const [logs, setLogs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [action, setAction] = useState('');
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    setLoading(true);
    adminService
      .listAuditLogs({ page, size: 20, action: action || undefined })
      .then((data) => {
        setLogs(data.content || []);
        setTotalPages(data.totalPages || 0);
      })
      .catch(() => setLogs([]))
      .finally(() => setLoading(false));
  }, [page, action]);

  const doExport = async () => {
    setExporting(true);
    try {
      const blob = await adminService.exportAuditLogs({ action: action || undefined });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'jurnal-activitate.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      showToast('Export finalizat.', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Exportul a eșuat.', 'error');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Securitate"
        title="Jurnal de activitate"
        as="h1"
        subtitle="Cine a modificat produse, stocuri, imagini și comenzi — cu dată, autor și detalii."
        action={
          <NeonButton
            variant="secondary"
            onClick={doExport}
            disabled={exporting}
            charging={exporting}
            icon={<GeoIcon name="document" className="h-4 w-4" />}
          >
            {exporting ? 'Se exportă...' : 'Exportă jurnalul'}
          </NeonButton>
        }
      />

      <div className="mb-4 w-72">
        <HoloInput
          as="select"
          id="audit-action-filter"
          label="Tip acțiune"
          value={action}
          onChange={(e) => {
            setAction(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Toate acțiunile</option>
          {ACTION_OPTIONS.map((a) => (
            <option key={a} value={a}>
              {ACTION_LABELS[a]}
            </option>
          ))}
        </HoloInput>
      </div>

      {loading ? (
        <HoloLoader label="Se încarcă jurnalul" />
      ) : logs.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">
            {action
              ? 'Nicio activitate pentru acest filtru.'
              : 'Nicio activitate înregistrată încă.'}
          </p>
        </div>
      ) : (
        <Reveal>
          <div className="card overflow-x-auto">
            <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Data
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Autor
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Acțiune
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Entitate
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Detalii
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.06)]">
                {logs.map((l) => (
                  <tr key={l.id} className="transition-colors duration-200">
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs xx-ink-muted">
                      {formatDate(l.createdAt)}
                    </td>
                    <td className="px-4 py-3 font-medium text-[#e8ecff]">{l.actor}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-semibold ${
                          ACTION_STYLE[l.action] || ACTION_STYLE_FALLBACK
                        }`}
                      >
                        <GeoIcon
                          name={ACTION_ICON[l.action] || ACTION_ICON_FALLBACK}
                          className="h-3.5 w-3.5"
                          accent="currentColor"
                        />
                        {ACTION_LABELS[l.action] || l.action}
                      </span>
                    </td>
                    <td className="px-4 py-3 xx-ink-muted">
                      {l.entityType}
                      {l.entityId ? <span className="font-mono"> #{l.entityId}</span> : ''}
                    </td>
                    <td className="px-4 py-3 xx-ink-muted">{l.details || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Reveal>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
