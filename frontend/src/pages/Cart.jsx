import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { formatPrice, resolveImage } from '../utils/format';

export default function Cart() {
  const { items, updateQuantity, removeItem, totalPrice, clearCart } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const handleCheckout = () => {
    navigate(isAuthenticated ? '/checkout' : '/login', {
      state: isAuthenticated ? undefined : { from: { pathname: '/checkout' } },
    });
  };

  if (items.length === 0) {
    return (
      <div className="py-20 text-center">
        <p className="text-5xl">🛒</p>
        <h2 className="mt-4 text-xl font-semibold text-slate-700">Coșul tău este gol</h2>
        <Link to="/products" className="btn-primary mt-6 inline-flex">
          Continuă cumpărăturile
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Coșul meu</h1>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          {items.map((item) => (
            <div key={item.id} className="card flex items-center gap-4 p-4">
              <img
                src={resolveImage(item.imageUrl)}
                alt={item.name}
                className="h-20 w-20 rounded-lg object-cover"
              />
              <div className="flex-1">
                <Link to={`/products/${item.id}`} className="font-semibold text-slate-800 hover:text-brand-600">
                  {item.name}
                </Link>
                <p className="text-sm text-slate-500">{formatPrice(item.price)}</p>
              </div>
              <input
                type="number"
                min={1}
                max={item.stockQuantity ?? 999}
                value={item.quantity}
                onChange={(e) => updateQuantity(item.id, Number(e.target.value))}
                className="input w-20"
              />
              <div className="w-28 text-right font-semibold text-slate-800">
                {formatPrice(Number(item.price) * item.quantity)}
              </div>
              <button
                onClick={() => removeItem(item.id)}
                className="rounded-md p-2 text-red-500 hover:bg-red-50"
                aria-label="Elimină"
              >
                🗑️
              </button>
            </div>
          ))}
          <button onClick={clearCart} className="text-sm text-slate-500 hover:text-red-600">
            Golește coșul
          </button>
        </div>

        <div className="card h-fit p-6">
          <h2 className="text-lg font-semibold text-slate-800">Sumar comandă</h2>
          <div className="mt-4 flex justify-between text-slate-600">
            <span>Subtotal</span>
            <span>{formatPrice(totalPrice)}</span>
          </div>
          <div className="mt-2 flex justify-between text-slate-600">
            <span>Transport</span>
            <span className="text-green-600">Gratuit</span>
          </div>
          <div className="mt-4 flex justify-between border-t border-slate-200 pt-4 text-lg font-bold text-slate-900">
            <span>Total</span>
            <span>{formatPrice(totalPrice)}</span>
          </div>
          <button onClick={handleCheckout} className="btn-primary mt-6 w-full">
            Finalizează comanda
          </button>
        </div>
      </div>
    </div>
  );
}
