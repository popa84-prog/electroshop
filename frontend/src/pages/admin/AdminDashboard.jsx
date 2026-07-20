import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  CartesianGrid,
  Legend,
} from 'recharts';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Spinner from '../../components/Spinner';
import { formatPrice, formatDate } from '../../utils/format';

const COLORS = ['#f59e0b', '#3b82f6', '#6366f1', '#22c55e', '#ef4444'];

const STATUS_STYLE = {
  PENDING: 'bg-amber-100 text-amber-800',
  PAID: 'bg-blue-100 text-blue-800',
  SHIPPED: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
};

function StatCard({ label, value, icon }) {
  return (
    <div className="card flex items-center gap-4 p-5">
      <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-brand-50 text-2xl">
        {icon}
      </div>
      <div>
        <p className="text-sm text-slate-500">{label}</p>
        <p className="text-2xl font-bold text-slate-900">{value}</p>
      </div>
    </div>
  );
}

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [recent, setRecent] = useState([]);
  const [loading, setLoading] = useState(true);
  const [salesView, setSalesView] = useState('day'); // 'day' | 'month'

  useEffect(() => {
    Promise.all([
      adminService.dashboard().then(setStats).catch(() => setStats(null)),
      adminService
        .listOrders({ page: 0, size: 8 })
        .then((d) => setRecent(d.content || []))
        .catch(() => setRecent([])),
    ]).finally(() => setLoading(false));
  }, []);

  const salesData = useMemo(() => {
    const byDay = stats?.salesByDay || [];
    if (salesView === 'day') return byDay;
    const map = new Map();
    for (const p of byDay) {
      const month = String(p.date).slice(0, 7); // YYYY-MM
      map.set(month, (map.get(month) || 0) + Number(p.amount || 0));
    }
    return Array.from(map, ([date, amount]) => ({ date, amount }));
  }, [stats, salesView]);

  return (
    <div>
      <AdminNav />
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Panou de administrare</h1>

      {loading ? (
        <Spinner />
      ) : !stats ? (
        <p className="text-slate-500">Statisticile nu au putut fi încărcate.</p>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Utilizatori" value={stats.totalUsers} icon="👥" />
            <StatCard label="Produse" value={stats.totalProducts} icon="📦" />
            <StatCard label="Comenzi" value={stats.totalOrders} icon="🧾" />
            <StatCard label="Venit total" value={formatPrice(stats.totalRevenue)} icon="💰" />
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <div className="card p-5">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="font-semibold text-slate-800">Vânzări</h2>
                <div className="flex overflow-hidden rounded-lg border border-slate-200 text-sm">
                  <button
                    className={`px-3 py-1 ${salesView === 'day' ? 'bg-brand-600 text-white' : 'text-slate-600'}`}
                    onClick={() => setSalesView('day')}
                  >
                    Pe zile
                  </button>
                  <button
                    className={`px-3 py-1 ${salesView === 'month' ? 'bg-brand-600 text-white' : 'text-slate-600'}`}
                    onClick={() => setSalesView('month')}
                  >
                    Pe luni
                  </button>
                </div>
              </div>
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={salesData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(v) => formatPrice(v)} />
                  <Line type="monotone" dataKey="amount" stroke="#2563eb" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>

            <div className="card p-5">
              <h2 className="mb-4 font-semibold text-slate-800">Comenzi după status</h2>
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie
                    data={stats.ordersByStatus}
                    dataKey="count"
                    nameKey="status"
                    outerRadius={100}
                    label
                  >
                    {stats.ordersByStatus.map((entry, i) => (
                      <Cell key={entry.status} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Legend />
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <div className="card p-5">
              <h2 className="mb-4 font-semibold text-slate-800">Top produse vândute</h2>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={stats.topProducts}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis
                    dataKey="name"
                    tick={{ fontSize: 11 }}
                    interval={0}
                    angle={-15}
                    textAnchor="end"
                    height={60}
                  />
                  <YAxis tick={{ fontSize: 12 }} />
                  <Tooltip />
                  <Bar dataKey="unitsSold" name="Unități vândute" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            <div className="card p-5">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="font-semibold text-slate-800">Activitate recentă</h2>
                <Link to="/admin/orders" className="text-sm text-brand-600 hover:underline">
                  Toate comenzile →
                </Link>
              </div>
              {recent.length === 0 ? (
                <p className="py-8 text-center text-sm text-slate-500">Nicio comandă încă.</p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {recent.map((o) => (
                    <li key={o.id} className="flex items-center justify-between py-2.5 text-sm">
                      <div>
                        <p className="font-medium text-slate-800">
                          #{o.id} · {o.userFullName || o.userEmail}
                        </p>
                        <p className="text-xs text-slate-400">{formatDate(o.createdAt)}</p>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="font-medium text-slate-700">{formatPrice(o.totalAmount)}</span>
                        <span
                          className={`badge ${STATUS_STYLE[o.status] || 'bg-slate-100 text-slate-700'}`}
                        >
                          {o.status}
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
