import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import orderService from '../api/orderService';
import { formatPrice, resolveImage } from '../utils/format';
import {
  GeoIcon,
  Grid12,
  HoloInput,
  Module,
  NeonBadge,
  NeonButton,
  SectionHeader,
} from '../components/xxii';

/**
 * XXII — TASK 5 (Checkout Futurist: Zero Gravity Flow).
 *
 * Two steps, not four. The backend needs exactly one field — the shipping
 * address — plus the cart, so the flow is: **details → confirmation**. Inventing
 * extra steps to look sophisticated would add friction and buy nothing.
 *
 *   Step 1  Date de livrare — name, phone, address, city, county, postal code,
 *           optional notes, all with instant validation.
 *   Step 2  Confirmare — a read-only review of exactly what will be sent, and
 *           the charging final button.
 *
 * Validation model. A field is validated on blur and re-validated on every
 * keystroke *after* it has been touched. That is the honest middle ground: the
 * user is not shouted at while typing their first character, and once a field is
 * known-bad the error clears the instant it becomes good. `HoloInput`'s
 * three-value `status` prop carries this directly — `null` for untouched.
 *
 * The step transition slides: the outgoing step is not animated out (which would
 * hold the user waiting), only the incoming one animates in, so the flow feels
 * fast in the direction the user is moving.
 *
 * The submit button uses `charging` while the request is in flight, which is the
 * only place in the app where a >250ms animation is correct: it is reporting
 * real network latency, not decorating an interaction.
 */

const FREE_SHIPPING_FROM = 300;
const SHIPPING_COST = 19.99;

/** One rule per field. Returns an error string, or null when the value is good. */
const RULES = {
  fullName: (value) =>
    value.trim().length < 3 ? 'Introdu numele complet (minim 3 caractere).' : null,
  phone: (value) =>
    /^(\+4)?0?7\d{8}$/.test(value.replace(/[\s.-]/g, ''))
      ? null
      : 'Introdu un număr de telefon mobil valid (ex. 07xx xxx xxx).',
  street: (value) =>
    value.trim().length < 5 ? 'Introdu strada și numărul.' : null,
  city: (value) => (value.trim().length < 2 ? 'Introdu localitatea.' : null),
  county: (value) => (value.trim().length < 2 ? 'Introdu județul.' : null),
  postalCode: (value) =>
    /^\d{6}$/.test(value.trim()) ? null : 'Codul poștal are 6 cifre.',
  notes: () => null,
};

const FIELDS = ['fullName', 'phone', 'street', 'city', 'county', 'postalCode'];

const EMPTY = {
  fullName: '',
  phone: '',
  street: '',
  city: '',
  county: '',
  postalCode: '',
  notes: '',
};

