import { useCallback, useEffect, useRef, useState } from 'react';
import GeoIcon from './GeoIcon';

/**
 * XXII — TASK 3 (3D gallery: fluid zoom, subtle reflections, mouse-tilt).
 *
 * Three behaviours layered on one image:
 *
 *   1. Tilt — the frame leans toward the cursor (max 7°), giving the product a
 *      sense of sitting in space rather than on the page.
 *   2. Zoom — the image scales and pans under the cursor, so moving the mouse
 *      inspects the product instead of merely animating it. The pan uses
 *      `transform-origin` at the pointer, which keeps the point under the
 *      cursor stationary while everything around it expands — the same feel as
 *      a real magnifier.
 *   3. Reflection — a mirrored, blurred, heavily faded copy under the frame, so
 *      the product appears to rest on a polished surface.
 *
 * All three are disabled on coarse pointers and under `prefers-reduced-motion`.
 * On touch the thumbnail strip and swipe are the interaction model instead, so
 * nothing is lost.
 *
 * Pointer reads are coalesced into one animation frame, so a fast sweep across
 * the image recalculates once per painted frame rather than once per event.
 */

export default function HoloGallery({ images = [], alt = '', onZoom, className = '' }) {
  const list = images.filter(Boolean);
  const [index, setIndex] = useState(0);
  const [hovering, setHovering] = useState(false);
  const [pointer, setPointer] = useState({ x: 0.5, y: 0.5 });
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const frameRef = useRef(0);
  const boxRef = useRef(null);
  const enabledRef = useRef(false);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    enabledRef.current =
      window.matchMedia('(hover: hover) and (pointer: fine)').matches &&
      !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }, []);

  // A new product means a new image list; reset to the cover.
  useEffect(() => {
    setIndex(0);
  }, [images.length, list[0]]);

  useEffect(() => () => cancelAnimationFrame(frameRef.current), []);

  const onMouseMove = useCallback((event) => {
    if (!enabledRef.current) return;
    const box = boxRef.current;
    if (!box) return;

    const rect = box.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;

    cancelAnimationFrame(frameRef.current);
    frameRef.current = requestAnimationFrame(() => {
      setPointer({ x: Math.min(1, Math.max(0, x)), y: Math.min(1, Math.max(0, y)) });
      // rotateX is inverted so the surface leans *toward* the cursor.
      setTilt({ x: -(y - 0.5) * 14, y: (x - 0.5) * 14 });
    });
  }, []);

  const onMouseLeave = useCallback(() => {
    cancelAnimationFrame(frameRef.current);
    setHovering(false);
    setTilt({ x: 0, y: 0 });
    setPointer({ x: 0.5, y: 0.5 });
  }, []);

  const step = (delta) => {
    if (list.length < 2) return;
    setIndex((current) => (current + delta + list.length) % list.length);
  };

  const current = list[index];

  if (list.length === 0) return null;

  return (
    <div className={className}>
      <div style={{ perspective: '1200px' }}>
        <div
          ref={boxRef}
          onMouseEnter={() => enabledRef.current && setHovering(true)}
          onMouseMove={onMouseMove}
          onMouseLeave={onMouseLeave}
          onKeyDown={(event) => {
            if (event.key === 'ArrowRight') step(1);
            if (event.key === 'ArrowLeft') step(-1);
          }}
          role="group"
          aria-label={`Galerie: ${alt}`}
          tabIndex={0}
          className="group relative overflow-hidden rounded-[1.5rem] border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.04)] shadow-glass-lg transition-shadow duration-xxslow ease-xx focus:outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-[color:var(--xx-cyan)]"
          style={{
            transform: `rotateX(${tilt.x.toFixed(2)}deg) rotateY(${tilt.y.toFixed(2)}deg)`,
            transformStyle: 'preserve-3d',
            transition: 'transform 220ms cubic-bezier(0.22, 1, 0.36, 1)',
            boxShadow: hovering
              ? '0 34px 80px -30px rgba(0,0,0,0.95), 0 0 60px -14px rgba(46,123,255,0.55)'
              : undefined,
          }}
        >
          <div className="aspect-square w-full overflow-hidden sm:aspect-[4/3]">
            <img
              key={current}
              src={current}
              alt={alt}
              className="h-full w-full object-cover animate-xx-materialize"
              style={{
                transform: hovering ? 'scale(1.55)' : 'scale(1)',
                transformOrigin: `${(pointer.x * 100).toFixed(1)}% ${(pointer.y * 100).toFixed(1)}%`,
                transition: 'transform 240ms cubic-bezier(0.22, 1, 0.36, 1)',
              }}
            />
          </div>

          {/* Specular sheen following the cursor. */}
          {hovering ? (
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-0"
              style={{
                background: `radial-gradient(420px circle at ${(pointer.x * 100).toFixed(1)}% ${(
                  pointer.y * 100
                ).toFixed(1)}%, rgba(255,255,255,0.16), transparent 62%)`,
              }}
            />
          ) : null}

          {/* Corner brackets — the viewfinder framing of a targeting HUD. */}
          <span aria-hidden="true" className="pointer-events-none absolute inset-3">
            <span className="absolute left-0 top-0 h-5 w-5 border-l-2 border-t-2 border-[rgba(34,232,245,0.55)]" />
            <span className="absolute right-0 top-0 h-5 w-5 border-r-2 border-t-2 border-[rgba(34,232,245,0.55)]" />
            <span className="absolute bottom-0 left-0 h-5 w-5 border-b-2 border-l-2 border-[rgba(34,232,245,0.55)]" />
            <span className="absolute bottom-0 right-0 h-5 w-5 border-b-2 border-r-2 border-[rgba(34,232,245,0.55)]" />
          </span>

          {onZoom ? (
            <button
              type="button"
              onClick={() => onZoom(index)}
              className="absolute bottom-3 right-3 z-10 inline-flex items-center gap-2 rounded-full border border-[rgba(255,255,255,0.18)] bg-[rgba(4,5,12,0.7)] px-3 py-1.5 text-xs font-semibold text-white backdrop-blur-glass transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.6)] hover:shadow-glow-aqua"
            >
              <GeoIcon name="zoom" className="h-3.5 w-3.5" accent="var(--xx-cyan)" />
              Mărește
            </button>
          ) : null}

          {list.length > 1 ? (
            <>
              <button
                type="button"
                onClick={() => step(-1)}
                aria-label="Imaginea anterioară"
                className="absolute left-3 top-1/2 z-10 grid h-10 w-10 -translate-y-1/2 place-items-center rounded-full border border-[rgba(255,255,255,0.16)] bg-[rgba(4,5,12,0.65)] text-white opacity-0 backdrop-blur-glass transition-all duration-xx ease-xx group-hover:opacity-100 group-focus-within:opacity-100 hover:border-[rgba(34,232,245,0.6)]"
              >
                <GeoIcon name="chevron" className="h-4 w-4 rotate-180" accent="var(--xx-cyan)" />
              </button>
              <button
                type="button"
                onClick={() => step(1)}
                aria-label="Imaginea următoare"
                className="absolute right-3 top-1/2 z-10 grid h-10 w-10 -translate-y-1/2 place-items-center rounded-full border border-[rgba(255,255,255,0.16)] bg-[rgba(4,5,12,0.65)] text-white opacity-0 backdrop-blur-glass transition-all duration-xx ease-xx group-hover:opacity-100 group-focus-within:opacity-100 hover:border-[rgba(34,232,245,0.6)]"
              >
                <GeoIcon name="chevron" className="h-4 w-4" accent="var(--xx-cyan)" />
              </button>
            </>
          ) : null}
        </div>
      </div>

      {/* Reflection — a mirrored, blurred copy that fades out downward. */}
      <div aria-hidden="true" className="relative -mt-1 hidden h-20 overflow-hidden sm:block">
        <img
          src={current}
          alt=""
          className="h-full w-full scale-y-[-1] object-cover opacity-[0.14] blur-[3px]"
          style={{ maskImage: 'linear-gradient(to bottom, black, transparent 82%)', WebkitMaskImage: 'linear-gradient(to bottom, black, transparent 82%)' }}
        />
      </div>

      {/* Thumbnail strip — snap-scrolled, so touch swipe is the mobile model. */}
      {list.length > 1 ? (
        <div className="xx-no-scrollbar xx-snap-x mt-4 flex gap-3 overflow-x-auto pb-1">
          {list.map((url, thumbIndex) => (
            <button
              key={`${url}-${thumbIndex}`}
              type="button"
              onClick={() => setIndex(thumbIndex)}
              aria-label={`Imaginea ${thumbIndex + 1}`}
              aria-current={thumbIndex === index}
              className={`xx-snap-item h-16 w-16 shrink-0 overflow-hidden rounded-xl border transition-all duration-xx ease-xx sm:h-20 sm:w-20 ${
                thumbIndex === index
                  ? 'border-[rgba(34,232,245,0.75)] shadow-glow-aqua'
                  : 'border-[rgba(255,255,255,0.12)] opacity-60 hover:opacity-100'
              }`}
            >
              <img src={url} alt="" loading="lazy" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
