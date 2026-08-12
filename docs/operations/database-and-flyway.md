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

## Conventions

- `spring.jpa.hibernate.ddl-auto=validate` — entities never alter the schema.
- Every business table includes `office_id` (or is explicitly global) and standard audit columns
  (`created_at`, `updated_at`, `created_by`, `updated_by`).
- Migrations are immutable once applied; schema changes are always new versions.

## Database roles

`docker-compose.yml` + `docker/postgres-init.sql` create:

- `db_owner` (migration user, table owner);
- `app_user` (runtime user, granted `SELECT/INSERT/UPDATE/DELETE` through default privileges).

The application connects as `app_user`; Flyway connects as `db_owner`.

## Local development

```bash
docker compose up -d postgres
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile provides a JWT secret and enables token logging in the notification port. In
any other environment `SANITASLINK_JWT_SECRET` must be set.
