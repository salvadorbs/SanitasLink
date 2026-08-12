# Row-Level Security

## Overview

Tenant isolation is enforced at the database with PostgreSQL Row-Level Security (RLS). Business
tables carry `office_id` and are protected by policies that compare the row against the current
request context.

## Database roles

- `db_owner` — schema owner, used only by Flyway for migrations. It never runs application
  queries.
- `app_user` — the runtime JDBC user. It does **not** own the tables, so RLS always applies.

All tenant-scoped tables additionally use `FORCE ROW LEVEL SECURITY` so even the owner cannot
bypass the policies.

## Context GUCs

The application sets the following custom settings on the current JDBC connection inside each
transaction using `set_config(..., false)` (equivalent to `SET LOCAL`):

| GUC | Purpose |
| --- | --- |
| `app.current_office_id` | the current office UUID (empty string when absent) |
| `app.current_user_id` | the current user UUID |
| `app.is_admin` | `'true'` when the caller holds the global `ADMIN` role |
| `app.current_token_hash` | the invitation token hash during the acceptance flow |

`SET LOCAL` semantics guarantee the context is scoped to the transaction and never leaks across
pooled connections. The transaction layer applies the context through `TenantContextManager`
before any tenant-scoped query.

## Policies

- `offices`: a member sees/updates only their own office; only global admins can insert or manage
  arbitrary offices.
- `office_memberships`: a user can read their own membership rows (bootstrap), and members can
  read/modify memberships of their own office; admins see everything.
- `user_roles`: a user can read their own assignments; writes require office membership or admin.
- `office_invitations`: members and admins manage invitations of their office; additionally, an
  invitation can be read by whoever presents the matching token hash (the bearer credential of
  the acceptance flow).
- `refresh_tokens` and `password_reset_tokens`: reads are allowed for the owning user, for the
  bearer of the matching token hash and for admins; writes require the owning user's context (or
  admin).
- `patients`, `appointments`, `prescriptions`: office members can access only their office's
  clinical data; admins see everything.
- `audit_events`: reads are limited to the owning office (or admin); global events are readable
  only by admins; inserts require the current office context (or admin) unless the event is
  genuinely global; updates and deletes are blocked by the immutable trigger and by the absence of
  policies.

## Context bootstrap

Membership and role metadata are themselves RLS-protected, so the resolution flow runs in two
phases inside one transaction:

1. a **self-read context** (`app.current_user_id`) lets the user read their own membership and
   role rows;
2. once the office and admin flag are known, the **full context** is applied and the effective
   roles/permissions are resolved.

Pending writes from the surrounding transaction are flushed before the context is switched, so
they are committed under the caller's office context.
