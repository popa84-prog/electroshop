import { createContext, useContext, useEffect, useMemo, useState } from 'react';

const CartContext = createContext(null);
const CART_KEY = 'es_cart';
const STOCK_MSG = 'Cantitatea solicitată depășește stocul disponibil.';

const maxStock = (v) => (Number.isFinite(v) ? v : 999);

export function CartProvider({ children }) {
  const [items, setItems] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(CART_KEY)) || [];
    } catch {
      return [];
    }
  });

  // Transient notice shown as a toast (e.g. when stock is exceeded).
  const [notice, setNotice] = useState(null);

  useEffect(() => {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
  }, [items]);

  useEffect(() => {
    if (!notice) return undefined;
    const t = setTimeout(() => setNotice(null), 3800);
    return () => clearTimeout(t);
  }, [notice]);

  const flagStock = () => setNotice({ id: Date.now(), text: STOCK_MSG });
  const clearNotice = () => setNotice(null);

  /**
   * Add `quantity` of a product to the cart. Never lets the cart quantity exceed
   * the product's available stock. Returns true if the full amount was added,
   * false if it had to be clamped (in which case a notice is shown).
   */
  const addItem = (product, quantity = 1) => {
    const max = maxStock(product.stockQuantity);
    const existing = items.find((i) => i.id === product.id);
    const current = existing ? existing.quantity : 0;
    let next = current + quantity;
    let clamped = false;
    if (next > max) {
      next = max;
      clamped = true;
    }
    if (next < 1) next = 1;

    setItems((prev) => {
      const ex = prev.find((i) => i.id === product.id);
      if (ex) {
        return prev.map((i) =>
          i.id === product.id ? { ...i, quantity: next, stockQuantity: max } : i
        );
      }
      return [
        ...prev,
        {
          id: product.id,
          name: product.name,
          price: product.price,
          imageUrl: product.imageUrl,
          stockQuantity: max,
          quantity: next,
        },
      ];
    });

    if (clamped) flagStock();
    return !clamped;
  };

  /**
   * Set the quantity of a cart line. Clamps to [1, stock]; going below 1 removes
   * the line. Requesting more than the available stock shows the notice.
   */
  const updateQuantity = (id, quantity) => {
    const item = items.find((i) => i.id === id);
    const max = maxStock(item?.stockQuantity);
    let q = Number(quantity);
    if (!Number.isFinite(q)) q = 1;
    let clamped = false;
    if (q > max) {
      q = max;
      clamped = true;
    }

    setItems((prev) =>
      prev
        .map((i) => (i.id === id ? { ...i, quantity: Math.max(0, q) } : i))
        .filter((i) => i.quantity > 0)
    );

    if (clamped) flagStock();
  };

  const removeItem = (id) => setItems((prev) => prev.filter((i) => i.id !== id));

  const clearCart = () => setItems([]);

  const totalItems = items.reduce((sum, i) => sum + i.quantity, 0);
  const totalPrice = items.reduce((sum, i) => sum + Number(i.price) * i.quantity, 0);

  const value = useMemo(
    () => ({
      items,
      addItem,
      updateQuantity,
      removeItem,
      clearCart,
      totalItems,
      totalPrice,
      notice,
      clearNotice,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items, notice]
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within a CartProvider');
  return ctx;
}
