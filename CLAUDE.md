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
| GED & CAPAS Security Hardening (MIME validation, path traversal, TOCTOU fix) | `qms/ged/`, `qms/` | 38 | ✅ done |
| NC↔GED Link + CAPA Aging Dashboard + Relatório Executivo de Qualidade (iText 7) | `qms/`, `qms/ged/` | 39 | 🚧 em andamento |
| Gestão de Treinamentos e Competências (ISO 13485 §6.2) | `training/` | 40 | ⬜ planejado |
| Gestão de Calibração e MSA (ISO 13485 §7.6) | `maintenance/` | 41 | ⬜ planejado |
| Auditorias Internas (ISO 13485 §8.2.4) | `qms/audit/` | 42 | ⬜ planejado |
| Gestão de Risco / FMEA (ISO 14971) | `qms/risk/` | 43 | ⬜ planejado |
| Controle de Mudanças (ISO 13485 §4.1) | `common/changes/` | 44 | ⬜ planejado |
| Reclamações de Clientes + MDR (ISO 13485 §8.2.1 / ANVISA RDC 665/2022) | `qms/complaints/` | 45 | ⬜ planejado |
| Análise Crítica pela Direção (ISO 13485 §5.6) | `common/presentation/`, `common/application/` | 46 | ⬜ planejado |

## Key Conventions

