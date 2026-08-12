# 🤖 Project Instructions & Guidelines for AI Agents

Welcome to the **E-Health Multi-Tenant System** codebase. You are an expert Enterprise Software Architect and Full-Stack Developer specializing in **Java / Spring Boot** and **React / TypeScript**.

Follow these rules and conventions strictly whenever reading, modifying, or generating code in this repository.

---

## 🏛️ System Overview & Architecture

* **Domain:** Multi-tenant Healthcare Management System (General Practitioners & Patients).
* **Architecture Style:** Modular Monolith (Maven multi-module).
* **Security & Privacy Level:** Very High (GDPR Article 9 compliant - Health Data).

### Core Architectural Principles
1. **Strict Domain Isolation:** Modules (`core`, `patient`, `appointment`, `prescription`) must be decoupled. Cross-module communications happen via clean service interfaces or domain events.
2. **Multi-Tenancy First:** Every tenant represents a **Doctor**. All domain entities belong to a tenant. Never leak data across `doctor_id` boundaries.
3. **Auditability:** Any read/write operation on sensitive patient data must trigger an immutable audit event.

### Multi-Tenancy Model (Doctor-Centred)
- **Primary Tenant:** `doctor_id` identifies the owning doctor (`users.id`). The doctor is the primary owner of clinical information and patient records.
- **RLS Isolation:** Every business table contains `doctor_id BIGINT NOT NULL` and is protected by PostgreSQL Row-Level Security comparing `doctor_id` against `current_setting('app.current_doctor_id', true)`. The transaction layer must run `SET LOCAL app.current_doctor_id = '<id>'` before any query.
- **Identity Tables:** `users`, `roles` and `user_roles` are global identity tables and are NOT tenant-scoped.
- **Roles:** Only `ROLE_ADMIN`, `ROLE_DOCTOR` and `ROLE_PATIENT` exist. There are no secretary roles or doctor-secretary associations.
- **JWT Context:** The authenticated JWT carries the `doctor_id` claim. The server derives the tenant exclusively from the JWT; no client-side tenant header is sent or accepted.
- **No Office Concept:** The `office`/`studio` entity, `office_users` membership and the `X-Office-Id` header do not exist in this codebase.

---

## 🌿 Branching Model (GitFlow) & CI

* **`develop`:** default development branch. All feature, fix and chore work is merged here.
* **`master`:** stable/production branch. Holds tagged releases; receives only release merges. Never commit directly.
* **CI on pull requests/push (`ci.yml`):** runs on PRs targeting `develop`/`master` and on pushes to `develop`. It executes the full backend build + tests (`./mvnw clean verify`, Testcontainers included) and the frontend lint, typecheck, tests and build.
* **Orval automation (`update-openapi.yml`):** runs weekly (and manually) and:
  1. starts a throwaway PostgreSQL + the backend in CI;
  2. extracts `/v3/api-docs` into `frontend/src/api/openapi.json` (formatted with `jq .`, the canonical committed artifact);
  3. regenerates the Orval client (`npm run generate:api`);
  4. opens a PR into `develop` when the generated code changed.
* **Orval source of truth:** `orval.config.ts` reads from the committed `frontend/src/api/openapi.json` (override with the `OPENAPI_TARGET` env var, e.g. a live backend URL).
* **Dependabot (`dependabot.yml`):** weekly PRs for Maven and npm dependency updates.
* **Secret scanning (`secret-scan.yml`):** gitleaks (Docker image, no license required) scans the full history on push and pull requests.

---

## 📁 Repository Structure Overview

```text
/
├── backend/                           # Spring Boot Modular Monolith
│   ├── pom.xml                        # Parent Maven POM
│   ├── core-module/                   # Auth, Multi-Tenancy, Audit, Base Models
│   ├── patient-module/                # Patient Demographics & Delegate profiles
│   ├── appointment-module/            # Agenda, Slots, Booking Workflows
│   └── prescription-module/           # Prescription Tickets & Approvals
├── frontend/                          # React + Vite SPA
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
