import { useEffect, useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import {
  GeoIcon,
  HoloLoader,
  HoloTooltip,
  NeonBadge,
  NeonButton,
  Reveal,
  SectionHeader,
  StatTile,
  XXChartDefs,
  XX_GLOW_FILTER,
  XX_SERIES_GREEN,
  XX_SERIES_MAGENTA,
  xxAxisProps,
  xxBarCursor,
  xxGridProps,
  xxLegendProps,
} from '../../components/xxii';
import { formatPrice } from '../../utils/format';

/**
 * XXII — TASK 6: Contabilitate primară inside the Quantum Control Center.
 *
 * The arithmetic is untouched — this screen already reports profit on a
 * cost-of-goods-sold basis and that logic is correct. What changed is
 * everything visual: the four headline figures are now `StatTile`s (count-up on
 * load, scan sweep when a new date range lands, trend stated with a glyph as
 * well as a colour), the range picker is a glass toolbar, and the bar chart
 * draws from the shared validated chart palette instead of the two hard-coded
 * light-theme hexes it used to carry.
 *
 * On the chart colours specifically: sales take the green series slot
 * (`#1fac79`) and purchases the magenta one (`#d032b8`) rather than the obvious
 * green/red pairing. Red is a *reserved status* colour in this design system
 * and is never spent on a data series; magenta is also the safer choice here,
 * since green against red is the one pair a deuteranopic operator cannot
 * separate by hue. Green↔magenta measures ΔE 12.6 under that simulation, well
 * clear of the floor. The neon comes from the glow filter on the bars, not from
 * brightening the fills.
 */

const firstOfMonthISO = () => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
};
const todayISO = () => new Date().toISOString().slice(0, 10);

/** Quick ranges, so the common questions do not require typing two dates. */
const PRESETS = [
  { key: 'month', label: 'Luna curentă' },
  { key: 'last30', label: 'Ultimele 30 zile' },
  { key: 'year', label: 'Anul curent' },
];

function presetRange(key) {
  const now = new Date();
  const iso = (d) => d.toISOString().slice(0, 10);
  if (key === 'last30') {
    const start = new Date(now);
    start.setDate(start.getDate() - 29);
    return { from: iso(start), to: iso(now) };
  }
  if (key === 'year') {
    return { from: iso(new Date(now.getFullYear(), 0, 1)), to: iso(now) };
  }
  return { from: firstOfMonthISO(), to: todayISO() };
}