export default function Checkout() {
  const { items, totalPrice, clearCart } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [form, setForm] = useState(() => ({
    ...EMPTY,
    fullName: [user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.fullName || '',
    phone: user?.phone || '',
  }));
  const [touched, setTouched] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const shipping = totalPrice >= FREE_SHIPPING_FROM ? 0 : SHIPPING_COST;
  const grandTotal = totalPrice + shipping;

  const errors = useMemo(() => {
    const result = {};
    Object.keys(RULES).forEach((field) => {
      result[field] = RULES[field](form[field] || '');
    });
    return result;
  }, [form]);

  const stepOneValid = FIELDS.every((field) => !errors[field]);

  /** `null` until touched, then 'valid' or 'invalid'. */
  const statusOf = (field) => {
    if (!touched[field]) return null;
    return errors[field] ? 'invalid' : 'valid';
  };

  const setField = (field) => (event) => {
    const { value } = event.target;
    setForm((current) => ({ ...current, [field]: value }));
  };

  const markTouched = (field) => () => setTouched((current) => ({ ...current, [field]: true }));

  const shippingAddress = useMemo(
    () =>
      [
        form.fullName.trim(),
        form.phone.trim(),
        form.street.trim(),
        `${form.city.trim()}, jud. ${form.county.trim()}`,
        form.postalCode.trim(),
        form.notes.trim() ? `Observații: ${form.notes.trim()}` : '',
      ]
        .filter(Boolean)
        .join(' · '),
    [form],
  );

  const goToConfirmation = () => {
    // Touch everything so any remaining problem is shown, then gate.
    setTouched(FIELDS.reduce((all, field) => ({ ...all, [field]: true }), {}));
    if (!stepOneValid) return;
    setStep(2);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!stepOneValid || submitting) return;

    setError(null);
    setSubmitting(true);
    try {
      const order = await orderService.place({
        shippingAddress,
        items: items.map((item) => ({ productId: item.id, quantity: item.quantity })),
      });
      clearCart();
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } });
    } catch (err) {
      setError(err.response?.data?.message || 'Comanda nu a putut fi plasată.');
      setStep(2);
    } finally {
      setSubmitting(false);
    }
  };

  /* ---------------- empty cart ---------------- */

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-lg py-20 text-center">
        <span
          aria-hidden="true"
          className="mx-auto grid h-16 w-16 place-items-center rounded-2xl border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)]"
        >
          <GeoIcon name="cart" className="h-7 w-7" accent="var(--xx-ink-dim)" />
        </span>
        <h1 className="xx-title mt-5 text-2xl">Coșul este gol</h1>
        <p className="mt-2 text-sm xx-ink-muted">
          Nu există produse de comandat. Alege ceva din catalog și revino la finalizare.
        </p>
        <NeonButton to="/products" size="lg" className="mt-6">
          Vezi produse
        </NeonButton>
      </div>
    );
  }

  /* ---------------- flow ---------------- */

  return (
    <div className="pb-6">
      <SectionHeader as="h1" eyebrow="Zero gravity flow" title="Finalizare comandă" />

      {/* Step rail — two states, never more, and the current one is named. */}
      <ol className="mb-6 flex items-center gap-3" aria-label="Pași finalizare">
        {[
          { index: 1, label: 'Date de livrare' },
          { index: 2, label: 'Confirmare' },
        ].map((entry) => {
          const active = step === entry.index;
          const done = step > entry.index;
          return (
            <li key={entry.index} className="flex flex-1 items-center gap-3">
              <span
                aria-current={active ? 'step' : undefined}
                className={`grid h-9 w-9 shrink-0 place-items-center rounded-full border font-display text-sm font-bold transition-all duration-xx ease-xx ${
                  active
                    ? 'border-[rgba(34,232,245,0.7)] bg-[rgba(34,232,245,0.14)] text-white shadow-glow-aqua'
                    : done
                      ? 'border-[rgba(110,247,168,0.5)] bg-[rgba(110,247,168,0.12)] text-[color:var(--xx-lime)]'
                      : 'border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] text-[color:var(--xx-ink-dim)]'
                }`}
              >
                {done ? <GeoIcon name="check" className="h-4 w-4" accent="currentColor" /> : entry.index}
              </span>
              <span
                className={`text-sm font-semibold ${
                  active ? 'text-[color:var(--xx-ink)]' : 'xx-ink-dim'
                }`}
              >
                {entry.label}
              </span>
              {entry.index === 1 ? (
                <span
                  aria-hidden="true"
                  className="hidden h-px flex-1 bg-[rgba(255,255,255,0.12)] sm:block"
                />
              ) : null}
            </li>
          );
        })}
      </ol>

      {error && (
        <div
          role="alert"
          className="mb-5 flex items-start gap-3 rounded-2xl border border-[rgba(255,84,112,0.45)] bg-[rgba(30,8,18,0.75)] px-4 py-3 text-sm text-[#ffc2cc] animate-xx-materialize"
        >
          <GeoIcon name="alert" className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-red)" />
          <span>{error}</span>
        </div>
      )}

      <Grid12 className="items-start">
        <Module span={8} spanSm={6} spanTv={4}>
          <form onSubmit={handleSubmit}>
            {step === 1 ? (
              <section key="step-1" className="card p-5 animate-xx-materialize sm:p-6">
                <p className="xx-eyebrow">Pasul 1</p>
                <h2 className="xx-title text-xl">Date de livrare</h2>

                <div className="mt-5 grid gap-4 sm:grid-cols-2">
                  <HoloInput
                    label="Nume complet"
                    value={form.fullName}
                    onChange={setField('fullName')}
                    onBlur={markTouched('fullName')}
                    status={statusOf('fullName')}
                    message={touched.fullName ? errors.fullName : ''}
                    hint="Persoana care primește coletul"
                    autoComplete="name"
                    icon={<GeoIcon name="user" className="h-4 w-4" accent="currentColor" />}
                  />
                  <HoloInput
                    label="Telefon"
                    value={form.phone}
                    onChange={setField('phone')}
                    onBlur={markTouched('phone')}
                    status={statusOf('phone')}
                    message={touched.phone ? errors.phone : ''}
                    hint="Curierul sună înainte de livrare"
                    inputMode="tel"
                    autoComplete="tel"
                    icon={<GeoIcon name="pulse" className="h-4 w-4" accent="currentColor" />}
                  />
                  <HoloInput
                    containerClassName="sm:col-span-2"
                    label="Stradă și număr"
                    value={form.street}
                    onChange={setField('street')}
                    onBlur={markTouched('street')}
                    status={statusOf('street')}
                    message={touched.street ? errors.street : ''}
                    hint="Include bloc, scară și apartament dacă e cazul"
                    autoComplete="address-line1"
                    icon={<GeoIcon name="home" className="h-4 w-4" accent="currentColor" />}
                  />
                  <HoloInput
                    label="Localitate"
                    value={form.city}
                    onChange={setField('city')}
                    onBlur={markTouched('city')}
                    status={statusOf('city')}
                    message={touched.city ? errors.city : ''}
                    autoComplete="address-level2"
                  />
                  <HoloInput
                    label="Județ"
                    value={form.county}
                    onChange={setField('county')}
                    onBlur={markTouched('county')}
                    status={statusOf('county')}
                    message={touched.county ? errors.county : ''}
                    autoComplete="address-level1"
                  />
                  <HoloInput
                    label="Cod poștal"
                    value={form.postalCode}
                    onChange={setField('postalCode')}
                    onBlur={markTouched('postalCode')}
                    status={statusOf('postalCode')}
                    message={touched.postalCode ? errors.postalCode : ''}
                    inputMode="numeric"
                    autoComplete="postal-code"
                  />
                  <HoloInput
                    containerClassName="sm:col-span-2"
                    as="textarea"
                    label="Observații (opțional)"
                    value={form.notes}
                    onChange={setField('notes')}
                    className="min-h-[92px]"
                    hint="Interval orar preferat, reper, alte detalii"
                  />
                </div>

                <div className="mt-2 flex items-center gap-3 rounded-xl border border-[rgba(34,232,245,0.22)] bg-[rgba(34,232,245,0.06)] px-4 py-3 text-sm xx-ink-muted">
                  <GeoIcon name="coins" className="h-5 w-5 shrink-0" accent="var(--xx-cyan)" />
                  Plata se face la livrare (ramburs). Nu este necesar un card.
                </div>

                <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:justify-end">
                  <NeonButton variant="ghost" size="lg" to="/cart">
                    Înapoi la coș
                  </NeonButton>
                  <NeonButton
                    size="lg"
                    onClick={goToConfirmation}
                    icon={<GeoIcon name="arrow" className="h-5 w-5" accent="currentColor" />}
                  >
                    Continuă spre confirmare
                  </NeonButton>
                </div>
              </section>
            ) : (
              <section key="step-2" className="card p-5 animate-xx-materialize sm:p-6">
                <p className="xx-eyebrow">Pasul 2</p>
                <h2 className="xx-title text-xl">Confirmare</h2>
                <p className="mt-2 text-sm xx-ink-muted">
                  Verifică datele înainte de plasare. Comanda se trimite exact cu informațiile de mai
                  jos.
                </p>

                <dl className="mt-5 space-y-2">
                  {[
                    { label: 'Nume', value: form.fullName, icon: 'user' },
                    { label: 'Telefon', value: form.phone, icon: 'pulse' },
                    { label: 'Adresă', value: form.street, icon: 'home' },
                    {
                      label: 'Localitate',
                      value: `${form.city}, jud. ${form.county}, ${form.postalCode}`,
                      icon: 'globe',
                    },
                    { label: 'Observații', value: form.notes, icon: 'document' },
                  ]
                    .filter((row) => row.value && String(row.value).trim())
                    .map((row) => (
                      <div
                        key={row.label}
                        className="flex items-start gap-3 rounded-xl border border-[rgba(255,255,255,0.09)] bg-[rgba(255,255,255,0.035)] px-3.5 py-3"
                      >
                        <GeoIcon name={row.icon} className="mt-0.5 h-4 w-4 shrink-0" accent="var(--xx-cyan)" />
                        <dt className="w-24 shrink-0 text-xs uppercase tracking-[0.12em] xx-ink-dim">
                          {row.label}
                        </dt>
                        <dd className="min-w-0 flex-1 text-sm text-[color:var(--xx-ink)]">{row.value}</dd>
                      </div>
                    ))}
                </dl>

                <div className="mt-5 flex items-center gap-3 rounded-xl border border-[rgba(110,247,168,0.28)] bg-[rgba(110,247,168,0.07)] px-4 py-3 text-sm xx-ink-muted">
                  <GeoIcon name="truck" className="h-5 w-5 shrink-0" accent="var(--xx-lime)" />
                  Livrare estimată în <span className="font-semibold text-[color:var(--xx-ink)]">24–48 de ore</span>,
                  plata ramburs la curier.
                </div>

                <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-between">
                  <NeonButton
                    variant="ghost"
                    size="lg"
                    disabled={submitting}
                    onClick={() => setStep(1)}
                  >
                    Modifică datele
                  </NeonButton>
                  <NeonButton
                    type="submit"
                    size="lg"
                    pulse={!submitting}
                    charging={submitting}
                    disabled={submitting}
                    icon={<GeoIcon name="bolt" className="h-5 w-5" accent="currentColor" />}
                  >
                    {submitting ? 'Se plasează comanda...' : `Plasează comanda · ${formatPrice(grandTotal)}`}
                  </NeonButton>
                </div>
              </section>
            )}
          </form>
        </Module>

        {/* ---------- data panel ---------- */}
        <Module span={4} spanSm={6} spanTv={2}>
          <div
            className="card p-5 lg:sticky lg:top-28"
            style={{ boxShadow: 'inset 0 0 80px -26px rgba(122,60,255,0.65), 0 26px 60px -30px rgba(0,0,0,0.9)' }}
          >
            <div className="flex items-center justify-between">
              <div>
                <p className="xx-eyebrow">Data panel</p>
                <h2 className="xx-title text-lg">Comanda ta</h2>
              </div>
              <NeonBadge tone="aqua" icon={<GeoIcon name="box" className="h-3 w-3" accent="currentColor" />}>
                {items.length} {items.length === 1 ? 'produs' : 'produse'}
              </NeonBadge>
            </div>

            <ul className="mt-4 space-y-3">
              {items.map((item) => (
                <li key={item.id} className="flex items-center gap-3">
                  <img
                    src={resolveImage(item.imageUrl)}
                    alt=""
                    loading="lazy"
                    className="h-12 w-12 shrink-0 rounded-lg object-cover"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-1 text-sm font-medium text-[color:var(--xx-ink)]">{item.name}</p>
                    <p className="text-xs xx-ink-dim">
                      {item.quantity} × {formatPrice(item.price)}
                    </p>
                  </div>
                  <span className="shrink-0 text-sm font-semibold text-[color:var(--xx-ink)]">
                    {formatPrice(Number(item.price) * item.quantity)}
                  </span>
                </li>
              ))}
            </ul>

            <dl className="mt-4 space-y-2 border-t border-[rgba(255,255,255,0.1)] pt-4 text-sm">
              <div className="flex items-center justify-between">
                <dt className="xx-ink-muted">Subtotal</dt>
                <dd className="font-semibold text-[color:var(--xx-ink)]">{formatPrice(totalPrice)}</dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="xx-ink-muted">Transport</dt>
                <dd className="font-semibold">
                  {shipping === 0 ? (
                    <span className="text-[color:var(--xx-lime)]">Gratuit</span>
                  ) : (
                    <span className="text-[color:var(--xx-ink)]">{formatPrice(shipping)}</span>
                  )}
                </dd>
              </div>
            </dl>

            <div className="mt-4 flex items-center justify-between border-t border-[rgba(255,255,255,0.1)] pt-4">
              <span className="text-sm uppercase tracking-[0.14em] xx-ink-dim">Total</span>
              <span className="font-display text-2xl font-bold xx-text-gradient">
                {formatPrice(grandTotal)}
              </span>
            </div>

            <p className="mt-3 flex items-center gap-2 text-xs xx-ink-dim">
              <GeoIcon name="shield" className="h-4 w-4" accent="var(--xx-aqua)" />
              Datele sunt folosite exclusiv pentru livrare.
            </p>
          </div>
        </Module>
      </Grid12>
    </div>
  );
}
