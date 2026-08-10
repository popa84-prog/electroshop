import { Link } from 'react-router-dom';
import { CountUp, GlassPanel, TrendPill } from '../../../components/xxii';
import metricsService from '../../../api/metricsService';
import usePanelData from '../../../hooks/usePanelData';

/**
 * The four business figures at the top of the dashboard. Task 9.
 *
 * Replaces the old "Utilizatori / Produse / Comenzi / Venit total" cards. Those
 * counted rows: how many products exist is a fact about the database. These
 * measure the business — what the stock is worth, what margin it carries, what
 * it sold this month — and only the second kind supports a decision.
 *
 * ## The data-quality strip is not a nicety
 *
 * The catalogue permits a product with no purchase price, and every figure here
 * needs one. Products without a cost are excluded from the arithmetic rather
 * than counted as free, so the numbers are correct for what they cover and
 * silent about the rest. The strip says how much of the catalogue that is.
 *
 * An operator reading a 42% margin has to know whether it covers the whole
 * catalogue or four fifths of it — an incomplete cost column always produces an
 * optimistic margin, and the direction of that error is exactly the one that
 * makes a business feel safer than it is.
 */
export default function BusinessBanner({ compact = false }) {
  const { data, loading, error, reload } = usePanelData(
    (signal) => metricsService.banner(signal),
    []
  );

  if (error) {
    return (
      <GlassPanel padded className="text-sm text-[color:var(--xx-ink)]">
        <p className="mb-2">Indicatorii de business nu au putut fi încărcați.</p>
        <button
          type="button"
          onClick={reload}
          className="rounded-lg border border-[rgba(255,255,255,0.18)] px-3 py-1.5 text-xs
            transition-colors duration-xx hover:border-[color:var(--xx-cyan)]
            hover:text-[color:var(--xx-cyan)]"
        >
          Încearcă din nou
        </button>
      </GlassPanel>
    );
  }

  const cards = [
    {
      key: 'stockValue',
      label: 'Valoare totală stoc',
      hint: 'Capitalul imobilizat în marfă, la preț de achiziție',
      accent: '#2e7bff',
      icon: <BoxIcon />,
      to: '/admin/products',
    },
    {
      key: 'profitPotential',
      label: 'Profit potențial',
      hint: 'Marja pe care ar aduce-o stocul curent vândut la prețul de listă',
      accent: '#1fac79',
      icon: <TrendIcon />,
      to: '/admin/products',
    },
    {
      key: 'monthSales',
      label: 'Vânzări luna curentă',
      hint: 'Venit înregistrat de la începutul lunii',
      accent: '#d032b8',
      icon: <CartIcon />,
      to: '/admin/orders',
    },
    {
      key: 'averageMargin',
      label: 'Marjă medie',
      hint: 'Marja ca procent din valoarea de vânzare a stocului',
      accent: '#b08c09',
      icon: <PercentIcon />,
      to: '/admin/accounting',
    },
  ];

  const quality = data?.dataQuality;
  const hasGap = quality && quality.productsWithoutCost > 0;

  return (
    <div className="space-y-3">
      <div className={`grid gap-3 ${compact ? 'sm:grid-cols-2 xl:grid-cols-4' : 'sm:grid-cols-2 xl:grid-cols-4'}`}>
        {cards.map((card, index) => {
          const metric = data?.[card.key];
          return (
            <MetricCard
              key={card.key}
              {...card}
              metric={metric}
              currency={data?.currency || 'RON'}
              loading={loading}
              compact={compact}
              // Each card enters a beat after the one before it. The stagger is
              // small enough to read as one motion rather than four, which is
              // what stops it looking like the page loaded in pieces.
              delay={index * 70}
            />
          );
        })}
      </div>

      {hasGap ? (
        <p
          className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border
            border-[rgba(176,140,9,0.35)] bg-[rgba(176,140,9,0.08)] px-3 py-2 text-xs
            text-[color:var(--xx-ink-dim)]"
        >
          <span aria-hidden="true" className="text-[#e0bd4a]">●</span>
          <span>
            <strong className="font-semibold text-[color:var(--xx-ink)]">
              {quality.productsWithoutCost}
            </strong>{' '}
            din {quality.totalActiveProducts} produse active nu au preț de achiziție
            înregistrat. Cifrele de mai sus acoperă {quality.coveragePct}% din catalog și sunt,
            prin urmare, optimiste.
          </span>
          <Link
            to="/admin/products?noCost=1"
            className="font-medium text-[color:var(--xx-cyan)] underline underline-offset-2"
          >
            Completează
          </Link>
        </p>
      ) : null}
    </div>
  );
}

function MetricCard({ label, hint, accent, icon, to, metric, currency, loading, compact, delay }) {
  const value = Number(metric?.value ?? 0);
  const unit = metric?.unit === 'PERCENT' ? 'percent' : 'currency';

  return (
    <Link
      to={to}
      className="xx-rise group block focus:outline-none focus-visible:ring-2
        focus-visible:ring-[color:var(--xx-cyan)] rounded-[1.25rem]"
      style={{ animationDelay: `${delay}ms` }}
      aria-label={`${label}: vezi detalii`}
    >
      <GlassPanel
        interactive
        className={`h-full ${compact ? 'p-3.5' : 'p-4 sm:p-5'} transition-transform duration-xx
          ease-xx group-hover:-translate-y-0.5`}
      >
        <div className="flex items-start justify-between gap-2">
          <span
            className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border"
            style={{
              color: accent,
              borderColor: `${accent}55`,
              background: `${accent}14`,
            }}
            aria-hidden="true"
          >
            {icon}
          </span>
          {metric?.delta ? (
            <TrendPill delta={metric.delta} suffix="față de perioada anterioară" compact />
          ) : null}
        </div>

        <p className={`mt-3 font-display font-semibold tabular-nums text-[color:var(--xx-ink)]
          ${compact ? 'text-lg' : 'text-xl sm:text-2xl'}`}>
          {loading && !metric ? (
            <span className="xx-shimmer inline-block h-6 w-24 rounded bg-[rgba(255,255,255,0.08)]" />
          ) : (
            <CountUp value={value} format={unit} currency={currency} />
          )}
        </p>

        <p className="mt-1 text-xs font-medium text-[color:var(--xx-ink)]">{label}</p>
        {!compact ? (
          <p className="mt-0.5 text-[11px] leading-snug text-[color:var(--xx-ink-dim)]">{hint}</p>
        ) : null}
      </GlassPanel>
    </Link>
  );
}

/* ---- Icons ----
 * Inline rather than imported from an icon package. The admin chrome already
 * carries a hand-drawn set for exactly this reason, and four glyphs do not
 * justify putting a dependency on the build's critical path. */

function BoxIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4.5 w-4.5" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 8l9-4 9 4-9 4-9-4z" />
      <path d="M3 8v8l9 4 9-4V8" />
      <path d="M12 12v9" />
    </svg>
  );
}

function TrendIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4.5 w-4.5" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 17l6-6 4 4 8-8" />
      <path d="M15 7h6v6" />
    </svg>
  );
}

function CartIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4.5 w-4.5" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="19" r="1.4" />
      <circle cx="17" cy="19" r="1.4" />
      <path d="M3 4h2l2.2 11.2a2 2 0 002 1.6h7.6a2 2 0 002-1.6L21 8H6" />
    </svg>
  );
}

function PercentIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-4.5 w-4.5" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <path d="M19 5L5 19" />
      <circle cx="7.5" cy="7.5" r="2.5" />
      <circle cx="16.5" cy="16.5" r="2.5" />
    </svg>
  );
}
