# 🤖 Backend AI Agent Guidelines (Spring Boot / Java 21)

You are an expert Java/Spring Boot Developer. You are working inside the `/backend` modular monolith.

## 🛠️ Stack & Standards
* **Framework:** Java 21 LTS + Spring Boot 4.0.7
* **Security:** Spring Security OAuth2 Resource Server (JWT / RBAC)
* **Persistence & Migrations:** Spring Data JPA + PostgreSQL (Row-Level Security) + Flyway
* **Build Tool:** Maven (Multi-module)

### Core Architectural Principles
1. **Strict Domain Isolation:** Modules (`core`, `patient`, `appointment`, `prescription`) must be decoupled. Cross-module communications happen via clean service interfaces or domain events.
2. **Multi-Tenancy First:** Every tenant represents a **Doctor**. All domain entities belong to a tenant. Never leak data across `doctor_id` boundaries.
3. **Auditability:** Any read/write operation on sensitive patient data must trigger an immutable audit event.
4. **Layering**: Controller -> Service -> Repository. 
5. **Validation**: Use Jakarta Validation annotations on all DTOs.
6. **Exception Handling**: Every new feature must include an entry in `GlobalExceptionHandler`.
7. **Security**: Never expose sequential IDs; use UUIDs or HashIDs for public resources.

## 🗄️ Database & Flyway Rules
* **Migration Tool:** All database schema changes (tables, indexes, constraints, RLS policies) MUST be managed using **Flyway**.
* **Hibernate Config:** Set `spring.jpa.hibernate.ddl-auto=validate`. JPA Entities MUST NEVER alter the database directly.

### Migration Naming Conventions
* Script location: `backend/<module>-module/src/main/resources/db/migration/`
* Naming format: `V{VERSION}__{description}.sql` (e.g., `V1__init_core_tenant_schema.sql`, `V2__add_patient_emergency_contact.sql`).
* Version numbers must be sequential and uppercase `V`. Use double underscores `__` between version and description.

### Schema Change Guidelines
1. **Always Include Tenant Isolation:** Every new business table must include `doctor_id BIGINT NOT NULL` (or equivalent tenant key) and foreign key constraints unless explicitly global.
2. **Enable Row Level Security (RLS):** New tables storing sensitive or tenant-specific data must include `ALTER TABLE {table_name} ENABLE ROW LEVEL SECURITY;` and its corresponding tenant policy in the migration script. Policies must compare `doctor_id` against `current_setting('app.current_doctor_id', true)`. The transaction layer MUST execute `SET LOCAL app.current_doctor_id = '<doctor-id>';` on the current JDBC connection before any query.
3. **Audit Fields Required:** Business tables MUST include standard audit columns:
   * `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
   * `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
   * `created_by VARCHAR(100)`
   * `updated_by VARCHAR(100)`
4. **Non-Destructive Changes:** Avoid `DROP COLUMN` or `ALTER COLUMN TYPE` directly in production-targeted scripts without a phased migration strategy.

### Database Roles & RLS Testing
* The application JDBC user MUST NOT own the tables. Use a separate `db_owner` role for schema/Flyway migrations and a restricted application role (`app_user`) for runtime queries, otherwise PostgreSQL bypasses RLS for the table owner.
* Where required, use `FORCE ROW LEVEL SECURITY` so even the owner cannot bypass policies during tests.
* Never use `SET` (session-level) for the doctor context: it leaks across pooled connections. Only `SET LOCAL` inside the current transaction.

## 🔐 Security & Compliance Requirements

0. **JWT Secret:** `sanitaslink.security.jwt.secret` is required and validated at startup (`@NotBlank`). Only the `dev` profile provides a default value; run local dev with `--spring.profiles.active=dev` or set `SANITASLINK_JWT_SECRET`. The JWT decoder validates the `iss` claim against `sanitaslink.security.jwt.issuer`.
1. **RBAC Rules:**
   * `ROLE_ADMIN`: System maintenance.
   * `ROLE_DOCTOR`: Access to clinical notes, prescription issuing, patient data within their tenant scope.
   * `ROLE_PATIENT`: Self-service profile, appointment bookings, personal prescription requests.

2. **Data Encryption:**
   * Sensitive health data fields in JPA entities MUST use attribute converters for Encryption-at-Rest.

3. **Audit Log Mandatory Metadata:**
   * Every audit log entry must record: `operator_id`, `action_type`, `patient_id`, `doctor_id`, `timestamp`, `ip_address`.

## 📐 Java Coding Conventions
* **DTO Pattern:** Never expose JPA Entities directly via REST controllers. Always map Entities <-> DTOs (e.g., MapStruct or explicit mappers).
* **Transaction Management:** Annotate write methods with `@Transactional`. Ensure atomic updates across operations.
* **Multi-Tenancy Checks:** Ensure Spring Data JPA specifications or PostgreSQL RLS filters explicitly enforce `doctor_id` context on queries.
* **Error Handling:** Use standard custom exceptions handled globally via `@RestControllerAdvice`. Return uniform RFC-7807 Problem Details JSON responses.
* Never hardcode secrets, encryption keys, or tenant IDs. Always use environment variables or Spring Profiles.

## 🧪 Testing Guidelines
* **Unit Testing:** **JUnit 5** + **Mockito** for service layer logic, validations, and mapping.
* **Persistence & Multi-Tenancy:** `@DataJpaTest` with Testcontainers (PostgreSQL) to verify Row-Level Security (RLS) policies and complex multi-tenant query specifications.
* Use Testcontainers for integration tests involving the database.
* Mocks must strictly use Mockito via @MockBean.
* **Controller / Integration Testing:** `@SpringBootTest` + `MockMvc` for verifying security filters, RBAC roles, and RFC-7807 error responses.
