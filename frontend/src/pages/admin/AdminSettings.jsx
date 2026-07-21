import { useEffect, useState } from 'react';
import AdminNav from '../../components/AdminNav';
import adminService from '../../api/adminService';
import Spinner from '../../components/Spinner';

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
