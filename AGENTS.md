# 🤖 Project Instructions & Guidelines for AI Agents

Welcome to the **E-Health Multi-Tenant System** codebase. You are an expert Enterprise Software Architect and Full-Stack Developer specializing in **Java / Spring Boot** and **React / TypeScript**.

Follow these rules and conventions strictly whenever reading, modifying, or generating code in this repository.

## 📚 Documentation & Guidance

Before implementing or modifying anything, **read and follow the relevant guidance**:

- **`AGENTS.md` (this file)** — project-wide architecture, tenancy and conventions.
- **`backend/AGENTS.md`** — backend (Spring Boot / Java) conventions, Flyway and database rules, RBAC, RLS and testing strategy.
- **`frontend/AGENTS.md`** — frontend (React / Vite) conventions, API client/Orval rules and testing strategy.
- **`docs/`** — technical documentation in English describing the current architecture, security model and database design. **Read it when it covers the area you are working on**, and **keep it up to date** whenever behavior, schema or security properties change. Documentation is part of the deliverable, not an afterthought.

---

## 🏛️ System Overview & Architecture

* **Domain:** Multi-tenant Healthcare Management System (General Practitioners & Patients).
* **Architecture Style:** Modular Monolith (Maven multi-module).
* **Security & Privacy Level:** Very High (GDPR Article 9 compliant - Health Data).

### Core Architectural Principles
1. **Strict Domain Isolation:** Modules (`core`, `patient`, `appointment`, `prescription`) must be decoupled. Cross-module communications happen via clean service interfaces or domain events.
2. **Multi-Tenancy First:** Every tenant represents an **Office** (studio). All domain entities belong to a tenant. Never leak data across `office_id` boundaries.
3. **Auditability:** Any read/write operation on sensitive patient data must trigger an immutable audit event.

### Multi-Tenancy Model (Office-Centred)
- **Primary Tenant:** `office_id` (UUID) identifies the owning office. The office is the primary owner of clinical information and patient records.
- **RLS Isolation:** Every business table contains `office_id UUID NOT NULL` (or is explicitly global) and is protected by PostgreSQL Row-Level Security comparing `office_id` against `current_setting('app.current_office_id', true)`. The transaction layer must run `set_config('app.current_office_id', '<id>', false)` (equivalent to `SET LOCAL`) on the current JDBC connection before any tenant-scoped query. Only `SET LOCAL` semantics are allowed — session-level `SET` leaks across pooled connections.
- **Database Roles:** Flyway runs as `db_owner` (table owner); the runtime application connects as `app_user`, which must never own tables so RLS always applies. Tenant tables use `FORCE ROW LEVEL SECURITY`.
- **Identity Tables:** `users`, `roles`, `permissions`, `role_permissions` are global identity/catalog tables and are NOT tenant-scoped. `office_memberships` and `user_roles` are RLS-protected access-control metadata.
- **Mono-Office Invariant:** A user can belong to at most one office (enforced by the primary key on `office_memberships.user_id`) and must hold at least one active office role.
- **Roles & Permissions:** Roles (`scope = PLATFORM | OFFICE`), permissions and `role_permissions` are **database-managed and fully mutable**; authorization is re-resolved from the database on every request. Seeded office roles: `MEDICO_TITOLARE`, `MEDICO_COLLABORATORE`, `SEGRETARIA_BASE`, `SEGRETARIA_AVANZATA`. Platform role: `ADMIN`. Multiple role assignments aggregate permissions (set union / logical OR).
- **JWT Context:** The authenticated JWT carries the `office_id` claim (plus informational `roles` and `permissions` claims). The server derives the tenant exclusively from the JWT and the server-side membership; **no client-side tenant header is sent or accepted** (there is no `X-Office-Id` and no `X-Doctor-Id`).
- **Staff:** `CORE_STAFF_INVITE` invites collaborators; `CORE_STAFF_MANAGE` manages memberships and roles. Staff onboarding uses admin provisioning plus one-time invitation tokens.

---

## 🌿 Branching Model (GitFlow) & CI

* **`develop`:** default development branch. All feature, fix and chore work is merged here.
* **`master`:** stable/production branch. Holds tagged releases; receives only release merges. Never commit directly.
* **CI on pull requests/push (`ci.yml`):** runs on PRs targeting `develop`/`master` and on pushes to `develop`. It executes the full backend build + tests (`./mvnw clean verify`, Testcontainers included), the frontend lint, typecheck, tests and build, an Orval idempotency check (`npm run generate:api` must produce no diff) and the Playwright E2E suite (disposable PostgreSQL seeded from `docker/e2e-seed.sql`).
* **Orval source of truth:** `orval.config.ts` reads from the committed `frontend/src/api/openapi.json` (override with the `OPENAPI_TARGET` env var, e.g. a live backend URL). The client is regenerated locally with `npm run generate:api` whenever backend endpoints or DTOs change.
* **Dependabot (`dependabot.yml`):** weekly PRs for Maven and npm dependency updates.
* **Secret scanning (`secret-scan.yml`):** gitleaks (Docker image, no license required) scans the full history on push and pull requests.

---

## 📁 Repository Structure Overview

```text
/
├── backend/                           # Spring Boot Modular Monolith
│   ├── AGENTS.md                      # Backend agent guidelines
│   ├── pom.xml                        # Parent Maven POM
│   ├── core-module/                   # Identity, Offices, Auth, RBAC, Audit, Tenancy
│   ├── patient-module/                # Patient Demographics & clinical records
│   ├── appointment-module/            # Agenda, Slots, Booking Workflows
│   ├── prescription-module/           # Prescription Tickets & Approvals
│   └── app-module/                    # Runnable Spring Boot bootstrap + integration tests
├── docs/                              # Technical documentation (English)
├── frontend/                          # React + Vite SPA
│   ├── AGENTS.md                      # Frontend agent guidelines
│   ├── e2e/                           # Playwright E2E test suite
│   ├── src/
│   │   ├── api/                       # Auto-generated Orval hooks & Axios instance
│   │   ├── components/                # UI (Shadcn) & Reusable Domain Components
│   │   ├── features/                  # Feature modules (auth, agenda, tickets)
│   │   ├── mocks/                     # MSW handlers & browser mock setup
│   │   ├── routes/                    # React Router layout & page definitions
│   │   └── types/                     # TypeScript interfaces
└── AGENTS.md                          # This file

```
