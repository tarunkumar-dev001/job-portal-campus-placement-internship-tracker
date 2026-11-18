import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { DataState, MutationFeedback } from '../components/DashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';

export default function RecruiterOffers() {
  const [offers, setOffers] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const { feedback, hasPending, isPending, runMutation } = useMutationFeedback();
  const requestId = useRef(0);
  const mounted = useRef(false);
  const load = async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      const { data } = await api.get('/recruiter/offers');
      if (mounted.current && currentRequest === requestId.current) setOffers(data || []);
    } catch (requestError) {
      if (mounted.current && currentRequest === requestId.current) {
        setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to load offers');
      }
    } finally {
      if (mounted.current && currentRequest === requestId.current) setLoading(false);
    }
  };
  useEffect(() => {
    mounted.current = true;
    load();
    return () => {
      mounted.current = false;
      requestId.current += 1;
    };
  }, []);
  const withdraw = async (offerId) => {
    setError('');
    const result = await runMutation(`offer:${offerId}:withdraw`,
      () => api.patch(`/recruiter/offers/${offerId}/status`, { status: 'WITHDRAWN' }),
      'Offer withdrawn.', 'Unable to withdraw offer.');
    if (result.ok && result.mounted) await load();
  };
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/recruiter">Dashboard</Link><Link className="rounded border px-3 py-1" to="/recruiter/interviews">Interview management</Link></nav>
    <h1 className="text-2xl font-bold">Offer Management</h1>
    <MutationFeedback feedback={feedback} />
    <DataState loading={loading} error={error} onRetry={load} isEmpty={offers.length === 0}
      loadingMessage="Loading offers…" emptyMessage="No offers issued." />
    <div className="mt-4 space-y-3">{offers.map(offer => <article key={offer.id} className="rounded border p-4">
      <p className="font-semibold">Application {offer.applicationId} · {offer.status}</p>
      <p className="text-sm">{offer.packageDetails || 'Package details unavailable'} · {offer.packageAmount ?? 'Amount not specified'}</p>
      {offer.status === 'ISSUED' && <button aria-live="polite" disabled={hasPending}
        onClick={() => withdraw(offer.id)} className="mt-3 rounded border px-3 py-1 text-red-700 disabled:opacity-50">
        {isPending(`offer:${offer.id}:withdraw`) ? 'Withdrawing…' : 'Withdraw'}
      </button>}
    </article>)}</div>
  </section></main>;
}
