import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AdminNav from '../../components/AdminNav';
import adminService from '../../api/adminService';
import authService from '../../api/authService';
import { useAuth } from '../../context/AuthContext';
import Spinner from '../../components/Spinner';
import { showToast } from '../../components/Toast';

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
        <Spinner />
      </div>
    );
  }

  return (
    <div>
      <AdminNav />
      <div className="mb-4">
        <h1 className="text-2xl font-bold text-slate-800">Date firmă & facturare</h1>
        <p className="mt-1 text-sm text-slate-500">
          Completează datele firmei tale. Ele apar automat pe facturile PDF generate din comenzi.
          Poți reveni oricând să le actualizezi.
        </p>
      </div>

      <TwoFactorSection />

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700">{error}</div>
      )}
      {saved && (
        <div className="mb-4 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
          ✓ Datele au fost salvate.
        </div>
      )}

      <form onSubmit={submit} className="space-y-6">
        <Section title="Identitate firmă">
          <Field label="Denumire legală" name="legalName" value={form.legalName} onChange={change} placeholder="ELECTROSHOP SRL" />
          <Field label="CUI / CIF" name="cui" value={form.cui} onChange={change} placeholder="RO12345678" />
          <Field label="Nr. Reg. Com." name="regCom" value={form.regCom} onChange={change} placeholder="J40/1234/2020" />
        </Section>

        <Section title="Adresă sediu">
          <Field label="Adresă (stradă, nr.)" name="address" value={form.address} onChange={change} wide />
          <Field label="Oraș / localitate" name="city" value={form.city} onChange={change} />
          <Field label="Județ" name="county" value={form.county} onChange={change} />
          <Field label="Țară" name="country" value={form.country} onChange={change} />
          <Field label="Cod poștal" name="postalCode" value={form.postalCode} onChange={change} />
        </Section>

        <Section title="Cont bancar">
          <Field label="IBAN" name="iban" value={form.iban} onChange={change} placeholder="RO49AAAA1B31007593840000" wide />
          <Field label="Banca" name="bankName" value={form.bankName} onChange={change} />
        </Section>

        <Section title="Contact">
          <Field label="Telefon" name="phone" value={form.phone} onChange={change} />
          <Field label="Email" name="email" value={form.email} onChange={change} />
          <Field label="Website" name="website" value={form.website} onChange={change} />
        </Section>

        <Section title="TVA & facturare">
          <label className="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
            <input type="checkbox" name="vatPayer" checked={form.vatPayer} onChange={change} />
            Firmă plătitoare de TVA
          </label>
          <Field label="Cotă TVA (%)" name="vatRate" type="number" step="0.01" value={form.vatRate} onChange={change} />
          <Field label="Seria facturii" name="invoiceSeries" value={form.invoiceSeries} onChange={change} placeholder="ELS" />
          <Field label="Următorul nr. factură" name="invoiceNextNumber" type="number" value={form.invoiceNextNumber} onChange={change} />
        </Section>

        <Section title="Opțional">
          <Field label="URL logo (apare pe factură)" name="logoUrl" value={form.logoUrl} onChange={change} wide />
          <div className="sm:col-span-2">
            <label className="mb-1 block text-sm font-medium text-slate-600">Mențiuni pe factură</label>
            <textarea
              name="invoiceNotes"
              className="input min-h-[80px]"
              value={form.invoiceNotes}
              onChange={change}
              placeholder="Ex: Factura este valabilă fără semnătură și ștampilă."
            />
          </div>
        </Section>

        <div className="flex justify-end gap-2">
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Se salvează...' : 'Salvează datele'}
          </button>
        </div>
      </form>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div className="card p-5">
      <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">{title}</h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">{children}</div>
    </div>
  );
}

function Field({ label, wide, ...props }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <label className="mb-1 block text-sm font-medium text-slate-600">{label}</label>
      <input className="input" {...props} />
    </div>
  );
}

/**
 * Feature #6 — Admin self-service 2FA. Setup returns a secret + otpauth:// URI
 * (no QR image is rendered — the environment this was built in has no
 * verified way to add a QR-code library — so the code/URI is shown for
 * manual/copy-paste entry into any TOTP authenticator app instead).
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

  return (
    <div className="card mb-6 p-5">
      <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-slate-500">
        Securitate — Autentificare în doi pași (2FA)
      </h2>
      <p className="mb-4 text-sm text-slate-500">
        Protejează contul tău de Admin cu un cod suplimentar generat de o aplicație de autentificare
        (Google Authenticator, Microsoft Authenticator, Authy etc.).
      </p>

      {msg && (
        <div
          className={`mb-4 rounded-lg px-4 py-2 text-sm ${
            msg.type === 'error' ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'
          }`}
        >
          {msg.text}
        </div>
      )}

      {loadingStatus ? (
        <Spinner />
      ) : status?.twoFactorEnabled ? (
        <div className="space-y-3">
          <p className="text-sm text-green-700">✓ Autentificarea în doi pași este activă pe contul tău.</p>
          <form onSubmit={disable} className="flex flex-wrap items-end gap-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">
                Cod curent (pentru a dezactiva)
              </label>
              <input
                className="input w-40"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                required
              />
            </div>
            <button type="submit" className="btn-secondary" disabled={busy || code.length !== 6}>
              {busy ? 'Se procesează...' : 'Dezactivează 2FA'}
            </button>
          </form>
        </div>
      ) : setup ? (
        <form onSubmit={confirmSetup} className="space-y-3">
          <div className="rounded-lg bg-slate-50 p-4">
            <p className="mb-2 text-sm text-slate-600">
              1. Adaugă manual un cont în aplicația de autentificare folosind cheia de mai jos, sau
              copiază linkul otpauth și importă-l.
            </p>
            <p className="mb-1 text-xs font-medium text-slate-500">Cheie secretă</p>
            <code className="mb-3 block break-all rounded bg-white px-3 py-2 text-sm font-mono text-slate-800">
              {setup.secret}
            </code>
            <p className="mb-1 text-xs font-medium text-slate-500">Link otpauth://</p>
            <code className="block break-all rounded bg-white px-3 py-2 text-xs font-mono text-slate-600">
              {setup.otpAuthUrl}
            </code>
          </div>
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">
                2. Introdu codul generat pentru a confirma
              </label>
              <input
                className="input w-40"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                autoFocus
                required
              />
            </div>
            <button type="submit" className="btn-primary" disabled={busy || code.length !== 6}>
              {busy ? 'Se verifică...' : 'Confirmă și activează'}
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => {
                setSetup(null);
                setCode('');
                setMsg(null);
              }}
            >
              Anulează
            </button>
          </div>
        </form>
      ) : (
        <button type="button" className="btn-primary" onClick={startSetup} disabled={busy}>
          {busy ? 'Se pregătește...' : 'Activează 2FA'}
        </button>
      )}

      <div className="mt-5 border-t border-slate-100 pt-4">
        <p className="mb-2 text-xs text-slate-500">
          Ai suspiciunea că un dispozitiv sau o sesiune neautorizată are acces la contul tău?
        </p>
        <button
          type="button"
          className="text-sm font-medium text-red-600 hover:underline"
          onClick={async () => {
            if (!window.confirm('Deconectezi toate sesiunile active de pe toate dispozitivele?')) return;
            await logoutAllSessions();
            navigate('/login');
          }}
        >
          Deconectează toate sesiunile
        </button>
      </div>
    </div>
  );
}
