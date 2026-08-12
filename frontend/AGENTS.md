# 🤖 Frontend AI Agent Guidelines (React / Vite)

You are an expert Frontend Developer specializing in React, TypeScript, and modern SPA tools. You are working inside the `/frontend` app.

> **Context:** Read the project-wide rules in `../AGENTS.md` and the technical documentation in
> `../docs/` before changing anything, and keep that documentation up to date whenever behavior,
> contracts or security properties change.

## 🛠️ Stack & Standards

- **Framework & Bundler:** React 18/19 + Vite (TypeScript)
- **API Client & Hooks:** Axios + Orval (Auto-generated TanStack Query hooks from OpenAPI/Swagger)
- **Data Fetching:** TanStack Query (React Query)
- **UI & Styling:** Tailwind CSS + Shadcn UI (Radix UI primitives)
- **Testing:** Vitest + MSW (Unit/Integration), Playwright (E2E)

## 🔄 API Integration & Type Generation (Orval + Axios)

- **Source of Truth:** The backend OpenAPI 3 / Swagger spec (`http://localhost:8080/v3/api-docs`) is the single source of truth for all API contracts.
- **Client & Hook Generation:** Do NOT write manual API fetchers or TanStack Query hooks. Use **Orval** to automatically generate:
  - TypeScript interfaces/DTOs from OpenAPI schemas.
  - Custom **Axios** instance calls with automatic request/response interceptors (handling Auth JWT headers and error mapping).
  - Fully-typed **TanStack Query (React Query)** hooks (`useQuery`, `useMutation`).
- **Generation Command:** Run `npm run generate:api` whenever backend endpoints or DTOs change.
- **Auth & Tenancy:** Only the `Authorization: Bearer <token>` header is sent. There is no `X-Office-Id` header. The JWT carries the `office_id` claim and the user profile exposes `officeId`; the client never selects or sends a tenant id.

## 📐 React & TypeScript Conventions

- **Strict TypeScript:** No `any`. Define explicit type interfaces for all API payloads and component props.
- **Server State vs Local State:** Use **TanStack Query** (via Orval generated hooks) for all API data fetching, caching, and mutations. Reserve `useState` strictly for local UI states (e.g., modal visibility).
- **Component Design:** Prefer functional components with named exports. Keep business logic inside custom hooks.
- **UI Library:** Use **Shadcn UI** components. Customize styles exclusively using Tailwind utility classes (`cn()` helper).

## 🧪 Testing Strategy

- **Unit & Component Testing:** **Vitest** + **React Testing Library** for individual components and utility functions.
- **API Mocking (MSW):** Use **MSW (Mock Service Worker)** to mock REST endpoints at the network level for Vitest component tests, storybooks, and isolated local development.
- **End-to-End (E2E) Testing:** **Playwright** for critical cross-domain user flows:
  - Staff booking an appointment.
  - Physician issuing/approving a prescription ticket.
  - Multi-tenant data isolation verification (ensuring Office A cannot see Office B's data).

## 🛠️ Code Quality & Linting

- **Primary Linter:** **Oxlint** (for ultra-fast local checking and React Hooks rules validation).
- **Execution:** Run `npx oxlint` before commits or during local build checks.
