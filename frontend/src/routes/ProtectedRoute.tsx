import type { ReactNode } from 'react';

import { Navigate, useLocation } from 'react-router-dom';

import { PageLoader } from '@/components/PageLoader';
import { useAuth } from '@/features/auth/useAuth';

/** Redirects unauthenticated users to /login; waits for the session bootstrap to finish. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <PageLoader />;
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return children;
}
