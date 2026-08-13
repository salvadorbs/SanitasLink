import { Navigate } from 'react-router-dom';

import { PageLoader } from '@/components/PageLoader';
import { useAuth } from '@/features/auth/useAuth';

/** Sends anonymous visitors to /login and authenticated ones to /app. */
export function RootRedirect() {
  const { status } = useAuth();
  if (status === 'loading') {
    return <PageLoader />;
  }
  return <Navigate to={status === 'authenticated' ? '/app' : '/login'} replace />;
}
