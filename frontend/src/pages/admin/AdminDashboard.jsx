import { useEffect, useId, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AreaChart,
  Area,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import adminService from '../../api/adminService';
import AdminNav, { Icon } from '../../components/AdminNav';
import Spinner from '../../components/Spinner';
import { formatPrice, formatRelative, resolveImage } from '../../utils/format';

// Real order statuses only — there is no "Returned" state in this system, so the
// legend below only ever shows the five that can actually occur.
const STATUS_COLOR = {
  PENDING: '#f59e0b',
  PAID: '#3b82f6',
  SHIPPED: '#6366f1',
  DELIVERED: '#22c55e',
  CANCELLED: '#ef4444',
};
const STATUS_LABEL = {
  PENDING: 'În așteptare',
  PAID: 'Plătită',
  SHIPPED: 'Expediată',
  DELIVERED: 'Livrată',
  CANCELLED: 'Anulată',
};

const CARD_ACCENT = {
  blue: { bg: 'bg-blue-50', text: 'text-blue-600', line: '#3b82f6' },
  violet: { bg: 'bg-violet-50', text: 'text-violet-600', line: '#8b5cf6' },
  amber: { bg: 'bg-amber-50', text: 'text-amber-600', line: '#f59e0b' },
  brand: { bg: 'bg-brand-50', text: 'text-brand-600', line: '#0c7857' },
};

// Icons the shared admin set (sidebar) doesn't need but this activity feed does.
const LOCAL_ICONS = {
  image: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="8.5" cy="10" r="1.5" />
      <path d="M21 16.5 16 11l-4.5 5.5M11 16.5 9 14l-6 4.5" />
    </svg>
  ),
  trash: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M4 7h16" />
      <path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2" />
      <path d="M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  ),
  refresh: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M4 4v5h5" />
      <path d="M20 20v-5h-5" />
      <path d="M5.5 9A7 7 0 0 1 19 12M18.5 15A7 7 0 0 1 5 12" />
    </svg>
  ),
  alert: (props) => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M12 3 2 20h20L12 3z" />
      <path d="M12 10v4" />
      <path d="M12 17h.01" />
    </svg>
  ),
};

function ActivityIcon({ name, className }) {
  const Local = LOCAL_ICONS[name];
  if (Local) return <Local className={className} aria-hidden="true" />;
  return <Icon name={name} className={className} />;
}

// Every admin action the backend already records (see AuditService), mapped to
// an icon, a tone, a human label and which activity-feed tab it belongs under.
const ACTIVITY_META = {
  PRODUCT_CREATED: { icon: 'box', tone: 'text-green-600 bg-green-50', label: 'Produs adăugat', category: 'products' },
  PRODUCT_UPDATED: { icon: 'box', tone: 'text-blue-600 bg-blue-50', label: 'Produs actualizat', category: 'products' },
  PRODUCT_IMAGE_ADDED: { icon: 'image', tone: 'text-indigo-600 bg-indigo-50', label: 'Imagine adăugată', category: 'products' },
  PRODUCT_IMAGE_UPDATED: { icon: 'image', tone: 'text-indigo-600 bg-indigo-50', label: 'Imagine actualizată', category: 'products' },
  PRODUCT_IMAGE_PRIMARY: { icon: 'image', tone: 'text-indigo-600 bg-indigo-50', label: 'Imagine principală schimbată', category: 'products' },
  PRODUCT_IMAGE_DELETED: { icon: 'trash', tone: 'text-red-600 bg-red-50', label: 'Imagine ștearsă', category: 'products' },
  PRODUCT_DELETED: { icon: 'trash', tone: 'text-red-600 bg-red-50', label: 'Produs șters', category: 'products' },
  PRODUCTS_BULK_DELETED: { icon: 'trash', tone: 'text-red-600 bg-red-50', label: 'Produse șterse', category: 'products' },
  ORDER_CREATED: { icon: 'cart', tone: 'text-green-600 bg-green-50', label: 'Comandă nouă', category: 'orders' },
  ORDER_STATUS_CHANGED: { icon: 'refresh', tone: 'text-amber-600 bg-amber-50', label: 'Status comandă schimbat', category: 'orders' },
  ORDER_DELETED: { icon: 'trash', tone: 'text-red-600 bg-red-50', label: 'Comandă ștearsă', category: 'orders' },
  COMPANY_SETTINGS_UPDATED: { icon: 'gear', tone: 'text-slate-600 bg-slate-100', label: 'Date firmă actualizate', category: 'system' },
  // Feature #10 — "VÂNDUT" quick sale from the products page.
  PRODUCT_SOLD: { icon: 'cart', tone: 'text-green-600 bg-green-50', label: 'Vânzare directă (VÂNDUT)', category: 'orders' },
};
const DEFAULT_ACTIVITY_META = { icon: 'document', tone: 'text-slate-600 bg-slate-100', label: null, category: 'system' };

