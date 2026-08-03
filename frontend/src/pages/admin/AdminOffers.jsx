import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import {
  GeoIcon,
  geoIconNames,
  HoloInput,
  HoloLoader,
  NeonBadge,
  NeonButton,
  SectionHeader,
} from '../../components/xxii';
import { useDebounce } from '../../hooks/useDebounce';

/**
 * Panoul „Oferte" — tab nou în Quantum Control Center (cerința 1).
 *
 * Fiecare ofertă are două zone posibile de afișare: modulul mare de promoție
 * de pe prima pagină ({@code HOME_PROMO}, o singură ofertă activă la un
 * moment dat) și banda de patru cartonașe de beneficii ({@code BENEFIT_BAR}).
 * Limita de sloturi este impusă de backend (`OfferService`), deci interfața
 * nu trebuie să numere nimic — pur și simplu afișează ce există.
 *
 * Fereastra de timp (`startsAt`/`endsAt`) este complet opțională și separată
 * de comutatorul „Activă": o ofertă poate fi activă dar programată în viitor,
 * sau activă dar deja încheiată. Câmpul calculat `live`, primit din backend,
 * este singurul care spune dacă se afișează chiar acum pe site — de aceea
 * tabelul citește `live`, nu doar `active`.
 */

const emptyForm = {
  title: '',
  headline: '',
  description: '',
  badgeLabel: '',
  ctaLabel: '',
  ctaUrl: '',
  icon: 'tag',
  accent: 'var(--xx-cyan)',
  placement: 'HOME_PROMO',
  active: true,
  startsAt: '',
  endsAt: '',
  showTimer: false,
  recurringDaily: false,
  sortOrder: 0,
};

const PLACEMENT_LABELS = {
  HOME_PROMO: 'Promoție principală',
  BENEFIT_BAR: 'Bandă beneficii',
};

/** Trunchiază un LocalDateTime ISO ("2026-08-03T10:30:00") la formatul cerut de <input type="datetime-local">. */
function toLocalInput(iso) {
  if (!iso) return '';
  return iso.length > 16 ? iso.slice(0, 16) : iso;
}

/** Trimite null în loc de string gol, ca backend-ul să nu primească o dată invalidă. */
function fromLocalInput(value) {
  return value ? value : null;
}

function liveBadge(offer) {
  if (!offer.active) {
    return { tone: 'neutral', label: 'Dezactivată' };
  }
  if (offer.live) {
    return { tone: 'good', label: 'Activă acum' };
  }
  if (offer.startsAt && new Date(offer.startsAt) > new Date()) {
    return { tone: 'warning', label: 'Programată' };
  }
  return { tone: 'critical', label: 'Încheiată' };
}

