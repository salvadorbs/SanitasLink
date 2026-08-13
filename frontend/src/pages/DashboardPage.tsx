import { ThemeToggle } from '@/components/ThemeToggle';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';

export function DashboardPage() {
  const { profile, logout } = useAuth();

  return (
    <div className="flex min-h-svh flex-col">
      <header className="flex items-center justify-between gap-4 border-b px-6 py-4">
        <h1 className="text-2xl font-semibold tracking-tight">Panoramica</h1>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <Button variant="outline" type="button" onClick={() => void logout()}>
            Esci
          </Button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 p-6">
        <Card aria-labelledby="profile-heading">
          <CardHeader>
            <h2 id="profile-heading" className="text-lg font-semibold">
              Il tuo profilo
            </h2>
          </CardHeader>
          <CardContent>
            {profile ? (
              <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
                <div>
                  <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Nome</dt>
                  <dd className="mt-1 break-words">
                    {profile.firstName} {profile.lastName}
                  </dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Email</dt>
                  <dd className="mt-1 break-words">{profile.email}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Stato</dt>
                  <dd className="mt-1 break-words">{profile.status}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Office ID</dt>
                  <dd className="mt-1 break-words">{profile.officeId ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                    Ruoli effettivi
                  </dt>
                  <dd className="mt-1 break-words">{profile.roles?.length ? profile.roles.join(', ') : '—'}</dd>
                </div>
                <div>
                  <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                    Permessi effettivi
                  </dt>
                  <dd className="mt-1 break-words">
                    {profile.permissions?.length ? profile.permissions.join(', ') : '—'}
                  </dd>
                </div>
              </dl>
            ) : (
              <p>Profilo non disponibile.</p>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
