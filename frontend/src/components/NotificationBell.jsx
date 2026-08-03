import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import adminService from '../api/adminService';
import GeoIcon from './xxii/GeoIcon';
import HoloLoader from './xxii/HoloLoader';
import { TYPE_ICON, TYPE_ICON_FALLBACK, TYPE_LABELS } from '../utils/notificationLabels';
import { formatRelative } from '../utils/format';

/** How often the unread badge re-checks the server while the panel is closed. */
const POLL_MS = 20000;

/**
 * Bell + unread badge for the admin rail header (feature #8). Polls the unread
 * count so the badge stays live across pages without the operator refreshing,
 * and opens a small dropdown with the most recent unread items — "Vezi toate"
 * goes to the full notification center for filtering and history.
 *
 * XXII — TASK 1 / TASK 4 / TASK 8. Beyond the glass treatment, three real
 * defects were fixed while converting:
 *
 *   1. **Escape did not close the panel.** A dropdown that traps the keyboard
 *      is a dropdown a keyboard user cannot leave without tabbing through every
 *      item inside it.
 *   2. **The unread count was announced to nobody.** The badge is now inside a
 *      live region with a written label, so a screen reader says "3 notificări
 *      necitite" rather than reading a bare number next to an unlabelled bell.
 *   3. **The trigger had no `aria-haspopup`**, so assistive technology gave no
 *      warning that activating it would open a menu.
 *
 * The panel materializes in 250ms — the ceiling of the interaction budget —
 * rather than appearing instantly, so the eye tracks where it came from.
 */
export default function NotificationBell() {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const rootRef = useRef(null);
  const buttonRef = useRef(null);

  const refreshCount = () => {
    adminService
      .unreadNotificationCount()
      .then((n) => setUnread(Number(n) || 0))
      .catch(() => {
        // A failed poll must not disturb whatever the operator is doing —
        // it just tries again on the next tick.
      });
  };

  useEffect(() => {
    refreshCount();
    const timer = window.setInterval(refreshCount, POLL_MS);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!open) return undefined;

    const onClickOutside = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    };
    const onKeyDown = (e) => {
      if (e.key !== 'Escape') return;
      setOpen(false);
      // Focus goes back to the bell, otherwise it lands on <body> and the next
      // Tab restarts from the top of the page.
      buttonRef.current?.focus();
    };

    document.addEventListener('mousedown', onClickOutside);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onClickOutside);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const togglePanel = () => {
    const next = !open;
    setOpen(next);
    if (next) {
      setLoading(true);
      adminService
        .listNotifications({ unreadOnly: true, page: 0, size: 8 })
        .then((data) => setItems(data.content || []))
        .catch(() => setItems([]))
        .finally(() => setLoading(false));
    }
  };

  const markOneRead = async (id) => {
    try {
      await adminService.markNotificationRead(id);
      setItems((prev) => prev.filter((n) => n.id !== id));
      setUnread((prev) => Math.max(0, prev - 1));
    } catch {
      // Best-effort — the badge will self-correct on the next poll either way.
    }
  };

  return (
    <div className="relative" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        onClick={togglePanel}
        aria-label="Notificări"
        aria-expanded={open}
        aria-haspopup="menu"
        className={`relative flex h-9 w-9 items-center justify-center rounded-[0.7rem] border transition-colors duration-200 ${
          open
            ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[#22e8f5]'
            : 'border-[rgba(255,255,255,0.12)] text-[#c9d4ff] hover:border-[rgba(34,232,245,0.4)] hover:text-[#22e8f5]'
        }`}
      >
        <GeoIcon name="bell" className="h-4 w-4" accent="currentColor" />
        {unread > 0 && (
          <span
            aria-hidden="true"
            className="absolute -right-1 -top-1 flex h-[1.15rem] min-w-[1.15rem] animate-xx-pulse-glow items-center justify-center rounded-full border border-[rgba(255,90,122,0.6)] bg-[#ff5a7a] px-1 text-[10px] font-bold leading-none text-white shadow-[0_0_16px_rgba(255,90,122,0.75)]"
          >
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {/* Contorul citit cu voce tare, separat de insigna vizuală. */}
      <span aria-live="polite" className="sr-only">
        {unread > 0 ? `${unread} notificări necitite` : 'Nicio notificare necitită'}
      </span>

      {open && (
        <div
          role="menu"
          aria-label="Notificări noi"
          className="absolute right-0 top-11 z-50 w-80 max-w-[90vw] animate-xx-materialize rounded-[1rem] border border-[rgba(255,255,255,0.12)] bg-[rgba(9,11,28,0.92)] p-2 shadow-[0_28px_70px_-30px_rgba(0,0,0,0.95),0_0_50px_-18px_rgba(122,60,255,0.55)] backdrop-blur-xl"
        >
          <div className="flex items-center justify-between px-2 py-1.5">
            <p className="text-[0.68rem] font-semibold uppercase tracking-[0.16em] xx-ink-muted">
              Notificări noi
            </p>
            <Link
              to="/admin/notifications"
              onClick={() => setOpen(false)}
              className="text-xs font-semibold text-[#22e8f5] transition-colors duration-200 hover:text-[#7ee9ff]"
            >
              Vezi toate
            </Link>
          </div>

          <div className="max-h-96 space-y-1 overflow-y-auto">
            {loading && <HoloLoader inline size="sm" label="Se încarcă" className="px-2 py-4" />}

            {!loading && items.length === 0 && (
              <p className="px-2 py-5 text-center text-sm xx-ink-muted">
                Nicio notificare necitită.
              </p>
            )}

            {!loading &&
              items.map((n) => (
                <div
                  key={n.id}
                  className="flex items-start gap-2.5 rounded-[0.8rem] border border-transparent px-2 py-2 transition-colors duration-200 hover:border-[rgba(255,255,255,0.1)] hover:bg-[rgba(255,255,255,0.05)]"
                >
                  <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-[rgba(122,60,255,0.35)] bg-[rgba(122,60,255,0.12)]">
                    <GeoIcon
                      name={TYPE_ICON[n.type] || TYPE_ICON_FALLBACK}
                      className="h-3.5 w-3.5"
                    />
                  </span>

                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-[#e8ecff]">{n.title}</p>
                    {n.message && <p className="line-clamp-2 text-xs xx-ink-muted">{n.message}</p>}
                    <p className="mt-0.5 text-[11px] xx-ink-dim">
                      {TYPE_LABELS[n.type] || n.type} · {formatRelative(n.createdAt)}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() => markOneRead(n.id)}
                    title="Marchează ca citită"
                    aria-label={`Marchează „${n.title}” ca citită`}
                    className="shrink-0 rounded-lg border border-transparent p-1.5 text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(31,172,121,0.5)] hover:text-[#7ee9bd]"
                  >
                    <GeoIcon name="check" className="h-3.5 w-3.5" accent="currentColor" />
                  </button>
                </div>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
