import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import productService from '../../api/productService';
import { useCart } from '../../context/CartContext';
import { formatPrice, resolveImage } from '../../utils/format';
import { buildProfile, recommend } from '../../utils/recommendations';
import GeoIcon from './GeoIcon';
import HoloCarousel from './HoloCarousel';
import NeonBadge from './NeonBadge';
import NeonButton from './NeonButton';
import Reveal from './Reveal';
import SectionHeader from './SectionHeader';
import TiltCard from './TiltCard';
import { HoloGridSkeleton } from './HoloLoader';

/**
 * XXII — TASK 7 (Predictive Shopping: AI Picks, scanning animation,
 * intelligent carousel, contextual side panel).
 *
 * The module has three parts:
 *
 *   1. a candidate pool fetched from the catalogue — deliberately wider than
 *      what is shown, because ranking a pool of four produces the same four;
 *   2. the client-side scoring engine in `utils/recommendations.js`;
 *   3. this presentation layer.
 *
 * Two deliberate choices about honesty:
 *
 *   - Each card states *why* it was picked ("Completează coșul tău",
 *     "Din categoria ta recentă"). An opaque "AI Recommended" badge with no
 *     reason is a black box; a stated reason is a feature the user can trust
 *     or dismiss.
 *   - The scan animation runs once, while the ranking is being computed, and
 *     then stops. A permanent scanning effect would claim the system is
 *     continuously analysing when it is not.
 *
 * `variant` switches the presentation without changing the ranking:
 *   'carousel' — the storefront module (TASK 7 desktop)
 *   'grid'     — a plain responsive grid
 *   'panel'    — the compact contextual side panel (TASK 7 AI side panel)
 */

const SCAN_MS = 900;

