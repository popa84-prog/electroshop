import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import productService from '../../api/productService';
import { useDebounce } from '../../hooks/useDebounce';
import { formatPrice, resolveImage } from '../../utils/format';
import { trackSearch } from '../../utils/recommendations';
import GeoIcon from './GeoIcon';

/**
 * XXII — TASK 4 (neon search bar: visual autocomplete, scan effect on focus).
 *
 * "Visual" autocomplete means the suggestions are products with thumbnails and
 * prices, not a list of strings. The user recognises the item they wanted
 * before they finish typing its name, which is the entire value of the feature.
 *
 * Behaviour that a plain input does not have:
 *
 *   - Keystrokes are debounced at 260ms, so typing "televizor" issues one
 *     request instead of nine.
 *   - Every in-flight request carries a sequence number; a response whose
 *     sequence is not the latest is discarded. Without that, a slow request for
 *     "tel" can land after a fast one for "televizor" and overwrite the correct
 *     results with stale ones.
 *   - Full keyboard control: ↑/↓ move the active suggestion, Enter opens it (or
 *     runs the full search when nothing is highlighted), Escape closes.
 *   - The combobox ARIA pattern is wired properly, so the listbox is announced
 *     and the active option is reported via `aria-activedescendant`.
 *
 * The scan bar sweeps once per focus — the holographic cue from TASK 1.
 */

const MAX_SUGGESTIONS = 6;

