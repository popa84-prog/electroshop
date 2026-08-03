import { useEffect, useRef } from 'react';
import GeoIcon from './xxii/GeoIcon';

/**
 * XXII — TASK 6 / TASK 8: the dialog every admin screen opens.
 *
 * Three things changed beyond the surface treatment, and each fixes a real
 * defect in the previous version rather than restyling it:
 *
 *   1. **Escape closes it.** Before, the only way out was the ✕ or the scrim,
 *      which is a keyboard trap in everything but name.
 *   2. **Focus moves into the dialog on open and returns to the trigger on
 *      close.** Without this a screen-reader user opening "Editează produs"
 *      stays parked behind the dialog, reading the page underneath.
 *   3. **The page behind stops scrolling while it is open.** A long product
 *      form over a long product table otherwise scrolls the table instead of
 *      the form.
 *
 * The panel materializes rather than appearing — 250ms, the top of the global
 * motion budget, because a dialog arriving instantly reads as a glitch. The
 * scrim carries its own blur so the control center visibly recedes behind it.
 */
export default function Modal({ open, title, onClose, children, maxWidth = 'max-w-lg' }) {
  const panelRef = useRef(null);
  const restoreRef = useRef(null);

  // `onClose` is passed as a brand-new inline arrow function by every caller
  // on every parent re-render — and the parent re-renders on every single
  // keystroke in any field inside the dialog, because typing updates form
  // state one level up. If the focus-management effect below depended on
  // `onClose` directly, it would tear down and re-run on every keystroke:
  // its cleanup unconditionally restores focus to whatever opened the
  // dialog, then its body re-schedules a fresh "focus the first control"
  // pass — wrenching focus out of the field the operator is actively typing
  // into, over and over. Keeping the latest callback in a ref lets the
  // effect read a live `onClose` without `onClose`'s identity being part of
  // its dependency array.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return undefined;

    // Remember who opened us, so focus can go home afterwards.
    restoreRef.current = document.activeElement;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onCloseRef.current?.();
      }
    };
    document.addEventListener('keydown', onKeyDown);

    // Focus the first genuine form control inside the panel — never the
    // close button, even though it sits earlier in the DOM (the header is
    // rendered before the form). Landing focus on "✕" first is what made a
    // freshly opened dialog feel like it was fighting the operator; falling
    // back to the panel itself keeps the dialog from being left with focus
    // outside it when it has no fields at all (a plain confirmation, say).
    const frame = requestAnimationFrame(() => {
      const panel = panelRef.current;
      if (!panel) return;
      const field = panel.querySelector(
        'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])'
      );
      const anyFocusable = panel.querySelector(
        'button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
      );
      (field || anyFocusable || panel).focus({ preventScroll: true });
    });

    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      const restore = restoreRef.current;
      if (restore && typeof restore.focus === 'function') restore.focus({ preventScroll: true });
    };
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-[rgba(3,4,12,0.72)] backdrop-blur-xxs"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
        tabIndex={-1}
        className={`card card-static relative z-10 w-full ${maxWidth} max-h-[90vh] animate-xx-materialize overflow-y-auto p-6 shadow-[0_40px_100px_-40px_rgba(0,0,0,0.95),0_0_70px_-24px_rgba(122,60,255,0.6)] outline-none`}
      >
        <div className="mb-4 flex items-center justify-between gap-3 border-b border-[rgba(255,255,255,0.1)] pb-3">
          <h2 className="font-display text-lg font-semibold text-[color:var(--xx-ink)]">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className="grid h-8 w-8 shrink-0 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.5)] hover:text-[color:var(--xx-red)]"
            aria-label="Închide"
          >
            <GeoIcon name="close" className="h-4 w-4" accent="currentColor" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
