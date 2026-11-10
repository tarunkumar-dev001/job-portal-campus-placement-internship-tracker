import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

const emptyDrive = {
  title: '', companyName: '', jobTitle: '', driveType: 'INTERNSHIP',
  description: '', minCgpa: '', maxBacklogs: '', registrationDeadline: '', driveDate: '',
};

export default function PlacementDashboard() {
  const { logout, user } = useAuth();
  const [drives, setDrives] = useState([]);
  const [stats, setStats] = useState(null);
  const [drive, setDrive] = useState(emptyDrive);
  const [selectedFile, setSelectedFile] = useState(null);
  const [activeDriveId, setActiveDriveId] = useState(null);
  const [registrations, setRegistrations] = useState([]);
  const [selectionDetails, setSelectionDetails] = useState({});
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    try {
      const [driveResponse, statsResponse] = await Promise.all([
        api.get('/placement/drives'),
        api.get('/placement/stats'),
      ]);
      setDrives(driveResponse.data);
      setStats(statsResponse.data);
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to load placement data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const createDrive = async (event) => {
    event.preventDefault();
    setMessage('');
    try {
      await api.post('/placement/drives', {
        ...drive,
        minCgpa: drive.minCgpa === '' ? null : Number(drive.minCgpa),
        maxBacklogs: drive.maxBacklogs === '' ? null : Number(drive.maxBacklogs),
      });
      setDrive(emptyDrive);
      setMessage('Placement drive created');
      await load();
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to create drive');
    }
  };

  const updateStatus = async (driveId, status) => {
    await api.patch(`/placement/drives/${driveId}/status`, null, { params: { status } });
    await load();
  };

  const viewRegistrations = async (driveId) => {
    try {
      const response = await api.get(`/placement/drives/${driveId}/registrations`);
      setActiveDriveId(driveId);
      setRegistrations(response.data);
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to load registrations');
    }
  };

  const verifyEligibility = async (driveId, studentId) => {
    try {
      await api.patch(`/placement/drives/${driveId}/registrations/${studentId}/eligibility`);
      await viewRegistrations(driveId);
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to verify eligibility');
    }
  };

  const selectStudent = async (driveId, studentId) => {
    const details = selectionDetails[studentId] || {};
    try {
      await api.post(`/placement/drives/${driveId}/selections`, {
        studentId,
        jobTitle: details.jobTitle,
        packageAmount: Number(details.packageAmount),
      });
      setMessage('Student selection recorded');
      await Promise.all([viewRegistrations(driveId), load()]);
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Unable to record selection');
    }
  };

  const importStudents = async () => {
    if (!selectedFile) return;
    const form = new FormData();
    form.append('file', selectedFile);
    try {
      const response = await api.post('/placement/students/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setMessage(`Imported ${response.data.imported}; skipped ${response.data.skipped}`);
      setSelectedFile(null);
      await load();
    } catch (error) {
      setMessage(error.response?.data?.detail || error.response?.data?.message || 'Student import failed');
    }
  };

  return (
    <main className="min-h-screen bg-slate-50 p-6">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Placement Officer Dashboard</h1>
          <p className="text-slate-500">{user?.email}</p>
        </div>
        <button onClick={logout} className="rounded-lg border px-4 py-2 text-sm font-semibold">Logout</button>
      </header>
      {message && <p className="mx-auto mt-4 max-w-6xl rounded-lg bg-indigo-50 p-3 text-sm text-indigo-700">{message}</p>}
      {loading ? <p className="mx-auto mt-8 max-w-6xl text-slate-500">Loading placement data...</p> : <>
      <section className="mx-auto mt-6 grid max-w-6xl gap-4 md:grid-cols-3">
        {[
          ['Active drives', stats?.activeDrives ?? 0],
          ['Registrations', stats?.registeredStudents ?? 0],
          ['Selections', stats?.selectedStudents ?? 0],
        ].map(([label, value]) => <article key={label} className="rounded-xl bg-white p-5 shadow-sm">
          <p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-3xl font-bold text-indigo-700">{value}</p>
        </article>)}
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Placement operations</h2>
        <form onSubmit={createDrive} className="mt-4 grid gap-3 md:grid-cols-2">
          {Object.entries(emptyDrive).map(([key]) => key === 'description'
            ? <textarea key={key} required={key === 'title'} className="rounded border p-2 md:col-span-2" placeholder="Description"
                name={key} value={drive[key]} onChange={(event) => setDrive({...drive, [key]: event.target.value})} />
            : <input key={key} className="rounded border p-2" type={key.includes('Date') || key === 'registrationDeadline' ? 'date' : key === 'minCgpa' ? 'number' : key === 'maxBacklogs' ? 'number' : 'text'}
                placeholder={key.replace(/([A-Z])/g, ' $1')} required={['title', 'registrationDeadline'].includes(key)}
                name={key} value={drive[key]} onChange={(event) => setDrive({...drive, [key]: event.target.value})} />)}
          <button className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white md:col-span-2">Create placement drive</button>
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Student CSV import</h2>
        <p className="mt-1 text-sm text-slate-500">Columns: email, fullName, rollNumber, department, cgpa, backlogs, resumeUrl(optional)</p>
        <div className="mt-4 flex gap-3">
          <input type="file" accept=".csv,text/csv" onChange={(event) => setSelectedFile(event.target.files[0])} />
          <button onClick={importStudents} disabled={!selectedFile} className="rounded bg-slate-800 px-4 py-2 text-white disabled:bg-slate-300">Import</button>
        </div>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white shadow-sm">
        <h2 className="p-5 text-xl font-semibold">Placement drives</h2>
        <div className="divide-y">
          {drives.length === 0 && <p className="p-5 text-slate-500">No placement drives have been created.</p>}
          {drives.map(item => <article key={item.id} className="flex flex-col gap-3 p-5 md:flex-row md:items-center md:justify-between">
            <div><h3 className="font-semibold">{item.title}</h3><p className="text-sm text-slate-500">{item.companyName || 'Company not specified'} - {item.registeredCount} registered</p></div>
            <div className="flex flex-wrap gap-2">
              <button onClick={() => viewRegistrations(item.id)} className="rounded border px-3 py-2">Registrations</button>
              <select aria-label={`Status for ${item.title}`} className="rounded border p-2" value={item.status} onChange={(event) => updateStatus(item.id, event.target.value)}>
                {['DRAFT','OPEN','CLOSED','COMPLETED','CANCELLED'].map(status => <option key={status}>{status}</option>)}
              </select>
            </div>
          </article>)}
        </div>
      </section>
      {activeDriveId !== null && <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Drive registrations</h2>
        {registrations.length === 0 && <p className="mt-3 text-slate-500">No registrations for this drive.</p>}
        <div className="mt-3 divide-y">
          {registrations.map(registration => {
            const studentId = registration.studentId;
            return <article key={registration.id} className="flex flex-col gap-3 py-4 md:flex-row md:items-center md:justify-between">
              <div><p className="font-medium">{registration.studentName || registration.studentEmail || `Student ${studentId}`}</p>
                <p className="text-sm text-slate-500">{registration.studentEmail}</p>
                <p className="text-sm text-slate-500">Status: {registration.status}</p></div>
              <div className="flex flex-wrap items-center gap-2">
                <button onClick={() => verifyEligibility(activeDriveId, studentId)} className="rounded border px-3 py-2">Verify eligibility</button>
                <input aria-label={`Job title for student ${studentId}`} className="w-40 rounded border p-2" placeholder="Job title"
                  value={selectionDetails[studentId]?.jobTitle || ''}
                  onChange={event => setSelectionDetails({...selectionDetails, [studentId]: {...selectionDetails[studentId], jobTitle: event.target.value}})} />
                <input aria-label={`Package for student ${studentId}`} type="number" min="0.01" step="0.01" className="w-32 rounded border p-2" placeholder="Package"
                  value={selectionDetails[studentId]?.packageAmount || ''}
                  onChange={event => setSelectionDetails({...selectionDetails, [studentId]: {...selectionDetails[studentId], packageAmount: event.target.value}})} />
                <button disabled={registration.status !== 'ELIGIBLE' || !selectionDetails[studentId]?.jobTitle || !selectionDetails[studentId]?.packageAmount}
                  onClick={() => selectStudent(activeDriveId, studentId)} className="rounded bg-indigo-600 px-3 py-2 text-white disabled:bg-slate-300">Record selection</button>
              </div>
            </article>;
          })}
        </div>
      </section>}
      </>}
    </main>
  );
}
