import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonBadge,
  NeonButton,
  SectionHeader,
} from '../../components/xxii';
import { formatDate } from '../../utils/format';
import { ROLE_BADGE_STYLE, ROLE_LABELS } from '../../utils/permissions';
import { useDebounce } from '../../hooks/useDebounce';

/**
 * XXII — TASK 6: user management inside the Quantum Control Center.
 *
 * The permission model, the debounce and every service call are unchanged. The
 * redesign concentrates on the two things this screen gets wrong when it is
 * busy:
 *
 *   · **Pending approvals used to be an amber strip that looked like an alert
 *     and behaved like a queue.** It is now an explicitly labelled queue panel
 *     with a count, a pulsing badge and per-row approve/reject controls — the
 *     operator can see at a glance whether anything is waiting without reading
 *     the text.
 *   · **Account state was four badges of similar weight.** Locked and 2FA are
 *     now marked with icons as well as colour, because "blocat" is an
 *     actionable condition and must not disappear into a row of chips.
 *
 * Roles keep their own accent ladder (see `ROLE_BADGE_STYLE`), and the role
 * name is always printed, so privilege is never inferred from hue alone.
 */

const emptyForm = { fullName: '', email: '', password: '', enabled: true, roles: ['ROLE_USER'] };
// Feature #6: Admin/Manager/Editor + the plain storefront-customer role.
const ASSIGNABLE_ROLES = ['ROLE_USER', 'ROLE_EDITOR', 'ROLE_MANAGER', 'ROLE_ADMIN'];