const ACTIVITY_FILTERS = [
  { key: 'all', label: 'Toate' },
  { key: 'products', label: 'Produse' },
  { key: 'orders', label: 'Comenzi' },
  { key: 'system', label: 'Sistem' },
];

const ENTITY_LINK = {
  Product: '/admin/products',
  Order: '/admin/orders',
  CompanySettings: '/admin/settings',
  User: '/admin/users',
};

const SALES_VIEWS = [
  { key: 'day', label: 'Zile' },
  { key: 'month', label: 'Luni' },
  { key: 'year', label: 'Ani' },
];

/** Aggregates a {date, value} series (already daily) into day/month/year buckets. */
function aggregateByView(points, view, valueKey) {
  if (view === 'day') return points;
  const map = new Map();
  for (const p of points) {
    const key = view === 'month' ? String(p.date).slice(0, 7) : String(p.date).slice(0, 4);
    map.set(key, (map.get(key) || 0) + Number(p[valueKey] || 0));
  }
  return Array.from(map, ([date, value]) => ({ date, [valueKey]: value }));
}

/** A trailing moving average of `key` over the given window, added as `avg` on each point. */
function withMovingAverage(data, key, window) {
  return data.map((point, idx) => {
    const start = Math.max(0, idx - window + 1);
    const slice = data.slice(start, idx + 1);
    const avg = slice.reduce((sum, p) => sum + Number(p[key] || 0), 0) / slice.length;
    return { ...point, avg };
  });
}

