import GeoIcon from './GeoIcon';

/**
 * XXII — TASK 1 / TASK 8 / TASK 9 (Template: ecranele de acces).
 *
 * Login și Register desenau fiecare, în propriul fișier, aceeași scenografie:
 * două sfere de nebuloasă, o grilă cu mască radială, un card plutitor cu aură
 * conică rotitoare și un bloc `<style>` cu cadre-cheie duplicate. Ambele
 * foloseau `bg-cyan-500/20` și `bg-indigo-600/20` — clase care nu există în
 * stratul de compatibilitate din index.css, deci nuanțe scăpate de sub control
 * exact pe primul ecran pe care îl vede un vizitator.
 *
 * Scenografia trăiește acum într-un singur loc, ca șablon în sensul TASK 9.
 * Culorile vin din paleta XXII (violet cosmic, cyan, magenta), nu din scara
 * implicită Tailwind, iar cadrele-cheie sunt declarate o singură dată.
 *
 * Trei decizii care nu sunt estetice:
 *
 *   1. **Tot decorul este `aria-hidden` și `pointer-events-none`.** Sferele,
 *      grila, stelele și aura sunt atmosferă; un cititor de ecran nu trebuie să
 *      le întâlnească, iar un click destinat formularului nu trebuie să cadă
 *      pe ele.
 *   2. **Mișcarea se oprește complet la `prefers-reduced-motion`.** Sferele
 *      care plutesc și aura care se rotește sunt exact tipul de mișcare
 *      periferică ce provoacă disconfort vestibular. Regula este scrisă în
 *      același bloc `<style>`, deci nu se poate pierde separat de animație.
 *   3. **Câmpul de stele se generează în afara componentei, o singură dată pe
 *      încărcarea modulului.** Generat la fiecare randare, s-ar rearanja la
 *      fiecare tastă apăsată în formular.
 */

/** Câmp de stele stabil: calculat o dată pe modul, nu la fiecare randare. */
const STARS = Array.from({ length: 60 }, (_, i) => ({
  id: i,
  top: Math.random() * 100,
  left: Math.random() * 100,
  size: Math.random() * 2 + 1,
  delay: Math.random() * 4,
  dur: Math.random() * 3 + 2,
}));

const SCENE_CSS = `
  @keyframes xx-auth-drift-a { 0%,100%{transform:translate3d(0,0,0)} 50%{transform:translate3d(40px,30px,0)} }
  @keyframes xx-auth-drift-b { 0%,100%{transform:translate3d(0,0,0)} 50%{transform:translate3d(-50px,-20px,0)} }
  @keyframes xx-auth-twinkle { 0%,100%{opacity:.15} 50%{opacity:1} }
  @keyframes xx-auth-spin { to { transform: translate(-50%, -50%) rotate(360deg) } }
  @keyframes xx-auth-scanline { 0%{transform:translateY(-120%)} 100%{transform:translateY(2200%)} }
  @media (prefers-reduced-motion: reduce) {
    .xx-auth-scene *, .xx-auth-card { animation: none !important; }
  }
`;

/**
 * @param eyebrow textul mic, spațiat, de sub titlu (contextul ecranului)
 * @param title titlul cardului
 * @param children conținutul cardului — formularul propriu-zis
 * @param footer conținut opțional sub formular (linkuri, note)
 */
