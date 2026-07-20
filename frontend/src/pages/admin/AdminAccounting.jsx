import { useEffect, useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Spinner from '../../components/Spinner';
import { formatPrice } from '../../utils/format';

const firstOfMonthISO = () => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
};
const todayISO = () => new Date().toISOString().slice(0, 10);

function Stat({ label, value, tone = 'slate', icon }) {
  const tones = {
    green: 'text-green-700',
    red: 'text-red-700',
    blue: 'text-brand-700',
    slate: 'text-slate-900',
  };
  return (
    <div className="card p-5">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <span>{icon}</span>
        {label}
      </div>
      <p className={`mt-2 text-2xl font-bold ${tones[tone]}`}>{value}</p>
    </div>
  );
}

export default function AdminAccounting() {
  const [from, setFrom] = useState(firstOfMonthISO());
  const [to, setTo] = useState(todayISO());
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    adminService
      .accountingReport({ from, to })
      .then(setReport)
      .catch(() => setReport(null))
      .finally(() => setLoading(false));
  };

  useEffect(load, []); // initial load
  // eslint-disable-next-line react-hooks/exhaustive-deps

  const profitTone = report && Number(report.profit) >= 0 ? 'green' : 'red';

  return (
    <div>
      <AdminNav />
      <h1 className="mb-4 text-2xl font-bold text-slate-800">Contabilitate primară</h1>

      <div className="card mb-6 flex flex-wrap items-end gap-3 p-4">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-600">De la</label>
          <input type="date" className="input" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-600">Până la</label>
          <input type="date" className="input" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <button className="btn-primary" onClick={load}>
          Aplică
        </button>
      </div>

      {loading ? (
        <Spinner />
      ) : !report ? (
        <p className="text-slate-500">Raportul nu a putut fi încărcat.</p>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Venituri (vânzări)" value={formatPrice(report.salesTotal)} tone="green" icon="📈" />
            <Stat label="Cheltuieli (cumpărări)" value={formatPrice(report.purchasesTotal)} tone="red" icon="📉" />
            <Stat
              label="Rezultat (profit)"
              value={formatPrice(report.profit)}
              tone={profitTone}
              icon="💰"
            />
            <Stat label="Marjă" value={`${Number(report.marginPercent).toFixed(1)}%`} tone="blue" icon="％" />
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="card p-4 text-sm text-slate-600">
              <span className="font-medium">Comenzi (vânzări):</span> {report.salesCount}
            </div>
            <div className="card p-4 text-sm text-slate-600">
              <span className="font-medium">Intrări (cumpărări):</span> {report.purchasesCount}
            </div>
          </div>

          <div className="card p-5">
            <h2 className="mb-4 font-semibold text-slate-800">Vânzări vs. cumpărări pe zile</h2>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={report.byDay}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip formatter={(v) => formatPrice(v)} />
                <Legend />
                <Bar dataKey="sales" name="Vânzări" fill="#16a34a" radius={[3, 3, 0, 0]} />
                <Bar dataKey="purchases" name="Cumpărări" fill="#ef4444" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <p className="text-xs text-slate-400">
            Contabilitate primară pe bază de casă: venituri din comenzile plasate (exceptând cele anulate)
            minus cheltuielile cu marfa (intrări de la furnizori) în perioada selectată.
          </p>
        </div>
      )}
    </div>
  );
}