/** Small single-series trend line — used by every stat card and every top-product row. */
function Sparkline({ data, color }) {
  const rawId = useId().replace(/[^a-zA-Z0-9]/g, '');
  const gradId = `spark-${rawId}`;
  return (
    <ResponsiveContainer width="100%" height={36}>
      <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.35} />
            <stop offset="100%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <Area
          type="monotone"
          dataKey="v"
          stroke={color}
          strokeWidth={1.75}
          fill={`url(#${gradId})`}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

function StatCard({ label, value, icon, accent, trend, loading }) {
  const colors = CARD_ACCENT[accent];
  const sparkData = (trend?.series || []).slice(-7).map((v, i) => ({ i, v }));
  const changePct = trend?.changePct;
  const isUp = (changePct ?? 0) >= 0;

  if (loading) {
    return (
      <div className="card animate-pulse p-5">
        <div className="mb-4 h-12 w-12 rounded-lg bg-slate-100" />
        <div className="mb-2 h-3 w-20 rounded bg-slate-100" />
        <div className="h-6 w-24 rounded bg-slate-100" />
      </div>
    );
  }

  return (
    <div className="card flex flex-col gap-3 p-5 transition-transform duration-150 hover:scale-[1.02]">
      <div className="flex items-center gap-4">
        <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-lg ${colors.bg} ${colors.text}`}>
          <Icon name={icon} className="h-6 w-6" />
        </div>
        <div className="min-w-0">
          <p className="text-sm text-slate-500">{label}</p>
          <p className="truncate text-2xl font-bold text-slate-900">{value}</p>
        </div>
      </div>
      <div className="flex items-center justify-between gap-3">
        {changePct != null ? (
          <span className={`flex shrink-0 items-center gap-1 text-xs font-semibold ${isUp ? 'text-green-600' : 'text-red-600'}`}>
            <span aria-hidden="true">{isUp ? '▲' : '▼'}</span>
            {Math.abs(changePct)}% față de săpt. trecută
          </span>
        ) : (
          <span />
        )}
        <div className="w-20">
          <Sparkline data={sparkData} color={colors.line} />
        </div>
      </div>
    </div>
  );
}

function SalesTooltip({ active, payload, label }) {
  if (!active || !payload || !payload.length) return null;
  const amount = payload.find((p) => p.dataKey === 'amount')?.value;
  const avg = payload.find((p) => p.dataKey === 'avg')?.value;
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <p className="mb-1 font-semibold text-slate-700">{label}</p>
      {amount != null && (
        <p className="text-slate-600">
          Vânzări: <span className="font-medium text-slate-900">{formatPrice(amount)}</span>
        </p>
      )}
      {avg != null && <p className="text-slate-500">Medie mobilă: {formatPrice(avg)}</p>}
    </div>
  );
}

function OrdersTooltip({ active, payload, label }) {
  if (!active || !payload || !payload.length) return null;
  const count = payload.find((p) => p.dataKey === 'count')?.value;
  if (count == null) return null;
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <p className="mb-1 font-semibold text-slate-700">{label}</p>
      <p className="text-slate-600">
        Comenzi: <span className="font-medium text-slate-900">{count}</span>
      </p>
    </div>
  );
}

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [activity, setActivity] = useState([]);
  const [loading, setLoading] = useState(true);
  const [salesView, setSalesView] = useState('day');
  const [ordersView, setOrdersView] = useState('day');
  const [activityFilter, setActivityFilter] = useState('all');

  useEffect(() => {
    Promise.all([
      adminService.dashboard().then(setStats).catch(() => setStats(null)),
      adminService
        .listAuditLogs({ page: 0, size: 10 })
        .then((d) => setActivity(d.content || []))
        .catch(() => setActivity([])),
    ]).finally(() => setLoading(false));
  }, []);

  const salesData = useMemo(() => {
    const byDay = stats?.salesByDay || [];
    if (salesView === 'day') return withMovingAverage(byDay, 'amount', 7);
    const map = new Map();
    for (const p of byDay) {
      const key = salesView === 'month' ? String(p.date).slice(0, 7) : String(p.date).slice(0, 4);
      map.set(key, (map.get(key) || 0) + Number(p.amount || 0));
    }
    return Array.from(map, ([date, amount]) => ({ date, amount }));
  }, [stats, salesView]);

  const ordersData = useMemo(
    () => aggregateByView(stats?.ordersByDay || [], ordersView, 'count'),
    [stats, ordersView]
  );

  const totalForStatus = (stats?.ordersByStatus || []).reduce((sum, s) => sum + s.count, 0);

  const monthly = stats?.monthlyRevenue;
  const monthlyUp = (monthly?.changePct ?? 0) >= 0;

  const filteredActivity = useMemo(() => {
    if (activityFilter === 'all') return activity;
    return activity.filter((a) => (ACTIVITY_META[a.action]?.category || 'system') === activityFilter);
  }, [activity, activityFilter]);

  return (
    <div>
      <AdminNav />
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Panou de administrare</h1>

      <div className="space-y-6">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            loading={loading}
            label="Utilizatori"
            value={stats?.totalUsers}
            icon="users"
            accent="blue"
            trend={stats?.usersTrend}
          />
          <StatCard
            loading={loading}
            label="Produse"
            value={stats?.totalProducts}
            icon="box"
            accent="violet"
            trend={stats?.productsTrend}
          />
          <StatCard
            loading={loading}
            label="Comenzi"
            value={stats?.totalOrders}
            icon="cart"
            accent="amber"
            trend={stats?.ordersTrend}
          />
          <StatCard
            loading={loading}
            label="Venit total"
            value={stats ? formatPrice(stats.totalRevenue) : ''}
            icon="banknote"
            accent="brand"
            trend={stats?.revenueTrend}
          />
        </div>

        {loading ? (
          <Spinner />
        ) : !stats ? (
          <p className="text-slate-500">Statisticile nu au putut fi încărcate.</p>
        ) : (
          <>
            <div className="grid gap-6 lg:grid-cols-2">
              <div className="card p-5">
                <div className="mb-1 flex items-center justify-between">
                  <h2 className="font-semibold text-slate-800">Vânzări</h2>
                  <div className="flex overflow-hidden rounded-lg border border-slate-200 text-sm">
                    {SALES_VIEWS.map((v) => (
                      <button
                        key={v.key}
                        type="button"
                        className={`px-3 py-1 ${salesView === v.key ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}
                        onClick={() => setSalesView(v.key)}
                      >
                        {v.label}
                      </button>
                    ))}
                  </div>
                </div>
                {/* Feature #9 — "venit lunar + comparație cu luna precedentă": a
                    calendar-month comparison, distinct from the 7-vs-7-day badges
                    on the stat cards above. */}
                {monthly && (
                  <p className="mb-3 text-sm text-slate-500">
                    Venit luna aceasta: <span className="font-semibold text-slate-800">{formatPrice(monthly.current)}</span>
                    {monthly.changePct != null && (
                      <span className={`ml-2 font-semibold ${monthlyUp ? 'text-green-600' : 'text-red-600'}`}>
                        {monthlyUp ? '▲' : '▼'} {Math.abs(monthly.changePct)}% față de luna trecută
                      </span>
                    )}
                    {' '}
                    <span className="text-slate-400">(luna trecută: {formatPrice(monthly.previous)})</span>
                  </p>
                )}
                <ResponsiveContainer width="100%" height={280}>
                  <LineChart data={salesData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} />
                    <Tooltip content={<SalesTooltip />} />
                    {salesView === 'day' && <Legend wrapperStyle={{ fontSize: 12 }} />}
                    <Line
                      type="monotone"
                      dataKey="amount"
                      name="Vânzări"
                      stroke="#2563eb"
                      strokeWidth={2}
                      dot={false}
                      animationEasing="ease-out"
                    />
                    {salesView === 'day' && (
                      <Line
                        type="monotone"
                        dataKey="avg"
                        name="Medie mobilă (7 zile)"
                        stroke="#f59e0b"
                        strokeWidth={2}
                        strokeDasharray="4 3"
                        dot={false}
                        animationEasing="ease-out"
                      />
                    )}
                  </LineChart>
                </ResponsiveContainer>
              </div>

              <div className="card p-5">
                <h2 className="mb-4 font-semibold text-slate-800">Comenzi după status</h2>
                {!stats.ordersByStatus || stats.ordersByStatus.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-500">Nicio comandă încă.</p>
                ) : (
                  <>
                    <div className="relative">
                      <ResponsiveContainer width="100%" height={220}>
                        <PieChart>
                          <Pie
                            data={stats.ordersByStatus}
                            dataKey="count"
                            nameKey="status"
                            innerRadius={62}
                            outerRadius={96}
                            paddingAngle={2}
                          >
                            {stats.ordersByStatus.map((entry) => (
                              <Cell key={entry.status} fill={STATUS_COLOR[entry.status] || '#94a3b8'} />
                            ))}
                          </Pie>
                          <Tooltip
                            formatter={(value, name) => [`${value} comenzi`, STATUS_LABEL[name] || name]}
                          />
                        </PieChart>
                      </ResponsiveContainer>
                      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                        <p className="text-2xl font-bold text-slate-900">{totalForStatus}</p>
                        <p className="text-xs text-slate-500">comenzi</p>
                      </div>
                    </div>
                    <ul className="mt-2 space-y-1.5">
                      {stats.ordersByStatus.map((s) => (
                        <li key={s.status} className="flex items-center justify-between text-sm">
                          <span className="flex items-center gap-2 text-slate-600">
                            <span
                              className="h-2.5 w-2.5 rounded-full"
                              style={{ backgroundColor: STATUS_COLOR[s.status] || '#94a3b8' }}
                              aria-hidden="true"
                            />
                            {STATUS_LABEL[s.status] || s.status}
                          </span>
                          <span className="font-medium text-slate-700">
                            {s.count} · {totalForStatus ? Math.round((s.count / totalForStatus) * 100) : 0}%
                          </span>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </div>
            </div>

            {/* Feature #9 — "grafic evoluție comenzi": order-count history, with the
                same zile/luni/ani granularity as the Vânzări chart above but tracking
                order volume rather than revenue. */}
            <div className="card p-5">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="font-semibold text-slate-800">Evoluția comenzilor</h2>
                <div className="flex overflow-hidden rounded-lg border border-slate-200 text-sm">
                  {SALES_VIEWS.map((v) => (
                    <button
                      key={v.key}
                      type="button"
                      className={`px-3 py-1 ${ordersView === v.key ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}
                      onClick={() => setOrdersView(v.key)}
                    >
                      {v.label}
                    </button>
                  ))}
                </div>
              </div>
              {ordersData.length === 0 ? (
                <p className="py-8 text-center text-sm text-slate-500">Nicio comandă încă.</p>
              ) : (
                <ResponsiveContainer width="100%" height={240}>
                  <AreaChart data={ordersData}>
                    <defs>
                      <linearGradient id="orders-evolution-fill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#8b5cf6" stopOpacity={0.3} />
                        <stop offset="100%" stopColor="#8b5cf6" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                    <YAxis tick={{ fontSize: 12 }} allowDecimals={false} />
                    <Tooltip content={<OrdersTooltip />} />
                    <Area
                      type="monotone"
                      dataKey="count"
                      name="Comenzi"
                      stroke="#8b5cf6"
                      strokeWidth={2}
                      fill="url(#orders-evolution-fill)"
                      isAnimationActive={false}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              )}
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
              <div className="card p-5">
                <h2 className="mb-4 font-semibold text-slate-800">Top produse vândute</h2>
                {!stats.topProducts || stats.topProducts.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-500">Niciun produs vândut încă.</p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {stats.topProducts.map((p, idx) => {
                      const spark = (p.dailyUnits || []).map((v, i) => ({ i, v }));
                      const trendUp = (p.trendPct ?? 0) >= 0;
                      const rankStyle =
                        idx === 0
                          ? 'bg-amber-100 text-amber-700'
                          : idx === 1
                            ? 'bg-slate-200 text-slate-700'
                            : idx === 2
                              ? 'bg-orange-100 text-orange-700'
                              : 'bg-slate-100 text-slate-500';
                      return (
                        <li key={p.productId} className="flex items-center gap-3 py-3">
                          <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${rankStyle}`}>
                            {idx + 1}
                          </span>
                          <img
                            src={resolveImage(p.imageUrl)}
                            alt={p.name}
                            loading="lazy"
                            className="h-10 w-10 shrink-0 rounded object-cover"
                          />
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium text-slate-800">{p.name}</p>
                            <p className="text-xs text-slate-500">
                              {p.unitsSold} unități · {formatPrice(p.revenue)}
                            </p>
                          </div>
                          <div className="hidden w-16 sm:block">
                            <Sparkline data={spark} color="#3b82f6" />
                          </div>
                          {p.trendPct != null && (
                            <span className={`shrink-0 text-xs font-semibold ${trendUp ? 'text-green-600' : 'text-red-600'}`}>
                              {trendUp ? '▲' : '▼'} {Math.abs(p.trendPct)}%
                            </span>
                          )}
                          <Link to="/admin/products" className="shrink-0 text-xs font-medium text-brand-600 hover:underline">
                            Vezi produs
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>

              <div className="card p-5">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <h2 className="font-semibold text-slate-800">Activitate recentă</h2>
                  <Link to="/admin/audit" className="text-sm text-brand-600 hover:underline">
                    Tot jurnalul →
                  </Link>
                </div>
                <div className="mb-3 flex flex-wrap gap-1.5">
                  {ACTIVITY_FILTERS.map((f) => (
                    <button
                      key={f.key}
                      type="button"
                      onClick={() => setActivityFilter(f.key)}
                      className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                        activityFilter === f.key ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      }`}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>
                {filteredActivity.length === 0 ? (
                  <p className="py-8 text-center text-sm text-slate-500">Nicio activitate încă.</p>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {filteredActivity.map((a) => {
                      const meta = ACTIVITY_META[a.action] || DEFAULT_ACTIVITY_META;
                      const linkTo = ENTITY_LINK[a.entityType];
                      return (
                        <li key={a.id} className="flex items-start gap-3 py-2.5 text-sm">
                          <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${meta.tone}`}>
                            <ActivityIcon name={meta.icon} className="h-4 w-4" />
                          </span>
                          <div className="min-w-0 flex-1">
                            <p className="text-slate-800">
                              {meta.label || a.action}
                              {a.entityId ? <span className="text-slate-400"> · #{a.entityId}</span> : null}
                            </p>
                            {a.details && <p className="truncate text-xs text-slate-500">{a.details}</p>}
                            <p className="text-xs text-slate-400">
                              {formatRelative(a.createdAt)} · {a.actor}
                            </p>
                          </div>
                          {linkTo && (
                            <Link to={linkTo} className="shrink-0 text-xs font-medium text-brand-600 hover:underline">
                              Deschide
                            </Link>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </div>

            {stats.lowStockProducts && stats.lowStockProducts.length > 0 && (
              <div className="card p-5">
                <div className="mb-3 flex items-center gap-2">
                  <ActivityIcon name="alert" className="h-4 w-4 text-amber-500" />
                  <h2 className="font-semibold text-slate-800">Stoc scăzut</h2>
                </div>
                <div className="flex gap-3 overflow-x-auto pb-1">
                  {stats.lowStockProducts.map((p) => (
                    <Link
                      key={p.productId}
                      to="/admin/products"
                      className="flex w-48 shrink-0 items-center gap-2 rounded-lg border border-slate-200 p-2 transition hover:border-brand-300 hover:bg-brand-50/40"
                    >
                      <img
                        src={resolveImage(p.imageUrl)}
                        alt={p.name}
                        loading="lazy"
                        className="h-9 w-9 shrink-0 rounded object-cover"
                      />
                      <div className="min-w-0">
                        <p className="truncate text-xs font-medium text-slate-800">{p.name}</p>
                        <p className={`text-xs font-semibold ${p.stockQuantity === 0 ? 'text-red-600' : 'text-amber-600'}`}>
                          {p.stockQuantity === 0 ? 'Stoc epuizat' : `${p.stockQuantity} în stoc`}
                        </p>
                      </div>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