export default function NeonSearch({ className = '', compact = false, onNavigate }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [focused, setFocused] = useState(false);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [active, setActive] = useState(-1);

  const debounced = useDebounce(query, 260);
  const containerRef = useRef(null);
  const inputRef = useRef(null);
  const sequenceRef = useRef(0);

  const listId = 'xx-search-listbox';

  useEffect(() => {
    const term = debounced.trim();

    if (term.length < 2) {
      setResults([]);
      setLoading(false);
      return undefined;
    }

    const sequence = ++sequenceRef.current;
    setLoading(true);

    productService
      .list({ page: 0, size: MAX_SUGGESTIONS, search: term })
      .then((data) => {
        // Discard out-of-order responses.
        if (sequence !== sequenceRef.current) return;
        const list = Array.isArray(data) ? data : data?.content || [];
        setResults(list.slice(0, MAX_SUGGESTIONS));
        setActive(-1);
      })
      .catch(() => {
        if (sequence === sequenceRef.current) setResults([]);
      })
      .finally(() => {
        if (sequence === sequenceRef.current) setLoading(false);
      });

    return undefined;
  }, [debounced]);

  // Close on any click outside the component.
  useEffect(() => {
    const onPointerDown = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false);
        setActive(-1);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, []);

  const showPanel = open && query.trim().length >= 2;

  const submit = (term) => {
    const value = (term ?? query).trim();
    if (!value) return;
    trackSearch(value);
    setOpen(false);
    setActive(-1);
    inputRef.current?.blur();
    if (onNavigate) onNavigate();
    navigate(`/products?search=${encodeURIComponent(value)}&page=0`);
  };

  const openProduct = (product) => {
    trackSearch(query.trim());
    setOpen(false);
    setActive(-1);
    inputRef.current?.blur();
    if (onNavigate) onNavigate();
    navigate(`/products/${product.id}`);
  };

  const onKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActive((index) => (results.length === 0 ? -1 : (index + 1) % results.length));
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((index) => (results.length === 0 ? -1 : (index - 1 + results.length) % results.length));
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      if (active >= 0 && results[active]) openProduct(results[active]);
      else submit();
      return;
    }
    if (event.key === 'Escape') {
      setOpen(false);
      setActive(-1);
    }
  };

  const activeId = active >= 0 && results[active] ? `xx-search-option-${results[active].id}` : undefined;

  const emptyState = useMemo(
    () => !loading && results.length === 0 && debounced.trim().length >= 2,
    [loading, results.length, debounced]
  );

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <div
        className={`relative flex items-center overflow-hidden rounded-full border transition-all duration-xx ease-xx ${
          focused
            ? 'border-[rgba(34,232,245,0.55)] bg-[rgba(9,11,28,0.72)] shadow-[0_0_34px_-8px_rgba(34,232,245,0.65)]'
            : 'border-[rgba(255,255,255,0.13)] bg-[rgba(255,255,255,0.05)]'
        }`}
      >
        <span aria-hidden="true" className="pointer-events-none absolute left-4 z-10">
          <GeoIcon name="search" className="h-4 w-4" accent={focused ? 'var(--xx-cyan)' : 'var(--xx-ink-dim)'} />
        </span>

        <input
          ref={inputRef}
          type="search"
          role="combobox"
          aria-expanded={showPanel}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeId}
          aria-label="Caută produse"
          placeholder={compact ? 'Caută…' : 'Caută produse, branduri, categorii…'}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setOpen(true);
          }}
          onFocus={() => {
            setFocused(true);
            setOpen(true);
          }}
          onBlur={() => setFocused(false)}
          onKeyDown={onKeyDown}
          className={`w-full bg-transparent py-2.5 pl-11 pr-11 text-sm text-[color:var(--xx-ink)] placeholder:text-[color:var(--xx-ink-dim)] focus:outline-none ${
            compact ? '' : 'sm:py-3'
          }`}
        />

        {query ? (
          <button
            type="button"
            onClick={() => {
              setQuery('');
              setResults([]);
              inputRef.current?.focus();
            }}
            aria-label="Golește căutarea"
            className="absolute right-3 z-10 grid h-6 w-6 place-items-center rounded-full text-[color:var(--xx-ink-dim)] transition-colors duration-xx hover:bg-white/10 hover:text-white"
          >
            <GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />
          </button>
        ) : null}

        {/* Focus scan — one cyan sweep across the field. */}
        {focused ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-y-0 left-0 w-1/3 animate-xx-scan-x bg-gradient-to-r from-transparent via-[rgba(34,232,245,0.22)] to-transparent"
          />
        ) : null}
      </div>

      {showPanel ? (
        <div className="absolute left-0 right-0 top-[calc(100%+0.6rem)] z-50 overflow-hidden rounded-2xl border border-[rgba(255,255,255,0.14)] bg-[rgba(7,8,24,0.94)] shadow-glass-lg backdrop-blur-glass-xl animate-xx-materialize">
          {loading ? (
            <div className="space-y-2 p-3">
              {[0, 1, 2].map((index) => (
                <div key={index} className="xx-scanning h-12 rounded-xl bg-[rgba(255,255,255,0.05)]" />
              ))}
            </div>
          ) : emptyState ? (
            <p className="px-4 py-6 text-center text-sm xx-ink-dim">
              Niciun rezultat pentru „{debounced.trim()}”.
            </p>
          ) : (
            <ul id={listId} role="listbox" aria-label="Sugestii de produse" className="max-h-[22rem] overflow-y-auto p-2">
              {results.map((product, index) => (
                <li key={product.id} id={`xx-search-option-${product.id}`} role="option" aria-selected={index === active}>
                  <button
                    type="button"
                    onMouseEnter={() => setActive(index)}
                    onClick={() => openProduct(product)}
                    className={`flex w-full items-center gap-3 rounded-xl p-2 text-left transition-colors duration-xxfast ${
                      index === active ? 'bg-[rgba(46,123,255,0.16)]' : 'hover:bg-white/5'
                    }`}
                  >
                    <img
                      src={resolveImage(product.imageThumbUrl || product.imageUrl)}
                      alt=""
                      loading="lazy"
                      className="h-10 w-10 shrink-0 rounded-lg object-cover"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-[color:var(--xx-ink)]">
                        {product.name}
                      </span>
                      <span className="block truncate text-xs xx-ink-dim">
                        {product.brand}
                        {product.category ? ` · ${product.category}` : ''}
                      </span>
                    </span>
                    <span className="shrink-0 text-sm font-bold text-[color:var(--xx-cyan)]">
                      {formatPrice(product.price)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            onClick={() => submit()}
            className="flex w-full items-center justify-center gap-2 border-t border-[rgba(255,255,255,0.1)] px-4 py-3 text-sm font-semibold text-[color:var(--xx-cyan)] transition-colors duration-xx hover:bg-[rgba(34,232,245,0.1)]"
          >
            <GeoIcon name="zoom" className="h-4 w-4" accent="currentColor" />
            Vezi toate rezultatele pentru „{query.trim()}”
          </button>
        </div>
      ) : null}
    </div>
  );
}
