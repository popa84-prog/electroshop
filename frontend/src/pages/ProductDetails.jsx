import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import productService from '../api/productService';
import { useCart } from '../context/CartContext';
import { formatPrice, resolveImage } from '../utils/format';
import { useSeo } from '../utils/seo';
import { trackProductView } from '../utils/recommendations';
import Lightbox from '../components/Lightbox';
import ProductCard from '../components/ProductCard';
import {
  AIPicks,
  GeoIcon,
  Grid12,
  HoloCarousel,
  HoloGallery,
  HoloGridSkeleton,
  HoloLoader,
  HoloReviews,
  MiniCart,
  Module,
  NeonBadge,
  NeonButton,
  SectionHeader,
  Reveal,
} from '../components/xxii';

/**
 * XXII — TASK 3 (Holographic Product View), with TASK 7 and TASK 9 applied.
 *
 * The page is four independent modules on the 12-column grid, exactly as TASK 9
 * requires — each is a self-contained card that can be moved or removed without
 * touching the others:
 *
 *   1. gallery + floating side panel  (the buy decision)
 *   2. specification sheet            (the facts)
 *   3. reviews                        (the social proof)
 *   4. similar products + AI picks    (the next step)
 *
 * Layout decisions worth stating:
 *
 *   - The side panel is `lg:sticky`. On a long product page the price, stock and
 *     the buy button must never scroll out of reach; on mobile the panel simply
 *     stacks under the gallery, because a sticky panel on a small screen eats
 *     the content it is supposed to support.
 *   - Adding to the cart opens the holographic mini-cart rather than navigating.
 *     The user is mid-comparison; a page change would destroy that context.
 *   - `trackProductView` fires once per product load and feeds the TASK 7
 *     recommender. It runs in the same effect that loads the product, so a
 *     failed fetch never records a view that did not happen.
 */

/** Static guarantees — real shop policy, not per-product data. */
const ASSURANCES = [
  { icon: 'truck', title: 'Livrare 24–48h', detail: 'Curier rapid în toată țara' },
  { icon: 'shield', title: 'Garanție 24 luni', detail: 'Service autorizat' },
  { icon: 'refresh', title: 'Retur 14 zile', detail: 'Fără justificare' },
  { icon: 'bolt', title: 'Plată securizată', detail: 'Card sau ramburs' },
];

function SpecRow({ icon, label, value }) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <div className="flex items-center gap-3 rounded-xl border border-[rgba(255,255,255,0.09)] bg-[rgba(255,255,255,0.035)] px-3.5 py-3 transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.35)] hover:bg-[rgba(34,232,245,0.06)]">
      <span
        aria-hidden="true"
        className="grid h-8 w-8 shrink-0 place-items-center rounded-lg border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.05)]"
      >
        <GeoIcon name={icon} className="h-4 w-4" accent="var(--xx-cyan)" />
      </span>
      <span className="text-xs uppercase tracking-[0.12em] xx-ink-dim">{label}</span>
      <span className="ml-auto text-right text-sm font-semibold text-[color:var(--xx-ink)]">{value}</span>
    </div>
  );
}

