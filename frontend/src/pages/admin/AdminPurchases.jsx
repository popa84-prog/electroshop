import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { formatPrice, formatDate } from '../../utils/format';

const todayISO = () => new Date().toISOString().slice(0, 10);

export default function AdminPurchases() {
  const [purchases, setPurchases] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [detail, setDetail] = useState(null);

  const [form, setForm] = useState({
    supplierId: '',
    purchaseDate: todayISO(),
    invoiceNumber: '',
    notes: '',
    items: [{ productId: '', quantity: 1, unitPurchasePrice: '' }],
  });

  const load = () => {
    setLoading(true);
    adminService
      .listPurchases({ page, size: 10 })
      .then((data) => {
        setPurchases(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setPurchases([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page]);

  useEffect(() => {
    adminService.listSuppliers({ page: 0, size: 200 }).then((d) => setSuppliers(d.content)).catch(() => {});
    adminService.listProductsAll().then((d) => setProducts(d.content)).catch(() => {});
  }, []);

  const openCreate = () => {
    setForm({
      supplierId: '',
      purchaseDate: todayISO(),
      invoiceNumber: '',
      notes: '',
      items: [{ productId: '', quantity: 1, unitPurchasePrice: '' }],
    });
    setError(null);
    setModalOpen(true);
  };

  const updateItem = (idx, field, value) => {
    setForm((f) => {
      const items = [...f.items];
      items[idx] = { ...items[idx], [field]: value };
      return { ...f, items };
    });
  };

  const addItemRow = () =>
    setForm((f) => ({ ...f, items: [...f.items, { productId: '', quantity: 1, unitPurchasePrice: '' }] }));

  const removeItemRow = (idx) =>
    setForm((f) => ({ ...f, items: f.items.filter((_, i) => i !== idx) }));

  const total = form.items.reduce(
    (sum, it) => sum + (Number(it.unitPurchasePrice) || 0) * (Number(it.quantity) || 0),
    0
  );

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!form.supplierId) {
      setError('Alege un furnizor.');
      return;
    }
    const items = form.items
      .filter((it) => it.productId)
      .map((it) => ({
        productId: Number(it.productId),
        quantity: Number(it.quantity),
        unitPurchasePrice: Number(it.unitPurchasePrice),
      }));
    if (items.length === 0) {
      setError('Adaugă cel puțin un produs.');
      return;
    }
    setSaving(true);
    try {
      await adminService.createPurchase({
        supplierId: Number(form.supplierId),
        purchaseDate: form.purchaseDate,
        invoiceNumber: form.invoiceNumber,
        notes: form.notes,
        items,
      });
      setModalOpen(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (p) => {
    if (!window.confirm(`Ștergi intrarea #${p.id}? Stocul adăugat va fi scăzut înapoi.`)) return;
    try {
      await adminService.deletePurchase(p.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    }
  };

  return (
    <div>
      <AdminNav />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-800">Intrări marfă (cumpărări)</h1>
        <button className="btn-primary" onClick={openCreate}>
          + Intrare nouă
        </button>
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-3">#</th>
                <th className="px-4 py-3">Data</th>
                <th className="px-4 py-3">Furnizor</th>
                <th className="px-4 py-3">Factură</th>
                <th className="px-4 py-3">Produse</th>
                <th className="px-4 py-3">Total</th>
                <th className="px-4 py-3 text-right">Acțiuni</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {purchases.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-slate-500">
                    Nicio intrare de marfă înregistrată.
                  </td>
                </tr>
              )}
              {purchases.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium">#{p.id}</td>
                  <td className="px-4 py-3 text-slate-600">{p.purchaseDate}</td>
                  <td className="px-4 py-3 text-slate-800">{p.supplierName}</td>
                  <td className="px-4 py-3 text-slate-600">{p.invoiceNumber || '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{p.items.length}</td>
                  <td className="px-4 py-3 font-medium">{formatPrice(p.totalAmount)}</td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => setDetail(p)} className="mr-2 text-brand-600 hover:underline">
                      Detalii
                    </button>
                    <button onClick={() => handleDelete(p)} className="text-red-600 hover:underline">
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

      {/* New purchase modal */}
      <Modal open={modalOpen} title="Intrare marfă nouă" onClose={() => setModalOpen(false)} maxWidth="max-w-3xl">
        {error && (
          <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700">{error}</div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Furnizor *</label>
              <select
                className="input"
                value={form.supplierId}
                onChange={(e) => setForm({ ...form, supplierId: e.target.value })}
                required
              >
                <option value="">Alege...</option>
                {suppliers.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Data</label>
              <input
                type="date"
                className="input"
                value={form.purchaseDate}
                onChange={(e) => setForm({ ...form, purchaseDate: e.target.value })}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Nr. factură</label>
              <input
                className="input"
                value={form.invoiceNumber}
                onChange={(e) => setForm({ ...form, invoiceNumber: e.target.value })}
              />
            </div>
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <label className="text-sm font-medium text-slate-600">Produse</label>
              <button type="button" className="text-sm text-brand-600 hover:underline" onClick={addItemRow}>
                + adaugă rând
              </button>
            </div>
            <div className="space-y-2">
              {form.items.map((it, idx) => (
                <div key={idx} className="flex flex-wrap items-center gap-2">
                  <select
                    className="input flex-1 min-w-[160px]"
                    value={it.productId}
                    onChange={(e) => updateItem(idx, 'productId', e.target.value)}
                  >
                    <option value="">Produs...</option>
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    min="1"
                    className="input w-20"
                    placeholder="Cant."
                    value={it.quantity}
                    onChange={(e) => updateItem(idx, 'quantity', e.target.value)}
                  />
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="input w-28"
                    placeholder="Preț achiz."
                    value={it.unitPurchasePrice}
                    onChange={(e) => updateItem(idx, 'unitPurchasePrice', e.target.value)}
                  />
                  <span className="w-24 text-right text-sm font-medium">
                    {formatPrice((Number(it.unitPurchasePrice) || 0) * (Number(it.quantity) || 0))}
                  </span>
                  {form.items.length > 1 && (
                    <button
                      type="button"
                      className="text-red-500 hover:text-red-700"
                      onClick={() => removeItemRow(idx)}
                    >
                      ✕
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Note</label>
            <textarea
              className="input min-h-[60px]"
              value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
            />
          </div>

          <div className="flex items-center justify-between border-t border-slate-200 pt-3">
            <span className="text-lg font-bold text-slate-900">Total: {formatPrice(total)}</span>
            <div className="flex gap-2">
              <button type="button" className="btn-secondary" onClick={() => setModalOpen(false)}>
                Anulează
              </button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Se salvează...' : 'Înregistrează intrarea'}
              </button>
            </div>
          </div>
          <p className="text-xs text-slate-400">
            La salvare, stocul produselor crește automat cu cantitățile introduse.
          </p>
        </form>
      </Modal>

      {/* Detail modal */}
      <Modal open={!!detail} title={detail ? `Intrare #${detail.id}` : ''} onClose={() => setDetail(null)}>
        {detail && (
          <div className="space-y-3">
            <div className="text-sm text-slate-600">
              <p><span className="font-medium">Furnizor:</span> {detail.supplierName}</p>
              <p><span className="font-medium">Data:</span> {detail.purchaseDate}</p>
              <p><span className="font-medium">Factură:</span> {detail.invoiceNumber || '-'}</p>
              {detail.notes && <p><span className="font-medium">Note:</span> {detail.notes}</p>}
            </div>
            <div className="space-y-2">
              {detail.items.map((it) => (
                <div key={it.id} className="flex items-center justify-between rounded-lg bg-slate-50 p-2 text-sm">
                  <span className="font-medium text-slate-700">{it.productName}</span>
                  <span className="text-slate-500">
                    {it.quantity} × {formatPrice(it.unitPurchasePrice)}
                  </span>
                  <span className="font-semibold">{formatPrice(it.subtotal)}</span>
                </div>
              ))}
            </div>
            <div className="flex items-center justify-between border-t border-slate-200 pt-3">
              <span className="font-semibold">Total</span>
              <span className="text-lg font-bold">{formatPrice(detail.totalAmount)}</span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
