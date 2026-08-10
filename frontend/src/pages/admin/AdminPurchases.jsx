import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import productService from '../../api/productService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonButton,
  SectionHeader,
} from '../../components/xxii';
import { formatPrice } from '../../utils/format';

/**
 * XXII — TASK 6: goods-in (stock purchases) inside the Quantum Control Center.
 *
 * The stock arithmetic and every service call are untouched. The line-item
 * editor is the part that actually needed work: it was a flat row of unlabelled
 * inputs where the only way to know which box was "cantitate" and which was
 * "preț de achiziție" was the placeholder — which vanishes the moment a value
 * is typed. Each row now carries a persistent header, the running line total is
 * emphasised, and the grand total sits in a glass footer that stays visible
 * while the operator scrolls a long delivery.
 */

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
    let cancelled = false;
    adminService
      .listSuppliers({ page: 0, size: 200 })
      .then((d) => {
        if (!cancelled) setSuppliers(d.content);
      })
      .catch(() => {});
    adminService
      .listProductsAll()
      .then((d) => {
        if (!cancelled) setProducts(d.content);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
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
    setForm((f) => ({
      ...f,
      items: [...f.items, { productId: '', quantity: 1, unitPurchasePrice: '' }],
    }));

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

      <SectionHeader
        eyebrow="Aprovizionare"
        title="Intrări marfă"
        subtitle="Fiecare intrare crește automat stocul produselor cu cantitățile înregistrate."
        as="h1"
        action={
          <NeonButton
            onClick={openCreate}
            icon={<GeoIcon name="box" className="h-4 w-4" accent="currentColor" />}
          >
            Intrare nouă
          </NeonButton>
        }
      />

      {loading ? (
        <HoloLoader label="Se încarcă intrările" />
      ) : purchases.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">Nicio intrare de marfă înregistrată.</p>
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">#</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Data</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Furnizor</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">NIR</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Factură</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Produse</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Total</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.14em]">
                    Acțiuni
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.07)]">
                {purchases.map((p) => (
                  <tr key={p.id}>
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-[color:var(--xx-cyan)]">
                      #{p.id}
                    </td>
                    <td className="px-4 py-3 text-xs xx-ink-muted">{p.purchaseDate}</td>
                    <td className="px-4 py-3 font-medium text-[color:var(--xx-ink)]">{p.supplierName}</td>
                    {/* Doua numere care nu trebuie confundate: NIR-ul este al
                        magazinului, factura este a furnizorului. Coloane
                        separate, pentru ca puse impreuna ar parea acelasi lucru. */}
                    <td className="px-4 py-3 font-mono text-xs text-[color:var(--xx-ink)]">
                      {p.receptionNumber || '—'}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs xx-ink-dim">{p.invoiceNumber || '—'}</td>
                    <td className="px-4 py-3 tabular-nums xx-ink-muted">{p.items.length}</td>
                    <td className="px-4 py-3 font-semibold tabular-nums text-[color:var(--xx-ink)]">
                      {formatPrice(p.totalAmount)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1.5">
                        {p.receptionNumber && (
                          <button
                            type="button"
                            onClick={() => productService.downloadReceptionNote(p.id)}
                            title={`Descarcă ${p.receptionNumber}`}
                            aria-label={`Descarcă nota de intrare-recepție ${p.receptionNumber}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(13,148,136,0.6)] hover:text-[#5eead4]"
                          >
                            <GeoIcon name="document" className="h-4 w-4" accent="currentColor" />
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setDetail(p)}
                          title="Detaliile intrării"
                          aria-label={`Detaliile intrării #${p.id}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] hover:text-[#7fb0ff]"
                        >
                          <GeoIcon name="zoom" className="h-4 w-4" accent="currentColor" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(p)}
                          title="Șterge intrarea"
                          aria-label={`Șterge intrarea #${p.id}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)]"
                        >
                          <GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
          </table>
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {/* New purchase modal */}
      <Modal
        open={modalOpen}
        title="Intrare marfă nouă"
        onClose={() => setModalOpen(false)}
        maxWidth="max-w-3xl"
      >
        {error && (
          <div className="mb-4 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-sm text-[#ffc2cc]">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <HoloInput
              as="select"
              label="Furnizor *"
              value={form.supplierId}
              onChange={(e) => setForm({ ...form, supplierId: e.target.value })}
              required
            >
              <option value="">Alege…</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </HoloInput>

            <HoloInput
              label="Data"
              type="date"
              value={form.purchaseDate}
              onChange={(e) => setForm({ ...form, purchaseDate: e.target.value })}
            />

            <HoloInput
              label="Nr. factură"
              value={form.invoiceNumber}
              onChange={(e) => setForm({ ...form, invoiceNumber: e.target.value })}
            />
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
                Produse recepționate
              </p>
              <NeonButton
                type="button"
                size="sm"
                variant="ghost"
                onClick={addItemRow}
                icon={<GeoIcon name="layers" className="h-3.5 w-3.5" accent="currentColor" />}
              >
                Adaugă rând
              </NeonButton>
            </div>

            {/* Persistent column headers — a placeholder disappears the moment a
                value is typed, which is exactly when the operator needs it. */}
            <div className="mb-1 hidden gap-2 px-1 text-[0.65rem] font-semibold uppercase tracking-[0.14em] xx-ink-dim sm:flex">
              <span className="flex-1">Produs</span>
              <span className="w-20 text-center">Cant.</span>
              <span className="w-28 text-center">Preț achiziție</span>
              <span className="w-24 text-right">Subtotal</span>
              <span className="w-8" />
            </div>

            <div className="space-y-2">
              {form.items.map((it, idx) => (
                <div
                  key={idx}
                  className="flex flex-wrap items-center gap-2 rounded-xl border border-[rgba(255,255,255,0.09)] bg-[rgba(255,255,255,0.04)] p-2"
                >
                  <select
                    className="input min-w-[160px] flex-1"
                    aria-label={`Produs pe rândul ${idx + 1}`}
                    value={it.productId}
                    onChange={(e) => updateItem(idx, 'productId', e.target.value)}
                  >
                    <option value="">Produs…</option>
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    min="1"
                    className="input w-20 text-center"
                    aria-label={`Cantitate pe rândul ${idx + 1}`}
                    placeholder="Cant."
                    value={it.quantity}
                    onChange={(e) => updateItem(idx, 'quantity', e.target.value)}
                  />
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    className="input w-28 text-center"
                    aria-label={`Preț de achiziție pe rândul ${idx + 1}`}
                    placeholder="Preț achiz."
                    value={it.unitPurchasePrice}
                    onChange={(e) => updateItem(idx, 'unitPurchasePrice', e.target.value)}
                  />
                  <span className="w-24 text-right text-sm font-semibold tabular-nums text-[color:var(--xx-cyan)]">
                    {formatPrice((Number(it.unitPurchasePrice) || 0) * (Number(it.quantity) || 0))}
                  </span>
                  {form.items.length > 1 ? (
                    <button
                      type="button"
                      onClick={() => removeItemRow(idx)}
                      aria-label={`Elimină rândul ${idx + 1}`}
                      title="Elimină rândul"
                      className="grid h-8 w-8 shrink-0 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)]"
                    >
                      <GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />
                    </button>
                  ) : (
                    <span className="w-8 shrink-0" />
                  )}
                </div>
              ))}
            </div>
          </div>

          <HoloInput
            as="textarea"
            label="Note"
            className="min-h-[60px]"
            value={form.notes}
            onChange={(e) => setForm({ ...form, notes: e.target.value })}
          />

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[rgba(34,232,245,0.28)] bg-[rgba(34,232,245,0.07)] p-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">Total intrare</p>
              <p
                className="font-display text-xl font-bold tabular-nums text-[color:var(--xx-ink)]"
                style={{ textShadow: '0 0 26px rgba(34,232,245,0.45)' }}
              >
                {formatPrice(total)}
              </p>
            </div>
            <div className="flex gap-2">
              <NeonButton type="button" variant="ghost" onClick={() => setModalOpen(false)}>
                Anulează
              </NeonButton>
              <NeonButton type="submit" disabled={saving} charging={saving}>
                {saving ? 'Se salvează…' : 'Înregistrează intrarea'}
              </NeonButton>
            </div>
          </div>

          <p className="text-xs xx-ink-dim">
            La salvare, stocul produselor crește automat cu cantitățile introduse.
          </p>
        </form>
      </Modal>

      {/* Detail modal */}
      <Modal
        open={!!detail}
        title={detail ? `Intrare #${detail.id}` : ''}
        onClose={() => setDetail(null)}
      >
        {detail && (
          <div className="space-y-3">
            <dl className="grid grid-cols-1 gap-2 rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] p-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">Furnizor</dt>
                <dd className="text-[color:var(--xx-ink)]">{detail.supplierName}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">Data</dt>
                <dd className="text-[color:var(--xx-ink)]">{detail.purchaseDate}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">Factură</dt>
                <dd className="font-mono text-[color:var(--xx-ink)]">{detail.invoiceNumber || '—'}</dd>
              </div>
              {detail.notes ? (
                <div>
                  <dt className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">Note</dt>
                  <dd className="xx-ink-muted">{detail.notes}</dd>
                </div>
              ) : null}
            </dl>

            <div className="space-y-2">
              {detail.items.map((it) => (
                <div
                  key={it.id}
                  className="flex items-center justify-between gap-3 rounded-xl border border-[rgba(255,255,255,0.09)] bg-[rgba(255,255,255,0.04)] p-2 text-sm"
                >
                  <span className="flex-1 font-medium text-[color:var(--xx-ink)]">{it.productName}</span>
                  <span className="text-xs tabular-nums xx-ink-dim">
                    {it.quantity} × {formatPrice(it.unitPurchasePrice)}
                  </span>
                  <span className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                    {formatPrice(it.subtotal)}
                  </span>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between border-t border-[rgba(255,255,255,0.12)] pt-3">
              <span className="font-semibold xx-ink-muted">Total</span>
              <span className="font-display text-lg font-bold text-[color:var(--xx-ink)]">
                {formatPrice(detail.totalAmount)}
              </span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