export default function AdminAccounting() {
  const [from, setFrom] = useState(firstOfMonthISO());
  const [to, setTo] = useState(todayISO());
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);

  // A sequence number rather than a boolean: two rapid "Aplică" clicks would
  // otherwise let the slower response overwrite the faster one.
  const [requestId, setRequestId] = useState(0);
  const [range, setRange] = useState({ from: firstOfMonthISO(), to: todayISO() });

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    adminService
      .accountingReport({ from: range.from, to: range.to })
      .then((data) => {
        if (!cancelled) setReport(data);
      })
      .catch(() => {
        if (!cancelled) setReport(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [range, requestId]);

  const apply = () => {
    setRange({ from, to });
    // Re-applying the same range must still refetch: the operator is asking for
    // fresh numbers, not for the same numbers again.
    setRequestId((value) => value + 1);
  };

  const applyPreset = (key) => {
    const next = presetRange(key);
    setFrom(next.from);
    setTo(next.to);
    setRange(next);
  };

  const profit = report ? Number(report.profit) : 0;
  const margin = report ? Number(report.marginPercent) : 0;
  const unknownCost = report ? Number(report.itemsWithUnknownCost) : 0;

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Financiar"
        title="Contabilitate primară"
        subtitle="Venituri, cost de achiziție al mărfii efectiv vândute și rezultatul real pe perioada selectată."
      />

      {/* Range console */}
      <div className="card mb-6 flex flex-wrap items-end gap-3 p-4">
        <div>
          <label htmlFor="acc-from" className="mb-1 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
            De la
          </label>
          <input
            id="acc-from"
            type="date"
            className="input"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="acc-to" className="mb-1 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
            Până la
          </label>
          <input
            id="acc-to"
            type="date"
            className="input"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </div>
        <NeonButton
          onClick={apply}
          icon={<GeoIcon name="refresh" className="h-4 w-4" accent="currentColor" />}
        >
          Aplică
        </NeonButton>

        <div className="ml-auto flex flex-wrap gap-2">
          {PRESETS.map((preset) => (
            <button
              key={preset.key}
              type="button"
              onClick={() => applyPreset(preset.key)}
              className="rounded-full border border-[rgba(255,255,255,0.12)] px-3 py-1.5 text-xs font-medium text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.5)] hover:text-[color:var(--xx-ink)]"
            >
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <HoloLoader label="Se calculează raportul" />
      ) : !report ? (
        <div className="card card-static p-8 text-center">
          <p className="text-sm xx-ink-muted">Raportul nu a putut fi încărcat.</p>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile
              label="Venituri (vânzări)"
              value={Number(report.salesTotal)}
              format={(value) => formatPrice(value)}
              tone="good"
              icon={<GeoIcon name="chart" className="h-5 w-5" accent="currentColor" />}
              hint={`${report.salesCount} ${report.salesCount === 1 ? 'comandă' : 'comenzi'} în perioadă`}
            />
            <StatTile
              label="Cost marfă vândută"
              value={Number(report.cogsTotal)}
              format={(value) => formatPrice(value)}
              tone="critical"
              icon={<GeoIcon name="box" className="h-5 w-5" accent="currentColor" />}
              hint="Preț de achiziție × cantitate vândută"
            />
            <StatTile
              label="Rezultat (profit)"
              value={profit}
              format={(value) => formatPrice(value)}
              tone={profit >= 0 ? 'good' : 'critical'}
              icon={<GeoIcon name="coins" className="h-5 w-5" accent="currentColor" />}
              hint={profit >= 0 ? 'Marjă pozitivă pe perioadă' : 'Marjă negativă pe perioadă'}
            />
            <StatTile
              label="Marjă"
              value={margin}
              format={(value) => `${value.toFixed(1)}%`}
              tone="aqua"
              icon={<GeoIcon name="pulse" className="h-5 w-5" accent="currentColor" />}
              progress={Math.max(0, Math.min(1, margin / 100))}
            />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="card card-static p-4">
              <p className="xx-eyebrow">Achiziții furnizori (cash)</p>
              <p className="mt-1 font-display text-xl font-bold text-[color:var(--xx-ink)]">
                {formatPrice(report.purchasesTotal)}
              </p>
              <p className="mt-1 text-xs xx-ink-dim">
                {report.purchasesCount} {report.purchasesCount === 1 ? 'intrare' : 'intrări'} de stoc în perioadă
              </p>
            </div>

            {unknownCost > 0 ? (
              <div className="card card-static border-[rgba(255,194,75,0.4)] p-4">
                <NeonBadge
                  tone="warning"
                  icon={<GeoIcon name="alert" className="h-3 w-3" accent="currentColor" />}
                >
                  Cost incomplet
                </NeonBadge>
                <p className="mt-2 text-sm xx-ink-muted">
                  {unknownCost} {unknownCost === 1 ? 'bucată vândută are' : 'bucăți vândute au'} preț de achiziție
                  necunoscut și {unknownCost === 1 ? 'nu este inclusă' : 'nu sunt incluse'} în costul mărfii vândute
                  de mai sus. Setează prețul de achiziție al produsului respectiv pentru un rezultat exact.
                </p>
              </div>
            ) : (
              <div className="card card-static p-4">
                <NeonBadge
                  tone="good"
                  icon={<GeoIcon name="check" className="h-3 w-3" accent="currentColor" />}
                >
                  Cost complet
                </NeonBadge>
                <p className="mt-2 text-sm xx-ink-muted">
                  Toate bucățile vândute în perioadă au preț de achiziție cunoscut, deci rezultatul de mai sus este
                  exact.
                </p>
              </div>
            )}
          </div>

          <Reveal>
            <div className="card p-5">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                <h2 className="font-display text-lg font-semibold text-[color:var(--xx-ink)]">
                  Vânzări vs. cumpărări pe zile
                </h2>
                <p className="text-xs xx-ink-dim">
                  {range.from} → {range.to}
                </p>
              </div>

              {!report.byDay || report.byDay.length === 0 ? (
                <p className="py-10 text-center text-sm xx-ink-muted">
                  Nu există mișcări în perioada selectată.
                </p>
              ) : (
                <ResponsiveContainer width="100%" height={320}>
                  <BarChart data={report.byDay} barGap={2}>
                    <XXChartDefs />
                    <CartesianGrid {...xxGridProps} />
                    <XAxis dataKey="date" {...xxAxisProps} tick={{ fontSize: 11, fill: xxAxisProps.tick.fill }} />
                    <YAxis {...xxAxisProps} />
                    <Tooltip
                      cursor={xxBarCursor}
                      content={<HoloTooltip format={(value) => formatPrice(value)} />}
                    />
                    <Legend {...xxLegendProps} />
                    <Bar
                      dataKey="sales"
                      name="Vânzări"
                      fill={XX_SERIES_GREEN}
                      radius={[4, 4, 0, 0]}
                      filter={XX_GLOW_FILTER}
                    />
                    <Bar
                      dataKey="purchases"
                      name="Cumpărări"
                      fill={XX_SERIES_MAGENTA}
                      radius={[4, 4, 0, 0]}
                      filter={XX_GLOW_FILTER}
                    />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </Reveal>

          <p className="text-xs leading-relaxed xx-ink-dim">
            Rezultatul (profitul) se calculează ca marjă reală pe marfa efectiv vândută: pentru fiecare produs din
            comenzile plasate în perioada selectată (exceptând cele anulate), se scade din prețul de vânzare prețul
            de achiziție al acelui produs, înmulțit cu cantitatea vândută — la fel ca profitul afișat în tabelul de
            produse. Achizițiile de la furnizori sunt afișate separat, informativ, și nu sunt scăzute din profit: ele
            reprezintă banii cheltuiți pentru reaprovizionarea stocului în perioadă, indiferent dacă acel stoc a fost
            deja vândut sau nu.
          </p>
        </div>
      )}
    </div>
  );
}
