import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { queryClient } from '@/lib/queryClient';
import { server } from '@/test/server';

import { ProtectedRoute } from './ProtectedRoute';

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="path">{location.pathname}</span>;
}

function renderProtected(children: React.ReactNode) {
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/app']}>
          <Routes>
            <Route
              path="/app"
              element={
                <ProtectedRoute>
                  <div>
                    <LocationProbe />
                    {children}
                  </div>
                </ProtectedRoute>
              }
            />
            <Route path="/login" element={<LocationProbe />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('ProtectedRoute', () => {
  it('shows a loader while the session is being resolved and never renders the page', async () => {
    server.use(
      // A never-resolving refresh keeps the session in the loading state for the whole test.
      http.post('*/api/v1/auth/refresh', () => new Promise<never>(() => {})),
    );

    renderProtected(<div data-testid="protected-content">top secret</div>);

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
    expect(screen.queryByTestId('path')).not.toBeInTheDocument();
  });

  it('redirects unauthenticated users to /login', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })));

    renderProtected(<div data-testid="protected-content">top secret</div>);

    await waitFor(() => expect(screen.getByTestId('path')).toHaveTextContent('/login'));
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
  });

  it('renders the protected page once the session is authenticated', async () => {
    renderProtected(<div data-testid="protected-content">top secret</div>);

    await waitFor(() => expect(screen.getByTestId('protected-content')).toHaveTextContent('top secret'));
    expect(screen.getByTestId('path')).toHaveTextContent('/app');
  });
});
