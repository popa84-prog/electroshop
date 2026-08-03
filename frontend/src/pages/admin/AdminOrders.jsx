import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import {
  GeoIcon,
  HoloLoader,
  NeonButton,
  SectionHeader,
} from '../../components/xxii';
import {
  formatPrice,
  formatDate,
  statusColor,
  statusGlyph,
  statusLabel,
  resolveImage,
} from '../../utils/format';
import { cachedList, invalidateListCache } from '../../utils/listCache';

/**
 * XXII — TASK 6: order management inside the Quantum Control Center.
 *
 * Every network call, the cache namespace and the export logic are unchanged.
 * What changed is how the operator reads the screen:
 *
 *   · The status column was a bare `<select>` wearing a badge class, which
 *     looked like a chip but behaved like a dropdown with no affordance saying
 *     so. It is now an explicit control with a caret, and each status prints a
 *     glyph beside its Romanian name — the state survives greyscale and does
 *     not depend on the operator distinguishing amber from magenta.
 *   · The export toolbar collapses into a slide-in panel instead of occupying
 *     a permanent strip: it is used once a month, not once a minute.
 *   · Row actions are icon buttons with labels rather than three underlined
 *     words, so the destructive one is visibly destructive.
 */

const STATUSES = ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
/** Cache namespace for this page's list (feature #7 — cache pentru liste mari). */
const LIST_CACHE_NS = 'admin-orders';