export default function ProductDetails() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addItem } = useCart();

  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lightbox, setLightbox] = useState(null);
  const [miniCart, setMiniCart] = useState(false);

  const [similar, setSimilar] = useState([]);
  const [similarLoading, setSimilarLoading] = useState(true);

  /* ---------------- data ---------------- */

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setQuantity(1);
    setMiniCart(false);

    productService
      .getById(id)
      .then((data) => {
        if (cancelled) return;
        setProduct(data);
        // The behaviour log only records views that actually resolved.
        trackProductView(data);
      })
      .catch(() => {
        if (!cancelled) setError('Produsul nu a fost găsit.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  // Similar products: same category, current product removed. Fetched only once
  // the category is known, so the request is never wasted on a wrong filter.
  const category = product?.category || null;

  useEffect(() => {
    if (!category) {
      setSimilar([]);
      setSimilarLoading(false);
      return undefined;
    }

    let cancelled = false;
    setSimilarLoading(true);

    productService
      .list({ page: 0, size: 12, category, sortBy: 'id', direction: 'desc' })
      .then((data) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : data?.content || [];
        setSimilar(list.filter((item) => String(item.id) !== String(id)).slice(0, 8));
      })
      .catch(() => {
        if (!cancelled) setSimilar([]);
      })
      .finally(() => {
        if (!cancelled) setSimilarLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [category, id]);

  useSeo({
    title: product?.name,
    description: product
      ? `${product.name}${product.brand ? ' ' + product.brand : ''} — ${
          product.description || 'disponibil la ElectroShop'
        }`.slice(0, 160)
      : undefined,
    path: `/products/${id}`,
    image: product ? resolveImage(product.imageUrl) : undefined,
  });

  // The raw gallery (unresolved URLs) feeds the Lightbox, which resolves them
  // itself; HoloGallery receives resolved URLs because it renders them directly.
  const rawGallery = useMemo(() => {
    if (!product) return [];
    return (product.images && product.images.length
      ? product.images.map((image) => image.url)
      : [product.imageUrl]
    ).filter(Boolean);
  }, [product]);

  const gallery = useMemo(() => rawGallery.map((url) => resolveImage(url)), [rawGallery]);

  // A new array literal on every render would re-rank the AI module needlessly.
  const excludeIds = useMemo(() => (product ? [product.id] : []), [product]);

  /* ---------------- states ---------------- */

  if (loading) {
    return (
      <div className="grid min-h-[50vh] place-items-center">
        <HoloLoader size="xl" label="Se încarcă produsul" />
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="mx-auto max-w-md py-20 text-center">
        <span
          aria-hidden="true"
          className="mx-auto grid h-14 w-14 place-items-center rounded-2xl border border-[rgba(255,84,112,0.35)] bg-[rgba(255,84,112,0.1)]"
        >
          <GeoIcon name="alert" className="h-6 w-6" accent="var(--xx-red)" />
        </span>
        <h1 className="xx-title mt-4 text-2xl">{error || 'Produsul nu a fost găsit.'}</h1>
        <p className="mt-2 text-sm xx-ink-muted">
          Linkul poate fi expirat sau produsul a fost retras din catalog.
        </p>
        <NeonButton to="/products" className="mt-6">
          Înapoi la catalog
        </NeonButton>
      </div>
    );
  }

  const outOfStock = product.stockQuantity <= 0;
  const lowStock = !outOfStock && product.stockQuantity < 5;

  const handleAddToCart = () => {
    addItem(product, quantity);
    setMiniCart(true);
  };

  const handleBuyNow = () => {
    addItem(product, quantity);
    navigate('/cart');
  };

  const stepQuantity = (delta) =>
    setQuantity((current) => Math.max(1, Math.min(product.stockQuantity, current + delta)));

  return (
    <div className="pb-6">
      {/* Breadcrumb — the trail back, in the system's voice. */}
      <nav aria-label="Navigare" className="flex flex-wrap items-center gap-2 text-xs xx-ink-dim">
        <Link to="/" className="transition-colors duration-xx hover:text-[color:var(--xx-cyan)]">
          Acasă
        </Link>
        <span aria-hidden="true">/</span>
        <Link to="/products" className="transition-colors duration-xx hover:text-[color:var(--xx-cyan)]">
          Produse
        </Link>
        {product.category ? (
          <>
            <span aria-hidden="true">/</span>
            <Link
              to={`/products?category=${encodeURIComponent(product.category)}&page=0`}
              className="transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
            >
              {product.category}
            </Link>
          </>
        ) : null}
        <span aria-hidden="true">/</span>
        <span className="text-[color:var(--xx-ink-muted)]">{product.name}</span>
      </nav>

      {/* ---------- MODULE 1 — gallery + floating side panel ---------- */}
      <Grid12 className="mt-5 items-start">
        <Module span={7} spanSm={6} spanTv={4}>
          <HoloGallery
            images={gallery}
            alt={product.name}
            onZoom={(index) => setLightbox(index)}
          />
        </Module>

        <Module span={5} spanSm={6} spanTv={2}>
          {/* The floating data panel from the brief: violet glow, glass surface,
              sticky on desktop so the decision controls never scroll away. */}
          <div className="lg:sticky lg:top-28">
            <div
              className="card relative overflow-hidden p-5 sm:p-6"
              style={{ boxShadow: 'inset 0 0 80px -26px rgba(122,60,255,0.7), 0 26px 60px -30px rgba(0,0,0,0.9)' }}
            >
              <div className="flex flex-wrap items-center gap-2">
                {product.brand ? (
                  <NeonBadge tone="neon" icon={<GeoIcon name="tag" className="h-3 w-3" accent="currentColor" />}>
                    {product.brand}
                  </NeonBadge>
                ) : null}
                {product.category ? <NeonBadge tone="neutral">{product.category}</NeonBadge> : null}
                {product.subcategory ? <NeonBadge tone="neutral">{product.subcategory}</NeonBadge> : null}
              </div>

              <h1 className="xx-title mt-3 text-2xl sm:text-3xl tv:text-4xl">{product.name}</h1>

              <div className="mt-5 flex items-end gap-3">
                <span className="font-display text-4xl font-bold xx-text-gradient tv:text-5xl">
                  {formatPrice(product.price)}
                </span>
                <span className="pb-2 text-xs xx-ink-dim">TVA inclus</span>
              </div>

              <div className="mt-3">
                {outOfStock ? (
                  <NeonBadge
                    tone="critical"
                    icon={<GeoIcon name="close" className="h-3 w-3" accent="currentColor" />}
                  >
                    Stoc epuizat
                  </NeonBadge>
                ) : lowStock ? (
                  <NeonBadge
                    tone="warning"
                    pulse
                    icon={<GeoIcon name="alert" className="h-3 w-3" accent="currentColor" />}
                  >
                    Ultimele {product.stockQuantity} bucăți
                  </NeonBadge>
                ) : (
                  <NeonBadge
                    tone="good"
                    icon={<GeoIcon name="check" className="h-3 w-3" accent="currentColor" />}
                  >
                    În stoc: {product.stockQuantity} buc.
                  </NeonBadge>
                )}
              </div>

              {/* Delivery estimate — computed, not decorative. */}
              <div className="mt-4 flex items-center gap-3 rounded-xl border border-[rgba(34,232,245,0.22)] bg-[rgba(34,232,245,0.06)] px-3.5 py-3">
                <GeoIcon name="truck" className="h-5 w-5 shrink-0" accent="var(--xx-cyan)" />
                <p className="text-sm xx-ink-muted">
                  {outOfStock ? (
                    'Produs indisponibil momentan. Revino pentru reaprovizionare.'
                  ) : (
                    <>
                      Livrare estimată{' '}
                      <span className="font-semibold text-[color:var(--xx-ink)]">24–48 de ore</span> prin curier
                      rapid.
                    </>
                  )}
                </p>
              </div>

              {!outOfStock && (
                <div className="mt-5 flex items-center gap-3">
                  <span className="text-sm xx-ink-muted">Cantitate</span>
                  <div className="flex items-center gap-1 rounded-full border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] p-1">
                    <button
                      type="button"
                      onClick={() => stepQuantity(-1)}
                      disabled={quantity <= 1}
                      aria-label="Scade cantitatea"
                      className="grid h-8 w-8 place-items-center rounded-full text-[color:var(--xx-ink)] transition-colors duration-xx hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-30"
                    >
                      −
                    </button>
                    <input
                      type="number"
                      min={1}
                      max={product.stockQuantity}
                      value={quantity}
                      aria-label="Cantitate"
                      onChange={(event) =>
                        setQuantity(
                          Math.max(1, Math.min(product.stockQuantity, Number(event.target.value) || 1))
                        )
                      }
                      className="w-12 border-0 bg-transparent text-center font-display text-base font-bold text-[color:var(--xx-ink)] focus:outline-none"
                    />
                    <button
                      type="button"
                      onClick={() => stepQuantity(1)}
                      disabled={quantity >= product.stockQuantity}
                      aria-label="Crește cantitatea"
                      className="grid h-8 w-8 place-items-center rounded-full text-[color:var(--xx-ink)] transition-colors duration-xx hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-30"
                    >
                      +
                    </button>
                  </div>
                  <span className="text-sm xx-ink-dim">
                    Total {formatPrice(product.price * quantity)}
                  </span>
                </div>
              )}

              <div className="mt-5 flex flex-col gap-3 sm:flex-row">
                <NeonButton
                  variant="secondary"
                  size="lg"
                  className="flex-1"
                  disabled={outOfStock}
                  onClick={handleAddToCart}
                  icon={<GeoIcon name="cart" className="h-5 w-5" accent="currentColor" />}
                >
                  Adaugă în coș
                </NeonButton>
                <NeonButton
                  size="lg"
                  className="flex-1"
                  pulse={!outOfStock}
                  disabled={outOfStock}
                  onClick={handleBuyNow}
                  icon={<GeoIcon name="bolt" className="h-5 w-5" accent="currentColor" />}
                >
                  Cumpără acum
                </NeonButton>
              </div>

              <div className="mt-5 grid grid-cols-2 gap-2 border-t border-[rgba(255,255,255,0.1)] pt-4">
                {ASSURANCES.map((item) => (
                  <div key={item.title} className="flex items-start gap-2">
                    <GeoIcon name={item.icon} className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-aqua)" />
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-[color:var(--xx-ink)]">{item.title}</p>
                      <p className="text-[0.68rem] xx-ink-dim">{item.detail}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </Module>
      </Grid12>

      {/* ---------- MODULE 2 — description + specification sheet ---------- */}
      <Grid12 className="mt-10 items-start">
        <Module span={7} spanSm={6} spanTv={4}>
          <Reveal>
            <section className="card p-5 sm:p-6">
              <p className="xx-eyebrow">Descriere</p>
              <h2 className="xx-title text-xl">Despre produs</h2>
              <p className="mt-3 whitespace-pre-line text-sm leading-relaxed xx-ink-muted tv:text-base">
                {product.description || 'Descrierea detaliată pentru acest produs va fi disponibilă în curând.'}
              </p>
            </section>
          </Reveal>
        </Module>

        <Module span={5} spanSm={6} spanTv={2}>
          <Reveal delay={80}>
            <section className="card p-5 sm:p-6">
              <p className="xx-eyebrow">Fișă tehnică</p>
              <h2 className="xx-title text-xl">Specificații</h2>
              <div className="mt-4 space-y-2">
                <SpecRow icon="tag" label="Brand" value={product.brand} />
                <SpecRow icon="grid" label="Categorie" value={product.category} />
                <SpecRow icon="layers" label="Subcategorie" value={product.subcategory} />
                <SpecRow icon="document" label="Cod produs" value={product.sku} />
                <SpecRow icon="box" label="Stoc" value={`${product.stockQuantity} buc.`} />
                <SpecRow icon="shield" label="Garanție" value="24 luni" />
                <SpecRow icon="coins" label="Preț" value={formatPrice(product.price)} />
              </div>
            </section>
          </Reveal>
        </Module>
      </Grid12>

      {/* ---------- MODULE 3 — reviews ---------- */}
      <div className="mt-10">
        <SectionHeader eyebrow="Social proof" title="Ce spun clienții" />
        <HoloReviews product={product} />
      </div>

      {/* ---------- MODULE 4 — similar products + AI panel ---------- */}
      <Grid12 className="mt-12 items-start">
        <Module span={8} spanSm={6} spanTv={4}>
          <SectionHeader
            eyebrow="Alternative"
            title="Produse similare"
            subtitle={
              product.category
                ? `Alte produse din categoria ${product.category}.`
                : 'Alte produse din catalog.'
            }
            actionTo={
              product.category
                ? `/products?category=${encodeURIComponent(product.category)}&page=0`
                : '/products'
            }
            actionLabel="Vezi categoria"
          />

          {similarLoading ? (
            <HoloGridSkeleton count={3} />
          ) : similar.length > 0 ? (
            <HoloCarousel label="Produse similare">
              {similar.map((item) => (
                <div key={item.id} className="w-[268px] sm:w-[300px]">
                  <ProductCard product={item} />
                </div>
              ))}
            </HoloCarousel>
          ) : (
            <div className="card card-static p-8 text-center text-sm xx-ink-muted">
              Nu există alte produse în această categorie momentan.
            </div>
          )}
        </Module>

        <Module span={4} spanSm={6} spanTv={2}>
          <div className="lg:sticky lg:top-28">
            <AIPicks
              variant="panel"
              limit={6}
              exclude={excludeIds}
              title="Recomandări pentru tine"
              eyebrow="Predictive Shopping"
            />
          </div>
        </Module>
      </Grid12>

      {/* ---------- overlays ---------- */}
      <MiniCart
        open={miniCart}
        onClose={() => setMiniCart(false)}
        product={product}
        quantity={quantity}
      />

      {lightbox !== null && (
        <Lightbox images={rawGallery} index={lightbox} onClose={() => setLightbox(null)} />
      )}
    </div>
  );
}
