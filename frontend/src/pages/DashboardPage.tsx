import { useAuth } from '@/features/auth/useAuth';

export function DashboardPage() {
  const { profile, logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Panoramica</h1>
        <button type="button" className="auth-submit logout-button" onClick={() => void logout()}>
          Esci
        </button>
      </header>

      <main className="app-content">
        <section className="profile-card" aria-labelledby="profile-heading">
          <h2 id="profile-heading">Il tuo profilo</h2>
          {profile ? (
            <dl className="profile-details">
              <div>
                <dt>Nome</dt>
                <dd>
                  {profile.firstName} {profile.lastName}
                </dd>
              </div>
              <div>
                <dt>Email</dt>
                <dd>{profile.email}</dd>
              </div>
              <div>
                <dt>Stato</dt>
                <dd>{profile.status}</dd>
              </div>
              <div>
                <dt>Office ID</dt>
                <dd>{profile.officeId ?? '—'}</dd>
              </div>
              <div>
                <dt>Ruoli effettivi</dt>
                <dd>{profile.roles?.length ? profile.roles.join(', ') : '—'}</dd>
              </div>
              <div>
                <dt>Permessi effettivi</dt>
                <dd>{profile.permissions?.length ? profile.permissions.join(', ') : '—'}</dd>
              </div>
            </dl>
          ) : (
            <p>Profilo non disponibile.</p>
          )}
        </section>
      </main>
    </div>
  );
}
