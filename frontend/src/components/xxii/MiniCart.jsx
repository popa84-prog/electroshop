import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useCart } from '../../context/CartContext';
import { formatPrice, resolveImage } from '../../utils/format';
import GeoIcon from './GeoIcon';
import NeonButton from './NeonButton';

/**
 * XXII — TASK 3 (holographic mini-cart with materialize animation).
 *
 * A floating confirmation panel that materialises when a product is added, so
 * the action gets feedback without navigating away from the product. It shows
 * what was just added, the live cart total, and the two ways forward.
 *
 * Three deliberate behaviours:
 *
 *   - It auto-dismisses after `AUTO_MS`, but the timer is cancelled while the
 *     pointer is over the panel; a panel that vanishes mid-click is a trap.
 *   - Escape closes it, and the close button is a real button, so it is
 *     dismissible without a mouse.
 *   - It never blocks the page. There is no backdrop and no scroll lock: the
 *     user added an item, they did not open a modal.
 */

const AUTO_MS = 5200;

export default function MiniCart({ open, onClose, product = null, quantity = 1 }) {
  const { items, totalItems, totalPrice } = useCart();
  // While the pointer rests on the panel the countdown is suspended: a panel
  // that disappears while it is being read (or clicked) is a trap.
  const [held, setHeld] = useState(false);

  useEffect(() => {
    if (!open) return undefined;

    const onKey = (event) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);

    const timer = held ? null : window.setTimeout(onClose, AUTO_MS);

    return () => {
      if (timer) window.clearTimeout(timer);
      window.removeEventListener('keydown', onKey);
    };
  }, [open, held, onClose]);

  // A fresh open always restarts un-held, otherwise a panel dismissed while the
  // cursor sat on it would reopen frozen.
  useEffect(() => {
    if (!open) setHeld(false);
  }, [open]);

  if (!open) return null;

  const others = items.filter((item) => !product || item.id !== product.id).slice(0, 2);

  return (
    <div
      className="pointer-events-none fixed inset-x-0 top-24 z-50 flex justify-center px-4 sm:inset-x-auto sm:right-6 sm:justify-end"
      role="status"
      aria-live="polite"
    >
      <div
        onMouseEnter={() => setHeld(true)}
        onMouseLeave={() => setHeld(false)}
        onFocusCapture={() => setHeld(true)}
        onBlurCapture={() => setHeld(false)}
        className="pointer-events-auto w-full max-w-sm rounded-[1.25rem] border border-[rgba(34,232,245,0.35)] bg-[rgba(7,8,24,0.88)] p-4 shadow-[0_28px_70px_-28px_rgba(0,0,0,0.95),0_0_54px_-14px_rgba(34,232,245,0.55)] backdrop-blur-glass-xl animate-xx-materialize"
      >
        <header className="flex items-start justify-between gap-3">
          <p className="flex items-center gap-2 text-sm font-semibold text-[color:var(--xx-cyan)]">
            <GeoIcon name="check" className="h-4 w-4" accent="currentColor" />
            Adăugat în coș
          </p>
          <button
            type="button"
            onClick={onClose}
            aria-label="Închide"
            className="grid h-7 w-7 shrink-0 place-items-center rounded-full text-[color:var(--xx-ink-muted)] transition-colors duration-xx hover:bg-white/10 hover:text-white"
          >
            <GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />
          </button>
        </header>

        {product ? (
          <div className="mt-3 flex items-center gap-3 rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] p-2.5">
            <img
              src={resolveImage(product.imageThumbUrl || product.imageUrl)}
              alt=""
              className="h-14 w-14 shrink-0 rounded-lg object-cover"
            />
            <div className="min-w-0 flex-1">
              <p className="line-clamp-2 text-sm font-semibold text-[color:var(--xx-ink)]">{product.name}</p>
              <p className="mt-0.5 text-xs xx-ink-dim">
                {quantity} × {formatPrice(product.price)}
              </p>
            </div>
          </div>
        ) : null}

        {others.length > 0 ? (
          <ul className="mt-2 space-y-1">
            {others.map((item) => (
              <li key={item.id} className="flex items-center justify-between gap-2 px-1 text-xs xx-ink-dim">
                <span className="line-clamp-1">
                  {item.quantity} × {item.name}
                </span>
                <span className="shrink-0">{formatPrice(item.price * item.quantity)}</span>
              </li>
            ))}
          </ul>
        ) : null}

        <div className="mt-3 flex items-center justify-between border-t border-[rgba(255,255,255,0.1)] pt-3">
          <span className="text-xs uppercase tracking-[0.14em] xx-ink-dim">
            {totalItems} {totalItems === 1 ? 'produs' : 'produse'}
          </span>
          <span className="font-display text-lg font-bold text-[color:var(--xx-ink)]">
            {formatPrice(totalPrice)}
          </span>
        </div>

        <div className="mt-3 flex gap-2">
          <NeonButton variant="ghost" size="sm" className="flex-1" onClick={onClose}>
            Continuă
          </NeonButton>
          <NeonButton
            to="/cart"
            size="sm"
            className="flex-1"
            icon={<GeoIcon name="cart" className="h-4 w-4" accent="currentColor" />}
            onClick={onClose}
          >
            Vezi coșul
          </NeonButton>
        </div>

        <p className="mt-2 text-center text-[0.68rem] xx-ink-dim">
          <Link to="/checkout" className="transition-colors duration-xx hover:text-[color:var(--xx-cyan)]">
            Mergi direct la finalizare →
          </Link>
        </p>
      </div>
    </div>
  );
}
