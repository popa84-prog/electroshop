import { useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from?.pathname || '/';

  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  // A stable starfield generated once.
  const stars = useMemo(
    () =>
      Array.from({ length: 60 }, (_, i) => ({
        id: i,
        top: Math.random() * 100,
        left: Math.random() * 100,
        size: Math.random() * 2 + 1,
        delay: Math.random() * 4,
        dur: Math.random() * 3 + 2,
      })),
    []
  );

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(form);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Autentificare esuata.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#04060f] px-4 py-10">
      <style>{`
        @keyframes es-twinkle { 0%,100%{opacity:.15} 50%{opacity:1} }
        @keyframes es-drift1 { 0%,100%{transform:translate(0,0)} 50%{transform:translate(40px,30px)} }
        @keyframes es-drift2 { 0%,100%{transform:translate(0,0)} 50%{transform:translate(-50px,-20px)} }
        @keyframes es-spin { to { transform: rotate(360deg) } }
        @keyframes es-scan { 0%{transform:translateY(-120%)} 100%{transform:translateY(2200%)} }
        @keyframes es-float { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-8px)} }
      `}</style>

      {/* Nebula orbs */}
      <div
        className="pointer-events-none absolute -left-40 -top-40 h-[32rem] w-[32rem] rounded-full bg-cyan-500/20 blur-[120px]"
        style={{ animation: 'es-drift1 18s ease-in-out infinite' }}
      />
      <div
        className="pointer-events-none absolute -bottom-40 -right-40 h-[34rem] w-[34rem] rounded-full bg-indigo-600/20 blur-[130px]"
        style={{ animation: 'es-drift2 22s ease-in-out infinite' }}
      />
      <div className="pointer-events-none absolute left-1/2 top-1/2 h-72 w-72 -translate-x-1/2 -translate-y-1/2 rounded-full bg-sky-400/10 blur-[100px]" />

      {/* Grid */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.15]"
        style={{
          backgroundImage:
            'linear-gradient(rgba(56,189,248,.25) 1px, transparent 1px), linear-gradient(90deg, rgba(56,189,248,.25) 1px, transparent 1px)',
          backgroundSize: '46px 46px',
          maskImage: 'radial-gradient(ellipse at center, black 40%, transparent 80%)',
          WebkitMaskImage: 'radial-gradient(ellipse at center, black 40%, transparent 80%)',
        }}
      />

      {/* Stars */}
      <div className="pointer-events-none absolute inset-0">
        {stars.map((s) => (
          <span
            key={s.id}
            className="absolute rounded-full bg-white"
            style={{
              top: `${s.top}%`,
              left: `${s.left}%`,
              width: `${s.size}px`,
              height: `${s.size}px`,
              animation: `es-twinkle ${s.dur}s ease-in-out ${s.delay}s infinite`,
            }}
          />
        ))}
      </div>

      {/* Card */}
      <div className="relative w-full max-w-md" style={{ animation: 'es-float 6s ease-in-out infinite' }}>
        {/* Rotating conic glow behind the card */}
        <div className="pointer-events-none absolute -inset-[1px] overflow-hidden rounded-3xl">
          <div
            className="absolute left-1/2 top-1/2 h-[200%] w-[200%] -translate-x-1/2 -translate-y-1/2 opacity-40"
            style={{
              background:
                'conic-gradient(from 0deg, transparent 0deg, rgba(34,211,238,.7) 60deg, transparent 140deg, transparent 220deg, rgba(99,102,241,.6) 300deg, transparent 360deg)',
              animation: 'es-spin 8s linear infinite',
            }}
          />
        </div>

        <div className="relative overflow-hidden rounded-3xl border border-cyan-400/25 bg-white/[0.04] p-8 shadow-[0_0_60px_-10px_rgba(34,211,238,0.35)] backdrop-blur-2xl">
          {/* Scan line */}
          <div
            className="pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b from-cyan-400/20 to-transparent"
            style={{ animation: 'es-scan 5s linear infinite' }}
          />

          <div className="relative">
            {/* Logo */}
            <div className="mb-6 flex flex-col items-center text-center">
              <div className="mb-3 flex h-16 w-16 items-center justify-center rounded-2xl border border-cyan-400/40 bg-cyan-400/10 text-3xl shadow-[0_0_25px_-5px_rgba(34,211,238,0.6)]">
                <span style={{ filter: 'drop-shadow(0 0 6px rgba(34,211,238,.9))' }}>&#9889;</span>
              </div>
              <h1
                className="bg-gradient-to-r from-cyan-300 via-sky-200 to-cyan-400 bg-clip-text text-2xl font-bold tracking-[0.2em] text-transparent"
                style={{ textShadow: '0 0 24px rgba(34,211,238,.35)' }}
              >
                ELECTROSHOP
              </h1>
              <p className="mt-2 text-xs uppercase tracking-[0.35em] text-cyan-300/70">
                Acces securizat
              </p>
            </div>

            {error && (
              <div className="mb-4 rounded-xl border border-red-400/30 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-cyan-200/80">
                  Email
                </label>
                <input
                  type="email"
                  name="email"
                  autoComplete="email"
                  className="w-full rounded-xl border border-cyan-400/20 bg-white/5 px-4 py-3 text-sm text-cyan-50 placeholder-slate-500 outline-none transition focus:border-cyan-300/60 focus:bg-white/10 focus:shadow-[0_0_0_3px_rgba(34,211,238,0.15)]"
                  placeholder="nume@exemplu.com"
                  value={form.email}
                  onChange={handleChange}
                  required
                />
              </div>
              <div>
                <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-cyan-200/80">
                  Parola
                </label>
                <input
                  type="password"
                  name="password"
                  autoComplete="current-password"
                  className="w-full rounded-xl border border-cyan-400/20 bg-white/5 px-4 py-3 text-sm text-cyan-50 placeholder-slate-500 outline-none transition focus:border-cyan-300/60 focus:bg-white/10 focus:shadow-[0_0_0_3px_rgba(34,211,238,0.15)]"
                  placeholder="&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;"
                  value={form.password}
                  onChange={handleChange}
                  required
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="group relative w-full overflow-hidden rounded-xl bg-gradient-to-r from-cyan-400 to-sky-500 px-4 py-3 text-sm font-semibold uppercase tracking-[0.15em] text-[#04060f] shadow-[0_0_30px_-6px_rgba(34,211,238,0.7)] transition hover:from-cyan-300 hover:to-sky-400 disabled:opacity-60"
              >
                {loading ? 'Se conecteaza...' : 'Conectare'}
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-slate-400">
              Nu ai cont?{' '}
              <Link to="/register" className="font-medium text-cyan-300 hover:text-cyan-200 hover:underline">
                Solicita acces
              </Link>
            </p>

            <p className="mt-5 border-t border-white/5 pt-4 text-center text-[11px] leading-relaxed text-slate-500">
              Acces exclusiv autentificat. Conturile noi sunt activate dupa
              aprobarea administratorului.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
