import { useAuthStore } from './authStore';
import type { AuthContextValue } from './authTypes';

/** Current session state and auth actions. */
export function useAuth(): AuthContextValue {
  // Individual selectors keep stable references; an object-literal selector would
  // force an infinite re-render loop under useSyncExternalStore.
  const status = useAuthStore((state) => state.status);
  const profile = useAuthStore((state) => state.profile);
  const login = useAuthStore((state) => state.login);
  const logout = useAuthStore((state) => state.logout);
  return { status, profile, login, logout };
}