export default function AdminOffers() {
  const [offers, setOffers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const debouncedSearch = useDebounce(search, 350);

  const load = () => {
    setLoading(true);
    adminService
      .listOffers({ page, size: 10, search: debouncedSearch })
      .then((data) => {
        setOffers(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setOffers([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page, debouncedSearch]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setError(null);
    setModalOpen(true);
  };

  const openEdit = (o) => {
    setEditing(o);
    setForm({
      title: o.title || '',
      headline: o.headline || '',
      description: o.description || '',
      badgeLabel: o.badgeLabel || '',
      ctaLabel: o.ctaLabel || '',
      ctaUrl: o.ctaUrl || '',
      icon: o.icon || 'tag',
      accent: o.accent || 'var(--xx-cyan)',
      placement: o.placement || 'HOME_PROMO',
      active: !!o.active,
      startsAt: toLocalInput(o.startsAt),
      endsAt: toLocalInput(o.endsAt),
      showTimer: !!o.showTimer,
      recurringDaily: !!o.recurringDaily,
      sortOrder: o.sortOrder ?? 0,
    });
    setError(null);
    setModalOpen(true);
  };

  const handleChange = (e) => {
    const { name, type, value, checked } = e.target;
    setForm({ ...form, [name]: type === 'checkbox' ? checked : value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const payload = {
        ...form,
        sortOrder: Number(form.sortOrder) || 0,
        startsAt: fromLocalInput(form.startsAt),
        endsAt: fromLocalInput(form.endsAt),
      };
      if (editing) await adminService.updateOffer(editing.id, payload);
      else await adminService.createOffer(payload);
      setModalOpen(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (o) => {
    try {
      await adminService.toggleOffer(o.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Comutarea a eșuat.');
    }
  };

  const handleDelete = async (o) => {
    if (!window.confirm(`Ștergi oferta "${o.title}"?`)) return;
    try {
      await adminService.deleteOffer(o.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    }
  };

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Marketing"
        title="Oferte"
        subtitle="Promoția de pe prima pagină și banda de beneficii — editabile, cu timer, fără să atingi codul."
        as="h1"
        action={
          <NeonButton
            onClick={openCreate}
            icon={<GeoIcon name="tag" className="h-4 w-4" accent="currentColor" />}
          >
            Ofertă nouă
          </NeonButton>
        }
      />

      <div className="mb-5 sm:max-w-md">
        <HoloInput
          label="Caută ofertă"
          placeholder="Titlu sau subtitlu…"
          icon={<GeoIcon name="search" className="h-4 w-4" accent="currentColor" />}
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
        />
      </div>

      {loading ? (
        <HoloLoader label="Se încarcă ofertele" />
      ) : offers.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">
            {search ? 'Nicio ofertă nu corespunde căutării.' : 'Nicio ofertă. Adaugă prima ofertă.'}
          </p>
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Ofertă</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Zonă</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Fereastră</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Stare</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.14em]">
                    Acțiuni
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.07)]">
                {offers.map((o) => {
                  const badge = liveBadge(o);
                  return (
                    <tr key={o.id}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2 font-medium text-[color:var(--xx-ink)]">
                          <GeoIcon name={o.icon} className="h-4 w-4 shrink-0" accent={o.accent} />
                          {o.title}
                        </div>
                        {o.headline && <div className="text-xs xx-ink-muted">{o.headline}</div>}
                      </td>
                      <td className="px-4 py-3 text-xs xx-ink-muted">
                        {PLACEMENT_LABELS[o.placement] || o.placement}
                      </td>
                      <td className="px-4 py-3 text-xs xx-ink-dim">
                        {o.recurringDaily
                          ? 'Zilnic, până la miezul nopții'
                          : o.endsAt
                            ? `Până la ${new Date(o.endsAt).toLocaleString('ro-RO')}`
                            : 'Permanentă'}
                      </td>
                      <td className="px-4 py-3">
                        <NeonBadge tone={badge.tone}>{badge.label}</NeonBadge>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            type="button"
                            onClick={() => handleToggle(o)}
                            title={o.active ? 'Dezactivează oferta' : 'Activează oferta'}
                            aria-label={o.active ? `Dezactivează oferta ${o.title}` : `Activează oferta ${o.title}`}
                            className={`grid h-8 w-8 place-items-center rounded-lg border transition-all duration-xx ease-xx ${
                              o.active
                                ? 'border-[rgba(110,247,168,0.5)] text-[#b8ffd6] hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)]'
                                : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] hover:border-[rgba(110,247,168,0.5)] hover:text-[#b8ffd6]'
                            }`}
                          >
                            <GeoIcon name="bolt" className="h-4 w-4" accent="currentColor" />
                          </button>
                          <button
                            type="button"
                            onClick={() => openEdit(o)}
                            title="Editează oferta"
                            aria-label={`Editează oferta ${o.title}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] hover:text-[#7fb0ff]"
                          >
                            <GeoIcon name="gear" className="h-4 w-4" accent="currentColor" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(o)}
                            title="Șterge oferta"
                            aria-label={`Șterge oferta ${o.title}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)]"
                          >
                            <GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <Modal
        open={modalOpen}
        title={editing ? 'Editează oferta' : 'Ofertă nouă'}
        onClose={() => setModalOpen(false)}
        maxWidth="max-w-2xl"
      >
        {error && (
          <div className="mb-4 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-sm text-[#ffc2cc]">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <HoloInput label="Titlu *" name="title" value={form.title} onChange={handleChange} required />
            <HoloInput label="Subtitlu" name="headline" value={form.headline} onChange={handleChange} />
          </div>

          <HoloInput
            as="textarea"
            label="Descriere"
            name="description"
            className="min-h-[70px]"
            value={form.description}
            onChange={handleChange}
          />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <HoloInput label="Etichetă (badge)" name="badgeLabel" value={form.badgeLabel} onChange={handleChange} />
            <HoloInput label="Text buton" name="ctaLabel" value={form.ctaLabel} onChange={handleChange} />
            <HoloInput label="Link buton" name="ctaUrl" value={form.ctaUrl} onChange={handleChange} placeholder="/products" />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <HoloInput as="select" label="Iconiță" name="icon" value={form.icon} onChange={handleChange}>
              {geoIconNames.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </HoloInput>
            <HoloInput
              label="Culoare accent"
              name="accent"
              value={form.accent}
              onChange={handleChange}
              hint="Variabilă CSS, ex. var(--xx-cyan)"
            />
            <HoloInput as="select" label="Zonă de afișare" name="placement" value={form.placement} onChange={handleChange}>
              <option value="HOME_PROMO">Promoție principală</option>
              <option value="BENEFIT_BAR">Bandă beneficii</option>
            </HoloInput>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <HoloInput
              type="datetime-local"
              label="Începe la"
              name="startsAt"
              value={form.startsAt}
              onChange={handleChange}
              hint="Gol = pornește imediat"
            />
            <HoloInput
              type="datetime-local"
              label="Se încheie la"
              name="endsAt"
              value={form.endsAt}
              onChange={handleChange}
              hint="Gol = fără dată fixă de sfârșit"
              disabled={form.recurringDaily}
            />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="flex items-center gap-2 rounded-xl border border-[rgba(255,255,255,0.12)] px-3.5 py-2.5 text-sm text-[color:var(--xx-ink)]">
              <input
                type="checkbox"
                name="recurringDaily"
                checked={form.recurringDaily}
                onChange={handleChange}
                className="h-4 w-4 accent-[color:var(--xx-cyan)]"
              />
              Se resetează zilnic la miezul nopții
            </label>
            <label className="flex items-center gap-2 rounded-xl border border-[rgba(255,255,255,0.12)] px-3.5 py-2.5 text-sm text-[color:var(--xx-ink)]">
              <input
                type="checkbox"
                name="showTimer"
                checked={form.showTimer}
                onChange={handleChange}
                className="h-4 w-4 accent-[color:var(--xx-cyan)]"
              />
              Afișează cronometru
            </label>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="flex items-center gap-2 rounded-xl border border-[rgba(255,255,255,0.12)] px-3.5 py-2.5 text-sm text-[color:var(--xx-ink)]">
              <input
                type="checkbox"
                name="active"
                checked={form.active}
                onChange={handleChange}
                className="h-4 w-4 accent-[color:var(--xx-cyan)]"
              />
              Ofertă activă
            </label>
            <HoloInput
              type="number"
              label="Ordine afișare"
              name="sortOrder"
              value={form.sortOrder}
              onChange={handleChange}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <NeonButton type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              Anulează
            </NeonButton>
            <NeonButton type="submit" disabled={saving} charging={saving}>
              {saving ? 'Se salvează…' : 'Salvează'}
            </NeonButton>
          </div>
        </form>
      </Modal>
    </div>
  );
}
