import { GeoIcon, NeonBadge, Reveal, SectionHeader } from '../components/xxii';
import { useSeo } from '../utils/seo';

/**
 * Garanția acordată de vânzător.
 *
 * Pagina a fost redusă deliberat la o singură afirmație. Versiunea anterioară
 * descria pe larg garanția de conformitate — termene, remedii, sarcina probei —
 * plus o garanție comercială de 7 zile cu cinci condiții cumulative. Vânzătorul
 * a decis să comunice o singură garanție, a lui, iar restul textului a fost
 * eliminat.
 *
 * Ruta rămâne activă, deși conținutul s-a redus la câteva rânduri. Ștergerea
 * paginii ar fi lăsat linkurile din footer să ducă în 404, iar un cumpărător
 * care caută garanția ar fi întâlnit o eroare în loc de răspunsul scurt de mai
 * jos — ceea ce este mai rău decât un răspuns scurt.
 */

export default function Garantie() {
  useSeo({
    title: 'Garanție',
    description: 'Garantăm benevol că produsul funcționează la livrare, 3 zile de la primire.',
    path: '/garantie',
  });

  return (
    <div className="space-y-8 pb-10">
      <SectionHeader
        eyebrow="Informații"
        title="Garanție"
        subtitle="Garanția acordată de vânzător."
        as="h1"
      />

      <Reveal>
        <div className="card card-static p-6 sm:p-8">
          <div className="flex items-start gap-3">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl border border-[rgba(var(--xx-veil),0.13)] bg-[rgba(var(--xx-veil),0.05)]">
              <GeoIcon name="shield" className="h-[1.15rem] w-[1.15rem]" accent="var(--xx-lime)" />
            </span>
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-lg font-semibold text-[color:var(--xx-ink)]">
                  Garanție de funcționare la livrare
                </h2>
                <NeonBadge tone="good">3 zile</NeonBadge>
              </div>

              <p className="text-sm leading-relaxed xx-ink-muted">
                Garantăm benevol că produsul funcționează la livrare. Garanția este valabilă 3 zile de
                la primire.
              </p>
            </div>
          </div>
        </div>
      </Reveal>
    </div>
  );
}
