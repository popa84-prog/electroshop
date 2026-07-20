import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { formatPrice, formatDate, statusColor, resolveImage } from '../../utils/format';

const STATUSES = ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export default function AdminOrders() {
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);

  const [detail, setDetail] = useState(null);
  const [savingStatus, setSavingStatus] = useState(false);

  const [expFrom, setExpFrom] = useState('');
  const [expTo, setExpTo] = useState('');
  const [exporting, setExporting] = useState(false);

  const doExport = async (format) => {
    setExporting(true);
    try {
      const blob = await adminService.exportOrders({
        from: expFrom || undefined,
        to: expTo || undefined,
        format,
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = format === 'csv' ? 'comenzi.csv' : 'comenzi.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      alert(err.response?.data?.message || 'Exportul a eșuat.');
    } finally {
      setExporting(false);
    }
  };

  const load = () => {
    setLoading(true);
    adminService
      .listOrders({ page, size: 10, status: statusFilter })
      .then((data) => {
        setOrders(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page, statusFilter]);

  const changeStatus = async (order, status) => {
    setSavingStatus(true);
    try {
      const updated = await adminService.updateOrderStatus(order.id, status);
      setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
      if (detail?.id === updated.id) setDetail(updated);
    } catch (err) {
      alert(err.response?.data?.message || 'Actualizarea a eșuat.');
    } finally {
      setSavingStatus(false);
    }
  };

  const handleDelete = async (order) => {
    if (!window.confirm(`Ștergi comanda #${order.id}?`)) return;
    try {
      await adminService.deleteOrder(order.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    }
  };

  return (
    <div>
      <AdminNav />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-800">Management comenzi</h1>
        <select
          className="input sm:w-52"
          value={statusFilter}
          onChange={(e) => {
            setPage(0);
            setStatusFilter(e.target.value);
          }}
        >
          <option value="">Toate statusurile</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      <div className="card mb-4 flex flex-wrap items-end gap-3 p-4">
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">Export — de la</label>
          <input type="date" className="input" value={expFrom} onChange={(e) => setExpFrom(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-slate-500">până la</label>
          <input type="date" className="input" value={expTo} onChange={(e) => setExpTo(e.target.value)} />
        </div>
        <button className="btn-primary" disabled={exporting} onClick={() => doExport('xlsx')}>
          {exporting ? 'Se exportă...' : '⬇ Excel (.xlsx)'}
        </button>
        <button className="btn-secondary" disabled={exporting} onClick={() => doExport('csv')}>
          ⬇ CSV
        </button>
        <span className="text-xs text-slate-400">Lasă datele goale pentru toate comenzile.</span>
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-3">#</th>
                <th className="px-4 py-3">Client</th>
                <th className="px-4 py-3">Data</th>
                <th className="px-4 py-3">Total</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Acțiuni</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {orders.map((o) => (
                <tr key={o.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium">#{o.id}</td>
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-800">{o.userFullName}</p>
                    <p className="text-xs text-slate-500">{o.userEmail}</p>
                  </td>
                  <td className="px-4 py-3 text-slate-500">{formatDate(o.createdAt)}</td>
                  <td className="px-4 py-3 font-medium">{formatPrice(o.totalAmount)}</td>
                  <td className="px-4 py-3">
                    <select
                      className={`badge cursor-pointer border-0 ${statusColor(o.status)}`}
                      value={o.status}
                      disabled={savingStatus}
                      onChange={(e) => changeStatus(o, e.target.value)}
                    >
                      {STATUSES.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => setDetail(o)} className="mr-2 text-brand-600 hover:underline">
                      Detalii
                    </button>
                    <button onClick={() => handleDelete(o)} className="text-red-600 hover:underline">
                      Șterge
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <Modal open={!!detail} title={detail ? `Comanda #${detail.id}` : ''} onClose={() => setDetail(null)}>
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center justify-between text-sm">
              <div>
                <p className="font-medium text-slate-800">{detail.userFullName}</p>
                <p className="text-slate-500">{detail.userEmail}</p>
              </div>
              <span className={`badge ${statusColor(detail.status)}`}>{detail.status}</span>
            </div>
            <div className="text-sm text-slate-600">
              <span className="font-medium">Adresă:</span> {detail.shippingAddress || '-'}
            </div>
            <div className="space-y-2">
              {detail.items.map((it) => (
                <div key={it.id} className="flex items-center gap-3 rounded-lg bg-slate-50 p-2">
                  <img
                    src={resolveImage(it.imageUrl)}
                    alt={it.productName}
                    className="h-10 w-10 rounded object-cover"
                  />
                  <div className="flex-1 text-sm">
                    <p className="font-medium text-slate-700">{it.productName}</p>
                    <p className="text-slate-500">
                      {it.quantity} × {formatPrice(it.unitPrice)}
                    </p>
                  </div>
                  <span className="text-sm font-semibold">{formatPrice(it.subtotal)}</span>
                </div>
              ))}
            </div>
            <div className="flex items-center justify-between border-t border-slate-200 pt-3">
              <span className="font-semibold">Total</span>
              <span className="text-lg font-bold">{formatPrice(detail.totalAmount)}</span>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Schimbă status</label>
              <select
                className="input"
                value={detail.status}
                disabled={savingStatus}
                onChange={(e) => changeStatus(detail, e.target.value)}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