function PickCard({ entry, compact = false, fixedWidth = false }) {
  const { product, reason } = entry;
  const { addItem } = useCart();
  const outOfStock = product.stockQuantity <= 0;

  if (compact) {
    return (
      <div className="group flex items-center gap-3 rounded-xl border border-[rgba(255,255,255,0.09)] bg-[rgba(255,255,255,0.035)] p-2.5 transition-all duration-xx ease-xx hover:border-[rgba(122,60,255,0.45)] hover:bg-[rgba(122,60,255,0.08)]">
        <Link to={`/products/${product.id}`} className="shrink-0">
          <img
            src={resolveImage(product.imageThumbUrl || product.imageUrl)}
            alt={product.name}
            loading="lazy"
            className="h-14 w-14 rounded-lg object-cover"
          />
        </Link>
        <div className="min-w-0 flex-1">
          <Link
            to={`/products/${product.id}`}
            className="line-clamp-2 text-sm font-semibold text-[color:var(--xx-ink)] transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
          >
            {product.name}
          </Link>
          <p className="mt-0.5 text-xs xx-ink-dim">{reason}</p>
        </div>
        <span className="shrink-0 text-sm font-bold text-[color:var(--xx-ink)]">{formatPrice(product.price)}</span>
      </div>
    );
  }

  return (
    // A carousel rail needs an intrinsic card width (flex children with no
    // width collapse); a grid cell supplies its own, so the width is opt-in.
    <TiltCard max={5} className={`h-full ${fixedWidth ? 'w-[268px] sm:w-[300px]' : 'w-full'}`}>
      <article className="card relative flex h-full flex-col overflow-hidden">
        {/* The violet glow from the brief — applied to the AI module only, so it
            visually separates from ordinary product cards. */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 rounded-[inherit] opacity-70"
          style={{ boxShadow: 'inset 0 0 60px -18px rgba(122,60,255,0.65)' }}
        />

        <div className="absolute left-3 top-3 z-20">
          <NeonBadge tone="magenta" icon={<GeoIcon name="sparkle" className="h-3.5 w-3.5" accent="currentColor" />}>
            AI Recommended
          </NeonBadge>
        </div>

        <Link to={`/products/${product.id}`} className="relative block overflow-hidden">
          <div className="aspect-[4/3] w-full overflow-hidden bg-[rgba(255,255,255,0.04)]">
            <img
              src={resolveImage(product.imageThumbUrl || product.imageUrl)}
              alt={product.name}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-500 ease-xx hover:scale-[1.07]"
            />
          </div>
        </Link>

        <div className="relative z-10 flex flex-1 flex-col p-4">
          <p className="flex items-center gap-1.5 text-[0.68rem] font-semibold uppercase tracking-[0.14em] text-[#c9a4ff]">
            <GeoIcon name="pulse" className="h-3.5 w-3.5" accent="currentColor" />
            {reason}
          </p>

          <Link to={`/products/${product.id}`}>
            <h3 className="mt-1.5 line-clamp-2 font-semibold text-[color:var(--xx-ink)] transition-colors duration-xx hover:text-[color:var(--xx-cyan)]">
              {product.name}
            </h3>
          </Link>

          <div className="mt-auto flex items-center justify-between gap-2 pt-4">
            <span className="font-display text-lg font-bold text-[color:var(--xx-ink)]">
              {formatPrice(product.price)}
            </span>
            <NeonButton
              variant="secondary"
              size="sm"
              disabled={outOfStock}
              onClick={() => addItem(product, 1)}
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

export default function AIPicks({
  variant = 'carousel',
  limit = 8,
  exclude = [],
  category = null,
  title = 'AI Picks',
  eyebrow = 'Predictive Shopping',
  subtitle = 'Selecție generată în timp real din categoriile pe care le explorezi, din coșul tău și din cele mai bine cotate produse.',
  className = '',
  products: providedProducts = null,
}) {
  const { items: cartItems } = useCart();
  const [pool, setPool] = useState(providedProducts || []);
  const [loading, setLoading] = useState(!providedProducts);
  const [scanning, setScanning] = useState(true);
  const [failed, setFailed] = useState(false);

  // Candidate pool. Fetched once per category; ranking then happens locally on
  // every cart change without another network round-trip.
  useEffect(() => {
    if (providedProducts) {
      setPool(providedProducts);
      setLoading(false);
      return undefined;
    }

    let cancelled = false;
    setLoading(true);
    setFailed(false);

    productService
      .list({ page: 0, size: 40, sortBy: 'id', direction: 'desc', ...(category ? { category } : {}) })
      .then((data) => {
        if (cancelled) return;
        setPool(Array.isArray(data) ? data : data?.content || []);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [category, providedProducts]);

  // The scan runs once, while the ranking settles, then stops.
  useEffect(() => {
    if (loading) return undefined;
    setScanning(true);
    const timer = window.setTimeout(() => setScanning(false), SCAN_MS);
    return () => window.clearTimeout(timer);
  }, [loading, pool.length]);

  const picks = useMemo(() => {
    if (pool.length === 0) return [];
    const profile = buildProfile();
    return recommend(pool, { cartItems, exclude, limit, profile });
  }, [pool, cartItems, limit, exclude]);

  // A module with nothing to show removes itself rather than rendering an empty
  // shell — an "AI" section with zero results reads as broken.
  if (failed || (!loading && picks.length === 0)) return null;

  /* ---------------- panel variant ---------------- */
  if (variant === 'panel') {
    return (
      <aside className={`card p-4 ${scanning ? 'xx-scanning' : ''} ${className}`}>
        <div className="mb-3 flex items-center gap-2">
          <span
            aria-hidden="true"
            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(122,60,255,0.4)] bg-[rgba(122,60,255,0.14)]"
          >
            <GeoIcon name="cpu" className="h-4 w-4" accent="var(--xx-purple)" />
          </span>
          <div>
            <p className="xx-eyebrow mb-0">{eyebrow}</p>
            <h3 className="font-display text-base font-bold text-[color:var(--xx-ink)]">{title}</h3>
          </div>
        </div>

        {loading ? (
          <div className="space-y-2.5">
            {[0, 1, 2].map((index) => (
              <div key={index} className="xx-scanning h-16 rounded-xl bg-[rgba(255,255,255,0.05)]" />
            ))}
          </div>
        ) : (
          <div className="space-y-2.5">
            {picks.slice(0, 4).map((entry) => (
              <PickCard key={entry.product.id} entry={entry} compact />
            ))}
          </div>
        )}
      </aside>
    );
  }

  /* ---------------- grid / carousel variants ---------------- */
  return (
    <section className={className}>
      <SectionHeader
        eyebrow={eyebrow}
        title={
          <span className="inline-flex items-center gap-3">
            <span
              aria-hidden="true"
              className="grid h-10 w-10 place-items-center rounded-xl border border-[rgba(122,60,255,0.45)] bg-[rgba(122,60,255,0.14)] shadow-glow-purple"
            >
              <GeoIcon name="cpu" className="h-5 w-5" accent="var(--xx-purple)" />
            </span>
            <span className="xx-text-gradient-hot">{title}</span>
          </span>
        }
        subtitle={subtitle}
        actionTo="/products"
        actionLabel="Vezi tot catalogul"
      />

      {loading ? (
        <HoloGridSkeleton count={4} />
      ) : (
        <div className={scanning ? 'xx-scanning rounded-[1.25rem]' : ''}>
          {variant === 'grid' ? (
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {picks.map((entry, index) => (
                <Reveal key={entry.product.id} delay={index * 70}>
                  <PickCard entry={entry} />
                </Reveal>
              ))}
            </div>
          ) : (
            <HoloCarousel label="Recomandări AI">
              {picks.map((entry) => (
                <PickCard key={entry.product.id} entry={entry} fixedWidth />
              ))}
            </HoloCarousel>
          )}
        </div>
      )}
    </section>
  );
}
