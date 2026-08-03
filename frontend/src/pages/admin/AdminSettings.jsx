import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import adminService from '../../api/adminService';
import authService from '../../api/authService';
import { useAuth } from '../../context/AuthContext';
import { showToast } from '../../components/Toast';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonBadge,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../../components/xxii';

const empty = {
  legalName: '',
  cui: '',
  regCom: '',
  address: '',
  city: '',
  county: '',
  country: 'România',
  postalCode: '',
  iban: '',
  bankName: '',
  phone: '',
  email: '',
  website: '',
  vatPayer: true,
  vatRate: 19,
  invoiceSeries: 'ELS',
  invoiceNextNumber: 1,
  logoUrl: '',
  invoiceNotes: '',
};

/**
 * XXII — TASK 6 / TASK 9 (Quantum Control Center: panouri modulare).
 *
 * Ecranul de setări este o secvență de panouri, nu un formular lung. Fiecare
 * panou este o unitate de sens închisă — identitate, adresă, bancă, contact,
 * TVA, opțional — și fiecare primește propria pictogramă geometrică, astfel
 * încât operatorul să găsească secțiunea căutată prin formă, nu prin citirea
 * fiecărui titlu.
 *
 * Panourile intră cu `Reveal`, decalate, în ordinea în care sunt completate.
 */
export default function AdminSettings() {
  const [form, setForm] = useState(empty);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    adminService
      .getCompanySettings()
      .then((data) => {
        // keep controlled inputs: replace nulls with '' but preserve numbers/bools
        const merged = { ...empty };
        Object.keys(empty).forEach((k) => {
          if (data && data[k] !== null && data[k] !== undefined) merged[k] = data[k];
        });
        setForm(merged);
      })
      .catch(() => setError('Nu am putut încărca datele firmei.'))
      .finally(() => setLoading(false));
  }, []);

  const change = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }));
    setSaved(false);
  };

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const payload = {
        ...form,
        vatRate: form.vatRate === '' ? null : Number(form.vatRate),
        invoiceNextNumber:
          form.invoiceNextNumber === '' ? 1 : Math.max(1, parseInt(form.invoiceNextNumber, 10) || 1),
      };
      await adminService.updateCompanySettings(payload);
      setSaved(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div>
        <AdminNav />
        <HoloLoader label="Se încarcă datele firmei" />
      </div>
    );
  }

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Configurare"
        title="Date firmă & facturare"
        as="h1"
        subtitle="Datele completate aici apar automat pe facturile PDF generate din comenzi. Poți reveni oricând să le actualizezi."
      />

      <TwoFactorSection />

      {/* Starea salvării — niciodată doar culoare: fiecare casetă poartă pictogramă și text. */}
      {error && (
        <div
          role="alert"
          className="mb-4 flex items-center gap-2 rounded-[0.9rem] border border-[rgba(255,90,122,0.4)] bg-[rgba(255,90,122,0.1)] px-4 py-2.5 text-sm text-[#ff8fa8]"
        >
          <GeoIcon name="alert" className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}
      {saved && (
        <div
          role="status"
          className="mb-4 flex items-center gap-2 rounded-[0.9rem] border border-[rgba(31,172,121,0.4)] bg-[rgba(31,172,121,0.1)] px-4 py-2.5 text-sm text-[#7ee9bd]"
        >
          <GeoIcon name="check" className="h-4 w-4 shrink-0" />
          Datele au fost salvate.
        </div>
      )}

      <form onSubmit={submit} className="space-y-5">
        <Section title="Identitate firmă" icon="shield" delay={0}>
          <Field label="Denumire legală" name="legalName" value={form.legalName} onChange={change} placeholder="ELECTROSHOP SRL" />
          <Field label="CUI / CIF" name="cui" value={form.cui} onChange={change} placeholder="RO12345678" />
          <Field label="Nr. Reg. Com." name="regCom" value={form.regCom} onChange={change} placeholder="J40/1234/2020" />
        </Section>

        <Section title="Adresă sediu" icon="globe" delay={60}>
          <Field label="Adresă (stradă, nr.)" name="address" value={form.address} onChange={change} wide />
          <Field label="Oraș / localitate" name="city" value={form.city} onChange={change} />
          <Field label="Județ" name="county" value={form.county} onChange={change} />
          <Field label="Țară" name="country" value={form.country} onChange={change} />
          <Field label="Cod poștal" name="postalCode" value={form.postalCode} onChange={change} />
        </Section>

        <Section title="Cont bancar" icon="coins" delay={120}>
          <Field label="IBAN" name="iban" value={form.iban} onChange={change} placeholder="RO49AAAA1B31007593840000" wide />
          <Field label="Banca" name="bankName" value={form.bankName} onChange={change} />
        </Section>

        <Section title="Contact" icon="user" delay={180}>
          <Field label="Telefon" name="phone" value={form.phone} onChange={change} />
          <Field label="Email" name="email" value={form.email} onChange={change} />
          <Field label="Website" name="website" value={form.website} onChange={change} />
        </Section>

        <Section title="TVA & facturare" icon="document" delay={240}>
          <label className="flex cursor-pointer items-center gap-2.5 rounded-[0.8rem] border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] px-3.5 py-2.5 text-sm text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(34,232,245,0.4)] sm:col-span-2">
            <input
              type="checkbox"
              name="vatPayer"
              checked={form.vatPayer}
              onChange={change}
              className="h-4 w-4 cursor-pointer accent-[#22e8f5]"
            />
            Firmă plătitoare de TVA
          </label>
          <Field label="Cotă TVA (%)" name="vatRate" type="number" step="0.01" value={form.vatRate} onChange={change} />
          <Field label="Seria facturii" name="invoiceSeries" value={form.invoiceSeries} onChange={change} placeholder="ELS" />
          <Field
            label="Următorul nr. factură"
            name="invoiceNextNumber"
            type="number"
            value={form.invoiceNextNumber}
            onChange={change}
            hint="Următoarea factură emisă va primi acest număr."
          />
        </Section>

        <Section title="Opțional" icon="sparkle" delay={300}>
          <Field label="URL logo (apare pe factură)" name="logoUrl" value={form.logoUrl} onChange={change} wide />
          <div className="sm:col-span-2">
            <HoloInput
              as="textarea"
              label="Mențiuni pe factură"
              name="invoiceNotes"
              className="min-h-[80px]"
              value={form.invoiceNotes}
              onChange={change}
              placeholder="Ex: Factura este valabilă fără semnătură și ștampilă."
            />
          </div>
        </Section>

        <div className="flex justify-end gap-2">
          <NeonButton
            type="submit"
            disabled={saving}
            charging={saving}
            pulse={!saving}
            icon={<GeoIcon name="check" className="h-4 w-4" />}
          >
            {saving ? 'Se salvează...' : 'Salvează datele'}
          </NeonButton>
        </div>
      </form>
    </div>
  );
}

