# SanitasLink Technical Documentation

SanitasLink is a modular-monolith, multi-tenant healthcare management platform. This
documentation describes the current architecture, security model, database design and the
identity/office subsystem.

> **Convention:** All documentation is written in English.

## Table of contents

### Architecture
- [Identity and Office Tenancy](architecture/identity-and-office-tenancy.md)

### Security
- [Authentication and JWT](security/authentication-and-jwt.md)
- [Roles and Permissions](security/roles-and-permissions.md)
- [Row-Level Security](security/row-level-security.md)
- [Auditability](security/auditability.md)

### Database
- [Database and Flyway](operations/database-and-flyway.md)

### Product flows
- [Admin Provisioning and Invitations](product-flows/admin-provisioning-and-invitations.md)

## Scope

This release delivers the **backend** identity, office (tenant), authorization, invitation and
clinical subsystems. The runnable application is the `app-module`; the domain code lives in the
`core`, `patient`, `appointment` and `prescription` modules:

- users, offices and mono-office membership;
- a fully mutable, database-managed role and permission catalog;
- JWT authentication with rotated refresh tokens;
- Admin-driven office provisioning and email invitations;
- password reset and first-access password setup;
- PostgreSQL Row-Level Security for tenant isolation, including composite office-scoped clinical
  foreign keys;
- immutable audit trail with request metadata, including read auditing for sensitive lists;
- encryption-at-rest (versioned AES-GCM) for sensitive clinical and personal fields;
- clinical domain: patient registry, appointments with lifecycle transitions and prescriptions
  with atomic issue/print flows, all with granular permissions and read/write audit;
- RFC 7807 error responses.

The frontend and the self-service practice-owner registration are intentionally out of scope and
will be built on top of this subsystem. The frontend authentication layer exists and is verified
with an end-to-end Playwright suite (see below).

## End-to-end testing (Playwright)

The critical auth flows are verified against a real backend and a real PostgreSQL database:

1. `docker compose up -d postgres` — starts PostgreSQL with the `db_owner` bootstrap role and the
   runtime `app_user` role (see `docker/postgres-init.sql`);
2. apply the Flyway migrations by starting the backend once, or apply them manually; then seed the
   deterministic E2E account:

   ```bash
   docker compose exec -T postgres psql -U db_owner -d sanitaslink_db \
     -v ON_ERROR_STOP=1 -f docker/e2e-seed.sql
   ```

   The seed (credentials `e2e@studio.example` / `E2E-Password-123!`, BCrypt cost 12, test only)
   is idempotent and is **only** meant for E2E runs;
3. build the frontend and install the browser:

   ```bash
   cd frontend && npm ci && npm run build && npx playwright install chromium
   ```

4. run the suite (Playwright starts both the preview server and the backend itself):

   ```bash
   cd frontend && npm run test:e2e
   ```

The suite runs in CI (`e2e` job) against a disposable PostgreSQL service seeded from
`docker/e2e-seed.sql`. It covers the full session lifecycle: login, HttpOnly cookie attributes,
refresh on reload, logout, and replay rejection after logout.
