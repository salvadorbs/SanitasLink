import { useState } from 'react';

import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '@/features/auth/useAuth';

interface LoginFormValues {
  email: string;
  password: string;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [authError, setAuthError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    defaultValues: { email: '', password: '' },
  });

  if (status === 'authenticated') {
    return <Navigate to="/app" replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setAuthError(null);
    try {
      await login(values.email, values.password);
      const pendingPath = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      // Only internal destinations are ever trusted for the post-login redirect. Both a leading
      // slash and the absence of a protocol-relative prefix (//host) are required, otherwise a
      // crafted state like "//evil.example" would escape the SPA and open an external origin.
      const isInternal = pendingPath != null && pendingPath.startsWith('/') && !pendingPath.startsWith('//');
      void navigate(isInternal ? pendingPath : '/app', { replace: true });
    } catch {
      // Uniform, non-enumerative error (invalid credentials, blocked account, rate limit).
      setAuthError('Credenziali non valide. Verifica i dati inseriti e riprova.');
    }
  });

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="login-heading">
        <h1 id="login-heading">Accedi a SanitasLink</h1>
        <p className="auth-subtitle">Inserisci le credenziali del tuo studio.</p>
        <form className="auth-form" onSubmit={onSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              autoFocus
              aria-invalid={errors.email ? 'true' : undefined}
              aria-describedby={errors.email ? 'email-error' : undefined}
              {...register('email', {
                required: 'Inserisci la tua email.',
                pattern: { value: EMAIL_PATTERN, message: 'Inserisci un indirizzo email valido.' },
              })}
            />
            {errors.email && (
              <p id="email-error" className="auth-field-error" role="alert">
                {errors.email.message}
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={errors.password ? 'true' : undefined}
              aria-describedby={errors.password ? 'password-error' : undefined}
              {...register('password', { required: 'Inserisci la tua password.' })}
            />
            {errors.password && (
              <p id="password-error" className="auth-field-error" role="alert">
                {errors.password.message}
              </p>
            )}
          </div>

          <p className="auth-error" role="alert" aria-live="polite">
            {authError}
          </p>

          <button type="submit" className="auth-submit" disabled={isSubmitting}>
            {isSubmitting ? 'Accesso in corso…' : 'Accedi'}
          </button>
        </form>
      </section>
    </main>
  );
}
