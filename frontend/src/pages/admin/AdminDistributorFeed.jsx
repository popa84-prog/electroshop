import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { GeoIcon, NeonBadge, NeonButton, SectionHeader } from '../../components/xxii';
import { showToast } from '../../components/Toast';
import productService from '../../api/productService';
import adminService from '../../api/adminService';

/** Câte imagini încap într-o cerere. Aceeași limită ca pe server. */
const IMAGINI_PE_TRANSA = 40;

/**
 * Preluarea fotografiilor din feedul unui distribuitor.
 *
 * <h2>De ce ecranul cere două lucruri înainte de orice</h2>
 *
 * Tehnic, a lua o fotografie din feed și a lua una de pe site-ul unui magazin
 * este aceeași operație. Juridic sunt opuse. CJUE a stabilit în Renckhoff
 * (C-161/17) că republicarea unei fotografii pe serverul propriu este
 * reproducere neautorizată chiar dacă sursa era liber accesibilă, iar în
 * GS Media (C-160/15) că un site comercial este prezumat să cunoască
 * nelegalitatea sursei. Legea 8/1996, art. 139 alin. (2) lit. b) permite
 * despăgubiri de trei ori remunerația datorată.
 *
 * <p>Feedul de distribuitor este legitim pentru că distribuitorul îl pune la
 * dispoziție ca revânzătorii să îi vândă marfa. Dar dreptul acela nu se
 * presupune, se dovedește — de aceea furnizorul și temeiul folosirii sunt
 * obligatorii, iar amândouă se scriu pe fiecare imagine preluată. Fără ele, la
 * o verificare, fotografia este imposibil de deosebit de una copiată.</p>
 *
 * <h2>Ce se bifează singur și ce nu</h2>
 *
 * Potrivirea pe EAN sau pe cod de producător vine bifată: codul de bare
 * identifică articolul fizic, nu seamănă cu el. Potrivirea pe denumire sau pe
 * codul intern al distribuitorului nu vine bifată, pentru că o fotografie
 * greșită se vede abia când clientul deschide cutia, iar atunci costă un retur,
 * nu un clic.
 *
 * <h2>De ce trimiterea se face în tranșe</h2>
 *
 * Fiecare imagine este o cerere pe care Cloudinary o face către distribuitor.
 * Două sute de potriviri cu trei fotografii fiecare sunt șase sute de preluări
 * într-o singură cerere HTTP — care ar expira, lăsând operatorul cu o eroare
 * pentru o operație reușită pe jumătate. Se trimit maximum {@link
 * IMAGINI_PE_TRANSA} pe cerere, iar progresul se vede.
 */
