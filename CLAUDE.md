# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Name**: Industrial Hub — MSB (Medical System do Brasil)
**Domain**: Industrial operations management (OEE, QMS, Maintenance)
**Users**: ~53 internal users with roles OPERATOR / SUPERVISOR / ADMIN

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4.1, Spring Data JPA, Spring Security |
| Frontend | Angular 21, standalone components, signals, Angular Material |
| Database | PostgreSQL 16 (H2 in tests) |
| Build | Maven (`./mvnw`), npm |
| Infra | Docker Compose (`infra/docker/docker-compose.yml`) |

## Commands

```bash
# Backend
./mvnw clean package -DskipTests          # build
./mvnw test                               # all tests
./mvnw test -Dtest=ClassName              # single test class
./mvnw dependency:analyze                 # dependency audit

# Frontend (from apps/frontend/)
npm install
npm test -- --watch=false                 # all tests once
npm test -- --include=**/foo.spec.ts      # single spec
npm audit                                 # security audit
npm run build                             # production build
```

## Architecture

**Monorepo** with `apps/backend/` and `apps/frontend/`. Feature-first packages.

### Backend (`apps/backend/src/main/java/com/industrialhub/backend/`)

```
{feature}/
├── domain/          — JPA entities, enums
├── application/
│   ├── dto/         — request/response DTOs (records preferred)
│   └── usecase/     — one class per use case, @Service
├── infrastructure/  — Spring Data repositories, interface projections
└── presentation/    — @RestController, one per feature
common/              — cross-cutting: exception handler, validators, audit
```

- **No service layer between use case and repository** — use case calls repository directly
- **Interface projections** for read-only queries (avoid loading full entities)
- **JPQL only** — no native SQL except when strictly necessary for aggregations
- **DTOs as Java records** when possible
- Tests in `src/test/` mirroring `src/main/` structure

### Frontend (`apps/frontend/src/app/`)

```
{feature}/
├── {feature}.service.ts     — HTTP calls, interfaces/types
├── {page}/
│   ├── {page}.component.ts
│   ├── {page}.component.html
│   ├── {page}.component.scss
│   └── {page}.component.spec.ts
shared/
├── nav/                     — top navigation
└── ...
```

- **Standalone components** — no NgModules
- **Signals** for state (`signal<T>`, `computed()`, `input()`, `output()`)
- **`ChangeDetectionStrategy.OnPush`** mandatory
- **Lazy loading** for all feature routes
- Native control flow: `@if`, `@for`, `@switch`
- No `ngClass`/`ngStyle` — use class/style bindings

## Visual Identity (MSB)

Brand color: **`#0099B8`** (teal, from MSB logo)
Secondary: **`#006B82`** (dark teal), Accent: **`#00C4E8`** (light teal)
Background: **`#F4F6F9`**, Surface: **`#FFFFFF`**
Font: Inter (sans-serif), professional industrial look

## Domain Modules

| Module | Package | Sprints | Status |
|--------|---------|---------|--------|
| OEE (Overall Equipment Effectiveness) | `oee/` | 1–3 | ✅ done |
| Authentication | `common/auth/` | 4 | ✅ done |
| QMS (Quality Management System) | `qms/` | 5–6, 13–14 | ✅ done |
| Maintenance (TPM) | `maintenance/` | 7–8, 15 | ✅ done |
| Cross-module KPI + Reports + Analytics | `common/kpi/`, `common/presentation/` | 9–10, 16 | ✅ done |
| Security Hardening | `common/security/` | 11 | ✅ done |
| User Management | `common/auth/` | 12 | ✅ done |

## Key Conventions

- **Error responses**: `{ "message": "..." }` for 4xx/5xx
- **Date params**: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`
- **Pagination**: `Page<T>` with `@PageableDefault(size = 20)` for lists > 50 items
- **Auth**: JWT Bearer, roles checked via `@PreAuthorize("hasRole('SUPERVISOR')")`
- **`@RequestParam` validation**: controllers with `@Min`/`@Max`/`@NotNull` on `@RequestParam` **must** have `@Validated` on the class; violations throw `ConstraintViolationException` (not `MethodArgumentNotValidException`) — handled by `GlobalExceptionHandler` returning 400 (ADR-031)
- **No CLAUDE/agent references** in committed code or docs
