# Admin Provisioning and Invitations

## Overview

The initial onboarding is **admin-driven**: a global platform administrator provisions offices and
their members. Self-registration of practice owners is not part of this release.

## Workflow A — Office and titular doctor

```
[Admin]
  1. create office (ACTIVE)
  2. create (or reuse) the titular user with status INVITED
  3. create membership (INVITED) + assign MEDICO_TITOLARE
  4. create a one-time invitation and notify the titular
```

The operation is atomic. The titular then completes first access through the invitation
acceptance flow, which activates the membership and sets the password. No initial password is
ever sent.

## Workflow B — Invite a collaborator

```
[Titular or Admin]
  1. provide the email and a predefined office role
  2. a pending invitation is created and the invitee is notified
```

Rules:

- the role must be an active `OFFICE`-scope role from the catalog;
- only one pending invitation per (office, email);
- the email must not already belong to another office (mono-office invariant);
- the token is single-use, expires after `sanitaslink.tokens.invitation-ttl` (default 72h) and is
  stored only as a SHA-256 hash.

## Workflow C — Invitation acceptance (first access)

The invitee presents the token together with their profile and a new password:

1. the token hash is looked up under the token-bearer RLS policy;
2. the token must be pending and unexpired;
3. the user is created (or an existing user without a membership is completed);
4. the membership is created/activated for the invitation's office;
5. the invited role is assigned (aggregated with any existing roles);
6. the invitation is marked accepted and tokens are issued.

## Staff management

The practice owner (or an admin) can, with `CORE_STAFF_MANAGE`:

- list members;
- assign additional office roles;
- revoke office roles;
- revoke a member's membership.

Invariants:

- the last `MEDICO_TITOLARE` cannot be removed or lose the owner role;
- a user keeps at least one office role while active.

Pending invitations can be revoked with `CORE_STAFF_INVITE`.

## Permissions overview

| Action | Permission | Who |
| --- | --- | --- |
| Provision office / members | `ROLE_ADMIN` | platform admin |
| Invite staff | `CORE_STAFF_INVITE` | practice owner |
| Manage members/roles | `CORE_STAFF_MANAGE` | practice owner |
| Update office info | `CORE_OFFICE_UPDATE` | practice owner |
| View office info | `CORE_OFFICE_READ` | all members |
