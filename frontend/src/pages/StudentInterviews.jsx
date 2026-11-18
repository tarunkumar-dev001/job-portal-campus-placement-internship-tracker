import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { DataState } from '../components/DashboardAnalytics';

export default function StudentInterviews() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const requestId = useRef(0);
  const load = async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      const response = await api.get('/student/interviews');
      if (currentRequest === requestId.current) setRows(response.data || []);
    } catch (requestError) {
      if (currentRequest === requestId.current) {
        setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to load interviews');
      }
    } finally {
      if (currentRequest === requestId.current) setLoading(false);
    }
  };
  useEffect(() => {
    load();
  }, []);
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/student">Dashboard</Link><Link className="rounded border px-3 py-1" to="/student/offers">My offers</Link></nav>
    <h1 className="text-2xl font-bold">My Interviews</h1>
    <DataState loading={loading} error={error} onRetry={load} isEmpty={rows.length === 0}
      loadingMessage="Loading interviews…" emptyMessage="No interviews scheduled." />
    <div className="mt-4 space-y-3">{rows.map(row => <article key={row.id} className="rounded border p-4">
      <p className="font-semibold">{new Date(row.scheduledAt).toLocaleString()}</p>
      <p>{row.mode} · {row.locationOrLink || 'Meeting details pending'}</p>
      <p className="text-sm text-slate-600">{row.status || 'SCHEDULED'} · {row.notes || 'No notes'}</p>
      {row.feedback && <p className="mt-2 text-sm">Feedback: {row.feedback}</p>}
    </article>)}</div>
  </section></main>;
}
