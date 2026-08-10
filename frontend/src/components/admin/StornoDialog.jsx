import { useEffect, useMemo, useState } from 'react';
import Modal from '../Modal';
import { NeonButton, NeonBadge } from '../xxii';

/**
 * Dialogul de stornare a unei facturi.
 *
 * Trei decizii de interfață, fiecare cu un motiv concret.
 *
 * **Cantitatea rămasă vine de la server, nu se calculează aici.** Fiecare linie
 * primește `remainingToStorno` din DTO. Dedus în browser din `quantity` minus
 * `stornoedQuantity`, ar exista două implementări ale aceleiași reguli, care se
 * pot despărți la prima modificare. Serverul respinge oricum orice depășire;
 * limita din câmp există doar ca operatorul să nu ajungă acolo.
 *
 * **Motivul este obligatoriu și butonul rămâne blocat fără el.** Serverul refuză
 * o stornare fără motiv, dar un refuz venit după apăsare este o cale mai proastă
 * de a afla decât un buton care spune de la început ce lipsește.
 *
 * **Restituirea stocului este o alegere vizibilă, bifată implicit.** Cazul
 * obișnuit este că marfa se întoarce. Cel în care nu se întoarce — produs
 * deteriorat, pierdut la transport, sau factură emisă din greșeală pentru o
 * comandă care nu a plecat niciodată — este real și frecvent, iar dacă
 * restituirea ar fi automată și invizibilă, stocul s-ar umple de bucăți
 * inexistente fără ca nimeni să observe momentul în care s-a întâmplat.
 */
