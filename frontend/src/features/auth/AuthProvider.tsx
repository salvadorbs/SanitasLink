import { type ReactNode, useEffect, useRef } from 'react';

import { useAuthStore } from './authStore';

/**
 * Bootstraps the session once on application start: the refresh cookie is exchanged for an
 * access token in memory, then the profile is loaded from /me.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const bootstrap = useAuthStore((state) => state.bootstrap);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;
    void bootstrap();
  }, [bootstrap]);

  return children;
}
