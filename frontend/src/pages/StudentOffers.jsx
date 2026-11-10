import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';

export default function StudentOffers() {
  const [offers, setOffers] = useState([]);
  const [error, setError] = useState('');
  const load = async () => {
    try { setOffers((await api.get('/student/offers')).data || []); }
    catch (requestError) { setError(requestError.response?.data?.detail || 'Unable to load offers'); }
  };
  useEffect(() => { load(); }, []);
  const respond = async (offerId, status) => {
    try { await api.patch(`/student/offers/${offerId}/status`, { status }); await load(); }
    catch (requestError) { setError(requestError.response?.data?.detail || 'Unable to respond to offer'); }
  };
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/student">Dashboard</Link><Link className="rounded border px-3 py-1" to="/student/interviews">Interview details</Link></nav>
    <h1 className="text-2xl font-bold">My Offers</h1>
    {error && <p className="mt-3 text-red-700">{error}</p>}
    {offers.length === 0 && <p className="mt-4 text-slate-500">No offers received.</p>}
    <div className="mt-4 space-y-3">{offers.map(offer => <article key={offer.id} className="rounded border p-4">
      <p className="font-semibold">{offer.jobTitle}</p>
      <p>{offer.packageDetails || 'Package details unavailable'} · {offer.packageAmount ?? 'Amount not specified'}</p>
      <p className="text-sm text-slate-600">Joining: {offer.joiningDate ? new Date(offer.joiningDate).toLocaleDateString() : 'Not specified'} · {offer.status}</p>
      {offer.status === 'ISSUED' && <div className="mt-3 flex gap-2">
        <button className="rounded bg-green-600 px-3 py-1 text-white" onClick={() => respond(offer.id, 'ACCEPTED')}>Accept</button>
        <button className="rounded bg-red-600 px-3 py-1 text-white" onClick={() => respond(offer.id, 'DECLINED')}>Decline</button>
      </div>}
    </article>)}</div>
  </section></main>;
}
