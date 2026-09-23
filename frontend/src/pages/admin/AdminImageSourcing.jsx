import { useCallback, useEffect, useState } from 'react';
import { GeoIcon, NeonBadge, NeonButton, SectionHeader } from '../../components/xxii';
import { showToast } from '../../components/Toast';
import productService from '../../api/productService';

/**
 * Completarea fotografiilor lipsă din catalogul Icecat.
 *
 * <h2>De ce ecranul acesta există, în loc de un buton „completează tot"</h2>
 *
 * Pentru că potrivirea automată greșește, și greșește convingător. Catalogul
 * conține „Acumulator pentru Sony DSC-RX100 Sony NP-BX1": codul cu cea mai mare
 * încredere este aparatul foto, nu acumulatorul care se vinde. Icecat are fișe
 * pentru amândouă și ar întoarce o fotografie perfect validă — a produsului
 * greșit. O poză greșită pe produsul greșit produce comandă, retur și
 * reclamație, deci este mai rea decât absența ei.
 *
 * De aceea fiecare potrivire se arată cu denumirea noastră lângă denumirea din
 * Icecat. Comparația celor două este, practic, întreaga verificare: dacă scrie
 * „Acumulator Sony NP-BX1" la noi și „Sony Cyber-shot DSC-RX100" acolo, se vede
 * dintr-o privire.
 *
 * <h2>Ce nu face</h2>
 *
 * Nu publică nimic singur și nu ține o coadă. Fiecare confirmare este un apel
 * separat, iar o potrivire pe care o ignori pur și simplu nu se aplică. Nu
 * există „respinge", pentru că nu există nimic de respins — nimic nu s-a
 * întâmplat până la apăsarea butonului.
 */