function Section({ title, icon, delay = 0, children }) {
  return (
    <Reveal delay={delay}>
      <div className="card card-static p-5">
        <h2 className="mb-4 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] xx-ink-muted">
          <GeoIcon name={icon} className="h-4 w-4" />
          {title}
        </h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">{children}</div>
      </div>
    </Reveal>
  );
}

function Field({ label, wide, ...props }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <HoloInput label={label} {...props} />
    </div>
  );
}

/**
 * Feature #6 — Admin self-service 2FA. Setup returns a secret + otpauth:// URI
 * (no QR image is rendered — the environment this was built in has no
 * verified way to add a QR-code library — so the code/URI is shown for
 * manual/copy-paste entry into any TOTP authenticator app instead).
 *
 * XXII — două schimbări dincolo de suprafață:
 *
 *   1. `window.confirm` pentru deconectarea tuturor sesiunilor a fost înlocuit
 *      cu un dialog al aplicației. Dialogul nativ blochează firul de execuție,
 *      nu poate fi stilizat și, pe mobil, apare rupt de restul interfeței.
 *      Acțiunea este ireversibilă, deci merită o confirmare care spune explicit
 *      ce se întâmplă.
 *   2. Cheia secretă și linkul otpauth au acum butoane de copiere. Sunt șiruri
 *      lungi care se selectează greu cu degetul pe ecran mic, iar o greșeală de
 *      copiere înseamnă un cont 2FA nefuncțional.
 */
