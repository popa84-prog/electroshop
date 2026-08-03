import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import productService from '../api/productService';
import ProductCard from '../components/ProductCard';
import AIPicks from '../components/xxii/AIPicks';
import GeoIcon from '../components/xxii/GeoIcon';
import HoloTimer from '../components/xxii/HoloTimer';
import NeonButton from '../components/xxii/NeonButton';
import Reveal from '../components/xxii/Reveal';
import SectionHeader from '../components/xxii/SectionHeader';
import TiltCard from '../components/xxii/TiltCard';
import { HoloGridSkeleton } from '../components/xxii/HoloLoader';
import { useSeo } from '../utils/seo';

/**
 * XXII — TASK 2 (Homepage Futurist / Reactor Layout).
 *
 * The page is laid out as a spaceship control panel, top to bottom:
 *
 *   1. the cinematic hero — an animated gradient reactor core with ambient edge
 *      light and a pulsing neon CTA;
 *   2. the benefits bar — neon icons swept by a scan line;
 *   3. the 3D category tiles — VisionOS-style, rotating 3–5° toward the cursor;
 *   4. the promotion module with the holographic countdown;
 *   5. the featured grid;
 *   6. the AI Picks module (TASK 7).
 *
 * Every module below the fold is wrapped in `Reveal`, so sections materialise
 * as the user scrolls instead of all existing at once. The stagger is the array
 * index times a small step, which is what makes a grid land as a wave rather
 * than as a block.
 *
 * The category icons stay as emoji glyphs: they are recognisable, they are
 * already mapped to the real catalogue names, and replacing 22 of them with
 * bespoke geometry would trade recognisability for consistency. They now sit
 * inside a geometric neon frame, which supplies the consistency instead.
 */

const CATEGORY_ICONS = {
  audio: '🎧',
  'foto & video': '📷',
  'auto & moto': '🚗',
  wearables: '⌚',
  'smart home': '🏠',
  tablete: '📱',
  telefoane: '📱',
  monitoare: '🖥️',
  gaming: '🎮',
  laptopuri: '💻',
  'periferice pc': '⌨️',
  'sisteme pc': '🖥️',
  'componente pc': '🧩',
  stocare: '💾',
  'tv & proiectoare': '📺',
  electrocasnice: '🔌',
  accesorii: '🔗',
  'ingrijire personala': '💇',
  'instrumente muzicale': '🎸',
  'scule & unelte': '🛠️',
  retea: '📶',
  sanatate: '❤️',
  folosit: '♻️',
};

const FALLBACK_ICON = '🔌';

function iconFor(name) {
  return CATEGORY_ICONS[String(name).trim().toLowerCase()] || FALLBACK_ICON;
}

// Rezervă locală, folosită doar cât timp API-ul de oferte nu a răspuns încă
// sau a eșuat — nu mai este sursa de adevăr. Aceleași patru cartonașe pe care
// `OfferService.seedDefaults()` le creează în baza de date, ca prima
// randare (înainte ca cererea către server să se întoarcă) să arate identic
// cu ce va veni oricum din API. Al treilea cartonaș a fost înlocuit conform
// cerinței: „Retur 14 zile” nu mai există, magazinul evaluează și cumpără
// electronice noi.
const BENEFITS_FALLBACK = [
  { icon: 'truck', accent: 'var(--xx-cyan)', title: 'Livrare rapidă', text: 'Transport gratuit, oriunde în țară' },
  { icon: 'shield', accent: 'var(--xx-lime)', title: 'Garanție completă', text: 'Produse originale, garanție legală' },
  { icon: 'coins', accent: 'var(--xx-amber)', title: 'Cumpărăm electronice', text: 'Evaluare corectă, plată pe loc' },
  { icon: 'tag', accent: 'var(--xx-purple)', title: 'Plata la livrare', text: 'Plătești doar când primești coletul' },
];

// Rezerva locală pentru modulul mare de promoție — identică, ca idee, cu
// oferta implicită din `OfferService.seedDefaults()`. `endsAt` rămâne `null`
// aici: fereastra reală (recurentă zilnic sau cu dată fixă) vine din API.
const HOME_PROMO_FALLBACK = {
  title: 'Transport gratuit',
  headline: 'la orice comandă',
  description: 'Fără prag minim și fără costuri ascunse. Oferta se resetează la miezul nopții.',
  badgeLabel: 'Ofertă activă',
  ctaLabel: 'Profită acum',
  ctaUrl: '/products',
  endsAt: null,
  showTimer: true,
  recurringDaily: true,
};

