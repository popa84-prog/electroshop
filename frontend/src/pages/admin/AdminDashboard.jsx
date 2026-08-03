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
import {
  GeoIcon,
  HoloLoader,
  HoloSkeleton,
  HoloTooltip,
  Reveal,
  SectionHeader,
  StatTile,
  XXChartDefs,
  XX_GLOW_FILTER,
  XX_SERIES_AMBER,
  XX_SERIES_BLUE,
  XX_SERIES_PURPLE,
  XX_STATUS,
  XX_STATUS_UNKNOWN,
  xxAxisProps,
  xxCursor,
  xxGridProps,
  xxLegendProps,
} from '../../components/xxii';
import { formatPrice, formatRelative, resolveImage } from '../../utils/format';

/**
 * XXII — TASK 6: the Quantum Control Center.
 *
 * The data contract with the backend is unchanged; what changed is the whole
 * visual and motion layer:
 *
 *   - The four headline metrics are `StatTile`s: they count up on first load
 *     and run a scan sweep when a value actually changes, which is the
 *     "animated refresh" from the brief. Their trend is stated with an arrow
 *     glyph and a sign as well as a colour.
 *   - Every chart draws from the shared, validated palette in `ChartTheme`
 *     instead of the light-theme hexes this file used to carry (`#e2e8f0`
 *     grid, `#2563eb` line, `#8b5cf6` area). Neon is added with a glow filter
 *     on the marks, never by brightening a mark past the legible lightness
 *     band.
 *   - Panels reveal on scroll, staggered, and the whole page is a modular grid
 *     in the TASK 9 sense: each panel is a self-contained module with its own
 *     header, empty state and controls.
 *
 * Order statuses keep dedicated reserved colours, and every place they appear
 * — the donut legend, the tooltip — prints the status name in words. A donut is
 * an all-pairs comparison, so those five were validated against each other on
 * that basis rather than merely as neighbours.
 */

// Real order statuses only — there is no "Returned" state in this system, so the
// legend below only ever shows the five that can actually occur.
const STATUS_COLOR = {
  PENDING: XX_STATUS.pending,
  PAID: XX_STATUS.paid,
  SHIPPED: XX_STATUS.shipped,
  DELIVERED: XX_STATUS.delivered,
  CANCELLED: XX_STATUS.cancelled,
};
const STATUS_LABEL = {
  PENDING: 'În așteptare',
  PAID: 'Plătită',
  SHIPPED: 'Expediată',
  DELIVERED: 'Livrată',
  CANCELLED: 'Anulată',
};

