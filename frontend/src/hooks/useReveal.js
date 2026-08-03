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
    return () => observer.disconnect();
  }, [threshold, rootMargin, once]);

  return [ref, shown];
}

export default useReveal;