export default function AdminOrders() {
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [loading, setLoading] = useState(true);

  const [detail, setDetail] = useState(null);
  const [savingStatus, setSavingStatus] = useState(false);

  const [expOpen, setExpOpen] = useState(false);
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
    const params = { page, size: 10, status: statusFilter };
    // Feature #7: short-TTL cache — paging back within a few seconds skips the
    // network round-trip entirely.
    cachedList(LIST_CACHE_NS, params, () => adminService.listOrders(params))
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
      invalidateListCache(LIST_CACHE_NS);
      setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
      if (detail?.id === updated.id) setDetail(updated);
    } catch (err) {
      alert(err.response?.data?.message || 'Actualizarea a eșuat.');
    } finally {
      setSavingStatus(false);
    }
  };

  const downloadInvoice = async (order) => {
    try {
      const blob = await adminService.downloadInvoice(order.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Factura-comanda-${order.id}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      alert(err.response?.data?.message || 'Generarea facturii a eșuat.');
    }
  };

  const handleDelete = async (order) => {
    if (!window.confirm(`Ștergi comanda #${order.id}?`)) return;
    try {
      await adminService.deleteOrder(order.id);
      invalidateListCache(LIST_CACHE_NS);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    }
  };

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Operațiuni"
        title="Management comenzi"
        subtitle="Statusul fiecărei comenzi, factura PDF și exportul contabil pe interval."
        as="h1"
      />

      {/* Filter + export console */}
      <div className="card mb-5 p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label
              htmlFor="ord-status"
              className="mb-1 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim"
            >
              Status
            </label>
            <select
              id="ord-status"
              className="input sm:w-56"
              value={statusFilter}
              onChange={(e) => {
                setPage(0);
                setStatusFilter(e.target.value);
              }}
            >
              <option value="">Toate statusurile</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {statusLabel(s)}
                </option>
              ))}
            </select>
          </div>

          <div className="ml-auto">
            <NeonButton
              variant={expOpen ? 'primary' : 'ghost'}
              onClick={() => setExpOpen((open) => !open)}
              icon={<GeoIcon name="document" className="h-4 w-4" accent="currentColor" />}
              aria-expanded={expOpen}
              aria-controls="ord-export-panel"
            >
              Export contabil
            </NeonButton>
          </div>
        </div>

        {/* Slide-in export panel — materializes rather than jumping into place. */}
        {expOpen ? (
          <div
            id="ord-export-panel"
            className="mt-4 animate-xx-materialize rounded-[1rem] border border-[rgba(122,60,255,0.32)] bg-[rgba(122,60,255,0.07)] p-4"
          >
            <div className="flex flex-wrap items-end gap-3">
              <div>
                <label
                  htmlFor="ord-exp-from"
                  className="mb-1 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim"
                >
                  De la
                </label>
                <input
                  id="ord-exp-from"
                  type="date"
                  className="input"
                  value={expFrom}
                  onChange={(e) => setExpFrom(e.target.value)}
                />
              </div>
              <div>
                <label
                  htmlFor="ord-exp-to"
                  className="mb-1 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim"
                >
                  Până la
                </label>
                <input
                  id="ord-exp-to"
                  type="date"
                  className="input"
                  value={expTo}
                  onChange={(e) => setExpTo(e.target.value)}
                />
              </div>
              <NeonButton
                disabled={exporting}
                charging={exporting}
                onClick={() => doExport('xlsx')}
                icon={<GeoIcon name="chart" className="h-4 w-4" accent="currentColor" />}
              >
                {exporting ? 'Se exportă…' : 'Excel (.xlsx)'}
              </NeonButton>
              <NeonButton
                variant="secondary"
                disabled={exporting}
                onClick={() => doExport('csv')}
                icon={<GeoIcon name="document" className="h-4 w-4" accent="currentColor" />}
              >
                CSV
              </NeonButton>
              <p className="text-xs xx-ink-dim">Lasă datele goale pentru toate comenzile.</p>
            </div>
          </div>
        ) : null}
      </div>

      {loading ? (
        <HoloLoader label="Se încarcă comenzile" />
      ) : orders.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">Nu există comenzi pentru filtrul selectat.</p>
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">#</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Client</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Data</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Total</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.14em]">
                    Acțiuni
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.07)]">
                {orders.map((o) => (
                  <tr key={o.id}>
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-[color:var(--xx-cyan)]">
                      #{o.id}
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-medium text-[color:var(--xx-ink)]">{o.userFullName}</p>
                      <p className="text-xs xx-ink-dim">{o.userEmail}</p>
                    </td>
                    <td className="px-4 py-3 text-xs xx-ink-muted">{formatDate(o.createdAt)}</td>
                    <td className="px-4 py-3 font-semibold tabular-nums text-[color:var(--xx-ink)]">
                      {formatPrice(o.totalAmount)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${statusColor(
                            o.status
                          )}`}
                        >
                          <span aria-hidden="true">{statusGlyph(o.status)}</span>
                          {statusLabel(o.status)}
                        </span>
                        <select
                          className="input h-8 w-8 cursor-pointer appearance-none bg-center p-0 text-center text-xs"
                          value={o.status}
                          disabled={savingStatus}
                          aria-label={`Schimbă statusul comenzii #${o.id}`}
                          onChange={(e) => changeStatus(o, e.target.value)}
                        >
                          {STATUSES.map((s) => (
                            <option key={s} value={s}>
                              {statusLabel(s)}
                            </option>
                          ))}
                        </select>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => downloadInvoice(o)}
                          title="Descarcă factura PDF"
                          aria-label={`Descarcă factura comenzii #${o.id}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.5)] hover:text-[color:var(--xx-cyan)]"
                        >
                          <GeoIcon name="document" className="h-4 w-4" accent="currentColor" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setDetail(o)}
                          title="Detalii comandă"
                          aria-label={`Detaliile comenzii #${o.id}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] hover:text-[#7fb0ff]"
                        >
                          <GeoIcon name="zoom" className="h-4 w-4" accent="currentColor" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(o)}
                          title="Șterge comanda"
                          aria-label={`Șterge comanda #${o.id}`}
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

      <Modal
        open={!!detail}
        title={detail ? `Comanda #${detail.id}` : ''}
        onClose={() => setDetail(null)}
      >
        {detail && (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-3 text-sm">
              <div>
                <p className="font-medium text-[color:var(--xx-ink)]">{detail.userFullName}</p>
                <p className="text-xs xx-ink-muted">{detail.userEmail}</p>
              </div>
              <span
                className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${statusColor(
                  detail.status
                )}`}
              >
                <span aria-hidden="true">{statusGlyph(detail.status)}</span>
                {statusLabel(detail.status)}
              </span>
            </div>

            <div className="rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] p-3 text-sm">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
                Adresă de livrare
              </p>
              <p className="mt-1 xx-ink-muted">{detail.shippingAddress || '—'}</p>
            </div>

            <div className="space-y-2">
              {detail.items.map((it) => (
                <div
                  key={it.id}
                  className="flex items-center gap-3 rounded-xl border border-[rgba(255,255,255,0.09)] bg-[rgba(255,255,255,0.04)] p-2"
                >
                  <img
                    src={resolveImage(it.imageUrl)}
                    alt={it.productName}
                    loading="lazy"
                    className="h-10 w-10 rounded-lg border border-[rgba(255,255,255,0.1)] object-cover"
                  />
                  <div className="flex-1 text-sm">
                    <p className="font-medium text-[color:var(--xx-ink)]">{it.productName}</p>
                    <p className="text-xs xx-ink-dim">
                      {it.quantity} × {formatPrice(it.unitPrice)}
                    </p>
                  </div>
                  <span className="text-sm font-semibold tabular-nums text-[color:var(--xx-ink)]">
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

            <NeonButton
              variant="secondary"
              block
              onClick={() => downloadInvoice(detail)}
              icon={<GeoIcon name="document" className="h-4 w-4" accent="currentColor" />}
            >
              Descarcă factura PDF
            </NeonButton>

            <div>
              <label
                htmlFor="ord-detail-status"
                className="mb-1 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim"
              >
                Schimbă status
              </label>
              <select
                id="ord-detail-status"
                className="input"
                value={detail.status}
                disabled={savingStatus}
                onChange={(e) => changeStatus(detail, e.target.value)}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {statusLabel(s)}
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
