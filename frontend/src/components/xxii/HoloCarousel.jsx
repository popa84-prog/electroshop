import { useCallback, useEffect, useRef, useState } from 'react';
import GeoIcon from './GeoIcon';

/**
 * XXII — TASK 3 / TASK 7 (3D carousel with rotation and depth; intelligent
 * carousel with fluid movement).
 *
 * A horizontal rail with depth. Rather than a JS-driven transform loop, the
 * track is a native scroll container with CSS scroll-snap, and depth is applied
 * per item from its distance to the rail's centre. That choice matters:
 *
 *   - touch, trackpad, shift+wheel, keyboard and screen-reader focus all work
 *     for free, because the browser is doing the scrolling;
 *   - momentum scrolling stays native on iOS instead of fighting a rAF loop;
 *   - the depth pass is read-only, so it cannot desynchronise from the actual
 *     scroll position the way an index-driven transform can.
 *
 * The depth pass is coalesced into one animation frame per scroll burst, so a
 * fast flick recalculates once per painted frame rather than once per event.
 */

export default function HoloCarousel({
  children,
  depth = true,
  arrows = true,
  className = '',
  itemClassName = '',
  label = 'Carusel produse',
}) {
  const trackRef = useRef(null);
  const frameRef = useRef(0);
  const [edges, setEdges] = useState({ start: true, end: false });

  const items = Array.isArray(children) ? children.filter(Boolean) : [children].filter(Boolean);

  /**
   * Applies perspective per card from its horizontal distance to the centre of
   * the viewport of the rail. Cards at the centre sit flat and fully opaque;
   * cards at the edges rotate away and recede.
   */
  const applyDepth = useCallback(() => {
    const track = trackRef.current;
    if (!track) return;

    const rect = track.getBoundingClientRect();
    const centre = rect.left + rect.width / 2;

    setEdges({
      start: track.scrollLeft <= 4,
      end: track.scrollLeft + track.clientWidth >= track.scrollWidth - 4,
    });

    if (!depth) return;

    Array.from(track.children).forEach((child) => {
      const box = child.getBoundingClientRect();
      const childCentre = box.left + box.width / 2;
      // -1 at the far left, 0 at the centre, +1 at the far right.
      const offset = Math.max(-1, Math.min(1, (childCentre - centre) / (rect.width / 2)));
      const distance = Math.abs(offset);

      child.style.transform = `perspective(1100px) rotateY(${(-offset * 13).toFixed(2)}deg) scale(${(
        1 - distance * 0.09
      ).toFixed(3)}) translateZ(${(-distance * 60).toFixed(1)}px)`;
      child.style.opacity = String(1 - distance * 0.42);
      child.style.zIndex = String(100 - Math.round(distance * 100));
    });
  }, [depth]);

  const onScroll = useCallback(() => {
    cancelAnimationFrame(frameRef.current);
    frameRef.current = requestAnimationFrame(applyDepth);
  }, [applyDepth]);

  useEffect(() => {
    // Reduced motion: leave the rail flat. The depth effect is decoration, and
    // a receding, rotating card is exactly what that preference asks us to drop.
    const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (reduced) {
      setEdges({ start: true, end: false });
      return undefined;
    }

    applyDepth();

    const track = trackRef.current;
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(applyDepth) : null;
    if (observer && track) observer.observe(track);
    window.addEventListener('resize', applyDepth);

    return () => {
      cancelAnimationFrame(frameRef.current);
      window.removeEventListener('resize', applyDepth);
      if (observer) observer.disconnect();
    };
  }, [applyDepth, items.length]);

  /** Pages by one visible width minus a card, so context is never lost. */
  const page = (direction) => {
    const track = trackRef.current;
    if (!track) return;
    const first = track.firstElementChild;
    const step = first ? first.getBoundingClientRect().width + 24 : track.clientWidth * 0.8;
    track.scrollBy({ left: direction * step, behavior: 'smooth' });
  };

  return (
    <div className={`relative ${className}`}>
      <div
        ref={trackRef}
        onScroll={onScroll}
        role="group"
        aria-label={label}
        tabIndex={0}
        className="xx-no-scrollbar xx-snap-x flex gap-6 overflow-x-auto scroll-smooth px-1 py-4 focus:outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-[color:var(--xx-cyan)]"
        style={{ perspective: '1100px' }}
      >
        {items.map((child, index) => (
          <div
            key={child?.key ?? index}
            className={`xx-snap-item shrink-0 transition-[transform,opacity] duration-xxslow ease-xx ${itemClassName}`}
            style={{ transformStyle: 'preserve-3d' }}
          >
            {child}
          </div>
        ))}
      </div>

      {arrows && items.length > 1 ? (
        <>
          <button
            type="button"
            onClick={() => page(-1)}
            disabled={edges.start}
            aria-label="Produsele anterioare"
            className="absolute -left-2 top-1/2 z-20 hidden h-11 w-11 -translate-y-1/2 place-items-center rounded-full border border-[rgba(255,255,255,0.16)] bg-[rgba(9,11,28,0.75)] text-white backdrop-blur-glass transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.55)] hover:shadow-glow-aqua disabled:cursor-not-allowed disabled:opacity-25 sm:grid"
          >
            <GeoIcon name="chevron" className="h-5 w-5 rotate-180" />
          </button>
          <button
            type="button"
            onClick={() => page(1)}
            disabled={edges.end}
            aria-label="Produsele următoare"
            className="absolute -right-2 top-1/2 z-20 hidden h-11 w-11 -translate-y-1/2 place-items-center rounded-full border border-[rgba(255,255,255,0.16)] bg-[rgba(9,11,28,0.75)] text-white backdrop-blur-glass transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.55)] hover:shadow-glow-aqua disabled:cursor-not-allowed disabled:opacity-25 sm:grid"
          >
            <GeoIcon name="chevron" className="h-5 w-5" />
          </button>
        </>
      ) : null}
    </div>
  );
}
