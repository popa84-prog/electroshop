import { GeoIcon, NeonBadge, Reveal, SectionHeader } from '../components/xxii';
import { useSeo } from '../utils/seo';

/**
 * Condiții de garanție.
 *
 * Structura separă explicit două garanții de natură diferită, pentru că
 * amestecarea lor este exact ce face o politică de garanție neaplicabilă
 * legal:
 *
 *  - garanția legală de conformitate (OUG 140/2021) — obligatorie, 24 de luni
 *    pentru produse noi, redusă contractual la minimul legal de 12 luni
 *    pentru produsele testate/resigilate (art. 9 alin. (5));
 *  - o garanție comercială suplimentară, voluntară, de 7 zile
 *    (art. 17 OUG 140/2021), care oferă rambursare integrală condiționată de
 *    folosirea corespunzătoare a produsului — aici, și doar aici, condițiile
 *    cerute au valoare juridică, pentru că o garanție comercială poate fi
 *    condiționată, spre deosebire de garanția legală.
 */

const Article = ({ children }) => (
  <span className="rounded-md border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] px-1.5 py-0.5 font-mono text-[0.7rem] text-[color:var(--xx-ink-muted)]">
    {children}
  </span>
);

const EXCLUSIONS = [
  'Lovituri, șocuri mecanice sau urme de impact ulterioare livrării.',
  'Contact cu lichide, umiditate excesivă sau expunere la condiții de mediu improprii.',
  'Alimentare electrică necorespunzătoare (tensiune eronată, supratensiune) sau folosire cu accesorii neoriginale.',
  'Intervenții, reparații sau dezmembrări efectuate de persoane neautorizate de vânzător sau de producător.',
  'Folosirea produsului în alte condiții decât cele indicate în manualul producătorului.',
  'Uzura normală a componentelor consumabile (baterii, filtre, consumabile de imprimare etc.).',
];

