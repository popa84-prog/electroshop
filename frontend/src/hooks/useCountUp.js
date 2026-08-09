import { useEffect, useRef, useState } from 'react';

/**
 * Animates a number from zero to its value when it first appears.
 *
 * Task 9 asks for a count-up on the banner cards.
 *
 * ## Reduced motion is honoured, not decorated around
 *
 * `prefers-reduced-motion` is a medical setting before it is a preference: for
 * some people animated numbers trigger nausea or migraine. When it is set the
 * hook returns the final value immediately. It does not animate more slowly or
 * fade instead — the request is for no motion, and a gentler animation is still
 * animation.
 *
 * ## Why the easing is cubic-out rather than linear
 *
 * A linear count reaches its last digit at full speed and stops dead, which
 * reads as a glitch. Cubic-out decelerates into the final value, so the number
 * settles rather than halting, and the eye can follow the last two digits.
 *
 * ## Why it re-animates only from a real change
 *
 * The previous value is remembered, so a re-render with the same number does
 * nothing. A card that re-counts every time its parent re-renders is a card
 * nobody can read.
 *
 * @param {number} value the target
 * @param {{duration?: number, decimals?: number}} options
 * @returns {number} the current animated value
 */
export default function useCountUp(value, options = {}) {
  const { duration = 900, decimals = 0 } = options;

  const target = Number.isFinite(value) ? value : 0;
  const [display, setDisplay] = useState(target);

  const frameRef = useRef(0);
  const fromRef = useRef(target);
  const previousTarget = useRef(target);

  useEffect(() => {
    if (previousTarget.current === target) {
      return undefined;
    }
    fromRef.current = previousTarget.current;
    previousTarget.current = target;

    const reduced = typeof window !== 'undefined'
      && window.matchMedia
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduced || duration <= 0) {
      setDisplay(target);
      return undefined;
    }

    const from = fromRef.current;
    const delta = target - from;
    const start = performance.now();
    const factor = 10 ** decimals;

    const step = (now) => {
      const elapsed = now - start;
      const t = Math.min(1, elapsed / duration);
      // Cubic ease-out: fast at the start, settling into the final value.
      const eased = 1 - (1 - t) ** 3;
      const current = from + delta * eased;

      setDisplay(Math.round(current * factor) / factor);

      if (t < 1) {
        frameRef.current = requestAnimationFrame(step);
      } else {
        // Land exactly on the target. Accumulated float error in the easing
        // can leave the last digit one off, and on a currency figure that is a
        // number somebody will try to reconcile.
        setDisplay(target);
      }
    };

    frameRef.current = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frameRef.current);
  }, [target, duration, decimals]);

  return display;
}
