import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import productService from '../api/productService';
import ProductCard from '../components/ProductCard';
import Spinner from '../components/Spinner';
import { useSeo } from '../utils/seo';

export default function Home() {
  const [featured, setFeatured] = useState([]);
  const [loading, setLoading] = useState(true);

  useSeo({
    description:
      'ElectroShop – magazin online de electronice: telefoane, laptopuri, audio și accesorii la prețuri bune, cu livrare rapidă.',
    path: '/',
  });

  useEffect(() => {
    productService
      .list({ page: 0, size: 4, sortBy: 'price', direction: 'desc' })
      .then((data) => setFeatured(data.content))
      .catch(() => setFeatured([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-12">
      {/* Hero */}
      <section className="overflow-hidden rounded-2xl bg-gradient-to-r from-brand-700 to-brand-500 px-6 py-14 text-white sm:px-12">
        <div className="max-w-2xl">
          <h1 className="text-3xl font-bold sm:text-5xl">
            Tehnologie de top, la prețuri corecte
          </h1>
          <p className="mt-4 text-brand-50">
            Descoperă cele mai noi telefoane, laptopuri, produse audio și accesorii. Livrare rapidă
            și garanție inclusă.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/products" className="btn bg-white text-brand-700 hover:bg-brand-50">
              Vezi produsele
            </Link>
            <Link to="/register" className="btn border border-white/60 text-white hover:bg-white/10">
              Creează cont
            </Link>
          </div>
        </div>
      </section>

      {/* Categories */}
      <section>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { icon: '📱', name: 'Smartphones' },
            { icon: '💻', name: 'Laptops' },
            { icon: '🎧', name: 'Audio' },
            { icon: '📷', name: 'Cameras' },
          ].map((c) => (
            <Link
              key={c.name}
              to={`/products?category=${encodeURIComponent(c.name)}`}
              className="card flex flex-col items-center gap-2 p-6 transition hover:shadow-md"
            >
              <span className="text-4xl">{c.icon}</span>
              <span className="font-medium text-slate-700">{c.name}</span>
            </Link>
          ))}
        </div>
      </section>

      {/* Featured */}
      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-2xl font-bold text-slate-800">Produse recomandate</h2>
          <Link to="/products" className="text-sm font-medium text-brand-600 hover:underline">
            Vezi toate →
          </Link>
        </div>
        {loading ? (
          <Spinner />
        ) : (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {featured.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
