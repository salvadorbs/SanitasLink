import type { ReactNode } from 'react';

import { Navigate } from 'react-router-dom';

import { PageLoader } from '@/components/PageLoader';
import { useAuth } from '@/features/auth/useAuth';

/** Keeps already-authenticated users away from public pages (e.g. /login). */
export function PublicRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();

  if (status === 'loading') {
    return <PageLoader />;
  }
  if (status === 'authenticated') {
    return <Navigate to="/app" replace />;
  }
  return children;
}
