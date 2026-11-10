import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({
    email: '', password: '', role: 'STUDENT', fullName: '',
    rollNumber: '', department: '', cgpa: '', backlogs: '',
  });
  const [error, setError] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    setError('');
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
      setError(requestError.response?.data?.detail
        || requestError.response?.data?.message
        || 'Authentication failed');
    }
  };

  const change = (event) => setForm({ ...form, [event.target.name]: event.target.value });
  const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2';

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <form onSubmit={submit} className="w-full max-w-md rounded-xl bg-white p-6 shadow-lg">
        <h1 className="text-2xl font-bold text-slate-900">
          {mode === 'login' ? 'Sign in' : 'Create account'}
        </h1>
        {error && <p className="mt-4 rounded bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        <label className="mt-4 block text-sm font-medium">Email
          <input className={inputClass} name="email" type="email" required value={form.email} onChange={change} />
        </label>
        <label className="mt-4 block text-sm font-medium">Password
          <input className={inputClass} name="password" type="password" required
            minLength={mode === 'register' ? 12 : undefined}
            maxLength={mode === 'register' ? 72 : undefined}
            value={form.password} onChange={change} />
        </label>
        {mode === 'register' && (
          <>
            <label className="mt-4 block text-sm font-medium">Role
              <select className={inputClass} name="role" value={form.role} onChange={change}>
                <option value="STUDENT">Student</option>
                <option value="RECRUITER">Recruiter</option>
                <option value="PLACEMENT_OFFICER">Placement Officer</option>
              </select>
            </label>
            {form.role === 'STUDENT' && <>
              <label className="mt-4 block text-sm font-medium">Full name
                <input className={inputClass} name="fullName" required value={form.fullName} onChange={change} />
              </label>
              <label className="mt-4 block text-sm font-medium">Roll number
                <input className={inputClass} name="rollNumber" required value={form.rollNumber} onChange={change} />
              </label>
              <label className="mt-4 block text-sm font-medium">Department
                <input className={inputClass} name="department" required value={form.department} onChange={change} />
              </label>
              <div className="grid grid-cols-2 gap-4">
                <label className="mt-4 block text-sm font-medium">CGPA
                  <input className={inputClass} name="cgpa" type="number" step="0.01" required value={form.cgpa} onChange={change} />
                </label>
                <label className="mt-4 block text-sm font-medium">Backlogs
                  <input className={inputClass} name="backlogs" type="number" min="0" required value={form.backlogs} onChange={change} />
                </label>
              </div>
            </>}
          </>
        )}
        <button className="mt-6 w-full rounded-lg bg-indigo-600 py-2.5 font-semibold text-white hover:bg-indigo-700">
          {mode === 'login' ? 'Sign in' : 'Register'}
        </button>
        <button type="button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
          className="mt-4 w-full text-sm text-indigo-700">
          {mode === 'login' ? 'Create an account' : 'Already have an account? Sign in'}
        </button>
      </form>
    </main>
  );
}
