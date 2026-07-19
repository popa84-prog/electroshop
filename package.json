import { useEffect, useState } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import orderService from '../api/orderService';
import Spinner from '../components/Spinner';
import { formatPrice, formatDate, statusColor, resolveImage } from '../utils/format';

export default function OrderDetails() {
  const { id } = useParams();
  const location = useLocation();
  const justPlaced = location.state?.justPlaced;
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    orderService
      .getOne(id)
      .then(setOrder)
      .catch(() => setError('Comanda nu a fost găsită.'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Spinner />;
  if (error) return <p className="py-16 text-center text-slate-500">{error}</p>;

  return (
    <div>
      <Link to="/orders" className="text-sm text-brand-600 hover:underline">
        ← Înapoi la comenzi
      </Link>

      {justPlaced && (
        <div className="mt-4 rounded-lg bg-green-50 px-4 py-3 text-sm text-green-700">
          ✅ Comanda ta a fost plasată cu succes!
        </div>
      )}

      <div className="mt-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Comanda #{order.id}</h1>
        <span className={`badge ${statusColor(order.status)}`}>{order.status}</span>
      </div>
      <p className="mt-1 text-sm text-slate-500">Plasată la {formatDate(order.createdAt)}</p>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <div className="space-y-3 lg:col-span-2">
          {order.items.map((item) => (
            <div key={item.id} className="card flex items-center gap-4 p-4">
              <img
                src={resolveImage(item.imageUrl)}
                alt={item.productName}
                className="h-16 w-16 rounded-lg object-cover"
              />
              <div className="flex-1">
                <p className="font-medium text-slate-800">{item.productName}</p>
                <p className="text-sm text-slate-500">
                  {item.quantity} × {formatPrice(item.unitPrice)}
                </p>
              </div>
              <span className="font-semibold text-slate-800">{formatPrice(item.subtotal)}</span>
            </div>
          ))}
        </div>

        <div className="card h-fit p-6">
          <h2 className="text-lg font-semibold text-slate-800">Detalii</h2>
          <div className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-500">Adresă livrare</span>
            </div>
            <p className="text-slate-700">{order.shippingAddress || '-'}</p>
          </div>
          <div className="mt-4 flex justify-between border-t border-slate-200 pt-4 text-lg font-bold">
            <span>Total</span>
            <span>{formatPrice(order.totalAmount)}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
