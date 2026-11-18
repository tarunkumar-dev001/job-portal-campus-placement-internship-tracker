import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';

export default function Login() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({
    email: '', password: '', role: 'STUDENT', fullName: '',
    rollNumber: '', department: '', cgpa: '', backlogs: '',
  });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setFieldErrors)) return;
    setError('');
    setSubmitting(true);
    try {
      if (mode === 'register') {
        const result = await register({
            ...form,
            cgpa: form.cgpa === '' ? undefined : Number(form.cgpa),
            backlogs: form.backlogs === '' ? undefined : Number(form.backlogs),
          });
        navigate(`/verify-email?email=${encodeURIComponent(form.email.trim())}`, {
          state: { message: result.message },
        });
        return;
      }
      const result = await login({ email: form.email, password: form.password });
      navigate({
        STUDENT: '/student',
        RECRUITER: '/recruiter',
        PLACEMENT_OFFICER: '/placement',
        ADMIN: '/admin',
      }[result.role] || '/login');
    } catch (requestError) {
      setFieldErrors(getServerFieldErrors(requestError));
      setError(requestError.response?.data?.detail
        || requestError.response?.data?.message
        || 'Authentication failed');
    } finally {
      setSubmitting(false);
    }
  };

  const change = (event) => {
    setForm({ ...form, [event.target.name]: event.target.value });
    clearFieldError(setFieldErrors, event.target.name);
    setError('');
  };
  const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2';

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <form noValidate aria-busy={submitting} onSubmit={submit} className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 shadow-lg">
        <h1 className="text-2xl font-bold text-slate-900">
          {mode === 'login' ? 'Sign in' : 'Create account'}
        </h1>
        <FormErrorSummary errors={fieldErrors} />
        {error && <p role="alert" aria-live="assertive" className="rounded bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        <FormField id="auth-email" label="Email" required error={fieldErrors.email}>
          <input className={inputClass} name="email" type="email" autoComplete="email" value={form.email} onChange={change} />
        </FormField>
        <FormField id="auth-password" label="Password" required error={fieldErrors.password}>
          <input className={inputClass} name="password" type="password"
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            minLength={mode === 'register' ? 12 : undefined}
            maxLength={mode === 'register' ? 72 : undefined}
            value={form.password} onChange={change} />
        </FormField>
        {mode === 'register' && (
          <>
            <FormField id="auth-role" label="Role" required error={fieldErrors.role}>
              <select className={inputClass} name="role" value={form.role} onChange={change}>
                <option value="STUDENT">Student</option>
                <option value="RECRUITER">Recruiter</option>
              </select>
            </FormField>
            {form.role === 'STUDENT' && <>
              <FormField id="auth-full-name" label="Full name" required error={fieldErrors.fullName}>
                <input className={inputClass} name="fullName" autoComplete="name" value={form.fullName} onChange={change} />
              </FormField>
              <FormField id="auth-roll-number" label="Roll number" required error={fieldErrors.rollNumber}>
                <input className={inputClass} name="rollNumber" value={form.rollNumber} onChange={change} />
              </FormField>
              <FormField id="auth-department" label="Department" required error={fieldErrors.department}>
                <input className={inputClass} name="department" value={form.department} onChange={change} />
              </FormField>
              <div className="grid grid-cols-2 gap-4">
                <FormField id="auth-cgpa" label="CGPA" required error={fieldErrors.cgpa}>
                  <input className={inputClass} name="cgpa" type="number" min="0" max="10" step="0.01" value={form.cgpa} onChange={change} />
                </FormField>
                <FormField id="auth-backlogs" label="Backlogs" required error={fieldErrors.backlogs}>
                  <input className={inputClass} name="backlogs" type="number" min="0" step="1" value={form.backlogs} onChange={change} />
                </FormField>
              </div>
            </>}
          </>
        )}
        <button disabled={submitting} className="w-full rounded-lg bg-indigo-600 py-2.5 font-semibold text-white hover:bg-indigo-700 disabled:cursor-wait disabled:opacity-60">
          {submitting ? 'Submitting…' : mode === 'login' ? 'Sign in' : 'Register'}
        </button>
        <button type="button" onClick={() => {
          setMode(mode === 'login' ? 'register' : 'login');
          setFieldErrors({});
          setError('');
        }}
          className="mt-4 w-full text-sm text-indigo-700">
          {mode === 'login' ? 'Create an account' : 'Already have an account? Sign in'}
        </button>
      </form>
    </main>
  );
}
