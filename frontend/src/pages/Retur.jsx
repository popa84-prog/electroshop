import { GeoIcon, NeonBadge, Reveal, SectionHeader } from '../components/xxii';
import { useSeo } from '../utils/seo';

/**
 * Condiții de retur.
 *
 * Pagina descrie regulile vânzătorului, formulate direct, fără trimiteri la
 * acte normative. Versiunea anterioară cita ordonanțe și articole în șapte
 * locuri; citarea a fost eliminată, regulile au rămas. Motivul este practic:
 * un cumpărător care deschide pagina vrea să afle ce se întâmplă cu produsul
 * lui, nu numărul articolului care produce acel rezultat, iar o citare greșită
 * sau depășită este mai dăunătoare decât absența ei.
 *
 * Se disting în continuare cele trei situații care se confundă frecvent în
 * practică: produsele noi sigilate, produsele sigilate din categoria
 * igienico-sanitară și produsele vândute ca testate sau resigilate. Separarea
 * lor este singurul lucru care face politica aplicabilă, pentru că fiecare are
 * un alt rezultat la retur.
 */

/**
 * Evidențiere pentru termenele și pragurile care contează.
 *
 * Componenta a purtat inițial numerele de articol. Rolul ei vizual — un reper
 * scurt, monospațiat, pe care ochiul îl găsește fără să citească paragraful —
 * rămâne util pentru termene și procente, deci componenta rămâne, cu alt
 * conținut.
 */
const Reper = ({ children }) => (
  <span className="rounded-md border border-[rgba(var(--xx-veil),0.14)] bg-[rgba(var(--xx-veil),0.05)] px-1.5 py-0.5 font-mono text-[0.7rem] text-[color:var(--xx-ink-muted)]">
    {children}
  </span>
);