/** Which StatTile tone and sparkline colour each headline metric wears. */
const CARD_ACCENT = {
  blue: { tone: 'blue', line: XX_SERIES_BLUE },
  violet: { tone: 'purple', line: XX_SERIES_PURPLE },
  amber: { tone: 'warning', line: XX_SERIES_AMBER },
  brand: { tone: 'aqua', line: '#0e9fb0' },
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
// The tones are XXII surface classes now: a translucent tinted disc with a neon
// edge, rather than the flat pastel fills the light theme used.
const TONE_GOOD = 'border border-[rgba(110,247,168,0.4)] bg-[rgba(110,247,168,0.12)] text-[#b8ffd6]';
const TONE_INFO = 'border border-[rgba(46,123,255,0.4)] bg-[rgba(46,123,255,0.14)] text-[#b7d0ff]';
const TONE_ACCENT = 'border border-[rgba(122,60,255,0.4)] bg-[rgba(122,60,255,0.16)] text-[#d5c2ff]';
const TONE_WARN = 'border border-[rgba(255,194,75,0.4)] bg-[rgba(255,194,75,0.12)] text-[#ffe0a3]';
const TONE_BAD = 'border border-[rgba(255,84,112,0.4)] bg-[rgba(255,84,112,0.12)] text-[#ffc2cc]';
const TONE_MUTED = 'border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.06)] text-[color:var(--xx-ink-muted)]';

const ACTIVITY_META = {
  PRODUCT_CREATED: { icon: 'box', tone: TONE_GOOD, label: 'Produs adăugat', category: 'products' },
  PRODUCT_UPDATED: { icon: 'box', tone: TONE_INFO, label: 'Produs actualizat', category: 'products' },
  PRODUCT_IMAGE_ADDED: { icon: 'image', tone: TONE_ACCENT, label: 'Imagine adăugată', category: 'products' },
  PRODUCT_IMAGE_UPDATED: { icon: 'image', tone: TONE_ACCENT, label: 'Imagine actualizată', category: 'products' },
  PRODUCT_IMAGE_PRIMARY: { icon: 'image', tone: TONE_ACCENT, label: 'Imagine principală schimbată', category: 'products' },
  PRODUCT_IMAGE_DELETED: { icon: 'trash', tone: TONE_BAD, label: 'Imagine ștearsă', category: 'products' },
  PRODUCT_DELETED: { icon: 'trash', tone: TONE_BAD, label: 'Produs șters', category: 'products' },
  PRODUCTS_BULK_DELETED: { icon: 'trash', tone: TONE_BAD, label: 'Produse șterse', category: 'products' },
  ORDER_CREATED: { icon: 'cart', tone: TONE_GOOD, label: 'Comandă nouă', category: 'orders' },
  ORDER_STATUS_CHANGED: { icon: 'refresh', tone: TONE_WARN, label: 'Status comandă schimbat', category: 'orders' },
  ORDER_DELETED: { icon: 'trash', tone: TONE_BAD, label: 'Comandă ștearsă', category: 'orders' },
  COMPANY_SETTINGS_UPDATED: { icon: 'gear', tone: TONE_MUTED, label: 'Date firmă actualizate', category: 'system' },
  // Feature #10 — "VÂNDUT" quick sale from the products page.
  PRODUCT_SOLD: { icon: 'cart', tone: TONE_GOOD, label: 'Vânzare directă (VÂNDUT)', category: 'orders' },
};
const DEFAULT_ACTIVITY_META = { icon: 'document', tone: TONE_MUTED, label: null, category: 'system' };

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

/** A segmented control shared by the two time-granularity switchers. */
function ViewSwitch({ value, onChange, options, label }) {
  return (
    <div
      role="group"
      aria-label={label}
      className="flex overflow-hidden rounded-full border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.04)] text-sm"
    >
      {options.map((option) => {
        const active = value === option.key;
        return (
          <button
            key={option.key}
            type="button"
            aria-pressed={active}
            className={`px-3.5 py-1.5 transition-all duration-xx ease-xx ${
              active
                ? 'bg-[rgba(34,232,245,0.16)] font-semibold text-[color:var(--xx-ink)] shadow-[inset_0_0_22px_-8px_rgba(34,232,245,0.9)]'
                : 'text-[color:var(--xx-ink-muted)] hover:text-[color:var(--xx-ink)]'
            }`}
            onClick={() => onChange(option.key)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/** Small single-series trend line — used by every stat tile and every top-product row. */
function Sparkline({ data, color }) {
  const rawId = useId().replace(/[^a-zA-Z0-9]/g, '');
  const gradId = `spark-${rawId}`;
  return (
    <ResponsiveContainer width="100%" height={36}>
      <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.4} />
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

/**
 * One headline metric. Wraps `StatTile` so the dashboard can also show a
 * 7-point sparkline in the tile's footer slot — the tile itself stays generic.
 */
function MetricTile({ label, value, format, icon, accent, trend, loading }) {
  const colors = CARD_ACCENT[accent] || CARD_ACCENT.blue;

  if (loading) {
    return <HoloSkeleton height="9.5rem" label={`Se încarcă ${label}`} />;
  }

  const sparkData = (trend?.series || []).slice(-7).map((v, i) => ({ i, v }));
  const changePct = trend?.changePct;

  return (
    <StatTile
      label={label}
      value={typeof value === 'number' ? value : (value ?? 0)}
      format={format}
      tone={colors.tone}
      icon={<GeoIcon name={icon} className="h-5 w-5" accent="currentColor" />}
      trend={typeof changePct === 'number' ? changePct : null}
      trendLabel={typeof changePct === 'number' ? 'față de săpt. trecută' : undefined}
      footer={
        sparkData.length > 1 ? (
          <div aria-hidden="true" className="h-9">
            <Sparkline data={sparkData} color={colors.line} />
          </div>
        ) : null
      }
    />
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
    let cancelled = false;
    Promise.all([
      adminService
        .dashboard()
        .then((data) => {
          if (!cancelled) setStats(data);
        })
        .catch(() => {
          if (!cancelled) setStats(null);
        }),
      adminService
        .listAuditLogs({ page: 0, size: 10 })
        .then((d) => {
          if (!cancelled) setActivity(d.content || []);
        })
        .catch(() => {
          if (!cancelled) setActivity([]);
        }),
    ]).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => {
      cancelled = true;
    };
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

      <SectionHeader
        eyebrow="Quantum Control Center"
        title="Panou de administrare"
        subtitle="Starea magazinului în timp real: metrici, evoluții, distribuția comenzilor și activitatea recentă."
        as="h1"
      />

      <div className="space-y-6">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricTile
            loading={loading}
            label="Utilizatori"
            value={stats?.totalUsers}
            icon="user"
            accent="blue"
            trend={stats?.usersTrend}
          />
          <MetricTile
            loading={loading}
            label="Produse"
            value={stats?.totalProducts}
            icon="box"
            accent="violet"
            trend={stats?.productsTrend}
          />
          <MetricTile
            loading={loading}
            label="Comenzi"
            value={stats?.totalOrders}
            icon="cart"
            accent="amber"
            trend={stats?.ordersTrend}
          />
          <MetricTile
            loading={loading}
            label="Venit total"
            value={stats ? Number(stats.totalRevenue) : 0}
            format={(value) => formatPrice(value)}
            icon="coins"
            accent="brand"
            trend={stats?.revenueTrend}
          />
        </div>

        {loading ? (
          <div className="card card-static grid place-items-center p-12">
            <HoloLoader size="lg" label="Se încarcă statisticile" />
          </div>
        ) : !stats ? (
          <div className="card card-static p-8 text-center">
            <p className="text-sm xx-ink-muted">Statisticile nu au putut fi încărcate.</p>
          </div>
        ) : (
          <>
            <div className="grid gap-6 lg:grid-cols-2">
              {/* Module — Vânzări */}
              <Reveal>
                <div className="card h-full p-5">
                  <div className="mb-1 flex flex-wrap items-center justify-between gap-3">
                    <h2 className="font-display text-lg font-semibold text-[color:var(--xx-ink)]">Vânzări</h2>
                    <ViewSwitch
                      value={salesView}
                      onChange={setSalesView}
                      options={SALES_VIEWS}
                      label="Granularitate vânzări"
                    />
                  </div>

                  {/* Feature #9 — "venit lunar + comparație cu luna precedentă": a
                      calendar-month comparison, distinct from the 7-vs-7-day badges
                      on the tiles above. */}
                  {monthly && (
                    <p className="mb-3 text-sm xx-ink-muted">
                      Venit luna aceasta:{' '}
                      <span className="font-semibold text-[color:var(--xx-ink)]">{formatPrice(monthly.current)}</span>
                      {monthly.changePct != null && (
                        <span
                          className="ml-2 font-semibold"
                          style={{ color: monthlyUp ? 'var(--xx-lime)' : 'var(--xx-red)' }}
                        >
                          <span aria-hidden="true">{monthlyUp ? '▲' : '▼'}</span> {Math.abs(monthly.changePct)}% față
                          de luna trecută
                        </span>
                      )}{' '}
                      <span className="xx-ink-dim">(luna trecută: {formatPrice(monthly.previous)})</span>
                    </p>
                  )}

                  {salesData.length === 0 ? (
                    <p className="py-12 text-center text-sm xx-ink-muted">Nicio vânzare înregistrată încă.</p>
                  ) : (
                    <ResponsiveContainer width="100%" height={280}>
                      <LineChart data={salesData}>
                        <XXChartDefs />
                        <CartesianGrid {...xxGridProps} />
                        <XAxis dataKey="date" {...xxAxisProps} />
                        <YAxis {...xxAxisProps} />
                        <Tooltip
                          cursor={xxCursor}
                          content={<HoloTooltip format={(value) => formatPrice(value)} />}
                        />
                        {salesView === 'day' && <Legend {...xxLegendProps} />}
                        <Line
                          type="monotone"
                          dataKey="amount"
                          name="Vânzări"
                          stroke={XX_SERIES_BLUE}
                          strokeWidth={2}
                          dot={false}
                          activeDot={{ r: 5, fill: XX_SERIES_BLUE, stroke: '#04050c', strokeWidth: 2 }}
                          filter={XX_GLOW_FILTER}
                          animationEasing="ease-out"
                        />
                        {salesView === 'day' && (
                          <Line
                            type="monotone"
                            dataKey="avg"
                            name="Medie mobilă (7 zile)"
                            stroke={XX_SERIES_AMBER}
                            strokeWidth={2}
                            strokeDasharray="4 3"
                            dot={false}
                            activeDot={{ r: 4, fill: XX_SERIES_AMBER, stroke: '#04050c', strokeWidth: 2 }}
                            animationEasing="ease-out"
                          />
                        )}
                      </LineChart>
                    </ResponsiveContainer>
                  )}
                </div>
              </Reveal>

              {/* Module — Comenzi după status */}
              <Reveal delay={80}>
                <div className="card h-full p-5">
                  <h2 className="mb-4 font-display text-lg font-semibold text-[color:var(--xx-ink)]">
                    Comenzi după status
                  </h2>
                  {!stats.ordersByStatus || stats.ordersByStatus.length === 0 ? (
                    <p className="py-12 text-center text-sm xx-ink-muted">Nicio comandă încă.</p>
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
                              stroke="#0a0b1e"
                              strokeWidth={2}
                            >
                              {stats.ordersByStatus.map((entry) => (
                                <Cell key={entry.status} fill={STATUS_COLOR[entry.status] || XX_STATUS_UNKNOWN} />
                              ))}
                            </Pie>
                            <Tooltip
                              content={
                                <HoloTooltip
                                  rows={(payload) =>
                                    payload.map((entry) => ({
                                      label: STATUS_LABEL[entry.name] || entry.name,
                                      value: `${entry.value} ${entry.value === 1 ? 'comandă' : 'comenzi'}`,
                                      color: entry.payload?.fill,
                                    }))
                                  }
                                />
                              }
                            />
                          </PieChart>
                        </ResponsiveContainer>
                        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                          <p
                            className="font-display text-3xl font-bold text-[color:var(--xx-ink)]"
                            style={{ textShadow: '0 0 26px rgba(34,232,245,0.45)' }}
                          >
                            {totalForStatus}
                          </p>
                          <p className="text-xs uppercase tracking-[0.16em] xx-ink-dim">comenzi</p>
                        </div>
                      </div>

                      {/* The legend is the secondary encoding: every slice is named
                          in words with its count and share, so the donut never
                          depends on telling two hues apart. */}
                      <ul className="mt-2 space-y-1.5">
                        {stats.ordersByStatus.map((s) => (
                          <li key={s.status} className="flex items-center justify-between text-sm">
                            <span className="flex items-center gap-2 xx-ink-muted">
                              <span
                                className="h-2.5 w-2.5 rounded-full"
                                style={{
                                  backgroundColor: STATUS_COLOR[s.status] || XX_STATUS_UNKNOWN,
                                  boxShadow: `0 0 10px ${STATUS_COLOR[s.status] || XX_STATUS_UNKNOWN}`,
                                }}
                                aria-hidden="true"
                              />
                              {STATUS_LABEL[s.status] || s.status}
                            </span>
                            <span className="font-semibold tabular-nums text-[color:var(--xx-ink)]">
                              {s.count} · {totalForStatus ? Math.round((s.count / totalForStatus) * 100) : 0}%
                            </span>
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              </Reveal>
            </div>

            {/* Feature #9 — "grafic evoluție comenzi": order-count history, with the
                same zile/luni/ani granularity as the Vânzări chart above but tracking
                order volume rather than revenue. */}
            <Reveal>
              <div className="card p-5">
                <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                  <h2 className="font-display text-lg font-semibold text-[color:var(--xx-ink)]">
                    Evoluția comenzilor
                  </h2>
                  <ViewSwitch
                    value={ordersView}
                    onChange={setOrdersView}
                    options={SALES_VIEWS}
                    label="Granularitate comenzi"
                  />
                </div>
                {ordersData.length === 0 ? (
                  <p className="py-12 text-center text-sm xx-ink-muted">Nicio comandă încă.</p>
                ) : (
                  <ResponsiveContainer width="100%" height={240}>
                    <AreaChart data={ordersData}>
                      <XXChartDefs
                        areaFills={[{ id: 'orders-evolution-fill', color: XX_SERIES_PURPLE, opacity: 0.34 }]}
                      />
                      <CartesianGrid {...xxGridProps} />
                      <XAxis dataKey="date" {...xxAxisProps} />
                      <YAxis {...xxAxisProps} allowDecimals={false} />
                      <Tooltip cursor={xxCursor} content={<HoloTooltip />} />
                      <Area
                        type="monotone"
                        dataKey="count"
                        name="Comenzi"
                        stroke={XX_SERIES_PURPLE}
                        strokeWidth={2}
                        fill="url(#orders-evolution-fill)"
                        activeDot={{ r: 5, fill: XX_SERIES_PURPLE, stroke: '#04050c', strokeWidth: 2 }}
                        isAnimationActive={false}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
            </Reveal>

            <div className="grid gap-6 lg:grid-cols-2">
              {/* Module — Top produse */}
              <Reveal>
                <div className="card h-full p-5">
                  <h2 className="mb-4 font-display text-lg font-semibold text-[color:var(--xx-ink)]">
                    Top produse vândute
                  </h2>
                  {!stats.topProducts || stats.topProducts.length === 0 ? (
                    <p className="py-12 text-center text-sm xx-ink-muted">Niciun produs vândut încă.</p>
                  ) : (
                    <ul className="divide-y divide-[rgba(255,255,255,0.08)]">
                      {stats.topProducts.map((p, idx) => {
                        const spark = (p.dailyUnits || []).map((v, i) => ({ i, v }));
                        const trendUp = (p.trendPct ?? 0) >= 0;
                        // Rank is stated as a number; the ring colour only
                        // reinforces the first three places.
                        const rankStyle =
                          idx === 0
                            ? 'border-[rgba(255,194,75,0.6)] bg-[rgba(255,194,75,0.16)] text-[#ffe0a3]'
                            : idx === 1
                              ? 'border-[rgba(255,255,255,0.25)] bg-[rgba(255,255,255,0.08)] text-[color:var(--xx-ink)]'
                              : idx === 2
                                ? 'border-[rgba(255,122,61,0.5)] bg-[rgba(255,122,61,0.14)] text-[#ffc9a8]'
                                : 'border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] text-[color:var(--xx-ink-dim)]';
                        return (
                          <li key={p.productId} className="flex items-center gap-3 py-3">
                            <span
                              className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-bold ${rankStyle}`}
                            >
                              {idx + 1}
                            </span>
                            <img
                              src={resolveImage(p.imageUrl)}
                              alt=""
                              loading="lazy"
                              className="h-10 w-10 shrink-0 rounded-lg border border-[rgba(255,255,255,0.1)] object-cover"
                            />
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-medium text-[color:var(--xx-ink)]">{p.name}</p>
                              <p className="text-xs xx-ink-dim">
                                {p.unitsSold} unități · {formatPrice(p.revenue)}
                              </p>
                            </div>
                            <div className="hidden w-16 sm:block" aria-hidden="true">
                              <Sparkline data={spark} color={XX_SERIES_BLUE} />
                            </div>
                            {p.trendPct != null && (
                              <span
                                className="shrink-0 text-xs font-semibold"
                                style={{ color: trendUp ? 'var(--xx-lime)' : 'var(--xx-red)' }}
                              >
                                <span aria-hidden="true">{trendUp ? '▲' : '▼'}</span> {Math.abs(p.trendPct)}%
                              </span>
                            )}
                            <Link
                              to="/admin/products"
                              className="shrink-0 text-xs font-semibold text-[color:var(--xx-cyan)] transition-opacity duration-xx hover:opacity-80"
                            >
                              Vezi produs
                            </Link>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </Reveal>

              {/* Module — Activitate recentă */}
              <Reveal delay={80}>
                <div className="card h-full p-5">
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <h2 className="font-display text-lg font-semibold text-[color:var(--xx-ink)]">
                      Activitate recentă
                    </h2>
                    <Link
                      to="/admin/audit"
                      className="text-sm font-semibold text-[color:var(--xx-cyan)] transition-opacity duration-xx hover:opacity-80"
                    >
                      Tot jurnalul →
                    </Link>
                  </div>

                  <div className="mb-3 flex flex-wrap gap-1.5">
                    {ACTIVITY_FILTERS.map((f) => {
                      const active = activityFilter === f.key;
                      return (
                        <button
                          key={f.key}
                          type="button"
                          aria-pressed={active}
                          onClick={() => setActivityFilter(f.key)}
                          className={`rounded-full border px-3 py-1 text-xs font-medium transition-all duration-xx ease-xx ${
                            active
                              ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.14)] text-[color:var(--xx-ink)]'
                              : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] hover:border-[rgba(122,60,255,0.5)] hover:text-[color:var(--xx-ink)]'
                          }`}
                        >
                          {f.label}
                        </button>
                      );
                    })}
                  </div>

                  {filteredActivity.length === 0 ? (
                    <p className="py-12 text-center text-sm xx-ink-muted">Nicio activitate în această categorie.</p>
                  ) : (
                    <ul className="divide-y divide-[rgba(255,255,255,0.08)]">
                      {filteredActivity.map((a) => {
                        const meta = ACTIVITY_META[a.action] || DEFAULT_ACTIVITY_META;
                        const linkTo = ENTITY_LINK[a.entityType];
                        return (
                          <li key={a.id} className="flex items-start gap-3 py-2.5 text-sm">
                            <span
                              className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${meta.tone}`}
                            >
                              <ActivityIcon name={meta.icon} className="h-4 w-4" />
                            </span>
                            <div className="min-w-0 flex-1">
                              <p className="text-[color:var(--xx-ink)]">
                                {meta.label || a.action}
                                {a.entityId ? <span className="xx-ink-dim"> · #{a.entityId}</span> : null}
                              </p>
                              {a.details && <p className="truncate text-xs xx-ink-muted">{a.details}</p>}
                              <p className="text-xs xx-ink-dim">
                                {formatRelative(a.createdAt)} · {a.actor}
                              </p>
                            </div>
                            {linkTo && (
                              <Link
                                to={linkTo}
                                className="shrink-0 text-xs font-semibold text-[color:var(--xx-cyan)] transition-opacity duration-xx hover:opacity-80"
                              >
                                Deschide
                              </Link>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </Reveal>
            </div>

            {stats.lowStockProducts && stats.lowStockProducts.length > 0 && (
              <Reveal>
                <div className="card border-[rgba(255,194,75,0.32)] p-5">
                  <div className="mb-3 flex items-center gap-2">
                    <ActivityIcon name="alert" className="h-4 w-4 text-[color:var(--xx-amber)]" />
                    <h2 className="font-display text-lg font-semibold text-[color:var(--xx-ink)]">Stoc scăzut</h2>
                  </div>
                  <div className="xx-no-scrollbar xx-snap-x flex gap-3 overflow-x-auto pb-1">
                    {stats.lowStockProducts.map((p) => (
                      <Link
                        key={p.productId}
                        to="/admin/products"
                        className="xx-snap-item flex w-48 shrink-0 items-center gap-2 rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.04)] p-2 transition-all duration-xx ease-xx hover:border-[rgba(255,194,75,0.55)] hover:bg-[rgba(255,194,75,0.08)]"
                      >
                        <img
                          src={resolveImage(p.imageUrl)}
                          alt=""
                          loading="lazy"
                          className="h-9 w-9 shrink-0 rounded-lg object-cover"
                        />
                        <div className="min-w-0">
                          <p className="truncate text-xs font-medium text-[color:var(--xx-ink)]">{p.name}</p>
                          <p
                            className="text-xs font-semibold"
                            style={{ color: p.stockQuantity === 0 ? 'var(--xx-red)' : 'var(--xx-amber)' }}
                          >
                            {p.stockQuantity === 0 ? 'Stoc epuizat' : `${p.stockQuantity} în stoc`}
                          </p>
                        </div>
                      </Link>
                    ))}
                  </div>
                </div>
              </Reveal>
            )}
          </>
        )}
      </div>
    </div>
  );
}
