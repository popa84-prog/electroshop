import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AuthShell, GeoIcon, HoloInput, NeonButton } from '../components/xxii';

/**
 * XXII — TASK 1 / TASK 5 / TASK 8 (Zero Gravity Flow: poarta de acces).
 *
 * Scenografia — sfere de nebuloasă, grilă, stele, aură rotitoare — a fost mutată
 * în `components/xxii/AuthShell.jsx`, pe care îl folosește și pagina de
 * înregistrare. Fișierul acesta conține de acum doar logica de autentificare și
 * cele două formulare, ceea ce înseamnă că o schimbare de atmosferă se face
 * într-un singur loc, nu în două fișiere care se pot desincroniza.
 *
 * Câmpurile erau `<input>` brute cu clase cyan scrise de mână. Sunt acum
 * `HoloInput`, deci primesc gratuit: eticheta legată prin `htmlFor`, bara de
 * scanare la focus, linia de status cu înălțime rezervată (layoutul nu mai
 * sare când apare un mesaj) și micro-tremurul o singură dată la trecerea în
 * starea invalidă.
 *
 * Trei corecții de comportament:
 *
 *   1. **Eroarea are `role="alert"` și o pictogramă.** Înainte era un simplu
 *      `<div>` roșu — un cititor de ecran nu afla niciodată că autentificarea
 *      a eșuat, pentru că focusul rămânea pe buton.
 *   2. **Câmpul de cod 2FA primește `status="valid"` la exact șase cifre.**
 *      Confirmarea că este complet apare înainte de apăsarea butonului.
 *   3. **Diacriticele lipsă au fost corectate** („Se conecteaza” → „Se
 *      conectează”, „Autentificare esuata” → „Autentificare eșuată”).
 */
export default function Login() {
  const { login, verifyTwoFactor } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from?.pathname || '/';

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  // Feature #6: 2FA challenge step. Once login() reports requiresTwoFactor,
  // the password screen is replaced by a 6-digit authenticator-code screen —
  // no session exists yet until that code is verified.
  const [twoFactorToken, setTwoFactorToken] = useState(null);
  const [code, setCode] = useState('');

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const data = await login(form);
      if (data.requiresTwoFactor) {
        setTwoFactorToken(data.twoFactorToken);
      } else {
        navigate(from, { replace: true });
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Autentificare eșuată.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyTwoFactor = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await verifyTwoFactor(twoFactorToken, code);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Cod incorect.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell
      title="ELECTROSHOP"
      eyebrow={twoFactorToken ? 'Verificare în doi pași' : 'Acces securizat'}
      footer={
        !twoFactorToken ? (
          <>
            <p className="mt-6 text-center text-sm xx-ink-muted">
              Nu ai cont?{' '}
              <Link
                to="/register"
                className="font-semibold text-[#22e8f5] transition-colors duration-200 hover:text-[#7ee9ff] hover:underline"
              >
                Solicită acces
              </Link>
            </p>

            <p className="mt-5 border-t border-[rgba(255,255,255,0.07)] pt-4 text-center text-[11px] leading-relaxed xx-ink-dim">
              Acces exclusiv autentificat. Conturile noi sunt activate după aprobarea
              administratorului.
            </p>
          </>
        ) : null
      }
    >
      {error && (
        <div
          role="alert"
          className="mb-4 flex items-start gap-2.5 rounded-[0.9rem] border border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.12)] px-4 py-3 text-sm text-[#ff8fa8]"
        >
          <GeoIcon name="alert" className="mt-0.5 h-4 w-4 shrink-0" accent="currentColor" />
          <span>{error}</span>
        </div>
      )}

      {twoFactorToken ? (
        <form onSubmit={handleVerifyTwoFactor} className="space-y-2">
          <p className="mb-3 text-center text-sm xx-ink-muted">
            Introdu codul din aplicația de autentificare (Google Authenticator, Authy sau
            echivalent).
          </p>

          <HoloInput
            id="login-2fa-code"
            label="Cod de verificare"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="000000"
            className="text-center text-lg tracking-[0.4em]"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            status={code.length === 6 ? 'valid' : null}
            hint="Șase cifre, valabile 30 de secunde."
            message={code.length === 6 ? 'Cod complet.' : undefined}
            required
          />

          <NeonButton
            type="submit"
            block
            pulse={code.length === 6 && !loading}
            charging={loading}
            disabled={loading || code.length !== 6}
            icon={<GeoIcon name="shield" className="h-4 w-4" accent="currentColor" />}
          >
            {loading ? 'Se verifică...' : 'Confirmă codul'}
          </NeonButton>

          <button
            type="button"
            onClick={() => {
              setTwoFactorToken(null);
              setCode('');
              setError(null);
            }}
            className="mt-2 flex w-full items-center justify-center gap-1.5 text-center text-xs xx-ink-dim transition-colors duration-200 hover:text-[#22e8f5]"
          >
            <GeoIcon name="arrow" className="h-3 w-3 rotate-180" accent="currentColor" />
            Înapoi la autentificare
          </button>
        </form>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-2">
          <HoloInput
            id="login-email"
            label="Email"
            type="email"
            name="email"
            autoComplete="email"
            placeholder="nume@exemplu.com"
            icon={<GeoIcon name="user" className="h-4 w-4" accent="currentColor" />}
            value={form.email}
            onChange={handleChange}
            required
          />

          <HoloInput
            id="login-password"
            label="Parolă"
            type="password"
            name="password"
            autoComplete="current-password"
            placeholder="••••••••"
            icon={<GeoIcon name="shield" className="h-4 w-4" accent="currentColor" />}
            value={form.password}
            onChange={handleChange}
            required
          />

          <NeonButton
            type="submit"
            block
            charging={loading}
            disabled={loading}
            icon={<GeoIcon name="bolt" className="h-4 w-4" accent="currentColor" />}
          >
            {loading ? 'Se conectează...' : 'Conectare'}
          </NeonButton>
        </form>
      )}
    </AuthShell>
  );
}
