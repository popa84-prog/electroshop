import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { showToast } from '../../components/Toast';
import { formatDate } from '../../utils/format';
import { ACTION_STYLE, ACTION_LABELS, ACTION_OPTIONS } from '../../utils/auditLabels';

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
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Jurnal de activitate</h1>
          <p className="mt-1 text-sm text-slate-500">
            Cine a modificat produse, stocuri, imagini și comenzi — cu dată, autor și detalii.
          </p>
        </div>
        <button className="btn-secondary" onClick={doExport} disabled={exporting}>
          {exporting ? 'Se exportă...' : '⭳ Exportă jurnalul'}
        </button>
      </div>

      <div className="mb-4 flex items-center gap-2">
        <label className="text-sm font-medium text-slate-600" htmlFor="audit-action-filter">
          Tip acțiune
        </label>
        <select
          id="audit-action-filter"
          className="input w-auto"
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
        </select>
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-3">Data</th>
                <th className="px-4 py-3">Autor</th>
                <th className="px-4 py-3">Acțiune</th>
                <th className="px-4 py-3">Entitate</th>
                <th className="px-4 py-3">Detalii</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {logs.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                    {action ? 'Nicio activitate pentru acest filtru.' : 'Nicio activitate înregistrată încă.'}
                  </td>
                </tr>
              )}
              {logs.map((l) => (
                <tr key={l.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-3 text-slate-500">{formatDate(l.createdAt)}</td>
                  <td className="px-4 py-3 text-slate-700">{l.actor}</td>
                  <td className="px-4 py-3">
                    <span className={`badge ${ACTION_STYLE[l.action] || 'bg-slate-100 text-slate-700'}`}>
                      {ACTION_LABELS[l.action] || l.action}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {l.entityType}
                    {l.entityId ? ` #${l.entityId}` : ''}
                  </td>
                  <td className="px-4 py-3 text-slate-600">{l.details || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