export default function Garantie() {
  useSeo({
    title: 'Condiții de garanție',
    description: 'Garanția legală de conformitate și garanția comercială de 7 zile, cu condițiile aplicabile.',
    path: '/garantie',
  });

  return (
    <div className="space-y-8 pb-10">
      <SectionHeader
        eyebrow="Informații legale"
        title="Condiții de garanție"
        subtitle="Garanția legală de conformitate și garanția comercială suplimentară de 7 zile."
        as="h1"
      />

      <Reveal>
        <div className="card card-static p-6 sm:p-8">
          <div className="flex items-start gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border border-[rgba(255,255,255,0.13)] bg-[rgba(255,255,255,0.05)]">
              <GeoIcon name="shield" className="h-[1.15rem] w-[1.15rem]" accent="var(--xx-lime)" />
            </span>
            <div>
              <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">Garanția legală de conformitate</h2>
              <p className="mt-2 text-sm leading-relaxed xx-ink-muted">
                Conform <Article>OUG nr. 140/2021</Article>, orice produs vândut beneficiază de garanția legală de
                conformitate, indiferent de existența unei garanții comerciale.
              </p>
            </div>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <div className="rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.03)] p-4">
              <NeonBadge tone="good">Produse noi</NeonBadge>
              <p className="mt-2 text-2xl font-bold text-[color:var(--xx-ink)]">24 de luni</p>
              <p className="mt-1 text-xs xx-ink-muted">
                De la data livrării (<Article>art. 9 alin. (1) OUG 140/2021</Article>).
              </p>
            </div>
            <div className="rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.03)] p-4">
              <NeonBadge tone="warning">Produse testate / resigilate</NeonBadge>
              <p className="mt-2 text-2xl font-bold text-[color:var(--xx-ink)]">12 luni</p>
              <p className="mt-1 text-xs xx-ink-muted">
                Termenul minim permis contractual pentru produse la mâna a doua (
                <Article>art. 9 alin. (5) OUG 140/2021</Article>).
              </p>
            </div>
          </div>

          <p className="mt-5 text-sm leading-relaxed xx-ink-muted">
            În primul an de la livrare, orice neconformitate constatată este prezumată a fi existat deja la
            livrare (<Article>art. 10 alin. (1)</Article>) — sarcina probei revine vânzătorului. După acest
            interval, sarcina probei că neconformitatea exista la livrare revine cumpărătorului.
          </p>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            Remediile legale se aplică în ordine: cumpărătorul poate alege între reparare și înlocuire, cu excepția
            situației în care remediul ales este imposibil sau disproporționat față de celălalt; doar dacă niciunul
            dintre cele două remedii nu poate fi aplicat în termen rezonabil, cumpărătorul poate cere o reducere de
            preț sau rezoluțiunea contractului. Reparațiile în garanție se finalizează în cel mult 15 zile
            calendaristice de la primirea produsului.
          </p>
          <p className="mt-3 text-sm leading-relaxed xx-ink-muted">
            O particularitate sau un defect comunicat expres cumpărătorului și acceptat separat de acesta la
            momentul achiziției (fișa de stare a produselor testate) <strong className="text-[color:var(--xx-ink)]">nu
            constituie neconformitate</strong> (<Article>art. 6 alin. (5)</Article>) și nu poate fi invocat ulterior
            ca temei al unei cereri de garanție.
          </p>
        </div>
      </Reveal>

      <Reveal delay={80}>
        <div className="card card-static p-6 sm:p-8">
          <div className="flex items-start gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border border-[rgba(255,84,112,0.3)] bg-[rgba(255,84,112,0.08)]">
              <GeoIcon name="bolt" className="h-[1.15rem] w-[1.15rem]" accent="var(--xx-red)" />
            </span>
            <div>
              <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">
                Garanția comercială suplimentară — 7 zile
              </h2>
              <p className="mt-2 text-sm leading-relaxed xx-ink-muted">
                Pe lângă garanția legală de mai sus, se oferă voluntar (<Article>art. 17 OUG 140/2021</Article>) un
                drept de <strong className="text-[color:var(--xx-ink)]">rambursare integrală</strong> în primele 7
                zile calendaristice de la livrare, condiționat cumulativ de îndeplinirea tuturor punctelor de mai
                jos.
              </p>
            </div>
          </div>

          <ul className="mt-5 space-y-3">
            {[
              'Produsul funcționează corespunzător, fără nicio defecțiune cauzată de utilizator.',
              'Nu prezintă urme de lovituri, șocuri sau zgârieturi apărute după livrare.',
              'Nu a fost folosit în condiții improprii: umiditate, temperaturi extreme, praf sau nisip pentru echipamente neprotejate, tensiune electrică necorespunzătoare.',
              'Este complet: cutie originală, toate accesoriile, manualele și foliile de protecție primite la livrare.',
              'Nu a suferit nicio intervenție, reparație sau dezmembrare neautorizată.',
            ].map((line) => (
              <li key={line} className="flex items-start gap-2.5 text-sm leading-relaxed xx-ink-muted">
                <GeoIcon name="check" className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-lime)" />
                {line}
              </li>
            ))}
          </ul>

          <p className="mt-5 text-sm leading-relaxed xx-ink-muted">
            Neîndeplinirea oricăreia dintre condițiile de mai sus înseamnă că rambursarea în cadrul garanției
            comerciale de 7 zile este refuzată. Rămâne aplicabilă, în continuare, garanția legală de conformitate
            de mai sus — pentru defecte reale, nu pentru daune provocate ulterior livrării. Garanția comercială de
            7 zile este distinctă de dreptul de retragere din contractele la distanță; cele două nu se cumulează,
            iar condițiile complete privind returul se regăsesc pe pagina{' '}
            <a href="/retur" className="font-semibold text-[color:var(--xx-cyan)] hover:underline">
              Condiții de retur
            </a>
            .
          </p>
        </div>
      </Reveal>

      <Reveal delay={160}>
        <div className="card card-static p-6 sm:p-8">
          <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">Ce nu este acoperit</h2>
          <ul className="mt-3 space-y-2.5">
            {EXCLUSIONS.map((line) => (
              <li key={line} className="flex items-start gap-2.5 text-sm leading-relaxed xx-ink-muted">
                <GeoIcon name="close" className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-red)" />
                {line}
              </li>
            ))}
          </ul>
        </div>
      </Reveal>

      <Reveal delay={220}>
        <div className="card card-static p-6 sm:p-8">
          <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">Procedura de evaluare tehnică</h2>
          <ol className="mt-3 space-y-2.5 text-sm leading-relaxed xx-ink-muted">
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                1
              </span>
              Sesizarea se transmite în scris, cu numărul comenzii, descrierea defectului și, unde este posibil,
              fotografii sau un scurt clip video.
            </li>
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                2
              </span>
              Produsul intră într-o evaluare tehnică ce stabilește dacă defectul reclamat este real și dacă
              reprezintă o neconformitate sau rezultă din utilizare improprie.
            </li>
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                3
              </span>
              Dacă defectul nu se confirmă sau este cauzat de o excludere de mai sus, cererea este respinsă
              motivat, cu raport tehnic, iar produsul este returnat cumpărătorului.
            </li>
            <li className="flex gap-2.5">
              <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[rgba(34,232,245,0.4)] text-[0.65rem] font-semibold text-[color:var(--xx-cyan)]">
                4
              </span>
              Dacă defectul se confirmă ca neconformitate reală, se aplică remediile legale — reparare, înlocuire,
              reducere de preț sau rezoluțiune, după caz.
            </li>
          </ol>
        </div>
      </Reveal>
    </div>
  );
}
