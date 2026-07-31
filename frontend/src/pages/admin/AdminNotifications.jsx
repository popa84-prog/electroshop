import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { showToast } from '../../components/Toast';
import { formatRelative } from '../../utils/format';
import { TYPE_ICON, TYPE_LABELS, TYPE_OPTIONS, TYPE_STYLE } from '../../utils/notificationLabels';

/**
 * Full notification center (feature #8) — "centru de notificări în admin".
 * Lists every notification (not just unread, unless the filter is used),
 * with mark-as-read per row, a bulk "marchează tot ca citit", and filtering
 * by type. Mirrors AdminAuditLog.jsx's layout so the two "activity" style
 * admin pages feel consistent.
 */
export default function AdminNotifications() {
    const [items, setItems] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [type, setType] = useState('');
    const [unreadOnly, setUnreadOnly] = useState(false);
    const [markingAll, setMarkingAll] = useState(false);

  const load = () => {
        setLoading(true);
        adminService
          .listNotifications({ page, size: 20, type: type || undefined, unreadOnly })
          .then((data) => {
                    setItems(data.content || []);
                    setTotalPages(data.totalPages || 0);
          })
          .catch(() => setItems([]))
          .finally(() => setLoading(false));
  };

  useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, type, unreadOnly]);

  const markOneRead = async (id) => {
        try {
                await adminService.markNotificationRead(id);
                setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
        } catch (err) {
                showToast(err.response?.data?.message || 'Nu am putut marca notificarea ca citită.', 'error');
        }
  };

  const markAllRead = async () => {
        setMarkingAll(true);
        try {
                await adminService.markAllNotificationsRead();
                showToast('Toate notificările au fost marcate ca citite.', 'success');
                load();
        } catch (err) {
                showToast(err.response?.data?.message || 'Operațiunea a eșuat.', 'error');
        } finally {
                setMarkingAll(false);
        }
  };

  return (
        <div>
              <AdminNav />
              <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                      <div>
                                <h1 className="text-2xl font-bold text-slate-800">Notificări</h1>
                                <p className="mt-1 text-sm text-slate-500">
                                            Stoc redus, produse fără imagini, produse dezactivate, comenzi noi și conturi blocate.
                                </p>
                      </div>
                      <button className="btn-secondary" onClick={markAllRead} disabled={markingAll}>
                        {markingAll ? 'Se marchează...' : '✓ Marchează tot ca citit'}
                      </button>
              </div>
          
                <div className="mb-4 flex flex-wrap items-center gap-3">
                      <div className="flex items-center gap-2">
                                <label className="text-sm font-medium text-slate-600" htmlFor="notif-type-filter">
                                            Tip notificare
                                </label>
                                <select
                                              id="notif-type-filter"
                                              className="input w-auto"
                                              value={type}
                                              onChange={(e) => {
                                                              setType(e.target.value);
                                                              setPage(0);
                                              }}
                                            >
                                            <option value="">Toate tipurile</option>
                                  {TYPE_OPTIONS.map((t) => (
                                                            <option key={t} value={t}>
                                                              {TYPE_LABELS[t]}
                                                            </option>
                                                          ))}
                                </select>
                      </div>
                      <label className="flex items-center gap-2 text-sm font-medium text-slate-600">
                                <input
                                              type="checkbox"
                                              checked={unreadOnly}
                                              onChange={(e) => {
                                                              setUnreadOnly(e.target.checked);
                                                              setPage(0);
                                              }}
                                            />
                                Doar necitite
                      </label>
              </div>

          {loading ? (
                  <Spinner />
                ) : (
                  <div className="card overflow-x-auto">
                            <table className="min-w-full divide-y divide-slate-200 text-sm">
                                        <thead className="bg-slate-50 text-left text-slate-500">
                                                      <tr>
                                                                      <th className="px-4 py-3">Tip</th>
                                                                      <th className="px-4 py-3">Notificare</th>
                                                                      <th className="px-4 py-3">Data</th>
                                                                      <th className="px-4 py-3">Status</th>
                                                                      <th className="px-4 py-3" />
                                                      </tr>
                                        </thead>
                                          <tbody className="divide-y divide-slate-100">
                                            {items.length === 0 && (
                                    <tr>
                                                      <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                                                        {type || unreadOnly ? 'Nicio notificare pentru acest filtru.' : 'Nicio notificare încă.'}
                                                      </td>
                                    </tr>
                                                        )}
                                            {items.map((n) => (
                                    <tr key={n.id} className={n.read ? 'hover:bg-slate-50' : 'bg-brand-50/40 hover:bg-brand-50'}>
                                                      <td className="px-4 py-3">
                                                                          <span className={`badge ${TYPE_STYLE[n.type] || 'bg-slate-100 text-slate-700'}`}>
                                                                            {TYPE_ICON[n.type] || '🔔'} {TYPE_LABELS[n.type] || n.type}
                                                                          </span>
                                                      </td>
                                                      <td className="px-4 py-3">
                                                                          <p className="font-medium text-slate-800">{n.title}</p>
                                                        {n.message && <p className="text-slate-500">{n.message}</p>}
                                                      </td>
                                                      <td className="whitespace-nowrap px-4 py-3 text-slate-500">{formatRelative(n.createdAt)}</td>
                                                      <td className="px-4 py-3">
                                                        {n.read ? (
                                                            <span className="badge bg-slate-100 text-slate-600">Citită</span>
                                                          ) : (
                                                            <span className="badge bg-blue-100 text-blue-800">Necitită</span>
                                                                          )}
                                                      </td>
                                                      <td className="px-4 py-3 text-right">
                                                        {!n.read && (
                                                            <button
                                                                                      type="button"
                                                                                      className="btn-secondary px-2 py-1 text-xs"
                                                                                      onClick={() => markOneRead(n.id)}
                                                                                    >
                                                                                    Marchează ca citită
                                                            </button>
                                                                          )}
                                                      </td>
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