export default function AuthShell({ eyebrow, title, children, footer = null }) {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#04060f] px-4 py-10">
      <style>{SCENE_CSS}</style>

      <div className="xx-auth-scene pointer-events-none absolute inset-0" aria-hidden="true">
        {/* Sfere de nebuloasă — violet cosmic și cyan, culorile de bază XXII. */}
        <div
          className="absolute -left-40 -top-40 h-[32rem] w-[32rem] rounded-full blur-[120px]"
          style={{
            background: 'radial-gradient(circle, rgba(34,232,245,0.28), rgba(34,232,245,0) 70%)',
            animation: 'xx-auth-drift-a 18s ease-in-out infinite',
          }}
        />
        <div
          className="absolute -bottom-40 -right-40 h-[34rem] w-[34rem] rounded-full blur-[130px]"
          style={{
            background: 'radial-gradient(circle, rgba(122,60,255,0.32), rgba(122,60,255,0) 70%)',
            animation: 'xx-auth-drift-b 22s ease-in-out infinite',
          }}
        />
        <div
          className="absolute left-1/2 top-1/2 h-72 w-72 -translate-x-1/2 -translate-y-1/2 rounded-full blur-[100px]"
          style={{
            background: 'radial-gradient(circle, rgba(255,79,216,0.16), rgba(255,79,216,0) 70%)',
          }}
        />

        {/* Grila de perspectivă, estompată spre margini printr-o mască radială. */}
        <div
          className="absolute inset-0 opacity-[0.14]"
          style={{
            backgroundImage:
              'linear-gradient(rgba(34,232,245,.25) 1px, transparent 1px), linear-gradient(90deg, rgba(34,232,245,.25) 1px, transparent 1px)',
            backgroundSize: '46px 46px',
            maskImage: 'radial-gradient(ellipse at center, black 40%, transparent 80%)',
            WebkitMaskImage: 'radial-gradient(ellipse at center, black 40%, transparent 80%)',
          }}
        />

        {/* Stele */}
        {STARS.map((s) => (
          <span
            key={s.id}
            className="absolute rounded-full bg-[rgba(255,255,255,0.92)]"
            style={{
              top: `${s.top}%`,
              left: `${s.left}%`,
              width: `${s.size}px`,
              height: `${s.size}px`,
              animation: `xx-auth-twinkle ${s.dur}s ease-in-out ${s.delay}s infinite`,
            }}
          />
        ))}
      </div>

      <div
        className="xx-auth-card relative w-full max-w-md animate-xx-float"
        style={{ animationDuration: '6s' }}
      >
        {/* Aura conică rotitoare din spatele cardului. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -inset-px overflow-hidden rounded-[1.6rem]"
        >
          <div
            className="absolute left-1/2 top-1/2 h-[200%] w-[200%] opacity-40"
            style={{
              background:
                'conic-gradient(from 0deg, transparent 0deg, rgba(34,232,245,.7) 60deg, transparent 140deg, transparent 220deg, rgba(122,60,255,.65) 300deg, transparent 360deg)',
              animation: 'xx-auth-spin 9s linear infinite',
            }}
          />
        </div>

        <div className="relative overflow-hidden rounded-[1.6rem] border border-[rgba(34,232,245,0.25)] bg-[rgba(255,255,255,0.04)] p-8 shadow-[0_0_60px_-10px_rgba(34,232,245,0.38)] backdrop-blur-2xl">
          {/* Linia de scanare care coboară peste card. */}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-[rgba(34,232,245,0.2)] to-transparent"
            style={{ animation: 'xx-auth-scanline 5s linear infinite' }}
          />

          <div className="relative">
            <div className="mb-6 flex flex-col items-center text-center">
              <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-[1.1rem] border border-[rgba(34,232,245,0.4)] bg-[rgba(34,232,245,0.1)] shadow-[0_0_25px_-5px_rgba(34,232,245,0.65)]">
                <GeoIcon name="bolt" className="h-7 w-7" accent="#22e8f5" />
              </div>

              <h1
                className="bg-gradient-to-r from-[#22e8f5] via-[#b795ff] to-[#ff4fd8] bg-clip-text text-2xl font-bold tracking-[0.2em] text-transparent"
                style={{ textShadow: '0 0 24px rgba(34,232,245,.3)' }}
              >
                {title}
              </h1>

              {eyebrow && (
                <p className="mt-2 text-xs uppercase tracking-[0.3em] text-[rgba(34,232,245,0.7)]">
                  {eyebrow}
                </p>
              )}
            </div>

            {children}

            {footer}
          </div>
        </div>
      </div>
    </div>
  );
}
