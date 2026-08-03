import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import productService from '../api/productService';
import ProductCard from '../components/ProductCard';
import Pagination from '../components/Pagination';
import { useSeo } from '../utils/seo';
import {
  GeoIcon,
  HoloGridSkeleton,
  HoloInput,
  NeonBadge,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../components/xxii';

/**
 * XXII — TASK 1 / TASK 8 / TASK 9 (Modular Grid: catalogul).
 *
 * Pagina păstrează exact aceeași logică de date ca înainte — starea trăiește în
 * URL prin `useSearchParams`, deci un filtru aplicat rămâne valid la refresh și
 * poate fi trimis ca link. Nimic din `updateParam`, `handleSearch`, `applyPrice`,
 * `clearFilters` sau din cele două efecte nu s-a schimbat.
 *
 * Ce s-a schimbat, și de ce:
 *
 *   1. **Filtrele active devin vizibile.** Înainte, singurul semn că un filtru
 *      era pornit se afla în bara laterală — pe telefon, unde bara ajunge sub
 *      rezultate, un utilizator putea vedea „3 produse” fără să înțeleagă de ce.
 *      Banda de etichete de sub titlu arată fiecare filtru activ și îl poate
 *      stinge individual, nu doar pe toate deodată.
 *   2. **Comutatorul grilă/listă poartă `aria-pressed`.** Erau două butoane cu
 *      caracterele „▦” și „☰”, iar starea apăsată se citea doar din culoare.
 *      Acum starea este anunțată, iar pictogramele sunt `GeoIcon`, identice pe
 *      orice sistem de operare.
 *   3. **Așteptarea are forma rezultatului.** `Spinner` era un cerc într-un gol;
 *      `HoloGridSkeleton` desenează exact atâtea plăci câte produse urmează, deci
 *      pagina nu sare când sosesc datele.
 *   4. **Numărul de rezultate este `aria-live`.** Filtrarea schimbă conținutul
 *      fără navigare; fără asta, un cititor de ecran nu afla niciodată că lista
 *      s-a schimbat.
 *
 * Vocabularul vechi — `text-slate-800`, `text-slate-700`, `text-brand-600`,
 * perechea `bg-brand-600 text-white` / `bg-white text-graphite-600` — a dispărut
 * complet. Perechea comutatorului era singurul loc din pagină cu fundal alb opac
 * pe suprafața întunecată.
 */

/** Sortările acceptate de backend, în ordinea în care sunt oferite. */
const SORT_OPTIONS = [
  { value: 'createdAt:desc', label: 'Cele mai noi' },
  { value: 'price:asc', label: 'Preț crescător' },
  { value: 'price:desc', label: 'Preț descrescător' },
  { value: 'name:asc', label: 'Nume (A–Z)' },
];

/** Numărul de produse pe pagină. */
const PAGE_SIZES = [12, 24, 48, 96];

/**
 * O etichetă de filtru activ. Este un buton, nu un `NeonBadge` decorativ:
 * întregul dreptunghi stinge filtrul, deci ținta de atingere este toată
 * eticheta, nu doar micul „×” din dreapta.
 */
function FilterChip({ label, value, onClear }) {
  return (
    <button
      type="button"
      onClick={onClear}
      aria-label={`Elimină filtrul ${label}: ${value}`}
      className="group inline-flex items-center gap-1.5 rounded-full border border-[rgba(34,232,245,0.35)] bg-[rgba(34,232,245,0.1)] py-1 pl-3 pr-2 text-xs font-medium text-[#c9d4ff] transition-all duration-200 hover:border-[rgba(255,90,122,0.55)] hover:bg-[rgba(255,90,122,0.12)] hover:text-[#ff8fa8]"
    >
      <span className="uppercase tracking-[0.12em] text-[#22e8f5] transition-colors duration-200 group-hover:text-[#ff8fa8]">
        {label}
      </span>
      <span>{value}</span>
      <GeoIcon name="close" className="h-3 w-3" accent="currentColor" />
    </button>
  );
}

export default function Products() {
  useSeo({
    title: 'Produse',
    description:
      'Descoperă toate produsele ElectroShop: telefoane, laptopuri, audio și accesorii. Filtrează după categorie, brand și preț.',
    path: '/products',
  });
  const [searchParams, setSearchParams] = useSearchParams();
  const [products, setProducts] = useState([]);
  const [tree, setTree] = useState({});
  const [brands, setBrands] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const page = Number(searchParams.get('page') || 0);
  const category = searchParams.get('category') || '';
  const subcategory = searchParams.get('subcategory') || '';
  const brand = searchParams.get('brand') || '';
  const minPrice = searchParams.get('minPrice') || '';
  const maxPrice = searchParams.get('maxPrice') || '';
  const inStock = searchParams.get('inStock') === 'true';
  const sort = searchParams.get('sort') || 'createdAt:desc';
  const search = searchParams.get('search') || '';
  const view = searchParams.get('view') || 'grid';
  const pageSize = Number(searchParams.get('size') || 12);
  const [total, setTotal] = useState(0);

  const [searchInput, setSearchInput] = useState(search);
  const [minInput, setMinInput] = useState(minPrice);
  const [maxInput, setMaxInput] = useState(maxPrice);

  useEffect(() => {
    productService.categoryTree().then(setTree).catch(() => {});
    productService.brands().then(setBrands).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    const [sortBy, direction] = sort.split(':');
    productService
      .list({
        page,
        size: pageSize,
        search,
        category,
        subcategory,
        brand,
        minPrice: minPrice || undefined,
        maxPrice: maxPrice || undefined,
        inStock: inStock || undefined,
        sortBy,
        direction,
      })
      .then((data) => {
        setProducts(data.content);
        setTotalPages(data.totalPages);
        setTotal(data.totalElements ?? data.content.length);
      })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  }, [page, pageSize, search, category, subcategory, brand, minPrice, maxPrice, inStock, sort]);

  const updateParam = (updates) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([k, v]) => {
      if (v === '' || v == null || v === false) next.delete(k);
      else next.set(k, v);
    });
    setSearchParams(next);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    updateParam({ search: searchInput, page: 0 });
  };

  const applyPrice = () => updateParam({ minPrice: minInput, maxPrice: maxInput, page: 0 });

  const clearFilters = () => {
    setSearchInput('');
    setMinInput('');
    setMaxInput('');
    setSearchParams(new URLSearchParams());
  };

  const categories = Object.keys(tree);
  const subcategories = category ? tree[category] || [] : [];

  const activeFilters =
    category || subcategory || brand || minPrice || maxPrice || inStock || search;

  /**
   * Etichetele filtrelor active. Fiecare știe să se stingă singură — și, acolo
   * unde filtrul are o oglindă în starea locală (căutarea, pragurile de preț),
   * golește și câmpul, altfel bara laterală ar continua să arate o valoare care
   * nu se mai aplică.
   */
  const chips = [];

  if (search) {
    chips.push({
      key: 'search',
      label: 'Căutare',
      value: search,
      onClear: () => {
        setSearchInput('');
        updateParam({ search: '', page: 0 });
      },
    });
  }
  if (category) {
    chips.push({
      key: 'category',
      label: 'Categorie',
      value: category,
      // Subcategoria aparține categoriei: fără ea, ar rămâne un filtru orfan.
      onClear: () => updateParam({ category: '', subcategory: '', page: 0 }),
    });
  }
  if (subcategory) {
    chips.push({
      key: 'subcategory',
      label: 'Subcategorie',
      value: subcategory,
      onClear: () => updateParam({ subcategory: '', page: 0 }),
    });
  }
  if (brand) {
    chips.push({
      key: 'brand',
      label: 'Brand',
      value: brand,
      onClear: () => updateParam({ brand: '', page: 0 }),
    });
  }
  if (minPrice || maxPrice) {
    chips.push({
      key: 'price',
      label: 'Preț',
      value: `${minPrice || '0'} – ${maxPrice || '∞'} RON`,
      onClear: () => {
        setMinInput('');
        setMaxInput('');
        updateParam({ minPrice: '', maxPrice: '', page: 0 });
      },
    });
  }
  if (inStock) {
    chips.push({
      key: 'inStock',
      label: 'Stoc',
      value: 'Doar în stoc',
      onClear: () => updateParam({ inStock: false, page: 0 }),
    });
  }

  /** Un buton al comutatorului de vizualizare, cu starea apăsată anunțată. */
  const viewButton = (mode, iconName, label) => {
    const active = view === mode;
    return (
      <button
        type="button"
        title={`Vizualizare ${label.toLowerCase()}`}
        aria-label={label}
        aria-pressed={active}
        onClick={() => updateParam({ view: mode })}
        className={`flex h-9 w-10 items-center justify-center transition-all duration-200 ${
          active
            ? 'bg-[rgba(34,232,245,0.16)] text-[#22e8f5] shadow-[inset_0_0_18px_-6px_rgba(34,232,245,0.9)]'
            : 'bg-[rgba(255,255,255,0.03)] text-[#c9d4ff] hover:bg-[rgba(255,255,255,0.07)] hover:text-[#e8ecff]'
        }`}
      >
        <GeoIcon name={iconName} className="h-4 w-4" accent="currentColor" />
      </button>
    );
  };

  return (
    <div className="py-2">
      <SectionHeader
        eyebrow="Catalog"
        title="Produse"
        as="h1"
        action={
          <NeonBadge tone="aqua" icon={<GeoIcon name="box" className="h-3.5 w-3.5" accent="currentColor" />}>
            <span aria-live="polite">{loading ? 'Se încarcă…' : `${total} produse`}</span>
          </NeonBadge>
        }
      />

      {chips.length > 0 && (
        <Reveal direction="down" className="mb-5 flex flex-wrap items-center gap-2">
          {chips.map((c) => (
            <FilterChip key={c.key} label={c.label} value={c.value} onClear={c.onClear} />
          ))}
          <button
            type="button"
            onClick={clearFilters}
            className="ml-1 text-xs font-semibold text-[#b795ff] underline-offset-4 transition-colors duration-200 hover:text-[#ff4fd8] hover:underline"
          >
            Șterge toate filtrele
          </button>
        </Reveal>
      )}

      <div className="grid gap-6 lg:grid-cols-[260px_1fr]">
        {/* Bara de filtre */}
        <Reveal direction="right" as="aside" className="lg:sticky lg:top-24 lg:h-fit">
          <div className="card card-static space-y-1 p-5">
            <form onSubmit={handleSearch}>
              <HoloInput
                id="products-search"
                label="Caută"
                placeholder="Nume produs…"
                icon={<GeoIcon name="search" className="h-4 w-4" accent="currentColor" />}
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
              />
              <NeonButton
                type="submit"
                size="sm"
                block
                icon={<GeoIcon name="search" className="h-3.5 w-3.5" accent="currentColor" />}
              >
                Caută
              </NeonButton>
            </form>

            <div className="pt-3">
              <HoloInput
                as="select"
                id="products-category"
                label="Categorie"
                value={category}
                onChange={(e) => updateParam({ category: e.target.value, subcategory: '', page: 0 })}
              >
                <option value="">Toate categoriile</option>
                {categories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </HoloInput>
            </div>

            {subcategories.length > 0 && (
              <HoloInput
                as="select"
                id="products-subcategory"
                label="Subcategorie"
                value={subcategory}
                onChange={(e) => updateParam({ subcategory: e.target.value, page: 0 })}
              >
                <option value="">Toate</option>
                {subcategories.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </HoloInput>
            )}

            <HoloInput
              as="select"
              id="products-brand"
              label="Brand"
              value={brand}
              onChange={(e) => updateParam({ brand: e.target.value, page: 0 })}
            >
              <option value="">Toate brandurile</option>
              {brands.map((b) => (
                <option key={b} value={b}>
                  {b}
                </option>
              ))}
            </HoloInput>

            <div>
              <p className="mb-1.5 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
                Preț (RON)
              </p>
              <div className="flex items-start gap-2">
                <HoloInput
                  id="products-min-price"
                  type="number"
                  min="0"
                  placeholder="min"
                  containerClassName="flex-1"
                  value={minInput}
                  onChange={(e) => setMinInput(e.target.value)}
                />
                <span className="pt-2.5 text-sm xx-ink-dim" aria-hidden="true">
                  –
                </span>
                <HoloInput
                  id="products-max-price"
                  type="number"
                  min="0"
                  placeholder="max"
                  containerClassName="flex-1"
                  value={maxInput}
                  onChange={(e) => setMaxInput(e.target.value)}
                />
              </div>
              <NeonButton
                variant="secondary"
                size="sm"
                block
                onClick={applyPrice}
                icon={<GeoIcon name="check" className="h-3.5 w-3.5" accent="currentColor" />}
              >
                Aplică prețul
              </NeonButton>
            </div>

            <label className="flex cursor-pointer items-center gap-2.5 py-3 text-sm text-[#c9d4ff] transition-colors duration-200 hover:text-[#e8ecff]">
              <input
                type="checkbox"
                checked={inStock}
                onChange={(e) => updateParam({ inStock: e.target.checked, page: 0 })}
                className="h-4 w-4 cursor-pointer rounded border-[rgba(34,232,245,0.4)] bg-[rgba(255,255,255,0.05)] accent-[#22e8f5]"
              />
              Doar produse în stoc
            </label>

            <HoloInput
              as="select"
              id="products-sort"
              label="Sortează"
              value={sort}
              onChange={(e) => updateParam({ sort: e.target.value, page: 0 })}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </HoloInput>

            {activeFilters && (
              <NeonButton
                variant="ghost"
                size="sm"
                block
                onClick={clearFilters}
                icon={<GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />}
              >
                Șterge filtrele
              </NeonButton>
            )}
          </div>
        </Reveal>

        {/* Rezultate */}
        <div>
          {/* Bara de instrumente: produse pe pagină și comutatorul de vizualizare. */}
          <div className="mb-4 flex flex-wrap items-center justify-end gap-3">
            <label
              htmlFor="products-page-size"
              className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim"
            >
              Pe pagină
              <select
                id="products-page-size"
                className="input w-auto py-1.5 text-xs"
                value={pageSize}
                onChange={(e) => updateParam({ size: e.target.value, page: 0 })}
              >
                {PAGE_SIZES.map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>

            <div className="flex overflow-hidden rounded-[0.7rem] border border-[rgba(255,255,255,0.12)]">
              {viewButton('grid', 'grid', 'Grilă')}
              {viewButton('list', 'menu', 'Listă')}
            </div>
          </div>

          {loading ? (
            <HoloGridSkeleton count={pageSize > 12 ? 12 : pageSize} />
          ) : products.length === 0 ? (
            <Reveal direction="scale">
              <div className="card card-static flex flex-col items-center gap-3 py-16 text-center">
                <span className="flex h-16 w-16 items-center justify-center rounded-full border border-[rgba(122,60,255,0.45)] bg-[rgba(122,60,255,0.12)] shadow-[0_0_38px_-10px_rgba(122,60,255,0.8)]">
                  <GeoIcon name="zoom" className="h-7 w-7" accent="#b795ff" />
                </span>

                <p className="text-base font-semibold text-[#e8ecff]">Niciun produs găsit</p>

                <p className="max-w-sm text-sm leading-relaxed xx-ink-muted">
                  {activeFilters
                    ? 'Filtrele active nu se potrivesc cu niciun produs din catalog. Elimină o condiție și încearcă din nou.'
                    : 'Catalogul este momentan gol.'}
                </p>

                {activeFilters && (
                  <NeonButton
                    variant="secondary"
                    size="sm"
                    className="mt-2"
                    onClick={clearFilters}
                    icon={<GeoIcon name="refresh" className="h-3.5 w-3.5" accent="currentColor" />}
                  >
                    Șterge filtrele
                  </NeonButton>
                )}
              </div>
            </Reveal>
          ) : (
            <>
              {view === 'list' ? (
                <div className="flex flex-col gap-3">
                  {products.map((p, i) => (
                    <Reveal key={p.id} delay={Math.min(i, 8) * 45}>
                      <ProductCard product={p} layout="list" />
                    </Reveal>
                  ))}
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                  {products.map((p, i) => (
                    <Reveal key={p.id} delay={Math.min(i, 8) * 45}>
                      <ProductCard product={p} layout="grid" />
                    </Reveal>
                  ))}
                </div>
              )}
              <Pagination
                page={page}
                totalPages={totalPages}
                onChange={(p) => updateParam({ page: p })}
              />
            </>
          )}
        </div>
      </div>
    </div>
  );
}
