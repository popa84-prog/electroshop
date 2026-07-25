import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (form.password !== form.confirm) {
      setError('Parolele nu coincid.');
      return;
    }
    if (form.password.length < 6) {
      setError('Parola trebuie sa aiba minimum 6 caractere.');
      return;
    }

    setLoading(true);
    try {
      await register({ fullName: form.fullName, email: form.email, password: form.password });
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Inregistrare esuata.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#04060f] px-4 py-10">
      <style>{`
        @keyframes es-drift1 { 0%,100%{transform:translate(0,0)} 50%{transform:translate(40px,30px)} }
        @keyframes es-drift2 { 0%,100%{transform:translate(0,0)} 50%{transform:translate(-50px,-20px)} }
      `}</style>
      <div
        className="pointer-events-none absolute -left-40 -top-40 h-[30rem] w-[30rem] rounded-full bg-cyan-500/20 blur-[120px]"
        style={{ animation: 'es-drift1 18s ease-in-out infinite' }}
      />
      <div
        className="pointer-events-none absolute -bottom-40 -right-40 h-[32rem] w-[32rem] rounded-full bg-indigo-600/20 blur-[130px]"
        style={{ animation: 'es-drift2 22s ease-in-out infinite' }}
      />
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.12]"
        style={{
          backgroundImage:
            'linear-gradient(rgba(56,189,248,.25) 1px, transparent 1px), linear-gradient(90deg, rgba(56,189,248,.25) 1px, transparent 1px)',
          backgroundSize: '46px 46px',
          maskImage: 'radial-gradient(ellipse at center, black 40%, transparent 80%)',
          WebkitMaskImage: 'radial-gradient(ellipse at center, black 40%, transparent 80%)',
        }}
      />

      <div className="relative w-full max-w-md">
        <div className="relative overflow-hidden rounded-3xl border border-cyan-400/25 bg-white/[0.04] p-8 shadow-[0_0_60px_-10px_rgba(34,211,238,0.35)] backdrop-blur-2xl">
          <div className="mb-6 flex flex-col items-center text-center">
            <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-2xl border border-cyan-400/40 bg-cyan-400/10 text-2xl shadow-[0_0_25px_-5px_rgba(34,211,238,0.6)]">
              <span style={{ filter: 'drop-shadow(0 0 6px rgba(34,211,238,.9))' }}>&#9889;</span>
            </div>
            <h1
              className="bg-gradient-to-r from-cyan-300 via-sky-200 to-cyan-400 bg-clip-text text-2xl font-bold tracking-[0.15em] text-transparent"
              style={{ textShadow: '0 0 24px rgba(34,211,238,.35)' }}
            >
              SOLICITA ACCES
            </h1>
            <p className="mt-2 text-xs uppercase tracking-[0.3em] text-cyan-300/70">Cont nou</p>
          </div>

          {done ? (
            <div className="text-center">
              <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full border border-cyan-400/40 bg-cyan-400/10 text-3xl text-cyan-300 shadow-[0_0_25px_-5px_rgba(34,211,238,0.6)]">
                &#10003;
              </div>
              <h2 className="text-lg font-semibold text-cyan-100">Cerere trimisa</h2>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">
                Contul tau a fost creat si asteapta aprobarea administratorului. Vei putea
                intra dupa ce este activat.
              </p>
              <Link
                to="/login"
                className="mt-6 inline-block rounded-xl bg-gradient-to-r from-cyan-400 to-sky-500 px-6 py-2.5 text-sm font-semibold uppercase tracking-wider text-[#04060f] shadow-[0_0_30px_-6px_rgba(34,211,238,0.7)] transition hover:from-cyan-300 hover:to-sky-400"
              >
                Inapoi la login
              </Link>
            </div>
          ) : (
            <>
              {error && (
                <div className="mb-4 rounded-xl border border-red-400/30 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                  {error}
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-4">
                <Field label="Nume complet" name="fullName" value={form.fullName} onChange={handleChange} />
                <Field label="Email" name="email" type="email" value={form.email} onChange={handleChange} />
                <Field
                  label="Parola"
                  name="password"
                  type="password"
                  value={form.password}
                  onChange={handleChange}
                />
                <Field
                  label="Confirma parola"
                  name="confirm"
                  type="password"
                  value={form.confirm}
                  onChange={handleChange}
                />

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full rounded-xl bg-gradient-to-r from-cyan-400 to-sky-500 px-4 py-3 text-sm font-semibold uppercase tracking-[0.15em] text-[#04060f] shadow-[0_0_30px_-6px_rgba(34,211,238,0.7)] transition hover:from-cyan-300 hover:to-sky-400 disabled:opacity-60"
                >
                  {loading ? 'Se trimite...' : 'Trimite cererea'}
                </button>
              </form>

              <p className="mt-6 text-center text-sm text-slate-400">
                Ai deja cont?{' '}
                <Link to="/login" className="font-medium text-cyan-300 hover:text-cyan-200 hover:underline">
                  Autentifica-te
                </Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function Field({ label, name, type = 'text', value, onChange }) {
  return (
    <div>
      <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-cyan-200/80">
        {label}
      </label>
      <input
        type={type}
        name={name}
        value={value}
        onChange={onChange}
        required
        className="w-full rounded-xl border border-cyan-400/20 bg-white/5 px-4 py-3 text-sm text-cyan-50 placeholder-slate-500 outline-none transition focus:border-cyan-300/60 focus:bg-white/10 focus:shadow-[0_0_0_3px_rgba(34,211,238,0.15)]"
      />
    </div>
  );
}
