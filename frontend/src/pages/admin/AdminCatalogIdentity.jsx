import { useCallback, useEffect, useMemo, useState } from 'react';
import { NeonBadge, NeonButton, SectionHeader } from '../../components/xxii';
import { showToast } from '../../components/Toast';
import productService from '../../api/productService';

/**
 * Completarea mărcii și a codului de produs din denumire.
 *
 * <h2>De ce ecranul acesta este primul</h2>
 *
 * Măsurat pe catalogul real: din 100 de produse fără fotografie, 36 nu au marcă
 * nicăieri și 27 nu au cod în denumire. <b>63 din 100 nu pot fi identificate de
 * nicio sursă</b> — nici Icecat, nici feed-ul unui distribuitor, nici o
 * asociere automată cu fotografiile făcute de operator. Toate au nevoie de
 * aceeași pereche: marca și codul.
 *
 * <h2>Confirmare în masă, nu formular</h2>
 *
 * Spre deosebire de ecranul de fotografii, unde fiecare potrivire se confirmă
 * separat pentru că o poză greșită produce retururi, aici greșeala este
 * ieftină și reversibilă: un cod greșit înseamnă doar că interogarea
 * următoare nu găsește nimic, iar câmpul se poate rescrie oricând.
 *
 * Așa că valorile vin precompletate, bifate, și operatorul <em>debifează</em>
 * ce e greșit în loc să bifeze ce e bun. Pentru 251 de produse, diferența
 * dintre cele două este între zece minute și o după-amiază.
 *
 * <h2>Ce nu face</h2>
 *
 * Nu suprascrie nimic. Un produs care are deja marcă păstrează marca scrisă de
 * om; propunerea completează doar golurile. Cineva care a scris manual codul a
 * știut mai bine decât o expresie regulată.
 */
