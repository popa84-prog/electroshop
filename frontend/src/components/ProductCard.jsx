import { Link } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { formatPrice, resolveImage } from '../utils/format';

export default function ProductCard({ product, layout = 'grid' }) {
  const { addItem } = useCart();
  const outOfStock = product.stockQuantity <= 0;

  if (layout === 'list') {
    return (
      <div className="card flex items-center gap-4 overflow-hidden p-3 transition hover:shadow-md">
        <Link to={`/products/${product.id}`} className="block shrink-0">
          <div className="h-24 w-24 overflow-hidden rounded-lg bg-champagne-100 sm:h-28 sm:w-28">
            <img
              src={resolveImage(product.imageUrl)}
              alt={product.name}
              loading="lazy"
              className="h-full w-full object-cover"
            />
          </div>
        </Link>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="text-xs font-medium uppercase tracking-wide text-brand-600">
            {product.brand}
            {product.category ? <span className="text-graphite-400"> · {product.category}</span> : null}
          </span>
          <Link to={`/products/${product.id}`}>
            <h3 className="mt-0.5 line-clamp-2 font-semibold text-graphite-800 hover:text-brand-600">
              {product.name}
            </h3>
          </Link>
          <span className="mt-1 text-xs text-graphite-400">
            {outOfStock ? 'Stoc epuizat' : `În stoc: ${product.stockQuantity}`}
          </span>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-2">
          <span className="text-lg font-bold text-graphite-900">{formatPrice(product.price)}</span>
          <button className="btn-primary" disabled={outOfStock} onClick={() => addItem(product, 1)}>
            {outOfStock ? 'Indisponibil' : 'Adaugă'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="card flex flex-col overflow-hidden transition hover:shadow-md">
      <Link to={`/products/${product.id}`} className="block">
        <div className="aspect-[4/3] w-full overflow-hidden bg-champagne-100">
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
          <h3 className="mt-1 line-clamp-2 font-semibold text-graphite-800 hover:text-brand-600">
            {product.name}
          </h3>
        </Link>
        <div className="mt-auto flex items-center justify-between pt-4">
          <span className="text-lg font-bold text-graphite-900">{formatPrice(product.price)}</span>
          <button className="btn-primary" disabled={outOfStock} onClick={() => addItem(product, 1)}>
            {outOfStock ? 'Stoc epuizat' : 'Adaugă'}
          </button>
        </div>
      </div>
    </div>
  );
}
