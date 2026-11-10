import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL
  || (import.meta.env.DEV ? 'http://localhost:8080/api' : '/api');

const api = axios.create({
  baseURL,
});

let refreshRequest;

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(undefined, async (error) => {
  const original = error.config;
  const authEndpoint = original?.url?.startsWith('/auth/');
  if (error.response?.status !== 401 || !original || original._retried || authEndpoint) {
    return Promise.reject(error);
  }

  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    clearSession();
    return Promise.reject(error);
  }

  original._retried = true;
  try {
    if (!refreshRequest) {
      refreshRequest = api.post('/auth/refresh', { refreshToken })
        .then(({ data }) => {
          localStorage.setItem('token', data.token);
          if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
          window.dispatchEvent(new Event('careerconnect:session-updated'));
          return data.token;
        })
        .finally(() => { refreshRequest = undefined; });
    }
    const token = await refreshRequest;
    original.headers.Authorization = `Bearer ${token}`;
    return api(original);
  } catch (refreshError) {
    clearSession();
    return Promise.reject(refreshError);
  }
});

function clearSession() {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('role');
  localStorage.removeItem('email');
  window.dispatchEvent(new Event('careerconnect:session-expired'));
}

export default api;
