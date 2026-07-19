import { useEffect, useState } from 'react';
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
import { formatPrice } from '../../utils/format';

const COLORS = ['#f59e0b', '#3b82f6', '#6366f1', '#22c55e', '#ef4444'];

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
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminService
      .dashboard()
      .then(setStats)
      .catch(() => setStats(null))
      .finally(() => setLoading(false));
  }, []);

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
              <h2 className="mb-4 font-semibold text-slate-800">Vânzări pe zile</h2>
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={stats.salesByDay}>
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

          <div className="card p-5">
            <h2 className="mb-4 font-semibold text-slate-800">Top produse vândute</h2>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={stats.topProducts}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} angle={-15} textAnchor="end" height={60} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="unitsSold" name="Unități vândute" fill="#3b82f6" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </div>
  );
}
