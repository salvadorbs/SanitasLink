import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { tokenStore } from '@/api/tokenStore';
import { queryClient } from '@/lib/queryClient';
import { ME_PROFILE, REFRESHED_ACCESS_TOKEN } from '@/test/handlers';
import { server } from '@/test/server';

import { AuthProvider } from './AuthProvider';
import { useAuthStore } from './authStore';
import { useAuth } from './useAuth';

function Probe() {
  const { status, profile, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="profile-email">{profile?.email ?? 'none'}</span>
      <button type="button" onClick={() => void login('medico@studio.example', 'pass-123').catch(() => {})}>
        sign-in
      </button>
      <button type="button" onClick={() => void logout()}>
        sign-out
      </button>
    </div>
  );
}

function renderProbe() {
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('AuthProvider', () => {
  it('bootstraps the session via the refresh cookie and loads /me', async () => {
    renderProbe();

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('profile-email')).toHaveTextContent(ME_PROFILE.email);
    expect(tokenStore.get()).toBe(REFRESHED_ACCESS_TOKEN);
    expect(localStorage.getItem('sanitaslink.access_token')).toBeNull();
  });

  it('ends unauthenticated when there is no valid refresh cookie', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })));
    renderProbe();

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'));
    expect(screen.getByTestId('profile-email')).toHaveTextContent('none');
  });

  it('authenticates after login and stores the access token only in memory', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'));

    await user.click(screen.getByRole('button', { name: 'sign-in' }));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('profile-email')).toHaveTextContent(ME_PROFILE.email);
    expect(tokenStore.get()).not.toBeNull();
    expect(localStorage.getItem('sanitaslink.access_token')).toBeNull();
  });

  it('revokes the session on logout and clears the in-memory token', async () => {
    let logoutCalls = 0;
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        logoutCalls += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    await user.click(screen.getByRole('button', { name: 'sign-out' }));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'));
    expect(logoutCalls).toBe(1);
    expect(tokenStore.get()).toBeNull();
  });

  it('clears the local session even when logout revocation fails', async () => {
    server.use(http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 500 })));
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    await user.click(screen.getByRole('button', { name: 'sign-out' }));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'));
    expect(tokenStore.get()).toBeNull();
  });

  it('session expiry clears the token and every cached query', async () => {
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    // A query cached by the previous session must not survive for the next user.
    queryClient.setQueryData(['patients'], [{ id: 'patient-1' }]);
    useAuthStore.getState().handleSessionExpired();

    expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated');
    expect(tokenStore.get()).toBeNull();
    expect(queryClient.getQueryData(['patients'])).toBeUndefined();
  });

  it('a failed login that loads /me rejects and leaves no stale token behind', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
      http.get('*/api/v1/auth/me', () => new HttpResponse(null, { status: 500 })),
    );
    const user = userEvent.setup();
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'));

    await user.click(screen.getByRole('button', { name: 'sign-in' }));

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unauthenticated'));
    // The token obtained by login was discarded when /me failed.
    expect(tokenStore.get()).toBeNull();
  });
});
