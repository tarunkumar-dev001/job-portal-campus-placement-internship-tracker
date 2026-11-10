import { useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import api from '../services/api';

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

  const submit = async (event) => {
    event.preventDefault();
    setError(''); setMessage('');
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
      setError(requestError.response?.data?.detail || 'Request failed');
    }
  };

  const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2';
  return <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
    <form onSubmit={submit} className="w-full max-w-md rounded-xl bg-white p-6 shadow-lg">
      <h1 className="text-2xl font-bold">{mode === 'forgot' ? 'Forgot password' : mode === 'verify' ? 'Verify email' : 'Reset password'}</h1>
      {message && <p className="mt-4 rounded bg-green-50 p-3 text-sm text-green-700">{message}</p>}
      {error && <p className="mt-4 rounded bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {mode === 'forgot' && <label className="mt-4 block text-sm">Email<input required type="email" className={inputClass} value={email} onChange={e => setEmail(e.target.value)} /></label>}
      {mode === 'verify' && !token && <label className="mt-4 block text-sm">Email<input required type="email" className={inputClass} value={email} onChange={e => setEmail(e.target.value)} /></label>}
      {(mode === 'verify' || mode === 'reset') && (mode === 'reset' || token) && <label className="mt-4 block text-sm">Token<input required className={inputClass} value={token} onChange={e => setToken(e.target.value)} /></label>}
      {mode === 'reset' && <label className="mt-4 block text-sm">New password<input required minLength="12" maxLength="72" type="password" className={inputClass} value={password} onChange={e => setPassword(e.target.value)} /></label>}
      <button className="mt-6 w-full rounded-lg bg-indigo-600 py-2.5 font-semibold text-white">
        {mode === 'forgot' ? 'Send reset link' : mode === 'verify' ? (token ? 'Verify email' : 'Resend verification email') : 'Reset password'}
      </button>
    </form>
  </main>;
}