export default function AdminImageSourcing() {
  const [status, setStatus] = useState(null);
  const [raport, setRaport] = useState(null);
  const [cauta, setCauta] = useState(false);
  const [limita, setLimita] = useState(25);
  // Ce s-a aplicat deja în sesiunea aceasta, ca rândurile confirmate să dispară
  // fără o nouă căutare — o nouă căutare ar consuma din nou din cota Icecat.
  const [aplicate, setAplicate] = useState({});
  const [inLucru, setInLucru] = useState(null);

  useEffect(() => {
    const ctrl = new AbortController();
    productService
      .imageSourcingStatus(ctrl.signal)
      .then(setStatus)
      .catch(() => setStatus({ configurat: false }));
    return () => ctrl.abort();
  }, []);

  const cautaPotriviri = useCallback(async () => {
    setCauta(true);
    try {
      const r = await productService.imageSourcingProposals(limita);
      setRaport(r);
      if (!r.propuneri?.length) {
        showToast('Nicio potrivire în lotul acesta. Încearcă un lot mai mare.', 'info');
      }
    } catch (err) {
      showToast(err.response?.data?.message || 'Căutarea a eșuat.', 'error');
    } finally {
      setCauta(false);
    }
  }, [limita]);

  const aplica = async (p, adresa) => {
    setInLucru(p.productId);
    try {
      await productService.imageSourcingApply({
        productId: p.productId,
        adresa,
        icecatId: p.icecatId,
        marca: p.marcaInterogata,
        mpn: p.codMarca || p.codIncercat,
        gtin: p.gtin,
      });
      setAplicate((prev) => ({ ...prev, [p.productId]: adresa }));
      showToast(
        p.gtin
          ? `Imagine atașată. Codul de bare ${p.gtin} a fost completat.`
          : 'Imagine atașată produsului.',
        'success'
      );
    } catch (err) {
      showToast(err.response?.data?.message || 'Preluarea imaginii a eșuat.', 'error');
    } finally {
      setInLucru(null);
    }
  };

  const neconfigurat = status && !status.configurat;
  const propuneri = (raport?.propuneri || []).filter((p) => !aplicate[p.productId]);

  return (
    <div className="space-y-6 pb-10">
      <SectionHeader
        eyebrow="Catalog"
        title="Fotografii din Icecat"
        subtitle="Potriviri propuse pentru produsele fără imagine. Fiecare se confirmă separat."
        as="h1"
      />

      {neconfigurat && (
        <div className="card card-static border-[rgba(255,194,75,0.4)] p-5">
          <div className="flex items-start gap-3">
            <GeoIcon name="alert" className="mt-0.5 h-5 w-5 shrink-0" accent="var(--xx-amber)" />
            <div className="space-y-2 text-sm leading-relaxed xx-ink-muted">
              <p className="font-semibold text-[color:var(--xx-ink)]">
                Integrarea nu este configurată încă.
              </p>
              <p>
                Deschide un cont gratuit Open Icecat, apoi pune pe server variabilele de mai jos și
                repornește serviciul. Nivelul gratuit acoperă cele circa 600 de mărci care plătesc ca
                fișele și fotografiile lor să ajungă la comercianți — acela este și temeiul pe care
                le putem folosi.
              </p>
              <ul className="space-y-1 font-mono text-xs">
                {(status.variabile || []).map((v) => (
                  <li key={v} className="text-[color:var(--xx-ink)]">
                    {v}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}

      <div className="card card-static p-5">
        <div className="flex flex-wrap items-end gap-4">
          <label className="flex flex-col gap-1.5 text-xs font-semibold uppercase tracking-wider xx-ink-muted">
            Produse examinate
            <select
              value={limita}
              onChange={(e) => setLimita(Number(e.target.value))}
              className="input w-40"
              disabled={cauta || neconfigurat}
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </label>

          <NeonButton onClick={cautaPotriviri} disabled={cauta || neconfigurat} variant="primary">
            {cauta ? 'Se caută…' : 'Caută potriviri'}
          </NeonButton>

          <p className="max-w-md text-xs leading-relaxed xx-ink-muted">
            Fiecare produs înseamnă până la trei interogări. Lotul este mic dinadins — nivelul
            gratuit Icecat are o cotă lunară, iar o căutare peste tot catalogul ar consuma-o fără să
            producă mai multe potriviri.
          </p>
        </div>

        {raport && (
          <div className="mt-5 flex flex-wrap gap-2.5 border-t border-[rgba(var(--xx-veil),0.08)] pt-4">
            <NeonBadge tone="good">{raport.gasite} potriviri</NeonBadge>
            <NeonBadge>{raport.examinate} examinate</NeonBadge>
            <NeonBadge tone="warning">{raport.sariteFaraMarca} fără marcă</NeonBadge>
            <NeonBadge tone="warning">{raport.sariteFaraCod} fără cod în denumire</NeonBadge>
            <NeonBadge tone="critical">{raport.totalFaraImagine} fără imagine, total</NeonBadge>
          </div>
        )}
      </div>

      {propuneri.map((p) => (
        <div key={p.productId} className="card card-static p-5">
          <div className="grid gap-5 lg:grid-cols-[1fr_auto]">
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs uppercase tracking-wider xx-ink-muted">La noi</span>
                {p.marcaGhicita && (
                  <NeonBadge tone="warning">marcă dedusă din denumire</NeonBadge>
                )}
                <NeonBadge>{Math.round(p.increderea * 100)}% încredere în cod</NeonBadge>
              </div>
              <p className="text-sm font-semibold text-[color:var(--xx-ink)]">{p.denumireNoastra}</p>

              <div className="border-t border-[rgba(var(--xx-veil),0.08)] pt-3">
                <span className="text-xs uppercase tracking-wider xx-ink-muted">În Icecat</span>
                <p className="mt-1 text-sm text-[color:var(--xx-ink)]">
                  {p.titluIcecat || '(fără denumire)'}
                </p>
              </div>

              <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs xx-ink-muted sm:grid-cols-4">
                <div>
                  <dt className="uppercase tracking-wider">Marcă</dt>
                  <dd className="font-mono text-[color:var(--xx-ink)]">{p.marcaInterogata}</dd>
                </div>
                <div>
                  <dt className="uppercase tracking-wider">Cod căutat</dt>
                  <dd className="font-mono text-[color:var(--xx-ink)]">{p.codIncercat}</dd>
                </div>
                <div>
                  <dt className="uppercase tracking-wider">Cod producător</dt>
                  <dd className="font-mono text-[color:var(--xx-ink)]">{p.codMarca || '—'}</dd>
                </div>
                <div>
                  <dt className="uppercase tracking-wider">Cod de bare</dt>
                  <dd className="font-mono text-[color:var(--xx-ink)]">{p.gtin || '—'}</dd>
                </div>
              </dl>
            </div>

            <div className="flex flex-wrap gap-3 lg:max-w-[22rem]">
              {p.imagini.slice(0, 4).map((adresa) => (
                <button
                  key={adresa}
                  type="button"
                  onClick={() => aplica(p, adresa)}
                  disabled={inLucru === p.productId}
                  title="Folosește această fotografie"
                  className="group relative h-28 w-28 overflow-hidden rounded-xl border border-[rgba(var(--xx-veil),0.14)] bg-[rgba(var(--xx-veil),0.04)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.6)] hover:shadow-glow-aqua disabled:opacity-50"
                >
                  <img
                    src={adresa}
                    alt=""
                    loading="lazy"
                    className="h-full w-full object-contain p-1.5"
                  />
                  <span className="absolute inset-x-0 bottom-0 bg-[rgba(var(--xx-panel),0.85)] py-1 text-center text-[0.65rem] font-semibold text-[color:var(--xx-ink)] opacity-0 transition-opacity duration-xx group-hover:opacity-100">
                    {inLucru === p.productId ? 'se preia…' : 'folosește'}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      ))}

      {raport && propuneri.length === 0 && !cauta && (
        <div className="card card-static p-8 text-center text-sm xx-ink-muted">
          Nicio potrivire de confirmat în lotul acesta.
        </div>
      )}
    </div>
  );
}
