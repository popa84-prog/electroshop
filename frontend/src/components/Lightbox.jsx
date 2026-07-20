import { useCallback, useEffect, useState } from 'react';
import { resolveImage } from '../utils/format';

/**
 * Dependency-free image viewer: full-screen, click-to-zoom (toward the cursor),
 * swipe on touch, arrow / keyboard navigation, and a thumbnail strip when there
 * is more than one image. Works on both desktop and mobile.
 */
export default function Lightbox({ images = [], index = 0, onClose }) {
  const list = images.filter(Boolean);
  const [i, setI] = useState(index);
  const [zoom, setZoom] = useState(false);
  const [origin, setOrigin] = useState('center center');
  const [touchX, setTouchX] = useState(null);

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
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
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

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/90"
      onClick={onClose}
    >
      <button
        className="absolute right-4 top-3 z-10 text-4xl leading-none text-white/80 hover:text-white"
        onClick={onClose}
        aria-label="Închide"
      >
        ×
      </button>

      {list.length > 1 && (
        <>
          <button
            className="absolute left-3 top-1/2 z-10 -translate-y-1/2 rounded-full bg-white/10 px-4 py-2 text-3xl text-white hover:bg-white/20"
            onClick={(e) => {
              e.stopPropagation();
              prev();
            }}
            aria-label="Anterior"
          >
            ‹
          </button>
          <button
            className="absolute right-3 top-1/2 z-10 -translate-y-1/2 rounded-full bg-white/10 px-4 py-2 text-3xl text-white hover:bg-white/20"
            onClick={(e) => {
              e.stopPropagation();
              next();
            }}
            aria-label="Următor"
          >
            ›
          </button>
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
          transition: 'transform 0.2s ease',
        }}
        className="max-h-[85vh] max-w-[92vw] select-none object-contain"
      />

      {list.length > 1 && (
        <div
          className="absolute bottom-4 flex max-w-[90vw] gap-2 overflow-x-auto px-2"
          onClick={(e) => e.stopPropagation()}
        >
          {list.map((img, idx) => (
            <button
              key={idx}
              onClick={() => {
                setZoom(false);
                setI(idx);
              }}
              className={`h-14 w-14 flex-shrink-0 overflow-hidden rounded border-2 ${
                idx === i ? 'border-white' : 'border-transparent opacity-60 hover:opacity-100'
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
