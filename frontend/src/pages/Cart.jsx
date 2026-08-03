import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { formatPrice, resolveImage } from '../utils/format';
import {
  AIPicks,
  GeoIcon,
  Grid12,
  Module,
  NeonBadge,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../components/xxii';

/**
 * XXII — TASK 9 (modular grid) with the TASK 7 contextual AI panel.
 *
 * The cart is two modules: the line-item list (8 columns) and a sticky order
 * summary (4 columns). The summary sticks because the decision it carries —
 * total and checkout — must stay reachable while a long cart scrolls.
 *
 * The delete control uses a two-step confirmation inline on the row rather than
 * a modal. Removing a line is cheap to undo by re-adding, but an accidental tap
 * on a 44px target is common on mobile, so the second tap is worth its cost and
 * a full modal is not.
 *
 * Free-shipping threshold is real logic, not decoration: the progress bar is
 * driven by the actual subtotal against `FREE_SHIPPING_FROM`.
 */

const FREE_SHIPPING_FROM = 300;
const SHIPPING_COST = 19.99;

export default function Cart() {
  const { items, updateQuantity, removeItem, totalPrice, totalItems, clearCart } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(null);
  const [clearing, setClearing] = useState(false);

  const shipping = totalPrice >= FREE_SHIPPING_FROM || totalPrice === 0 ? 0 : SHIPPING_COST;
  const missing = Math.max(0, FREE_SHIPPING_FROM - totalPrice);
  const progress = Math.min(100, (totalPrice / FREE_SHIPPING_FROM) * 100);

  const excludeIds = useMemo(() => items.map((item) => item.id), [items]);

  const handleCheckout = () => {
    navigate(isAuthenticated ? '/checkout' : '/login', {
      state: isAuthenticated ? undefined : { from: { pathname: '/checkout' } },
    });
  };

  /* ---------------- empty state ---------------- */

  if (items.length === 0) {
    return (
      <div className="py-16">
        <div className="mx-auto max-w-lg text-center">
          <span
            aria-hidden="true"
            className="mx-auto grid h-20 w-20 place-items-center rounded-2xl border border-[rgba(34,232,245,0.28)] bg-[rgba(34,232,245,0.08)] shadow-glow-aqua animate-xx-float"
          >
            <GeoIcon name="cart" className="h-9 w-9" accent="var(--xx-cyan)" />
          </span>
          <h1 className="xx-title mt-6 text-2xl sm:text-3xl">Coșul tău este gol</h1>
          <p className="mt-2 text-sm xx-ink-muted">
            Adaugă produse din catalog și revino aici pentru finalizare. Coșul se păstrează pe acest
            dispozitiv.
          </p>
          <NeonButton
            to="/products"
            size="lg"
            className="mt-6"
            icon={<GeoIcon name="grid" className="h-5 w-5" accent="currentColor" />}
          >
            Continuă cumpărăturile
          </NeonButton>
        </div>

        <div className="mt-14">
          <AIPicks variant="carousel" limit={8} title="Poate te interesează" />
        </div>
      </div>
    );
  }

  /* ---------------- populated cart ---------------- */

  return (
    <div className="pb-6">
      <SectionHeader
        as="h1"
        eyebrow="Checkout flow"
        title="Coșul meu"
        subtitle={`${totalItems} ${totalItems === 1 ? 'produs' : 'produse'} pregătite pentru comandă.`}
        actionTo="/products"
        actionLabel="Continuă cumpărăturile"
      />

      <Grid12 className="items-start">
        {/* ---------- line items ---------- */}
        <Module span={8} spanSm={6} spanTv={4}>
          <div className="space-y-4">
            {items.map((item, index) => {
              const max = item.stockQuantity ?? 999;
              const atMax = item.quantity >= max;
              const isConfirming = confirming === item.id;

              return (
                <Reveal key={item.id} delay={index * 60}>
                  <article className="card flex flex-wrap items-center gap-4 p-4 transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.32)]">
                    <Link to={`/products/${item.id}`} className="shrink-0" aria-label={item.name}>
                      <div className="h-20 w-20 overflow-hidden rounded-xl bg-[rgba(255,255,255,0.05)]">
                        <img
                          src={resolveImage(item.imageUrl)}
                          alt={item.name}
                          loading="lazy"
                          className="h-full w-full object-cover transition-transform duration-500 ease-xx hover:scale-105"
                        />
                      </div>
                    </Link>

                    <div className="min-w-0 flex-1">
                      <Link
                        to={`/products/${item.id}`}
                        className="line-clamp-2 font-semibold text-[color:var(--xx-ink)] transition-colors duration-xx hover:text-[color:var(--xx-cyan)]"
                      >
                        {item.name}
                      </Link>
                      <p className="mt-0.5 text-sm xx-ink-dim">{formatPrice(item.price)} / buc.</p>
                      {atMax ? (
                        <span className="mt-1.5 inline-flex">
                          <NeonBadge
                            tone="warning"
                            icon={<GeoIcon name="alert" className="h-3 w-3" accent="currentColor" />}
                          >
                            Stoc maxim atins
                          </NeonBadge>
                        </span>
                      ) : null}
                    </div>

                    {/* Quantity stepper — 44px targets, so it works with a thumb. */}
                    <div className="flex items-center gap-1 rounded-full border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] p-1">
                      <button
                        type="button"
                        onClick={() => updateQuantity(item.id, item.quantity - 1)}
                        disabled={item.quantity <= 1}
                        aria-label={`Scade cantitatea pentru ${item.name}`}
                        className="grid h-9 w-9 place-items-center rounded-full text-[color:var(--xx-ink)] transition-colors duration-xx hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        −
                      </button>
                      <input
                        type="number"
                        min={1}
                        max={max}
                        value={item.quantity}
                        aria-label={`Cantitate pentru ${item.name}`}
                        onChange={(event) =>
                          updateQuantity(item.id, Math.max(1, Number(event.target.value) || 1))
                        }
                        className="w-12 border-0 bg-transparent text-center font-display text-base font-bold text-[color:var(--xx-ink)] focus:outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => updateQuantity(item.id, item.quantity + 1)}
                        disabled={atMax}
                        aria-label={`Crește cantitatea pentru ${item.name}`}
                        className="grid h-9 w-9 place-items-center rounded-full text-[color:var(--xx-ink)] transition-colors duration-xx hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        +
                      </button>
                    </div>

                    <div className="w-28 text-right font-display text-lg font-bold text-[color:var(--xx-ink)]">
                      {formatPrice(Number(item.price) * item.quantity)}
                    </div>

                    {/* Two-step remove: the second tap is the destructive one. */}
                    {isConfirming ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            removeItem(item.id);
                            setConfirming(null);
                          }}
                          className="rounded-full border border-[rgba(255,84,112,0.5)] bg-[rgba(255,84,112,0.12)] px-3 py-1.5 text-xs font-semibold text-[#ffb3c0] transition-all duration-xx hover:shadow-[0_0_28px_-6px_rgba(255,84,112,0.7)]"
                        >
                          Confirmă
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirming(null)}
                          className="rounded-full px-2 py-1.5 text-xs xx-ink-dim transition-colors duration-xx hover:text-white"
                        >
                          Anulează
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirming(item.id)}
                        aria-label={`Elimină ${item.name}`}
                        className="grid h-10 w-10 place-items-center rounded-full text-[color:var(--xx-ink-dim)] transition-all duration-xx hover:bg-[rgba(255,84,112,0.12)] hover:text-[color:var(--xx-red)]"
                      >
                        <GeoIcon name="trash" className="h-[1.15rem] w-[1.15rem]" accent="currentColor" />
                      </button>
                    )}
                  </article>
                </Reveal>
              );
            })}

            <div className="flex items-center gap-3 pt-1">
              {clearing ? (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      clearCart();
                      setClearing(false);
                    }}
                    className="rounded-full border border-[rgba(255,84,112,0.5)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-xs font-semibold text-[#ffb3c0]"
                  >
                    Confirmă golirea coșului
                  </button>
                  <button
                    type="button"
                    onClick={() => setClearing(false)}
                    className="text-xs xx-ink-dim transition-colors duration-xx hover:text-white"
                  >
                    Anulează
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  onClick={() => setClearing(true)}
                  className="inline-flex items-center gap-2 text-sm xx-ink-dim transition-colors duration-xx hover:text-[color:var(--xx-red)]"
                >
                  <GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />
                  Golește coșul
                </button>
              )}
            </div>
          </div>
        </Module>

        {/* ---------- summary ---------- */}
        <Module span={4} spanSm={6} spanTv={2}>
          <div className="space-y-6 lg:sticky lg:top-28">
            <div
              className="card p-5 sm:p-6"
              style={{ boxShadow: 'inset 0 0 80px -26px rgba(46,123,255,0.6), 0 26px 60px -30px rgba(0,0,0,0.9)' }}
            >
              <p className="xx-eyebrow">Sumar</p>
              <h2 className="xx-title text-xl">Comanda ta</h2>

              <dl className="mt-4 space-y-2.5 text-sm">
                <div className="flex items-center justify-between">
                  <dt className="xx-ink-muted">Subtotal</dt>
                  <dd className="font-semibold text-[color:var(--xx-ink)]">{formatPrice(totalPrice)}</dd>
                </div>
                <div className="flex items-center justify-between">
                  <dt className="xx-ink-muted">Transport</dt>
                  <dd className="font-semibold">
                    {shipping === 0 ? (
                      <span className="text-[color:var(--xx-lime)]">Gratuit</span>
                    ) : (
                      <span className="text-[color:var(--xx-ink)]">{formatPrice(shipping)}</span>
                    )}
                  </dd>
                </div>
              </dl>

              {/* Free-shipping progress — real arithmetic on the subtotal. */}
              <div className="mt-4">
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-[rgba(255,255,255,0.08)]">
                  <span
                    className="block h-full rounded-full bg-xx-primary transition-[width] duration-700 ease-xx"
                    style={{ width: `${progress}%` }}
                  />
                </div>
                <p className="mt-2 text-xs xx-ink-dim">
                  {missing > 0
                    ? `Mai adaugă ${formatPrice(missing)} pentru transport gratuit.`
                    : 'Transport gratuit deblocat.'}
                </p>
              </div>

              <div className="mt-4 flex items-center justify-between border-t border-[rgba(255,255,255,0.1)] pt-4">
                <span className="text-sm uppercase tracking-[0.14em] xx-ink-dim">Total</span>
                <span className="font-display text-2xl font-bold xx-text-gradient">
                  {formatPrice(totalPrice + shipping)}
                </span>
              </div>

              <NeonButton
                size="lg"
                block
                pulse
                className="mt-5"
                onClick={handleCheckout}
                icon={<GeoIcon name="bolt" className="h-5 w-5" accent="currentColor" />}
              >
                Finalizează comanda
              </NeonButton>

              {!isAuthenticated ? (
                <p className="mt-2 text-center text-xs xx-ink-dim">
                  Vei fi rugat să te autentifici înainte de plasarea comenzii.
                </p>
              ) : null}

              <div className="mt-4 grid grid-cols-2 gap-2 border-t border-[rgba(255,255,255,0.1)] pt-4">
                <span className="flex items-center gap-2 text-xs xx-ink-dim">
                  <GeoIcon name="shield" className="h-4 w-4" accent="var(--xx-aqua)" />
                  Plată securizată
                </span>
                <span className="flex items-center gap-2 text-xs xx-ink-dim">
                  <GeoIcon name="refresh" className="h-4 w-4" accent="var(--xx-aqua)" />
                  Retur 14 zile
                </span>
              </div>
            </div>

            {/* TASK 7 — contextual recommendations driven by the cart itself. */}
            <AIPicks
              variant="panel"
              limit={6}
              exclude={excludeIds}
              title="Completează coșul"
              eyebrow="Predictive Shopping"
            />
          </div>
        </Module>
      </Grid12>
    </div>
  );
}