function TwoFactorSection() {
  const { logoutAllSessions } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState(null); // { twoFactorEnabled }
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [setup, setSetup] = useState(null); // { secret, otpAuthUrl }
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);
  const [confirmLogoutAll, setConfirmLogoutAll] = useState(false);
  const [copied, setCopied] = useState(null);

  const refreshStatus = () => {
    setLoadingStatus(true);
    authService
      .me()
      .then((u) => setStatus(u))
      .catch(() => setStatus(null))
      .finally(() => setLoadingStatus(false));
  };

  useEffect(refreshStatus, []);

  const startSetup = async () => {
    setMsg(null);
    setBusy(true);
    try {
      const data = await authService.setupTwoFactor();
      setSetup(data);
    } catch (err) {
      setMsg({ type: 'error', text: err.response?.data?.message || 'Nu am putut începe configurarea.' });
    } finally {
      setBusy(false);
    }
  };

  const confirmSetup = async (e) => {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await authService.confirmTwoFactor(code);
      setSetup(null);
      setCode('');
      showToast('Autentificarea în doi pași a fost activată.', 'success');
      refreshStatus();
    } catch (err) {
      setMsg({ type: 'error', text: err.response?.data?.message || 'Cod incorect.' });
    } finally {
      setBusy(false);
    }
  };

  const disable = async (e) => {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await authService.disableTwoFactor(code);
      setCode('');
      showToast('Autentificarea în doi pași a fost dezactivată.', 'success');
      refreshStatus();
    } catch (err) {
      setMsg({ type: 'error', text: err.response?.data?.message || 'Cod incorect.' });
    } finally {
      setBusy(false);
    }
  };

  // Clipboard access can be denied (insecure context, permission policy); the
  // fallback is simply not showing the "copiat" confirmation, never an error
  // the operator has to dismiss.
  const copy = async (value, key) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      window.setTimeout(() => setCopied((current) => (current === key ? null : current)), 1800);
    } catch {
      showToast('Copierea automată nu este disponibilă. Selectează textul manual.', 'error');
    }
  };

  const runLogoutAll = async () => {
    setConfirmLogoutAll(false);
    await logoutAllSessions();
    navigate('/login');
  };

  return (
    <>
      <Reveal className="card card-static mb-6 p-5">
        <div className="mb-1 flex flex-wrap items-center gap-2">
          <h2 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] xx-ink-muted">
            <GeoIcon name="shield" className="h-4 w-4" />
            Securitate — Autentificare în doi pași (2FA)
          </h2>
          {!loadingStatus && (
            <NeonBadge tone={status?.twoFactorEnabled ? 'good' : 'warning'}>
              {status?.twoFactorEnabled ? 'Activă' : 'Inactivă'}
            </NeonBadge>
          )}
        </div>
        <p className="mb-4 text-sm xx-ink-muted">
          Protejează contul tău de Admin cu un cod suplimentar generat de o aplicație de autentificare
          (Google Authenticator, Microsoft Authenticator, Authy etc.).
        </p>

        {msg && (
          <div
            role="alert"
            className={`mb-4 flex items-center gap-2 rounded-[0.9rem] border px-4 py-2.5 text-sm ${
              msg.type === 'error'
                ? 'border-[rgba(255,90,122,0.4)] bg-[rgba(255,90,122,0.1)] text-[#ff8fa8]'
                : 'border-[rgba(31,172,121,0.4)] bg-[rgba(31,172,121,0.1)] text-[#7ee9bd]'
            }`}
          >
            <GeoIcon name={msg.type === 'error' ? 'alert' : 'check'} className="h-4 w-4 shrink-0" />
            {msg.text}
          </div>
        )}

        {loadingStatus ? (
          <HoloLoader inline size="sm" label="Se verifică starea 2FA" />
        ) : status?.twoFactorEnabled ? (
          <div className="space-y-3">
            <p className="flex items-center gap-2 text-sm text-[#7ee9bd]">
              <GeoIcon name="check" className="h-4 w-4 shrink-0" />
              Autentificarea în doi pași este activă pe contul tău.
            </p>
            <form onSubmit={disable} className="flex flex-wrap items-start gap-3">
              <div className="w-40">
                <HoloInput
                  label="Cod curent (pentru a dezactiva)"
                  inputMode="numeric"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  autoComplete="one-time-code"
                  required
                  status={code.length === 0 ? null : code.length === 6 ? 'valid' : null}
                />
              </div>
              <NeonButton
                type="submit"
                variant="secondary"
                className="mt-6"
                disabled={busy || code.length !== 6}
                charging={busy}
              >
                {busy ? 'Se procesează...' : 'Dezactivează 2FA'}
              </NeonButton>
            </form>
          </div>
        ) : setup ? (
          <form onSubmit={confirmSetup} className="space-y-3">
            <div className="rounded-[1rem] border border-[rgba(34,232,245,0.28)] bg-[rgba(34,232,245,0.06)] p-4">
              <p className="mb-3 text-sm xx-ink-muted">
                1. Adaugă manual un cont în aplicația de autentificare folosind cheia de mai jos, sau
                copiază linkul otpauth și importă-l.
              </p>

              <p className="mb-1 text-[0.68rem] font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                Cheie secretă
              </p>
              <div className="mb-3 flex items-stretch gap-2">
                <code className="block flex-1 break-all rounded-[0.7rem] border border-[rgba(255,255,255,0.12)] bg-[rgba(9,11,28,0.6)] px-3 py-2 font-mono text-sm text-[#e8ecff]">
                  {setup.secret}
                </code>
                <button
                  type="button"
                  onClick={() => copy(setup.secret, 'secret')}
                  title="Copiază cheia secretă"
                  aria-label="Copiază cheia secretă"
                  className="inline-flex w-10 shrink-0 items-center justify-center rounded-[0.7rem] border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(34,232,245,0.5)] hover:text-[#22e8f5]"
                >
                  <GeoIcon name={copied === 'secret' ? 'check' : 'layers'} className="h-4 w-4" />
                </button>
              </div>

              <p className="mb-1 text-[0.68rem] font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                Link otpauth://
              </p>
              <div className="flex items-stretch gap-2">
                <code className="block flex-1 break-all rounded-[0.7rem] border border-[rgba(255,255,255,0.12)] bg-[rgba(9,11,28,0.6)] px-3 py-2 font-mono text-xs xx-ink-muted">
                  {setup.otpAuthUrl}
                </code>
                <button
                  type="button"
                  onClick={() => copy(setup.otpAuthUrl, 'url')}
                  title="Copiază linkul otpauth"
                  aria-label="Copiază linkul otpauth"
                  className="inline-flex w-10 shrink-0 items-center justify-center rounded-[0.7rem] border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(34,232,245,0.5)] hover:text-[#22e8f5]"
                >
                  <GeoIcon name={copied === 'url' ? 'check' : 'layers'} className="h-4 w-4" />
                </button>
              </div>

              <p aria-live="polite" className="mt-2 h-4 text-xs text-[#7ee9bd]">
                {copied ? 'Copiat în clipboard.' : ''}
              </p>
            </div>

            <div className="flex flex-wrap items-start gap-3">
              <div className="w-40">
                <HoloInput
                  label="2. Introdu codul generat"
                  inputMode="numeric"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  autoComplete="one-time-code"
                  autoFocus
                  required
                  status={code.length === 0 ? null : code.length === 6 ? 'valid' : null}
                />
              </div>
              <NeonButton
                type="submit"
                className="mt-6"
                disabled={busy || code.length !== 6}
                charging={busy}
                icon={<GeoIcon name="shield" className="h-4 w-4" />}
              >
                {busy ? 'Se verifică...' : 'Confirmă și activează'}
              </NeonButton>
              <NeonButton
                type="button"
                variant="ghost"
                className="mt-6"
                onClick={() => {
                  setSetup(null);
                  setCode('');
                  setMsg(null);
                }}
              >
                Anulează
              </NeonButton>
            </div>
          </form>
        ) : (
          <NeonButton
            type="button"
            onClick={startSetup}
            disabled={busy}
            charging={busy}
            icon={<GeoIcon name="shield" className="h-4 w-4" />}
          >
            {busy ? 'Se pregătește...' : 'Activează 2FA'}
          </NeonButton>
        )}

        <div className="mt-5 border-t border-[rgba(255,255,255,0.08)] pt-4">
          <p className="mb-2 text-xs xx-ink-muted">
            Ai suspiciunea că un dispozitiv sau o sesiune neautorizată are acces la contul tău?
          </p>
          <NeonButton
            type="button"
            variant="danger"
            size="sm"
            icon={<GeoIcon name="bolt" className="h-4 w-4" />}
            onClick={() => setConfirmLogoutAll(true)}
          >
            Deconectează toate sesiunile
          </NeonButton>
        </div>
      </Reveal>

      {/*
        Dialogul stă în afara lui `Reveal`, nu în interiorul lui. `Reveal` lasă
        `filter: blur(0px)` pe element și după ce animația s-a încheiat, iar un
        `filter` diferit de `none` creează un bloc de conținere pentru
        `position: fixed` — un dialog randat înăuntru s-ar poziționa față de
        card, nu față de fereastră, și ar apărea decalat pe pagină.
      */}
      <Modal
        open={confirmLogoutAll}
        title="Deconectezi toate sesiunile?"
        onClose={() => setConfirmLogoutAll(false)}
        maxWidth="max-w-md"
      >
        <div className="space-y-4">
          <div className="flex items-start gap-3 rounded-[0.9rem] border border-[rgba(255,90,122,0.4)] bg-[rgba(255,90,122,0.1)] p-4">
            <GeoIcon name="alert" className="mt-0.5 h-5 w-5 shrink-0" accent="#ff8fa8" />
            <p className="text-sm text-[#ff8fa8]">
              Toate sesiunile active de pe toate dispozitivele vor fi închise, inclusiv aceasta. Vei
              fi redirecționat către pagina de autentificare și va trebui să te conectezi din nou.
            </p>
          </div>

          <div className="flex justify-end gap-2">
            <NeonButton type="button" variant="ghost" onClick={() => setConfirmLogoutAll(false)}>
              Anulează
            </NeonButton>
            <NeonButton type="button" variant="danger" onClick={runLogoutAll}>
              Deconectează tot
            </NeonButton>
          </div>
        </div>
      </Modal>
    </>
  );
}
