import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import useDashboardAnalytics from '../hooks/useDashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';
import {
  ActivityList, AnalyticsPanel, DataState, MetricGrid, MutationFeedback, StatusDistribution, StudentCommitments,
} from '../components/DashboardAnalytics';

const emptyProfile = {
  fullName: '', rollNumber: '', department: '', phone: '', education: '', graduationYear: '',
  currentSemester: '', cgpa: '', backlogs: '', skills: '', certifications: '', projects: '',
  experience: '', linkedinUrl: '', githubUrl: '', portfolioUrl: '',
};
const profileLabels = {
  fullName: 'Full name',
  rollNumber: 'Roll number',
  department: 'Department',
  phone: 'Phone',
  education: 'Education',
  graduationYear: 'Graduation year',
  currentSemester: 'Current semester',
  cgpa: 'CGPA',
  backlogs: 'Backlogs',
  skills: 'Skills',
  certifications: 'Certifications',
  projects: 'Projects',
  experience: 'Experience',
  linkedinUrl: 'LinkedIn URL',
  githubUrl: 'GitHub URL',
  portfolioUrl: 'Portfolio URL',
};
const initialSectionStates = {
  profile: { loading: true, error: '' },
  opportunities: { loading: true, error: '' },
  savedJobs: { loading: true, error: '' },
  applications: { loading: true, error: '' },
  interviews: { loading: true, error: '' },
  drives: { loading: true, error: '' },
};

