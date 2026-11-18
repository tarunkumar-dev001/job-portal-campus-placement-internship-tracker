import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import useDashboardAnalytics from '../hooks/useDashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';
import {
  AnalyticsPanel, CountBreakdown, DataState, DepartmentComparison, MetricGrid, MutationFeedback,
  PlacementOutcomes, PlacementRate, StatusDistribution,
} from '../components/DashboardAnalytics';

const emptyDrive = {
  title: '', companyName: '', jobTitle: '', driveType: 'INTERNSHIP',
  description: '', minCgpa: '', maxBacklogs: '', registrationDeadline: '', driveDate: '',
};
const driveLabels = {
  title: 'Title',
  companyName: 'Company name',
  jobTitle: 'Job title',
  driveType: 'Drive type',
  description: 'Description',
  minCgpa: 'Minimum CGPA',
  maxBacklogs: 'Maximum backlogs',
  registrationDeadline: 'Registration deadline',
  driveDate: 'Drive date',
};

export default function PlacementDashboard() {
  const { logout, user } = useAuth();
  const fetchStats = useCallback((signal) => api.get('/placement/stats', { signal }), []);
  const { stats, loading: analyticsLoading, error: analyticsError, refresh: refreshAnalytics } =
    useDashboardAnalytics(fetchStats, 'Unable to load placement analytics. Please try again.');
  const { feedback, isPending, runMutation } = useMutationFeedback();
  const [drives, setDrives] = useState([]);
  const [drive, setDrive] = useState(emptyDrive);
  const [selectedFile, setSelectedFile] = useState(null);
  const [driveFieldErrors, setDriveFieldErrors] = useState({});
  const [importFieldErrors, setImportFieldErrors] = useState({});
  const [selectionFieldErrors, setSelectionFieldErrors] = useState({});
  const [activeDriveId, setActiveDriveId] = useState(null);
  const [registrations, setRegistrations] = useState([]);
  const [selectionDetails, setSelectionDetails] = useState({});
  const [drivesState, setDrivesState] = useState({ loading: true, error: '' });
  const [registrationsState, setRegistrationsState] = useState({ loading: false, error: '' });
  const drivesRequestId = useRef(0);
  const registrationsRequestId = useRef(0);
  const componentMounted = useRef(false);

  const load = async () => {
    const requestId = ++drivesRequestId.current;
    setDrivesState({ loading: true, error: '' });
    try {
      const driveResponse = await api.get('/placement/drives');
      if (componentMounted.current && requestId === drivesRequestId.current) {
        setDrives(driveResponse.data || []);
        setDrivesState({ loading: false, error: '' });
      }
    } catch (requestError) {
      if (componentMounted.current && requestId === drivesRequestId.current) {
        setDrivesState({
          loading: false,
          error: requestError.response?.data?.detail
            || requestError.response?.data?.message || 'Unable to load placement drives.',
        });
      }
    }
  };

  useEffect(() => {
    componentMounted.current = true;
    load();
    return () => {
      componentMounted.current = false;
      drivesRequestId.current += 1;
      registrationsRequestId.current += 1;
    };
  }, []);

  const createDrive = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setDriveFieldErrors)) return;
    const result = await runMutation('drive:create', () => api.post('/placement/drives', {
        ...drive,
        minCgpa: drive.minCgpa === '' ? null : Number(drive.minCgpa),
        maxBacklogs: drive.maxBacklogs === '' ? null : Number(drive.maxBacklogs),
      }), 'Placement drive created.', 'Unable to create placement drive.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setDriveFieldErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    setDrive(emptyDrive);
    await Promise.all([load(), refreshAnalytics()]);
  };

  const updateStatus = async (driveId, status) => {
    const result = await runMutation(`drive:${driveId}:status`,
      () => api.patch(`/placement/drives/${driveId}/status`, null, { params: { status } }),
      'Placement drive status updated.', 'Unable to update placement drive status.');
    if (result.ok && result.mounted) await Promise.all([load(), refreshAnalytics()]);
  };

  const viewRegistrations = async (driveId) => {
    const requestId = ++registrationsRequestId.current;
    setActiveDriveId(driveId);
    setRegistrations([]);
    setRegistrationsState({ loading: true, error: '' });
    try {
      const response = await api.get(`/placement/drives/${driveId}/registrations`);
      if (componentMounted.current && requestId === registrationsRequestId.current) {
        setRegistrations(response.data || []);
        setRegistrationsState({ loading: false, error: '' });
      }
    } catch (requestError) {
      if (componentMounted.current && requestId === registrationsRequestId.current) {
        setRegistrationsState({
          loading: false,
          error: requestError.response?.data?.detail
            || requestError.response?.data?.message || 'Unable to load registrations.',
        });
      }
    }
  };

  const verifyEligibility = async (driveId, studentId) => {
    const result = await runMutation(`registration:${driveId}:${studentId}:eligibility`,
      () => api.patch(`/placement/drives/${driveId}/registrations/${studentId}/eligibility`),
      'Student eligibility verified.', 'Unable to verify student eligibility.');
    if (result.ok && result.mounted) await viewRegistrations(driveId);
  };

  const selectStudent = async (event, driveId, studentId) => {
    event.preventDefault();
    const setStudentErrors = updater => setSelectionFieldErrors(current => ({
      ...current,
      [studentId]: typeof updater === 'function' ? updater(current[studentId] || {}) : updater,
    }));
    if (!validateForm(event.currentTarget, setStudentErrors)) return;
    const details = selectionDetails[studentId] || {};
    const result = await runMutation(`selection:${driveId}:${studentId}`,
      () => api.post(`/placement/drives/${driveId}/selections`, {
        studentId,
        jobTitle: details.jobTitle,
        packageAmount: Number(details.packageAmount),
      }), 'Student selection recorded.', 'Unable to record student selection.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setStudentErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    await Promise.all([viewRegistrations(driveId), load(), refreshAnalytics()]);
  };

  const importStudents = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setImportFieldErrors)) return;
    if (!selectedFile) return;
    if (!selectedFile.name.toLowerCase().endsWith('.csv')) {
      setImportFieldErrors({ file: 'Choose a CSV file.', _form: 'Please correct the highlighted fields and try again.' });
      return;
    }
    const form = new FormData();
    form.append('file', selectedFile);
    const result = await runMutation('students:import',
      () => api.post('/placement/students/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }), response => `Imported ${response.data.imported}; skipped ${response.data.skipped}.`,
      'Student import failed.');
    if (!result.ok) {
      const serverErrors = getServerFieldErrors(result.error);
      if (Object.keys(serverErrors).length) setImportFieldErrors(serverErrors);
    }
    if (!result.ok || !result.mounted) return;
    setSelectedFile(null);
    await Promise.all([load(), refreshAnalytics()]);
  };

  return (
    <main className="min-h-screen bg-slate-50 p-6">
      <header className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-3xl font-bold text-slate-900">Placement Officer Dashboard</h1>
          <p className="break-words text-slate-500">{user?.email}</p>
        </div>
        <button onClick={logout} className="min-h-11 shrink-0 rounded-lg border px-4 py-2 text-sm font-semibold">Logout</button>
      </header>
      <MutationFeedback feedback={feedback} />
      <AnalyticsPanel loading={analyticsLoading} error={analyticsError} onRefresh={refreshAnalytics} hasData={stats !== null}>
        {stats && <div className="space-y-4">
          <MetricGrid metrics={[
            { label: 'Active drives', value: stats.activeDrives },
            { label: 'Registrations', value: stats.registeredStudents },
            { label: 'Selections', value: stats.selectedStudents },
            { label: 'Total students', value: stats.totalStudents },
            { label: 'Total drives', value: stats.totalDrives },
            { label: 'Applications', value: stats.totalApplications },
            { label: 'Drive registrations', value: stats.totalDriveRegistrations },
            { label: 'Rejected applications', value: stats.rejectedApplications },
          ]} />
          <div className="grid gap-4 lg:grid-cols-2">
            <PlacementOutcomes placed={stats.uniqueStudentsPlaced} shortlisted={stats.uniqueStudentsShortlisted} unplaced={stats.unplacedStudents} />
            <PlacementRate percentage={stats.placementPercentage} placed={stats.uniqueStudentsPlaced} students={stats.totalStudents} />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <StatusDistribution title="Application status distribution" counts={stats.applicationsByStatus} />
            <DepartmentComparison studentsByDepartment={stats.studentsByDepartment} selectionsByDepartment={stats.selectionsByDepartment} />
          </div>
          {Object.values(stats.rejectionReasons || {}).some(value => typeof value === 'number' && Number.isFinite(value) && value > 0)
            && <CountBreakdown title="Recorded rejection reasons" counts={stats.rejectionReasons} />}
        </div>}
      </AnalyticsPanel>
      <>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Placement operations</h2>
        <form noValidate aria-busy={isPending('drive:create')} onSubmit={createDrive} className="mt-4 grid gap-3 md:grid-cols-2">
          <FormErrorSummary errors={driveFieldErrors} />
          {Object.entries(emptyDrive).map(([key]) => {
            const label = driveLabels[key];
            const required = ['title', 'registrationDeadline'].includes(key);
            const control = key === 'description'
              ? <textarea className="w-full rounded border p-2" name={key} value={drive[key]} rows={3}
                  onChange={(event) => { setDrive({...drive, [key]: event.target.value}); clearFieldError(setDriveFieldErrors, key); }} />
              : <input className="w-full rounded border p-2"
                  type={key.includes('Date') || key === 'registrationDeadline' ? 'date'
                    : ['minCgpa', 'maxBacklogs'].includes(key) ? 'number' : 'text'}
                  min={key === 'minCgpa' ? 0 : key === 'maxBacklogs' ? 0 : undefined}
                  max={key === 'minCgpa' ? 10 : undefined}
                  step={key === 'minCgpa' ? '0.01' : key === 'maxBacklogs' ? '1' : undefined}
                  name={key} value={drive[key]}
                  onChange={(event) => { setDrive({...drive, [key]: event.target.value}); clearFieldError(setDriveFieldErrors, key); }} />;
            return <FormField key={key} id={`drive-${key}`} label={label} required={required}
              error={driveFieldErrors[key]} className={key === 'description' ? 'md:col-span-2' : ''}>
              {control}
            </FormField>;
          })}
          <button aria-live="polite" disabled={isPending('drive:create')}
            className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white disabled:cursor-wait disabled:opacity-60 md:col-span-2">
            {isPending('drive:create') ? 'Creating drive…' : 'Create placement drive'}
          </button>
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Student CSV import</h2>
        <p id="student-csv-columns" className="mt-1 text-sm text-slate-500">Columns: email, fullName, rollNumber, department, cgpa, backlogs, resumeUrl(optional)</p>
        <form noValidate aria-busy={isPending('students:import')} onSubmit={importStudents} className="mt-4 flex flex-wrap items-end gap-3">
          <FormErrorSummary errors={importFieldErrors} className="w-full" />
          <FormField id="students-csv" label="Student CSV file" required error={importFieldErrors.file}>
            <input name="file" type="file" accept=".csv,text/csv" aria-describedby="student-csv-columns" className="w-full rounded border p-2"
              onChange={(event) => { setSelectedFile(event.target.files[0] || null); clearFieldError(setImportFieldErrors, 'file'); }} />
          </FormField>
          <button aria-live="polite" type="submit" disabled={isPending('students:import')}
            className="rounded bg-slate-800 px-4 py-2 text-white disabled:bg-slate-300">
            {isPending('students:import') ? 'Importing…' : 'Import'}
          </button>
        </form>
      </section>
      <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white shadow-sm">
        <h2 className="p-5 text-xl font-semibold">Placement drives</h2>
        <div className="px-5">
          <DataState {...drivesState} onRetry={load} isEmpty={drives.length === 0}
            loadingMessage="Loading placement drives…" emptyMessage="No placement drives have been created." />
        </div>
        <div className="divide-y">
          {drives.map(item => <article key={item.id} className="flex flex-col gap-3 p-5 md:flex-row md:items-center md:justify-between">
            <div><h3 className="font-semibold">{item.title}</h3><p className="text-sm text-slate-500">{item.companyName || 'Company not specified'} - {item.registeredCount} registered</p></div>
            <div className="flex flex-wrap gap-2">
              <button disabled={activeDriveId === item.id && registrationsState.loading}
                onClick={() => viewRegistrations(item.id)} className="rounded border px-3 py-2 disabled:cursor-wait disabled:opacity-60">
                {activeDriveId === item.id && registrationsState.loading ? 'Loading…' : 'Registrations'}
              </button>
              <FormField id={`drive-status-${item.id}`} label="Drive status" required>
                <select name="status" aria-busy={isPending(`drive:${item.id}:status`)} disabled={isPending(`drive:${item.id}:status`)}
                  className="rounded border p-2 disabled:cursor-wait disabled:opacity-60"
                  value={item.status} onChange={(event) => updateStatus(item.id, event.target.value)}>
                  {['DRAFT','OPEN','CLOSED','COMPLETED','CANCELLED'].map(status => <option key={status}>{status}</option>)}
                </select>
              </FormField>
              {isPending(`drive:${item.id}:status`)
                && <span role="status" className="text-sm text-slate-600">Updating status…</span>}
            </div>
          </article>)}
        </div>
      </section>
      {activeDriveId !== null && <section className="mx-auto mt-6 max-w-6xl rounded-xl bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Drive registrations</h2>
        <DataState {...registrationsState} onRetry={() => viewRegistrations(activeDriveId)}
          isEmpty={registrations.length === 0} loadingMessage="Loading registrations…"
          emptyMessage="No registrations for this drive." />
        <div className="mt-3 divide-y">
          {registrations.map(registration => {
            const studentId = registration.studentId;
            return <article key={registration.id} className="flex flex-col gap-3 py-4 md:flex-row md:items-center md:justify-between">
              <div><p className="font-medium">{registration.studentName || registration.studentEmail || `Student ${studentId}`}</p>
                <p className="text-sm text-slate-500">{registration.studentEmail}</p>
                <p className="text-sm text-slate-500">Status: {registration.status}</p></div>
              <div className="flex flex-wrap items-center gap-2">
                <button type="button" aria-live="polite" disabled={isPending(`registration:${activeDriveId}:${studentId}:eligibility`)}
                  onClick={() => verifyEligibility(activeDriveId, studentId)}
                  className="rounded border px-3 py-2 disabled:cursor-wait disabled:opacity-60">
                  {isPending(`registration:${activeDriveId}:${studentId}:eligibility`) ? 'Verifying…' : 'Verify eligibility'}
                </button>
                <form noValidate aria-busy={isPending(`selection:${activeDriveId}:${studentId}`)}
                  onSubmit={event => selectStudent(event, activeDriveId, studentId)} className="flex flex-wrap items-end gap-2">
                  <FormErrorSummary errors={selectionFieldErrors[studentId]} className="w-full" />
                  <FormField id={`selection-job-${studentId}`} label="Job title" required
                    error={selectionFieldErrors[studentId]?.jobTitle} className="w-40">
                    <input name="jobTitle" className="w-full rounded border p-2"
                      value={selectionDetails[studentId]?.jobTitle || ''}
                      onChange={event => {
                        setSelectionDetails({...selectionDetails, [studentId]: {...selectionDetails[studentId], jobTitle: event.target.value}});
                        setSelectionFieldErrors(current => {
                          const errors = { ...(current[studentId] || {}) };
                          delete errors.jobTitle;
                          delete errors._form;
                          return { ...current, [studentId]: errors };
                        });
                      }} />
                  </FormField>
                  <FormField id={`selection-package-${studentId}`} label="Package" required
                    error={selectionFieldErrors[studentId]?.packageAmount} className="w-32">
                    <input name="packageAmount" type="number" min="0.01" step="0.01" className="w-full rounded border p-2"
                      value={selectionDetails[studentId]?.packageAmount || ''}
                      onChange={event => {
                        setSelectionDetails({...selectionDetails, [studentId]: {...selectionDetails[studentId], packageAmount: event.target.value}});
                        setSelectionFieldErrors(current => {
                          const errors = { ...(current[studentId] || {}) };
                          delete errors.packageAmount;
                          delete errors._form;
                          return { ...current, [studentId]: errors };
                        });
                      }} />
                  </FormField>
                  <button aria-live="polite" type="submit"
                    disabled={registration.status !== 'ELIGIBLE' || isPending(`selection:${activeDriveId}:${studentId}`)}
                    className="rounded bg-indigo-600 px-3 py-2 text-white disabled:bg-slate-300">
                    {isPending(`selection:${activeDriveId}:${studentId}`) ? 'Recording…' : 'Record selection'}
                  </button>
                </form>
              </div>
            </article>;
          })}
        </div>
      </section>}
      </>
    </main>
  );
}