- **Error responses**: `{ "message": "..." }` for 4xx/5xx
- **Date params**: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`
- **Pagination**: `Page<T>` with `@PageableDefault(size = 20)` for lists > 50 items
- **Auth**: JWT Bearer, roles checked via `@PreAuthorize("hasRole('SUPERVISOR')")`
- **`@RequestParam` validation**: controllers with `@Min`/`@Max`/`@NotNull` on `@RequestParam` **must** have `@Validated` on the class; violations throw `ConstraintViolationException` (not `MethodArgumentNotValidException`) — handled by `GlobalExceptionHandler` returning 400 (ADR-031)
- **`qms/ged/` sub-module**: GED (Sprint 36) lives under `qms/ged/` with its own `domain/`, `application/`, `infrastructure/` and `presentation/` — not a top-level feature package. `GedController` is its own `@RestController`.
- **Bean name collision**: use case named `GetDocumentDownloadUrlUseCase` conflicts with the `GetDownloadUrlUseCase` bean in `common/storage/`; the GED use case is registered as `GedGetDownloadUrlUseCase` (class renamed to avoid `ConflictingBeanDefinitionException`).
- **`CapaController`** (Sprint 37): CAPA cross-NC list lives in a dedicated `CapaController`, separate from `QmsController`, to respect SRP.
- **`GedFileValidator`** (Sprint 38, ADR-049): `@Component` following the same pattern as `ExcelFileValidator`; `validate(MultipartFile)` uses Apache Tika magic-byte detection and throws `InvalidGedFileException` (422); `sanitizeFilename()` is `public static` to allow reuse across use cases without duplication.
- **PII masking on revisions** (Sprint 38, ADR-049 §4): `uploadedBy` removed from `DocumentRevisionResponse` — field is retained on entity for audit trail but must not be exposed via API.
- **`DataIntegrityViolationException` → domain exception** (Sprint 38, ADR-049 §5): wrap `repository.save()` in try-catch for unique-constraint violations; convert to a domain exception (e.g. `DocumentCodeAlreadyExistsException`) returning 409 — prevents leaking DB constraint names in error responses.
- **TOCTOU fix pattern** (Sprint 38, ADR-049 §6): for read-then-write state transitions, use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on a dedicated repository method named `findByIdForUpdate` instead of the standard `findById`.
- **`NcDocumentLink`** (Sprint 39, ADR-050 §1): entidade de junção NC↔Documento em `qms/domain/` (não em `qms/ged/domain/`); campos de identidade com `updatable = false` — vínculo imutável. `linkedBy` retido na entidade para auditoria mas não exposto via API (padrão ADR-049 §4).
- **`DELETE NC↔GED link`** (Sprint 39, ADR-050 §2): URL semântica `DELETE /non-conformances/{ncId}/documents/{documentId}` — não usa UUID interno do link; use case usa `findByNonConformanceIdAndDocumentId`.
- **`iText 7 Community`** (Sprint 39, ADR-050 §6): dependência `com.itextpdf:itext7-core:7.2.6` (AGPL, adequado para uso interno); iText 2.1.7 (legacy MPL) foi descartado — não usar versão antiga.
- **`QmsReportController`** (Sprint 39, ADR-050 §9): controller dedicado em `qms/presentation/` para relatórios executivos (`/api/v1/qms/reports`), separado de `QmsController`, `CapaController` e `GedController` por SRP.
- **`training/` pacote top-level** (Sprint 40, ADR-051): módulo de treinamentos isolado de `qms/` e `maintenance/`; `TrainingRecord.username` é `String` — referência leve sem FK física para `User` (mesmo padrão de `CorrectiveAction.responsible`); `expiresAt` calculado no use case (`completedAt.plusMonths(validityMonths)`), não em trigger SQL.
- **`GedFileValidator` em `training/`** (Sprint 40, ADR-051 Decisão 3): upload de certificado PDF reutiliza `GedFileValidator.validate()` (MIME via Tika) e `GedFileValidator.sanitizeFilename()` (public static) — sem duplicação de lógica.
- **`TrainingExpiryAlertJob`** (Sprint 40, ADR-051 Decisão 5): job semanal (`cron = "0 0 8 * * MON"`, America/Sao_Paulo); debounce 144h para vencendo, 24h para vencido; notificação pessoal por colaborador (não broadcast).
- **Calibração em `maintenance/domain/`** (Sprint 41, ADR-052 Decisão 1): `CalibrationSchedule` e `CalibrationRecord` em `maintenance/domain/` — não novo pacote top-level; coesão com `Equipment` e `WorkOrder`.
- **`certificateDocumentId` vs `certificateStoragePath` mutuamente exclusivos** (Sprint 41, ADR-052 Decisão 2): ambos presentes → `400`; referência leve `certificateDocumentId: UUID` sem `@ManyToOne` para `Document` — evita acoplamento estrutural entre `maintenance` e `qms/ged`.
- **NC automática em calibração `OUT_OF_TOLERANCE`** (Sprint 41, ADR-052 Decisão 3): `CreateCalibrationRecordUseCase` chama `CreateNonConformanceUseCase` na mesma `@Transactional`; `recordedBy = "system"` identifica NCs automáticas no `AuditLog`.
- **`qms/audit/` sub-pacote** (Sprint 42, ADR-053 Decisão 1): auditorias ISO em `qms/audit/` para evitar colisão com `common/domain/AuditLog`; entidade nomeada `InternalAudit` (explícito); `AuditController` dedicado por SRP. `NcSeverity` reutilizado em `AuditFinding` — sem enum duplicado dentro do bounded context QMS.
- **Auto-geração de código sequencial** (Sprints 42–45): padrão `AUD-{ANO}-{NNN}` (auditorias), `CR-{ANO}-{NNN}` (mudanças), `REC-{ANO}-{NNN}` (reclamações); `countByCodeStartingWith("PREFIX-" + year)`; unique constraint captura colisão → `DataIntegrityViolationException` → 409 (ADR-049 §5).
- **`qms/risk/` sub-pacote** (Sprint 43, ADR-054 Decisão 1): FMEA em `qms/risk/`; `RiskController` dedicado; RPN calculado e persistido via método de domínio `recalculateRpn()` — chamado em toda criação e atualização; `RiskLevel.fromRpn()` como factory method com thresholds centralizados (`LOW ≤ 30`, `MEDIUM 31–100`, `HIGH 101–200`, `CRITICAL > 200`).
- **`CRITICAL → ACCEPTED` bloqueado** (Sprint 43, ADR-054 Decisão 4): riscos críticos não podem ser aceitos sem mitigação `COMPLETED` com `residualRpn ≤ 100` — implementa ISO 14971 §6; validação no `TransitionRiskStatusUseCase`.
- **`common/changes/` pacote transversal** (Sprint 44, ADR-055 Decisão 1): controle de mudanças é cross-cutting (afeta Manutenção, QMS, Produção) — não pertence a `qms/`; `ChangeRequestController` em `/api/v1/changes`; regra de identidade `requestedBy == principal` verificada no use case (não em SpEL).
- **`qms/complaints/` sub-pacote** (Sprint 45, ADR-056 Decisão 1): reclamações de clientes em `qms/complaints/`; `ComplaintController` dedicado; notificação ANVISA (`PUT /complaints/{id}/anvisa-report`) exclusiva para ADMIN; relatório MDR disponível apenas quando `reportedToAnvisa=true AND status=CLOSED` (`422` caso contrário).
- **`ManagementReviewController` em `common/presentation/`** (Sprint 46, ADR-057 Decisão 1): análise crítica é transversal — sem nova entidade JPA; `ManagementReviewData` é DTO record calculado sob demanda; cache Caffeine TTL 30 min adicionado ao `CacheConfig` existente (`maximumSize=10`); `SemaphoreChipComponent` standalone `OnPush` em `shared/semaphore-chip/` — reutilizável em outros dashboards.
- **No CLAUDE/agent references** in committed code or docs
