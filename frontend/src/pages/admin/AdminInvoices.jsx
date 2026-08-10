import { useCallback, useEffect, useMemo, useState } from 'react';
import invoiceService from '../../api/invoiceService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import StornoDialog from '../../components/admin/StornoDialog';
import {
  EmptyState,
  HoloLoader,
  NeonBadge,
  NeonButton,
  HoloInput,
  SectionHeader,
} from '../../components/xxii';
import { useDebounce } from '../../hooks/useDebounce';

/**
 * Registrul de facturi.
 *
 * Până acum nu exista deloc: singura cale către o factură era butonul de
 * descărcare de pe fiecare comandă, deci nu se putea răspunde la întrebări
 * simple precum „ce am facturat luna trecută" sau „unde este factura clientului
 * care tocmai a sunat" fără să cauți comanda întâi.
 *
 * Totalurile din partea de sus vin din același răspuns ca lista, calculate pe
 * server peste tot setul filtrat. Adunarea rândurilor din pagina curentă ar
 * produce cifre care se schimbă la trecerea la pagina a doua — ceea ce nu sunt
 * totaluri. Stornările intră în sumă cu valorile lor negative, deci cifra
 * afișată este ce s-a facturat net, nu suma brută a documentelor emise: o
 * factură stornată integral nu a produs niciun venit și nu are ce căuta în
 * total.
 */

const STATUS_LABELS = {
  ISSUED: 'Emisă',
  PARTIALLY_STORNOED: 'Stornată parțial',
  CANCELLED: 'Stornată integral',
};

// Tonurile disponibile în NeonBadge sunt neutral, neon, aqua, magenta, good,
// warning și critical. O valoare inventată ar cădea tăcut pe „neutral", adică
// toate cele trei stări ar arăta identic.
const STATUS_TONES = {
  ISSUED: 'good',
  PARTIALLY_STORNOED: 'warning',
  CANCELLED: 'critical',
};

const TYPE_LABELS = {
  INVOICE: 'Factură',
  STORNO: 'Storno',
};

