import { useEffect, useState } from 'react';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';

function fmt(dt) {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleString('ro-RO', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dt;
  }
}

// Feature #6: "loguri pentru autentificări reușite/eșuate" — human labels for
// the machine-readable failureReason the backend records on a failed attempt.
const FAILURE_LABELS = {
  bad_credentials: 'Parolă greșită',
  account_locked: 'Cont blocat (brute-force)',
  account_disabled: 'Cont dezactivat',
  not_approved: 'Cont neaprobat',
  bad_2fa_code: 'Cod 2FA greșit',
};

function shortDevice(ua) {
  if (!ua) return '—';
  let os = '';
  if (/Windows/i.test(ua)) os = 'Windows';
  else if (/Android/i.test(ua)) os = 'Android';
  else if (/iPhone|iPad|iOS/i.test(ua)) os = 'iOS';
  else if (/Mac OS X|Macintosh/i.test(ua)) os = 'macOS';
  else if (/Linux/i.test(ua)) os = 'Linux';
  let br = '';
  if (/Edg\//i.test(ua)) br = 'Edge';
  else if (/Chrome\//i.test(ua)) br = 'Chrome';
  else if (/Firefox\//i.test(ua)) br = 'Firefox';
  else if (/Safari\//i.test(ua)) br = 'Safari';
  return [br, os].filter(Boolean).join(' · ') || 'Necunoscut';
}

export default function AdminLoginEvents() {
  const [events, setEvents] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    adminService
      .loginEvents({ page, size: 25 })
      .then((data) => {
        setEvents(data.content || []);
        setTotalPages(data.totalPages || 0);
      })
      .catch(() => setEvents([]))
      .finally(() => setLoading(false));
  }, [page]);

  return (
    <div>
      <AdminNav />
      <div className="mb-4">
        <h1 className="text-2xl font-bold text-slate-800">Conectări utilizatori</h1>
        <p className="mt-1 text-sm text-slate-500">
          Cine s-a autentificat, de la ce adresă IP și din ce locație aproximativă.
        </p>
      </div>

      {loading ? (
        <Spinner />
      ) : events.length === 0 ? (
        <p className="py-16 text-center text-slate-500">Nicio conectare înregistrată încă.</p>
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Utilizator</th>
                <th className="px-4 py-3">Adresă IP</th>
                <th className="px-4 py-3">Locație</th>
                <th className="px-4 py-3">Dispozitiv</th>
                <th className="px-4 py-3">Data / ora</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {events.map((e) => (
                <tr key={e.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    {e.success ? (
                      <span className="badge bg-green-100 text-green-800">✓ Reușită</span>
                    ) : (
                      <span
                        className="badge bg-red-100 text-red-800"
                        title={FAILURE_LABELS[e.failureReason] || e.failureReason || ''}
                      >
                        ✕ {FAILURE_LABELS[e.failureReason] || 'Eșuată'}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-800">{e.userName || '—'}</p>
                    <p className="text-xs text-slate-500">{e.userEmail}</p>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-600">{e.ipAddress || '—'}</td>
                  <td className="px-4 py-3 text-slate-600">
                    <span className="inline-flex items-center gap-1">
                      📍 {e.location || 'Necunoscut'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{shortDevice(e.userAgent)}</td>
                  <td className="px-4 py-3 text-slate-600">{fmt(e.loginAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={totalPages} onChange={setPage} />
    </div>
  );
}
