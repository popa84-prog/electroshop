import { Link } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { formatPrice, resolveImage } from '../utils/format';

export default function ProductCard({ product }) {
  const { addItem } = useCart();
  const outOfStock = product.stockQuantity <= 0;

  return (
    <div className="card flex flex-col overflow-hidden transition hover:shadow-md">
      <Link to={`/products/${product.id}`} className="block">
        <div className="aspect-[4/3] w-full overflow-hidden bg-slate-100">
          <img
            src={resolveImage(product.imageUrl)}
            alt={product.name}
            loading="lazy"
            className="h-full w-full object-cover transition hover:scale-105"
          />
        </div>
      </Link>
      <div className="flex flex-1 flex-col p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-brand-600">
          {product.brand}
        </span>
        <Link to={`/products/${product.id}`}>
          <h3 className="mt-1 line-clamp-2 font-semibold text-slate-800 hover:text-brand-600">
            {product.name}
          </h3>
        </Link>
        <div className="mt-auto flex items-center justify-between pt-4">
          <span className="text-lg font-bold text-slate-900">{formatPrice(product.price)}</span>
          <button
            className="btn-primary"
            disabled={outOfStock}
            onClick={() => addItem(product, 1)}
          >
            {outOfStock ? 'Stoc epuizat' : 'Adaugă'}
          </button>
        </div>
      </div>
    </div>
  );
}
