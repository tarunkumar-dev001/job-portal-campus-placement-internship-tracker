import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import useDashboardAnalytics from '../hooks/useDashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';
import {
  AnalyticsPanel, DataState, MetricGrid, MutationFeedback, RecruiterPipeline, StatusDistribution,
} from '../components/DashboardAnalytics';

const statuses = ['APPLIED', 'SHORTLISTED', 'INTERVIEW_SCHEDULED', 'REJECTED'];
const emptyJob = { title: '', description: '', companyName: '', minCgpa: '', maxBacklogs: '', deadline: '', location: '', jobType: '', requiredSkills: '' };
const emptyCompany = { name: '', industry: '', website: '', location: '', description: '' };

export default function RecruiterDashboard() {
  const { logout, user } = useAuth();
  const fetchStats = useCallback((signal) => api.get('/recruiter/stats', { signal }), []);
  const { stats, loading: analyticsLoading, error: analyticsError, refresh: refreshAnalytics } =
    useDashboardAnalytics(fetchStats, 'Unable to load recruiter analytics. Please try again.');
  const { feedback: mutationFeedback, isPending, runMutation } = useMutationFeedback();
  const [job, setJob] = useState(emptyJob);
  const [applicants, setApplicants] = useState([]);
  const [pendingRejections, setPendingRejections] = useState({});
  const [jobs, setJobs] = useState([]);
  const [company, setCompany] = useState(emptyCompany);
  const [selectedJob, setSelectedJob] = useState(null);
  const [companyFieldErrors, setCompanyFieldErrors] = useState({});
  const [jobFieldErrors, setJobFieldErrors] = useState({});
  const [rejectionFieldErrors, setRejectionFieldErrors] = useState({});
  const [applicationAction, setApplicationAction] = useState(null);
  const [applicationActionErrors, setApplicationActionErrors] = useState({});
  const [companyState, setCompanyState] = useState({ loading: true, error: '' });
  const [jobsState, setJobsState] = useState({ loading: true, error: '' });
  const [applicantsState, setApplicantsState] = useState({ loading: false, error: '' });
  const companyRequestId = useRef(0);
  const jobsRequestId = useRef(0);
  const applicantsRequestId = useRef(0);
  const selectedJobRef = useRef(null);
  const componentMounted = useRef(false);

  const loadCompany = async () => {
    const requestId = ++companyRequestId.current;
    setCompanyState({ loading: true, error: '' });
    try {
      const { data } = await api.get('/recruiter/company');
      if (componentMounted.current && requestId === companyRequestId.current) {
        setCompany(data || emptyCompany);
        setCompanyState({ loading: false, error: '' });
      }
    } catch (loadError) {
      if (componentMounted.current && requestId === companyRequestId.current) {
        setCompanyState({
          loading: false,
          error: loadError.response?.data?.detail
            || loadError.response?.data?.message || 'Unable to load company profile.',
        });
      }
    }
  };
  const loadJobs = async () => {
    const requestId = ++jobsRequestId.current;
    setJobsState({ loading: true, error: '' });
    try {
      const { data } = await api.get('/recruiter/jobs');
      if (componentMounted.current && requestId === jobsRequestId.current) {
        setJobs(data || []);
        setJobsState({ loading: false, error: '' });
      }
    } catch (loadError) {
      if (componentMounted.current && requestId === jobsRequestId.current) {
        setJobsState({
          loading: false,
          error: loadError.response?.data?.detail
            || loadError.response?.data?.message || 'Unable to load job postings.',
        });
      }
    }
  };
  const loadData = () => Promise.all([loadCompany(), loadJobs()]);
  useEffect(() => {
    componentMounted.current = true;
    loadData();
    return () => {
      componentMounted.current = false;
      companyRequestId.current += 1;
      jobsRequestId.current += 1;
      applicantsRequestId.current += 1;
    };
  }, []);

  const saveCompany = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setCompanyFieldErrors)) return;
    const result = await runMutation('company:save', () => api.post('/recruiter/company', company),
      response => response.data.verified
        ? 'Company profile saved.' : 'Company saved and awaiting admin verification.',
      'Unable to save company profile.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setCompanyFieldErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    setCompany(result.result.data);
  };

  const loadApplicants = async (jobId) => {
    selectedJobRef.current = jobId;
    setSelectedJob(jobId);
    setApplicants([]);
    const requestId = ++applicantsRequestId.current;
    setApplicantsState({ loading: true, error: '' });
    try {
      const { data } = await api.get(`/recruiter/jobs/${jobId}/applications`);
      if (componentMounted.current && requestId === applicantsRequestId.current) {
        setApplicants(data || []);
        setApplicantsState({ loading: false, error: '' });
      }
    } catch (loadError) {
      if (componentMounted.current && requestId === applicantsRequestId.current) {
        setApplicantsState({
          loading: false,
          error: loadError.response?.data?.detail
            || loadError.response?.data?.message || 'Unable to load applicants.',
        });
      }
    }
  };
  const refreshSelectedApplicants = () => {
    const jobId = selectedJobRef.current;
    if (componentMounted.current && jobId !== null) return loadApplicants(jobId);
  };

  const createJob = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setJobFieldErrors)) return;
    const result = await runMutation('job:create', () => api.post('/recruiter/jobs', {
        ...job,
        minCgpa: Number(job.minCgpa),
        maxBacklogs: Number(job.maxBacklogs),
      }), 'Job posting created.', 'Unable to post job.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setJobFieldErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    setJob(emptyJob);
    await loadData();
    await refreshAnalytics();
  };

  const updateStatus = async (applicationId, status, rejectionExplanation) => {
    const result = await runMutation(`application:${applicationId}:status`, async () => {
      const request = { status };
      if (rejectionExplanation !== undefined) request.rejectionExplanation = rejectionExplanation.trim();
      return api.patch(`/recruiter/applications/${applicationId}/status`, request);
    }, 'Application status updated.', 'Unable to update application status.');
    if (!result.ok && rejectionExplanation !== undefined) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) {
        setRejectionFieldErrors(current => ({ ...current, [applicationId]: serverErrors }));
      }
    }
    if (!result.ok || !result.mounted) return;
    setPendingRejections((current) => {
      const next = { ...current };
      delete next[applicationId];
      return next;
    });
    setRejectionFieldErrors(current => {
      const next = { ...current };
      delete next[applicationId];
      return next;
    });
    await refreshSelectedApplicants();
    await refreshAnalytics();
  };
  const clearRejectionError = (applicationId, field) => setRejectionFieldErrors(current => {
    const errors = { ...(current[applicationId] || {}) };
    delete errors[field];
    delete errors._form;
    return { ...current, [applicationId]: errors };
  });
  const clearRejectionErrors = (applicationId) => setRejectionFieldErrors(current => {
    const next = { ...current };
    delete next[applicationId];
    return next;
  });

  const change = (event) => {
    setJob({ ...job, [event.target.name]: event.target.value });
    clearFieldError(setJobFieldErrors, event.target.name);
  };
  const lifecycle = async (jobId, action) => {
    const result = await runMutation(`job:${jobId}:${action}`,
      () => api.post(`/recruiter/jobs/${jobId}/${action}`),
      action === 'publish' ? 'Job published.' : 'Job closed.',
      `Unable to ${action} job.`);
    if (!result.ok || !result.mounted) return;
    await loadData();
    await refreshAnalytics();
  };
  const openApplicationAction = (type, applicationId) => {
    setApplicationAction({
      type, applicationId, scheduledAt: '', mode: 'ONLINE', locationOrLink: '',
      packageDetails: '', joiningDate: new Date().toISOString().slice(0, 10),
    });
    setApplicationActionErrors({});
  };
  const changeApplicationAction = (field, value) => {
    setApplicationAction(current => ({ ...current, [field]: value }));
    clearFieldError(setApplicationActionErrors, field);
  };
  const submitApplicationAction = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setApplicationActionErrors)) return;
    const { type, applicationId } = applicationAction;
    const key = `application:${applicationId}:${type}`;
    const operations = {
      'schedule-interview': {
        request: () => api.post(`/recruiter/applications/${applicationId}/interview`, {
          scheduledAt: new Date(applicationAction.scheduledAt).toISOString(),
          mode: applicationAction.mode,
          locationOrLink: applicationAction.locationOrLink,
        }),
        success: 'Interview scheduled.',
        failure: 'Unable to schedule interview.',
      },
      offer: {
        request: () => api.post(`/recruiter/applications/${applicationId}/offers`, {
          packageDetails: applicationAction.packageDetails,
          joiningDate: new Date(`${applicationAction.joiningDate}T00:00:00Z`).toISOString(),
        }),
        success: 'Offer issued.',
        failure: 'Unable to issue offer.',
      },
    };
    const action = operations[type];
    const result = await runMutation(key, action.request, action.success, action.failure);
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setApplicationActionErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    setApplicationAction(null);
    await refreshSelectedApplicants();
    await refreshAnalytics();
  };
  const rejectionApplicants = applicants.filter(application =>
    pendingRejections[application.applicationId] !== undefined);
  const actionApplicant = applicationAction
    ? applicants.find(application => application.applicationId === applicationAction.applicationId)
    : null;

  return (
    <main className="min-h-screen bg-slate-50 p-6">
      <header className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-3xl font-bold">Recruiter Dashboard</h1>
          <p className="break-words text-slate-500">{user?.email}</p>
        </div>
        <button onClick={logout} className="min-h-11 shrink-0 rounded-lg border px-4 py-2 text-sm font-semibold">Logout</button>
      </header>
      <nav className="mx-auto mt-5 flex max-w-6xl flex-wrap gap-2">
        <Link to="/recruiter/interviews" className="rounded-lg border bg-white px-4 py-2 text-sm font-semibold">Interview management</Link>
        <Link to="/recruiter/offers" className="rounded-lg border bg-white px-4 py-2 text-sm font-semibold">Offer management</Link>
      </nav>
      <MutationFeedback feedback={mutationFeedback} />
      <AnalyticsPanel loading={analyticsLoading} error={analyticsError} onRefresh={refreshAnalytics} hasData={stats !== null}>
        {stats && <div className="space-y-4">
          <MetricGrid columns="xl:grid-cols-4" metrics={[
            { label: 'Jobs', value: stats.totalJobs },
            { label: 'Internships', value: stats.totalInternships },
            { label: 'Active postings', value: stats.activeJobPostings },
            { label: 'Applicants', value: stats.totalApplicants },
          ]} />
          <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(18rem,2fr)]">
            <StatusDistribution title="Application status distribution" counts={stats.applicationsByStatus} />
            <RecruiterPipeline stats={stats} />
          </div>
        </div>}
      </AnalyticsPanel>
      <>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Company profile</h2>
        <DataState {...companyState} onRetry={loadCompany} isEmpty={false}
          loadingMessage="Loading company profile…" />
        <form noValidate aria-busy={isPending('company:save')} onSubmit={saveCompany} className="mt-4 grid gap-3 md:grid-cols-2">
          <FormErrorSummary errors={companyFieldErrors} className="md:col-span-2" />
          {['name','industry','website','location'].map((field) => <FormField key={field} id={`company-${field}`}
            label={field[0].toUpperCase() + field.slice(1)} required={field === 'name'} error={companyFieldErrors[field]}>
            <input className="w-full rounded border p-2" name={field} type={field === 'website' ? 'url' : 'text'}
              autoComplete={field === 'name' ? 'organization' : undefined} value={company[field] || ''}
              onChange={(event) => {
                setCompany({...company, [field]: event.target.value});
                clearFieldError(setCompanyFieldErrors, field);
              }} />
          </FormField>)}
          <FormField id="company-description" label="Description" error={companyFieldErrors.description} className="md:col-span-2">
            <textarea name="description" rows={3} className="w-full rounded border p-2"
              value={company.description || ''} onChange={(event) => {
                setCompany({...company, description: event.target.value});
                clearFieldError(setCompanyFieldErrors, 'description');
              }} />
          </FormField>
          <button aria-live="polite" disabled={isPending('company:save')}
            className="rounded bg-slate-800 px-4 py-2 text-white disabled:cursor-wait disabled:opacity-60">
            {isPending('company:save') ? 'Saving company…' : 'Save company'}
          </button>
          {company.verified === false && <span className="self-center text-sm text-amber-700">Verification required before publishing jobs.</span>}
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Post a job drive</h2>
        <form noValidate aria-busy={isPending('job:create')} onSubmit={createJob} className="mt-4 grid gap-4 md:grid-cols-2">
          <FormErrorSummary errors={jobFieldErrors} className="md:col-span-2" />
          <FormField id="job-title" label="Job title" required error={jobFieldErrors.title}>
            <input className="w-full rounded border p-2" name="title" autoComplete="off" value={job.title} onChange={change} />
          </FormField>
          <FormField id="job-company" label="Company" required error={jobFieldErrors.companyName}>
            <input className="w-full rounded border p-2" name="companyName" autoComplete="organization" value={job.companyName} onChange={change} />
          </FormField>
          <FormField id="job-description" label="Job description" required error={jobFieldErrors.description} className="md:col-span-2">
            <textarea className="w-full rounded border p-2" name="description" rows={4} value={job.description} onChange={change} />
          </FormField>
          <FormField id="job-min-cgpa" label="Minimum CGPA" required error={jobFieldErrors.minCgpa}>
            <input className="w-full rounded border p-2" name="minCgpa" type="number" min="0" max="10" step="0.01" value={job.minCgpa} onChange={change} />
          </FormField>
          <FormField id="job-max-backlogs" label="Maximum backlogs" required error={jobFieldErrors.maxBacklogs}>
            <input className="w-full rounded border p-2" name="maxBacklogs" type="number" min="0" step="1" value={job.maxBacklogs} onChange={change} />
          </FormField>
          <FormField id="job-deadline" label="Application deadline" required error={jobFieldErrors.deadline}>
            <input className="w-full rounded border p-2" name="deadline" type="date" value={job.deadline} onChange={change} />
          </FormField>
          <FormField id="job-location" label="Location" error={jobFieldErrors.location}>
            <input className="w-full rounded border p-2" name="location" value={job.location} onChange={change} />
          </FormField>
          <FormField id="job-type" label="Job type" error={jobFieldErrors.jobType}>
            <input className="w-full rounded border p-2" name="jobType" value={job.jobType} onChange={change} />
          </FormField>
          <FormField id="job-required-skills" label="Required skills" error={jobFieldErrors.requiredSkills} className="md:col-span-2">
            <input className="w-full rounded border p-2" name="requiredSkills" value={job.requiredSkills} onChange={change} />
          </FormField>
          <button aria-live="polite" disabled={isPending('job:create')}
            className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white disabled:cursor-wait disabled:opacity-60">
            {isPending('job:create') ? 'Posting…' : 'Post job drive'}
          </button>
        </form>
      </section>
      <section className="mx-auto mt-8 max-w-6xl rounded-xl bg-white p-5 shadow-sm">
        <h2 className="text-xl font-semibold">My postings</h2>
        <DataState {...jobsState} onRetry={loadJobs} isEmpty={jobs.length === 0}
          loadingMessage="Loading job postings…" emptyMessage="No jobs posted yet." />
        {jobs.length > 0 && <div className="mt-4 grid gap-3 md:grid-cols-2">
          {jobs.map((item) => <div key={item.id} className={`rounded border p-4 ${selectedJob === item.id ? 'border-indigo-500' : ''}`}>
            <button onClick={() => loadApplicants(item.id)} className="w-full text-left">
            <strong>{item.title}</strong><div className="text-sm text-slate-500">{item.location || 'Location not specified'} · {item.deadline}</div>
            </button>
            <div className="mt-3 flex gap-2">
              <button aria-live="polite" disabled={isPending(`job:${item.id}:publish`)}
                onClick={() => lifecycle(item.id, 'publish')}
                className="rounded bg-green-600 px-3 py-1 text-sm text-white disabled:cursor-wait disabled:opacity-60">
                {isPending(`job:${item.id}:publish`) ? 'Publishing…' : 'Publish'}
              </button>
              <button aria-live="polite" disabled={isPending(`job:${item.id}:close`)}
                onClick={() => lifecycle(item.id, 'close')}
                className="rounded border px-3 py-1 text-sm disabled:cursor-wait disabled:opacity-60">
                {isPending(`job:${item.id}:close`) ? 'Closing…' : 'Close'}
              </button>
            </div>
          </div>)}
        </div>}
      </section>
      <section className="mx-auto mt-8 max-w-6xl overflow-hidden rounded-xl bg-white shadow-sm">
        <h2 className="p-5 text-xl font-semibold">Applicants</h2>
        {selectedJob === null
          ? <p role="status" className="px-5 pb-4 text-sm text-slate-500">Select a posting to view its applicants.</p>
          : <div className="px-5">
            <DataState {...applicantsState} onRetry={() => loadApplicants(selectedJob)}
              isEmpty={applicants.length === 0} loadingMessage="Loading applicants…"
              emptyMessage="No applicants for this posting." />
          </div>}
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead className="bg-slate-100 text-sm"><tr><th className="p-3">Student</th><th className="p-3">Job</th><th className="p-3">Status</th></tr></thead>
            <tbody>{applicants.map((application) => (
              <tr key={application.applicationId} className="border-t">
                <td className="p-3">{application.student?.email}</td>
                <td className="p-3">{application.studentName || application.email}</td>
                <td className="p-3">
                  <FormField id={`application-status-${application.applicationId}`} label="Application status" required>
                    <select name="status" disabled={isPending(`application:${application.applicationId}:status`)}
                      className="w-full rounded border p-2 disabled:cursor-wait disabled:opacity-60"
                      value={pendingRejections[application.applicationId] !== undefined
                        ? 'REJECTED' : application.status}
                      onChange={(event) => {
                        const nextStatus = event.target.value;
                        if (nextStatus === 'REJECTED') {
                          setPendingRejections((current) => ({
                            ...current,
                            [application.applicationId]: current[application.applicationId] || '',
                          }));
                        } else {
                          setPendingRejections((current) => {
                            const next = { ...current };
                            delete next[application.applicationId];
                            return next;
                          });
                          clearRejectionErrors(application.applicationId);
                          updateStatus(application.applicationId, nextStatus);
                        }
                      }}>
                      {statuses.map((status) => <option key={status}>{status}</option>)}
                    </select>
                  </FormField>
                  {isPending(`application:${application.applicationId}:status`)
                    && <span role="status" className="ml-2 text-sm text-slate-600">Updating status…</span>}
                  <div className="mt-2 flex flex-wrap gap-2">
                    <button aria-live="polite" disabled={isPending(`application:${application.applicationId}:schedule-interview`)}
                      onClick={() => openApplicationAction('schedule-interview', application.applicationId)}
                      className="rounded border px-3 py-2 text-sm disabled:cursor-wait disabled:opacity-60">
                      {isPending(`application:${application.applicationId}:schedule-interview`) ? 'Scheduling…' : 'Schedule interview'}
                    </button>
                    {application.status === 'INTERVIEW_SCHEDULED' && <button aria-live="polite"
                      disabled={isPending(`application:${application.applicationId}:offer`)}
                      onClick={() => openApplicationAction('offer', application.applicationId)}
                      className="rounded border px-3 py-2 text-sm disabled:cursor-wait disabled:opacity-60">
                      {isPending(`application:${application.applicationId}:offer`) ? 'Issuing offer…' : 'Issue offer'}
                    </button>}
                  </div>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
        {(rejectionApplicants.length > 0 || actionApplicant) && (
          <div className="space-y-4 p-5">
            {rejectionApplicants.map(application => {
              const applicationId = application.applicationId;
              return <form key={`rejection-${applicationId}`} noValidate
                className="w-full max-w-2xl space-y-3 rounded-lg border bg-slate-50 p-4"
                onSubmit={event => {
                  event.preventDefault();
                  const setErrors = updater => setRejectionFieldErrors(current => ({
                    ...current,
                    [applicationId]: typeof updater === 'function'
                      ? updater(current[applicationId] || {}) : updater,
                  }));
                  if (!validateForm(event.currentTarget, setErrors)) return;
                  if (pendingRejections[applicationId].trim().length < 10) {
                    setErrors(current => ({
                      ...current,
                      rejectionExplanation: 'Enter at least 10 non-whitespace characters.',
                      _form: 'Please correct the highlighted fields and try again.',
                    }));
                    return;
                  }
                  updateStatus(applicationId, 'REJECTED', pendingRejections[applicationId]);
                }}>
                <h3 className="font-semibold">Rejection explanation for {application.studentName
                  || application.student?.email || application.email || `application ${applicationId}`}</h3>
                <p className="text-sm text-slate-600">{application.jobTitle || 'Application'}</p>
                <FormErrorSummary errors={rejectionFieldErrors[applicationId]} />
                <FormField id={`rejection-explanation-${applicationId}`} label="Rejection explanation"
                  required error={rejectionFieldErrors[applicationId]?.rejectionExplanation}>
                  <textarea name="rejectionExplanation" minLength={10} maxLength={1000}
                    className="w-full rounded border p-2" rows={3}
                    value={pendingRejections[applicationId]}
                    onChange={event => {
                      setPendingRejections(current => ({
                        ...current,
                        [applicationId]: event.target.value,
                      }));
                      clearRejectionError(applicationId, 'rejectionExplanation');
                    }} />
                </FormField>
                <div className="flex flex-wrap gap-2">
                  <button type="submit" aria-live="polite" disabled={isPending(`application:${applicationId}:status`)}
                    className="rounded bg-red-700 px-3 py-2 text-sm font-semibold text-white disabled:bg-slate-400">
                    {isPending(`application:${applicationId}:status`) ? 'Saving…' : 'Save rejection'}
                  </button>
                  <button type="button" disabled={isPending(`application:${applicationId}:status`)}
                    onClick={() => {
                      clearRejectionErrors(applicationId);
                      setPendingRejections(current => {
                        const next = { ...current };
                        delete next[applicationId];
                        return next;
                      });
                    }} className="rounded border px-3 py-2 text-sm">
                    Cancel
                  </button>
                </div>
              </form>;
            })}
            {actionApplicant && <form noValidate
              aria-busy={isPending(`application:${applicationAction.applicationId}:${applicationAction.type}`)}
              onSubmit={submitApplicationAction} className="w-full max-w-2xl space-y-3 rounded-lg border bg-slate-50 p-4">
              <h3 className="font-semibold">
                {applicationAction.type === 'offer' ? 'Issue offer' : 'Schedule interview'}
              </h3>
              <p className="text-sm text-slate-600">
                {actionApplicant.studentName || actionApplicant.student?.email || actionApplicant.email
                  || `Application ${applicationAction.applicationId}`}
                {actionApplicant.jobTitle ? ` · ${actionApplicant.jobTitle}` : ''}
              </p>
              <FormErrorSummary errors={applicationActionErrors} />
              {applicationAction.type === 'schedule-interview' && <>
                <FormField id={`scheduled-at-${actionApplicant.applicationId}`} label="Interview date and time" required error={applicationActionErrors.scheduledAt}>
                  <input name="scheduledAt" type="datetime-local" className="w-full rounded border p-2"
                    value={applicationAction.scheduledAt}
                    onChange={event => changeApplicationAction('scheduledAt', event.target.value)} />
                </FormField>
                <FormField id={`interview-mode-${actionApplicant.applicationId}`} label="Interview mode" required error={applicationActionErrors.mode}>
                  <input name="mode" className="w-full rounded border p-2" value={applicationAction.mode}
                    onChange={event => changeApplicationAction('mode', event.target.value)} />
                </FormField>
                <FormField id={`interview-location-${actionApplicant.applicationId}`} label="Meeting link or location" error={applicationActionErrors.locationOrLink}>
                  <input name="locationOrLink" className="w-full rounded border p-2" value={applicationAction.locationOrLink}
                    onChange={event => changeApplicationAction('locationOrLink', event.target.value)} />
                </FormField>
              </>}
              {applicationAction.type === 'offer' && <>
                <FormField id={`offer-package-${actionApplicant.applicationId}`} label="Offer package details" required error={applicationActionErrors.packageDetails}>
                  <input name="packageDetails" className="w-full rounded border p-2" value={applicationAction.packageDetails}
                    onChange={event => changeApplicationAction('packageDetails', event.target.value)} />
                </FormField>
                <FormField id={`offer-joining-date-${actionApplicant.applicationId}`} label="Joining date" required error={applicationActionErrors.joiningDate}>
                  <input name="joiningDate" type="date" className="w-full rounded border p-2" value={applicationAction.joiningDate}
                    onChange={event => changeApplicationAction('joiningDate', event.target.value)} />
                </FormField>
              </>}
              <div className="flex flex-wrap gap-2">
                <button type="submit" aria-live="polite" disabled={isPending(`application:${actionApplicant.applicationId}:${applicationAction.type}`)}
                  className="rounded bg-indigo-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60">
                  {isPending(`application:${actionApplicant.applicationId}:${applicationAction.type}`) ? 'Submitting…' : 'Submit'}
                </button>
                <button type="button" disabled={isPending(`application:${actionApplicant.applicationId}:${applicationAction.type}`)}
                  onClick={() => setApplicationAction(null)} className="rounded border px-3 py-2 text-sm">
                  Cancel
                </button>
              </div>
            </form>}
          </div>
        )}
      </section>
      </>
    </main>
  );
}
