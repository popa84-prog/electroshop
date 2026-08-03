import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../../components/xxii';
import { useDebounce } from '../../hooks/useDebounce';

/**
 * XXII — TASK 6: supplier directory inside the Quantum Control Center.
 *
 * A small screen, converted straight across: the same debounced search, the
 * same create/edit dialog, the same service calls. The form fields become
 * `HoloInput`s so the label/hint/id wiring is handled by the atom rather than
 * repeated seven times by hand — which is also how the previous version lost
 * its `htmlFor`/`id` pairing on every single field.
 */

const emptyForm = {
  name: '',
  contactName: '',
  email: '',
  phone: '',
  address: '',
  taxId: '',
  notes: '',
};

export default function AdminSuppliers() {
  const [suppliers, setSuppliers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  // Feature #7 (performance): debounce the search box so typing doesn't fire
  // one request per keystroke.
  const debouncedSearch = useDebounce(search, 350);

  const load = () => {
    setLoading(true);
    adminService
      .listSuppliers({ page, size: 10, search: debouncedSearch })
      .then((data) => {
        setSuppliers(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setSuppliers([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page, debouncedSearch]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setError(null);
    setModalOpen(true);
  };

  const openEdit = (s) => {
    setEditing(s);
    setForm({
      name: s.name || '',
      contactName: s.contactName || '',
      email: s.email || '',
      phone: s.phone || '',
      address: s.address || '',
      taxId: s.taxId || '',
      notes: s.notes || '',
    });
    setError(null);
    setModalOpen(true);
  };

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      if (editing) await adminService.updateSupplier(editing.id, form);
      else await adminService.createSupplier(form);
      setModalOpen(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (s) => {
    if (!window.confirm(`Ștergi furnizorul "${s.name}"?`)) return;
    try {
      await adminService.deleteSupplier(s.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    }
  };

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Aprovizionare"
        title="Furnizori"
        subtitle="Partenerii de la care intră marfa în stoc."
        as="h1"
        action={
          <NeonButton
            onClick={openCreate}
            icon={<GeoIcon name="truck" className="h-4 w-4" accent="currentColor" />}
          >
            Furnizor nou
          </NeonButton>
        }
      />

      <div className="mb-5 sm:max-w-md">
        <HoloInput
          label="Caută furnizor"
          placeholder="Nume, contact sau CUI…"
          icon={<GeoIcon name="search" className="h-4 w-4" accent="currentColor" />}
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
        />
      </div>

      {loading ? (
        <HoloLoader label="Se încarcă furnizorii" />
      ) : suppliers.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">
            {search ? 'Niciun furnizor nu corespunde căutării.' : 'Niciun furnizor. Adaugă primul furnizor.'}
          </p>
        </div>
      ) : (
        <Reveal>
          <div className="card overflow-x-auto">
            <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Nume</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">
                    Persoană contact
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Email</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Telefon</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">CUI</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.14em]">
                    Acțiuni
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.07)]">
                {suppliers.map((s) => (
                  <tr key={s.id}>
                    <td className="px-4 py-3 font-medium text-[color:var(--xx-ink)]">{s.name}</td>
                    <td className="px-4 py-3 text-xs xx-ink-muted">{s.contactName || '—'}</td>
                    <td className="px-4 py-3 text-xs xx-ink-muted">{s.email || '—'}</td>
                    <td className="px-4 py-3 text-xs xx-ink-muted">{s.phone || '—'}</td>
                    <td className="px-4 py-3 font-mono text-xs xx-ink-dim">{s.taxId || '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => openEdit(s)}
                          title="Editează furnizorul"
                          aria-label={`Editează furnizorul ${s.name}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] hover:text-[#7fb0ff]"
                        >
                          <GeoIcon name="gear" className="h-4 w-4" accent="currentColor" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(s)}
                          title="Șterge furnizorul"
                          aria-label={`Șterge furnizorul ${s.name}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)]"
                        >
                          <GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Reveal>
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <Modal
        open={modalOpen}
        title={editing ? 'Editează furnizor' : 'Furnizor nou'}
        onClose={() => setModalOpen(false)}
      >
        {error && (
          <div className="mb-4 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-sm text-[#ffc2cc]">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3">
          <HoloInput label="Nume *" name="name" value={form.name} onChange={handleChange} required />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <HoloInput
              label="Persoană contact"
              name="contactName"
              value={form.contactName}
              onChange={handleChange}
            />
            <HoloInput label="CUI" name="taxId" value={form.taxId} onChange={handleChange} />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <HoloInput
              label="Email"
              type="email"
              name="email"
              value={form.email}
              onChange={handleChange}
            />
            <HoloInput label="Telefon" name="phone" value={form.phone} onChange={handleChange} />
          </div>

          <HoloInput label="Adresă" name="address" value={form.address} onChange={handleChange} />

          <HoloInput
            as="textarea"
            label="Note"
            name="notes"
            className="min-h-[70px]"
            value={form.notes}
            onChange={handleChange}
          />

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
