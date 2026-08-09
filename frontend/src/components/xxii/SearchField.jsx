import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';

/**
 * XXII — the sidebar's global search box and its results panel. Task 3.
 *
 * ## It is a combobox, declared as one
 *
 * A text input with a floating list of links below it is a combobox, and saying
 * so — `role="combobox"`, `aria-expanded`, `aria-controls` — is what tells a
 * screen reader that typing produces results and how many arrived. Without it
 * the panel is invisible to anyone not looking at the screen, and the feature
 * exists for exactly the person who cannot find things by scanning.
 *
 * ## Results are grouped by kind
 *
 * Products, orders and users are not comparable, so they are never merged into
 * one ranked list. Each group has its own heading, which is also what lets a
 * group the operator has no permission for be simply absent rather than
 * conspicuously empty.
 *
 * ## Escape closes, and the box keeps focus
 *
 * Closing a panel and dropping focus to the document leaves a keyboard user at
 * the top of the page. Escape closes the results and leaves the cursor where it
 * was, which is what every other search field does.
 */
export default function SearchField({
  query,
  onQueryChange,
  results,
  loading,
  open,
  onOpenChange,
  onClear,
  collapsed = false,
  className = '',
}) {
  const wrapperRef = useRef(null);
  const inputRef = useRef(null);

  // Clicking outside closes the panel. Registered on pointerdown rather than
  // click so the panel closes before a click on the page behind it lands,
  // which stops the first click after a search being swallowed.
  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (event) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target)) {
        onOpenChange(false);
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open, onOpenChange]);

  const total = results?.totalHits ?? 0;
  const showPanel = open && query.trim().length >= 2;

  const groups = [
    { key: 'products', label: 'Produse', items: results?.products || [] },
    { key: 'orders', label: 'Comenzi', items: results?.orders || [] },
    { key: 'users', label: 'Utilizatori', items: results?.users || [] },
  ].filter((group) => group.items.length > 0);

  if (collapsed) {
    // In icon-only mode the field becomes a button that expands the rail. A
    // 44-pixel-wide text input is not a text input anybody can use.
    return (
      <button
        type="button"
        onClick={() => onOpenChange(true)}
        aria-label="Caută în administrare"
        className="grid h-9 w-9 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)]
          text-[color:var(--xx-ink-dim)] transition-colors duration-xx
          hover:border-[color:var(--xx-cyan)] hover:text-[color:var(--xx-cyan)]"
      >
        <SearchGlyph />
      </button>
    );
  }

  return (
    <div ref={wrapperRef} className={`relative ${className}`}>
      <div className="relative">
        <span
          className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2
            text-[color:var(--xx-ink-dim)]"
          aria-hidden="true"
        >
          <SearchGlyph />
        </span>

        <input
          ref={inputRef}
          type="text"
          role="combobox"
          aria-expanded={showPanel}
          aria-controls="admin-search-results"
          aria-autocomplete="list"
          aria-label="Caută produse, comenzi și utilizatori"
          value={query}
          onChange={(event) => {
            onQueryChange(event.target.value);
            onOpenChange(true);
          }}
          onFocus={() => onOpenChange(true)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault();
              onOpenChange(false);
            }
          }}
          placeholder="Caută…"
          className="w-full rounded-lg border border-[rgba(255,255,255,0.12)]
            bg-[rgba(255,255,255,0.04)] py-2 pl-8 pr-8 text-sm text-[color:var(--xx-ink)]
            placeholder:text-[color:var(--xx-ink-dim)] transition-colors duration-xx
            focus:border-[color:var(--xx-cyan)] focus:outline-none"
        />

        {query ? (
          <button
            type="button"
            onClick={onClear}
            aria-label="Golește căutarea"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-[color:var(--xx-ink-dim)]
              transition-colors duration-xx hover:text-[color:var(--xx-ink)]"
          >
            ✕
          </button>
        ) : null}
      </div>

      {/* The count is announced politely so a screen-reader user learns how many
          results arrived without the panel stealing focus mid-typing. */}
      <span className="sr-only" aria-live="polite">
        {loading ? 'Se caută…' : showPanel ? `${total} rezultate` : ''}
      </span>

      {showPanel ? (
        <div
          id="admin-search-results"
          role="listbox"
          className="xx-no-scrollbar absolute left-0 right-0 top-full z-40 mt-1 max-h-80
            overflow-y-auto rounded-xl border border-[rgba(255,255,255,0.14)]
            bg-[rgba(9,10,26,0.97)] p-1.5 shadow-[0_28px_70px_-32px_rgba(0,0,0,0.95)]
            backdrop-blur-glass-lg"
        >
          {loading && !results ? (
            <p className="px-2 py-3 text-xs text-[color:var(--xx-ink-dim)]">Se caută…</p>
          ) : total === 0 ? (
            <p className="px-2 py-3 text-xs text-[color:var(--xx-ink-dim)]">
              Niciun rezultat pentru „{query.trim()}”.
            </p>
          ) : (
            groups.map((group) => (
              <div key={group.key} className="mb-1 last:mb-0">
                <p className="px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em]
                  text-[color:var(--xx-ink-dim)]">
                  {group.label}
                </p>
                {group.items.map((item) => (
                  <Link
                    key={`${group.key}-${item.id}`}
                    to={item.linkTo}
                    role="option"
                    aria-selected="false"
                    onClick={onClear}
                    className="flex items-center justify-between gap-2 rounded-lg px-2 py-1.5
                      text-sm text-[color:var(--xx-ink)] transition-colors duration-xx
                      hover:bg-[rgba(34,232,245,0.1)]"
                  >
                    <span className="min-w-0 truncate">
                      {group.key === 'products' && item.name}
                      {group.key === 'orders' && `#${item.id} · ${item.customerEmail}`}
                      {group.key === 'users' && (item.fullName || item.email)}
                    </span>
                    <span className="shrink-0 text-[10px] text-[color:var(--xx-ink-dim)]">
                      {group.key === 'products' && `${item.stockQuantity} buc.`}
                      {group.key === 'orders' && item.status}
                      {group.key === 'users' && (item.enabled ? 'activ' : 'dezactivat')}
                    </span>
                  </Link>
                ))}
              </div>
            ))
          )}

          {results?.truncated ? (
            <p className="px-2 py-1.5 text-[10px] text-[color:var(--xx-ink-dim)]">
              Se afișează primele rezultate. Rafinează căutarea pentru mai multă precizie.
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function SearchGlyph() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
         strokeWidth="1.9" strokeLinecap="round" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l4.5 4.5" />
    </svg>
  );
}
