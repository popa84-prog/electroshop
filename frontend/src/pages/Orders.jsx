import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import orderService from '../api/orderService';
import Pagination from '../components/Pagination';
import Spinner from '../components/Spinner';
import { formatPrice, formatDate, statusColor } from '../utils/format';

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    orderService
      .myOrders({ page, size: 10 })
      .then((data) => {
        setOrders(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  }, [page]);

  if (loading) return <Spinner />;

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Comenzile mele</h1>

      {orders.length === 0 ? (
        <div className="py-16 text-center text-slate-500">
          <p>Nu ai plasat încă nicio comandă.</p>
          <Link to="/products" className="btn-primary mt-4 inline-flex">
            Vezi produse
          </Link>
        </div>
      ) : (
        <>
          <div className="space-y-4">
            {orders.map((o) => (
              <Link
                key={o.id}
                to={`/orders/${o.id}`}
                className="card flex flex-col gap-3 p-5 transition hover:shadow-md sm:flex-row sm:items-center sm:justify-between"
              >
                <div>
                  <p className="font-semibold text-slate-800">Comanda #{o.id}</p>
                  <p className="text-sm text-slate-500">{formatDate(o.createdAt)}</p>
                  <p className="text-sm text-slate-500">{o.items.length} produse</p>
                </div>
                <div className="flex items-center gap-4">
                  <span className={`badge ${statusColor(o.status)}`}>{o.status}</span>
                  <span className="text-lg font-bold text-slate-900">
                    {formatPrice(o.totalAmount)}
                  </span>
                </div>
              </Link>
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
