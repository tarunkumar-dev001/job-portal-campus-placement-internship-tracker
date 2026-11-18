import { useEffect, useRef, useState } from 'react';

function getErrorMessage(error, fallback) {
  const detail = error?.response?.data?.detail;
  const message = error?.response?.data?.message;
  return (typeof detail === 'string' && detail.trim())
    || (typeof message === 'string' && message.trim())
    || fallback;
}

export default function useMutationFeedback() {
  const [pending, setPending] = useState({});
  const [feedback, setFeedback] = useState(null);
  const locks = useRef(new Set());
  const mounted = useRef(false);
  const feedbackSequence = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      locks.current.clear();
    };
  }, []);

  const runMutation = async (key, operation, successMessage, errorMessage) => {
    if (locks.current.has(key)) {
      return { ok: false, duplicate: true, mounted: mounted.current };
    }

    locks.current.add(key);
    const sequence = ++feedbackSequence.current;
    if (mounted.current) {
      setPending(current => ({ ...current, [key]: true }));
      setFeedback(null);
    }

    try {
      const result = await operation();
      if (mounted.current && feedbackSequence.current === sequence) {
        setFeedback({
          type: 'success',
          message: typeof successMessage === 'function' ? successMessage(result) : successMessage,
        });
      }
      return { ok: true, result, mounted: mounted.current };
    } catch (error) {
      if (mounted.current && feedbackSequence.current === sequence) {
        setFeedback({ type: 'error', message: getErrorMessage(error, errorMessage) });
      }
      return { ok: false, error, mounted: mounted.current };
    } finally {
      locks.current.delete(key);
      if (mounted.current) {
        setPending(current => {
          const next = { ...current };
          delete next[key];
          return next;
        });
      }
    }
  };

  return {
    pending,
    feedback,
    runMutation,
    isPending: key => Boolean(pending[key]),
    hasPending: Object.values(pending).some(Boolean),
  };
}
