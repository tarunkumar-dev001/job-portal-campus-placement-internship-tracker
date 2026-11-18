import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import useDashboardAnalytics from '../hooks/useDashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';
import {
  AnalyticsPanel, CountBreakdown, DataState, MetricGrid, MutationFeedback, PlacementRate, StatusDistribution,
} from '../components/DashboardAnalytics';

const initialSectionStates = {
  users: { loading: true, error: '' },
  colleges: { loading: true, error: '' },
  companies: { loading: true, error: '' },
  auditLogs: { loading: true, error: '' },
};

export default function AdminDashboard() {
  const { logout, user } = useAuth();
  const fetchStats = useCallback((signal) => api.get('/admin/stats', { signal }), []);
  const { stats, loading: analyticsLoading, error: analyticsError, refresh: refreshAnalytics } =
    useDashboardAnalytics(fetchStats, 'Unable to load admin analytics. Please try again.');
  const { feedback, isPending, runMutation } = useMutationFeedback();
  const [users, setUsers] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [companies, setCompanies] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [college, setCollege] = useState({ code: '', name: '', city: '', state: '', university: '' });
  const [collegeFieldErrors, setCollegeFieldErrors] = useState({});
  const [sectionStates, setSectionStates] = useState(initialSectionStates);
  const sectionRequestIds = useRef({});
  const componentMounted = useRef(false);

  const loadSection = async (key, request, applyData, failureMessage) => {
    const requestId = (sectionRequestIds.current[key] || 0) + 1;
    sectionRequestIds.current[key] = requestId;
    setSectionStates(current => ({ ...current, [key]: { loading: true, error: '' } }));
    try {
      const { data } = await request();
      if (componentMounted.current && sectionRequestIds.current[key] === requestId) {
        applyData(data || []);
        setSectionStates(current => ({ ...current, [key]: { loading: false, error: '' } }));
      }
    } catch (requestError) {
      if (componentMounted.current && sectionRequestIds.current[key] === requestId) {
        setSectionStates(current => ({
          ...current,
          [key]: {
            loading: false,
            error: requestError.response?.data?.detail
              || requestError.response?.data?.message || failureMessage,
          },
        }));
      }
    }
  };

  const loadUsers = () => loadSection('users', () => api.get('/admin/users'),
    setUsers, 'Unable to load users.');
  const loadColleges = () => loadSection('colleges', () => api.get('/admin/colleges'),
    setColleges, 'Unable to load colleges.');
  const loadCompanies = () => loadSection('companies', () => api.get('/admin/companies'),
    setCompanies, 'Unable to load companies.');
  const loadAuditLogs = () => loadSection('auditLogs', () => api.get('/admin/audit-logs'),
    setAuditLogs, 'Unable to load audit events.');
  const load = () => Promise.all([loadUsers(), loadColleges(), loadCompanies(), loadAuditLogs()]);

  useEffect(() => {
    componentMounted.current = true;
    load();
    return () => {
      componentMounted.current = false;
      Object.keys(sectionRequestIds.current).forEach(key => {
        sectionRequestIds.current[key] += 1;
      });
    };
  }, []);

  const setStatus = async (id, enabled) => {
    const result = await runMutation(`user:${id}:status`,
      () => api.patch(`/admin/users/${id}/status`, { enabled }),
      enabled ? 'User account activated.' : 'User account deactivated.',
      'Unable to update user status.');
    if (result.ok && result.mounted) await loadUsers();
  };

  const createCollege = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setCollegeFieldErrors)) return;
    const result = await runMutation('college:create',
      () => api.post('/admin/colleges', { ...college, active: true }),
      'College created.', 'Unable to create college.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setCollegeFieldErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    setCollege({ code: '', name: '', city: '', state: '', university: '' });
    await Promise.all([load(), refreshAnalytics()]);
  };

  const verifyCompany = async (id, verified) => {
    const result = await runMutation(`company:${id}:verification`,
      () => api.patch(`/admin/companies/${id}/verification`, { verified }),
      verified ? 'Company verified.' : 'Company verification removed.',
      'Unable to update company verification.');
    if (result.ok && result.mounted) await loadCompanies();
  };

  return (
    <main className="min-h-screen bg-slate-50 p-6">
      <header className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-3xl font-bold">Administrator Dashboard</h1>
          <p className="break-words text-slate-500">{user?.email}</p>
        </div>
        <button onClick={logout} className="min-h-11 shrink-0 rounded border px-4 py-2 text-sm font-semibold">Logout</button>
      </header>
      <MutationFeedback feedback={feedback} />
      <AnalyticsPanel loading={analyticsLoading} error={analyticsError} onRefresh={refreshAnalytics} hasData={stats !== null}>
        {stats && <div className="space-y-4">
          <section aria-labelledby="admin-platform-overview">
            <h3 id="admin-platform-overview" className="mb-3 font-semibold text-slate-900">Platform overview</h3>
            <MetricGrid columns="xl:grid-cols-6" metrics={[
              { label: 'Users', value: stats.users },
              { label: 'Students', value: stats.students },
              { label: 'Recruiters', value: stats.recruiters },
              { label: 'Placement officers', value: stats.placementOfficers },
              { label: 'Colleges', value: stats.colleges },
              { label: 'Companies', value: stats.companies },
              { label: 'Drives', value: stats.drives },
              { label: 'Selections', value: stats.selections },
              { label: 'Active jobs', value: stats.activeJobs },
              { label: 'Active internships', value: stats.activeInternships },
              { label: 'Applications', value: stats.totalApplications },
            ]} />
          </section>
          <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(16rem,1fr)]">
            <StatusDistribution title="Application status distribution" counts={stats.applicationsByStatus} />
            <PlacementRate percentage={stats.placementPercentage} placed={stats.uniqueStudentsPlaced} students={stats.students} />
          </div>
        </div>}
      </AnalyticsPanel>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Create college</h2>
        <DataState {...sectionStates.colleges} onRetry={loadColleges} isEmpty={false}
          loadingMessage="Loading college data…" />
        <form noValidate aria-busy={isPending('college:create')} onSubmit={createCollege} className="mt-4 grid gap-3 md:grid-cols-2">
          <FormErrorSummary errors={collegeFieldErrors} />
          {Object.keys(college).map(key => <FormField key={key} id={`college-${key}`}
            label={key[0].toUpperCase() + key.slice(1)} required={key === 'code' || key === 'name'}
            error={collegeFieldErrors[key]}>
            <input name={key} className="w-full rounded border p-2" value={college[key]}
              onChange={(event) => {
                setCollege({...college, [key]: event.target.value});
                clearFieldError(setCollegeFieldErrors, key);
              }} />
          </FormField>)}
          <button aria-live="polite" disabled={isPending('college:create')}
            className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white disabled:cursor-wait disabled:opacity-60 md:col-span-2">
            {isPending('college:create') ? 'Creating college…' : 'Create college'}
          </button>
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white shadow-sm">
        <h2 className="p-5 text-xl font-semibold">Users</h2>
        <div className="px-5">
          <DataState {...sectionStates.users} onRetry={loadUsers} isEmpty={users.length === 0}
            loadingMessage="Loading users…" emptyMessage="No users are available." />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left"><thead className="bg-slate-100"><tr><th className="p-3">Email</th><th className="p-3">Role</th><th className="p-3">Status</th></tr></thead>
            <tbody>{users.map(item => <tr key={item.id} className="border-t"><td className="p-3">{item.email}</td><td className="p-3">{item.role}</td>
              <td className="p-3"><button aria-live="polite" disabled={isPending(`user:${item.id}:status`)}
                onClick={() => setStatus(item.id, !item.enabled)} className={`rounded px-3 py-1 text-xs font-semibold disabled:cursor-wait disabled:opacity-60 ${item.enabled ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                {isPending(`user:${item.id}:status`) ? 'Saving…' : item.enabled ? 'Deactivate' : 'Activate'}
              </button></td></tr>)}</tbody>
          </table>
        </div>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-5 shadow-sm">
        <h2 className="text-xl font-semibold">Companies</h2>
        <DataState {...sectionStates.companies} onRetry={loadCompanies} isEmpty={companies.length === 0}
          loadingMessage="Loading companies…" emptyMessage="No companies have been registered." />
        {companies.map(company => <div key={company.id} className="mt-3 flex items-center justify-between border-t pt-3">
          <span>{company.name}</span>
          <button aria-live="polite" disabled={isPending(`company:${company.id}:verification`)}
            onClick={() => verifyCompany(company.id, !company.verified)}
            className="rounded bg-slate-800 px-3 py-1 text-sm text-white disabled:cursor-wait disabled:opacity-60">
            {isPending(`company:${company.id}:verification`) ? 'Saving…' : company.verified ? 'Unverify' : 'Verify'}
          </button>
        </div>)}
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-5 shadow-sm">
        <h2 className="text-xl font-semibold">Audit log</h2>
        <DataState {...sectionStates.auditLogs} onRetry={loadAuditLogs} isEmpty={auditLogs.length === 0}
          loadingMessage="Loading audit events…" emptyMessage="No audit events recorded." />
        <div className="mt-3 space-y-2">{auditLogs.map((entry, index) => <article key={entry.id || index} className="border-t pt-2">
          <p className="font-medium">{entry.action}</p>
          <p className="text-sm text-slate-500">{entry.actorEmail} · {entry.targetType} {entry.targetId} · {entry.createdAt}</p>
        </article>)}</div>
      </section>
      {!sectionStates.colleges.loading && !sectionStates.colleges.error
        && <p className="mx-auto mt-6 max-w-6xl text-sm text-slate-500">{colleges.length} colleges configured.</p>}
    </main>
  );
}
