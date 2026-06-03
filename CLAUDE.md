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

Brand color: **`#56A4BB`** (azulFiltrado — sobriedade + dinamicidade, Manual de Marca p.21)
Secondary: **`#5F88A1`** (azulSafira), Accent: **`#9CE5EE`** (azulTecno)
Neutral: **`#818286`** (cinzaCirurgico), Background tint: **`#D9E4E8`** (cinzaSeguro)
Background: **`#F4F7F9`**, Surface: **`#FFFFFF`**
Text: **`#1F3A4A`** (azulProfundo), Deep text: **`#0E2230`** (azulNoturno)
Status: ok `#3FA66A`, warn `#E8A93C`, danger `#D24A4A`
Font: Inter (sans-serif), professional industrial look
Logo: `apps/frontend/public/msb-logo.png` (wordmark color), `msb-logo-white.png` (nav/login), `msb-mark.png` (icon)
Shell: topbar gradient `#1F3A4A → #5F88A1 → #56A4BB` (60px) + dark sidebar `#1F3A4A → #0E2230` (260px, drawer on mobile ≤900px)

## Domain Modules

| Module | Package | Sprints | Status |
|--------|---------|---------|--------|
| OEE (Overall Equipment Effectiveness) | `oee/` | 1–3, 17 | ✅ done |
| Authentication | `common/auth/` | 4 | ✅ done |
| QMS (Quality Management System) | `qms/` | 5–6, 13–14 | ✅ done |
| Maintenance (TPM) | `maintenance/` | 7–8, 15, 20 | ✅ done |
| Cross-module KPI + Reports + Analytics | `common/kpi/`, `common/presentation/` | 9–10, 16, 24, 28 | ✅ done |
| Security Hardening | `common/security/` | 11 | ✅ done |
| User Management | `common/auth/` | 12 | ✅ done |
| Alert Thresholds + Notifications | `common/` | 18 | ✅ done |
| Shift Management | `common/` | 19 | ✅ done |
| File Attachments | `common/` | 21 | ✅ done |
| SLA Rules + Escalation | `common/` | 22 | ✅ done |
| Multi-plant Support | `common/` | 23 | ✅ done |
| LGPD / Privacy | `common/` | 25 | ✅ done |
| PWA / Offline | `apps/frontend/` | 26 | ✅ done |
| Production (Dynamics import) | `production/` | 29 | ✅ done |
| Production Tracking (kanban por família) | `production/` | 30 | ✅ done |
| Sterilization Loads (Hub-managed) | `production/` | 31 | ✅ done |
| MRP Engine + Staffing + Planning Board | `production/` | 32 | ✅ done |
| BOM Import + Planning Report | `production/` | 33 | ✅ done |
| Production Overview + BOM Level 2 MRP | `production/` | 34 | ✅ done |
| Cache Caffeine (TTL 5 min) + NgxCharts trend chart + Security debt (SEC-112/113/069) | `production/`, `common/config/` | 35 | ✅ done |
| GED — Gestão de Documentos controlados (Document + DocumentRevision imutável + MinIO) | `qms/ged/` | 36 | ✅ done |
| CAPAS Formal — CorrectiveAction + PENDING_EFFECTIVENESS + lista cross-NC | `qms/` | 37 | ✅ done |

## Key Conventions

- **Error responses**: `{ "message": "..." }` for 4xx/5xx
- **Date params**: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`
- **Pagination**: `Page<T>` with `@PageableDefault(size = 20)` for lists > 50 items
- **Auth**: JWT Bearer, roles checked via `@PreAuthorize("hasRole('SUPERVISOR')")`
- **`@RequestParam` validation**: controllers with `@Min`/`@Max`/`@NotNull` on `@RequestParam` **must** have `@Validated` on the class; violations throw `ConstraintViolationException` (not `MethodArgumentNotValidException`) — handled by `GlobalExceptionHandler` returning 400 (ADR-031)
- **`qms/ged/` sub-module**: GED (Sprint 36) lives under `qms/ged/` with its own `domain/`, `application/`, `infrastructure/` and `presentation/` — not a top-level feature package. `GedController` is its own `@RestController`.
- **Bean name collision**: use case named `GetDocumentDownloadUrlUseCase` conflicts with the `GetDownloadUrlUseCase` bean in `common/storage/`; the GED use case is registered as `GedGetDownloadUrlUseCase` (class renamed to avoid `ConflictingBeanDefinitionException`).
- **`CapaController`** (Sprint 37): CAPA cross-NC list lives in a dedicated `CapaController`, separate from `QmsController`, to respect SRP.
- **No CLAUDE/agent references** in committed code or docs
