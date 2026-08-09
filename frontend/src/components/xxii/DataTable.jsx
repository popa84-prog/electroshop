import { useMemo, useState } from 'react';

/**
 * XXII — a sortable, searchable table. Tasks 13, 15, 17 and 19.
 *
 * ## It is a real `<table>`
 *
 * A grid of divs looks identical and is unusable with a screen reader: there is
 * no row, no column, no header association, so "stoc 3" arrives with no way to
 * know which product it belongs to. Four panels here are tables of numbers where
 * that association is the entire content, so the markup is a table with
 * `scope="col"` headers and `aria-sort` on the active column.
 *
 * ## Sorting is client-side and says so
 *
 * The data these tables show is already capped by the server — fifty rows, not
 * fifty thousand — so sorting in the browser is instant and needs no round trip.
 * What it must not do is pretend to sort data it does not have: the footer
 * states the row count, so an operator sorting by stock knows they are sorting
 * the fifty rows shown rather than the whole catalogue.
 *
 * ## Search filters, it does not query
 *
 * Same reasoning. The box narrows what is on screen, which is what an operator
 * scanning a table wants; a server round trip per keystroke would be slower and
 * would change the row set under their cursor.
 *
 * @param {Array<{key: string, label: string, align?: string, sortable?: boolean,
 *                render?: (row: any) => any, value?: (row: any) => any,
 *                width?: string}>} columns
 * @param {Array<any>} rows
 * @param {string} rowKey field to use as the React key
 */
export default function DataTable({
  columns,
  rows,
  rowKey = 'id',
  searchable = false,
  searchPlaceholder = 'Caută…',
  emptyMessage = 'Nu există date de afișat.',
  compact = false,
  maxHeight = null,
  onRowClick = null,
  className = '',
}) {
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState({ key: null, direction: 'asc' });

  /** Values used for both searching and sorting, so the two always agree. */
  const valueOf = (row, column) => {
    if (column.value) return column.value(row);
    return row?.[column.key];
  };

  const filtered = useMemo(() => {
    if (!query.trim()) return rows || [];
    const needle = query.trim().toLowerCase();
    return (rows || []).filter((row) =>
      columns.some((column) => {
        const value = valueOf(row, column);
        return value !== null && value !== undefined
          && String(value).toLowerCase().includes(needle);
      })
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, columns, query]);

  const sorted = useMemo(() => {
    if (!sort.key) return filtered;
    const column = columns.find((c) => c.key === sort.key);
    if (!column) return filtered;

    const factor = sort.direction === 'asc' ? 1 : -1;
    return [...filtered].sort((a, b) => {
      const av = valueOf(a, column);
      const bv = valueOf(b, column);

      // Missing values sort last in both directions. A null is not smaller than
      // everything — it is unknown — and letting it lead an ascending sort fills
      // the top of the table with rows that say nothing.
      const aMissing = av === null || av === undefined || av === '';
      const bMissing = bv === null || bv === undefined || bv === '';
      if (aMissing && bMissing) return 0;
      if (aMissing) return 1;
      if (bMissing) return -1;

      if (typeof av === 'number' && typeof bv === 'number') {
        return (av - bv) * factor;
      }
      return String(av).localeCompare(String(bv), 'ro') * factor;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtered, sort, columns]);

  const toggleSort = (key) => {
    setSort((prev) =>
      prev.key === key
        ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' }
        : { key, direction: 'asc' }
    );
  };

  const cellPad = compact ? 'px-2 py-1.5' : 'px-3 py-2.5';

  return (
    <div className={className}>
      {searchable ? (
        <div className="mb-3">
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            className="w-full rounded-lg border border-[rgba(255,255,255,0.12)]
              bg-[rgba(255,255,255,0.04)] px-3 py-2 text-sm text-[color:var(--xx-ink)]
              placeholder:text-[color:var(--xx-ink-dim)] transition-colors duration-xx
              focus:border-[color:var(--xx-cyan)] focus:outline-none
              focus:ring-1 focus:ring-[color:var(--xx-cyan)]"
          />
        </div>
      ) : null}

      <div
        className="xx-no-scrollbar overflow-auto"
        style={maxHeight ? { maxHeight } : undefined}
      >
        <table className="w-full border-collapse text-left text-sm">
          <thead className="sticky top-0 z-10 bg-[rgba(9,10,26,0.94)] backdrop-blur-sm">
            <tr>
              {columns.map((column) => {
                const active = sort.key === column.key;
                const sortable = column.sortable !== false;
                return (
                  <th
                    key={column.key}
                    scope="col"
                    style={column.width ? { width: column.width } : undefined}
                    aria-sort={
                      active ? (sort.direction === 'asc' ? 'ascending' : 'descending') : 'none'
                    }
                    className={`${cellPad} border-b border-[rgba(255,255,255,0.1)] text-[11px]
                      font-semibold uppercase tracking-[0.08em] text-[color:var(--xx-ink-dim)]
                      ${column.align === 'right' ? 'text-right' : ''}
                      ${column.align === 'center' ? 'text-center' : ''}`}
                  >
                    {sortable ? (
                      <button
                        type="button"
                        onClick={() => toggleSort(column.key)}
                        className="inline-flex items-center gap-1 transition-colors duration-xx
                          hover:text-[color:var(--xx-ink)]"
                      >
                        {column.label}
                        <span aria-hidden="true" className={active ? 'opacity-100' : 'opacity-30'}>
                          {active && sort.direction === 'desc' ? '↓' : '↑'}
                        </span>
                      </button>
                    ) : (
                      column.label
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>

          <tbody>
            {sorted.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className={`${cellPad} text-center text-[color:var(--xx-ink-dim)]`}
                >
                  {query.trim() ? 'Niciun rezultat pentru căutarea curentă.' : emptyMessage}
                </td>
              </tr>
            ) : (
              sorted.map((row, index) => (
                <tr
                  key={row?.[rowKey] ?? index}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  className={`border-b border-[rgba(255,255,255,0.06)] transition-colors duration-xx
                    hover:bg-[rgba(255,255,255,0.035)]
                    ${onRowClick ? 'cursor-pointer' : ''}`}
                >
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={`${cellPad} align-middle text-[color:var(--xx-ink)]
                        ${column.align === 'right' ? 'text-right tabular-nums' : ''}
                        ${column.align === 'center' ? 'text-center' : ''}`}
                    >
                      {column.render ? column.render(row) : valueOf(row, column) ?? '—'}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Stating the count is what keeps client-side sorting honest: the operator
          can see they are sorting these rows, not the whole dataset. */}
      {sorted.length > 0 ? (
        <p className="mt-2 text-[11px] text-[color:var(--xx-ink-dim)]">
          {query.trim()
            ? `${sorted.length} din ${(rows || []).length} rânduri`
            : `${sorted.length} rânduri`}
        </p>
      ) : null}
    </div>
  );
}
