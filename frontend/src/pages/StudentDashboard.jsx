import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

const emptyProfile = {
  fullName: '', rollNumber: '', department: '', phone: '', education: '', graduationYear: '',
  currentSemester: '', cgpa: '', backlogs: '', skills: '', certifications: '', projects: '',
  experience: '', linkedinUrl: '', githubUrl: '', portfolioUrl: '',
};

export default function StudentDashboard() {
  const { logout, user } = useAuth();
  const [tab, setTab] = useState('jobs');
  const [profile, setProfile] = useState(emptyProfile);
  const [opportunities, setOpportunities] = useState([]);
  const [saved, setSaved] = useState([]);
  const [applications, setApplications] = useState([]);
  const [interviews, setInterviews] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [placementDrives, setPlacementDrives] = useState([]);
  const [filters, setFilters] = useState({ search: '', location: '', company: '', skills: '', jobType: '', eligibleOnly: true });
  const [resume, setResume] = useState(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true); setError('');
    try {
      const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value !== false));
      const [profileResponse, jobsResponse, savedResponse, applicationsResponse, interviewsResponse, notificationsResponse, drivesResponse] = await Promise.all([
        api.get('/student/profile'),
        api.get('/student/opportunities', { params }),
        api.get('/student/saved-jobs'),
        api.get('/student/applications'),
        api.get('/student/interviews'),
        api.get('/student/notifications'),
        api.get('/student/placement-drives'),
      ]);
      setProfile({
        ...profileResponse.data,
        graduationYear: profileResponse.data.graduationYear ?? '',
        currentSemester: profileResponse.data.currentSemester ?? '',
        cgpa: profileResponse.data.cgpa ?? '',
        backlogs: profileResponse.data.backlogs ?? '',
      });
      setOpportunities(jobsResponse.data.content);
      setSaved(savedResponse.data);
      setApplications(applicationsResponse.data);
      setInterviews(interviewsResponse.data);
      setNotifications(notificationsResponse.data);
      setPlacementDrives(drivesResponse.data);
    } catch (requestError) {
      setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to load student workspace');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const saveProfile = async (event) => {
    event.preventDefault();
    try {
      await api.put('/student/profile', {
        ...profile,
        graduationYear: Number(profile.graduationYear),
        currentSemester: profile.currentSemester === '' ? null : Number(profile.currentSemester),
        cgpa: Number(profile.cgpa),
        backlogs: Number(profile.backlogs),
      });
      setMessage('Profile saved');
    } catch (requestError) {
      setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to save profile');
    }
  };

  const toggleSaved = async (jobId, isSaved) => {
    await api[isSaved ? 'delete' : 'put'](`/student/saved-jobs/${jobId}`);
    await load();
  };

  const apply = async (jobId) => {
    try {
      await api.post(`/student/applications/${jobId}`);
      setMessage('Application submitted');
      await load();
    } catch (requestError) {
      setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to apply');
    }
  };

  const withdraw = async (applicationId) => {
    await api.post(`/student/applications/${applicationId}/withdraw`);
    setMessage('Application withdrawn');
    await load();
  };

  const registerForDrive = async (driveId) => {
    try {
      await api.post(`/student/placement-drives/${driveId}/registration`);
      setMessage('Registered for placement drive');
      await load();
    } catch (requestError) {
      setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to register for drive');
    }
  };

  const uploadResume = async () => {
    if (!resume) return;
    const form = new FormData();
    form.append('file', resume);
    try {
      await api.post('/student/resume', form, { headers: { 'Content-Type': 'multipart/form-data' } });
      setMessage('Resume uploaded');
      setResume(null);
      await load();
    } catch (requestError) {
      setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Resume upload failed');
    }
  };

  const downloadResume = async () => {
    const response = await api.get('/student/resume', { responseType: 'blob' });
    const url = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url; link.download = 'resume.pdf'; link.click(); URL.revokeObjectURL(url);
  };

  const filterJobs = async (event) => {
    event.preventEvent?.(); event.preventDefault();
    await load();
  };

  return (
    <main className="min-h-screen bg-slate-50 p-4 md:p-6">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <div><h1 className="text-2xl font-bold md:text-3xl">Student Workspace</h1><p className="text-slate-500">{user?.email}</p></div>
        <button onClick={logout} className="rounded border px-4 py-2 font-semibold">Logout</button>
      </header>
      {message && <p className="mx-auto mt-4 max-w-6xl rounded bg-green-50 p-3 text-green-700">{message}</p>}
      {error && <p className="mx-auto mt-4 max-w-6xl rounded bg-red-50 p-3 text-red-700">{error}</p>}
      <nav className="mx-auto mt-6 flex max-w-6xl gap-2 overflow-x-auto">
        {['jobs', 'applications', 'profile', 'resume', 'interviews', 'notifications', 'drives'].map(item =>
          <button key={item} onClick={() => setTab(item)} className={`rounded-full px-4 py-2 text-sm capitalize ${tab === item ? 'bg-indigo-600 text-white' : 'border bg-white'}`}>{item}</button>)}
        <Link to="/student/interviews" className="shrink-0 rounded-full border bg-white px-4 py-2 text-sm">Interview details</Link>
        <Link to="/student/offers" className="shrink-0 rounded-full border bg-white px-4 py-2 text-sm">My offers</Link>
      </nav>
      {loading ? <p className="mx-auto mt-8 max-w-6xl text-slate-500">Loading workspace...</p> : <>
        {tab === 'jobs' && <section className="mx-auto mt-6 max-w-6xl">
          <form onSubmit={filterJobs} className="grid gap-3 rounded-xl bg-white p-4 shadow-sm md:grid-cols-3">
            {Object.entries(filters).filter(([key]) => key !== 'eligibleOnly').map(([key, value]) =>
              <input key={key} className="rounded border p-2" placeholder={key[0].toUpperCase() + key.slice(1)}
                value={value} onChange={(event) => setFilters({...filters, [key]: event.target.value})} />)}
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={filters.eligibleOnly}
              onChange={(event) => setFilters({...filters, eligibleOnly: event.target.checked})} /> Eligible only</label>
            <button className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white">Search opportunities</button>
          </form>
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            {opportunities.map(job => <article key={job.id} className="rounded-xl bg-white p-5 shadow-sm">
              <div className="flex justify-between gap-3"><div><h2 className="text-lg font-bold">{job.title}</h2><p className="text-indigo-600">{job.companyName}</p></div>
                <button onClick={() => toggleSaved(job.id, job.saved)} className="rounded border px-3 py-1 text-sm">{job.saved ? 'Saved' : 'Save'}</button></div>
              <p className="mt-3 text-sm text-slate-600">{job.description}</p>
              <p className="mt-3 text-sm">{job.location || 'Location not specified'} · Deadline {job.deadline}</p>
              <button disabled={!job.eligible} onClick={() => apply(job.id)}
                className="mt-4 w-full rounded bg-indigo-600 py-2 font-semibold text-white disabled:bg-slate-300">{job.eligible ? 'Apply' : 'Not eligible'}</button>
            </article>)}
            {opportunities.length === 0 && <p className="text-slate-500 md:col-span-2">No opportunities match these filters.</p>}
          </div>
        </section>}
        {tab === 'applications' && <section className="mx-auto mt-6 max-w-6xl space-y-3">
          {applications.map(application => <article key={application.id} className="rounded-xl bg-white p-5 shadow-sm">
            <div className="flex justify-between"><div><h2 className="font-bold">{application.jobTitle}</h2><p className="text-sm text-slate-500">{application.companyName}</p></div>
              <span className="rounded-full bg-indigo-50 px-3 py-1 text-sm font-semibold text-indigo-700">{application.status}</span></div>
            {application.recruiterFeedback && <p className="mt-3 text-sm">Feedback: {application.recruiterFeedback}</p>}
            {application.interview && <p className="mt-2 text-sm">Interview: {new Date(application.interview.scheduledAt).toLocaleString()} · {application.interview.mode}</p>}
            {application.status === 'APPLIED' && <button onClick={() => withdraw(application.id)} className="mt-3 rounded border px-3 py-1 text-sm">Withdraw</button>}
          </article>)}
          {applications.length === 0 && <p className="text-slate-500">You have not submitted any applications.</p>}
        </section>}
        {tab === 'profile' && <form onSubmit={saveProfile} className="mx-auto mt-6 grid max-w-6xl gap-3 rounded-xl bg-white p-6 shadow-sm md:grid-cols-2">
          {Object.entries(emptyProfile).map(([key]) => <label key={key} className="text-sm font-medium capitalize">{key.replace(/([A-Z])/g, ' $1')}
            <input className="mt-1 w-full rounded border p-2" required={['fullName','rollNumber','department','cgpa','backlogs','graduationYear'].includes(key)}
              type={['graduationYear','currentSemester','cgpa','backlogs'].includes(key) ? 'number' : 'text'}
              value={profile[key] ?? ''} onChange={(event) => setProfile({...profile, [key]: event.target.value})} /></label>)}
          <button className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white md:col-span-2">Save profile</button>
        </form>}
        {tab === 'resume' && <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
          <h2 className="text-xl font-semibold">Resume</h2><p className="mt-2 text-sm text-slate-500">{profile.resumeUrl ? 'A PDF resume is stored for your account.' : 'No resume has been uploaded.'}</p>
          <div className="mt-4 flex flex-wrap gap-3"><input type="file" accept="application/pdf,.pdf" onChange={(event) => setResume(event.target.files[0])} />
            <button disabled={!resume} onClick={uploadResume} className="rounded bg-indigo-600 px-4 py-2 text-white disabled:bg-slate-300">Upload / replace</button>
            {profile.resumeUrl && <button onClick={downloadResume} className="rounded border px-4 py-2">Download</button>}
          </div>
        </section>}
        {tab === 'interviews' && <section className="mx-auto mt-6 max-w-6xl space-y-3">{interviews.map(interview => <article key={interview.id} className="rounded-xl bg-white p-4 shadow-sm">
          <p className="font-semibold">{new Date(interview.scheduledAt).toLocaleString()}</p><p className="text-sm text-slate-500">{interview.mode} · {interview.locationOrLink || 'Meeting details pending'}</p>
        </article>)}{interviews.length === 0 && <p className="text-slate-500">No interviews scheduled.</p>}</section>}
        {tab === 'drives' && <section className="mx-auto mt-6 max-w-6xl space-y-3">
          {placementDrives.map(drive => <article key={drive.id} className="rounded-xl bg-white p-4 shadow-sm">
            <h2 className="font-semibold">{drive.title}</h2>
            <p className="text-sm text-slate-600">{drive.companyName || 'Company not specified'} · Registration deadline {drive.registrationDeadline}</p>
            <button disabled={drive.registered} onClick={() => registerForDrive(drive.id)}
              className="mt-3 rounded bg-indigo-600 px-4 py-2 text-white disabled:bg-slate-300">
              {drive.registered ? `Registered · ${drive.registrationStatus}` : 'Register'}
            </button>
          </article>)}
          {placementDrives.length === 0 && <p className="text-slate-500">No open placement drives.</p>}
        </section>}
        {tab === 'notifications' && <section className="mx-auto mt-6 max-w-6xl space-y-3">{notifications.map(notification => <article key={notification.id} className="rounded-xl bg-white p-4 shadow-sm">
          <p className="font-semibold">{notification.title}</p><p className="text-sm text-slate-600">{notification.message}</p>
        </article>)}{notifications.length === 0 && <p className="text-slate-500">No notifications.</p>}</section>}
        {tab === 'saved-jobs' && null}
      </>}
    </main>
  );
}
