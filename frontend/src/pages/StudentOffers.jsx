import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { DataState, MutationFeedback } from '../components/DashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';

export default function StudentOffers() {
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
      const { data } = await api.get('/student/offers');
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
  const respond = async (offerId, status) => {
    setError('');
    const result = await runMutation(`offer:${offerId}:respond`,
      () => api.patch(`/student/offers/${offerId}/status`, { status }),
      `Offer ${status.toLowerCase()}.`, 'Unable to respond to offer.');
    if (result.ok && result.mounted) await load();
  };
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/student">Dashboard</Link><Link className="rounded border px-3 py-1" to="/student/interviews">Interview details</Link></nav>
    <h1 className="text-2xl font-bold">My Offers</h1>
    <MutationFeedback feedback={feedback} />
    <DataState loading={loading} error={error} onRetry={load} isEmpty={offers.length === 0}
      loadingMessage="Loading offers…" emptyMessage="No offers received." />
    <div className="mt-4 space-y-3">{offers.map(offer => <article key={offer.id} className="rounded border p-4">
      <p className="font-semibold">{offer.jobTitle}</p>
      <p>{offer.packageDetails || 'Package details unavailable'} · {offer.packageAmount ?? 'Amount not specified'}</p>
      <p className="text-sm text-slate-600">Joining: {offer.joiningDate ? new Date(offer.joiningDate).toLocaleDateString() : 'Not specified'} · {offer.status}</p>
      {offer.status === 'ISSUED' && <div className="mt-3 flex gap-2">
        <button aria-live="polite" disabled={hasPending} className="rounded bg-green-600 px-3 py-1 text-white disabled:opacity-50" onClick={() => respond(offer.id, 'ACCEPTED')}>
          {isPending(`offer:${offer.id}:respond`) ? 'Saving…' : 'Accept'}
        </button>
        <button aria-live="polite" disabled={hasPending} className="rounded bg-red-600 px-3 py-1 text-white disabled:opacity-50" onClick={() => respond(offer.id, 'DECLINED')}>
          {isPending(`offer:${offer.id}:respond`) ? 'Saving…' : 'Decline'}
        </button>
      </div>}
    </article>)}</div>
  </section></main>;
}
