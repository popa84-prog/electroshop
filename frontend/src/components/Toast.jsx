import { useEffect, useState } from 'react';
import GeoIcon from './xxii/GeoIcon';

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

/*
 * XXII — TASK 1 / TASK 8. Cele trei stiluri purtau perechi Tailwind pentru
 * fundal alb (`bg-green-50 text-green-800` și celelalte două), care pe
 * suprafața întunecată apăreau ca dreptunghiuri palide cu text aproape negru.
 * Fiecare valoare este acum un jeton de sticlă din exact aceeași familie de
 * culori validată ca insignele de audit — un succes arată la fel indiferent
 * dacă îl anunță un toast sau o insignă dintr-un tabel.
 *
 * „info” primește violetul, nu albastrul-cyan: cyan poartă în tot sistemul
 * XXII semnificația de element interactiv, iar un toast nu se apasă.
 */
const STYLES = {
  success:
    'border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.14)] text-[#7ee9bd] shadow-[0_20px_50px_-24px_rgba(0,0,0,0.95),0_0_44px_-16px_rgba(31,172,121,0.6)]',
  error:
    'border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.14)] text-[#ff8fa8] shadow-[0_20px_50px_-24px_rgba(0,0,0,0.95),0_0_44px_-16px_rgba(255,90,122,0.6)]',
  info:
    'border-[rgba(122,60,255,0.5)] bg-[rgba(122,60,255,0.16)] text-[#b795ff] shadow-[0_20px_50px_-24px_rgba(0,0,0,0.95),0_0_44px_-16px_rgba(122,60,255,0.6)]',
};

/*
 * Pictogramele erau caractere tipografice („✓”, „✕”, „ℹ”), randate de fontul
 * sistemului — deci alt desen pe Windows, altul pe macOS, altul pe Android.
 * `GeoIcon` desenează același contur geometric peste tot și, mai important,
 * dublează culoarea printr-o formă distinctă: un cititor care nu separă roșul
 * de verde vede totuși un semn de bifă față de un triunghi de alertă.
 */
const ICONS = {
  success: 'check',
  error: 'alert',
  info: 'bell',
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
    /*
      Containerul este `position: fixed`, deci trebuie montat direct în pagină,
      niciodată în interiorul unui `Reveal` sau `TiltCard`: acele componente
      lasă un `filter` activ pe element, iar orice `filter` diferit de `none`
      creează un bloc de conținere pentru descendenții `fixed` — toast-urile
      s-ar ancora atunci în colțul cardului, nu al ferestrei.
    */
    <div className="fixed bottom-4 right-4 z-[100] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
      {items.map((t) => (
        <div
          key={t.id}
          role="status"
          className={`flex animate-xx-materialize items-start gap-2.5 rounded-[0.9rem] border px-4 py-3 text-sm backdrop-blur-xl ${
            STYLES[t.type] || STYLES.info
          }`}
        >
          <span className="mt-0.5 shrink-0" aria-hidden="true">
            <GeoIcon name={ICONS[t.type] || ICONS.info} className="h-4 w-4" accent="currentColor" />
          </span>

          <span className="flex-1 text-[#e8ecff]">{t.message}</span>

          <button
            type="button"
            onClick={() => dismissToast(t.id)}
            className="-mr-1 shrink-0 rounded-lg p-1 text-current opacity-60 transition-opacity duration-200 hover:opacity-100"
            aria-label="Închide notificarea"
          >
            <GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />
          </button>
        </div>
      ))}
    </div>
  );
}
