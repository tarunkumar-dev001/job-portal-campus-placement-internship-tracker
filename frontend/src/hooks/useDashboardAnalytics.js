import { useCallback, useEffect, useRef, useState } from 'react';

export default function useDashboardAnalytics(fetchStats, errorMessage) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const requestId = useRef(0);
  const controller = useRef(null);

  const refresh = useCallback(async () => {
    const currentRequest = ++requestId.current;
    controller.current?.abort();
    const nextController = new AbortController();
    controller.current = nextController;
    setLoading(true);
    setError('');

    try {
      const response = await fetchStats(nextController.signal);
      if (currentRequest === requestId.current) setStats(response.data);
    } catch (requestError) {
      if (requestError.name !== 'CanceledError' && requestError.name !== 'AbortError'
          && currentRequest === requestId.current) {
        setError(requestError.response?.data?.detail || requestError.response?.data?.message || errorMessage);
      }
    } finally {
      if (currentRequest === requestId.current) setLoading(false);
    }
  }, [errorMessage, fetchStats]);

  useEffect(() => {
    refresh();
    return () => {
      requestId.current += 1;
      controller.current?.abort();
    };
  }, [refresh]);

  return { stats, loading, error, refresh };
}
