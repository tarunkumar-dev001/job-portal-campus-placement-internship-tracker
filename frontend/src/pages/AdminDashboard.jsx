import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

export default function AdminDashboard() {
  const { logout, user } = useAuth();
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [companies, setCompanies] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [message, setMessage] = useState('');
  const [college, setCollege] = useState({ code: '', name: '', city: '', state: '', university: '' });

  const load = async () => {
    try {
      const [statsResponse, usersResponse, collegesResponse, companiesResponse, auditResponse] = await Promise.all([
        api.get('/admin/stats'), api.get('/admin/users'), api.get('/admin/colleges'),
        api.get('/admin/companies'), api.get('/admin/audit-logs'),
      ]);
      setStats(statsResponse.data); setUsers(usersResponse.data);
      setColleges(collegesResponse.data); setCompanies(companiesResponse.data);
      setAuditLogs(auditResponse.data);
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to load administration data');
    }
  };

  useEffect(() => { load(); }, []);

  const setStatus = async (id, enabled) => {
    await api.patch(`/admin/users/${id}/status`, { enabled });
    await load();
  };

  const createCollege = async (event) => {
    event.preventDefault();
    try {
      await api.post('/admin/colleges', { ...college, active: true });
      setCollege({ code: '', name: '', city: '', state: '', university: '' });
      setMessage('College created');
      await load();
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to create college');
    }
  };

  const verifyCompany = async (id, verified) => {
    await api.patch(`/admin/companies/${id}/verification`, { verified });
    await load();
  };

  return (
    <main className="min-h-screen bg-slate-50 p-6">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <div><h1 className="text-3xl font-bold">Administrator Dashboard</h1><p className="text-slate-500">{user?.email}</p></div>
        <button onClick={logout} className="rounded border px-4 py-2 text-sm font-semibold">Logout</button>
      </header>
      {message && <p className="mx-auto mt-4 max-w-6xl rounded bg-indigo-50 p-3 text-indigo-700">{message}</p>}
      <section className="mx-auto mt-6 grid max-w-6xl gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          ['Users', stats?.users], ['Students', stats?.students], ['Recruiters', stats?.recruiters],
          ['Placement officers', stats?.placementOfficers], ['Colleges', stats?.colleges],
          ['Companies', stats?.companies], ['Drives', stats?.drives], ['Selections', stats?.selections],
        ].map(([label, value]) => <article key={label} className="rounded-xl bg-white p-5 shadow-sm">
          <p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-3xl font-bold text-indigo-700">{value ?? '-'}</p>
        </article>)}
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Create college</h2>
        <form onSubmit={createCollege} className="mt-4 grid gap-3 md:grid-cols-2">
          {Object.keys(college).map(key => <input key={key} required={key === 'code' || key === 'name'}
            className="rounded border p-2" placeholder={key[0].toUpperCase() + key.slice(1)}
            value={college[key]} onChange={(event) => setCollege({...college, [key]: event.target.value})} />)}
          <button className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white md:col-span-2">Create college</button>
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white shadow-sm">
        <h2 className="p-5 text-xl font-semibold">Users</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left"><thead className="bg-slate-100"><tr><th className="p-3">Email</th><th className="p-3">Role</th><th className="p-3">Status</th></tr></thead>
            <tbody>{users.map(item => <tr key={item.id} className="border-t"><td className="p-3">{item.email}</td><td className="p-3">{item.role}</td>
              <td className="p-3"><button onClick={() => setStatus(item.id, !item.enabled)} className={`rounded px-3 py-1 text-xs font-semibold ${item.enabled ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                {item.enabled ? 'Deactivate' : 'Activate'}
              </button></td></tr>)}</tbody>
          </table>
        </div>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-5 shadow-sm">
        <h2 className="text-xl font-semibold">Companies</h2>
        {companies.length === 0 && <p className="mt-3 text-slate-500">No companies have been registered.</p>}
        {companies.map(company => <div key={company.id} className="mt-3 flex items-center justify-between border-t pt-3">
          <span>{company.name}</span>
          <button onClick={() => verifyCompany(company.id, !company.verified)} className="rounded bg-slate-800 px-3 py-1 text-sm text-white">
            {company.verified ? 'Unverify' : 'Verify'}
          </button>
        </div>)}
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-5 shadow-sm">
        <h2 className="text-xl font-semibold">Audit log</h2>
        {auditLogs.length === 0 && <p className="mt-3 text-slate-500">No audit events recorded.</p>}
        <div className="mt-3 space-y-2">{auditLogs.map((entry, index) => <article key={entry.id || index} className="border-t pt-2">
          <p className="font-medium">{entry.action}</p>
          <p className="text-sm text-slate-500">{entry.actorEmail} · {entry.targetType} {entry.targetId} · {entry.createdAt}</p>
        </article>)}</div>
      </section>
      <p className="mx-auto mt-6 max-w-6xl text-sm text-slate-500">{colleges.length} colleges configured.</p>
    </main>
  );
}