export default function AdminInvoices() {
  const [filters, setFilters] = useState({ type: '', status: '', from: '', to: '' });
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [stornoTarget, setStornoTarget] = useState(null);
  const [notice, setNotice] = useState(null);

  const debouncedSearch = useDebounce(search, 350);

  const load = useCallback(
    async (signal) => {
      setLoading(true);
      setError(null);
      try {
        const result = await invoiceService.list(
          { ...filters, q: debouncedSearch, page, size: 20 },
          signal
        );
        setData(result);
      } catch (e) {
        if (e?.name === 'CanceledError' || e?.code === 'ERR_CANCELED') return;
        setError(e?.response?.data?.message || 'Registrul nu a putut fi încărcat.');
      } finally {
        setLoading(false);
      }
    },
    [filters, debouncedSearch, page]
  );

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  // Orice schimbare de filtru readuce la prima pagină. Fără asta, restrângerea
  // rezultatelor de la trei pagini la una, în timp ce operatorul este pe pagina
  // a treia, ar afișa o listă goală care arată exact ca „nu există nimic".
  const changeFilter = (key, value) => {
    setPage(0);
    setFilters((f) => ({ ...f, [key]: value }));
  };

  const onSearch = (value) => {
    setPage(0);
    setSearch(value);
  };

  const summary = data?.summary;
  const rows = data?.content || [];

  const openStorno = async (invoice) => {
    // Lista nu transportă pozițiile — ele sunt cerute abia acum, când chiar
    // sunt editate. Douăzeci de facturi cu toate liniile lor ar însemna date
    // pe care nimeni nu le privește la încărcarea paginii.
    try {
      const full = await invoiceService.get(invoice.id);
      setStornoTarget(full);
    } catch (e) {
      setError(e?.response?.data?.message || 'Factura nu a putut fi deschisă.');
    }
  };

  const confirmStorno = async (payload) => {
    const created = await invoiceService.storno(stornoTarget.id, payload);
    setStornoTarget(null);
    setNotice(
      `Storno ${created.documentNumber} emis pentru factura ${
        stornoTarget.documentNumber
      }. Total ${formatMoney(created.totalGross)} ${created.currency || 'RON'}.`
    );
    const controller = new AbortController();
    load(controller.signal);
  };

  const activeFilterCount = useMemo(
    () => Object.values(filters).filter(Boolean).length + (search ? 1 : 0),
    [filters, search]
  );

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Financiar"
        title="Facturi"
        subtitle="Registrul documentelor fiscale emise: facturi și stornări."
        as="h1"
      />

      <div className="space-y-6">

        {/* ---- Totaluri ---- */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <TotalCard label="Documente" value={summary ? String(summary.documentCount) : '—'} />
          <TotalCard
            label="Valoare fără TVA"
            value={summary ? `${formatMoney(summary.totalNet)} ${summary.currency}` : '—'}
          />
          <TotalCard
            label="TVA"
            value={summary ? `${formatMoney(summary.totalVat)} ${summary.currency}` : '—'}
          />
          <TotalCard
            label="Total facturat net"
            value={summary ? `${formatMoney(summary.totalGross)} ${summary.currency}` : '—'}
            hint="Stornările sunt scăzute"
          />
        </div>

        {/* ---- Filtre ---- */}
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-[220px] flex-1">
            <HoloInput
              label="Caută"
              value={search}
              onChange={(e) => onSearch(e.target.value)}
              placeholder="Număr document, nume sau email client"
            />
          </div>

          <FilterSelect
            label="Tip"
            value={filters.type}
            onChange={(v) => changeFilter('type', v)}
            options={[
              { value: '', label: 'Toate' },
              { value: 'INVOICE', label: 'Facturi' },
              { value: 'STORNO', label: 'Stornări' },
            ]}
          />

          <FilterSelect
            label="Stare"
            value={filters.status}
            onChange={(v) => changeFilter('status', v)}
            options={[
              { value: '', label: 'Toate' },
              { value: 'ISSUED', label: 'Emise' },
              { value: 'PARTIALLY_STORNOED', label: 'Stornate parțial' },
              { value: 'CANCELLED', label: 'Stornate integral' },
            ]}
          />

          <FilterDate
            label="De la"
            value={filters.from}
            onChange={(v) => changeFilter('from', v)}
          />
          <FilterDate label="Până la" value={filters.to} onChange={(v) => changeFilter('to', v)} />

          {activeFilterCount > 0 && (
            <NeonButton
              variant="secondary"
              size="sm"
              onClick={() => {
                setFilters({ type: '', status: '', from: '', to: '' });
                setSearch('');
                setPage(0);
              }}
            >
              Șterge filtrele
            </NeonButton>
          )}
        </div>

        {notice && (
          <div className="flex items-start justify-between gap-3 rounded-lg border border-[rgba(34,197,94,0.35)] bg-[rgba(34,197,94,0.08)] p-3 text-sm text-[color:var(--xx-ink)]">
            <span>{notice}</span>
            <button type="button" onClick={() => setNotice(null)} aria-label="Închide mesajul">
              ×
            </button>
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-[rgba(244,63,94,0.4)] bg-[rgba(244,63,94,0.08)] p-3 text-sm text-[color:var(--xx-ink)]">
            {error}
          </div>
        )}

        {/* ---- Tabelul ---- */}
        {loading && !data ? (
          <HoloLoader />
        ) : rows.length === 0 ? (
          <EmptyState
            title="Niciun document"
            description={
              activeFilterCount > 0
                ? 'Niciun document nu corespunde filtrelor curente.'
                : 'Nu a fost emisă nicio factură încă. Emiterea se face din pagina de comenzi.'
            }
          />
        ) : (
          <div className="overflow-x-auto rounded-xl border border-[rgba(255,255,255,0.1)]">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[color:var(--xx-ink-dim)]">
                  <th className="px-4 py-3 font-medium">Document</th>
                  <th className="px-4 py-3 font-medium">Data</th>
                  <th className="px-4 py-3 font-medium">Client</th>
                  <th className="px-4 py-3 text-right font-medium">Total</th>
                  <th className="px-4 py-3 font-medium">Stare</th>
                  <th className="px-4 py-3 text-right font-medium">Acțiuni</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((inv) => (
                  <tr
                    key={inv.id}
                    className="border-t border-[rgba(255,255,255,0.07)] align-top"
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-[color:var(--xx-ink)]">
                        {inv.documentNumber}
                      </div>
                      <div className="text-xs text-[color:var(--xx-ink-dim)]">
                        {TYPE_LABELS[inv.type] || inv.type}
                        {inv.orderId ? ` · comanda #${inv.orderId}` : ''}
                      </div>
                      {inv.originalDocumentNumber && (
                        <div className="text-xs text-[color:var(--xx-ink-dim)]">
                          storneaza {inv.originalDocumentNumber}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-[color:var(--xx-ink-dim)]">{inv.issuedAt}</td>
                    <td className="px-4 py-3">
                      <div className="text-[color:var(--xx-ink)]">{inv.buyerName || '—'}</div>
                      {inv.buyerEmail && (
                        <div className="text-xs text-[color:var(--xx-ink-dim)]">
                          {inv.buyerEmail}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="font-medium text-[color:var(--xx-ink)]">
                        {formatMoney(inv.totalGross)} {inv.currency}
                      </div>
                      <div className="text-xs text-[color:var(--xx-ink-dim)]">
                        TVA {formatMoney(inv.totalVat)}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <NeonBadge tone={STATUS_TONES[inv.status] || 'neutral'}>
                        {STATUS_LABELS[inv.status] || inv.status}
                      </NeonBadge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <NeonButton
                          variant="secondary"
                          size="sm"
                          onClick={() => invoiceService.download(inv.id)}
                        >
                          PDF
                        </NeonButton>
                        {inv.stornable && (
                          <NeonButton variant="danger" size="sm" onClick={() => openStorno(inv)}>
                            Stornează
                          </NeonButton>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {data && data.totalPages > 1 && (
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        )}
      </div>

      <StornoDialog
        open={Boolean(stornoTarget)}
        invoice={stornoTarget}
        onClose={() => setStornoTarget(null)}
        onConfirm={confirmStorno}
      />
    </div>
  );
}

function TotalCard({ label, value, hint }) {
  return (
    <div className="rounded-xl border border-[rgba(255,255,255,0.1)] p-4">
      <div className="text-xs uppercase tracking-wide text-[color:var(--xx-ink-dim)]">{label}</div>
      <div className="mt-1 font-display text-xl text-[color:var(--xx-ink)]">{value}</div>
      {hint && <div className="mt-1 text-xs text-[color:var(--xx-ink-dim)]">{hint}</div>}
    </div>
  );
}

function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="text-xs text-[color:var(--xx-ink-dim)]">
      <span className="mb-1 block">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-lg border border-[rgba(255,255,255,0.15)] bg-transparent px-3 py-2 text-sm text-[color:var(--xx-ink)]"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function FilterDate({ label, value, onChange }) {
  return (
    <label className="text-xs text-[color:var(--xx-ink-dim)]">
      <span className="mb-1 block">{label}</span>
      <input
        type="date"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-lg border border-[rgba(255,255,255,0.15)] bg-transparent px-3 py-2 text-sm text-[color:var(--xx-ink)]"
      />
    </label>
  );
}

/**
 * Formatare cu două zecimale și separator de mii.
 *
 * Valorile negative ale stornărilor se afișează ca atare, cu semnul minus în
 * față. Ascunderea semnului ar face imposibil de spus, dintr-o privire, dacă un
 * rând din registru a adus sau a scos bani.
 */
function formatMoney(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '0,00';
  return n.toLocaleString('ro-RO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
