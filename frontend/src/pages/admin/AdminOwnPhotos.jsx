import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { GeoIcon, NeonBadge, NeonButton, SectionHeader } from '../../components/xxii';
import { showToast } from '../../components/Toast';
import productService from '../../api/productService';

/**
 * Încărcarea în masă a fotografiilor proprii, cu asociere automată la produse.
 *
 * <h2>De ce asta acoperă ce nicio altă sursă nu poate</h2>
 *
 * Catalogul conține SmallRig, iFi, Hori, Brembo, Lightech — suporturi de
 * cameră, audio de nișă, accesorii de gaming, piese de motocicletă. Icecat le
 * potrivește pe sub 1%, pentru că este un catalog de IT și electronice de larg
 * consum. Măsurat: o potrivire din 37 de produse interogabile.
 *
 * <p>Produsele sunt însă fizic în depozit. O fotografie făcută cu telefonul
 * acoperă 100% din catalog, nu depinde de nimeni, și devine un activ pe care
 * nimeni nu îl poate contesta — exact motivul pentru care magazinele mari își
 * fotografiază singure marfa.</p>
 *
 * <h2>Potrivirea se face aici, nu pe server</h2>
 *
 * Serverul trimite un index de câteva zeci de kiloocteți: identificator,
 * denumire, cod, SKU. Browserul potrivește instantaneu după numele fișierului,
 * fără să urce nimic. Alternativa — trimiterea fotografiilor ca serverul să
 * decidă — ar însemna zeci de megaocteți urcați doar ca să afli ce nu se
 * potrivește.
 *
 * <p>Fotografiile pleacă abia după confirmare, direct la produsul corect, pe
 * ruta de încărcare care exista deja și este testată.</p>
 *
 * <h2>Cum numești fișierele</h2>
 *
 * Numele fișierului decide potrivirea, în ordinea încrederii: codul de produs,
 * SKU-ul, identificatorul numeric, apoi cuvintele din denumire. Un sufix
 * numeric — {@code RM120-2.jpg}, {@code RM120 (3).jpg} — atașează mai multe
 * fotografii aceluiași produs, în ordine.
 *
 * <p>Aici se vede de ce pasul cu marca și codul a venit primul: fără coloana
 * de cod completată, potrivirea după cod nu ar avea cu ce să compare, iar
 * singura variantă ar rămâne cea slabă, pe cuvinte din denumire.</p>
 */
