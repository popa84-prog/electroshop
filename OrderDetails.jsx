import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import productService from '../api/productService';
import ProductCard from '../components/ProductCard';
import Pagination from '../components/Pagination';
import Spinner from '../components/Spinner';

export default function Products() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  const page = Number(searchParams.get('page') || 0);
  const category = searchParams.get('category') || '';
  const search = searchParams.get('search') || '';
  const [searchInput, setSearchInput] = useState(search);

  useEffect(() => {
    productService.categories().then(setCategories).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    productService
      .list({ page, size: 12, search, category })
      .then((data) => {
        setProducts(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  }, [page, search, category]);

  const updateParam = (updates) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([k, v]) => {
      if (v === '' || v == null) next.delete(k);
      else next.set(k, v);
    });
    setSearchParams(next);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    updateParam({ search: searchInput, page: 0 });
  };

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Produse</h1>

      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center">
        <form onSubmit={handleSearch} className="flex flex-1 gap-2">
          <input
            className="input"
            placeholder="Caută după nume sau brand..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          <button type="submit" className="btn-primary">
            Caută
          </button>
        </form>

        <select
          className="input sm:w-56"
          value={category}
          onChange={(e) => updateParam({ category: e.target.value, page: 0 })}
        >
          <option value="">Toate categoriile</option>
          {categories.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <Spinner />
      ) : products.length === 0 ? (
        <p className="py-16 text-center text-slate-500">Niciun produs găsit.</p>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {products.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={(p) => updateParam({ page: p })} />
        </>
      )}
    </div>
  );
}
