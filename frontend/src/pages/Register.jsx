import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { AuthShell, GeoIcon, HoloInput, NeonButton } from '../components/xxii';

/**
 * XXII — TASK 1 / TASK 5 / TASK 8 (Zero Gravity Flow: cererea de cont).
 *
 * Împarte scenografia cu pagina de autentificare prin `AuthShell`, deci cele
 * două ecrane nu pot ajunge niciodată să arate diferit.
 *
 * Validarea a fost mutată din momentul trimiterii în momentul tastării, ceea ce
 * este singura schimbare funcțională reală de aici. Înainte, „Parolele nu
 * coincid” apărea abia după apăsarea butonului; acum câmpul de confirmare
 * devine verde imediat ce cele două valori se potrivesc și roșu imediat ce
 * diverg. Butonul rămâne dezactivat cât timp formularul nu este valid, deci o
 * cerere care ar fi fost respinsă nu mai pleacă spre server.
 *
 * `bg-cyan-500/20` și `bg-indigo-600/20` din vechea scenografie au dispărut
 * odată cu mutarea în șablon — erau singurele două clase din acest fișier care
 * nu treceau prin stratul de compatibilitate.
 *
 * Diacriticele lipsă au fost corectate peste tot („Inregistrare esuata” →
 * „Înregistrare eșuată”, „Confirma parola” → „Confirmă parola”).
 */

/** Lungimea minimă acceptată de backend pentru parolă. */
const MIN_PASSWORD = 6;

export default function Register() {
  const { register } = useAuth();

  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // Starea fiecărui câmp se calculează la fiecare randare din valorile curente:
  // `null` cât timp câmpul este gol (un câmp neatins nu este greșit), apoi
  // 'valid' sau 'invalid'.
  const passwordStatus =
    form.password.length === 0 ? null : form.password.length >= MIN_PASSWORD ? 'valid' : 'invalid';

  const confirmStatus =
    form.confirm.length === 0 ? null : form.confirm === form.password ? 'valid' : 'invalid';

  const canSubmit =
    form.fullName.trim().length > 0 &&
    form.email.trim().length > 0 &&
    passwordStatus === 'valid' &&
    confirmStatus === 'valid';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    // Verificarea se repetă aici chiar dacă butonul este dezactivat: formularul
    // se poate trimite și cu Enter, iar un `disabled` vizual nu este o garanție.
    if (form.password !== form.confirm) {
      setError('Parolele nu coincid.');
      return;
    }
    if (form.password.length < MIN_PASSWORD) {
      setError(`Parola trebuie să aibă minimum ${MIN_PASSWORD} caractere.`);
      return;
    }

    setLoading(true);
    try {
      await register({ fullName: form.fullName, email: form.email, password: form.password });
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Înregistrare eșuată.');
    } finally {
      setLoading(false);
    }
  };

  if (done) {
    return (
      <AuthShell title="CERERE TRIMISĂ" eyebrow="Cont în așteptare">
        <div className="text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full border border-[rgba(31,172,121,0.5)] bg-[rgba(31,172,121,0.14)] shadow-[0_0_28px_-6px_rgba(31,172,121,0.7)]">
            <GeoIcon name="check" className="h-7 w-7" accent="#7ee9bd" />
          </div>

          <h2 className="text-lg font-semibold text-[#7ee9bd]">Contul tău a fost creat</h2>

          <p className="mt-2 text-sm leading-relaxed xx-ink-muted">
            Cererea așteaptă aprobarea administratorului. Vei putea intra imediat ce contul este
            activat.
          </p>

          <NeonButton
            to="/login"
            className="mt-6"
            icon={<GeoIcon name="arrow" className="h-4 w-4 rotate-180" accent="currentColor" />}
          >
            Înapoi la autentificare
          </NeonButton>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell
      title="SOLICITĂ ACCES"
      eyebrow="Cont nou"
      footer={
        <p className="mt-6 text-center text-sm xx-ink-muted">
          Ai deja cont?{' '}
          <Link
            to="/login"
            className="font-semibold text-[#22e8f5] transition-colors duration-200 hover:text-[#7ee9ff] hover:underline"
          >
            Autentifică-te
          </Link>
        </p>
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

      <form onSubmit={handleSubmit} className="space-y-2">
        <HoloInput
          id="register-name"
          label="Nume complet"
          name="fullName"
          autoComplete="name"
          placeholder="Popescu Ion"
          icon={<GeoIcon name="user" className="h-4 w-4" accent="currentColor" />}
          value={form.fullName}
          onChange={handleChange}
          required
        />

        <HoloInput
          id="register-email"
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="nume@exemplu.com"
          icon={<GeoIcon name="globe" className="h-4 w-4" accent="currentColor" />}
          value={form.email}
          onChange={handleChange}
          required
        />

        <HoloInput
          id="register-password"
          label="Parolă"
          name="password"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••"
          icon={<GeoIcon name="shield" className="h-4 w-4" accent="currentColor" />}
          value={form.password}
          onChange={handleChange}
          status={passwordStatus}
          hint={`Minimum ${MIN_PASSWORD} caractere.`}
          message={
            passwordStatus === 'invalid'
              ? `Prea scurtă — minimum ${MIN_PASSWORD} caractere.`
              : passwordStatus === 'valid'
                ? 'Lungime acceptată.'
                : undefined
          }
          required
        />

        <HoloInput
          id="register-confirm"
          label="Confirmă parola"
          name="confirm"
          type="password"
          autoComplete="new-password"
          placeholder="••••••••"
          icon={<GeoIcon name="shield" className="h-4 w-4" accent="currentColor" />}
          value={form.confirm}
          onChange={handleChange}
          status={confirmStatus}
          message={
            confirmStatus === 'invalid'
              ? 'Parolele nu coincid.'
              : confirmStatus === 'valid'
                ? 'Parolele coincid.'
                : undefined
          }
          required
        />

        <NeonButton
          type="submit"
          block
          pulse={canSubmit && !loading}
          charging={loading}
          disabled={loading || !canSubmit}
          icon={<GeoIcon name="sparkle" className="h-4 w-4" accent="currentColor" />}
        >
          {loading ? 'Se trimite...' : 'Trimite cererea'}
        </NeonButton>
      </form>
    </AuthShell>
  );
}
