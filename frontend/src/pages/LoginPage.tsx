import { useState } from 'react';

import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

import { ThemeToggle } from '@/components/ThemeToggle';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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
    <main className="relative flex min-h-svh items-center justify-center bg-muted/40 p-6">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <section className="w-full max-w-md">
        <Card>
          <CardHeader>
            <h1 className="text-2xl font-semibold tracking-tight">Accedi a SanitasLink</h1>
            <CardDescription>Inserisci le credenziali del tuo studio.</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
              <div className="grid gap-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  autoFocus
                  aria-invalid={errors.email ? 'true' : undefined}
                  aria-describedby={errors.email ? 'email-error' : undefined}
                  {...register('email', {
                    required: 'Inserisci la tua email.',
                    pattern: {
                      value: EMAIL_PATTERN,
                      message: 'Inserisci un indirizzo email valido.',
                    },
                  })}
                />
                {errors.email && (
                  <p id="email-error" className="text-sm text-destructive" role="alert">
                    {errors.email.message}
                  </p>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  aria-invalid={errors.password ? 'true' : undefined}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password', { required: 'Inserisci la tua password.' })}
                />
                {errors.password && (
                  <p id="password-error" className="text-sm text-destructive" role="alert">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <p className="min-h-5 text-sm text-destructive" role="alert" aria-live="polite">
                {authError}
              </p>

              <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? 'Accesso in corso…' : 'Accedi'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </section>
    </main>
  );
}
