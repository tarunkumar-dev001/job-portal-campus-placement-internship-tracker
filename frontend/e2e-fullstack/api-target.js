export const FULLSTACK_API_BASE_URL = 'http://127.0.0.1:8080/api';

export function configureFullstackApiTarget(environment) {
  const inheritedApiBaseURL = environment.VITE_API_BASE_URL;
  if (inheritedApiBaseURL !== undefined && inheritedApiBaseURL !== FULLSTACK_API_BASE_URL) {
    throw new Error(
      'Full-stack E2E refuses an inherited VITE_API_BASE_URL other than its local backend.',
    );
  }

  environment.VITE_API_BASE_URL = FULLSTACK_API_BASE_URL;
  return FULLSTACK_API_BASE_URL;
}
