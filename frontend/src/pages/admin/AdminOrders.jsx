import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import invoiceService from '../../api/invoiceService';
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

  // Selecția multiplă. Bifa din antet ia pagina curentă; extinderea la întregul
  // set filtrat este o a doua acțiune, explicită, pentru că o operație în masă
  // peste comenzi de pe pagini nevăzute este exact ce nimeni nu vrea din greșeală.
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [totalMatching, setTotalMatching] = useState(0);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [bulkStatus, setBulkStatus] = useState('');
  const [bulkReport, setBulkReport] = useState(null);

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

  /** Notificare scurtă; cade pe alert dacă gazda de toast nu este montată. */
  const showToastSafe = (message) => {
    try {
      window.dispatchEvent(new CustomEvent('xx-toast', { detail: { message, tone: 'success' } }));
    } catch {
      /* fără toast, mesajul rămâne în raportul de sub tabel */
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
        setTotalMatching(data.totalElements ?? data.content.length);
      })
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page, statusFilter]);

  // Schimbarea filtrului goleşte selecţia. Păstrată, ar însemna ca operatorul să
  // aplice o acţiune peste comenzi care nu mai sunt pe ecran şi pe care nu şi le
  // mai aminteşte.
  useEffect(() => {
    setSelectedIds(new Set());
    setBulkReport(null);
  }, [statusFilter]);

  const allOnPageSelected = orders.length > 0 && orders.every((o) => selectedIds.has(o.id));
  const someOnPageSelected = orders.some((o) => selectedIds.has(o.id));
  const selectedCount = selectedIds.size;

  const toggleOne = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
    setBulkReport(null);
  };

  const toggleAllOnPage = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) orders.forEach((o) => next.delete(o.id));
      else orders.forEach((o) => next.add(o.id));
      return next;
    });
    setBulkReport(null);
  };

  /** Extinde selecţia la toate comenzile care corespund filtrului curent. */
  const selectAllMatching = async () => {
    setBulkBusy(true);
    try {
      const ids = await adminService.orderIdsMatching(statusFilter || undefined);
      setSelectedIds(new Set(ids));
    } catch (err) {
      alert(err.response?.data?.message || 'Nu am putut extinde selecția.');
    } finally {
      setBulkBusy(false);
    }
  };

  const clearSelection = () => {
    setSelectedIds(new Set());
    setBulkReport(null);
  };

  // ---- Operaţii în masă ----

  const runBulkStatus = async () => {
    if (!bulkStatus || selectedCount === 0) return;
    const ids = [...selectedIds];

    // Anularea mişcă marfă reală. Confirmarea spune câte bucăţi se întorc,
    // pentru că „eşti sigur?” nu îi dă operatorului informaţia pe baza căreia
    // să răspundă.
    if (bulkStatus === 'CANCELLED') {
      let units = null;
      try {
        const preview = await adminService.previewBulkCancel(ids);
        units = preview.units;
      } catch {
        units = null;
      }
      const line =
        units === null
          ? ''
          : `\n\nSe vor întoarce în stoc ${units} bucăți.`;
      if (
        !window.confirm(
          `Anulezi ${ids.length} comenzi?${line}\n\nComenzile anulate ies din cifra de vânzări.`
        )
      ) {
        return;
      }
    }

    setBulkBusy(true);
    try {
      const report = await adminService.bulkOrderStatus(ids, bulkStatus);
      setBulkReport(report);
      showToastSafe(report.message);
      invalidateListCache(LIST_CACHE_NS);
      setSelectedIds(new Set());
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Operația a eșuat.');
    } finally {
      setBulkBusy(false);
    }
  };

  const runBulkInvoice = async () => {
    if (selectedCount === 0) return;
    const ids = [...selectedIds];
    const withoutInvoice = orders.filter((o) => selectedIds.has(o.id) && !o.invoiceNumber).length;

    if (
      !window.confirm(
        `Emiți facturile pentru ${ids.length} comenzi?\n\n` +
          `Fiecare emitere consumă definitiv un număr fiscal din serie. ` +
          `Comenzile care au deja factură sunt sărite.` +
          (withoutInvoice ? `\n\nPe pagina curentă, ${withoutInvoice} nu au încă factură.` : '')
      )
    ) {
      return;
    }

    setBulkBusy(true);
    try {
      const report = await invoiceService.issueBulk(ids);
      setBulkReport({
        requested: report.requested,
        succeeded: report.issued.length,
        skipped: report.skipped.map((s) => ({ orderId: s.orderId, reason: s.reason })),
        message: report.message,
      });
      showToastSafe(report.message);
      invalidateListCache(LIST_CACHE_NS);
      setSelectedIds(new Set());
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Emiterea a eșuat.');
    } finally {
      setBulkBusy(false);
    }
  };

  const runBulkDelete = async () => {
    if (selectedCount === 0) return;
    const ids = [...selectedIds];
    if (!window.confirm(`Ștergi ${ids.length} comenzi? Operația nu poate fi anulată.`)) return;
    if (!window.confirm(`Confirmi încă o dată ștergerea a ${ids.length} comenzi?`)) return;

    setBulkBusy(true);
    try {
      const report = await adminService.bulkDeleteOrders(ids);
      setBulkReport(report);
      showToastSafe(report.message);
      invalidateListCache(LIST_CACHE_NS);
      setSelectedIds(new Set());
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    } finally {
      setBulkBusy(false);
    }
  };

  /** Emiterea facturii pentru o singură comandă, ca acţiune explicită. */
  const issueInvoice = async (order) => {
    if (
      !window.confirm(
        `Emiți factura pentru comanda #${order.id}?\n\n` +
          'Se alocă definitiv un număr fiscal din serie.'
      )
    ) {
      return;
    }
    try {
      const invoice = await invoiceService.issue(order.id);
      showToastSafe(`Factura ${invoice.documentNumber} emisă.`);
      invalidateListCache(LIST_CACHE_NS);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Emiterea a eșuat.');
    }
  };

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

  /**
   * Descarcă factura deja emisă a comenzii.
   *
   * **Nu mai emite nimic.** Butonul acesta obişnuia să emită factura pe loc
   * dacă lipsea, după o confirmare — ceea ce însemna că o descărcare şi o
   * decizie fiscală stăteau sub acelaşi clic. Un operator care voia doar să
   * vadă documentul consuma un număr din serie. Emiterea este acum o acţiune
   * separată, cu buton propriu, iar aici rămâne strict descărcarea.
   */
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
      alert(err.response?.data?.message || 'Descărcarea a eșuat.');
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
        <>
        {selectedCount > 0 && (
          <div className="card mb-3 border-[rgba(34,232,245,0.35)] p-3">
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-sm font-semibold text-[color:var(--xx-ink)]">
                {selectedCount} {selectedCount === 1 ? 'comandă selectată' : 'comenzi selectate'}
              </span>

              {/* Extinderea la tot setul filtrat este o a doua apăsare, nu un
                  efect al bifei din antet: o operație peste comenzi de pe pagini
                  nevăzute trebuie cerută explicit. */}
              {allOnPageSelected && selectedCount < totalMatching && (
                <button
                  type="button"
                  onClick={selectAllMatching}
                  disabled={bulkBusy}
                  className="text-sm font-semibold text-[color:var(--xx-cyan)] underline underline-offset-2"
                >
                  Selectează toate cele {totalMatching}
                </button>
              )}

              <button
                type="button"
                onClick={clearSelection}
                className="text-sm xx-ink-muted underline underline-offset-2"
              >
                Renunță la selecție
              </button>

              <div className="ml-auto flex flex-wrap items-center gap-2">
                <select
                  className="input h-9 w-44 text-sm"
                  value={bulkStatus}
                  onChange={(e) => setBulkStatus(e.target.value)}
                  aria-label="Status pentru comenzile selectate"
                >
                  <option value="">Schimbă statusul…</option>
                  {STATUSES.map((st) => (
                    <option key={st} value={st}>
                      {statusLabel(st)}
                    </option>
                  ))}
                </select>
                <NeonButton
                  variant="secondary"
                  size="sm"
                  disabled={bulkBusy || !bulkStatus}
                  charging={bulkBusy}
                  onClick={runBulkStatus}
                >
                  Aplică
                </NeonButton>

                <NeonButton
                  variant="secondary"
                  size="sm"
                  disabled={bulkBusy}
                  onClick={runBulkInvoice}
                  icon={<GeoIcon name="document" className="h-4 w-4" accent="currentColor" />}
                >
                  Emite facturi
                </NeonButton>

                <NeonButton
                  variant="danger"
                  size="sm"
                  disabled={bulkBusy}
                  onClick={runBulkDelete}
                  icon={<GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />}
                >
                  Șterge
                </NeonButton>
              </div>
            </div>
          </div>
        )}

        {bulkReport && (
          <div className="card mb-3 p-3 text-sm">
            <div className="flex items-start justify-between gap-3">
              <span className="text-[color:var(--xx-ink)]">{bulkReport.message}</span>
              <button type="button" onClick={() => setBulkReport(null)} aria-label="Închide raportul">
                ×
              </button>
            </div>
            {bulkReport.skipped && bulkReport.skipped.length > 0 && (
              <ul className="mt-2 space-y-0.5 text-xs xx-ink-dim">
                {bulkReport.skipped.slice(0, 10).map((sk) => (
                  <li key={sk.orderId}>
                    #{sk.orderId} — {sk.reason}
                  </li>
                ))}
                {bulkReport.skipped.length > 10 && (
                  <li>și încă {bulkReport.skipped.length - 10}.</li>
                )}
              </ul>
            )}
          </div>
        )}

        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="w-10 px-3 py-3">
                    <input
                      type="checkbox"
                      className="h-4 w-4 cursor-pointer rounded accent-[#22e8f5]"
                      checked={allOnPageSelected}
                      ref={(el) => {
                        // Starea intermediară spune „unele, nu toate” — fără ea,
                        // o pagină parțial selectată arată identic cu una goală.
                        if (el) el.indeterminate = !allOnPageSelected && someOnPageSelected;
                      }}
                      onChange={toggleAllOnPage}
                      aria-label="Selectează comenzile de pe această pagină"
                    />
                  </th>
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
                  <tr key={o.id} className={selectedIds.has(o.id) ? 'bg-[rgba(34,232,245,0.06)]' : ''}>
                    <td className="w-10 px-3 py-3">
                      <input
                        type="checkbox"
                        className="h-4 w-4 cursor-pointer rounded accent-[#22e8f5]"
                        checked={selectedIds.has(o.id)}
                        onChange={() => toggleOne(o.id)}
                        aria-label={`Selectează comanda #${o.id}`}
                      />
                    </td>
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-[color:var(--xx-cyan)]">
                      #{o.id}
                      {o.invoiceNumber && (
                        <span className="mt-0.5 block font-sans text-[10px] font-normal xx-ink-dim">
                          {o.invoiceSeries} {o.invoiceNumber}
                        </span>
                      )}
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
                      {/* Badge-ul si selectorul stateau unul langa altul, iar
                          selectorul, desi ingustat la 8 unitati, isi desena mai
                          departe textul optiunii alese — de unde „Livrata” urmat
                          de un „Livrat…” taiat, in aceeasi celula. Acum selectorul
                          este transparent si acopera badge-ul: se vede o singura
                          eticheta, iar apasarea pe ea deschide lista. */}
                      <div className="relative inline-flex items-center">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 pr-6 text-xs font-semibold ${statusColor(
                            o.status
                          )}`}
                        >
                          <span aria-hidden="true">{statusGlyph(o.status)}</span>
                          {statusLabel(o.status)}
                          <span aria-hidden="true" className="absolute right-2 text-[9px] opacity-70">
                            ▾
                          </span>
                        </span>
                        <select
                          className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
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
                        {/* Doua butoane, nu unul. Descarcarea si emiterea sunt
                            decizii de greutate diferita: prima se poate apasa de
                            zece ori fara consecinte, a doua consuma definitiv un
                            numar fiscal. Sub acelasi buton, un operator care voia
                            sa vada documentul il crea. */}
                        {o.invoiceNumber ? (
                          <button
                            type="button"
                            onClick={() => downloadInvoice(o)}
                            title={`Descarcă factura ${o.invoiceSeries} ${o.invoiceNumber}`}
                            aria-label={`Descarcă factura comenzii #${o.id}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.5)] hover:text-[color:var(--xx-cyan)]"
                          >
                            <GeoIcon name="document" className="h-4 w-4" accent="currentColor" />
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => issueInvoice(o)}
                            title="Emite factura (consumă un număr fiscal)"
                            aria-label={`Emite factura pentru comanda #${o.id}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(110,247,168,0.55)] hover:text-[#6ef7a8]"
                          >
                            <GeoIcon name="check" className="h-4 w-4" accent="currentColor" />
                          </button>
                        )}
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
        </>
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
