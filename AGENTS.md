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
2. **Multi-Tenancy First:** Every tenant represents a **Studio** (Medical Practice). All domain entities belong to a tenant. Never leak data across `tenant_id` / `studio_id` boundaries.
3. **Auditability:** Any read/write operation on sensitive patient data must trigger an immutable audit event.

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