export default function AdminDistributorFeed() {
  const [furnizori, setFurnizori] = useState([]);
  const [supplierId, setSupplierId] = useState('');
  const [temei, setTemei] = useState('');
  const [fisier, setFisier] = useState(null);
  const [doarFaraImagine, setDoarFaraImagine] = useState(true);
  const [raport, setRaport] = useState(null);
  const [analizeaza, setAnalizeaza] = useState(false);
  const [trimite, setTrimite] = useState(false);
  const [progres, setProgres] = useState(null);
  const [randuri, setRanduri] = useState({});
  const [gata, setGata] = useState([]);
  const inputRef = useRef(null);

  useEffect(() => {
    adminService
      .listSuppliers({ page: 0, size: 200 })
      .then((d) => setFurnizori(d?.content || []))
      .catch(() => showToast('Lista de furnizori nu a putut fi citită.', 'error'));
  }, []);

  const citesteFeed = useCallback(async () => {
    if (!fisier) {
      showToast('Alege întâi fișierul feedului.', 'info');
      return;
    }
    setAnalizeaza(true);
    setGata([]);
    try {
      const r = await productService.feedAnalizeaza(fisier, doarFaraImagine, 300);
      setRaport(r);
      // Bifate din start numai potrivirile sigure. Toate imaginile propuse ale
      // unui rând bifat sunt acceptate implicit: dacă produsul este cel corect,
      // fotografiile lui sunt tot ale lui.
      const initial = {};
      (r.potriviri || []).forEach((p) => {
        initial[p.productId] = {
          bifat: !!p.sigura,
          imagini: new Set(p.imagini || []),
        };
      });
      setRanduri(initial);
      if (!(r.potriviri || []).length) {
        showToast('Feedul a fost citit, dar nimic nu s-a legat de catalog.', 'info');
      }
    } catch (err) {
      showToast(err.response?.data?.message || 'Citirea feedului a eșuat.', 'error');
      setRaport(null);
    } finally {
      setAnalizeaza(false);
    }
  }, [fisier, doarFaraImagine]);

  const potriviri = raport?.potriviri || [];

  const comuta = (id, valoare) =>
    setRanduri((prev) => ({ ...prev, [id]: { ...prev[id], bifat: valoare } }));

  const comutaImagine = (id, adresa) =>
    setRanduri((prev) => {
      const r = prev[id];
      if (!r) return prev;
      const set = new Set(r.imagini);
      if (set.has(adresa)) set.delete(adresa);
      else set.add(adresa);
      return { ...prev, [id]: { ...r, imagini: set } };
    });

  const toate = (valoare) =>
    setRanduri((prev) => {
      const next = {};
      Object.entries(prev).forEach(([id, r]) => {
        next[id] = { ...r, bifat: valoare };
      });
      return next;
    });

  /** Rândurile bifate care au cel puțin o imagine acceptată. */
  const deTrimis = useMemo(
    () =>
      potriviri
        .filter((p) => randuri[p.productId]?.bifat)
        .map((p) => ({
          productId: p.productId,
          imagini: (p.imagini || []).filter((a) => randuri[p.productId].imagini.has(a)),
          codProducator: p.codFeed || null,
          gtin: p.gtinFeed || null,
          codDistribuitor: null,
        }))
        .filter((i) => i.imagini.length > 0 && !gata.includes(i.productId)),
    [potriviri, randuri, gata]
  );

  const totalImagini = deTrimis.reduce((s, i) => s + i.imagini.length, 0);

  /**
   * Împarte confirmările în tranșe de cel mult {@link IMAGINI_PE_TRANSA}
   * imagini. Un produs nu se taie în două tranșe: altfel ordinea galeriei ar
   * depinde de unde a căzut granița, iar a doua tranșă ar putea eșua lăsând
   * produsul cu jumătate din fotografii.
   */
  const transe = (lista) => {
    const out = [];
    let curenta = [];
    let n = 0;
    lista.forEach((i) => {
      if (curenta.length && n + i.imagini.length > IMAGINI_PE_TRANSA) {
        out.push(curenta);
        curenta = [];
        n = 0;
      }
      curenta.push(i);
      n += i.imagini.length;
    });
    if (curenta.length) out.push(curenta);
    return out;
  };

  const preia = async () => {
    if (!supplierId) {
      showToast('Selectează furnizorul: proveniența se scrie pe fiecare imagine.', 'error');
      return;
    }
    if (!temei.trim()) {
      showToast('Scrie temeiul folosirii imaginilor.', 'error');
      return;
    }
    const loturi = transe(deTrimis);
    setTrimite(true);
    let imagini = 0;
    let produse = 0;
    const esecuri = [];

    for (let i = 0; i < loturi.length; i++) {
      setProgres({ curent: i + 1, total: loturi.length });
      try {
        // eslint-disable-next-line no-await-in-loop
        const r = await productService.feedAplica({
          supplierId: Number(supplierId),
          temeiLicenta: temei.trim(),
          intrari: loturi[i],
        });
        imagini += r.imaginiUrcate || 0;
        produse += r.produseModificate || 0;
        (r.esecuri || []).forEach((e) => esecuri.push(e));
        setGata((prev) => [...prev, ...loturi[i].map((x) => x.productId)]);
      } catch (err) {
        esecuri.push({
          productId: null,
          motiv: err.response?.data?.message || 'tranșa a eșuat',
        });
      }
    }

    setTrimite(false);
    setProgres(null);
    showToast(
      esecuri.length
        ? `${imagini} fotografii preluate pentru ${produse} produse, ${esecuri.length} eșecuri.`
        : `${imagini} fotografii preluate pentru ${produse} produse.`,
      esecuri.length ? 'error' : 'success'
    );
    if (esecuri.length) {
      setRaport((prev) => (prev ? { ...prev, esecuri } : prev));
    }
  };

  return (
    <div className="space-y-6 pb-28">
      <SectionHeader
        eyebrow="Catalog"
        title="Feed de distribuitor"
        subtitle="Fotografiile mărfii, de la cine ți-o vinde. Proveniența se scrie pe fiecare imagine."
        as="h1"
      />

      <div className="card card-static space-y-5 p-5">
        <div className="grid gap-4 md:grid-cols-2">
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider xx-ink-muted">
              Furnizorul de la care vine feedul
            </span>
            <select
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              className="input text-sm"
            >
              <option value="">alege furnizorul…</option>
              {furnizori.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider xx-ink-muted">
              Temeiul folosirii imaginilor
            </span>
            <input
              value={temei}
              onChange={(e) => setTemei(e.target.value)}
              placeholder="ex. contract distribuție nr. 412/2026"
              maxLength={120}
              className="input text-sm"
            />
          </label>
        </div>

        <p className="border-l-2 border-[rgba(255,194,75,0.5)] pl-3 text-xs leading-relaxed xx-ink-muted">
          Ambele câmpuri se scriu pe fiecare fotografie preluată și rămân verificabile. O
          fotografie fără ele este, la o verificare, imposibil de deosebit de una copiată de pe un
          site — iar pentru asta Legea 8/1996 permite despăgubiri de trei ori remunerația datorată.
          Feedul distribuitorului este altceva: el îl pune la dispoziție tocmai ca revânzătorii lui
          să îi vândă marfa.
        </p>

        <div className="flex flex-wrap items-center gap-4">
          <NeonButton variant="ghost" onClick={() => inputRef.current?.click()}>
            {fisier ? 'Schimbă fișierul' : 'Alege feedul'}
          </NeonButton>
          <input
            ref={inputRef}
            type="file"
            accept=".xlsx,.xlsm,.xls,.xml"
            hidden
            onChange={(e) => {
              setFisier(e.target.files?.[0] || null);
              setRaport(null);
              e.target.value = '';
            }}
          />
          {fisier && (
            <span className="font-mono text-xs xx-ink-muted">
              {fisier.name} · {(fisier.size / 1048576).toFixed(1)} MB
            </span>
          )}

          <label className="flex items-center gap-2.5 text-sm xx-ink-muted">
            <input
              type="checkbox"
              checked={doarFaraImagine}
              onChange={(e) => setDoarFaraImagine(e.target.checked)}
              className="h-4 w-4 accent-[color:var(--xx-cyan)]"
            />
            Doar produsele fără fotografie
          </label>

          <NeonButton onClick={citesteFeed} disabled={analizeaza || !fisier}>
            {analizeaza ? 'Se citește…' : 'Citește feedul'}
          </NeonButton>
        </div>

        <p className="text-xs leading-relaxed xx-ink-muted">
          Se acceptă .xlsx și .xml, până la 25 MB. Coloanele se recunosc singure: EAN, cod
          producător, denumire, imagine — în română sau engleză. Citirea nu preia nicio fotografie
          și nu schimbă nimic; se poate repeta.
        </p>
      </div>

      {raport && (
        <div className="card card-static space-y-4 p-5">
          <div className="flex flex-wrap gap-2.5">
            <NeonBadge tone="good">{raport.potrivite} potrivite</NeonBadge>
            <NeonBadge tone="warning">{raport.nepotrivite} nepotrivite</NeonBadge>
            <NeonBadge>{raport.randuriFeed} rânduri în feed</NeonBadge>
            {raport.sariteAuImagine > 0 && (
              <NeonBadge>{raport.sariteAuImagine} au deja fotografie</NeonBadge>
            )}
            {raport.faraFotografie > 0 && (
              <NeonBadge tone="warning">{raport.faraFotografie} fără adresă de poză</NeonBadge>
            )}
            {raport.faraIdentitate > 0 && (
              <NeonBadge tone="critical">{raport.faraIdentitate} fără identificare</NeonBadge>
            )}
          </div>

          {raport.dupaCheie && Object.keys(raport.dupaCheie).length > 0 && (
            <div className="flex flex-wrap gap-2.5">
              {Object.entries(raport.dupaCheie).map(([cheie, n]) => (
                <NeonBadge key={cheie} tone={cheie === 'denumire' ? 'warning' : 'aqua'}>
                  {n} după {cheie}
                </NeonBadge>
              ))}
            </div>
          )}

          {raport.coloaneGasite && Object.keys(raport.coloaneGasite).length > 0 && (
            <div className="border-t border-[rgba(var(--xx-veil),0.08)] pt-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider xx-ink-muted">
                Ce a fost citit ca ce
                {raport.elementXml ? ` · element XML: ${raport.elementXml}` : ''}
              </p>
              <div className="flex flex-wrap gap-2">
                {Object.entries(raport.coloaneGasite).map(([coloana, camp]) => (
                  <span
                    key={coloana}
                    className="rounded-md border border-[rgba(var(--xx-veil),0.14)] px-2 py-0.5 text-[0.68rem] xx-ink-muted"
                  >
                    <span className="font-mono">{coloana}</span> → {camp}
                  </span>
                ))}
              </div>
              <p className="mt-2 text-xs leading-relaxed xx-ink-muted">
                Verifică lista asta întâi. O coloană citită greșit produce potriviri greșite pentru
                tot feedul, iar aici se vede dintr-o privire.
              </p>
            </div>
          )}

          {raport.esecuri?.length > 0 && (
            <div className="border-t border-[rgba(var(--xx-veil),0.08)] pt-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-[color:var(--xx-red)]">
                Preluări eșuate
              </p>
              <ul className="space-y-1">
                {raport.esecuri.slice(0, 12).map((e, i) => (
                  <li key={`${e.productId}-${i}`} className="text-xs xx-ink-muted">
                    {e.productId ? `#${e.productId}` : 'tranșă'} — {e.motiv}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {potriviri.length > 0 && (
        <div className="card card-static overflow-hidden p-0">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[rgba(var(--xx-veil),0.08)] p-4">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => toate(true)}
                className="text-xs font-semibold uppercase tracking-wider text-[color:var(--xx-cyan)] hover:underline"
              >
                Bifează tot
              </button>
              <span className="xx-ink-dim">·</span>
              <button
                type="button"
                onClick={() => toate(false)}
                className="text-xs font-semibold uppercase tracking-wider xx-ink-muted hover:underline"
              >
                Debifează tot
              </button>
            </div>
            <span className="text-xs xx-ink-muted">
              {deTrimis.length} produse · {totalImagini} fotografii
            </span>
          </div>

          <div className="divide-y divide-[rgba(var(--xx-veil),0.06)]">
            {potriviri.map((p) => {
              const r = randuri[p.productId] || { bifat: false, imagini: new Set() };
              const preluat = gata.includes(p.productId);
              return (
                <div
                  key={p.productId}
                  className={`p-4 transition-opacity duration-xx ${
                    preluat ? 'opacity-40' : r.bifat ? '' : 'opacity-60'
                  }`}
                >
                  <div className="flex flex-wrap items-start gap-4">
                    <input
                      type="checkbox"
                      checked={!!r.bifat}
                      disabled={preluat}
                      onChange={(e) => comuta(p.productId, e.target.checked)}
                      className="mt-1 h-4 w-4 accent-[color:var(--xx-cyan)]"
                      aria-label={`Preia fotografiile pentru ${p.denumireNoastra}`}
                    />

                    <div className="min-w-0 flex-1">
                      {/* Cele două denumiri, una sub alta: comparația lor este
                          verificarea principală a operatorului. */}
                      <p
                        className="truncate text-sm text-[color:var(--xx-ink)]"
                        title={p.denumireNoastra}
                      >
                        {p.denumireNoastra}
                      </p>
                      <p className="truncate text-xs xx-ink-muted" title={p.denumireFeed}>
                        în feed: {p.denumireFeed || '—'}
                      </p>

                      <div className="mt-1.5 flex flex-wrap items-center gap-2">
                        <NeonBadge tone={p.sigura ? 'good' : 'warning'}>
                          {p.cum}
                        </NeonBadge>
                        {p.gtinFeed && (
                          <span className="font-mono text-[0.68rem] xx-ink-muted">
                            EAN {p.gtinFeed}
                          </span>
                        )}
                        {p.codFeed && (
                          <span className="font-mono text-[0.68rem] xx-ink-muted">
                            {p.codFeed}
                          </span>
                        )}
                        {p.areImagineDeja && <NeonBadge>are deja fotografie</NeonBadge>}
                        {preluat && <NeonBadge tone="good">preluat</NeonBadge>}
                        <span className="text-[0.68rem] xx-ink-dim">rând {p.randFeed}</span>
                      </div>
                    </div>
                  </div>

                  {/* Previzualizarea se face de la adresa distribuitorului,
                      într-un ecran de administrare privat. Nu este publicare:
                      nimic nu ajunge pe serverul nostru până la confirmare. */}
                  <div className="mt-3 flex flex-wrap gap-2 pl-8">
                    {(p.imagini || []).map((adresa) => {
                      const acceptata = r.imagini.has(adresa);
                      return (
                        <button
                          key={adresa}
                          type="button"
                          disabled={preluat}
                          onClick={() => comutaImagine(p.productId, adresa)}
                          title={acceptata ? 'se preia — clic pentru a exclude' : 'exclusă'}
                          className={`relative h-16 w-16 overflow-hidden rounded-lg border-2 transition-colors duration-xx ${
                            acceptata
                              ? 'border-[rgba(34,232,245,0.7)]'
                              : 'border-[rgba(var(--xx-veil),0.12)] opacity-35'
                          }`}
                        >
                          <img
                            src={adresa}
                            alt=""
                            loading="lazy"
                            referrerPolicy="no-referrer"
                            className="h-full w-full object-cover"
                          />
                          {!acceptata && (
                            <span className="absolute inset-0 grid place-items-center">
                              <GeoIcon
                                name="close"
                                className="h-4 w-4"
                                accent="var(--xx-red)"
                              />
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {deTrimis.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-[rgba(var(--xx-veil),0.1)] bg-[rgba(var(--xx-panel),0.9)] px-4 py-3 backdrop-blur-xl">
          <div className="mx-auto flex max-w-5xl items-center justify-between gap-4">
            <span className="text-sm xx-ink-muted">
              {progres
                ? `tranșa ${progres.curent} din ${progres.total}…`
                : `${totalImagini} fotografii pentru ${deTrimis.length} produse`}
            </span>
            <NeonButton onClick={preia} disabled={trimite}>
              {trimite ? 'Se preiau…' : 'Preia fotografiile'}
            </NeonButton>
          </div>
        </div>
      )}
    </div>
  );
}
