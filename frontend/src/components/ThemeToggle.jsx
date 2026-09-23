import { useTheme } from '../context/ThemeContext';

/**
 * Comutatorul de temă: un singur buton, fix într-un colț, pe toate paginile.
 *
 * <h2>De ce în stânga jos</h2>
 *
 * Celelalte trei colțuri sunt ocupate. Sus stă bara de navigare pe toată
 * lățimea. Dreapta jos este a notificărilor ({@code Toast}, {@code z-[100]}),
 * iar un buton acolo ar fi acoperit exact când apare un mesaj de eroare. Jos, pe
 * toată lățimea, stă bara de navigare mobilă — de aceea butonul urcă la
 * {@code bottom-20} sub pragul {@code sm} și coboară la {@code bottom-5} peste
 * el, în loc să stea la aceeași înălțime pe orice ecran și să acopere o filă.
 *
 * <h2>Cele două discuri</h2>
 *
 * Soarele și luna sunt desenate amândouă, suprapuse, și se schimbă prin rotație
 * și opacitate. Alternativa — randarea condiționată a unui singur SVG — ar
 * însemna montare și demontare la fiecare apăsare, deci nicio tranziție: pictograma
 * ar sări. Aici discul care pleacă se rotește afară în timp ce celălalt se
 * rotește înăuntru, pe aceeași durată cu restul interfeței
 * ({@code --xx-t-slow}), așa că butonul se mișcă în ritmul paginii.
 *
 * <h2>Accesibilitate</h2>
 *
 * Butonul anunță ce face, nu în ce stare este: {@code aria-label} spune „Comută
 * pe tema deschisă", pentru că o etichetă care spune „Temă întunecată" lasă
 * cititorul de ecran să ghicească dacă aceea este starea curentă sau rezultatul
 * apăsării. {@code aria-pressed} poartă starea, care este exact rolul lui.
 *
 * Pictogramele sunt {@code aria-hidden}: sunt decor peste o etichetă care spune
 * deja totul, iar anunțarea lor ar citi de două ori același lucru.
 */
export default function ThemeToggle() {
  const { isDark, toggle } = useTheme();
  const eticheta = isDark ? 'Comută pe tema deschisă' : 'Comută pe tema întunecată';

  return (
    <button
      type="button"
      onClick={toggle}
      title={eticheta}
      aria-label={eticheta}
      aria-pressed={!isDark}
      className="
        fixed bottom-20 left-4 z-[90]
        grid h-11 w-11 place-items-center rounded-full
        border border-[rgba(var(--xx-veil),0.16)]
        bg-[rgba(var(--xx-panel),0.72)]
        text-[color:var(--xx-ink)]
        shadow-[0_10px_30px_-14px_rgba(var(--xx-shade),0.8)]
        backdrop-blur-glass
        transition-all duration-xxslow ease-xx
        hover:border-[rgba(34,232,245,0.55)] hover:shadow-glow-aqua
        focus-visible:outline-none focus-visible:ring-2
        focus-visible:ring-[rgba(34,232,245,0.6)] focus-visible:ring-offset-0
        active:scale-95
        sm:bottom-5 sm:left-5
      "
    >
      <span className="relative block h-[1.15rem] w-[1.15rem]">
        {/* Soare — vizibil pe tema deschisă */}
        <svg
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
          className={`absolute inset-0 h-full w-full transition-all duration-xxslow ease-xx ${
            isDark ? 'rotate-90 scale-50 opacity-0' : 'rotate-0 scale-100 opacity-100'
          }`}
        >
          <circle cx="12" cy="12" r="4.4" stroke="currentColor" strokeWidth="1.7" />
          <g stroke="currentColor" strokeWidth="1.7" strokeLinecap="round">
            <path d="M12 2.4v2.3" />
            <path d="M12 19.3v2.3" />
            <path d="M21.6 12h-2.3" />
            <path d="M4.7 12H2.4" />
            <path d="M18.8 5.2l-1.6 1.6" />
            <path d="M6.8 17.2l-1.6 1.6" />
            <path d="M18.8 18.8l-1.6-1.6" />
            <path d="M6.8 6.8L5.2 5.2" />
          </g>
        </svg>

        {/* Lună — vizibilă pe tema întunecată */}
        <svg
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
          className={`absolute inset-0 h-full w-full transition-all duration-xxslow ease-xx ${
            isDark ? 'rotate-0 scale-100 opacity-100' : '-rotate-90 scale-50 opacity-0'
          }`}
        >
          <path
            d="M20.2 14.4A8.6 8.6 0 0 1 9.6 3.8a8.6 8.6 0 1 0 10.6 10.6z"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinejoin="round"
          />
        </svg>
      </span>
    </button>
  );
}