export default function AdminOwnPhotos() {
  const [index, setIndex] = useState([]);
  const [incarcaIndex, setIncarcaIndex] = useState(true);
  const [fisiere, setFisiere] = useState([]);
  const [trimite, setTrimite] = useState(false);
  const [progres, setProgres] = useState(null);
  const [cautare, setCautare] = useState({});
  const inputRef = useRef(null);
  const [dragActiv, setDragActiv] = useState(false);
  // Adresele de previzualizare trăiesc într-un ref, nu în state: funcția de
  // curățare a unui `useEffect` cu listă goală de dependențe vede starea de la
  // primul randament, adică lista goală, și n-ar elibera nimic. Un ref vede
  // întotdeauna valoarea curentă.
  const adreseRef = useRef(new Set());

  useEffect(() => {
    let anulat = false;
    productService
      .photoMatchIndex(true)
      .then((r) => {
        if (!anulat) setIndex(r || []);
      })
      .catch(() => showToast('Nu am putut citi lista de produse.', 'error'))
      .finally(() => {
        if (!anulat) setIncarcaIndex(false);
      });
    return () => {
      anulat = true;
    };
  }, []);

  /**
   * Indexul pregătit pentru comparare: fiecare cheie calculată o singură dată,
   * nu la fiecare fișier. Cu 250 de produse și 60 de fișiere, diferența este
   * între 250 de normalizări și 15 000.
   */
  const indexPregatit = useMemo(
    () =>
      index.map((p) => ({
        ...p,
        kMpn: norm(p.mpn),
        kSku: norm(p.sku),
        cuvinte: cuvinteSemnificative(p.denumire),
      })),
    [index]
  );

  const potriveste = useCallback(
    (numeFisier) => {
      const { baza, ordine } = desparteSufix(numeFisier);
      const cheie = norm(baza);
      if (!cheie) return { produs: null, cum: null, ordine };

      // 0. Numele întreg, înainte de a i se tăia sufixul de ordine. Coduri ca
      //    „NP-BX1-2" sau „MT-09-3" se termină ele însele cu liniuță și cifră,
      //    iar despărțirea le-ar transforma în „NP-BX1", fotografia 2. Un cod
      //    care se potrivește exact, întreg, nu poate fi o coincidență, deci
      //    are prioritate asupra oricărei interpretări a sufixului.
      const cheieIntreaga = norm(numeFisier.replace(/\.[a-z0-9]+$/i, ''));
      if (cheieIntreaga !== cheie) {
        const intreg = indexPregatit.find(
          (x) => (x.kMpn && x.kMpn === cheieIntreaga) || (x.kSku && x.kSku === cheieIntreaga)
        );
        if (intreg) return { produs: intreg, cum: 'cod', ordine: 1 };
      }

      // 1. Cod de produs, potrivire exactă. Cea mai sigură: un MPN este unic.
      let p = indexPregatit.find((x) => x.kMpn && x.kMpn === cheie);
      if (p) return { produs: p, cum: 'cod', ordine };

      // 2. SKU intern, exact.
      p = indexPregatit.find((x) => x.kSku && x.kSku === cheie);
      if (p) return { produs: p, cum: 'sku', ordine };

      // 3. Identificatorul numeric, pentru cine preferă să redenumească în id.
      if (/^\d+$/.test(baza.trim())) {
        const id = Number(baza.trim());
        p = indexPregatit.find((x) => x.id === id);
        if (p) return { produs: p, cum: 'id', ordine };
      }

      // 4. Numele conține codul. Sub patru caractere o potrivire de subșir este
      //    aproape sigur întâmplătoare, deci pragul nu coboară.
      p = indexPregatit.find((x) => x.kMpn && x.kMpn.length >= 4 && cheie.includes(x.kMpn));
      if (p) return { produs: p, cum: 'cod în nume', ordine };

      // 5. Cuvinte din denumire. Slabă prin construcție, deci marcată ca atare
      //    în interfață: propune, nu decide.
      const cuvinteFisier = cuvinteSemnificative(baza);
      if (cuvinteFisier.length) {
        let celMaiBun = null;
        let scorMax = 0;
        for (const x of indexPregatit) {
          const comune = x.cuvinte.filter((c) => cuvinteFisier.includes(c)).length;
          if (comune > scorMax) {
            scorMax = comune;
            celMaiBun = x;
          }
        }
        if (celMaiBun && scorMax >= 2) {
          return { produs: celMaiBun, cum: 'denumire', ordine, slab: true };
        }
      }

      return { produs: null, cum: null, ordine };
    },
    [indexPregatit]
  );

  const adaugaFisiere = (lista) => {
    const noi = Array.from(lista)
      .filter((f) => f.type.startsWith('image/'))
      .map((f) => {
        const m = potriveste(f.name);
        const adresa = URL.createObjectURL(f);
        adreseRef.current.add(adresa);
        return {
          cheie: `${f.name}-${f.size}-${f.lastModified}`,
          file: f,
          nume: f.name,
          marime: f.size,
          previzualizare: adresa,
          productId: m.produs?.id ?? null,
          cum: m.cum,
          slab: !!m.slab,
          ordine: m.ordine,
          stare: 'asteapta',
        };
      });
    setFisiere((prev) => {
      const existente = new Set(prev.map((f) => f.cheie));
      const adaugate = noi.filter((f) => !existente.has(f.cheie));
      // Dacă același fișier a fost tras de două ori, previzualizarea creată
      // pentru duplicat nu ajunge niciodată pe ecran. Se eliberează acum, nu
      // la ieșirea din pagină: o fotografie de telefon ține câțiva megaocteți.
      noi.filter((f) => existente.has(f.cheie)).forEach((f) => uita(f.previzualizare));
      return [...prev, ...adaugate];
    });
  };

  /** Eliberează o previzualizare și o scoate din evidență. */
  const uita = (adresa) => {
    if (!adresa) return;
    URL.revokeObjectURL(adresa);
    adreseRef.current.delete(adresa);
  };

  // Adresele de previzualizare țin fișierul în memorie până sunt revocate.
  // Cu șaizeci de fotografii de telefon, asta înseamnă sute de megaocteți care
  // nu se eliberează la navigarea către altă pagină de administrare.
  useEffect(() => {
    const adrese = adreseRef.current;
    return () => {
      adrese.forEach((a) => URL.revokeObjectURL(a));
      adrese.clear();
    };
  }, []);

  const potrivite = fisiere.filter((f) => f.productId && f.stare !== 'gata');
  const nepotrivite = fisiere.filter((f) => !f.productId && f.stare !== 'gata');
  const gata = fisiere.filter((f) => f.stare === 'gata');

  const trimiteTot = async () => {
    // Grupate pe produs: un singur apel per produs, cu toate fotografiile lui,
    // în ordinea sufixului. Altfel ordinea galeriei ar depinde de viteza rețelei.
    const peProdus = new Map();
    potrivite.forEach((f) => {
      if (!peProdus.has(f.productId)) peProdus.set(f.productId, []);
      peProdus.get(f.productId).push(f);
    });
    peProdus.forEach((lista) => lista.sort((a, b) => a.ordine - b.ordine));

    setTrimite(true);
    let reusite = 0;
    let esuate = 0;
    let i = 0;
    for (const [productId, lista] of peProdus) {
      i++;
      setProgres({ curent: i, total: peProdus.size });
      try {
        await productService.uploadProductImages(productId, lista.map((f) => f.file));
        reusite += lista.length;
        // Urcate înseamnă ieșite din listă: previzualizarea nu mai are ce să
        // arate, iar fișierul poate elibera memoria imediat, nu la finalul
        // unui lot care poate dura minute.
        lista.forEach((f) => uita(f.previzualizare));
        setFisiere((prev) =>
          prev.map((f) => (f.productId === productId ? { ...f, stare: 'gata' } : f))
        );
      } catch (err) {
        esuate += lista.length;
        setFisiere((prev) =>
          prev.map((f) =>
            f.productId === productId
              ? { ...f, stare: 'eroare', mesaj: err.response?.data?.message || 'a eșuat' }
              : f
          )
        );
      }
    }
    setTrimite(false);
    setProgres(null);
    showToast(
      esuate
        ? `${reusite} fotografii urcate, ${esuate} au eșuat.`
        : `${reusite} fotografii urcate.`,
      esuate ? 'error' : 'success'
    );
  };

  const rezultateCautare = (cheie) => {
    const q = norm(cautare[cheie] || '');
    if (q.length < 2) return [];
    return indexPregatit
      .filter((p) => norm(p.denumire).includes(q) || (p.kMpn && p.kMpn.includes(q)))
      .slice(0, 6);
  };

  return (
    <div className="space-y-6 pb-28">
      <SectionHeader
        eyebrow="Catalog"
        title="Fotografii proprii"
        subtitle="Încarcă în masă. Potrivirea la produs se face după numele fișierului."
        as="h1"
      />

      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragActiv(true);
        }}
        onDragLeave={() => setDragActiv(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragActiv(false);
          adaugaFisiere(e.dataTransfer.files);
        }}
        className={`card card-static grid place-items-center gap-3 border-2 border-dashed p-10 text-center transition-colors duration-xx ${
          dragActiv
            ? 'border-[rgba(34,232,245,0.6)] bg-[rgba(34,232,245,0.05)]'
            : 'border-[rgba(var(--xx-veil),0.16)]'
        }`}
      >
        <GeoIcon name="box" className="h-8 w-8" accent="var(--xx-cyan)" />
        <p className="text-sm text-[color:var(--xx-ink)]">
          Trage fotografiile aici, sau{' '}
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="font-semibold text-[color:var(--xx-cyan)] hover:underline"
          >
            alege fișiere
          </button>
        </p>
        <p className="max-w-lg text-xs leading-relaxed xx-ink-muted">
          Numește fișierul după codul produsului — <span className="font-mono">RM120.jpg</span> —
          și se asociază singur. Pentru mai multe fotografii la același produs, adaugă un număr:{' '}
          <span className="font-mono">RM120-2.jpg</span>. Merge și cu SKU-ul, sau cu
          identificatorul produsului.
        </p>
        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          multiple
          hidden
          onChange={(e) => {
            adaugaFisiere(e.target.files);
            e.target.value = '';
          }}
        />
        {incarcaIndex ? (
          <NeonBadge>se încarcă lista de produse…</NeonBadge>
        ) : (
          <NeonBadge tone="good">{index.length} produse fără fotografie în index</NeonBadge>
        )}
      </div>

      {fisiere.length > 0 && (
        <div className="flex flex-wrap gap-2.5">
          <NeonBadge tone="good">{potrivite.length} potrivite</NeonBadge>
          <NeonBadge tone="warning">{nepotrivite.length} nepotrivite</NeonBadge>
          {gata.length > 0 && <NeonBadge>{gata.length} urcate</NeonBadge>}
          <button
            type="button"
            onClick={() => {
              fisiere.forEach((f) => uita(f.previzualizare));
              setFisiere([]);
              setCautare({});
            }}
            className="text-xs font-semibold uppercase tracking-wider xx-ink-muted hover:underline"
          >
            Golește lista
          </button>
        </div>
      )}

      {fisiere.filter((f) => f.stare !== 'gata').map((f) => {
        const produs = indexPregatit.find((p) => p.id === f.productId);
        return (
          <div key={f.cheie} className="card card-static p-4">
            <div className="flex flex-wrap items-center gap-4">
              <img
                src={f.previzualizare}
                alt=""
                className="h-16 w-16 shrink-0 rounded-lg object-cover"
              />
              <div className="min-w-0 flex-1">
                <p className="truncate font-mono text-xs xx-ink-muted">{f.nume}</p>
                {produs ? (
                  <p className="mt-0.5 truncate text-sm text-[color:var(--xx-ink)]">
                    {produs.denumire}
                  </p>
                ) : (
                  <p className="mt-0.5 text-sm text-[color:var(--xx-amber)]">
                    Niciun produs potrivit — caută mai jos
                  </p>
                )}
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  {f.cum && (
                    <NeonBadge tone={f.slab ? 'warning' : 'good'}>
                      potrivit după {f.cum}
                    </NeonBadge>
                  )}
                  {f.ordine > 1 && <NeonBadge>fotografia {f.ordine}</NeonBadge>}
                  {f.stare === 'eroare' && <NeonBadge tone="critical">{f.mesaj}</NeonBadge>}
                </div>
              </div>

              <div className="flex items-center gap-2">
                {produs && (
                  <button
                    type="button"
                    onClick={() =>
                      setFisiere((prev) =>
                        prev.map((x) =>
                          x.cheie === f.cheie ? { ...x, productId: null, cum: null } : x
                        )
                      )
                    }
                    className="text-xs xx-ink-muted hover:underline"
                  >
                    schimbă
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    uita(f.previzualizare);
                    setFisiere((prev) => prev.filter((x) => x.cheie !== f.cheie));
                  }}
                  className="text-xs text-[color:var(--xx-red)] hover:underline"
                >
                  scoate
                </button>
              </div>
            </div>

            {!produs && (
              <div className="mt-3 border-t border-[rgba(var(--xx-veil),0.08)] pt-3">
                <input
                  value={cautare[f.cheie] || ''}
                  onChange={(e) => setCautare((p) => ({ ...p, [f.cheie]: e.target.value }))}
                  placeholder="caută produsul după denumire sau cod"
                  className="input text-sm"
                />
                <div className="mt-2 flex flex-col gap-1">
                  {rezultateCautare(f.cheie).map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => {
                        setFisiere((prev) =>
                          prev.map((x) =>
                            x.cheie === f.cheie
                              ? { ...x, productId: p.id, cum: 'ales manual', slab: false }
                              : x
                          )
                        );
                        setCautare((c) => ({ ...c, [f.cheie]: '' }));
                      }}
                      className="truncate rounded-lg px-2 py-1.5 text-left text-sm xx-ink-muted transition-colors duration-xx hover:bg-[rgba(var(--xx-veil),0.06)] hover:text-[color:var(--xx-ink)]"
                    >
                      {p.denumire}
                      {p.mpn && <span className="ml-2 font-mono text-xs">{p.mpn}</span>}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      })}

      {potrivite.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-[rgba(var(--xx-veil),0.1)] bg-[rgba(var(--xx-panel),0.9)] px-4 py-3 backdrop-blur-xl">
          <div className="mx-auto flex max-w-5xl items-center justify-between gap-4">
            <span className="text-sm xx-ink-muted">
              {progres
                ? `produsul ${progres.curent} din ${progres.total}…`
                : `${potrivite.length} fotografii gata de urcat`}
            </span>
            <NeonButton onClick={trimiteTot} disabled={trimite}>
              {trimite ? 'Se urcă…' : 'Urcă fotografiile'}
            </NeonButton>
          </div>
        </div>
      )}
    </div>
  );
}

