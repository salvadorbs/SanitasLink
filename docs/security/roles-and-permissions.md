# Roles and Permissions

## Model

Roles and permissions are **database-managed and fully mutable**. They are seeded by Flyway with
an initial catalog and matrix, but no role code or permission record is treated as an immutable
authorization branch in code. The catalog can be edited without redeploying.

- `roles`: UUID, unique `code`, `name`, `description`, `scope` (`PLATFORM` | `OFFICE`),
  `active`, `version` (optimistic lock), audit columns.
- `permissions`: UUID, unique `code`, `module`, `name`, `description`, `active`, `version`, audit
  columns.
- `role_permissions`: global `role_id` → `permission_id` mapping.
- `user_roles`: `user_id` → `role_id` assignments (a user can hold several roles).

Effective permissions for a user are the **set union** over all of their active roles of the
active permissions linked to those roles (logical OR). Multiple role assignments therefore
aggregate.

## Scopes

- **PLATFORM** roles grant platform-level authorities (for example `ADMIN`, mapped to
  `ROLE_ADMIN`) and do not require an office membership.
- **OFFICE** roles grant granular office-scoped permissions and only apply within the user's
  membership office.

## Initial catalog

### Core & staff (`CORE`)

| Permission | Description |
| --- | --- |
| `CORE_OFFICE_READ` | View office information and parameters |
| `CORE_OFFICE_UPDATE` | Update office address, hours and legal data |
| `CORE_STAFF_INVITE` | Invite physicians or secretaries to the office |
| `CORE_STAFF_MANAGE` | Assign/revoke roles and deactivate users |

### Patients (`PATIENT`)

| Permission | Description |
| --- | --- |
| `PATIENT_REGISTRY_READ` | Search and view the registry and contacts |
| `PATIENT_REGISTRY_CREATE` | Register a new patient |
| `PATIENT_REGISTRY_UPDATE` | Update demographic data, exemptions, contacts |
| `PATIENT_PRIVACY_MANAGE` | Record and print GDPR privacy/consent |
| `PATIENT_CLINICAL_READ` | Read the clinical record |
| `PATIENT_CLINICAL_WRITE` | Write clinical data, diagnoses, prescriptions |

### Appointments (`APPOINTMENT`)

| Permission | Description |
| --- | --- |
| `APPOINTMENT_READ` | View the agenda |
| `APPOINTMENT_CREATE` | Book appointments |
| `APPOINTMENT_UPDATE` | Edit appointments |
| `APPOINTMENT_CANCEL` | Cancel or mark missed appointments |
| `APPOINTMENT_SLOT_MANAGE` | Configure reception hours and agenda blocks |

### Prescriptions (`PRESCRIPTION`)

| Permission | Description |
| --- | --- |
| `PRESCRIPTION_READ` | View prescription history |
| `PRESCRIPTION_REQUEST_CREATE` | Create prescription requests for patients |
| `PRESCRIPTION_WRITE` | Sign and issue prescriptions |
| `PRESCRIPTION_PRINT` | Print and send prescriptions |

## Initial role matrix

| Permission | MEDICO_TITOLARE | MEDICO_COLLABORATORE | SEGRETARIA_BASE | SEGRETARIA_AVANZATA |
| --- | --- | --- | --- | --- |
| `CORE_OFFICE_READ` | ✓ | ✓ | ✓ | ✓ |
| `CORE_OFFICE_UPDATE` | ✓ | ✗ | ✗ | ✗ |
| `CORE_STAFF_INVITE` / `CORE_STAFF_MANAGE` | ✓ | ✗ | ✗ | ✗ |
| `PATIENT_REGISTRY_*` | ✓ | ✓ | ✓ | ✓ |
| `PATIENT_PRIVACY_MANAGE` | ✓ | ✓ | ✓ | ✓ |
| `PATIENT_CLINICAL_READ` / `PATIENT_CLINICAL_WRITE` | ✓ | ✓ | ✗ | ✗ |
| `APPOINTMENT_*` | ✓ | ✓ | ✓ | ✓ |
| `PRESCRIPTION_READ` | ✓ | ✓ | ✓ | ✓ |
| `PRESCRIPTION_REQUEST_CREATE` | ✓ | ✓ | ✓ | ✓ |
| `PRESCRIPTION_WRITE` | ✓ | ✓ | ✗ | ✗ |
| `PRESCRIPTION_PRINT` | ✓ | ✓ | ✗ | ✓ |

`ADMIN` is a global platform role used for system maintenance; it is not an office role.

## Resolution at runtime

On every authenticated request a filter (`PermissionEnrichmentFilter`) resolves the user's active
roles and effective permissions from the database, builds the Spring Security authorities
(`ROLE_<code>` for roles, `<code>` for permissions) and publishes the tenant context. Service
methods enforce access with `@PreAuthorize("hasAuthority('CORE_STAFF_MANAGE')")` and similar. The
client (JWT claims) is never a trusted source of authorization.
