# Database and Flyway

## Migrations

All schema changes are managed with Flyway. Migrations live in
`backend/core-module/src/main/resources/db/migration/` and follow
`V<version>__<description>.sql`.

| Migration | Content |
| --- | --- |
| `V1__create_global_identity_schema.sql` | `users`, `roles`, `permissions`, `role_permissions` |
| `V2__create_office_and_membership_schema.sql` | `offices`, `office_memberships`, `user_roles` |
| `V3__create_invitation_and_auth_token_schema.sql` | `office_invitations`, `refresh_tokens`, `password_reset_tokens` |
| `V4__create_audit_schema.sql` | `audit_events` + immutable trigger |
| `V5__seed_roles_and_permissions.sql` | initial catalog and role-permission matrix |
| `V6__enable_row_level_security.sql` | RLS policies and GUC helper functions |
| `V7__add_invitation_token_select_policy.sql` | token-bearer select policy for invitations |
| `V8__harden_constraints_and_token_rls.sql` | `security_version`, lifecycle/scope checks, ownership FKs, invitation role-scope trigger, RLS on token tables, tightened audit policies |
| `V9__create_clinical_schema.sql` | `patients`, `appointments`, `prescriptions` with RLS and audit columns |

## Conventions

- `spring.jpa.hibernate.ddl-auto=validate` — entities never alter the schema.
- Every business table includes `office_id` (or is explicitly global) and standard audit columns
  (`created_at`, `updated_at`, `created_by`, `updated_by`).
- Migrations are immutable once applied; schema changes are always new versions.
- `refresh_tokens`, `password_reset_tokens`, `user_roles` and `office_invitations` are **technical
  security tables**: they are exempt from the full business audit-column set and are instead
  protected by RLS and one-time/token lifecycle semantics. Only raw hashes are stored; the raw
  tokens are never persisted.
- Sensitive clinical fields (patient tax identifiers, clinical notes, prescription medication and
  instructions) are encrypted at rest with AES-GCM through
  `EncryptedStringConverter`, using the `SANITASLINK_ENCRYPTION_KEY` (a base64 32-byte key).

## Database roles

`docker-compose.yml` + `docker/postgres-init.sql` create (local-development only):

- `db_owner` (migration user, table owner);
- `app_user` (runtime user, granted `SELECT/INSERT/UPDATE/DELETE` through default privileges).

The application connects as `app_user`; Flyway connects as `db_owner`. Outside the `dev` profile,
the database credentials and the encryption key are **required** environment variables; the
application fails fast when they are missing.

## Local development

```bash
docker compose up -d postgres
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile provides the JWT secret, the encryption key, the local database credentials and
enables token logging in the notification port. In any other environment
`SANITASLINK_JWT_SECRET`, `SANITASLINK_ENCRYPTION_KEY`, `SANITASLINK_DB_USERNAME`,
`SANITASLINK_DB_PASSWORD`, `SANITASLINK_FLYWAY_USER` and `SANITASLINK_FLYWAY_PASSWORD` must be set.
