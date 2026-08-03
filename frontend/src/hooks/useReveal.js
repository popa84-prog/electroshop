import { useEffect, useRef, useState } from 'react';

/**
 * XXII — TASK 8 (scroll animations).
 *
 * Reveals an element the first time it enters the viewport, then stops
 * observing it. One-shot on purpose: a card that re-animates every time it
 * scrolls back into view reads as a glitch, not as motion design.
 *
 * Returns `[ref, shown]`. Attach `ref` to the element and drive its classes
 * from `shown`.
 *
 * @param {object}  [options]
 * @param {number}  [options.threshold=0.15] Fraction of the element that must
 *                                           be visible before it reveals.
 * @param {string}  [options.rootMargin='0px 0px -8% 0px'] Fires slightly before
 *                                           the element is fully on screen so
 *                                           the animation finishes as the user
 *                                           arrives at it.
 * @param {boolean} [options.once=true]      Keep observing after the first hit.
 * @returns {[import('react').RefObject<HTMLElement>, boolean]}
 */
export function useReveal({ threshold = 0.15, rootMargin = '0px 0px -8% 0px', once = true } = {}) {
  const ref = useRef(null);
  const [shown, setShown] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return undefined;

    // Two cases where the animation must never gate content visibility:
    // a browser without IntersectionObserver, and a user who asked for reduced
    // motion. Both resolve to "already shown".
    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (prefersReduced || typeof IntersectionObserver === 'undefined') {
      setShown(true);
      return undefined;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setShown(true);
            if (once) observer.unobserve(entry.target);
          } else if (!once) {
            setShown(false);
          }
        });
      },
      { threshold, rootMargin }
    );

    observer.observe(node);

    // Safety net: a page under heavy layout churn (large admin tables,
    // ancestors that measure their own DOM on every mutation) can starve the
    // browser's intersection callback long enough that it never fires for an
    // element that is plainly on screen. When that happens the content stays
    // at opacity 0 forever — a bug, not an animation — so anything still
    // hidden after a generous fixed delay is force-revealed regardless of
    // what the observer decides afterwards.
    const fallback = window.setTimeout(() => setShown(true), 600);

    return () => {
      observer.disconnect();
      window.clearTimeout(fallback);
    };
  }, [threshold, rootMargin, once]);

  return [ref, shown];
}

export default useReveal;
