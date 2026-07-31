import { memo, useMemo } from 'react';

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
 */
function Pagination({ page, totalPages, onChange }) {
  const pages = useMemo(() => buildPageList(page, totalPages), [page, totalPages]);

  if (totalPages <= 1) return null;

  return (
    <nav className="mt-6 flex flex-wrap items-center justify-center gap-1" aria-label="Paginare">
      <button
        type="button"
        className="btn-secondary"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        ← Anterior
      </button>

      <div className="mx-1 hidden items-center gap-1 sm:flex">
        {pages.map((p) =>
          typeof p === 'number' ? (
            <button
              key={p}
              type="button"
              aria-current={p === page ? 'page' : undefined}
              onClick={() => onChange(p)}
              className={`h-9 min-w-9 rounded-lg px-2 text-sm font-medium transition ${
                p === page
                  ? 'bg-brand-600 text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {p + 1}
            </button>
          ) : (
            <span key={p} className="px-1 text-sm text-slate-400" aria-hidden="true">
              …
            </span>
          )
        )}
      </div>

      <span className="px-2 text-sm text-slate-600 sm:hidden">
        Pagina {page + 1} din {totalPages}
      </span>

      <button
        type="button"
        className="btn-secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Următor →
      </button>
    </nav>
  );
}

export default memo(Pagination);
