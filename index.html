import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import productService from '../api/productService';
import { useCart } from '../context/CartContext';
import { formatPrice, resolveImage } from '../utils/format';
import Spinner from '../components/Spinner';

export default function ProductDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addItem } = useCart();
  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    productService
      .getById(id)
      .then(setProduct)
      .catch(() => setError('Produsul nu a fost găsit.'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Spinner />;
  if (error) return <p className="py-16 text-center text-slate-500">{error}</p>;

  const outOfStock = product.stockQuantity <= 0;

  const handleAddToCart = () => addItem(product, quantity);
  const handleBuyNow = () => {
    addItem(product, quantity);
    navigate('/cart');
  };

  return (
    <div>
      <Link to="/products" className="text-sm text-brand-600 hover:underline">
        ← Înapoi la produse
      </Link>

      <div className="mt-4 grid gap-8 lg:grid-cols-2">
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white">
          <img
            src={resolveImage(product.imageUrl)}
            alt={product.name}
            className="h-full max-h-[480px] w-full object-cover"
          />
        </div>

        <div className="flex flex-col">
          <span className="text-sm font-medium uppercase tracking-wide text-brand-600">
            {product.brand} · {product.category}
          </span>
          <h1 className="mt-2 text-3xl font-bold text-slate-900">{product.name}</h1>
          <p className="mt-4 whitespace-pre-line text-slate-600">{product.description}</p>

          <div className="mt-6 text-3xl font-bold text-slate-900">{formatPrice(product.price)}</div>

          <div className="mt-2">
            {outOfStock ? (
              <span className="badge bg-red-100 text-red-800">Stoc epuizat</span>
            ) : (
              <span className="badge bg-green-100 text-green-800">
                În stoc: {product.stockQuantity} buc.
              </span>
            )}
          </div>

          {!outOfStock && (
            <div className="mt-6 flex items-center gap-3">
              <label className="text-sm text-slate-600">Cantitate:</label>
              <input
                type="number"
                min={1}
                max={product.stockQuantity}
                value={quantity}
                onChange={(e) =>
                  setQuantity(Math.max(1, Math.min(product.stockQuantity, Number(e.target.value))))
                }
                className="input w-24"
              />
            </div>
          )}

          <div className="mt-6 flex flex-wrap gap-3">
            <button className="btn-secondary" disabled={outOfStock} onClick={handleAddToCart}>
              Adaugă în coș
            </button>
            <button className="btn-primary" disabled={outOfStock} onClick={handleBuyNow}>
              Cumpără acum
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
