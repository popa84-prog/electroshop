import { useCallback, useEffect, useRef, useState } from 'react';
import { resolveImage } from '../utils/format';
import GeoIcon from './xxii/GeoIcon';

/**
 * Dependency-free image viewer: full-screen, click-to-zoom (toward the cursor),
 * swipe on touch, arrow / keyboard navigation, and a thumbnail strip when there
 * is more than one image. Works on both desktop and mobile.
 *
 * XXII — TASK 3 / TASK 8 (Holographic Product View: vizualizarea imaginii).
 *
 * Fundalul era negru plat; acum este o pâclă de sticlă peste pagină, astfel
 * încât produsul pare ridicat deasupra magazinului, nu mutat într-o altă
 * aplicație. Controalele — închidere, navigare, miniaturi — poartă aceeași
 * geometrie ca restul sistemului: contur neon subțire pe fundal translucid.
 *
 * Patru corecții reale făcute odată cu conversia:
 *
 *   1. **`role="dialog"` + `aria-modal`.** Overlayul acoperea complet pagina,
 *      dar nu se anunța ca dialog, deci un cititor de ecran continua să
 *      parcurgă conținutul de dedesubt ca și cum ar fi fost vizibil.
 *   2. **Focusul intră la deschidere și se întoarce la închidere.** Fără asta,
 *      tastatura rămânea pe elementul din spatele overlayului, iar Escape era
 *      singura ieșire posibilă.
 *   3. **Săgețile erau caractere tipografice („‹”, „›”, „×”)**, randate diferit
 *      de fiecare sistem de operare și uneori aproape invizibile. Sunt acum
 *      pictograme `GeoIcon`, identice pe orice platformă.
 *   4. **Un contor „3 / 8” apare când există mai multe imagini.** Poziția în
 *      galerie era deductibilă doar din banda de miniaturi, care pe telefon
 *      este derulabilă și deci parțial ascunsă.
 *
 * Zoom-ul respectă `prefers-reduced-motion`: cu mișcarea redusă, saltul la
 * mărire se face instantaneu, fără tranziție.
 */
export default function Lightbox({ images = [], index = 0, onClose }) {
  const list = images.filter(Boolean);
  const [i, setI] = useState(index);
  const [zoom, setZoom] = useState(false);
  const [origin, setOrigin] = useState('center center');
  const [touchX, setTouchX] = useState(null);
  const closeRef = useRef(null);
  const previouslyFocused = useRef(null);

  const prev = useCallback(() => {
    setZoom(false);
    setI((v) => (v - 1 + list.length) % list.length);
  }, [list.length]);

  const next = useCallback(() => {
    setZoom(false);
    setI((v) => (v + 1) % list.length);
  }, [list.length]);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
      else if (e.key === 'ArrowLeft') prev();
      else if (e.key === 'ArrowRight') next();
    };
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';

    // Focusul se mută pe butonul de închidere, iar la demontare se întoarce
    // exact pe elementul care a deschis vizualizatorul.
    previouslyFocused.current = document.activeElement;
    closeRef.current?.focus();

    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
      if (previouslyFocused.current instanceof HTMLElement) previouslyFocused.current.focus();
    };
  }, [onClose, prev, next]);

  const toggleZoom = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    setOrigin(`${x}% ${y}%`);
    setZoom((z) => !z);
  };

  const onTouchStart = (e) => setTouchX(e.touches[0].clientX);
  const onTouchEnd = (e) => {
    if (touchX == null) return;
    const dx = e.changedTouches[0].clientX - touchX;
    if (Math.abs(dx) > 50 && list.length > 1) {
      if (dx > 0) prev();
      else next();
    }
    setTouchX(null);
  };

  if (list.length === 0) return null;

  const reduceMotion =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const navButton =
    'absolute top-1/2 z-10 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full border border-[rgba(255,255,255,0.16)] bg-[rgba(9,11,28,0.72)] text-[#c9d4ff] backdrop-blur-md transition-colors duration-200 hover:border-[rgba(34,232,245,0.55)] hover:text-[#22e8f5]';

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Vizualizare imagine produs"
      className="fixed inset-0 z-[60] flex animate-xx-materialize items-center justify-center bg-[rgba(3,4,12,0.9)] backdrop-blur-lg"
      onClick={onClose}
    >
      <button
        ref={closeRef}
        type="button"
        className="absolute right-4 top-4 z-10 flex h-10 w-10 items-center justify-center rounded-full border border-[rgba(255,255,255,0.16)] bg-[rgba(9,11,28,0.72)] text-[#c9d4ff] backdrop-blur-md transition-colors duration-200 hover:border-[rgba(255,90,122,0.55)] hover:text-[#ff8fa8]"
        onClick={onClose}
        aria-label="Închide"
      >
        <GeoIcon name="close" className="h-4 w-4" accent="currentColor" />
      </button>

      {list.length > 1 && (
        <>
          <button
            type="button"
            className={`${navButton} left-3`}
            onClick={(e) => {
              e.stopPropagation();
              prev();
            }}
            aria-label="Imaginea anterioară"
          >
            <GeoIcon name="chevron" className="h-4 w-4 rotate-180" accent="currentColor" />
          </button>
          <button
            type="button"
            className={`${navButton} right-3`}
            onClick={(e) => {
              e.stopPropagation();
              next();
            }}
            aria-label="Imaginea următoare"
          >
            <GeoIcon name="chevron" className="h-4 w-4" accent="currentColor" />
          </button>

          {/* Poziția în galerie, citită cu voce tare la fiecare schimbare. */}
          <p
            aria-live="polite"
            className="absolute left-1/2 top-4 z-10 -translate-x-1/2 rounded-full border border-[rgba(255,255,255,0.14)] bg-[rgba(9,11,28,0.72)] px-3.5 py-1 font-mono text-xs font-semibold text-[#c9d4ff] backdrop-blur-md"
          >
            {i + 1} / {list.length}
          </p>
        </>
      )}

      <img
        src={resolveImage(list[i])}
        alt=""
        draggable={false}
        onClick={(e) => {
          e.stopPropagation();
          toggleZoom(e);
        }}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        style={{
          transform: zoom ? 'scale(2.5)' : 'scale(1)',
          transformOrigin: origin,
          cursor: zoom ? 'zoom-out' : 'zoom-in',
          transition: reduceMotion ? 'none' : 'transform 0.2s cubic-bezier(0.22, 1, 0.36, 1)',
          filter: 'drop-shadow(0 30px 70px rgba(0,0,0,0.85))',
        }}
        className="max-h-[85vh] max-w-[92vw] select-none rounded-[0.9rem] object-contain"
      />

      {list.length > 1 && (
        <div
          className="absolute bottom-4 flex max-w-[90vw] gap-2 overflow-x-auto px-2"
          onClick={(e) => e.stopPropagation()}
        >
          {list.map((img, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => {
                setZoom(false);
                setI(idx);
              }}
              aria-label={`Imaginea ${idx + 1}`}
              aria-current={idx === i ? 'true' : undefined}
              className={`h-14 w-14 flex-shrink-0 overflow-hidden rounded-[0.7rem] border-2 transition-all duration-200 ${
                idx === i
                  ? 'border-[rgba(34,232,245,0.85)] shadow-[0_0_22px_-4px_rgba(34,232,245,0.8)]'
                  : 'border-[rgba(255,255,255,0.14)] opacity-60 hover:opacity-100'
              }`}
            >
              <img src={resolveImage(img)} className="h-full w-full object-cover" alt="" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
