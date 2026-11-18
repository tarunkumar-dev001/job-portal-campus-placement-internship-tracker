import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { DataState, MutationFeedback } from '../components/DashboardAnalytics';
import useMutationFeedback from '../hooks/useMutationFeedback';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';

function toLocalDateTime(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '';
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export default function RecruiterInterviews() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const { feedback: actionFeedback, hasPending, isPending, runMutation } = useMutationFeedback();
  const [editing, setEditing] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const requestId = useRef(0);
  const mounted = useRef(false);
  const load = async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      const { data } = await api.get('/recruiter/interviews');
      if (mounted.current && currentRequest === requestId.current) setRows(data || []);
    } catch (requestError) {
      if (mounted.current && currentRequest === requestId.current) {
        setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Unable to load interviews');
      }
    } finally {
      if (mounted.current && currentRequest === requestId.current) setLoading(false);
    }
  };
  useEffect(() => {
    mounted.current = true;
    load();
    return () => {
      mounted.current = false;
      requestId.current += 1;
    };
  }, []);
  const beginEdit = (row, type) => {
    setEditing({
      row,
      type,
      scheduledAt: toLocalDateTime(row.scheduledAt),
      feedback: '',
    });
    setFieldErrors({});
  };
  const changeField = (field, value) => {
    setEditing(current => ({ ...current, [field]: value }));
    clearFieldError(setFieldErrors, field);
  };
  const submitEdit = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setFieldErrors)) return;
    const { row, type, scheduledAt, feedback } = editing;
    setError('');
    const isReschedule = type === 'reschedule';
    const result = await runMutation(`interview:${row.id}`,
      () => isReschedule
        ? api.put(`/recruiter/applications/${row.applicationId}/interviews/${row.id}`, {
          scheduledAt: new Date(scheduledAt).toISOString(),
          mode: row.mode, locationOrLink: row.locationOrLink, notes: row.notes,
        })
        : api.post(`/recruiter/applications/${row.applicationId}/interviews/${row.id}/feedback`, { feedback }),
      isReschedule ? 'Interview rescheduled.' : 'Interview feedback saved.',
      isReschedule ? 'Unable to reschedule interview.' : 'Unable to save interview feedback.');
    if (!result.ok) setFieldErrors(getServerFieldErrors(result.error));
    if (result.ok && result.mounted) {
      setEditing(null);
      await load();
    }
  };
  const cancel = async (row) => {
    setError('');
    const result = await runMutation(`interview:${row.id}`,
      () => api.post(`/recruiter/applications/${row.applicationId}/interviews/${row.id}/cancel`),
      'Interview cancelled.', 'Unable to cancel interview.');
    if (result.ok && result.mounted) await load();
  };
  return <main className="min-h-screen bg-slate-50 p-6"><section className="mx-auto max-w-5xl rounded-xl bg-white p-5 shadow-sm">
    <nav className="mb-4 flex gap-2"><Link className="rounded border px-3 py-1" to="/recruiter">Dashboard</Link><Link className="rounded border px-3 py-1" to="/recruiter/offers">Offer management</Link></nav>
    <h1 className="text-2xl font-bold">Interview Management</h1>
    <MutationFeedback feedback={actionFeedback} />
    <DataState loading={loading} error={error} onRetry={load} isEmpty={rows.length === 0}
      loadingMessage="Loading interviews…" emptyMessage="No interviews scheduled." />
    <div className="mt-4 space-y-3">{rows.map(row => <article key={row.id} className="rounded border p-4">
      <p className="font-semibold">{new Date(row.scheduledAt).toLocaleString()}</p>
      <p className="text-sm text-slate-600">{row.mode} · {row.locationOrLink || 'No meeting details'} · {row.status}</p>
      {row.status !== 'CANCELLED' && row.status !== 'COMPLETED' && <div className="mt-3 flex gap-2">
        <button type="button" aria-live="polite" disabled={hasPending} className="rounded border px-3 py-1 disabled:opacity-50" onClick={() => beginEdit(row, 'reschedule')}>
          Reschedule
        </button>
        <button aria-live="polite" disabled={hasPending} className="rounded border px-3 py-1 text-red-700 disabled:opacity-50" onClick={() => cancel(row)}>
          {isPending(`interview:${row.id}`) ? 'Saving…' : 'Cancel'}
        </button>
        <button type="button" aria-live="polite" disabled={hasPending} className="rounded border px-3 py-1 disabled:opacity-50" onClick={() => beginEdit(row, 'feedback')}>
          Add feedback
        </button>
      </div>}
      {editing?.row.id === row.id && <form noValidate aria-busy={isPending(`interview:${row.id}`)}
        onSubmit={submitEdit} className="mt-4 max-w-xl space-y-3 rounded-lg border bg-slate-50 p-3">
        <h2 className="font-semibold">{editing.type === 'reschedule' ? 'Reschedule interview' : 'Add interview feedback'}</h2>
        <FormErrorSummary errors={fieldErrors} />
        {editing.type === 'reschedule'
          ? <FormField id={`interview-reschedule-${row.id}`} label="New interview date and time" required error={fieldErrors.scheduledAt}>
            <input name="scheduledAt" type="datetime-local" required className="w-full rounded border p-2"
              value={editing.scheduledAt} onChange={event => changeField('scheduledAt', event.target.value)} />
          </FormField>
          : <FormField id={`interview-feedback-${row.id}`} label="Interview feedback" required error={fieldErrors.feedback}>
            <textarea name="feedback" required rows={3} className="w-full rounded border p-2"
              value={editing.feedback} onChange={event => changeField('feedback', event.target.value)} />
          </FormField>}
        <div className="flex gap-2">
          <button type="submit" aria-live="polite" disabled={isPending(`interview:${row.id}`)}
            className="rounded bg-indigo-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60">
            {isPending(`interview:${row.id}`) ? 'Saving…' : editing.type === 'reschedule' ? 'Save schedule' : 'Save feedback'}
          </button>
          <button type="button" disabled={isPending(`interview:${row.id}`)} onClick={() => setEditing(null)}
            className="rounded border px-3 py-2 text-sm">Cancel</button>
        </div>
      </form>}
      {row.feedback && <p className="mt-2 text-sm text-slate-600">Feedback: {row.feedback}</p>}
    </article>)}</div>
  </section></main>;
}
