# 🤖 Backend AI Agent Guidelines (Spring Boot / Java 21)

You are an expert Java/Spring Boot Developer. You are working inside the `/backend` modular monolith.

## 🛠️ Stack & Standards
* **Framework:** Java 21 LTS + Spring Boot 3.x
* **Security:** Spring Security (OAuth2 / OIDC / JWT / RBAC)
* **Persistence & Migrations:** Spring Data JPA + PostgreSQL (Row-Level Security) + Flyway
* **Cache & Real-time:** Redis + WebSockets
* **Build Tool:** Maven (Multi-module)

### Core Architectural Principles
1. **Strict Domain Isolation:** Modules (`core`, `patient`, `appointment`, `prescription`) must be decoupled. Cross-module communications happen via clean service interfaces or domain events.
2. **Multi-Tenancy First:** Every tenant represents a **Studio** (Medical Practice). All domain entities belong to a tenant. Never leak data across `tenant_id` / `studio_id` boundaries.
3. **Auditability:** Any read/write operation on sensitive patient data must trigger an immutable audit event.

## 🗄️ Database & Flyway Rules
* **Migration Tool:** All database schema changes (tables, indexes, constraints, RLS policies) MUST be managed using **Flyway**.
* **Hibernate Config:** Set `spring.jpa.hibernate.ddl-auto=validate`. JPA Entities MUST NEVER alter the database directly.

### Migration Naming Conventions
* Script location: `backend/<module>-module/src/main/resources/db/migration/`
* Naming format: `V{VERSION}__{description}.sql` (e.g., `V1__init_core_tenant_schema.sql`, `V2__add_patient_emergency_contact.sql`).
* Version numbers must be sequential and uppercase `V`. Use double underscores `__` between version and description.

### Schema Change Guidelines
1. **Always Include Tenant Isolation:** Every new business table must include `studio_id BIGINT NOT NULL` (or equivalent tenant key) and foreign key constraints unless explicitly global.
2. **Enable Row Level Security (RLS):** New tables storing sensitive or tenant-specific data must include `ALTER TABLE {table_name} ENABLE ROW LEVEL SECURITY;` and its corresponding tenant policy in the migration script.
3. **Audit Fields Required:** Business tables MUST include standard audit columns:
   * `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
   * `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
   * `created_by VARCHAR(100)`
   * `updated_by VARCHAR(100)`
4. **Non-Destructive Changes:** Avoid `DROP COLUMN` or `ALTER COLUMN TYPE` directly in production-targeted scripts without a phased migration strategy.

## 🔐 Security & Compliance Requirements

1. **RBAC Rules:**
   * `ROLE_ADMIN`: System maintenance.
   * `ROLE_DOCTOR`: Access to clinical notes, prescription issuing, patient data within their tenant studio.
   * `ROLE_SECRETARY`: Access to appointments, calendar, patient demographic data (NO medical history/clinical details).
   * `ROLE_PATIENT`: Self-service profile, appointment bookings, personal prescription requests.

2. **Data Encryption:**
   * Sensitive health data fields in JPA entities MUST use attribute converters for Encryption-at-Rest.

3. **Audit Log Mandatory Metadata:**
   * Every audit log entry must record: `operator_id`, `action_type`, `patient_id`, `tenant_id`, `timestamp`, `ip_address`.

## 📐 Java Coding Conventions
* **DTO Pattern:** Never expose JPA Entities directly via REST controllers. Always map Entities <-> DTOs (e.g., MapStruct or explicit mappers).
* **Transaction Management:** Annotate write methods with `@Transactional`. Ensure atomic updates across operations.
* **Multi-Tenancy Checks:** Ensure Spring Data JPA specifications or PostgreSQL RLS filters explicitly enforce `tenant_id` context on queries.
* **Error Handling:** Use standard custom exceptions handled globally via `@RestControllerAdvice`. Return uniform RFC-7807 Problem Details JSON responses.
* Never hardcode secrets, encryption keys, or tenant IDs. Always use environment variables or Spring Profiles.

## 🧪 Testing Guidelines
* **Unit Testing:** **JUnit 5** + **Mockito** for service layer logic, validations, and mapping.
* **Persistence & Multi-Tenancy:** `@DataJpaTest` with Testcontainers (PostgreSQL) to verify Row-Level Security (RLS) policies and complex multi-tenant query specifications.
* **Controller / Integration Testing:** `@SpringBootTest` + `MockMvc` for verifying security filters, RBAC roles, and RFC-7807 error responses.