export default function StornoDialog({ open, invoice, onClose, onConfirm }) {
  const [mode, setMode] = useState('full');
  const [reason, setReason] = useState('');
  const [restock, setRestock] = useState(true);
  const [quantities, setQuantities] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const lines = useMemo(
    () => (invoice?.lines || []).filter((l) => (l.remainingToStorno || 0) > 0),
    [invoice]
  );

  // La fiecare deschidere, formularul repornește curat. Fără asta, motivul
  // scris pentru factura anterioară ar rămâne în câmp și ar putea fi trimis,
  // din inerție, pentru o stornare care nu are nicio legătură cu el.
  useEffect(() => {
    if (!open) return;
    setMode('full');
    setReason('');
    setRestock(true);
    setError(null);
    setSubmitting(false);
    const initial = {};
    (invoice?.lines || []).forEach((l) => {
      initial[l.id] = l.remainingToStorno || 0;
    });
    setQuantities(initial);
  }, [open, invoice]);

  const selectedPieces = useMemo(() => {
    if (mode === 'full') {
      return lines.reduce((sum, l) => sum + (l.remainingToStorno || 0), 0);
    }
    return lines.reduce((sum, l) => sum + clampFor(l, quantities[l.id]), 0);
  }, [mode, lines, quantities]);

  const canSubmit = reason.trim().length > 0 && selectedPieces > 0 && !submitting;

  const handleQuantity = (line, raw) => {
    const next = { ...quantities, [line.id]: clampFor(line, raw) };
    setQuantities(next);
  };

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const payload = { reason: reason.trim(), restock };
      if (mode === 'partial') {
        payload.lines = lines
          .map((l) => ({ lineId: l.id, quantity: clampFor(l, quantities[l.id]) }))
          .filter((l) => l.quantity > 0);
      }
      await onConfirm(payload);
    } catch (e) {
      // Mesajul serverului este mai precis decât orice text generic scris aici:
      // el spune exact ce linie și ce cantitate au fost respinse.
      setError(e?.response?.data?.message || e?.message || 'Stornarea a eșuat.');
      setSubmitting(false);
    }
  };

  if (!invoice) return null;

  return (
    <Modal
      open={open}
      title={`Stornare factura ${invoice.documentNumber || ''}`}
      onClose={submitting ? () => {} : onClose}
      maxWidth="max-w-2xl"
    >
      <div className="space-y-5">
        <p className="text-sm text-[color:var(--xx-ink-dim)]">
          Factura originală rămâne cu numărul ei. Se emite un document nou de stornare, cu număr
          propriu din aceeași serie și cu valori negative, care o referă. Nimic nu se șterge.
        </p>

        {/* ---- Total sau parțial ---- */}
        <div className="flex flex-wrap gap-2">
          <ModeButton active={mode === 'full'} onClick={() => setMode('full')}>
            Stornare totală
          </ModeButton>
          <ModeButton active={mode === 'partial'} onClick={() => setMode('partial')}>
            Stornare parțială
          </ModeButton>
        </div>

        {/* ---- Liniile ---- */}
        {lines.length === 0 ? (
          <p className="rounded-lg border border-[rgba(255,255,255,0.1)] p-4 text-sm text-[color:var(--xx-ink-dim)]">
            Factura este deja stornată integral. Nu mai există nimic de corectat.
          </p>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-[rgba(255,255,255,0.1)]">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[color:var(--xx-ink-dim)]">
                  <th className="px-3 py-2 font-medium">Produs</th>
                  <th className="px-3 py-2 text-center font-medium">Facturat</th>
                  <th className="px-3 py-2 text-center font-medium">Rămas</th>
                  <th className="px-3 py-2 text-center font-medium">De stornat</th>
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => (
                  <tr key={line.id} className="border-t border-[rgba(255,255,255,0.07)]">
                    <td className="px-3 py-2 text-[color:var(--xx-ink)]">{line.productName}</td>
                    <td className="px-3 py-2 text-center text-[color:var(--xx-ink-dim)]">
                      {line.quantity}
                    </td>
                    <td className="px-3 py-2 text-center text-[color:var(--xx-ink-dim)]">
                      {line.remainingToStorno}
                    </td>
                    <td className="px-3 py-2 text-center">
                      {mode === 'full' ? (
                        <span className="text-[color:var(--xx-ink)]">{line.remainingToStorno}</span>
                      ) : (
                        <input
                          type="number"
                          min={0}
                          max={line.remainingToStorno}
                          value={quantities[line.id] ?? 0}
                          onChange={(e) => handleQuantity(line, e.target.value)}
                          className="w-20 rounded-md border border-[rgba(255,255,255,0.15)] bg-transparent px-2 py-1 text-center text-[color:var(--xx-ink)]"
                          aria-label={`Cantitate de stornat pentru ${line.productName}`}
                        />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* ---- Motivul ---- */}
        <div>
          <label
            htmlFor="storno-reason"
            className="mb-1 block text-sm font-medium text-[color:var(--xx-ink)]"
          >
            Motivul stornării <span className="text-[color:var(--xx-danger,#f43f5e)]">*</span>
          </label>
          <textarea
            id="storno-reason"
            rows={2}
            maxLength={500}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Ex: retur integral solicitat de client, produs neconform"
            className="w-full rounded-lg border border-[rgba(255,255,255,0.15)] bg-transparent px-3 py-2 text-sm text-[color:var(--xx-ink)]"
          />
          <p className="mt-1 text-xs text-[color:var(--xx-ink-dim)]">
            Se tipărește pe documentul de stornare și intră în jurnalul de audit. O stornare fără
            explicație este, la un control, o stornare nejustificată.
          </p>
        </div>

        {/* ---- Stocul ---- */}
        <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-[rgba(255,255,255,0.1)] p-3">
          <input
            type="checkbox"
            checked={restock}
            onChange={(e) => setRestock(e.target.checked)}
            className="mt-1"
          />
          <span className="text-sm">
            <span className="block font-medium text-[color:var(--xx-ink)]">
              Readu produsele în stoc
            </span>
            <span className="block text-[color:var(--xx-ink-dim)]">
              Debifează dacă marfa nu s-a întors fizic — deteriorată, pierdută la transport, sau
              factură emisă greșit pentru o comandă care nu a plecat. Restituirea este idempotentă:
              dacă ai anulat deja comanda, cantitățile nu se adaugă a doua oară.
            </span>
          </span>
        </label>

        {error && (
          <p className="rounded-lg border border-[rgba(244,63,94,0.4)] bg-[rgba(244,63,94,0.08)] p-3 text-sm text-[color:var(--xx-ink)]">
            {error}
          </p>
        )}

        {/* ---- Acțiuni ---- */}
        <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
          <NeonBadge>
            {selectedPieces} {selectedPieces === 1 ? 'bucată' : 'bucăți'} de stornat
          </NeonBadge>
          <div className="flex gap-2">
            <NeonButton variant="secondary" onClick={onClose} disabled={submitting}>
              Renunță
            </NeonButton>
            <NeonButton onClick={submit} disabled={!canSubmit} charging={submitting}>
              {submitting ? 'Se emite…' : 'Emite stornarea'}
            </NeonButton>
          </div>
        </div>
      </div>
    </Modal>
  );
}

function ModeButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg border px-3 py-1.5 text-sm transition-colors ${
        active
          ? 'border-[color:var(--xx-cyan)] text-[color:var(--xx-ink)]'
          : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-dim)]'
      }`}
      aria-pressed={active}
    >
      {children}
    </button>
  );
}

/**
 * Plafonează la ce se mai poate storna din linie.
 *
 * Un câmp numeric acceptă text lipit, valori negative și numere arbitrar de
 * mari; `Number()` pe conținut neverificat produce `NaN`, care ar ajunge în
 * cerere și ar fi respins de server cu un mesaj despre altceva.
 */
function clampFor(line, raw) {
  const max = line.remainingToStorno || 0;
  const parsed = Number.parseInt(raw, 10);
  if (Number.isNaN(parsed) || parsed < 0) return 0;
  return Math.min(parsed, max);
}
