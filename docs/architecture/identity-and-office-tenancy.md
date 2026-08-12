# Identity and Office Tenancy

## The tenant model

The tenant is the **office** (an individual practice / studio). Every business entity belongs to
exactly one office through a mandatory `office_id`.

The office id is **never** sent by the client. There is **no `X-Office-Id` header**. The tenant
is always derived on the server from the authenticated JWT (`office_id` claim) and verified
against the user's current database membership.

## Entities

```
User (global)  1:N  UserRole  N:1  Role (global catalog)
User (global)  0..1  OfficeMembership  1..1  Office (tenant)
Office (tenant)  1:N  OfficeInvitation
User (global)  1:N  RefreshToken / PasswordResetToken (global, hashed)
```

### Users (global)

A `User` is a platform account: email, password hash, profile, lifecycle status
(`INVITED`, `ACTIVE`, `DISABLED`, `LOCKED`). Users are not tenant-scoped, but a user can belong
to **at most one office** (mono-office invariant).

### Offices (tenant)

An `Office` is the practice: identity, legal and contact data, lifecycle status
(`ACTIVE`, `SUSPENDED`, `DELETED`).

### Memberships (tenant)

`OfficeMembership` links a user to an office. Its primary key is `user_id`, which enforces the
mono-office invariant at the database level. Statuses: `INVITED`, `ACTIVE`, `REVOKED`.

### Roles (global catalog)

`Role` is a mutable catalog entry with a unique `code`, a `scope` (`PLATFORM` or `OFFICE`) and an
`active` flag. Roles are managed centrally; the schema, optimistic locking (`version`) and audit
columns are ready for future management functionality.

### UserRole (global assignment)

`UserRole` assigns a role to a user. A user may hold multiple roles; permissions aggregate with a
set union (logical OR). Both platform roles (for example `ADMIN`) and office roles are stored in
the same table.

## Invariants

- A user belongs to **at most one** office (enforced by the `office_memberships` primary key and
  application checks).
- A user must hold **at least one** office role while their membership is active.
- Roles and permissions are **fully mutable**: authorization is always resolved from the current
  database state on every request, so changes take effect immediately.
- The last `MEDICO_TITOLARE` of an office cannot be removed or lose the owner role without an
  atomic replacement.
