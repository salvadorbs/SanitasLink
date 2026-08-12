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
will be built on top of this subsystem.
