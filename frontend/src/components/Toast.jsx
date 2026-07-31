import { useEffect, useState } from 'react';

// Minimal pub/sub toast queue — no context provider needed. Mount <ToastHost />
// once on a page and call showToast(...) from anywhere on that page (event
// handlers, promise chains, etc.) to queue a notification.
let toasts = [];
let listeners = [];
let nextId = 1;

function emit() {
  listeners.forEach((listener) => listener([...toasts]));
}

/**
 * Queues a toast notification (feature: notificări).
 *
 * @param message text to show
 * @param type 'success' | 'error' | 'info' — picks the accent color/icon
 * @param duration ms before it auto-dismisses
 * @returns the toast id, in case the caller wants to dismiss it early
 */
export function showToast(message, type = 'success', duration = 4000) {
  const id = nextId++;
  toasts = [...toasts, { id, message, type }];
  emit();
  window.setTimeout(() => dismissToast(id), duration);
  return id;
}

export function dismissToast(id) {
  toasts = toasts.filter((t) => t.id !== id);
  emit();
}

const STYLES = {
  success: 'border-green-200 bg-green-50 text-green-800',
  error: 'border-red-200 bg-red-50 text-red-800',
  info: 'border-blue-200 bg-blue-50 text-blue-800',
};

const ICONS = {
  success: '✓',
  error: '✕',
  info: 'ℹ',
};

/** Mount once per page — renders whatever toasts are currently queued, bottom-right. */
export function ToastHost() {
  const [items, setItems] = useState(toasts);

  useEffect(() => {
    listeners.push(setItems);
    return () => {
      listeners = listeners.filter((l) => l !== setItems);
    };
  }, []);

  if (items.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-[100] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
      {items.map((t) => (
        <div
          key={t.id}
          role="status"
          className={`flex items-start gap-2 rounded-lg border px-4 py-3 text-sm shadow-lg ${
            STYLES[t.type] || STYLES.info
          }`}
        >
          <span className="mt-0.5 font-bold" aria-hidden="true">
            {ICONS[t.type] || ICONS.info}
          </span>
          <span className="flex-1">{t.message}</span>
          <button
            type="button"
            onClick={() => dismissToast(t.id)}
            className="text-current opacity-60 hover:opacity-100"
            aria-label="Închide notificarea"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
