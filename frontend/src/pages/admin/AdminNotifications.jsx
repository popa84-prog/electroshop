import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import { showToast } from '../../components/Toast';
import { formatRelative } from '../../utils/format';
import {
  TYPE_ICON,
  TYPE_ICON_FALLBACK,
  TYPE_LABELS,
  TYPE_OPTIONS,
  TYPE_STYLE,
  TYPE_STYLE_FALLBACK,
} from '../../utils/notificationLabels';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonBadge,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../../components/xxii';

/**
 * Full notification center (feature #8) — "centru de notificări în admin".
 * Lists every notification (not just unread, unless the filter is used),
 * with mark-as-read per row, a bulk "marchează tot ca citit", and filtering
 * by type. Mirrors AdminAuditLog.jsx's layout so the two "activity" style
 * admin pages feel consistent.
 *
 * XXII — TASK 6 / TASK 8. Rândurile necitite nu mai sunt marcate doar printr-un
 * fundal ușor colorat: poartă o bară verticală cyan la marginea din stânga și o
 * insignă „Necitită”, deci starea se citește și fără percepția culorii și de la
 * distanța la care se lucrează pe un ecran mare.
 *
 * Butonul de marcare per rând era vizibil permanent și, într-o listă de 20 de
 * rânduri, producea 20 de butoane care concurau cu textul. Acum este o
 * pictogramă compactă cu etichetă accesibilă, iar acțiunea în masă rămâne în
 * antet, unde se caută.
 */
export default function AdminNotifications() {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [type, setType] = useState('');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const [markingId, setMarkingId] = useState(null);

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
    setMarkingId(id);
    try {
      await adminService.markNotificationRead(id);
      setItems((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (err) {
      showToast(err.response?.data?.message || 'Nu am putut marca notificarea ca citită.', 'error');
    } finally {
      setMarkingId(null);
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

  const unreadOnPage = items.filter((n) => !n.read).length;

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Activitate"
        title="Notificări"
        as="h1"
        subtitle="Stoc redus, produse fără imagini, produse dezactivate, comenzi noi și conturi blocate."
        action={
          <NeonButton
            variant="secondary"
            onClick={markAllRead}
            disabled={markingAll}
            charging={markingAll}
            icon={<GeoIcon name="check" className="h-4 w-4" />}
          >
            {markingAll ? 'Se marchează...' : 'Marchează tot ca citit'}
          </NeonButton>
        }
      />

      {/* Filtrele stau pe un singur rând deasupra listei, conform TASK 9. */}
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-56">
          <HoloInput
            as="select"
            id="notif-type-filter"
            label="Tip notificare"
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
          </HoloInput>
        </div>

        <label className="mb-6 flex cursor-pointer items-center gap-2.5 rounded-[0.8rem] border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] px-3.5 py-2.5 text-sm text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(34,232,245,0.4)]">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => {
              setUnreadOnly(e.target.checked);
              setPage(0);
            }}
            className="h-4 w-4 cursor-pointer accent-[#22e8f5]"
          />
          Doar necitite
        </label>

        {unreadOnPage > 0 && (
          <span className="mb-6">
            <NeonBadge tone="aqua" pulse>
              {`${unreadOnPage} necitite pe pagină`}
            </NeonBadge>
          </span>
        )}
      </div>

      {loading ? (
        <HoloLoader label="Se încarcă notificările" />
      ) : items.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">
            {type || unreadOnly
              ? 'Nicio notificare pentru acest filtru.'
              : 'Nicio notificare încă.'}
          </p>
        </div>
      ) : (
        <Reveal>
          <div className="card overflow-x-auto">
            <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Tip
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Notificare
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Data
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                    Status
                  </th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.06)]">
                {items.map((n) => (
                  <tr
                    key={n.id}
                    className={
                      n.read
                        ? 'transition-colors duration-200'
                        : 'bg-[rgba(34,232,245,0.06)] shadow-[inset_2px_0_0_0_rgba(34,232,245,0.8)] transition-colors duration-200'
                    }
                  >
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold ${
                          TYPE_STYLE[n.type] || TYPE_STYLE_FALLBACK
                        }`}
                      >
                        <GeoIcon
                          name={TYPE_ICON[n.type] || TYPE_ICON_FALLBACK}
                          className="h-3.5 w-3.5"
                          accent="currentColor"
                        />
                        {TYPE_LABELS[n.type] || n.type}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-semibold text-[#e8ecff]">{n.title}</p>
                      {n.message && <p className="mt-0.5 xx-ink-muted">{n.message}</p>}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs xx-ink-muted">
                      {formatRelative(n.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      {n.read ? (
                        <NeonBadge tone="neutral">Citită</NeonBadge>
                      ) : (
                        <NeonBadge tone="aqua" pulse>
                          Necitită
                        </NeonBadge>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {!n.read && (
                        <button
                          type="button"
                          onClick={() => markOneRead(n.id)}
                          disabled={markingId === n.id}
                          title="Marchează ca citită"
                          aria-label={`Marchează „${n.title}” ca citită`}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(31,172,121,0.5)] hover:text-[#7ee9bd] disabled:opacity-45"
                        >
                          <GeoIcon name="check" className="h-4 w-4" />
                        </button>
                      )}
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
