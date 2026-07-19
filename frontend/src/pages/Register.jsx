import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (form.password !== form.confirm) {
      setError('Parolele nu coincid.');
      return;
    }
    if (form.password.length < 6) {
      setError('Parola trebuie să aibă minimum 6 caractere.');
      return;
    }

    setLoading(true);
    try {
      await register({ fullName: form.fullName, email: form.email, password: form.password });
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || 'Înregistrare eșuată.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-md py-10">
      <div className="card p-8">
        <h1 className="text-2xl font-bold text-slate-800">Creează cont</h1>
        <p className="mt-1 text-sm text-slate-500">Înregistrează-te pentru a comanda mai rapid.</p>

        {error && (
          <div className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Nume complet</label>
            <input
              type="text"
              name="fullName"
              className="input"
              value={form.fullName}
              onChange={handleChange}
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Email</label>
            <input
              type="email"
              name="email"
              className="input"
              value={form.email}
              onChange={handleChange}
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Parolă</label>
            <input
              type="password"
              name="password"
              className="input"
              value={form.password}
              onChange={handleChange}
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Confirmă parola</label>
            <input
              type="password"
              name="confirm"
              className="input"
              value={form.confirm}
              onChange={handleChange}
              required
            />
          </div>
          <button type="submit" className="btn-primary w-full" disabled={loading}>
            {loading ? 'Se creează...' : 'Înregistrare'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-500">
          Ai deja cont?{' '}
          <Link to="/login" className="font-medium text-brand-600 hover:underline">
            Autentifică-te
          </Link>
        </p>
      </div>
    </div>
  );
}