export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  // Pending self-registrations awaiting approval
  const [pending, setPending] = useState([]);
  const [approvingId, setApprovingId] = useState(null);

  const loadPending = () => {
    adminService
      .listPendingUsers({ page: 0, size: 50 })
      .then((data) => setPending(data.content || []))
      .catch(() => setPending([]));
  };

  // Feature #7 (performance): debounce the search box so typing doesn't fire
  // one request per keystroke.
  const debouncedSearch = useDebounce(search, 350);

  const load = () => {
    setLoading(true);
    adminService
      .listUsers({ page, size: 10, search: debouncedSearch })
      .then((data) => {
        setUsers(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setUsers([]))
      .finally(() => setLoading(false));
    loadPending();
  };

  useEffect(load, [page, debouncedSearch]);

  const handleApprove = async (u) => {
    setApprovingId(u.id);
    try {
      await adminService.approveUser(u.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Aprobarea a eșuat.');
    } finally {
      setApprovingId(null);
    }
  };

  const handleReject = async (u) => {
    if (!window.confirm(`Respingi și ștergi cererea de la "${u.email}"?`)) return;
    try {
      await adminService.deleteUser(u.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Respingerea a eșuat.');
    }
  };

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setError(null);
    setModalOpen(true);
  };

  const openEdit = (u) => {
    setEditing(u);
    setForm({
      fullName: u.fullName,
      email: u.email,
      password: '',
      enabled: u.enabled,
      roles: u.roles,
    });
    setError(null);
    setModalOpen(true);
  };

  const toggleRole = (role) => {
    setForm((f) => {
      const has = f.roles.includes(role);
      return { ...f, roles: has ? f.roles.filter((r) => r !== role) : [...f.roles, role] };
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const payload = { ...form };
      if (editing && !payload.password) delete payload.password;
      if (editing) {
        await adminService.updateUser(editing.id, payload);
      } else {
        await adminService.createUser(payload);
      }
      setModalOpen(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (u) => {
    if (!window.confirm(`Ștergi utilizatorul "${u.email}"?`)) return;
    try {
      await adminService.deleteUser(u.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
    }
  };

  // Feature #6 — brute-force lock override + lost-device 2FA reset.
  const handleUnlock = async (u) => {
    try {
      await adminService.unlockUser(u.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Deblocarea a eșuat.');
    }
  };

  const handleDisableTwoFactor = async (u) => {
    if (!window.confirm(`Dezactivezi autentificarea în doi pași pentru "${u.email}"?`)) return;
    try {
      await adminService.disableUserTwoFactor(u.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Operația a eșuat.');
    }
  };

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Acces"
        title="Management utilizatori"
        subtitle="Conturi, roluri, aprobări și intervenții de securitate."
        as="h1"
        action={
          <NeonButton
            onClick={openCreate}
            icon={<GeoIcon name="user" className="h-4 w-4" accent="currentColor" />}
          >
            Utilizator nou
          </NeonButton>
        }
      />

      {pending.length > 0 && (
        <div className="card card-static mb-5 border-[rgba(255,194,75,0.4)] p-4 shadow-[0_0_50px_-24px_rgba(255,194,75,0.8)]">
            <div className="mb-3 flex items-center gap-2">
              <NeonBadge
                tone="warning"
                pulse
                icon={<GeoIcon name="clock" className="h-3 w-3" accent="currentColor" />}
              >
                {pending.length} în așteptare
              </NeonBadge>
              <h2 className="font-display text-sm font-semibold text-[color:var(--xx-ink)]">
                Conturi care așteaptă aprobare
              </h2>
            </div>
            <div className="space-y-2">
              {pending.map((u) => (
                <div
                  key={u.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.05)] px-3 py-2"
                >
                  <div>
                    <p className="font-medium text-[color:var(--xx-ink)]">{u.fullName}</p>
                    <p className="text-xs xx-ink-dim">{u.email}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <NeonButton
                      size="sm"
                      disabled={approvingId === u.id}
                      charging={approvingId === u.id}
                      onClick={() => handleApprove(u)}
                      icon={<GeoIcon name="check" className="h-3.5 w-3.5" accent="currentColor" />}
                    >
                      {approvingId === u.id ? 'Se aprobă…' : 'Aprobă'}
                    </NeonButton>
                    <NeonButton
                      size="sm"
                      variant="danger"
                      onClick={() => handleReject(u)}
                      icon={<GeoIcon name="close" className="h-3.5 w-3.5" accent="currentColor" />}
                    >
                      Respinge
                    </NeonButton>
                  </div>
                </div>
              ))}
            </div>
        </div>
      )}

      <div className="mb-5 sm:max-w-md">
        <HoloInput
          label="Caută utilizatori"
          placeholder="Nume sau email…"
          icon={<GeoIcon name="search" className="h-4 w-4" accent="currentColor" />}
          value={search}
          onChange={(e) => {
            setPage(0);
            setSearch(e.target.value);
          }}
        />
      </div>

      {loading ? (
        <HoloLoader label="Se încarcă utilizatorii" />
      ) : users.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">Niciun utilizator nu corespunde căutării.</p>
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
              <thead className="text-left">
                <tr className="bg-[rgba(255,255,255,0.03)]">
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Nume</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Email</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Roluri</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Status</th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">
                    Ultima conectare
                  </th>
                  <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Creat</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.14em]">
                    Acțiuni
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(255,255,255,0.07)]">
                {users.map((u) => (
                  <tr key={u.id}>
                    <td className="px-4 py-3 font-medium text-[color:var(--xx-ink)]">{u.fullName}</td>
                    <td className="px-4 py-3 text-xs xx-ink-muted">{u.email}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {u.roles.map((r) => (
                          <span
                            key={r}
                            className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                              ROLE_BADGE_STYLE[r] || ROLE_BADGE_STYLE.ROLE_USER
                            }`}
                          >
                            {ROLE_LABELS[r] || r.replace('ROLE_', '')}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        <span
                          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                            u.enabled
                              ? 'border border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.16)] text-[#93e9c4]'
                              : 'border border-[rgba(184,47,60,0.45)] bg-[rgba(184,47,60,0.16)] text-[#ffb3bd]'
                          }`}
                        >
                          <span aria-hidden="true">{u.enabled ? '●' : '○'}</span>
                          {u.enabled ? 'Activ' : 'Inactiv'}
                        </span>
                        {!u.approved && (
                          <span className="inline-flex items-center gap-1 rounded-full border border-[rgba(176,140,9,0.45)] bg-[rgba(176,140,9,0.16)] px-2.5 py-0.5 text-xs font-semibold text-[#f0d089]">
                            <span aria-hidden="true">◷</span>
                            În așteptare
                          </span>
                        )}
                        {u.locked && (
                          <span className="inline-flex items-center gap-1 rounded-full border border-[rgba(184,47,60,0.45)] bg-[rgba(184,47,60,0.16)] px-2.5 py-0.5 text-xs font-semibold text-[#ffb3bd]">
                            <GeoIcon name="shield" className="h-3 w-3" accent="currentColor" />
                            Blocat
                          </span>
                        )}
                        {u.twoFactorEnabled && (
                          <span className="inline-flex items-center gap-1 rounded-full border border-[rgba(34,232,245,0.45)] bg-[rgba(34,232,245,0.14)] px-2.5 py-0.5 text-xs font-semibold text-[#a5f0f8]">
                            <GeoIcon name="bolt" className="h-3 w-3" accent="currentColor" />
                            2FA
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      {u.lastLoginAt ? (
                        <div>
                          <p className="text-xs xx-ink-muted">{formatDate(u.lastLoginAt)}</p>
                          <p className="text-xs xx-ink-dim">
                            {u.lastLoginLocation || u.lastLoginIp || ''}
                          </p>
                        </div>
                      ) : (
                        <span className="xx-ink-dim">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs xx-ink-dim">{formatDate(u.createdAt)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1.5">
                        {u.locked && (
                          <button
                            type="button"
                            onClick={() => handleUnlock(u)}
                            title="Deblochează contul"
                            aria-label={`Deblochează contul ${u.email}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-amber)] transition-all duration-xx ease-xx hover:border-[rgba(255,194,75,0.55)]"
                          >
                            <GeoIcon name="shield" className="h-4 w-4" accent="currentColor" />
                          </button>
                        )}
                        {u.twoFactorEnabled && (
                          <button
                            type="button"
                            onClick={() => handleDisableTwoFactor(u)}
                            title="Dezactivează autentificarea în doi pași"
                            aria-label={`Dezactivează 2FA pentru ${u.email}`}
                            className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-amber)] transition-all duration-xx ease-xx hover:border-[rgba(255,194,75,0.55)]"
                          >
                            <GeoIcon name="bolt" className="h-4 w-4" accent="currentColor" />
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => openEdit(u)}
                          title="Editează utilizatorul"
                          aria-label={`Editează utilizatorul ${u.email}`}
                          className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] hover:text-[#7fb0ff]"
                        >
                          <GeoIcon name="gear" className="h-4 w-4" accent="currentColor" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(u)}
                          title="Șterge utilizatorul"
                          aria-label={`Șterge utilizatorul ${u.email}`}
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
      )}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <Modal
        open={modalOpen}
        title={editing ? 'Editează utilizator' : 'Utilizator nou'}
        onClose={() => setModalOpen(false)}
      >
        {error && (
          <div className="mb-4 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-sm text-[#ffc2cc]">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3">
          <HoloInput
            label="Nume complet"
            value={form.fullName}
            onChange={(e) => setForm({ ...form, fullName: e.target.value })}
            required
          />
          <HoloInput
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
          <HoloInput
            label="Parolă"
            type="password"
            hint={editing ? 'Lasă gol pentru a păstra parola actuală.' : undefined}
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required={!editing}
          />

          <fieldset>
            <legend className="mb-2 text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
              Roluri
            </legend>
            <div className="flex flex-wrap gap-2">
              {ASSIGNABLE_ROLES.map((r) => {
                const active = form.roles.includes(r);
                return (
                  <label
                    key={r}
                    className={`flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition-all duration-xx ease-xx ${
                      active
                        ? 'border-[rgba(34,232,245,0.55)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-ink)]'
                        : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] hover:border-[rgba(122,60,255,0.5)]'
                    }`}
                  >
                    <input
                      type="checkbox"
                      className="h-3.5 w-3.5 accent-[#22e8f5]"
                      checked={active}
                      onChange={() => toggleRole(r)}
                    />
                    {ROLE_LABELS[r] || r.replace('ROLE_', '')}
                  </label>
                );
              })}
            </div>
            <p className="mt-2 text-xs leading-relaxed xx-ink-dim">
              Editor: editează produse. Manager: și stoc, prețuri, comenzi, ștergeri. Admin: acces total.
            </p>
          </fieldset>

          <label className="flex cursor-pointer items-center gap-2 text-sm xx-ink-muted">
            <input
              type="checkbox"
              className="h-3.5 w-3.5 accent-[#22e8f5]"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
            />
            Cont activ
          </label>

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
