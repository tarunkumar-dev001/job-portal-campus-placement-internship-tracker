import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [role, setRole] = useState(() => localStorage.getItem('role'));
  const [user, setUser] = useState(() => {
    const email = localStorage.getItem('email');
    return email ? { email } : null;
  });

  useEffect(() => {
    const syncSession = () => {
      setToken(localStorage.getItem('token'));
      setRole(localStorage.getItem('role'));
      const email = localStorage.getItem('email');
      setUser(email ? { email } : null);
    };
    window.addEventListener('careerconnect:session-updated', syncSession);
    window.addEventListener('careerconnect:session-expired', syncSession);
    return () => {
      window.removeEventListener('careerconnect:session-updated', syncSession);
      window.removeEventListener('careerconnect:session-expired', syncSession);
    };
  }, []);

  const persistSession = (response) => {
    localStorage.setItem('token', response.token);
    localStorage.setItem('role', response.role);
    localStorage.setItem('email', response.email);
    if (response.refreshToken) localStorage.setItem('refreshToken', response.refreshToken);
    setToken(response.token);
    setRole(response.role);
    setUser({ email: response.email });
  };

  const login = async (credentials) => {
    const { data } = await api.post('/auth/login', credentials);
    persistSession(data);
    return data;
  };

  const register = async (details) => {
    const { data } = await api.post('/auth/register', details);
    return data;
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      try { await api.post('/auth/logout', { refreshToken }); } catch { /* local logout remains safe */ }
    }
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('email');
    localStorage.removeItem('refreshToken');
    setToken(null);
    setRole(null);
    setUser(null);
    window.dispatchEvent(new Event('careerconnect:session-expired'));
  };

  const value = useMemo(
    () => ({ token, role, user, isAuthenticated: Boolean(token), login, register, logout }),
    [token, role, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
