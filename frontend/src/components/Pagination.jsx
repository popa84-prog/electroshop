import { memo, useMemo } from 'react';
import GeoIcon from './xxii/GeoIcon';
import NeonButton from './xxii/NeonButton';

/** How many page-number buttons show around the current page (each side). */
const SIBLINGS = 1;

/**
 * Builds a compact page list with '…' gaps, e.g. for page=6 (0-indexed) of 20:
 * [1, '…', 6, 7, 8, '…', 20]. Always keeps the first and last page visible so
 * jumping to either end is always one click away, however large the list is.
 */
function buildPageList(current, totalPages) {
  const first = 0;
  const last = totalPages - 1;
  const start = Math.max(first, current - SIBLINGS);
  const end = Math.min(last, current + SIBLINGS);

  const pages = [];
  pages.push(first);
  if (start > first + 1) pages.push('gap-start');
  for (let p = start; p <= end; p++) {
    if (p !== first && p !== last) pages.push(p);
  }
  if (end < last - 1) pages.push('gap-end');
  if (last !== first) pages.push(last);
  return pages;
}

/**
 * Feature #7 (performance/UX) — "paginare inteligentă": windowed page numbers
 * instead of just Prev/Next, so jumping several pages ahead (or straight to
 * the last page of a large product/order list) doesn't require clicking
 * through every page in between. Memoized: a page list only re-renders when
 * its own props actually change, not on every parent re-render.
 *
 * XXII — TASK 1 / TASK 8 (paginare în vocabularul sistemului).
 *
 * Cele două butoane de capăt erau `btn-secondary` brut, cu săgețile scrise ca
 * text („← Anterior”, „Următor →”). Sunt acum `NeonButton`, deci primesc aceeași
 * geometrie, aceeași stare de apăsare și aceleași unde la click ca orice alt
 * buton din magazin, iar săgețile sunt `GeoIcon` — randate identic pe orice
 * sistem de operare, spre deosebire de caracterele tipografice.
 *
 * Butoanele numerice rămân butoane proprii, nu `NeonButton`: sunt pătrate de
 * dimensiune fixă într-o bandă compactă, iar `aria-current="page"` este exact
 * atributul corect aici — un `NeonButton` ar aduce padding orizontal variabil
 * și banda și-ar schimba lățimea la trecerea de la pagina 9 la 10.
 */
function Pagination({ page, totalPages, onChange }) {
  const pages = useMemo(() => buildPageList(page, totalPages), [page, totalPages]);

  if (totalPages <= 1) return null;

  return (
    <nav className="mt-6 flex flex-wrap items-center justify-center gap-1" aria-label="Paginare">
      <NeonButton
        variant="secondary"
        size="sm"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        icon={<GeoIcon name="arrow" className="h-3.5 w-3.5 rotate-180" accent="currentColor" />}
      >
        Anterior
      </NeonButton>

      <div className="mx-1 hidden items-center gap-1 sm:flex">
        {pages.map((p) =>
          typeof p === 'number' ? (
            <button
              key={p}
              type="button"
              aria-current={p === page ? 'page' : undefined}
              onClick={() => onChange(p)}
              className={`h-9 min-w-9 rounded-lg border px-2 text-sm font-medium transition-all duration-xx ease-xx ${
                p === page
                  ? 'border-[rgba(34,232,245,0.55)] bg-[rgba(34,232,245,0.14)] text-[color:var(--xx-ink)] shadow-[0_0_26px_-10px_rgba(34,232,245,0.9)]'
                  : 'border-transparent text-[color:var(--xx-ink-muted)] hover:border-[rgba(122,60,255,0.5)] hover:text-[color:var(--xx-ink)]'
              }`}
            >
              {p + 1}
            </button>
          ) : (
            <span key={p} className="px-1 text-sm xx-ink-dim" aria-hidden="true">
              …
            </span>
          )
        )}
      </div>

      <span className="px-2 text-sm xx-ink-muted sm:hidden">
        Pagina {page + 1} din {totalPages}
      </span>

      <NeonButton
        variant="secondary"
        size="sm"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        iconRight={<GeoIcon name="arrow" className="h-3.5 w-3.5" accent="currentColor" />}
      >
        Următor
      </NeonButton>
    </nav>
  );
}

export default memo(Pagination);
