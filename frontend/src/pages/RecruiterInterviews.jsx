import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';

export default function RecruiterInterviews() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const load = async () => {
    try { setRows((await api.get('/recruiter/interviews')).data || []); }
    catch (requestError) { setError(requestError.response?.data?.detail || 'Unable to load interviews'); }
  };
  useEffect(() => { load(); }, []);
  const reschedule = async (row) => {
    const scheduledAt = window.prompt('New ISO date/time', row.scheduledAt);
    if (!scheduledAt) return;
    await api.put(`/recruiter/applications/${row.applicationId}/interviews/${row.id}`, {
      scheduledAt, mode: row.mode, locationOrLink: row.locationOrLink, notes: row.notes,
    });
    await load();
  };
  const cancel = async (row) => {
    await api.post(`/recruiter/applications/${row.applicationId}/interviews/${row.id}/cancel`);
    await load();
  };
  const feedback = async (row) => {
    const text = window.prompt('Interview feedback');
    if (!text) return;
    try {
      await api.post(`/recruiter/applications/${row.applicationId}/interviews/${row.id}/feedback`, { feedback: text });
      await load();
    } catch (requestError) {
      setError(requestError.response?.data?.detail || 'Unable to save feedback');
    }
  };
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/recruiter">Dashboard</Link><Link className="rounded border px-3 py-1" to="/recruiter/offers">Offer management</Link></nav>
    <h1 className="text-2xl font-bold">Interview Management</h1>
    {error && <p className="mt-3 text-red-700">{error}</p>}
    {rows.length === 0 && <p className="mt-4 text-slate-500">No interviews scheduled.</p>}
    <div className="mt-4 space-y-3">{rows.map(row => <article key={row.id} className="rounded border p-4">
      <p className="font-semibold">{new Date(row.scheduledAt).toLocaleString()}</p>
      <p className="text-sm text-slate-600">{row.mode} · {row.locationOrLink || 'No meeting details'} · {row.status}</p>
      {row.status !== 'CANCELLED' && row.status !== 'COMPLETED' && <div className="mt-3 flex gap-2">
        <button className="rounded border px-3 py-1" onClick={() => reschedule(row)}>Reschedule</button>
        <button className="rounded border px-3 py-1 text-red-700" onClick={() => cancel(row)}>Cancel</button>
        <button className="rounded border px-3 py-1" onClick={() => feedback(row)}>Submit feedback</button>
      </div>}
      {row.feedback && <p className="mt-2 text-sm text-slate-600">Feedback: {row.feedback}</p>}
    </article>)}</div>
  </section></main>;
}
