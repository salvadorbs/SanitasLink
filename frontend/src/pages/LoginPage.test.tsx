import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthProvider } from '@/features/auth/AuthProvider';
import { queryClient } from '@/lib/queryClient';
import { ME_PROFILE, REFRESHED_ACCESS_TOKEN } from '@/test/handlers';
import { server } from '@/test/server';

import { LoginPage } from './LoginPage';

function renderLogin() {
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/app" element={<div data-testid="app-page">area riservata</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  it('shows inline validation errors for an empty submit', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderLogin();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Accedi' })).toBeEnabled());

    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    expect(screen.getByText('Inserisci la tua email.')).toBeInTheDocument();
    expect(screen.getByText('Inserisci la tua password.')).toBeInTheDocument();
  });

  it('logs in, loads the profile and navigates to the protected area', async () => {
    let loginCalls = 0;
    let meCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
      http.post('*/api/v1/auth/login', () => {
        loginCalls += 1;
        return HttpResponse.json({
          accessToken: REFRESHED_ACCESS_TOKEN,
          expiresInSeconds: 900,
          tokenType: 'Bearer',
        });
      }),
      http.get('*/api/v1/auth/me', () => {
        meCalls += 1;
        return HttpResponse.json(ME_PROFILE);
      }),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText('Email'), 'medico@studio.example');
    await user.type(screen.getByLabelText('Password'), 'pass-123');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    await waitFor(() => expect(screen.getByTestId('app-page')).toBeInTheDocument());
    expect(loginCalls).toBe(1);
    expect(meCalls).toBe(1);
    expect(localStorage.getItem('sanitaslink.access_token')).toBeNull();
  });

  it('shows a uniform error and stays on /login for invalid credentials', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
      http.post('*/api/v1/auth/login', () => new HttpResponse(null, { status: 401 })),
    );
    const user = userEvent.setup();
    renderLogin();

    await user.type(screen.getByLabelText('Email'), 'medico@studio.example');
    await user.type(screen.getByLabelText('Password'), 'wrong-pass');
    await user.click(screen.getByRole('button', { name: 'Accedi' }));

    await waitFor(() =>
      expect(screen.getByText('Credenziali non valide. Verifica i dati inseriti e riprova.')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('app-page')).not.toBeInTheDocument();
  });

  it('is accessible: labels, live region and disabled submit while pending', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })));
    renderLogin();

    expect(screen.getByLabelText('Email')).toHaveAttribute('autocomplete', 'email');
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByRole('heading', { name: 'Accedi a SanitasLink' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveAttribute('aria-live', 'polite');
  });
});
