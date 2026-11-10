import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

const statuses = ['APPLIED', 'SHORTLISTED', 'INTERVIEW_SCHEDULED', 'REJECTED'];
const emptyJob = { title: '', description: '', companyName: '', minCgpa: '', maxBacklogs: '', deadline: '', location: '', jobType: '', requiredSkills: '' };
const emptyCompany = { name: '', industry: '', website: '', location: '', description: '' };

export default function RecruiterDashboard() {
  const { logout, user } = useAuth();
  const [job, setJob] = useState(emptyJob);
  const [applicants, setApplicants] = useState([]);
  const [message, setMessage] = useState('');
  const [jobs, setJobs] = useState([]);
  const [company, setCompany] = useState(emptyCompany);
  const [selectedJob, setSelectedJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadData = async () => {
    setLoading(true);
    setError('');
    try {
      const [companyResult, jobsResult] = await Promise.all([
        api.get('/recruiter/company').catch(() => null),
        api.get('/recruiter/jobs'),
      ]);
      if (companyResult) setCompany(companyResult.data);
      setJobs(jobsResult.data || []);
    } catch (loadError) {
      setError(loadError.response?.data?.detail || 'Unable to load recruiter data');
    } finally {
      setLoading(false);
    }
  };
  const createOffer = async (applicationId) => {
    const packageDetails = window.prompt('Offer package details', '12 LPA');
    if (!packageDetails) return;
    const joiningDate = window.prompt('Joining date (YYYY-MM-DD)', new Date().toISOString().slice(0, 10));
    if (!joiningDate) return;
    try {
      await api.post(`/recruiter/applications/${applicationId}/offers`, {
        packageDetails,
        joiningDate: new Date(`${joiningDate}T00:00:00Z`).toISOString(),
      });
      setMessage('Offer issued');
      await loadApplicants(selectedJob);
    } catch (offerError) { setError(offerError.response?.data?.detail || 'Unable to issue offer'); }
  };

  useEffect(() => { loadData(); }, []);

  const saveCompany = async (event) => {
    event.preventDefault();
    try {
      const { data } = await api.post('/recruiter/company', company);
      setCompany(data);
      setMessage(data.verified ? 'Company profile saved' : 'Company saved and awaiting admin verification');
    } catch (saveError) { setError(saveError.response?.data?.detail || 'Unable to save company'); }
  };

  const loadApplicants = async (jobId) => {
    setSelectedJob(jobId);
    try {
      const { data } = await api.get(`/recruiter/jobs/${jobId}/applications`);
      setApplicants(data || []);
    } catch (loadError) { setApplicants([]); setError(loadError.response?.data?.detail || 'Unable to load applicants'); }
  };

  const createJob = async (event) => {
    event.preventDefault();
    try {
      await api.post('/recruiter/jobs', {
        ...job,
        minCgpa: Number(job.minCgpa),
        maxBacklogs: Number(job.maxBacklogs),
      });
      setJob(emptyJob);
      setMessage('Job drive posted successfully');
      await loadData();
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to post job');
    }
  };

  const updateStatus = async (applicationId, status) => {
    try {
      await api.patch(`/recruiter/applications/${applicationId}/status`, { status });
      await loadApplicants(selectedJob);
    } catch (statusError) { setError(statusError.response?.data?.detail || 'Unable to update status'); }
  };

  const change = (event) => setJob({ ...job, [event.target.name]: event.target.value });
  const lifecycle = async (jobId, action) => {
    try {
      await api.post(`/recruiter/jobs/${jobId}/${action}`);
      await loadData();
      setMessage(action === 'publish' ? 'Job published' : 'Job closed');
    } catch (actionError) { setError(actionError.response?.data?.detail || `Unable to ${action} job`); }
  };
  const scheduleInterview = async (applicationId) => {
    const scheduledAt = window.prompt('Interview date and time (ISO, e.g. 2026-12-01T10:00:00Z)');
    if (!scheduledAt) return;
    const mode = window.prompt('Interview mode', 'ONLINE');
    const locationOrLink = window.prompt('Meeting link or location', '');
    try {
      await api.post(`/recruiter/applications/${applicationId}/interview`, { scheduledAt, mode, locationOrLink });
      await loadApplicants(selectedJob);
      setMessage('Interview scheduled');
    } catch (scheduleError) { setError(scheduleError.response?.data?.detail || 'Unable to schedule interview'); }
  };
  const feedback = async (applicationId, interviewId) => {
    const text = window.prompt('Interview feedback');
    if (!text) return;
    try {
      await api.post(`/recruiter/applications/${applicationId}/interviews/${interviewId}/feedback`, { feedback: text });
      setMessage('Feedback saved');
    } catch (feedbackError) { setError(feedbackError.response?.data?.detail || 'Unable to save feedback'); }
  };

  return (
    <main className="min-h-screen bg-slate-50 p-6">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <div><h1 className="text-3xl font-bold">Recruiter Dashboard</h1><p className="text-slate-500">{user?.email}</p></div>
        <button onClick={logout} className="rounded-lg border px-4 py-2 text-sm font-semibold">Logout</button>
      </header>
      <nav className="mx-auto mt-5 flex max-w-6xl flex-wrap gap-2">
        <Link to="/recruiter/interviews" className="rounded-lg border bg-white px-4 py-2 text-sm font-semibold">Interview management</Link>
        <Link to="/recruiter/offers" className="rounded-lg border bg-white px-4 py-2 text-sm font-semibold">Offer management</Link>
      </nav>
      {message && <p className="mx-auto mt-4 max-w-6xl rounded bg-indigo-50 p-3 text-indigo-700">{message}</p>}
      {error && <p className="mx-auto mt-4 max-w-6xl rounded bg-red-50 p-3 text-red-700">{error}</p>}
      {loading ? <p className="mx-auto mt-8 max-w-6xl text-slate-500">Loading...</p> : <>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Company profile</h2>
        <form onSubmit={saveCompany} className="mt-4 grid gap-3 md:grid-cols-2">
          {['name','industry','website','location'].map((field) => <input key={field} className="rounded border p-2" name={field} placeholder={field} required={field === 'name'} value={company[field] || ''} onChange={(e) => setCompany({...company, [field]: e.target.value})} />)}
          <textarea className="rounded border p-2 md:col-span-2" placeholder="Description" value={company.description || ''} onChange={(e) => setCompany({...company, description: e.target.value})} />
          <button className="rounded bg-slate-800 px-4 py-2 text-white">Save company</button>
          {company.verified === false && <span className="self-center text-sm text-amber-700">Verification required before publishing jobs.</span>}
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Post a job drive</h2>
        <form onSubmit={createJob} className="mt-4 grid gap-4 md:grid-cols-2">
          <input className="rounded border p-2" name="title" placeholder="Job title" required value={job.title} onChange={change} />
          <input className="rounded border p-2" name="companyName" placeholder="Company" required value={job.companyName} onChange={change} />
          <textarea className="rounded border p-2 md:col-span-2" name="description" placeholder="Job description" required value={job.description} onChange={change} />
          <input className="rounded border p-2" name="minCgpa" type="number" step="0.01" placeholder="Minimum CGPA" required value={job.minCgpa} onChange={change} />
          <input className="rounded border p-2" name="maxBacklogs" type="number" min="0" placeholder="Maximum backlogs" required value={job.maxBacklogs} onChange={change} />
          <input className="rounded border p-2" name="deadline" type="date" required value={job.deadline} onChange={change} />
          <input className="rounded border p-2" name="location" placeholder="Location" value={job.location} onChange={change} />
          <input className="rounded border p-2" name="jobType" placeholder="Job type" value={job.jobType} onChange={change} />
          <input className="rounded border p-2 md:col-span-2" name="requiredSkills" placeholder="Required skills" value={job.requiredSkills} onChange={change} />
          <button className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white">Post job drive</button>
        </form>
      </section>
      <section className="mx-auto mt-8 max-w-6xl rounded-xl bg-white p-5 shadow-sm">
        <h2 className="text-xl font-semibold">My postings</h2>
        {jobs.length === 0 ? <p className="mt-3 text-slate-500">No jobs posted yet.</p> : <div className="mt-4 grid gap-3 md:grid-cols-2">
          {jobs.map((item) => <div key={item.id} className={`rounded border p-4 ${selectedJob === item.id ? 'border-indigo-500' : ''}`}>
            <button onClick={() => loadApplicants(item.id)} className="w-full text-left">
            <strong>{item.title}</strong><div className="text-sm text-slate-500">{item.location || 'Location not specified'} · {item.deadline}</div>
            </button>
            <div className="mt-3 flex gap-2">
              <button onClick={() => lifecycle(item.id, 'publish')} className="rounded bg-green-600 px-3 py-1 text-sm text-white">Publish</button>
              <button onClick={() => lifecycle(item.id, 'close')} className="rounded border px-3 py-1 text-sm">Close</button>
            </div>
          </div>)}
        </div>}
      </section>
      <section className="mx-auto mt-8 max-w-6xl overflow-hidden rounded-xl bg-white shadow-sm">
        <h2 className="p-5 text-xl font-semibold">Applicants</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead className="bg-slate-100 text-sm"><tr><th className="p-3">Student</th><th className="p-3">Job</th><th className="p-3">Status</th></tr></thead>
            <tbody>{applicants.map((application) => (
              <tr key={application.applicationId} className="border-t">
                <td className="p-3">{application.student?.email}</td>
                <td className="p-3">{application.studentName || application.email}</td>
                <td className="p-3">
                  <select className="rounded border p-2" value={application.status}
                    onChange={(event) => updateStatus(application.applicationId, event.target.value)}>
                    {statuses.map((status) => <option key={status}>{status}</option>)}
                  </select>
                  <button onClick={() => scheduleInterview(application.applicationId)} className="ml-2 rounded border px-3 py-2 text-sm">Schedule interview</button>
                  {application.status === 'INTERVIEW_SCHEDULED' && <button onClick={() => createOffer(application.applicationId)} className="ml-2 rounded border px-3 py-2 text-sm">Issue offer</button>}
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      </section>
      </>}
    </main>
  );
}
