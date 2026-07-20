import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { formatDate } from '../../utils/format';

const ACTION_STYLE = {
  PRODUCT_CREATED: 'bg-green-100 text-green-800',
  PRODUCT_UPDATED: 'bg-blue-100 text-blue-800',
  PRODUCT_IMAGE_UPDATED: 'bg-indigo-100 text-indigo-800',
  PRODUCT_DELETED: 'bg-red-100 text-red-800',
  ORDER_CREATED: 'bg-green-100 text-green-800',
  ORDER_STATUS_CHANGED: 'bg-amber-100 text-amber-800',
  ORDER_DELETED: 'bg-red-100 text-red-800',
};

export default function AdminAuditLog() {
  const [logs, setLogs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    adminService
      .listAuditLogs({ page, size: 20 })
      .then((data) => {
        setLogs(data.content || []);
        setTotalPages(data.totalPages || 0);
      })
      .catch(() => setLogs([]))
      .finally(() => setLoading(false));
  }, [page]);

  return (
    <div>
      <AdminNav />
      <h1 className="mb-4 text-2xl font-bold text-slate-800">Jurnal de activitate</h1>
      <p className="mb-4 text-sm text-slate-500">
        Cine a modificat produse, stocuri, imagini și comenzi — cu dată, autor și detalii.
      </p>

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
                    Nicio activitate înregistrată încă.
                  </td>
                </tr>
              )}
              {logs.map((l) => (
                <tr key={l.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-3 text-slate-500">{formatDate(l.createdAt)}</td>
                  <td className="px-4 py-3 text-slate-700">{l.actor}</td>
                  <td className="px-4 py-3">
                    <span className={`badge ${ACTION_STYLE[l.action] || 'bg-slate-100 text-slate-700'}`}>
                      {l.action}
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
