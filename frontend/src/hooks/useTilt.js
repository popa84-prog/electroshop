import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * XXII — TASK 3 / TASK 6 / TASK 8 (3D hover tilt with parallax).
 *
 * Tracks the pointer across an element and returns the inline transform that
 * rotates it toward the cursor, plus the normalised pointer position so a child
 * can drive a parallax highlight from the same data.
 *
 * Design decisions:
 *  - The rotation is applied through a `style` object rather than a CSS class
 *    because the angle is continuous, not a discrete state.
 *  - Updates are coalesced into one `requestAnimationFrame` per frame; a raw
 *    `mousemove` handler fires far more often than the compositor can paint.
 *  - Touch devices are excluded entirely. A tilt driven by a finger that is
 *    also scrolling produces a wobble, and the effect is purely decorative.
 *
 * @param {object} [options]
 * @param {number} [options.max=6]        Maximum rotation in degrees.
 * @param {number} [options.scale=1.02]   Scale applied while hovering.
 * @param {number} [options.perspective=900] Perspective distance in px.
 * @param {boolean}[options.glare=true]   Track pointer position for a highlight.
 * @returns {{ref: object, style: object, active: boolean, pointer: {x:number,y:number},
 *            handlers: {onMouseEnter: Function, onMouseMove: Function, onMouseLeave: Function}}}
 */
export function useTilt({ max = 6, scale = 1.02, perspective = 900, glare = true } = {}) {
  const ref = useRef(null);
  const frame = useRef(0);
  const [active, setActive] = useState(false);
  const [tilt, setTilt] = useState({ rx: 0, ry: 0 });
  const [pointer, setPointer] = useState({ x: 0.5, y: 0.5 });
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const fine = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    setEnabled(fine && !reduced);
  }, []);

  const onMouseMove = useCallback(
    (event) => {
      if (!enabled) return;
      const node = ref.current;
      if (!node) return;

      const rect = node.getBoundingClientRect();
      // Normalised 0..1 position of the pointer inside the element.
      const px = (event.clientX - rect.left) / rect.width;
      const py = (event.clientY - rect.top) / rect.height;

      cancelAnimationFrame(frame.current);
      frame.current = requestAnimationFrame(() => {
        // Centre the range on 0 (-0.5..0.5) and scale to the maximum angle.
        // rotateX is inverted so the surface leans *toward* the cursor.
        setTilt({ rx: -(py - 0.5) * 2 * max, ry: (px - 0.5) * 2 * max });
        if (glare) setPointer({ x: px, y: py });
      });
    },
    [enabled, max, glare]
  );

  const onMouseEnter = useCallback(() => {
    if (enabled) setActive(true);
  }, [enabled]);

  const onMouseLeave = useCallback(() => {
    cancelAnimationFrame(frame.current);
    setActive(false);
    setTilt({ rx: 0, ry: 0 });
    setPointer({ x: 0.5, y: 0.5 });
  }, []);

  useEffect(() => () => cancelAnimationFrame(frame.current), []);

  const style = enabled
    ? {
        transform: `perspective(${perspective}px) rotateX(${tilt.rx.toFixed(2)}deg) rotateY(${tilt.ry.toFixed(
          2
        )}deg) scale(${active ? scale : 1})`,
      }
    : undefined;

  return {
    ref,
    style,
    active,
    pointer,
    handlers: { onMouseEnter, onMouseMove, onMouseLeave },
  };
}

export default useTilt;