export default function Home() {
  const [featured, setFeatured] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [benefits, setBenefits] = useState(BENEFITS_FALLBACK);
  const [promo, setPromo] = useState(HOME_PROMO_FALLBACK);

  useSeo({
    description:
      'ElectroShop – magazin online de electronice: telefoane, laptopuri, audio și accesorii la prețuri bune, cu livrare rapidă.',
    path: '/',
  });

  useEffect(() => {
    let cancelled = false;
    productService
      .list({ page: 0, size: 4, sortBy: 'price', direction: 'desc' })
      .then((data) => {
        if (!cancelled) setFeatured(data?.content || []);
      })
      .catch(() => {
        if (!cancelled) setFeatured([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // The tiles mirror the four best-stocked categories in the real catalogue,
  // so they never advertise a section that does not exist.
  useEffect(() => {
    let cancelled = false;
    productService
      .topCategories(4)
      .then((data) => {
        if (!cancelled) setCategories(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!cancelled) setCategories([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Banda de beneficii — vine acum din panoul de administrare (cerința 1).
  // Un răspuns gol înseamnă că operatorul a dezactivat explicit toate
  // cartonașele din această zonă, deci secțiunea chiar trebuie să dispară —
  // rezerva locală se folosește doar cât timp cererea e în curs sau a eșuat.
  useEffect(() => {
    let cancelled = false;
    productService
      .offers('BENEFIT_BAR')
      .then((data) => {
        if (cancelled) return;
        setBenefits(
          (data || []).map((o) => ({ icon: o.icon, accent: o.accent, title: o.title, text: o.headline }))
        );
      })
      .catch(() => {
        if (!cancelled) setBenefits(BENEFITS_FALLBACK);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Modulul mare de promoție — la fel, din API. `null` înseamnă „nicio ofertă
  // activă acum în această zonă”, ceea ce ascunde complet secțiunea: exact
  // efectul pe care comutatorul „Activă” din panou trebuie să îl aibă.
  useEffect(() => {
    let cancelled = false;
    productService
      .offers('HOME_PROMO')
      .then((data) => {
        if (!cancelled) setPromo((data && data[0]) || null);
      })
      .catch(() => {
        if (!cancelled) setPromo(HOME_PROMO_FALLBACK);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Ținta cronometrului. O ofertă recurentă zilnic se resetează la miezul
  // nopții din fusul orar al vizitatorului — un calcul server-side în UTC ar
  // fi greșit cu 3 ore pentru România, de aceea `recurringDaily` este doar un
  // steag și miezul nopții se calculează aici, în browser. O ofertă cu
  // `endsAt` explicit din backend își folosește direct data primită.
  const promoTarget = useMemo(() => {
    if (!promo) return null;
    if (promo.endsAt) return promo.endsAt;
    if (promo.recurringDaily) {
      const end = new Date();
      end.setHours(23, 59, 59, 999);
      return end.toISOString();
    }
    return null;
  }, [promo]);

  return (
    <div className="space-y-16 sm:space-y-24">
      {/* ─────────────── 1. Cinematic hero (reactor core) ─────────────── */}
      <section className="relative overflow-hidden rounded-[1.75rem] border border-[rgba(255,255,255,0.14)] px-6 py-16 sm:px-12 sm:py-24 tv:py-32">
        {/* Layer 1 — the animated gradient field. */}
        <span
          aria-hidden="true"
          className="absolute inset-0 -z-20 animate-xx-gradient bg-xx-deep"
          style={{ backgroundSize: '220% 220%' }}
        />
        {/* Layer 2 — the reactor light pools. */}
        <span aria-hidden="true" className="absolute inset-0 -z-10 bg-xx-reactor opacity-90" />
        {/* Layer 3 — the ambient edge light: a bright inner rim that fades inward. */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 rounded-[1.75rem]"
          style={{ boxShadow: 'inset 0 0 120px -40px rgba(34,232,245,0.85), inset 0 1px 0 0 rgba(255,255,255,0.16)' }}
        />
        {/* Layer 4 — a slow vertical scan, the single ambient motion of the hero. */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 top-0 h-40 animate-xx-scan bg-gradient-to-b from-[rgba(34,232,245,0.16)] to-transparent"
        />

        <div className="relative max-w-3xl">
          <p className="xx-eyebrow">ElectroShop · XXII</p>

          <h1 className="font-display text-4xl font-bold leading-[1.05] tracking-tight text-white sm:text-6xl tv:text-7xl">
            Tehnologie de top,
            <br />
            <span className="xx-text-gradient">la prețuri corecte</span>
          </h1>

          <p className="mt-5 max-w-xl text-base text-[#c6cdf0] sm:text-lg tv:text-xl">
            Descoperă cele mai noi telefoane, laptopuri, produse audio și accesorii — cu livrare gratuită și plata
            la primire.
          </p>

          <div className="mt-9 flex flex-wrap gap-3">
            <NeonButton to="/products" size="lg" pulse icon={<GeoIcon name="grid" className="h-5 w-5" accent="currentColor" />}>
              Vezi produsele
            </NeonButton>
            <NeonButton to="/register" variant="secondary" size="lg">
              Creează cont
            </NeonButton>
          </div>
        </div>
      </section>

      {/* ─────────────── 2. Benefits bar (scan-line) ─────────────── */}
      {benefits.length > 0 && (
      <section aria-label="Beneficii" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {benefits.map((benefit, index) => (
          <Reveal key={benefit.title} delay={index * 70} className="h-full">
            <div className="xx-scanning card card-static flex h-full items-start gap-3 p-4">
              <span
                aria-hidden="true"
                className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-[rgba(255,255,255,0.13)] bg-[rgba(255,255,255,0.05)]"
              >
                <GeoIcon name={benefit.icon} className="h-5 w-5" accent={benefit.accent} />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-[color:var(--xx-ink)]">{benefit.title}</span>
                <span className="mt-0.5 block text-xs xx-ink-muted">{benefit.text}</span>
              </span>
            </div>
          </Reveal>
        ))}
      </section>
      )}

      {/* ─────────────── 3. 3D category tiles ─────────────── */}
      {categories.length > 0 && (
        <section>
          <SectionHeader
            eyebrow="Navigare rapidă"
            title="Categorii populare"
            subtitle="Cele mai bine reprezentate categorii din catalog, direct din stocul real."
            actionTo="/products"
            actionLabel="Toate categoriile"
          />

          <div className="grid grid-cols-2 gap-5 sm:grid-cols-4">
            {categories.map((category, index) => (
              <Reveal key={category.name} delay={index * 80} direction="scale" className="h-full">
                <TiltCard max={6} className="h-full">
                  <Link
                    to={`/products?category=${encodeURIComponent(category.name)}`}
                    className="card group flex h-full flex-col items-center gap-3 p-6 text-center"
                  >
                    {/* The icon sits inside a rotating neon frame — the VisionOS
                        depth cue: the frame turns, the glyph stays upright. */}
                    <span className="relative grid h-16 w-16 place-items-center">
                      <span
                        aria-hidden="true"
                        className="absolute inset-0 rotate-45 rounded-xl border border-[rgba(34,232,245,0.35)] bg-[rgba(34,232,245,0.07)] transition-all duration-500 ease-xx group-hover:rotate-[60deg] group-hover:border-[rgba(34,232,245,0.75)] group-hover:shadow-glow-aqua"
                      />
                      <span className="relative text-3xl" aria-hidden="true">
                        {iconFor(category.name)}
                      </span>
                    </span>

                    <span className="font-semibold text-[color:var(--xx-ink)] transition-colors duration-xx group-hover:text-[color:var(--xx-cyan)]">
                      {category.name}
                    </span>
                    <span className="text-xs xx-ink-dim">
                      {category.productCount} {category.productCount === 1 ? 'produs' : 'produse'}
                    </span>
                  </Link>
                </TiltCard>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      {/* ─────────────── 4. Promotion module + holographic timer ─────────────── */}
      {/* Absent complet dacă niciun operator nu are o ofertă activă chiar acum
          în zona HOME_PROMO — comutatorul din panoul „Oferte” controlează
          direct dacă acest modul există pe pagină. */}
      {promo && (
      <Reveal>
        <section className="relative overflow-hidden rounded-[1.5rem] border border-[rgba(255,61,203,0.3)] bg-[rgba(12,7,26,0.72)] p-6 backdrop-blur-glass sm:p-9">
          <span
            aria-hidden="true"
            className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full opacity-60"
            style={{ background: 'radial-gradient(circle, rgba(255,61,203,0.45), transparent 68%)' }}
          />
          <span
            aria-hidden="true"
            className="pointer-events-none absolute -bottom-28 -left-16 h-72 w-72 rounded-full opacity-50"
            style={{ background: 'radial-gradient(circle, rgba(34,232,245,0.4), transparent 68%)' }}
          />

          <div className="relative flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between">
            <div className="max-w-xl">
              {promo.badgeLabel && <span className="badge badge-magenta">{promo.badgeLabel}</span>}
              <h2 className="mt-3 font-display text-2xl font-bold text-white sm:text-4xl">
                <span className="xx-text-gradient-hot">{promo.title}</span>
                {promo.headline ? ` ${promo.headline}` : ''}
              </h2>
              {promo.description && (
                <p className="mt-3 text-sm text-[#c6cdf0] sm:text-base">{promo.description}</p>
              )}
              {promo.ctaLabel && (
                <NeonButton to={promo.ctaUrl || '/products'} variant="hot" size="lg" className="mt-6" pulse>
                  {promo.ctaLabel}
                </NeonButton>
              )}
            </div>

            {promo.showTimer && promoTarget && (
              <HoloTimer target={promoTarget} label="Se încheie în" className="shrink-0" />
            )}
          </div>
        </section>
      </Reveal>
      )}

      {/* ─────────────── 5. Featured grid ─────────────── */}
      <section>
        <SectionHeader
          eyebrow="Selecția redacției"
          title="Produse recomandate"
          subtitle="Cele mai valoroase produse din catalog, actualizate automat."
          actionTo="/products"
          actionLabel="Vezi toate"
        />

        {loading ? (
          <HoloGridSkeleton count={4} />
        ) : featured.length === 0 ? (
          <p className="card card-static p-8 text-center text-sm xx-ink-muted">
            Catalogul nu conține încă produse publicate.
          </p>
        ) : (
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {featured.map((product, index) => (
              <Reveal key={product.id} delay={index * 80} className="h-full">
                <ProductCard product={product} />
              </Reveal>
            ))}
          </div>
        )}
      </section>

      {/* ─────────────── 6. AI Picks (TASK 7) ─────────────── */}
      <Reveal>
        <AIPicks variant="carousel" limit={8} />
      </Reveal>
    </div>
  );
}