export default function Retur() {
  useSeo({
    title: 'Condiții de retur',
    description:
      'Condițiile de retur pentru produsele noi sigilate și pentru produsele testate.',
    path: '/retur',
  });

  return (
    <div className="space-y-8 pb-10">
      <SectionHeader
        eyebrow="Informații"
        title="Condiții de retur"
        subtitle="Cum funcționează returul, în funcție de starea produsului la livrare."
        as="h1"
      />

      <Reveal>
        <div className="card card-static p-6 sm:p-8">
          <div className="flex items-start gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border border-[rgba(var(--xx-veil),0.13)] bg-[rgba(var(--xx-veil),0.05)]">
              <GeoIcon name="document" className="h-[1.15rem] w-[1.15rem]" accent="var(--xx-cyan)" />
            </span>
            <div>
              <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">Cui i se aplică</h2>
              <p className="mt-2 text-sm leading-relaxed xx-ink-muted">
                Condițiile de mai jos se aplică persoanelor fizice care cumpără pentru uz personal, în afara
                unei activități profesionale. Achizițiile efectuate în numele unei firme, pe bază de factură
                cu CUI, <strong className="text-[color:var(--xx-ink)]">nu intră în această procedură</strong> —
                pentru acestea, orice retur se analizează separat, de la caz la caz, în funcție de înțelegerea
                dintre părți.
              </p>
            </div>
          </div>
        </div>
      </Reveal>

      <Reveal delay={60}>
        <div className="card card-static p-6 sm:p-8">
          <div className="mb-3 flex items-center gap-2.5">
            <NeonBadge tone="good">Produse noi, sigilate</NeonBadge>
          </div>
          <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">
            Retur acceptat dacă produsul este returnat sigilat
          </h2>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Pentru produsele electronice noi, returul se poate cere în <Reper>14 zile</Reper> calendaristice
            de la primirea produsului. Rambursarea integrală a prețului se acordă atunci când produsul este
            returnat în ambalajul original, sigilat, nedesfăcut, cu toate accesoriile, folia de protecție și
            etichetele intacte — exact starea în care a fost livrat.
          </p>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Dacă produsul a fost desigilat și folosit peste ce este necesar pentru a-i verifica natura,
            caracteristicile și funcționarea de bază — testarea similară celei permise într-un magazin fizic —
            se aplică o <strong className="text-[color:var(--xx-ink)]">deducere pentru diminuarea valorii</strong>,
            stabilită proporțional cu gradul de uzură constatat: urme de utilizare, accesorii sau componente
            lipsă, ambalaj deteriorat sau folie de protecție lipsă. Dacă diminuarea face imposibilă revânzarea
            produsului ca produs nou, deducerea poate ajunge până la valoarea integrală a produsului.
          </p>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Costul direct al transportului de retur este suportat de cumpărător, cu excepția situației în care
            produsul returnat este confirmat ca fiind defect sau neconform la livrare — caz în care costul
            transportului este suportat de vânzător.
          </p>
        </div>
      </Reveal>

      <Reveal delay={120}>
        <div className="card card-static p-6 sm:p-8">
          <div className="mb-3 flex items-center gap-2.5">
            <NeonBadge tone="critical">Produse sigilate din categoria igienico-sanitară</NeonBadge>
          </div>
          <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">
            Retur exclus odată ce ambalajul a fost desfăcut
          </h2>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Pentru produsele sigilate care nu pot fi returnate din motive de protecție a sănătății sau de igienă
            odată desigilate — de exemplu căști in-ear, aparate de ras, epilatoare și alte produse de îngrijire
            personală care intră în contact direct cu corpul — dreptul de retur{' '}
            <strong className="text-[color:var(--xx-ink)]">încetează în momentul desigilării</strong>. Această
            categorie este afișată distinct pe pagina fiecărui produs căruia i se aplică.
          </p>
        </div>
      </Reveal>

      <Reveal delay={180}>
        <div className="card card-static p-6 sm:p-8">
          <div className="mb-3 flex items-center gap-2.5">
            <NeonBadge tone="warning">Produse testate / resigilate</NeonBadge>
          </div>
          <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">
            Retur acceptat doar dacă produsul este defect
          </h2>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Produsele vândute ca testate, resigilate sau second-hand au starea reală declarată explicit în fișa
            produsului la momentul cumpărării, iar cumpărătorul confirmă separat, la finalizarea comenzii, că a
            luat cunoștință de această stare. O caracteristică sau un defect comunicat expres și acceptat astfel
            face parte din produsul cumpărat — deci{' '}
            <strong className="text-[color:var(--xx-ink)]">nu poate fi invocat ulterior ca motiv de retur</strong>.
          </p>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Pentru aceste produse, returul este acceptat exclusiv atunci când produsul prezintă un{' '}
            <strong className="text-[color:var(--xx-ink)]">defect real, nedeclarat la vânzare</strong>, constatat
            printr-o evaluare tehnică. Cererile motivate de schimbarea opțiunii cumpărătorului, fără legătură cu o
            defecțiune efectivă, nu se încadrează în această procedură de retur, ci sunt tratate, dacă este cazul,
            potrivit condițiilor generale de mai sus.
          </p>
        </div>
      </Reveal>

      <Reveal delay={240}>
        <div className="card card-static p-6 sm:p-8">
          <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">Procedura de retur</h2>
          <ol className="mt-3 space-y-2.5 text-sm leading-relaxed xx-ink-muted">
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                1
              </span>
              Solicitarea de retur se transmite în scris, în termenul de mai sus, cu numărul comenzii și motivul.
            </li>
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                2
              </span>
              Produsul se ambalează cu grijă, cu toate accesoriile primite, și se însoțește de fotografii/video ale
              stării produsului înainte de expediere — necesare pentru evaluarea diminuării de valoare.
            </li>
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                3
              </span>
              La recepție, produsul intră într-o evaluare tehnică ce stabilește starea reală și, unde e cazul,
              deducerea pentru diminuarea valorii.
            </li>
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                4
              </span>
              Rambursarea se efectuează în cel mult <Reper>14 zile</Reper> de la primirea produsului sau de la
              dovada expedierii acestuia, folosind aceeași metodă de plată utilizată la comandă.
            </li>
          </ol>
        </div>
      </Reveal>
    </div>
  );
}
