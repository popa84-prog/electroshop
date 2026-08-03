import { memo } from 'react';
import { Link } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { formatPrice, resolveImage } from '../utils/format';
import { trackAddToCart } from '../utils/recommendations';
import GeoIcon from './xxii/GeoIcon';
import NeonBadge from './xxii/NeonBadge';
import NeonButton from './xxii/NeonButton';
import TiltCard from './xxii/TiltCard';

/**
 * XXII — TASK 1 / TASK 6 / TASK 8 (3D minimalist product card: hover tilt +
 * glow, neon accents).
 *
 * Still memoized: a grid of 12–96 cards only re-renders the cards whose own
 * `product`/`layout` props actually changed, so filtering the list or updating
 * the cart badge elsewhere no longer re-renders every card. The tilt is
 * pointer-driven inside `TiltCard` and does not touch this component's state,
 * so the memo boundary survives the added interactivity.
 *
 * Stock is communicated twice — by a badge with an icon *and* by text — so the
 * state never depends on colour alone.
 *
 * Adding to the cart also feeds the TASK 7 behaviour log. That call lives here
 * rather than inside `CartContext` because the cart stores a trimmed line item
 * without a category, and the recommender needs the category.
 */

function StockBadge({ stock }) {
  if (stock <= 0) {
    return (
      <NeonBadge tone="critical" icon={<GeoIcon name="close" className="h-3 w-3" accent="currentColor" />}>
        Stoc epuizat
      </NeonBadge>
    );
  }
  if (stock < 5) {
    return (
      <NeonBadge tone="warning" icon={<GeoIcon name="alert" className="h-3 w-3" accent="currentColor" />}>
        Ultimele {stock}
      </NeonBadge>
    );
  }
  return (
    <NeonBadge tone="good" icon={<GeoIcon name="check" className="h-3 w-3" accent="currentColor" />}>
      În stoc
    </NeonBadge>
  );
}

function ProductCard({ product, layout = 'grid' }) {
  const { addItem } = useCart();
  const outOfStock = product.stockQuantity <= 0;

  const handleAdd = () => {
    addItem(product, 1);
    trackAddToCart(product);
  };

  if (layout === 'list') {
    return (
      <article className="card group flex items-center gap-4 overflow-hidden p-3">
        <Link to={`/products/${product.id}`} className="block shrink-0" aria-label={product.name}>
          <div className="h-24 w-24 overflow-hidden rounded-xl bg-[rgba(255,255,255,0.05)] sm:h-28 sm:w-28">
            <img
              src={resolveImage(product.imageThumbUrl || product.imageUrl)}
              alt={product.name}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-500 ease-xx group-hover:scale-105"
            />
          </div>
        </Link>

        <div className="flex min-w-0 flex-1 flex-col">
          <span className="text-[0.68rem] font-semibold uppercase tracking-[0.14em] text-[#7fb0ff]">
            {product.brand}
            {product.category ? <span className="xx-ink-dim"> · {product.category}</span> : null}
          </span>

          <Link to={`/products/${product.id}`}>
            <h3 className="mt-0.5 line-clamp-2 font-semibold text-[color:var(--xx-ink)] transition-colors duration-xx ease-xx hover:text-[color:var(--xx-cyan)]">
              {product.name}
            </h3>
          </Link>

          <span className="mt-2">
            <StockBadge stock={product.stockQuantity} />
          </span>
        </div>

        <div className="flex shrink-0 flex-col items-end gap-2">
          <span className="font-display text-lg font-bold text-[color:var(--xx-ink)]">
            {formatPrice(product.price)}
          </span>
          <NeonButton
            size="sm"
            disabled={outOfStock}
            onClick={handleAdd}
            icon={<GeoIcon name="cart" className="h-4 w-4" accent="currentColor" />}
          >
            {outOfStock ? 'Indisponibil' : 'Adaugă'}
          </NeonButton>
        </div>
      </article>
    );
  }

  return (
    <TiltCard max={4} className="h-full">
      <article className="card group flex h-full flex-col overflow-hidden">
        <Link to={`/products/${product.id}`} className="relative block" aria-label={product.name}>
          <div className="aspect-[4/3] w-full overflow-hidden bg-[rgba(255,255,255,0.04)]">
            <img
              src={resolveImage(product.imageThumbUrl || product.imageUrl)}
              alt={product.name}
              loading="lazy"
              className={`h-full w-full object-cover transition-transform duration-500 ease-xx group-hover:scale-[1.07] ${
                outOfStock ? 'opacity-45 grayscale' : ''
              }`}
            />
          </div>

          {/* A soft floor of light under the image so the card body does not
              start with a hard horizontal cut. */}
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-[rgba(4,5,12,0.75)] to-transparent"
          />

          <span className="absolute left-3 top-3">
            <StockBadge stock={product.stockQuantity} />
          </span>
        </Link>

        <div className="flex flex-1 flex-col p-4">
          <span className="text-[0.68rem] font-semibold uppercase tracking-[0.14em] text-[#7fb0ff]">
            {product.brand}
          </span>

          <Link to={`/products/${product.id}`}>
            <h3 className="mt-1 line-clamp-2 font-semibold text-[color:var(--xx-ink)] transition-colors duration-xx ease-xx hover:text-[color:var(--xx-cyan)]">
              {product.name}
            </h3>
          </Link>

          <div className="mt-auto flex items-center justify-between gap-2 pt-4">
            <span className="font-display text-lg font-bold text-[color:var(--xx-ink)]">
              {formatPrice(product.price)}
            </span>
            <NeonButton
              size="sm"
              disabled={outOfStock}
              onClick={handleAdd}
              icon={<GeoIcon name="cart" className="h-4 w-4" accent="currentColor" />}
            >
              {outOfStock ? 'Epuizat' : 'Adaugă'}
            </NeonButton>
          </div>
        </div>
      </article>
    </TiltCard>
  );
}

export default memo(ProductCard);