export default function AdminCatalogIdentity() {
  const [raport, setRaport] = useState(null);
  const [incarca, setIncarca] = useState(false);
  const [salveaza, setSalveaza] = useState(false);
  const [doarFaraImagine, setDoarFaraImagine] = useState(true);
  // Starea editabilă per rând: ce se va scrie dacă rândul rămâne bifat.
  const [randuri, setRanduri] = useState({});

  const incarcaPropuneri = useCallback(async () => {
    setIncarca(true);
    try {
      const r = await productService.identityProposals(doarFaraImagine, 200);
      setRaport(r);
      const initial = {};
      (r.propuneri || []).forEach((p) => {
        initial[p.productId] = {
          bifat: true,
          marca: p.marcaActuala || p.marcaPropusa || '',
          mpn: p.mpnActual || p.coduriPropuse?.[0]?.cod || '',
        };
      });
      setRanduri(initial);
      if (!(r.propuneri || []).length) {
        showToast('Nu este nimic de completat automat aici.', 'info');
      }
    } catch (err) {
      showToast(err.response?.data?.message || 'Încărcarea a eșuat.', 'error');
    } finally {
      setIncarca(false);
    }
  }, [doarFaraImagine]);

  useEffect(() => {
    incarcaPropuneri();
  }, [incarcaPropuneri]);

  const seteaza = (id, camp, valoare) =>
    setRanduri((prev) => ({ ...prev, [id]: { ...prev[id], [camp]: valoare } }));

  const bifateCount = useMemo(
    () => Object.values(randuri).filter((r) => r.bifat && (r.marca || r.mpn)).length,
    [randuri]
  );

  const toateBifate = (valoare) =>
    setRanduri((prev) => {
      const next = {};
      Object.entries(prev).forEach(([id, r]) => {
        next[id] = { ...r, bifat: valoare };
      });
      return next;
    });

  const salveazaBifate = async () => {
    const intrari = Object.entries(randuri)
      .filter(([, r]) => r.bifat && (r.marca?.trim() || r.mpn?.trim()))
      .map(([id, r]) => ({
        productId: Number(id),
        marca: r.marca?.trim() || null,
        mpn: r.mpn?.trim() || null,
      }));
    if (!intrari.length) {
      showToast('Nu este nimic bifat.', 'info');
      return;
    }
    setSalveaza(true);
    try {
      const r = await productService.identityApply(intrari);
      showToast(
        `${r.marciScrise} mărci și ${r.coduriScrise} coduri completate.`,
        'success'
      );
      await incarcaPropuneri();
    } catch (err) {
      showToast(err.response?.data?.message || 'Salvarea a eșuat.', 'error');
    } finally {
      setSalveaza(false);
    }
  };

  const propuneri = raport?.propuneri || [];

  return (
    <div className="space-y-6 pb-24">
      <SectionHeader
        eyebrow="Catalog"
        title="Marcă și cod de produs"
        subtitle="Completate din denumire. Fără ele, niciun catalog extern nu poate identifica produsele."
        as="h1"
      />

      <div className="card card-static p-5">
        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2.5 text-sm xx-ink-muted">
            <input
              type="checkbox"
              checked={doarFaraImagine}
              onChange={(e) => setDoarFaraImagine(e.target.checked)}
              className="h-4 w-4 accent-[color:var(--xx-cyan)]"
            />
            Doar produsele fără fotografie
          </label>

          <NeonButton onClick={incarcaPropuneri} disabled={incarca} variant="ghost">
            {incarca ? 'Se încarcă…' : 'Reîncarcă'}
          </NeonButton>

          {raport && (
            <div className="flex flex-wrap gap-2.5">
              <NeonBadge tone="good">{raport.complete} complete</NeonBadge>
              <NeonBadge tone="warning">{raport.incomplete} incomplete</NeonBadge>
              <NeonBadge tone="critical">{raport.faraMarcaDeloc} fără marcă deductibilă</NeonBadge>
              <NeonBadge tone="critical">{raport.faraCodDeloc} fără cod deductibil</NeonBadge>
            </div>
          )}
        </div>

        {raport && raport.faraMarcaDeloc + raport.faraCodDeloc > 0 && (
          <p className="mt-4 border-t border-[rgba(var(--xx-veil),0.08)] pt-3 text-xs leading-relaxed xx-ink-muted">
            Produsele numărate ca „fără marcă deductibilă" nu apar mai jos, pentru că nu există
            nimic de bifat. Denumiri ca „7inch car 2din" nu conțin nici marcă, nici cod — pentru
            ele identitatea se scrie manual sau produsul rămâne pe fotografie proprie.
          </p>
        )}
      </div>

      {propuneri.length > 0 && (
        <div className="card card-static overflow-hidden p-0">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[rgba(var(--xx-veil),0.08)] p-4">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => toateBifate(true)}
                className="text-xs font-semibold uppercase tracking-wider text-[color:var(--xx-cyan)] hover:underline"
              >
                Bifează tot
              </button>
              <span className="xx-ink-dim">·</span>
              <button
                type="button"
                onClick={() => toateBifate(false)}
                className="text-xs font-semibold uppercase tracking-wider xx-ink-muted hover:underline"
              >
                Debifează tot
              </button>
            </div>
            <span className="text-xs xx-ink-muted">
              {bifateCount} din {propuneri.length} bifate
            </span>
          </div>

          <div className="divide-y divide-[rgba(var(--xx-veil),0.06)]">
            {propuneri.map((p) => {
              const r = randuri[p.productId] || {};
              return (
                <div
                  key={p.productId}
                  className={`grid gap-3 p-4 transition-colors duration-xx md:grid-cols-[auto_1fr_11rem_11rem] md:items-center ${
                    r.bifat ? '' : 'opacity-45'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={!!r.bifat}
                    onChange={(e) => seteaza(p.productId, 'bifat', e.target.checked)}
                    className="h-4 w-4 accent-[color:var(--xx-cyan)]"
                    aria-label={`Completează ${p.denumire}`}
                  />

                  <div className="min-w-0">
                    <p className="truncate text-sm text-[color:var(--xx-ink)]" title={p.denumire}>
                      {p.denumire}
                    </p>
                    {p.coduriPropuse?.length > 1 && (
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {p.coduriPropuse.map((c) => (
                          <button
                            key={c.cod}
                            type="button"
                            onClick={() => seteaza(p.productId, 'mpn', c.cod)}
                            title={`Încredere ${Math.round(c.scor * 100)}%`}
                            className={`rounded-md border px-1.5 py-0.5 font-mono text-[0.68rem] transition-colors duration-xx ${
                              r.mpn === c.cod
                                ? 'border-[rgba(34,232,245,0.6)] text-[color:var(--xx-cyan)]'
                                : 'border-[rgba(var(--xx-veil),0.14)] xx-ink-muted hover:border-[rgba(34,232,245,0.4)]'
                            }`}
                          >
                            {c.cod}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <input
                    value={r.marca || ''}
                    onChange={(e) => seteaza(p.productId, 'marca', e.target.value)}
                    placeholder="marcă"
                    disabled={!!p.marcaActuala}
                    title={p.marcaActuala ? 'Marca există deja și nu se suprascrie.' : 'Marca'}
                    className="input text-sm disabled:opacity-50"
                  />

                  <input
                    value={r.mpn || ''}
                    onChange={(e) => seteaza(p.productId, 'mpn', e.target.value)}
                    placeholder="cod produs"
                    disabled={!!p.mpnActual}
                    title={p.mpnActual ? 'Codul există deja și nu se suprascrie.' : 'Cod de produs'}
                    className="input font-mono text-sm disabled:opacity-50"
                  />
                </div>
              );
            })}
          </div>
        </div>
      )}

      {propuneri.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-[rgba(var(--xx-veil),0.1)] bg-[rgba(var(--xx-panel),0.9)] px-4 py-3 backdrop-blur-xl">
          <div className="mx-auto flex max-w-5xl items-center justify-between gap-4">
            <span className="text-sm xx-ink-muted">
              {bifateCount} produse de completat
            </span>
            <NeonButton onClick={salveazaBifate} disabled={salveaza || bifateCount === 0}>
              {salveaza ? 'Se salvează…' : 'Completează bifatele'}
            </NeonButton>
          </div>
        </div>
      )}
    </div>
  );
}
