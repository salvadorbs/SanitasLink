import { QueryClient } from '@tanstack/react-query';

/**
 * Shared query client. Retries are capped so stale 401 sessions do not cascade; the axios
 * mutator performs its own single refresh retry before any React Query retry runs.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
    mutations: {
      retry: 0,
    },
  },
});