export default function StudentDashboard() {
  const { logout, user } = useAuth();
  const fetchStats = useCallback((signal) => api.get('/student/stats', { signal }), []);
  const { stats, loading: analyticsLoading, error: analyticsError, refresh: refreshAnalytics } =
    useDashboardAnalytics(fetchStats, 'Unable to load student analytics. Please try again.');
  const { feedback, isPending, pending, runMutation } = useMutationFeedback();
  const [tab, setTab] = useState('jobs');
  const [profile, setProfile] = useState(emptyProfile);
  const [opportunities, setOpportunities] = useState([]);
  const [saved, setSaved] = useState([]);
  const [applications, setApplications] = useState([]);
  const [interviews, setInterviews] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [notificationsLoaded, setNotificationsLoaded] = useState(false);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState('');
  const notificationRequestId = useRef(0);
  const notificationRequest = useRef(null);
  const dashboardMounted = useRef(false);
  const [placementDrives, setPlacementDrives] = useState([]);
  const [filters, setFilters] = useState({ search: '', location: '', company: '', skills: '', jobType: '', eligibleOnly: true });
  const [resume, setResume] = useState(null);
  const [profileFieldErrors, setProfileFieldErrors] = useState({});
  const [resumeFieldErrors, setResumeFieldErrors] = useState({});
  const [resumeError, setResumeError] = useState('');
  const [sectionStates, setSectionStates] = useState(initialSectionStates);
  const sectionRequestIds = useRef({});

  const loadNotifications = async (force = false) => {
    if (notificationRequest.current && !force) return notificationRequest.current;
    const requestId = ++notificationRequestId.current;
    setNotificationsLoading(true);
    setNotificationsError('');
    const request = (async () => {
      try {
        const { data } = await api.get('/student/notifications');
        if (dashboardMounted.current && requestId === notificationRequestId.current) {
          setNotifications(data || []);
          setNotificationsLoaded(true);
        }
      } catch (requestError) {
        if (dashboardMounted.current && requestId === notificationRequestId.current) {
          setNotificationsError(requestError.response?.data?.detail
            || requestError.response?.data?.message || 'Unable to load notifications');
        }
      } finally {
        if (dashboardMounted.current && requestId === notificationRequestId.current) setNotificationsLoading(false);
        if (notificationRequest.current === request) notificationRequest.current = null;
      }
    })();
    notificationRequest.current = request;
    return request;
  };

  const loadSection = async (key, request, applyData, failureMessage) => {
    const requestId = (sectionRequestIds.current[key] || 0) + 1;
    sectionRequestIds.current[key] = requestId;
    setSectionStates(current => ({
      ...current,
      [key]: { loading: true, error: '' },
    }));
    try {
      const { data } = await request();
      if (dashboardMounted.current && sectionRequestIds.current[key] === requestId) {
        applyData(data);
        setSectionStates(current => ({
          ...current,
          [key]: { loading: false, error: '' },
        }));
      }
    } catch (requestError) {
      if (dashboardMounted.current && sectionRequestIds.current[key] === requestId) {
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

  const loadProfile = () => loadSection('profile', () => api.get('/student/profile'), data => {
    setProfile({
      ...data,
      graduationYear: data.graduationYear ?? '',
      currentSemester: data.currentSemester ?? '',
      cgpa: data.cgpa ?? '',
      backlogs: data.backlogs ?? '',
    });
  }, 'Unable to load your profile.');

  const loadOpportunities = (nextFilters = filters) => {
    const params = Object.fromEntries(Object.entries(nextFilters)
      .filter(([, value]) => value !== '' && value !== false));
    return loadSection('opportunities', () => api.get('/student/opportunities', { params }),
      data => setOpportunities(data.content || []), 'Unable to load opportunities.');
  };

  const loadSavedJobs = () => loadSection('savedJobs', () => api.get('/student/saved-jobs'),
    data => setSaved(data || []), 'Unable to load saved jobs.');

  const loadApplications = () => loadSection('applications', () => api.get('/student/applications'),
    data => setApplications(data || []), 'Unable to load applications.');

  const loadInterviews = () => loadSection('interviews', () => api.get('/student/interviews'),
    data => setInterviews(data || []), 'Unable to load interviews.');

  const loadDrives = () => loadSection('drives', () => api.get('/student/placement-drives'),
    data => setPlacementDrives(data || []), 'Unable to load placement drives.');

  const load = async () => {
    const notificationsRequest = loadNotifications();
    await Promise.all([
      loadProfile(), loadOpportunities(), loadSavedJobs(), loadApplications(), loadInterviews(), loadDrives(),
    ]);
    await notificationsRequest;
  };

  const markNotificationRead = async (notificationId) => {
    setNotificationsError('');
    const result = await runMutation(`notification:${notificationId}`,
      () => api.patch(`/student/notifications/${notificationId}/read`),
      'Notification marked as read.', 'Unable to mark notification as read.');
    if (result.ok && result.mounted) {
      await loadNotifications(true);
    }
  };

  const markAllNotificationsRead = async () => {
    setNotificationsError('');
    const result = await runMutation('notifications:read-all',
      () => api.patch('/student/notifications/read-all'),
      'All notifications marked as read.', 'Unable to mark notifications as read.');
    if (result.ok && result.mounted) {
      await loadNotifications(true);
    }
  };

  useEffect(() => {
    dashboardMounted.current = true;
    load();
    return () => {
      dashboardMounted.current = false;
      Object.keys(sectionRequestIds.current).forEach(key => {
        sectionRequestIds.current[key] += 1;
      });
    };
  }, []);

  const saveProfile = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setProfileFieldErrors)) return;
    const result = await runMutation('profile:save', () => api.put('/student/profile', {
        ...profile,
        graduationYear: Number(profile.graduationYear),
        currentSemester: profile.currentSemester === '' ? null : Number(profile.currentSemester),
        cgpa: Number(profile.cgpa),
        backlogs: Number(profile.backlogs),
      }), 'Profile saved.', 'Unable to save your profile.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setProfileFieldErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
  };

  const toggleSaved = async (jobId, isSaved) => {
    const result = await runMutation(`saved-job:${jobId}`,
      () => api[isSaved ? 'delete' : 'put'](`/student/saved-jobs/${jobId}`),
      isSaved ? 'Job removed from saved jobs.' : 'Job saved.',
      isSaved ? 'Unable to remove saved job.' : 'Unable to save job.');
    if (!result.ok || !result.mounted) return;
    await load();
    await refreshAnalytics();
  };

  const apply = async (jobId) => {
    const result = await runMutation(`application:${jobId}:apply`,
      () => api.post(`/student/applications/${jobId}`),
      'Application submitted.', 'Unable to submit your application.');
    if (!result.ok || !result.mounted) return;
    await load();
    await refreshAnalytics();
  };

  const withdraw = async (applicationId) => {
    const result = await runMutation(`application:${applicationId}:withdraw`,
      () => api.post(`/student/applications/${applicationId}/withdraw`),
      'Application withdrawn.', 'Unable to withdraw your application.');
    if (!result.ok || !result.mounted) return;
    await load();
    await refreshAnalytics();
  };

  const registerForDrive = async (driveId) => {
    const result = await runMutation(`drive:${driveId}:register`,
      () => api.post(`/student/placement-drives/${driveId}/registration`),
      'Registered for placement drive.', 'Unable to register for placement drive.');
    if (!result.ok || !result.mounted) return;
    await load();
  };

  const uploadResume = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setResumeFieldErrors)) return;
    if (!resume) return;
    if (resume.type !== 'application/pdf') {
      setResumeError('Choose a PDF file.');
      return;
    }
    setResumeError('');
    const form = new FormData();
    form.append('file', resume);
    const result = await runMutation('resume:upload',
      () => api.post('/student/resume', form, { headers: { 'Content-Type': 'multipart/form-data' } }),
      'Resume uploaded.', 'Unable to upload your resume.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (serverErrors.file || serverErrors.resume) {
        setResumeFieldErrors(current => ({
          ...current,
          file: serverErrors.file || serverErrors.resume,
        }));
      }
    }
    if (!result.ok || !result.mounted) return;
    setResume(null);
    await load();
  };

  const downloadResume = async () => {
    const response = await api.get('/student/resume', { responseType: 'blob' });
    const url = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url; link.download = 'resume.pdf'; link.click(); URL.revokeObjectURL(url);
  };

  const filterJobs = async (event) => {
    event.preventEvent?.(); event.preventDefault();
    await loadOpportunities(filters);
  };
  const notificationMutationPending = Object.keys(pending)
    .some(key => key.startsWith('notification:') || key === 'notifications:read-all');

  return (
    <main className="min-h-screen bg-slate-50 p-4 md:p-6">
      <header className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-bold md:text-3xl">Student Workspace</h1>
          <p className="break-words text-slate-500">{user?.email}</p>
        </div>
        <button onClick={logout} className="min-h-11 shrink-0 rounded border px-4 py-2 font-semibold">Logout</button>
      </header>
      <MutationFeedback feedback={feedback} />
      <nav className="mx-auto mt-6 flex max-w-6xl gap-2 overflow-x-auto">
        {['jobs', 'applications', 'profile', 'resume', 'interviews', 'notifications', 'drives'].map(item =>
          <button key={item} onClick={() => setTab(item)} className={`flex shrink-0 items-center gap-2 rounded-full px-4 py-2 text-sm capitalize ${tab === item ? 'bg-indigo-600 text-white' : 'border bg-white'}`}>
            {item}
            {item === 'notifications' && notifications.some(notification => !notification.readFlag)
              && <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-bold text-white"
                aria-label={`${notifications.filter(notification => !notification.readFlag).length} unread`}>
                {notifications.filter(notification => !notification.readFlag).length}
              </span>}
          </button>)}
        <Link to="/student/interviews" className="shrink-0 rounded-full border bg-white px-4 py-2 text-sm">Interview details</Link>
        <Link to="/student/offers" className="shrink-0 rounded-full border bg-white px-4 py-2 text-sm">My offers</Link>
      </nav>
      <AnalyticsPanel loading={analyticsLoading} error={analyticsError} onRefresh={refreshAnalytics} hasData={stats !== null}>
        {stats && <div className="space-y-4">
          {stats.totalApplications === 0 && stats.upcomingInterviews === 0 && (
            <p role="status" className="rounded-xl border border-indigo-100 bg-indigo-50 p-4 text-sm text-indigo-800">
              No applications or upcoming interviews yet. Browse opportunities to get started.
            </p>
          )}
          <MetricGrid columns="xl:grid-cols-2" metrics={[
            { label: 'Applications', value: stats.totalApplications },
            { label: 'Saved jobs', value: stats.savedJobs },
          ]} />
          <div className="grid gap-4 lg:grid-cols-2">
            <StatusDistribution title="Application progress" counts={stats.applicationsByStatus} />
            <StudentCommitments stats={stats} />
          </div>
          <ActivityList items={stats.recentApplicationActivity} />
        </div>}
      </AnalyticsPanel>
      <>
        {tab === 'jobs' && <section className="mx-auto mt-6 max-w-6xl">
          <form onSubmit={filterJobs} className="grid gap-3 rounded-xl bg-white p-4 shadow-sm md:grid-cols-3">
            {Object.entries(filters).filter(([key]) => key !== 'eligibleOnly').map(([key, value]) => (
              <FormField key={key} id={`opportunity-filter-${key}`} label={key[0].toUpperCase() + key.slice(1)}>
                <input name={key} type={key === 'search' ? 'search' : 'text'} placeholder={key[0].toUpperCase() + key.slice(1)}
                  className="w-full rounded border p-2"
                  value={value} onChange={(event) => setFilters({...filters, [key]: event.target.value})} />
              </FormField>
            ))}
            <label htmlFor="eligible-only-filter" className="flex items-center gap-2 text-sm">
              <input id="eligible-only-filter" name="eligibleOnly" type="checkbox" checked={filters.eligibleOnly}
                onChange={(event) => setFilters({...filters, eligibleOnly: event.target.checked})} />
              Eligible only (optional)
            </label>
            <button type="submit" className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white">Search opportunities</button>
          </form>
          <DataState {...sectionStates.opportunities}
            onRetry={() => loadOpportunities(filters)}
            isEmpty={opportunities.length === 0}
            loadingMessage="Loading opportunities…"
            emptyMessage="No opportunities match these filters." />
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            {opportunities.map(job => <article key={job.id} className="rounded-xl bg-white p-5 shadow-sm">
              <div className="flex justify-between gap-3"><div><h2 className="text-lg font-bold">{job.title}</h2><p className="text-indigo-600">{job.companyName}</p></div>
                <button aria-live="polite" disabled={isPending(`saved-job:${job.id}`)} onClick={() => toggleSaved(job.id, job.saved)}
                  className="rounded border px-3 py-1 text-sm disabled:cursor-wait disabled:opacity-60">
                  {isPending(`saved-job:${job.id}`) ? 'Saving…' : job.saved ? 'Saved' : 'Save'}
                </button></div>
              <p className="mt-3 text-sm text-slate-600">{job.description}</p>
              <p className="mt-3 text-sm">{job.location || 'Location not specified'} · Deadline {job.deadline}</p>
              <button aria-live="polite" disabled={!job.eligible || isPending(`application:${job.id}:apply`)} onClick={() => apply(job.id)}
                className="mt-4 w-full rounded bg-indigo-600 py-2 font-semibold text-white disabled:bg-slate-300">
                {isPending(`application:${job.id}:apply`) ? 'Applying…' : job.eligible ? 'Apply' : 'Not eligible'}
              </button>
            </article>)}
          </div>
        </section>}
        {tab === 'applications' && <section className="mx-auto mt-6 max-w-6xl space-y-3">
          <DataState {...sectionStates.applications} onRetry={loadApplications}
            isEmpty={applications.length === 0} loadingMessage="Loading applications…"
            emptyMessage="You have not submitted any applications." />
          {applications.map(application => {
            const withdrawn = Boolean(application.withdrawnAt);
            return <article key={application.id} className="rounded-xl bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div><h2 className="font-bold">{application.jobTitle}</h2><p className="text-sm text-slate-500">{application.companyName}</p></div>
                <span className={`rounded-full px-3 py-1 text-sm font-semibold ${withdrawn
                  ? 'bg-slate-100 text-slate-700' : 'bg-indigo-50 text-indigo-700'}`}>
                  {withdrawn ? 'WITHDRAWN' : application.status}
                </span>
              </div>
              {withdrawn && <p className="mt-2 text-sm text-slate-600">
                Withdrawn on <time dateTime={application.withdrawnAt}>{new Date(application.withdrawnAt).toLocaleString()}</time>
              </p>}
              {application.recruiterFeedback && <p className="mt-3 text-sm">Feedback: {application.recruiterFeedback}</p>}
              {application.interview && <p className="mt-2 text-sm">Interview: {new Date(application.interview.scheduledAt).toLocaleString()} · {application.interview.mode}</p>}
              {application.status === 'APPLIED' && !withdrawn && <button aria-live="polite"
                disabled={isPending(`application:${application.id}:withdraw`)}
                onClick={() => withdraw(application.id)}
                className="mt-3 rounded border px-3 py-1 text-sm disabled:cursor-wait disabled:opacity-60">
                {isPending(`application:${application.id}:withdraw`) ? 'Withdrawing…' : 'Withdraw'}
              </button>}
              <section className="mt-5 border-t pt-4" aria-label={`Application timeline for ${application.jobTitle}`}>
                <h3 className="font-semibold">Application timeline</h3>
                {application.history?.length ? (
                  <ol className="mt-3 space-y-4 border-l border-slate-200 pl-4 sm:pl-6">
                    {application.history.map((event, index) => {
                      const eventName = event.status === 'WITHDRAWN'
                        ? 'Application withdrawn' : event.status.replaceAll('_', ' ');
                      return <li key={`${event.changedAt}-${index}`} className="relative min-w-0">
                        <span className="absolute -left-[1.32rem] top-1.5 h-2 w-2 rounded-full bg-indigo-500 sm:-left-[1.82rem]" aria-hidden="true" />
                        <p className="font-medium">{eventName}</p>
                        <time className="text-sm text-slate-500" dateTime={event.changedAt}>
                          {new Date(event.changedAt).toLocaleString()}
                        </time>
                        {event.note && !event.rejectionExplanation && <p className="mt-1 break-words text-sm text-slate-700">
                          <span className="font-semibold">Status note:</span> {event.note}
                        </p>}
                        {event.rejectionExplanation && <p className="mt-1 break-words rounded bg-red-50 p-3 text-sm text-red-900">
                          <span className="font-semibold">Rejection explanation:</span> {event.rejectionExplanation}
                        </p>}
                      </li>;
                    })}
                  </ol>
                ) : <p className="mt-2 text-sm text-slate-500">No application history is available yet.</p>}
              </section>
            </article>;
          })}
        </section>}
        {tab === 'profile' && <section className="mx-auto mt-6 max-w-6xl">
          <DataState {...sectionStates.profile} onRetry={loadProfile} isEmpty={false}
            loadingMessage="Loading profile…" />
          {!sectionStates.profile.loading && !sectionStates.profile.error && (
            <form noValidate aria-busy={isPending('profile:save')} onSubmit={saveProfile} className="grid gap-3 rounded-xl bg-white p-6 shadow-sm md:grid-cols-2">
              <FormErrorSummary errors={profileFieldErrors} />
              {Object.entries(emptyProfile).map(([key]) => {
                const required = ['fullName','rollNumber','department','cgpa','backlogs','graduationYear'].includes(key);
                const type = ['graduationYear','currentSemester','cgpa','backlogs'].includes(key) ? 'number'
                  : key === 'phone' ? 'tel' : ['linkedinUrl','githubUrl','portfolioUrl'].includes(key) ? 'url' : 'text';
                return <FormField key={key} id={`profile-${key}`} label={profileLabels[key]} required={required}
                  error={profileFieldErrors[key]}>
                  <input name={key} className="w-full rounded border p-2" type={type}
                    min={key === 'graduationYear' ? 2020 : key === 'cgpa' ? 0 : key === 'backlogs' ? 0 : undefined}
                    max={key === 'graduationYear' ? 2040 : key === 'cgpa' ? 10 : undefined}
                    step={key === 'cgpa' ? '0.01' : ['graduationYear','currentSemester','backlogs'].includes(key) ? '1' : undefined}
                    autoComplete={key === 'fullName' ? 'name' : key === 'phone' ? 'tel' : undefined}
                    value={profile[key] ?? ''} onChange={(event) => {
                      setProfile({...profile, [key]: event.target.value});
                      clearFieldError(setProfileFieldErrors, key);
                    }} />
                </FormField>;
              })}
              <button aria-live="polite" disabled={isPending('profile:save')}
                className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white disabled:cursor-wait disabled:opacity-60 md:col-span-2">
                {isPending('profile:save') ? 'Saving profile…' : 'Save profile'}
              </button>
            </form>
          )}
        </section>}
        {tab === 'resume' && <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
          <h2 className="text-xl font-semibold">Resume</h2>
          <DataState {...sectionStates.profile} onRetry={loadProfile} isEmpty={false}
            loadingMessage="Loading resume details…" />
          {!sectionStates.profile.loading && !sectionStates.profile.error && (
            <>
              <p className="mt-2 text-sm text-slate-500">{profile.resumeUrl ? 'A PDF resume is stored for your account.' : 'No resume has been uploaded.'}</p>
              <form noValidate aria-busy={isPending('resume:upload')} onSubmit={uploadResume} className="mt-4 max-w-xl">
                <FormErrorSummary errors={{ _form: resumeError || resumeFieldErrors._form }} />
                <FormField id="resume-file" label="Resume (PDF)" required error={resumeError || resumeFieldErrors.file}>
                  <input name="file" type="file" accept="application/pdf,.pdf" className="w-full rounded border p-2"
                    onChange={(event) => { setResume(event.target.files[0] || null); setResumeError(''); clearFieldError(setResumeFieldErrors, 'file'); }} />
                </FormField>
                <button type="submit" aria-live="polite" disabled={isPending('resume:upload')}
                  className="rounded bg-indigo-600 px-4 py-2 text-white disabled:bg-slate-300">
                  {isPending('resume:upload') ? 'Uploading…' : 'Upload / replace'}
                </button>
                {resume && <p className="mt-2 text-sm text-slate-600">Selected: {resume.name}</p>}
                {profile.resumeUrl && <button type="button" onClick={downloadResume} className="rounded border px-4 py-2">Download</button>}
              </form>
            </>
          )}
        </section>}
        {tab === 'interviews' && <section className="mx-auto mt-6 max-w-6xl space-y-3">
          <DataState {...sectionStates.interviews} onRetry={loadInterviews}
            isEmpty={interviews.length === 0} loadingMessage="Loading interviews…"
            emptyMessage="No interviews scheduled." />
          {interviews.map(interview => <article key={interview.id} className="rounded-xl bg-white p-4 shadow-sm">
          <p className="font-semibold">{new Date(interview.scheduledAt).toLocaleString()}</p><p className="text-sm text-slate-500">{interview.mode} · {interview.locationOrLink || 'Meeting details pending'}</p>
        </article>)}</section>}
        {tab === 'drives' && <section className="mx-auto mt-6 max-w-6xl space-y-3">
          <DataState {...sectionStates.drives} onRetry={loadDrives}
            isEmpty={placementDrives.length === 0} loadingMessage="Loading placement drives…"
            emptyMessage="No open placement drives." />
          {placementDrives.map(drive => <article key={drive.id} className="rounded-xl bg-white p-4 shadow-sm">
            <h2 className="font-semibold">{drive.title}</h2>
            <p className="text-sm text-slate-600">{drive.companyName || 'Company not specified'} · Registration deadline {drive.registrationDeadline}</p>
            <button aria-live="polite" disabled={drive.registered || isPending(`drive:${drive.id}:register`)}
              onClick={() => registerForDrive(drive.id)}
              className="mt-3 rounded bg-indigo-600 px-4 py-2 text-white disabled:bg-slate-300">
              {isPending(`drive:${drive.id}:register`) ? 'Registering…'
                : drive.registered ? `Registered · ${drive.registrationStatus}` : 'Register'}
            </button>
          </article>)}
        </section>}
        {tab === 'notifications' && <section className="mx-auto mt-6 max-w-6xl space-y-3" aria-labelledby="notifications-heading">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="notifications-heading" className="text-xl font-semibold">Notifications</h2>
              <p className="text-sm text-slate-600" aria-live="polite">
                {notifications.filter(notification => !notification.readFlag).length} unread
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button aria-live="polite" disabled={notificationsLoading || notificationMutationPending}
                onClick={loadNotifications}
                className="rounded border px-4 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50">
                Refresh notifications
              </button>
              <button aria-live="polite" disabled={notificationMutationPending
                  || !notifications.some(notification => !notification.readFlag)}
                onClick={markAllNotificationsRead}
                className="rounded border px-4 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50">
                {isPending('notifications:read-all') ? 'Marking all read…' : 'Mark all as read'}
              </button>
            </div>
          </div>
          {notificationsError && <p role="alert" className="rounded bg-red-50 p-3 text-sm text-red-800">{notificationsError}</p>}
          {notificationsLoading && <p role="status" className="text-sm text-slate-500">Loading notifications…</p>}
          {!notificationsLoading && notificationsLoaded && notifications.length === 0 && !notificationsError
            && <p className="rounded-xl bg-white p-4 text-slate-500 shadow-sm">No notifications.</p>}
          <div className="space-y-3">
            {notifications.map(notification => <article key={notification.id}
              aria-label={notification.readFlag ? 'Read notification' : 'Unread notification'}
              className={`flex flex-wrap items-start justify-between gap-4 rounded-xl p-4 shadow-sm ${notification.readFlag
                ? 'border border-slate-200 bg-white' : 'border border-indigo-200 bg-indigo-50'}`}>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className={`font-semibold ${notification.readFlag ? 'text-slate-700' : 'text-slate-950'}`}>{notification.title}</p>
                  {!notification.readFlag && <span className="rounded-full bg-indigo-700 px-2 py-0.5 text-xs font-semibold text-white">Unread</span>}
                </div>
                <p className="mt-1 break-words text-sm text-slate-600">{notification.message}</p>
                {notification.createdAt && <time className="mt-2 block text-xs text-slate-500"
                  dateTime={notification.createdAt}>{new Date(notification.createdAt).toLocaleString()}</time>}
              </div>
              {!notification.readFlag && <button aria-live="polite" disabled={notificationMutationPending}
                aria-label={`Mark ${notification.title} as read`}
                onClick={() => markNotificationRead(notification.id)}
                className="rounded border bg-white px-3 py-2 text-sm font-semibold disabled:opacity-50">
                {isPending(`notification:${notification.id}`) ? 'Marking read…' : 'Mark as read'}
              </button>}
            </article>)}
          </div>
        </section>}
        {tab === 'saved-jobs' && null}
      </>
    </main>
  );
}
