# Database and Flyway

## Migrations

All schema changes are managed with Flyway. Migrations follow
`V<version>__<description>.sql` and live in the `db/migration` folder of the module that owns the
domain (`core-module`, `patient-module`, `appointment-module`, `prescription-module`); the version
numbering is global and sequential across modules.

| Migration | Module | Content |
| --- | --- | --- |
| `V1__create_global_identity_schema.sql` | core | `users`, `roles`, `permissions`, `role_permissions` |
| `V2__create_office_and_membership_schema.sql` | core | `offices`, `office_memberships`, `user_roles` |
| `V3__create_invitation_and_auth_token_schema.sql` | core | `office_invitations`, `refresh_tokens`, `password_reset_tokens` |
| `V4__create_audit_schema.sql` | core | `audit_events` + immutable trigger |
| `V5__seed_roles_and_permissions.sql` | core | initial catalog and role-permission matrix |
| `V6__enable_row_level_security.sql` | core | RLS policies and GUC helper functions |
| `V7__add_invitation_token_select_policy.sql` | core | token-bearer select policy for invitations |
| `V8__harden_constraints_and_token_rls.sql` | core | `security_version`, lifecycle/scope checks, ownership FKs, invitation role-scope trigger, RLS on token tables, tightened audit policies |
| `V9__create_patients_table.sql` | patient | `patients` with RLS, audit columns and composite `(office_id, id)` unique |
| `V10__create_appointments_table.sql` | appointment | `appointments` with RLS and office-scoped composite patient FK |
| `V11__create_prescriptions_table.sql` | prescription | `prescriptions` with mandatory office-scoped patient FK |
| `V12__widen_encrypted_columns.sql` | patient | widen columns that now store encrypted values |

## Conventions

- `spring.jpa.hibernate.ddl-auto=validate` — entities never alter the schema.
- Every business table includes `office_id` (or is explicitly global) and standard audit columns
  (`created_at`, `updated_at`, `created_by`, `updated_by`).
- Migrations are immutable once applied; schema changes are always new versions.
- Clinical tenant integrity is enforced at the database: appointments and prescriptions reference
  patients through composite `(office_id, patient_id)` foreign keys, so a row can never reference
  a patient of another office.
- `refresh_tokens`, `password_reset_tokens`, `user_roles` and `office_invitations` are **technical
  security tables**: they are exempt from the full business audit-column set and are instead
  protected by RLS and one-time/token lifecycle semantics. Only raw hashes are stored; the raw
  tokens are never persisted.
- Sensitive clinical and personal fields (patient tax identifiers, email, phone, address, clinical
  notes, appointment notes, prescription medication and instructions) are encrypted at rest with
  AES-GCM through `EncryptedStringConverter` using `SANITASLINK_ENCRYPTION_KEY`. Names and birth
  dates stay plaintext because they are required for search, indexing and age calculations.
- Encrypted values are stored as `v<version>:<base64>`. During key rotation set
  `SANITASLINK_ENCRYPTION_PREVIOUS_KEY` so old ciphertext remains readable, then re-encrypt with
  the new key and bump `SANITASLINK_ENCRYPTION_VERSION`.

## Database roles

`docker-compose.yml` + `docker/postgres-init.sql` create (local-development only):

- `db_owner` (deterministic bootstrap owner, migration user, table owner);
- `app_user` (runtime user, granted `SELECT/INSERT/UPDATE/DELETE` through default privileges).

The application connects as `app_user`; Flyway connects as `db_owner`. Outside the `dev` profile,
the database credentials and the encryption key are **required** environment variables; the
application fails fast when they are missing.

## Local development

```bash
docker compose up -d postgres
cd backend
./mvnw spring-boot:run -pl app-module -Dspring-boot.run.profiles=dev
```

The `dev` profile provides the JWT secret, the encryption key, the local database credentials and
enables token logging in the notification port. In any other environment
`SANITASLINK_JWT_SECRET`, `SANITASLINK_ENCRYPTION_KEY`, `SANITASLINK_DB_USERNAME`,
`SANITASLINK_DB_PASSWORD`, `SANITASLINK_FLYWAY_USER` and `SANITASLINK_FLYWAY_PASSWORD` must be set.
