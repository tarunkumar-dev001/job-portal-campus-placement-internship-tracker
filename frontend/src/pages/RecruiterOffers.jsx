import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';

export default function RecruiterOffers() {
  const [offers, setOffers] = useState([]);
  const [error, setError] = useState('');
  const load = async () => {
    try { setOffers((await api.get('/recruiter/offers')).data || []); }
    catch (requestError) { setError(requestError.response?.data?.detail || 'Unable to load offers'); }
  };
  useEffect(() => { load(); }, []);
  const withdraw = async (offerId) => {
    await api.patch(`/recruiter/offers/${offerId}/status`, { status: 'WITHDRAWN' });
    await load();
  };
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/recruiter">Dashboard</Link><Link className="rounded border px-3 py-1" to="/recruiter/interviews">Interview management</Link></nav>
    <h1 className="text-2xl font-bold">Offer Management</h1>
    {error && <p className="mt-3 text-red-700">{error}</p>}
    {offers.length === 0 && <p className="mt-4 text-slate-500">No offers issued.</p>}
    <div className="mt-4 space-y-3">{offers.map(offer => <article key={offer.id} className="rounded border p-4">
      <p className="font-semibold">Application {offer.applicationId} · {offer.status}</p>
      <p className="text-sm">{offer.packageDetails || 'Package details unavailable'} · {offer.packageAmount ?? 'Amount not specified'}</p>
      {offer.status === 'ISSUED' && <button onClick={() => withdraw(offer.id)} className="mt-3 rounded border px-3 py-1 text-red-700">Withdraw</button>}
    </article>)}</div>
  </section></main>;
}
