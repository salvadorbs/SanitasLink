# Auditability

## Principle

Any operation on sensitive data must produce an immutable audit event, and the audit trail must
be impossible to rewrite or delete.

## `audit_events` table

| Column | Meaning |
| --- | --- |
| `id` | event UUID |
| `office_id` | owning office (nullable for global events) |
| `operator_id` | the acting user |
| `action_type` | e.g. `LOGIN`, `OFFICE_CREATED`, `MEMBER_INVITED`, `INVITATION_ACCEPTED`, `MEMBER_ROLE_CHANGED`, `PASSWORD_CHANGE` |
| `resource_type` | e.g. `OFFICE`, `USER`, `INVITATION`, `MEMBERSHIP`, `SESSION` |
| `resource_id` | the affected resource id |
| `patient_id` | patient id for future clinical audit (nullable) |
| `ip_address`, `user_agent` | request metadata |
| `correlation_id` | request correlation |
| `metadata` | JSONB metadata |
| `occurred_at` | event timestamp |

## Immutability

- A database trigger raises an exception on any `UPDATE` or `DELETE` of `audit_events`.
- RLS exposes reads only to the owning office (or global admins).
- Audit is recorded **in the same transaction** as the operation (`Propagation.MANDATORY`), so the
  audit and the audited write commit or roll back atomically.

## Application-level guarantees

- `AuditService` is the single entry point for writing events.
- Raw tokens, passwords and health data are never written to the audit trail or application logs.
- The notification port only logs one-time tokens when `sanitaslink.notifications.log-secrets` is
  enabled (development/tests only).
