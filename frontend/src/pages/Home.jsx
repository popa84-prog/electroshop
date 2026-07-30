import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import productService from '../api/productService';
import ProductCard from '../components/ProductCard';
import Spinner from '../components/Spinner';
import { useSeo } from '../utils/seo';

/**
 * Icons for the category tiles. Keys are compared case-insensitively against
 * the real category names stored on the products, so a category that has no
 * entry here still renders — just with the neutral fallback icon.
 */
const CATEGORY_ICONS = {
  audio: '🎧',
  'foto & video': '📷',
  'auto & moto': '🚗',
  wearables: '⌚',
  'smart home': '🏠',
  tablete: '📱',
  telefoane: '📱',
  monitoare: '🖥️',
  gaming: '🎮',
  laptopuri: '💻',
  'periferice pc': '⌨️',
  'sisteme pc': '🖥️',
  'componente pc': '🧩',
  stocare: '💾',
  'tv & proiectoare': '📺',
  electrocasnice: '🔌',
  accesorii: '🔗',
  'ingrijire personala': '💇',
  'instrumente muzicale': '🎸',
  'scule & unelte': '🛠️',
  retea: '📶',
  sanatate: '❤️',
  folosit: '♻️',
};

const FALLBACK_ICON = '🔌';

function iconFor(name) {
  return CATEGORY_ICONS[String(name).trim().toLowerCase()] || FALLBACK_ICON;
}

export default function Home() {
  const [featured, setFeatured] = useState([]);
  const [categories, setCategories] = useState([]);
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

  // The tiles mirror the four best-stocked categories in the real catalogue,
  // so they never advertise a section that does not exist.
  useEffect(() => {
    productService
      .topCategories(4)
      .then((data) => setCategories(Array.isArray(data) ? data : []))
      .catch(() => setCategories([]));
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
            Descoperă cele mai noi telefoane, laptopuri, produse audio și accesorii.
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

      {/* Categories — the four most populated ones, straight from the catalogue */}
      {categories.length > 0 && (
        <section>
          <h2 className="mb-4 text-2xl font-bold text-slate-800">Categorii populare</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {categories.map((c) => (
              <Link
                key={c.name}
                to={`/products?category=${encodeURIComponent(c.name)}`}
                className="card flex flex-col items-center gap-2 p-6 text-center transition hover:shadow-md"
              >
                <span className="text-4xl">{iconFor(c.name)}</span>
                <span className="font-medium text-slate-700">{c.name}</span>
                <span className="text-xs text-slate-500">
                  {c.productCount} {c.productCount === 1 ? 'produs' : 'produse'}
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

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
