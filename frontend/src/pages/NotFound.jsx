import { GeoIcon, NeonButton, Reveal } from '../components/xxii';

/**
 * XXII — TASK 1 / TASK 8 (Modul: rută inexistentă).
 *
 * Pagina 404 este singurul ecran pe care utilizatorul îl vede fără să îl fi
 * cerut, deci trebuie să comunice trei lucruri în ordinea asta: că nu e vina
 * lui, ce s-a întâmplat și pe unde poate ieși. Numărul „404” devine un titlu
 * cu gradient neon și o aură difuză în spate — el poartă atmosfera, iar textul
 * de sub el rămâne sobru și lizibil.
 *
 * Vechiul `text-brand-600` pe numeral și perechea `text-slate-800` /
 * `text-slate-500` de sub el au fost înlocuite cu vocabularul XXII. Butonul
 * `btn-primary` brut a devenit `NeonButton`, care aduce unda de click și
 * pulsația — singurul element interactiv de pe ecran merită să fie evident.
 */
export default function NotFound() {
  return (
    <Reveal direction="scale" className="relative py-24 text-center">
      {/*
        Aura stă pe un strat propriu, în spatele conținutului și scoasă din
        arborele de accesibilitate: e decor, nu informație. `pointer-events-none`
        garantează că nu fură niciodată un click destinat butonului.
      */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 top-24 -z-10 h-64 w-64 -translate-x-1/2 rounded-full opacity-70 blur-[90px]"
        style={{
          background:
            'radial-gradient(circle, rgba(122,60,255,0.55) 0%, rgba(34,232,245,0.28) 45%, rgba(0,0,0,0) 72%)',
        }}
      />

      <p
        className="bg-gradient-to-r from-[#22e8f5] via-[#7a3cff] to-[#ff4fd8] bg-clip-text text-[5.5rem] font-bold leading-none tracking-tight text-transparent"
        style={{ filter: 'drop-shadow(0 0 38px rgba(122,60,255,0.55))' }}
      >
        404
      </p>

      <h1 className="mt-5 text-2xl font-semibold text-[#e8ecff]">Pagina nu a fost găsită</h1>

      <p className="mx-auto mt-2 max-w-md text-sm xx-ink-muted">
        Adresa aceasta nu corespunde niciunei pagini din magazin. Este posibil ca linkul să fie
        vechi sau produsul să fi fost retras din catalog.
      </p>

      <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
        <NeonButton to="/" pulse icon={<GeoIcon name="home" className="h-4 w-4" accent="currentColor" />}>
          Înapoi acasă
        </NeonButton>
        <NeonButton
          to="/products"
          variant="secondary"
          icon={<GeoIcon name="grid" className="h-4 w-4" accent="currentColor" />}
        >
          Vezi produsele
        </NeonButton>
      </div>
    </Reveal>
  );
}
