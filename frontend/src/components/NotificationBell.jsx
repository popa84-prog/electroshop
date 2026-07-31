import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import adminService from '../api/adminService';
import { Icon } from './AdminNav';
import { TYPE_ICON, TYPE_LABELS } from '../utils/notificationLabels';
import { formatRelative } from '../utils/format';

/** How often the unread badge re-checks the server while the panel is closed. */
const POLL_MS = 20000;

/**
 * Bell + unread badge for the admin rail header (feature #8). Polls the unread
 * count so the badge stays live across pages without the operator refreshing,
 * and opens a small dropdown with the most recent unread items — "Vezi toate"
 * goes to the full notification center for filtering and history.
 */
export default function NotificationBell() {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const rootRef = useRef(null);

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
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
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
        type="button"
        onClick={togglePanel}
        aria-label="Notificări"
        aria-expanded={open}
        className="relative flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-700"
      >
        <Icon name="bell" className="h-4 w-4" />
        {unread > 0 && (
          <span className="absolute -right-1 -top-1 flex h-[1.125rem] min-w-[1.125rem] items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold leading-none text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-10 z-50 w-80 max-w-[90vw] rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
          <div className="flex items-center justify-between px-2 py-1.5">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Notificări noi</p>
            <Link to="/admin/notifications" onClick={() => setOpen(false)} className="text-xs font-medium text-brand-600 hover:underline">
              Vezi toate
            </Link>
          </div>
          <div className="max-h-96 space-y-1 overflow-y-auto">
            {loading && <p className="px-2 py-4 text-center text-sm text-slate-400">Se încarcă...</p>}
            {!loading && items.length === 0 && (
              <p className="px-2 py-4 text-center text-sm text-slate-400">Nicio notificare necitită.</p>
            )}
            {!loading &&
              items.map((n) => (
                <div key={n.id} className="flex items-start gap-2 rounded-lg px-2 py-2 hover:bg-slate-50">
                  <span className="mt-0.5 text-base leading-none">{TYPE_ICON[n.type] || '🔔'}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-800">{n.title}</p>
                    {n.message && <p className="line-clamp-2 text-xs text-slate-500">{n.message}</p>}
                    <p className="mt-0.5 text-[11px] text-slate-400">
                      {TYPE_LABELS[n.type] || n.type} · {formatRelative(n.createdAt)}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => markOneRead(n.id)}
                    title="Marchează ca citită"
                    className="shrink-0 rounded-md p-1 text-slate-400 transition hover:bg-slate-200 hover:text-slate-600"
                  >
                    ✓
                  </button>
                </div>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
