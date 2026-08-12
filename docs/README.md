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

This release delivers the **backend** identity, office (tenant), authorization and invitation
subsystem:

- users, offices and mono-office membership;
- a fully mutable, database-managed role and permission catalog;
- JWT authentication with rotated refresh tokens;
- Admin-driven office provisioning and email invitations;
- password reset and first-access password setup;
- PostgreSQL Row-Level Security for tenant isolation;
- immutable audit trail with request metadata;
- encryption-at-rest for sensitive clinical fields;
- clinical domain foundation: patient registry, appointments and prescriptions with granular
  permissions and read/write audit;
- RFC 7807 error responses.

The frontend and the self-service practice-owner registration are intentionally out of scope and
will be built on top of this subsystem.
