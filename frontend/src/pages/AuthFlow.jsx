import { useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import api from '../services/api';
import FormField, { clearFieldError, FormErrorSummary, getServerFieldErrors, validateForm } from '../components/FormField';

export default function AuthFlow() {
  const location = useLocation();
  const [params] = useSearchParams();
  const mode = location.pathname.endsWith('forgot-password') ? 'forgot'
    : location.pathname.endsWith('verify-email') ? 'verify' : 'reset';
  const [email, setEmail] = useState(params.get('email') || '');
  const [token, setToken] = useState(params.get('token') || '');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState(location.state?.message || '');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (!validateForm(event.currentTarget, setFieldErrors)) return;
    setError(''); setMessage('');
    setSubmitting(true);
    try {
      const path = mode === 'forgot' ? '/auth/password/forgot'
        : mode === 'verify' && !token ? '/auth/verification/request'
        : mode === 'verify' ? '/auth/verification/verify'
        : '/auth/password/reset';
      const body = mode === 'forgot' || (mode === 'verify' && !token) ? { email }
        : mode === 'verify' ? { token }
        : { token, password };
      const { data } = await api.post(path, body);
      setMessage(data.message);
    } catch (requestError) {
      setFieldErrors(getServerFieldErrors(requestError));
      setError(requestError.response?.data?.detail || requestError.response?.data?.message || 'Request failed');
    } finally {
      setSubmitting(false);
    }
  };

  const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2';
  return <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
    <form noValidate aria-busy={submitting} onSubmit={submit} className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 shadow-lg">
      <h1 className="text-2xl font-bold">{mode === 'forgot' ? 'Forgot password' : mode === 'verify' ? 'Verify email' : 'Reset password'}</h1>
      <FormErrorSummary errors={fieldErrors} />
      {message && <p role="status" aria-live="polite" className="rounded bg-green-50 p-3 text-sm text-green-700">{message}</p>}
      {error && <p role="alert" aria-live="assertive" className="rounded bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {mode === 'forgot' && <FormField id="auth-flow-email" label="Email" required error={fieldErrors.email}>
        <input name="email" required type="email" autoComplete="email" className={inputClass} value={email}
          onChange={e => { setEmail(e.target.value); clearFieldError(setFieldErrors, 'email'); setError(''); }} />
      </FormField>}
      {mode === 'verify' && !token && <FormField id="auth-flow-email" label="Email" required error={fieldErrors.email}>
        <input name="email" required type="email" autoComplete="email" className={inputClass} value={email}
          onChange={e => { setEmail(e.target.value); clearFieldError(setFieldErrors, 'email'); setError(''); }} />
      </FormField>}
      {(mode === 'verify' || mode === 'reset') && (mode === 'reset' || token) && (
        <FormField id="auth-flow-token" label="Verification or reset token" required error={fieldErrors.token}>
          <input name="token" required autoComplete="one-time-code" className={inputClass} value={token}
            onChange={e => { setToken(e.target.value); clearFieldError(setFieldErrors, 'token'); setError(''); }} />
        </FormField>
      )}
      {mode === 'reset' && <FormField id="auth-flow-password" label="New password" required error={fieldErrors.password}>
        <input name="password" required minLength="12" maxLength="72" type="password" autoComplete="new-password"
          className={inputClass} value={password}
          onChange={e => { setPassword(e.target.value); clearFieldError(setFieldErrors, 'password'); setError(''); }} />
      </FormField>}
      <button disabled={submitting} className="w-full rounded-lg bg-indigo-600 py-2.5 font-semibold text-white disabled:cursor-wait disabled:opacity-60">
        {submitting ? 'Submitting…'
          : mode === 'forgot' ? 'Send reset link' : mode === 'verify' ? (token ? 'Verify email' : 'Resend verification email') : 'Reset password'}
      </button>
    </form>
  </main>;
}
