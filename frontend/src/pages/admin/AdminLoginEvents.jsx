import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import {
  GeoIcon,
  HoloLoader,
  NeonBadge,
  Reveal,
  SectionHeader,
} from '../../components/xxii';

function fmt(dt) {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleString('ro-RO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dt;
  }
}

// Feature #6: "loguri pentru autentificări reușite/eșuate" — human labels for
// the machine-readable failureReason the backend records on a failed attempt.
const FAILURE_LABELS = {
  bad_credentials: 'Parolă greșită',
  account_locked: 'Cont blocat (brute-force)',
  account_disabled: 'Cont dezactivat',
  not_approved: 'Cont neaprobat',
  bad_2fa_code: 'Cod 2FA greșit',
};

function shortDevice(ua) {
  if (!ua) return '—';
  let os = '';
  if (/Windows/i.test(ua)) os = 'Windows';
  else if (/Android/i.test(ua)) os = 'Android';
  else if (/iPhone|iPad|iOS/i.test(ua)) os = 'iOS';
  else if (/Mac OS X|Macintosh/i.test(ua)) os = 'macOS';
  else if (/Linux/i.test(ua)) os = 'Linux';
  let br = '';
  if (/Edg\//i.test(ua)) br = 'Edge';
  else if (/Chrome\//i.test(ua)) br = 'Chrome';
  else if (/Firefox\//i.test(ua)) br = 'Firefox';
  else if (/Safari\//i.test(ua)) br = 'Safari';
  return [br, os].filter(Boolean).join(' · ') || 'Necunoscut';
}

/** GeoIcon per operating system, so the device column is scannable by shape. */
function deviceIcon(ua) {
  if (!ua) return 'cpu';
  if (/Android|iPhone|iPad|iOS/i.test(ua)) return 'pulse';
  return 'cpu';
}

/**
 * XXII — TASK 6 / TASK 8 (Quantum Control Center: conectări utilizatori).
 *
 * Acesta este un ecran de securitate, deci încercările eșuate trebuie să sară
 * în ochi înaintea celor reușite. Un rând eșuat poartă o bară verticală roșie
 * la marginea din stânga pe lângă insignă, iar motivul eșecului este scris
 * complet în insignă — nu ascuns într-un `title` pe care un utilizator de
 * tastatură sau de ecran tactil nu îl vede niciodată.
 *
 * Un contor al eșecurilor de pe pagina curentă stă în antet: dacă apar zece
 * încercări eșuate pe aceeași pagină, asta se citește dintr-o privire.
 *
 * Coloanele IP și dată folosesc cifre monospațiate pentru aliniere verticală.
 */
export default function AdminLoginEvents() {
  const [events, setEvents] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    adminService
      .loginEvents({ page, size: 25 })
      .then((data) => {
        setEvents(data.content || []);
        setTotalPages(data.totalPages || 0);
      })
      .catch(() => setEvents([]))
      .finally(() => setLoading(false));
  }, [page]);

  const failedOnPage = events.filter((e) => !e.success).length;

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Securitate"
        title="Conectări utilizatori"
        as="h1"
        subtitle="Cine s-a autentificat, de la ce adresă IP și din ce locație aproximativă."
        action={
          !loading && events.length > 0 ? (
            <NeonBadge tone={failedOnPage > 0 ? 'critical' : 'good'} pulse={failedOnPage > 0}>
              {failedOnPage > 0
                ? `${failedOnPage} ${failedOnPage === 1 ? 'încercare eșuată' : 'încercări eșuate'} pe pagină`
                : 'Nicio încercare eșuată pe pagină'}
            </NeonBadge>
          ) : null
        }
      />

      {loading ? (
        <HoloLoader label="Se încarcă conectările" />
      ) : events.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">Nicio conectare înregistrată încă.</p>
        </div>
      ) : (
        <Reveal>
          <div className="card overflow-x-auto">
            <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  {['Status', 'Utilizator', 'Adresă IP', 'Locație', 'Dispozitiv', 'Data / ora'].map(
                    (h) => (
                      <th
                        key={h}
                        className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted"
                      >
                        {h}
                      </th>
                    )
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.06)]">
                {events.map((e) => (
                  <tr
                    key={e.id}
                    className={
                      e.success
                        ? 'transition-colors duration-200'
                        : 'bg-[rgba(255,90,122,0.06)] shadow-[inset_2px_0_0_0_rgba(255,90,122,0.85)] transition-colors duration-200'
                    }
                  >
                    <td className="px-4 py-3">
                      {e.success ? (
                        <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.14)] px-2.5 py-1 text-xs font-semibold text-[#7ee9bd]">
                          <span aria-hidden="true">✓</span>
                          Reușită
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.14)] px-2.5 py-1 text-xs font-semibold text-[#ff8fa8]">
                          <span aria-hidden="true">✕</span>
                          {FAILURE_LABELS[e.failureReason] || 'Eșuată'}
                        </span>
                      )}
                    </td>

                    <td className="px-4 py-3">
                      <p className="font-semibold text-[#e8ecff]">{e.userName || '—'}</p>
                      <p className="text-xs xx-ink-muted">{e.userEmail}</p>
                    </td>

                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs xx-ink-muted">
                      {e.ipAddress || '—'}
                    </td>

                    <td className="px-4 py-3 xx-ink-muted">
                      <span className="inline-flex items-center gap-1.5">
                        <GeoIcon name="globe" className="h-3.5 w-3.5 shrink-0" />
                        {e.location || 'Necunoscut'}
                      </span>
                    </td>

                    <td className="px-4 py-3 xx-ink-muted">
                      <span className="inline-flex items-center gap-1.5">
                        <GeoIcon name={deviceIcon(e.userAgent)} className="h-3.5 w-3.5 shrink-0" />
                        {shortDevice(e.userAgent)}
                      </span>
                    </td>

                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs xx-ink-muted">
                      {fmt(e.loginAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Reveal>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