/** Majuscule, fără diacritice, doar litere și cifre. */
function norm(valoare) {
  if (!valoare) return '';
  return valoare
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}

/**
 * Desparte numele de sufixul de ordine.
 *
 * `RM120-2.jpg`, `RM120 (3).jpg` și `RM120_4.jpg` sunt toate fotografii ale
 * aceluiași produs. Fără pasul acesta, a doua fotografie ar căuta un produs cu
 * codul „RM1202" și nu l-ar găsi.
 *
 * <h2>De ce separatorul este obligatoriu</h2>
 *
 * Sufixul se acceptă numai despărțit explicit: printr-un separator (spațiu,
 * punct, liniuță, underscore) sau prin paranteze. O variantă permisivă, care
 * accepta cifrele lipite de nume, tăia `RM120` în produsul `RM1`, fotografia
 * 20 — adică exact cazul obișnuit, un cod care se termină în cifre. Regula
 * corectă este că un număr de ordine este ceva ce omul adaugă la nume, iar
 * când adaugi ceva la un nume pui un separator.
 *
 * <h2>De ce zeroul din față descalifică sufixul</h2>
 *
 * `MT-09.jpg` este motocicleta Yamaha MT-09, nu fotografia a noua a unui
 * produs „MT". Nimeni nu numerotează fotografiile `-01`, `-02`; oamenii scriu
 * `-1`, `-2`, iar Windows scrie `(2)`. Un zero în față este, practic
 * întotdeauna, parte din codul produsului. Cazurile rămase ambigue sunt prinse
 * mai devreme, de potrivirea pe numele întreg.
 */
function desparteSufix(numeFisier) {
  const faraExtensie = numeFisier.replace(/\.[a-z0-9]+$/i, '');
  const inParanteze = faraExtensie.match(/^(.*\S)\s*[([]\s*([1-9]\d?)\s*[)\]]\s*$/);
  const cuSeparator = inParanteze || faraExtensie.match(/^(.*[^\s._-])[\s._-]+([1-9]\d?)$/);
  if (cuSeparator && cuSeparator[1].trim().length >= 2) {
    return { baza: cuSeparator[1], ordine: Number(cuSeparator[2]) };
  }
  return { baza: faraExtensie, ordine: 1 };
}

/** Cuvintele lungi dintr-un text, pentru potrivirea slabă pe denumire. */
function cuvinteSemnificative(text) {
  if (!text) return [];
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toUpperCase()
    .split(/[^A-Z0-9]+/)
    .filter((c) => c.length >= 4);
}
