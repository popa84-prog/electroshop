import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import orderService from '../api/orderService';
import { formatPrice, resolveImage } from '../utils/format';

export default function Checkout() {
  const { items, totalPrice, clearCart } = useCart();
  const navigate = useNavigate();
  const [shippingAddress, setShippingAddress] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const payload = {
        shippingAddress,
        items: items.map((i) => ({ productId: i.id, quantity: i.quantity })),
      };
      const order = await orderService.place(payload);
      clearCart();
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } });
    } catch (err) {
      setError(err.response?.data?.message || 'Comanda nu a putut fi plasată.');
    } finally {
      setSubmitting(false);
    }
  };

  if (items.length === 0) {
    return (
      <div className="py-20 text-center">
        <h2 className="text-xl font-semibold text-slate-700">Coșul este gol</h2>
        <button onClick={() => navigate('/products')} className="btn-primary mt-6">
          Vezi produse
        </button>
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Finalizare comandă</h1>

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <form onSubmit={handleSubmit} className="card space-y-4 p-6 lg:col-span-2">
          <h2 className="text-lg font-semibold text-slate-800">Adresă de livrare</h2>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">
              Adresă completă
            </label>
            <textarea
              className="input min-h-[100px]"
              placeholder="Stradă, număr, oraș, județ, cod poștal"
              value={shippingAddress}
              onChange={(e) => setShippingAddress(e.target.value)}
              required
            />
          </div>
          <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-600">
            💳 Plata se face la livrare (ramburs). Nu este necesar un card.
          </div>
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting ? 'Se plasează...' : `Plasează comanda · ${formatPrice(totalPrice)}`}
          </button>
        </form>

        <div className="card h-fit p-6">
          <h2 className="text-lg font-semibold text-slate-800">Produse</h2>
          <div className="mt-4 space-y-3">
            {items.map((i) => (
              <div key={i.id} className="flex items-center gap-3">
                <img
                  src={resolveImage(i.imageUrl)}
                  alt={i.name}
                  className="h-12 w-12 rounded object-cover"
                />
                <div className="flex-1 text-sm">
                  <p className="font-medium text-slate-700">{i.name}</p>
                  <p className="text-slate-500">
                    {i.quantity} × {formatPrice(i.price)}
                  </p>
                </div>
                <span className="text-sm font-semibold">
                  {formatPrice(Number(i.price) * i.quantity)}
                </span>
              </div>
            ))}
          </div>
          <div className="mt-4 flex justify-between border-t border-slate-200 pt-4 text-lg font-bold">
            <span>Total</span>
            <span>{formatPrice(totalPrice)}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
