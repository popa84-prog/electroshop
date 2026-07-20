import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import productService from '../api/productService';
import ProductCard from '../components/ProductCard';
import Pagination from '../components/Pagination';
import Spinner from '../components/Spinner';

export default function Products() {
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
        size: 12,
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
      })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  }, [page, search, category, subcategory, brand, minPrice, maxPrice, inStock, sort]);

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

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Produse</h1>

      <div className="grid gap-6 lg:grid-cols-[240px_1fr]">
        {/* Filter sidebar */}
        <aside className="space-y-5">
          <form onSubmit={handleSearch} className="flex gap-2">
            <input
              className="input"
              placeholder="Caută..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
            <button type="submit" className="btn-primary px-3">
              🔍
            </button>
          </form>

          <div>
            <label className="mb-1 block text-sm font-semibold text-slate-700">Categorie</label>
            <select
              className="input"
              value={category}
              onChange={(e) => updateParam({ category: e.target.value, subcategory: '', page: 0 })}
            >
              <option value="">Toate categoriile</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>

          {subcategories.length > 0 && (
            <div>
              <label className="mb-1 block text-sm font-semibold text-slate-700">Subcategorie</label>
              <select
                className="input"
                value={subcategory}
                onChange={(e) => updateParam({ subcategory: e.target.value, page: 0 })}
              >
                <option value="">Toate</option>
                {subcategories.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label className="mb-1 block text-sm font-semibold text-slate-700">Brand</label>
            <select
              className="input"
              value={brand}
              onChange={(e) => updateParam({ brand: e.target.value, page: 0 })}
            >
              <option value="">Toate brandurile</option>
              {brands.map((b) => (
                <option key={b} value={b}>
                  {b}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm font-semibold text-slate-700">Preț (RON)</label>
            <div className="flex items-center gap-2">
              <input
                type="number"
                className="input"
                placeholder="min"
                value={minInput}
                onChange={(e) => setMinInput(e.target.value)}
              />
              <span className="text-slate-400">–</span>
              <input
                type="number"
                className="input"
                placeholder="max"
                value={maxInput}
                onChange={(e) => setMaxInput(e.target.value)}
              />
            </div>
            <button className="btn-secondary mt-2 w-full text-sm" onClick={applyPrice}>
              Aplică prețul
            </button>
          </div>

          <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={inStock}
              onChange={(e) => updateParam({ inStock: e.target.checked, page: 0 })}
            />
            Doar produse în stoc
          </label>

          <div>
            <label className="mb-1 block text-sm font-semibold text-slate-700">Sortează</label>
            <select
              className="input"
              value={sort}
              onChange={(e) => updateParam({ sort: e.target.value, page: 0 })}
            >
              <option value="createdAt:desc">Cele mai noi</option>
              <option value="price:asc">Preț crescător</option>
              <option value="price:desc">Preț descrescător</option>
              <option value="name:asc">Nume (A–Z)</option>
            </select>
          </div>

          {activeFilters && (
            <button onClick={clearFilters} className="text-sm text-brand-600 hover:underline">
              ✕ Șterge filtrele
            </button>
          )}
        </aside>

        {/* Results */}
        <div>
          {loading ? (
            <Spinner />
          ) : products.length === 0 ? (
            <p className="py-16 text-center text-slate-500">Niciun produs găsit.</p>
          ) : (
            <>
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                {products.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>
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
