# Sprint Atual — Industrial Hub

## Sprint 4 ✅
**Objetivo**: Autenticação JWT completa — login, roles e proteção de todos os endpoints
**ADR**: ADR-004
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-013 | JWT Authentication Backend | 5 | ✅ concluído |
| US-014 | Login Page + Auth Guard (Frontend) | 3 | ✅ concluído |

### Critérios de Aceite
**US-013**
1. POST /api/v1/auth/login retorna JWT válido com `{ token, expiresInMs, username, role }`
2. Token expirado retorna 401 com body `{ message: "Token expirado" }`
3. Credenciais inválidas retornam 401 com body `{ message: "Credenciais inválidas" }`
4. Todos os endpoints GET /api/v1/oee/**, /workers, /maintenance/** exigem OPERATOR+
5. Endpoints de transição de status exigem SUPERVISOR+
6. Endpoints de admin (criação de usuário) exigem ADMIN
7. Senha armazenada com BCrypt (fator 12)
8. `User` entity com campos: id, username, password, role, active, email
9. Seed de usuários dev: admin/admin, supervisor/supervisor, operator/operator

**US-014**
1. Página /login com logo MSB, campos username e password, botão "Entrar"
2. Login bem-sucedido redireciona para /dashboard
3. Credenciais inválidas exibem mensagem de erro inline
4. JWT armazenado em localStorage sob chave `msb_token`
5. HTTP Interceptor adiciona `Authorization: Bearer <token>` em todas as requisições
6. AuthGuard redireciona para /login se token ausente ou expirado
7. Botão "Sair" no nav limpa o token e redireciona para /login
8. Role exibida no nav (ex: "SUPERVISOR")

---

## Sprint 5 ✅
**Objetivo**: QMS — cadastro e ciclo de vida de Não-Conformidades (NCs)
**ADR**: ADR-007
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-021 | Cadastro de Não-Conformidade | 5 | ✅ concluído |
| US-022 | Ciclo de vida (transição de status) de NC | 3 | ✅ concluído |
| US-023 | Listagem, detalhe e resumo de NCs + exportação CSV | 5 | ✅ concluído |

---

## Sprint 6 ✅
**Objetivo**: QMS — planos de ação corretiva (CAP) + notificações email
**ADR**: ADR-007
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-024 | Criar e listar ações corretivas | 5 | ✅ concluído |
| US-025 | Completar e excluir ações corretivas (auto-close) | 3 | ✅ concluído |
| US-026 | Notificações email assíncronas (NC crítica + fechamento) | 3 | ✅ concluído |

### Critérios de Aceite

---

#### US-021 — Cadastro de Não-Conformidade (5 pts)

**Backend**
1. `POST /api/v1/qms/non-conformances` retorna `201 Created` com `NcResponse` contendo todos os campos da entidade
2. Campos obrigatórios validados: `title` (não nulo, max 200 chars), `type` (enum `NcType`), `severity` (enum `NcSeverity`); falha retorna `400` com `{ "message": "..." }`
3. `description` é opcional (nullable)
4. `status` fixado em `OPEN` na criação — não deve ser aceito no request body
5. `reportedBy` preenchido automaticamente com o `username` do JWT (não enviado pelo cliente)
6. `reportedAt` preenchido com a data/hora UTC do servidor no momento da criação
7. Endpoint protegido: exige OPERATOR+ (`@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`)
8. Entidade `NonConformance` persistida com indexes em `status`, `severity`, `reportedAt` (conforme ADR-007)
9. Enums disponíveis:
   - `NcType`: `PROCESS`, `PRODUCT`, `SUPPLIER`, `EQUIPMENT`, `OTHER`
   - `NcSeverity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

**Frontend**
10. Rota `/qms/non-conformances/new` com formulário: título (input text), tipo (select), severidade (select), descrição (textarea opcional)
11. Validação client-side: título e tipo obrigatórios; botão "Registrar" desabilitado enquanto o formulário for inválido
12. Submit bem-sucedido redireciona para a listagem de NCs com snackbar "Não-conformidade registrada com sucesso"
13. Erro 400 exibe a mensagem retornada pela API em um snackbar de erro
14. Somente usuários OPERATOR+ visualizam o botão "Nova NC" na listagem

---

#### US-022 — Ciclo de vida (transição de status) de NC (3 pts)

**Backend**
1. `PUT /api/v1/qms/non-conformances/{id}/status` aceita body `{ "status": "IN_ANALYSIS" }` e retorna `200` com `NcResponse` atualizado
2. Transições permitidas (conforme ADR-007):
   - `OPEN → IN_ANALYSIS`
   - `IN_ANALYSIS → CLOSED`
   - `IN_ANALYSIS → OPEN` (re-abertura)
3. Transição inválida retorna `422 Unprocessable Entity` com body:
   ```json
   { "message": "Invalid transition from CLOSED to OPEN", "allowedNext": [] }
   ```
4. NC inexistente retorna `404` com `{ "message": "NC não encontrada" }`
5. Ao transitar para `CLOSED`: `closedAt` recebe data/hora UTC atual e `closedBy` recebe o `username` do JWT
6. Ao re-abrir (`IN_ANALYSIS → OPEN`): `closedAt` e `closedBy` são limpos (null)
7. Endpoint protegido: exige SUPERVISOR+ (`@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`)

**Frontend**
8. Na página de detalhe do NC, exibir botão(s) de transição de acordo com o status atual e o role do usuário:
   - OPEN → botão "Iniciar Análise" (visível para SUPERVISOR+)
   - IN_ANALYSIS → botões "Fechar NC" e "Re-abrir" (visível para SUPERVISOR+)
   - CLOSED → sem botão de transição
9. Confirmação via dialog do Angular Material antes de executar a transição
10. Após transição bem-sucedida, a página de detalhe é recarregada com o novo status e campos `closedAt`/`closedBy` (quando aplicável)
11. Erro 422 exibe a mensagem da API em um snackbar de erro

---

#### US-023 — Listagem, detalhe e resumo de NCs + exportação CSV (5 pts)

**Backend — Listagem**
1. `GET /api/v1/qms/non-conformances` retorna `Page<NcSummaryResponse>` com envelope `{ content, page, size, totalElements, totalPages }`
2. Suporta filtros via query params: `status` (enum), `severity` (enum), `type` (enum)
3. Paginação padrão: `size=20`, ordenação `reportedAt DESC` via `@PageableDefault`
4. Endpoint protegido: OPERATOR+

**Backend — Detalhe**
5. `GET /api/v1/qms/non-conformances/{id}` retorna `NcResponse` completo (incluindo lista de ações corretas)
6. NC inexistente retorna `404` com `{ "message": "NC não encontrada" }`

**Backend — Resumo**
7. `GET /api/v1/qms/non-conformances/summary` retorna:
   ```json
   {
     "totalOpen": 12,
     "totalInAnalysis": 5,
     "totalClosed": 30,
     "totalCritical": 3,
     "totalThisMonth": 8
   }
   ```
8. Campos calculados via JPQL (sem native SQL); endpoint protegido: OPERATOR+

**Backend — Exportação CSV**
9. `GET /api/v1/qms/non-conformances/export` retorna `Content-Type: text/csv` com header `Content-Disposition: attachment; filename="ncs-export.csv"`
10. Colunas CSV: `id`, `title`, `type`, `severity`, `status`, `reportedBy`, `reportedAt`, `closedBy`, `closedAt`
11. Endpoint protegido: SUPERVISOR+

**Frontend**
12. Rota `/qms/non-conformances` exibe cards de resumo (totalOpen, totalInAnalysis, totalClosed, totalCritical) no topo
13. Tabela paginada com colunas: título, tipo, severidade, status (chip colorido), registrado por, data
14. Filtros por status, severidade e tipo via dropdowns acima da tabela; "Limpar filtros" reseta a listagem
15. Botão "Exportar CSV" (SUPERVISOR+) dispara download do arquivo
16. Clique em uma linha navega para `/qms/non-conformances/{id}` (página de detalhe)
17. Página de detalhe exibe todos os campos + lista de ações corretivas (se houver)
18. Chip de status usa cores: OPEN=amber, IN_ANALYSIS=blue, CLOSED=green; severidade CRITICAL=red

---

---

## Sprint 7 ✅
**Objetivo**: Maintenance module — equipment registration and work order lifecycle (TPM)
**ADR**: ADR-008
**Status**: concluída

### User Stories
| ID | Title | Points | Status |
|----|-------|--------|--------|
| US-027 | Equipment CRUD (create, list, detail, update, soft-delete) | 5 | ✅ concluído |
| US-028 | Work Order lifecycle (create, list, status transitions) | 5 | ✅ concluído |

---

#### US-027 — Equipment CRUD (5 pts)

**Backend** ✅
1. `POST /api/v1/maintenance/equipment` returns `201 Created` with `EquipmentResponse`; requires ADMIN role
2. Required fields: `code` (unique, max 50 chars), `name` (max 200 chars), `type` (enum `EquipmentType`); optional: `location` (max 100 chars), `acquiredAt` (ISO date)
3. `status` defaults to `OPERATIONAL` on creation — not accepted in request body
4. Duplicate `code` returns `409 Conflict` with `{ "message": "Equipment code already exists: <code>" }`
5. `GET /api/v1/maintenance/equipment` returns list of active equipment (OPERATOR+); supports optional filters `type` and `status`
6. `GET /api/v1/maintenance/equipment/{id}` returns `EquipmentResponse` or `404` (OPERATOR+)
7. `PUT /api/v1/maintenance/equipment/{id}` updates `name`, `location`, `type`, `acquiredAt` only; `code` and `status` are immutable via this endpoint; returns `200` or `404` (ADMIN)
8. `DELETE /api/v1/maintenance/equipment/{id}` performs soft-delete (`active = false`); returns `204`; returns `409` if equipment has open or in-progress work orders (ADMIN)
9. Enums: `EquipmentType` = `MACHINE`, `TOOL`, `VEHICLE`, `INFRASTRUCTURE`; `EquipmentStatus` = `OPERATIONAL`, `UNDER_MAINTENANCE`, `DECOMMISSIONED`
10. Entity `Equipment` persisted with unique index on `code`, indexes on `status` and `type` (per ADR-008)

**Frontend** ⬜
11. Route `/maintenance/equipment` lists active equipment in a table: code, name, type, status (chip), location
12. "New Equipment" button (ADMIN only) navigates to `/maintenance/equipment/new`
13. Form fields: code, name, type (select), location (optional), acquiredAt (optional date picker)
14. Successful creation redirects to list with snackbar "Equipment registered successfully"
15. Row click navigates to `/maintenance/equipment/{id}` showing all fields and associated work orders list
16. Detail page shows "Edit" button (ADMIN) and "Deactivate" button (ADMIN); deactivate asks for confirmation
17. Status chip colors: OPERATIONAL=green, UNDER_MAINTENANCE=amber, DECOMMISSIONED=gray

---

#### US-028 — Work Order Lifecycle (5 pts)

**Backend** ✅
1. `POST /api/v1/maintenance/work-orders` returns `201` with `WorkOrderResponse` (SUPERVISOR+)
2. Required fields: `equipmentId` (UUID), `type` (enum `WorkOrderType`), `title` (max 200 chars), `priority` (enum `WorkOrderPriority`); optional: `description`, `assignedTo` (username)
3. `openedBy` set from JWT username; `openedAt` set to server UTC time; `status` defaults to `OPEN`
4. When `type = CORRECTIVE`: within the same `@Transactional`, set `equipment.status = UNDER_MAINTENANCE`
5. `GET /api/v1/maintenance/work-orders` returns `Page<WorkOrderResponse>` (OPERATOR+); filters: `equipmentId`, `type`, `status`, `priority`; default: `size=20`, sorted `openedAt DESC`
6. `PUT /api/v1/maintenance/work-orders/{id}/status` transitions status (SUPERVISOR+); body: `{ "status": "IN_PROGRESS" }`
7. Allowed transitions: `OPEN → IN_PROGRESS` (sets `startedAt`), `IN_PROGRESS → DONE` (sets `closedAt`), `OPEN → CANCELLED` (sets `closedAt`), `IN_PROGRESS → CANCELLED` (sets `closedAt`)
8. Invalid transition returns `422` with `{ "message": "Invalid transition from X to Y", "allowedNext": [...] }`
9. When transitioning to `DONE` and type is `CORRECTIVE`: if no other CORRECTIVE work orders with status `OPEN` or `IN_PROGRESS` exist for the equipment, set `equipment.status = OPERATIONAL`
10. Work order or equipment not found returns `404`
11. Enums: `WorkOrderType` = `CORRECTIVE`, `PREVENTIVE`; `WorkOrderPriority` = `LOW`, `MEDIUM`, `HIGH`, `URGENT`; `WorkOrderStatus` = `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`

**Frontend** ⬜
12. Equipment detail page (`/maintenance/equipment/{id}`) shows work orders section with list: title, type, priority chip, status chip, openedBy, openedAt
13. "New Work Order" button (SUPERVISOR+) opens inline form or navigates to form; fields: type, title, priority, description (optional), assignedTo (optional)
14. Standalone route `/maintenance/work-orders` lists all work orders paginated with filters: equipment, type, status, priority
15. Work order detail (inline in equipment page or separate route) shows transition buttons per allowed state machine (SUPERVISOR+ only), with confirmation dialog before each transition
16. Status chip colors: OPEN=amber, IN_PROGRESS=blue, DONE=green, CANCELLED=gray; priority URGENT=red chip
17. After status transition, work order list/detail refreshes; if equipment status changed (auto-OPERATIONAL), equipment detail also reflects new status

---

## Sprint 8 ✅
**Objetivo**: Maintenance — MTTR e métricas de ordens de serviço
**ADR**: ADR-008
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-029 | Work Order Metrics — MTTR por equipamento e global | 3 | ✅ concluído |

---

#### US-029 — Work Order Metrics / MTTR (3 pts)

**Backend**
1. `GET /api/v1/maintenance/work-orders/metrics` retorna `WorkOrderMetricsResponse` (OPERATOR+)
2. Query param opcional `?equipmentId=<uuid>`: se fornecido, retorna métricas do equipamento; sem ele, retorna métricas globais de todos os equipamentos
3. `mttr` = média de `(closedAt − startedAt)` em horas para OSs `CORRECTIVE` + `DONE` com `startedAt` não nulo; calculado em Java via `ChronoUnit.SECONDS` (sem SQL nativo — ADR-008 Decisão 4)
4. `mttr = null` quando nenhuma OS CORRECTIVE+DONE com `startedAt` existe no escopo
5. `totalOrders` = total de OSs no escopo; `openOrders` = OSs com status `OPEN` ou `IN_PROGRESS` no escopo
6. Response record:
   ```json
   { "mttr": 4.5, "totalOrders": 30, "openOrders": 3 }
   ```
7. Endpoint protegido: OPERATOR+
8. Use case `GetWorkOrderMetricsUseCase` criado em `application/usecase/`; endpoint adicionado em `MaintenanceController`

**Frontend**
9. Página de detalhe do equipamento (`/maintenance/equipment/{id}`) exibe card "Métricas": MTTR (h), Total OSs, OSs Abertas
10. MTTR exibido como "N/A" quando `null` (sem OS corretiva concluída)
11. Rota `/maintenance/work-orders` exibe card de métricas globais no topo: MTTR Global, OSs Abertas, Total OSs
12. Métricas carregadas via `ngOnInit`; falha de carregamento não bloqueia renderização da página (card exibe "—")

---

## Sprint 9 ✅
**Objetivo**: Cross-module KPI Dashboard + relatório semanal por email
**ADR**: ADR-009
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-030 | KPI Dashboard cross-module | 5 | ✅ concluído |
| US-031 | Relatório semanal por email (agendado) | 3 | ✅ concluído |

---

#### US-030 — KPI Dashboard cross-module (5 pts)

**Backend**
1. `GET /api/v1/kpi/summary` retorna JSON (OPERATOR+):
   ```json
   {
     "oeeAvgLast30Days": 0.78,
     "totalNcOpen": 5,
     "totalNcCritical": 2,
     "totalWorkOrdersOpen": 3,
     "mttrGlobalHours": 4.5,
     "activeEquipmentCount": 12
   }
   ```
2. Dados agregados em tempo real dos repositórios OEE, QMS e Maintenance — sem tabela nova (ADR-009 Decisão 3)
3. `oeeAvgLast30Days` = média de `(availableTimeMinutes / totalTimeMinutes)` das importações dos últimos 30 dias, calculada via JPQL no `ImportBatchRepository` (ou equivalente)
4. `mttrGlobalHours` = média Java de todas as OSs CORRECTIVE+DONE com `startedAt` não nulo (reutiliza lógica de US-029)
5. `KpiController` em `common/presentation/` com `GetKpiSummaryUseCase` em `common/application/`
6. Tempo de resposta ≤ 300ms com volume-alvo: 1k time_records + 200 NCs + 100 OSs (validado em US-036)

**Frontend**
7. Rota `/dashboard` exibe 6 cards KPI:
   - OEE médio (30 dias) — percentual com indicador de cor
   - NCs Abertas — contagem
   - NCs Críticas — contagem com chip vermelho se > 0
   - OSs Abertas — contagem
   - MTTR Global — horas com 1 decimal; "N/A" se null
   - Equipamentos Ativos — contagem
8. OEE < 65% → borda/ícone vermelho no card; NCs Críticas > 0 → chip vermelho pulsante
9. Auto-refresh a cada 5 minutos via `interval(300_000).pipe(startWith(0), switchMap(...))`
10. Skeleton loaders enquanto os dados carregam; erro exibe snackbar persistente (não auto-dismiss)
11. Dashboard é a rota padrão após login (já era `/dashboard` — nenhuma mudança de routing)

---

#### US-031 — Relatório semanal por email (3 pts)

**Backend**
1. `@Scheduled(cron = "0 0 7 * * MON", zone = "America/Sao_Paulo")` em `WeeklyReportScheduler` dispara toda segunda-feira às 7h
2. Relatório enviado para todos os usuários com role SUPERVISOR+ (buscados via `UserRepository`)
3. Conteúdo do email: OEE médio dos últimos 7 dias, novas NCs da semana (total + críticas), OSs abertas na segunda-feira, MTTR da semana (null → "N/A")
4. Lógica de coleta encapsulada em `WeeklyReportService` (separado do bean `@Scheduled` para testabilidade)
5. Envio via `JavaMailSender` assíncrono existente (`@Async`); falha de envio logada mas não aborta o scheduler
6. `@EnableScheduling` adicionado em `BackendApplication`
7. `POST /api/v1/admin/reports/weekly/send-now` (ADMIN) dispara envio imediato para validação sem aguardar segunda

---

## Sprint 10 ✅
**Objetivo**: Production readiness — audit trail, E2E tests, observabilidade e performance
**ADR**: ADR-009
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-033 | Audit Trail — log imutável de ações críticas | 5 | ✅ concluído |
| US-034 | E2E Tests com Playwright (6 jornadas) | 5 | ✅ concluído |
| US-035 | Observabilidade — Spring Boot Actuator + health indicator | 2 | ✅ concluído |
| US-036 | Performance benchmark — KPI e OEE dentro dos limites | 3 | ✅ concluído |

---

#### US-033 — Audit Trail (5 pts)

**Backend**
1. Entidade `AuditLog` em `common/domain/` com campos: `id` (UUID), `timestamp`, `username`, `action`, `entityType`, `entityId`, `details` (TEXT JSON), conforme ADR-009 Decisão 1
2. Índices: `idx_audit_timestamp`, `idx_audit_username`, `idx_audit_entity` (`entity_type, entity_id`)
3. `AuditService.log(username, action, entityType, entityId, details)` anotado com `@Async`; falha nunca aborta operação de negócio
4. `AsyncUncaughtExceptionHandler` em `AsyncConfig` loga exceções assíncronas (`log.error`) sem relançar
5. Constantes em enum `AuditAction`:
   ```
   NC_CREATED, NC_STATUS_CHANGED,
   ACTION_CREATED, ACTION_COMPLETED,
   WORK_ORDER_CREATED, WORK_ORDER_STATUS_CHANGED,
   EQUIPMENT_CREATED, EQUIPMENT_DELETED,
   IMPORT_CREATED
   ```
6. `AuditService.log()` chamado nos use cases: `CreateNonConformanceUseCase`, `TransitionNcStatusUseCase`, `CreateCorrectiveActionUseCase`, `CompleteCorrectiveActionUseCase`, `CreateWorkOrderUseCase`, `TransitionWorkOrderStatusUseCase`, `CreateEquipmentUseCase`, `DeleteEquipmentUseCase`
7. `GET /api/v1/admin/audit-log` (ADMIN) retorna `Page<AuditLogResponse>` com `size=50` padrão; filtros opcionais: `entityType`, `action`, `username`
8. Nenhum endpoint DELETE ou UPDATE exposto para `AuditLog` — imutabilidade garantida

---

#### US-034 — E2E Tests com Playwright (5 pts)

1. `@playwright/test` instalado como devDependency em `apps/frontend/`
2. `playwright.config.ts` em `apps/frontend/` configurado: `baseURL`, reporter HTML, `headless: true`; browser: Chromium apenas (ADR-009 Decisão 4)
3. Scripts em `package.json`: `"e2e": "playwright test"`, `"e2e:report": "playwright show-report"`
4. Page Objects em `apps/frontend/e2e/pages/`:
   - `login.page.ts` — fill + submit de credenciais
   - `dashboard.page.ts` — leitura de cards KPI
   - `nc.page.ts` — criar NC, transitar status
   - `maintenance.page.ts` — criar equipamento, criar OS, transitar status
5. Specs em `apps/frontend/e2e/specs/` (6 arquivos):
   - `auth.spec.ts`: login sucesso → redireciona `/dashboard`; login inválido → erro inline; rota protegida sem token → `/login`
   - `oee-import.spec.ts`: upload Excel válido → confirmação; arquivo inválido → erro 422 exibido
   - `oee-dashboard.spec.ts`: filtros de data aplicados → tabela atualiza; cards de resumo visíveis
   - `nc-lifecycle.spec.ts`: criar NC → listagem exibe nova NC; transitar OPEN→IN_ANALYSIS→CLOSED
   - `maintenance-wo.spec.ts`: criar equipamento → criar OS CORRECTIVE → equipamento UNDER_MAINTENANCE → concluir OS → OPERATIONAL
   - `role-access.spec.ts`: OPERATOR não vê botões SUPERVISOR+; ADMIN vê todos os botões
6. Todos os 6 specs passam com backend rodando via Docker Compose local

---

#### US-035 — Observabilidade / Health (2 pts)

**Backend**
1. `spring-boot-starter-actuator` adicionado ao `pom.xml`
2. `application.properties`:
   ```properties
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=when_authorized
   management.endpoint.health.roles=ADMIN
   info.app.version=@project.version@
   info.app.build-time=@maven.build.timestamp@
   ```
3. `DynamicsImportHealthIndicator` em `common/`: retorna `DOWN` com detalhe `"No import in last 30 days"` se nenhum `ImportBatch` com `periodDate` nos últimos 30 dias; `UP` caso contrário
4. `GET /actuator/health` sem autenticação retorna `{ "status": "UP" }` (detalhes ocultos)
5. `GET /actuator/health` autenticado como ADMIN retorna detalhes completos incluindo indicador de importação
6. `maven-resources-plugin` com filtering ativo para substituição de `@project.version@` no properties

---

#### US-036 — Performance Benchmark (3 pts)

**Backend**
1. `@SpringBootTest` com perfil `performance` valida via `StopWatch`:
   - `GET /api/v1/kpi/summary` ≤ 300ms com dataset: 1.000 time_records + 200 NCs + 100 OSs
   - `GET /api/v1/oee/dashboard?from=...&to=...` (30 dias, 13 workers) ≤ 400ms
2. `spring.jpa.properties.hibernate.generate_statistics=true` apenas no profile `performance` (jamais em `prod` ou `default`)
3. N+1 eliminado nos relacionamentos críticos via `@EntityGraph` onde necessário (WO→Equipment, NC→CorrectiveActions)
4. Todos os endpoints de leitura crítica passam nos limites definidos acima; falha de benchmark falha o test

---

## Sprint 11 ✅
**Objetivo**: API Security Hardening — rate limiting, security headers e proteções
**ADR**: ADR-021
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-065 | Rate limiting no login + security headers | 3 | ✅ concluído |
| US-066 | CORS hardening, JTI no JWT e log de falhas de login | 2 | ✅ concluído |

---

#### US-065 — Rate limiting e security headers (3 pts)

**Backend**
1. `bucket4j-spring-boot-starter` adicionado ao `pom.xml`
2. `LoginRateLimiter`: 5 tentativas/min por IP; bloqueio 5 min; 10 tentativas/h por username
3. Limite excedido retorna `429 Too Many Requests` com header `Retry-After: 300` e body `{ "message": "Muitas tentativas. Tente novamente em 5 minutos." }`
4. `SecurityHeadersFilter` adiciona em toda resposta: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Strict-Transport-Security`, `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`
5. `server.tomcat.max-http-form-post-size=2MB` configurado em `application.properties`
6. Rate limit armazenado em memória (`Caffeine`) — sem Redis

---

#### US-066 — CORS hardening, JTI e log de falhas (2 pts)

**Backend**
1. `allowedOrigins` configurável via `app.security.cors.allowed-origins` (property por profile: dev=localhost:4200, prod=domínio real)
2. JWT passa a incluir claim `jti` (UUID v4) — preparação para revogação futura
3. Falha de login registrada no `AuditLog` com `action = LOGIN_FAILED`, `username` tentado e `ipAddress`
4. Input size limit: upload bloqueado acima de 10 MB (já coberto em ADR-018); JSON body acima de 2 MB rejeitado com `413`

**Frontend**
5. Frontend exibe mensagem "Muitas tentativas de login. Aguarde N segundos." quando recebe `429` na tela de login; botão "Entrar" desabilitado durante o período de bloqueio com contador regressivo

---

## Sprint 12 ✅
**Objetivo**: Gestão de usuários — CRUD via UI + troca de senha self-service
**ADR**: ADR-010
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-037 | CRUD de usuários — backend (ADMIN) | 3 | ✅ concluído |
| US-038 | CRUD de usuários — frontend (ADMIN) | 3 | ✅ concluído |
| US-039 | Alteração de senha self-service | 2 | ✅ concluído |

---

#### US-037 — CRUD de usuários backend (3 pts)

**Backend**
1. `GET /api/v1/admin/users` retorna `List<UserResponse>` (ADMIN); campos: `id`, `username`, `email`, `role`, `active`, `mustChangePassword`
2. `POST /api/v1/admin/users` cria usuário com `username`, `email`, `role` e senha temporária; seta `mustChangePassword = true`; retorna `201 UserResponse`
3. Username duplicado retorna `409` com `{ "message": "Username já existe: <username>" }`
4. `PUT /api/v1/admin/users/{id}/role` aceita `{ "role": "SUPERVISOR" }` e atualiza role; retorna `200 UserResponse` ou `404`
5. `PUT /api/v1/admin/users/{id}/deactivate` seta `active = false`; retorna `204`; retorna `422` se for o último ADMIN ativo (`{ "message": "Não é possível desativar o único administrador ativo" }`)
6. `PUT /api/v1/admin/users/{id}/reactivate` seta `active = true`; retorna `204` ou `404`
7. Migration: coluna `must_change_password BOOLEAN NOT NULL DEFAULT FALSE` adicionada à tabela `users`
8. Validação de senha na criação: mínimo 8 caracteres, 1 maiúscula, 1 dígito; violação retorna `400`
9. `password` nunca exposto em nenhum DTO de resposta

**Frontend** _(parte de US-038)_

---

#### US-038 — CRUD de usuários frontend (3 pts)

**Frontend**
1. Rota `/admin/users` (ADMIN only) lista todos os usuários em tabela: username, email, role (chip), status (ativo/inativo), mustChangePassword (ícone alerta)
2. Botão "Novo Usuário" abre dialog com campos: username, email, role (select), senha temporária
3. Chip de role clicável abre dialog de alteração de role (select + confirmar)
4. Botão "Desativar" / "Reativar" por linha com confirmação; 422 exibe mensagem da API
5. Link "Usuários" visível no nav somente para ADMIN
6. Tentativa de acesso à rota por OPERATOR/SUPERVISOR redireciona para `/dashboard`
7. Snackbar de sucesso após cada operação (criar, alterar role, desativar, reativar)

---

#### US-039 — Alteração de senha self-service (2 pts)

**Backend**
1. `PUT /api/v1/users/me/password` aceita `{ "currentPassword": "...", "newPassword": "..." }` (qualquer usuário autenticado)
2. Valida `currentPassword` contra BCrypt; mismatch retorna `400` com `{ "message": "Senha atual incorreta" }`
3. Valida `newPassword`: mínimo 8 caracteres, 1 maiúscula, 1 dígito; violação retorna `400`
4. Após troca bem-sucedida, seta `mustChangePassword = false`, emite um **novo JWT** (sem o claim `mustChangePassword`) e o retorna no body `{ "token": "...", "expiresInMs": ... }` com status `200` — o frontend substitui o token em localStorage
5. Nova senha igual à senha atual retorna `400` com `{ "message": "A nova senha deve ser diferente da senha atual" }`

**Frontend**
6. Link "Alterar Senha" no menu do nav (para todos os usuários autenticados)
7. Formulário: senha atual, nova senha, confirmar nova senha
8. Validação client-side: nova senha === confirmação; botão desabilitado até validação passar
9. Se JWT contém `mustChangePassword = true` após login, frontend redireciona para `/change-password` antes de qualquer outra rota; guard impede navegação para outras rotas enquanto flag ativo
10. Após troca bem-sucedida, frontend substitui o token em localStorage com o novo JWT retornado, exibe snackbar "Senha alterada com sucesso" e redireciona para `/dashboard`

---

## Sprint 13 ⬜
**Objetivo**: Análise de Causa Raiz — 5-Porquês vinculado a NCs
**ADR**: ADR-015
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-052 | Backend de Análise de Causa Raiz (5-Porquês) | 3 | ⬜ pendente |
| US-053 | Frontend de 5-Porquês — wizard interativo | 3 | ⬜ pendente |

---

#### US-052 — Backend de RCA (3 pts)

1. `POST /api/v1/qms/non-conformances/{ncId}/rca` cria `RootCauseAnalysis` (SUPERVISOR+); campos: `why1` (obrigatório), `answer1` (obrigatório), `why2–why5` e `answer2–answer5` (opcionais), `rootCause` (optional string)
2. NC com `status = OPEN` retorna `422` com `{ "message": "RCA só pode ser criada após início da análise" }`
3. NC com RCA existente retorna `409` com `{ "message": "Esta NC já possui uma análise de causa raiz" }`
4. `GET /api/v1/qms/non-conformances/{ncId}/rca` retorna `RcaResponse` ou `404`
5. `PUT /api/v1/qms/non-conformances/{ncId}/rca` substitui RCA existente (SUPERVISOR+); bloqueado se NC estiver `CLOSED` (`422`)
6. `GET /api/v1/qms/non-conformances/{id}` (detalhe — US-023) passa a incluir campo `rca` nullable no response

---

#### US-053 — Frontend de 5-Porquês (3 pts)

1. Seção "Análise de Causa Raiz" na página de detalhe de NC, abaixo das ações corretivas
2. Se NC sem RCA e usuário é SUPERVISOR+: botão "Iniciar Análise"; desabilitado se NC está `OPEN` (tooltip "Inicie a análise da NC primeiro")
3. Wizard vertical: par "Por quê / Resposta" exibido como cards sequenciais; botão "+ Adicionar próximo Por quê" habilita par seguinte (máximo 5 pares)
4. Campo "Causa Raiz Identificada" aparece após preencher `why1 + answer1`
5. Botão "Salvar RCA" envia POST ou PUT dependendo de existência de RCA anterior
6. NC fechada (`CLOSED`): wizard em modo somente leitura (campos desabilitados, sem botão salvar)
7. OPERATOR: visualização somente leitura independente do status da NC

---

## Sprint 14 ⬜
**Objetivo**: Gestão de fornecedores + score de qualidade por fornecedor
**ADR**: ADR-017
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-057 | Cadastro de fornecedores (Supplier CRUD) | 3 | ⬜ pendente |
| US-058 | Score de qualidade e ranking de fornecedores | 3 | ⬜ pendente |

---

#### US-057 — Cadastro de fornecedores (3 pts)

**Backend**
1. `POST /api/v1/qms/suppliers` cria `Supplier` (ADMIN); campos: `code` (único), `name`, `contactEmail`, `contactPhone` (optional), `address` (optional), `onboardedAt` (optional date)
2. `code` duplicado retorna `409`
3. `GET /api/v1/qms/suppliers` lista fornecedores ativos (OPERATOR+)
4. `GET /api/v1/qms/suppliers/{id}` detalhe ou `404`
5. `PUT /api/v1/qms/suppliers/{id}` atualiza todos campos exceto `code` (ADMIN)
6. `PUT /api/v1/qms/suppliers/{id}/deactivate` soft-delete (ADMIN); `204`
7. `CreateNcRequest` e `NcResponse` ganham campo `supplierId` (UUID nullable)
8. `CreateNcUseCase`: se `type = SUPPLIER` e `supplierId` ausente → `400`; se `type != SUPPLIER` → `supplierId` ignorado
9. Migration: tabela `supplier` + coluna `supplier_id` nullable em `non_conformance`

**Frontend**
10. Rota `/qms/suppliers`: tabela com código, nome, email, status
11. Formulário de NC atualizado: quando tipo = "SUPPLIER" selecionado, aparece autocomplete de fornecedor (obrigatório)
12. Detalhe de NC exibe nome do fornecedor (quando presente) com link para `/qms/suppliers/{id}`

---

#### US-058 — Score de qualidade de fornecedores (3 pts)

**Backend**
1. `GET /api/v1/qms/suppliers/{id}/quality-score?days=90` retorna `SupplierQualityScore`: `{ supplierId, supplierName, totalNcs, criticalNcs, highNcs, qualityScore }`
2. Score = `100 - (criticalNcs*5 + highNcs*2 + mediumNcs + lowNcs*0.5) / max(totalNcs, 1) * 100` (limitado a 0–100)
3. `GET /api/v1/qms/suppliers/quality-ranking?days=90` retorna `List<SupplierQualityScore>` ordenado por `qualityScore ASC` (piores primeiro)
4. `days` default 90; SUPERVISOR+

**Frontend**
5. Detalhe do fornecedor (`/qms/suppliers/{id}`): card de score com gauge de cor (verde ≥ 80, amarelo 60–79, vermelho < 60), totalNcs, criticalNcs
6. Dropdown de período no card de score: 30 / 90 / 180 dias
7. Rota `/qms/suppliers/ranking`: tabela ordenada por score ASC com barra de progresso colorida por linha

---

## Sprint 15 ⬜
**Objetivo**: Manutenção preventiva — planos recorrentes e auto-geração de OSs
**ADR**: ADR-011
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-040 | Planos de manutenção preventiva — CRUD | 5 | ⬜ pendente |
| US-041 | Auto-geração de OSs preventivas pelo scheduler | 3 | ⬜ pendente |
| US-042 | Calendário de manutenção preventiva (frontend) | 3 | ⬜ pendente |

---

#### US-040 — Planos de manutenção preventiva CRUD (5 pts)

**Backend**
1. `POST /api/v1/maintenance/schedules` cria `MaintenanceSchedule`; retorna `201 ScheduleResponse` (SUPERVISOR+)
2. Campos obrigatórios: `equipmentId` (UUID), `title` (max 200 chars), `priority` (enum `WorkOrderPriority`), `recurrence` (enum `ScheduleRecurrence`: `DAILY`, `WEEKLY`, `MONTHLY`)
3. Regras de validação por recorrência:
   - `WEEKLY`: `dayOfWeek` obrigatório (1–7); `dayOfMonth` ignorado
   - `MONTHLY`: `dayOfMonth` obrigatório (1–28); `dayOfWeek` ignorado
   - `DAILY`: ambos ignorados
4. Equipamento inativo (`active = false`) retorna `422` com `{ "message": "Equipamento inativo não pode receber plano" }`
5. `nextRunAt` calculado no use case com base na recorrência e data atual
6. `GET /api/v1/maintenance/schedules` retorna `List<ScheduleResponse>` de planos ativos (OPERATOR+); filtro opcional: `?equipmentId=<uuid>`
7. `GET /api/v1/maintenance/schedules/{id}` retorna `ScheduleResponse` ou `404` (OPERATOR+)
8. `PUT /api/v1/maintenance/schedules/{id}` atualiza `title`, `description`, `priority`, `recurrence`, `dayOfWeek`, `dayOfMonth`; recalcula `nextRunAt`; retorna `200` ou `404` (SUPERVISOR+)
9. `PUT /api/v1/maintenance/schedules/{id}/deactivate` seta `active = false`; retorna `204` (SUPERVISOR+)
10. Entidade `MaintenanceSchedule` e enum `ScheduleRecurrence` criados conforme ADR-011
11. Migration: tabela `maintenance_schedule` + coluna nullable `schedule_id` em `work_order`

**Frontend** _(parte de US-042)_

---

#### US-041 — Auto-geração de OSs preventivas (3 pts)

**Backend**
1. `MaintenanceSchedulerJob` com `@Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")` chama `RunDueSchedulesUseCase.execute()`
2. `RunDueSchedulesUseCase` busca todos os planos ativos com `nextRunAt <= hoje`; para cada um, cria `WorkOrder` do tipo `PREVENTIVE` com `openedBy = "scheduler"`, `scheduleId` preenchido
3. Após criar cada OS, avança `nextRunAt` conforme regra de recorrência e persiste (`lastRunAt = hoje`)
4. `@EnableScheduling` adicionado em `BackendApplication`
5. Falha ao criar uma OS loga `ERROR` e continua processando os demais planos (não aborta o job)
6. `POST /api/v1/admin/maintenance/schedules/run-now` (ADMIN) dispara `RunDueSchedulesUseCase` imediatamente; retorna `200 { "created": N }` com número de OSs criadas
7. `WorkOrderResponse` inclui campo `scheduleId` (UUID nullable) para rastreabilidade

---

#### US-042 — Calendário de manutenção preventiva (3 pts)

**Frontend**
1. Rota `/maintenance/schedules` lista planos ativos em tabela: equipamento, título, recorrência, próxima execução, status (ativo/inativo)
2. Botão "Novo Plano" (SUPERVISOR+) navega para `/maintenance/schedules/new` com formulário completo
3. Formulário mostra/oculta campos `dayOfWeek` e `dayOfMonth` dinamicamente conforme recorrência selecionada
4. Criação bem-sucedida → snackbar "Plano criado" + redirect para listagem
5. Rota `/maintenance/calendar` exibe grade mensal (semanas em linhas, dias em colunas)
6. Cada dia exibe badges com os planos programados para aquela data (calculados no frontend com base em `nextRunAt` e recorrência)
7. Clique em um badge abre painel lateral com detalhe do plano + botão "Desativar" (SUPERVISOR+)
8. Calendário suporta navegação mensal (mês anterior / próximo) via botões no topo
9. Calendário implementado sem biblioteca externa — renderizado com `@for` sobre matriz de `LocalDate` gerada no componente

---

## Sprint 16 ⬜
**Objetivo**: Advanced analytics — trend charts e pareto por módulo
**ADR**: ADR-012
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-043 | Analytics de OEE — trend semanal + gráficos | 4 | ⬜ pendente |
| US-044 | Analytics de QMS — pareto e trend de NCs | 3 | ⬜ pendente |
| US-045 | Analytics de Manutenção — MTTR mensal + distribuição de OSs | 3 | ⬜ pendente |

---

#### US-043 — Analytics de OEE (4 pts)

**Backend**
1. `GET /api/v1/analytics/oee/trend?weeks=12` retorna `OeeTrendResponse`:
   ```json
   { "weekLabels": ["2026-W01", ...], "oeeValues": [0.82, ...], "sampleCounts": [5, ...] }
   ```
2. `weeks` default 12, max 52; valores `null` onde não há dados na semana
3. OEE por semana = média de `(availableTimeMinutes / totalTimeMinutes)` de todos os `ImportBatch` com `periodDate` na semana ISO; calculado em Java com `Collectors.groupingBy`
4. SUPERVISOR+

**Frontend**
5. Rota `/analytics/oee` exibe:
   - Line chart: OEE % por semana (eixo Y: 0–100%; linha de referência em 65%)
   - Tabela abaixo do gráfico: semana, OEE %, nº de importações
6. Dropdown para selecionar período: 4, 8, 12, 26, 52 semanas
7. Botão "Imprimir" chama `window.print()` (CSS `@media print` oculta nav e sidebar)
8. Componente `LineChartComponent` de `shared/charts/` reutilizado

---

#### US-044 — Analytics de QMS (3 pts)

**Backend**
1. `GET /api/v1/analytics/nc/pareto?days=30` retorna `NcParetoResponse`:
   ```json
   { "byType": { "PROCESS": 12, "PRODUCT": 5 }, "bySeverity": { "CRITICAL": 3, "HIGH": 8 } }
   ```
2. `days` default 30, opções: 30, 90, 180; Java stream sobre NCs no período
3. `GET /api/v1/analytics/nc/trend?weeks=12` retorna `TimeSeriesResponse` com contagem de NCs abertas por semana
4. SUPERVISOR+

**Frontend**
5. Rota `/analytics/qms` exibe:
   - Bar chart: NCs por tipo (ordenado DESC)
   - Bar chart: NCs por severidade (CRITICAL em vermelho, HIGH em laranja, MEDIUM em amarelo, LOW em verde)
   - Line chart: novas NCs por semana
6. Dropdowns de período independentes para pareto (30/90/180 dias) e trend (4/8/12 semanas)
7. Componentes `BarChartComponent` e `LineChartComponent` de `shared/charts/`

---

#### US-045 — Analytics de Manutenção (3 pts)

**Backend**
1. `GET /api/v1/analytics/maintenance/mttr-trend?months=6` retorna `MttrTrendResponse`:
   ```json
   { "monthLabels": ["2026-01", "2026-02", ...], "mttrValues": [4.2, null, 3.8, ...] }
   ```
2. `months` default 6, max 24; MTTR médio mensal das OSs CORRECTIVE+DONE; `null` onde sem OS concluída no mês
3. `GET /api/v1/analytics/maintenance/wo-summary` retorna distribuição de OSs por status e por tipo:
   ```json
   { "byStatus": { "OPEN": 5, "IN_PROGRESS": 2, "DONE": 30, "CANCELLED": 1 }, "byType": { "CORRECTIVE": 20, "PREVENTIVE": 18 } }
   ```
4. SUPERVISOR+

**Frontend**
5. Rota `/analytics/maintenance` exibe:
   - Line chart: MTTR médio mensal (eixo Y: horas; pontos nulos exibidos como gap na linha)
   - Doughnut chart: distribuição de OSs por status (cores: OPEN=amber, IN_PROGRESS=blue, DONE=green, CANCELLED=gray)
   - Doughnut chart: distribuição por tipo (CORRECTIVE vs PREVENTIVE)
6. Dropdown para período de MTTR: 3, 6, 12, 24 meses
7. Componentes `LineChartComponent` e `DoughnutChartComponent` de `shared/charts/`

---

## Sprint 17 ⬜
**Objetivo**: Planned Downtime — separação de paradas planejadas no cálculo de OEE
**ADR**: ADR-025
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-073 | Backend de registro de paradas planejadas | 3 | ⬜ pendente |
| US-074 | Integração com cálculo de OEE e frontend | 3 | ⬜ pendente |

---

#### US-073 — Registro de paradas planejadas (3 pts)

**Backend**
1. `POST /api/v1/oee/planned-downtimes` cria `PlannedDowntime` (SUPERVISOR+); campos: `equipmentId` (UUID, optional — null = parada de planta inteira), `date`, `durationMinutes`, `reason` (enum `DowntimeReason`), `description`
2. Enum `DowntimeReason`: `PREVENTIVE_MAINTENANCE`, `SCHEDULED_SETUP`, `HOLIDAY`, `OTHER`
3. `GET /api/v1/oee/planned-downtimes` lista com filtros `?date=`, `?equipmentId=` (OPERATOR+)
4. `PUT /api/v1/oee/planned-downtimes/{id}` atualiza; `DELETE /{id}` remove (SUPERVISOR+)
5. Migration: tabela `planned_downtime`

---

#### US-074 — OEE com paradas planejadas (3 pts)

**Backend**
1. `GET /api/v1/oee/dashboard?excludePlannedDowntime=true` subtrai `plannedDowntimeMinutes` do denominador: `Disponibilidade = availableTimeMinutes / (totalTimeMinutes - plannedDowntimeMinutes)`
2. Default `excludePlannedDowntime=false` — comportamento atual preservado
3. `GET /api/v1/analytics/oee/trend` também suporta `?excludePlannedDowntime=true`

**Frontend**
4. Toggle "Excluir paradas planejadas" no dashboard de OEE e na rota `/analytics/oee`
5. Rota `/oee/planned-downtimes`: calendário mensal com badges de paradas por dia; formulário de registro
6. Tooltip nos cards de OEE indica modo atual: "Incluindo paradas planejadas" / "Excluindo paradas planejadas"

---

## Sprint 18 ⬜
**Objetivo**: Threshold alerts configuráveis + central de notificações in-app
**ADR**: ADR-013
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-046 | Configuração de thresholds de alerta | 3 | ⬜ pendente |
| US-047 | Motor de avaliação de alertas + notificação email | 4 | ⬜ pendente |
| US-048 | Central de notificações in-app (sino no nav) | 3 | ⬜ pendente |

---

#### US-046 — Configuração de thresholds (3 pts)

**Backend**
1. `POST /api/v1/admin/alert-thresholds` cria `AlertThreshold` (ADMIN); campos: `metric` (enum `AlertMetric`), `threshold` (double), `emailEnabled` (bool); retorna `201`
2. Enums: `AlertMetric` = `OEE_AVG_BELOW`, `NC_CRITICAL_ABOVE`, `WO_URGENT_PENDING_HOURS`
3. `GET /api/v1/admin/alert-thresholds` retorna lista de thresholds (ADMIN)
4. `PUT /api/v1/admin/alert-thresholds/{id}` atualiza `threshold` e `emailEnabled`; retorna `200` ou `404`
5. `DELETE /api/v1/admin/alert-thresholds/{id}` remove threshold; retorna `204`
6. Seed de thresholds default: OEE < 65%, NCs críticas > 3, OSs urgentes pendentes > 4h

**Frontend**
7. Rota `/admin/alert-thresholds` (ADMIN): tabela com métrica, valor limite, email habilitado, toggle ativo
8. Formulário de criação: select de métrica + input numérico de threshold + toggle de email
9. Linha da tabela editável inline (valor + toggle email); botão salvar por linha

---

#### US-047 — Motor de avaliação de alertas (4 pts)

**Backend**
1. `AlertEvaluatorJob` com `@Scheduled(cron = "0 0/30 * * * *")` chama `AlertEvaluatorUseCase.execute()`
2. Para `OEE_AVG_BELOW`: calcula OEE médio dos últimos 30 dias; dispara se < threshold
3. Para `NC_CRITICAL_ABOVE`: conta NCs com `severity=CRITICAL` e `status` não-CLOSED; dispara se > threshold
4. Para `WO_URGENT_PENDING_HOURS`: conta OSs com `priority=URGENT` e `status=OPEN` com `openedAt + threshold horas < now()`; dispara se > 0
5. Debounce: não dispara nova notificação para a mesma métrica se existe `Notification` criada nos últimos 60 minutos (query por `createdAt`)
6. Se `emailEnabled = true`: envia email para todos SUPERVISOR+ via `JavaMailSender` assíncrono
7. `POST /api/v1/admin/alert-thresholds/evaluate-now` (ADMIN) dispara avaliação imediata para testes

---

#### US-048 — Central de notificações in-app (3 pts)

**Backend**
1. `GET /api/v1/notifications` retorna `Page<NotificationResponse>` do usuário autenticado (não lidas primeiro, depois por `createdAt DESC`); size=20
2. `PUT /api/v1/notifications/{id}/read` marca como lida (`readAt = now()`); retorna `200`
3. `PUT /api/v1/notifications/read-all` marca todas as não lidas do usuário como lidas; retorna `204`
4. `GET /api/v1/notifications/unread-count` retorna `{ "count": N }` — usado para badge do sino

**Frontend**
5. Ícone de sino no `NavComponent` com badge numérico (contagem não lidas); badge some quando = 0
6. Clique abre overlay/panel com lista das últimas 10 notificações; notificações CRITICAL com fundo vermelho, WARNING com âmbar
7. Botão "Marcar todas como lidas" no topo do panel
8. Polling de `unread-count` a cada 60 segundos via `interval(60_000)`
9. Notificação não lida: fonte bold; lida: normal; clique marca como lida e fecha o panel

---

## Sprint 19 ⬜
**Objetivo**: Gestão de turnos e rastreabilidade de registros por turno
**ADR**: ADR-016
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-054 | Cadastro de turnos (Shift CRUD) | 2 | ⬜ pendente |
| US-055 | Associação automática de OSs e importações OEE a turno | 3 | ⬜ pendente |
| US-056 | Filtro e analytics por turno | 3 | ⬜ pendente |

---

#### US-054 — Cadastro de turnos (2 pts)

**Backend**
1. `POST /api/v1/admin/shifts` cria `Shift` (ADMIN); campos: `name`, `startTime` (ISO time), `endTime`, `overnight` (bool)
2. Sobreposição de turno com turno ativo existente retorna `422` com `{ "message": "Turno sobrepõe turno existente: {nome}" }`
3. `GET /api/v1/admin/shifts` retorna lista de turnos (OPERATOR+)
4. `PUT /api/v1/admin/shifts/{id}` atualiza campos (ADMIN); valida sobreposição
5. `PUT /api/v1/admin/shifts/{id}/deactivate` desativa turno (ADMIN); `204`

**Frontend**
6. Rota `/admin/shifts` (ADMIN): tabela com nome, início, fim, overnight (ícone), status
7. Formulário de criação: nome, hora início (timepicker), hora fim (timepicker), toggle "Turno noturno (passa meia-noite)"
8. Linha de turno com chip de horário: ex "06:00 – 14:00"

---

#### US-055 — Associação automática de turnos (3 pts)

**Backend**
1. `ShiftResolverService.resolveCurrentShift()` busca turno ativo com intervalo cobrindo `LocalTime.now()` (considera `overnight`); retorna `Optional<Shift>`
2. `CreateWorkOrderUseCase` chama `ShiftResolverService`; associa `shift` à OS criada (null se nenhum turno ativo)
3. Migration: coluna `shift_id UUID NULLABLE` em `work_order` e `import_batch`
4. `WorkOrderResponse` e `ImportBatchResponse` incluem campo `shiftId` e `shiftName` (nullable)
5. `GET /api/v1/maintenance/work-orders` aceita filtro `?shiftId=<uuid>`

---

#### US-056 — Analytics por turno (3 pts)

**Backend**
1. `GET /api/v1/analytics/oee/trend?shiftId=<uuid>&weeks=12` retorna OEE médio por semana apenas dos `ImportBatch` do turno especificado
2. `GET /api/v1/analytics/maintenance/wo-summary?shiftId=<uuid>` retorna distribuição de OSs do turno

**Frontend**
3. Dropdown "Turno" adicionado aos filtros em `/maintenance/work-orders` e `/analytics/oee`
4. Card de OS exibe chip de turno (ex: "Tarde") quando presente
5. Página `/analytics/oee` exibe dropdown de seleção de turno; ao selecionar, reconsulta o endpoint com `shiftId`

---

## Sprint 20 ⬜
**Objetivo**: Peças e insumos — catálogo, consumo em OSs e alertas de reposição
**ADR**: ADR-014
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-049 | Cadastro de peças (catálogo) | 3 | ⬜ pendente |
| US-050 | Consumo de peças em ordens de serviço | 4 | ⬜ pendente |
| US-051 | Alertas de estoque mínimo | 2 | ⬜ pendente |

---

#### US-049 — Cadastro de peças (3 pts)

**Backend**
1. `POST /api/v1/maintenance/spare-parts` cria `SparePart` (ADMIN); campos: `code` (único), `name`, `category`, `unit`, `stockQty`, `minStockQty`; retorna `201`
2. `code` duplicado retorna `409`
3. `GET /api/v1/maintenance/spare-parts` lista peças ativas (OPERATOR+); filtros: `category`, `belowMin=true`
4. `GET /api/v1/maintenance/spare-parts/{id}` retorna detalhe ou `404`
5. `PUT /api/v1/maintenance/spare-parts/{id}` atualiza `name`, `category`, `unit`, `minStockQty` (ADMIN); `stockQty` imutável via este endpoint
6. `PUT /api/v1/maintenance/spare-parts/{id}/stock` ajuste manual de estoque (ADMIN); body `{ "quantity": N, "reason": "..." }`; `quantity` pode ser positivo (entrada) ou negativo (saída); bloqueia se resultado < 0

**Frontend**
7. Rota `/maintenance/spare-parts`: tabela com código, nome, categoria, unidade, estoque atual, estoque mínimo; linhas com estoque abaixo do mínimo têm row vermelha
8. Botão "Nova Peça" (ADMIN) → formulário completo
9. Coluna "Ajustar Estoque" (ADMIN): dialog de ajuste com campo de quantidade e motivo

---

#### US-050 — Consumo de peças em OSs (4 pts)

**Backend**
1. `POST /api/v1/maintenance/work-orders/{id}/parts` registra consumo (SUPERVISOR+); body: `{ "sparePartId": UUID, "quantity": N }
2. Dentro do mesmo `@Transactional`: cria `WorkOrderPart`, decrementa `sparePart.stockQty -= quantity`
3. Estoque insuficiente retorna `422` com `{ "message": "Estoque insuficiente: disponível N, solicitado M" }`
4. OS inexistente ou peça inexistente retorna `404`
5. `GET /api/v1/maintenance/work-orders/{id}/parts` lista peças consumidas da OS (OPERATOR+); retorna `List<WorkOrderPartResponse>`
6. `DELETE /api/v1/maintenance/work-orders/{id}/parts/{partId}` remove consumo e restaura estoque (SUPERVISOR+); retorna `204`

**Frontend**
7. Seção "Peças Utilizadas" na página de detalhe da OS com tabela: peça, quantidade, adicionada por, data
8. Botão "+ Adicionar Peça" (SUPERVISOR+): dialog com autocomplete de peça (busca por código ou nome) + campo quantidade
9. Botão de lixeira por linha (SUPERVISOR+) com confirmação
10. Estoque atual da peça exibido no dialog de adição para informar o técnico

---

#### US-051 — Alertas de estoque mínimo (2 pts)

**Backend**
1. `AddWorkOrderPartUseCase`: após decrementar estoque, se `stockQty < minStockQty`, chama `NotificationService.broadcast()` com título "Estoque baixo: {nome da peça}" (via ADR-013)
2. Debounce: não dispara notificação de estoque baixo para a mesma peça se já existe `Notification` nas últimas 24h com o título correspondente
3. `GET /api/v1/maintenance/spare-parts?belowMin=true` retorna apenas peças abaixo do mínimo (usado no dashboard)

**Frontend**
4. Card "Estoque Crítico" na rota `/maintenance/dashboard` (ou na listagem de equipamentos): lista de peças com `stockQty < minStockQty` com quantidade atual e mínima
5. Badge numérico "Estoque Crítico" no submenu de Maintenance no nav (contagem de peças abaixo do mínimo)

---

## Sprint 21 ⬜
**Objetivo**: Anexos — upload de documentos e imagens em NCs e OSs
**ADR**: ADR-018
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-059 | Backend de upload e storage de anexos (MinIO) | 5 | ⬜ pendente |
| US-060 | Frontend de upload, listagem e download de anexos | 3 | ⬜ pendente |

---

#### US-059 — Backend de attachments (5 pts)

**Backend**
1. `software.amazon.awssdk:s3` adicionado ao `pom.xml`; MinIO adicionado ao `docker-compose.yml`; bucket `industrial-hub-attachments` criado automaticamente no startup (via `S3Client.createBucketIfNotExists`)
2. `POST /api/v1/attachments` (multipart/form-data) aceita campos: `entityType` (`NonConformance` | `WorkOrder`), `entityId` (UUID), `file` (binário); max 10 MB; tipos aceitos: `image/jpeg`, `image/png`, `image/webp`, `application/pdf`
3. Tipo inválido retorna `400`; tamanho excedido retorna `413`
4. Arquivo salvo em `{entityType.lower()}/{entityId}/{UUID}-{filename}` no bucket; `Attachment` persistido no banco; retorna `201 AttachmentResponse`
5. `GET /api/v1/attachments?entityType=X&entityId=Y` lista anexos da entidade (OPERATOR+)
6. `GET /api/v1/attachments/{id}/download-url` retorna `{ "url": "...", "expiresAt": "..." }` (URL pré-assinada S3, TTL 15 minutos) (OPERATOR+)
7. `DELETE /api/v1/attachments/{id}` remove do storage e do banco (SUPERVISOR+); `204`
8. `StorageService` abstrai `S3Client` — testável com mock; configuração por properties (`app.storage.*`)

---

#### US-060 — Frontend de anexos (3 pts)

1. Seção "Anexos" na página de detalhe de NC e de OS
2. Botão "Anexar Arquivo" (OPERATOR+): file picker limitado a imagens e PDF; validação client-side de tamanho (10 MB) antes do upload
3. Progress bar durante upload; erro de upload exibe snackbar com mensagem da API
4. Lista de anexos: ícone de tipo (PDF/imagem), nome original, tamanho formatado (KB/MB), data de upload, botão download
5. Clique em "Download" → `GET /download-url` → `window.open(url)` (nova aba)
6. Imagens: thumbnail 80×80px via URL pré-assinada do mesmo endpoint
7. SUPERVISOR+: botão lixeira por anexo com confirmação; `DELETE` remove e atualiza lista

---

## Sprint 22 ⬜
**Objetivo**: SLA configurável e escalação automática de itens vencidos
**ADR**: ADR-019
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-061 | Configuração de regras de SLA | 3 | ⬜ pendente |
| US-062 | Job de escalação automática e sinalização de SLA vencido | 4 | ⬜ pendente |

---

#### US-061 — Configuração de SLA (3 pts)

**Backend**
1. `POST /api/v1/admin/sla-rules` cria `SlaRule` (ADMIN); campos: `entityType` (enum `SlaEntityType`: `NC`, `WORK_ORDER`), `classifierValue` (string — ex: "CRITICAL", "URGENT"), `slaHours` (int), `escalateByEmail` (bool)
2. `GET /api/v1/admin/sla-rules` lista regras (ADMIN)
3. `PUT /api/v1/admin/sla-rules/{id}` atualiza `slaHours` e `escalateByEmail`; retorna `200`
4. `DELETE /api/v1/admin/sla-rules/{id}` remove regra; `204`
5. Seed de regras default no `DataInitializer`: NC/CRITICAL→48h, NC/HIGH→72h, WO/URGENT→4h, WO/HIGH→24h
6. Migration: tabela `sla_rule` + colunas `sla_breached BOOLEAN DEFAULT FALSE` e `sla_breached_at TIMESTAMP` em `non_conformance` e `work_order`

**Frontend**
7. Rota `/admin/sla-rules` (ADMIN): tabela com entidade, classificador, SLA (horas), email, botões editar/excluir
8. Formulário de criação: select entityType, input classifierValue, input slaHours, toggle email
9. Inline edit por linha (slaHours + toggle email) com botão salvar

---

#### US-062 — Job de escalação automática (4 pts)

**Backend**
1. `EscalationJob` com `@Scheduled(cron = "0 0 * * * *")` chama `EscalationUseCase.execute()`
2. Para cada `SlaRule` ativa, busca entidades abertas com `(reportedAt/openedAt + slaHours) < now()` e `slaBreached = false`
3. Para cada item encontrado: seta `slaBreached = true`, `slaBreachedAt = now()`, persiste
4. Cria `Notification` via `NotificationService` (ADR-013): severity=CRITICAL, broadcast para SUPERVISOR+
5. Se `escalateByEmail = true`: envia email para SUPERVISOR+ (assíncrono, via JavaMailSender)
6. Idempotente: entidades com `slaBreached = true` já não são reprocessadas
7. `POST /api/v1/admin/sla-rules/run-now` (ADMIN) dispara `EscalationUseCase` imediatamente; retorna `{ "breachedNcs": N, "breachedWorkOrders": M }`
8. `GET /api/v1/qms/non-conformances?slaBreached=true` e `GET /api/v1/maintenance/work-orders?slaBreached=true` (novos filtros)

**Frontend**
9. Chip "SLA Vencido" (vermelho, animação pulse) em listagens e detalhes de NCs e OSs com `slaBreached = true`
10. Painel "SLA em Risco" no dashboard KPI (`/dashboard`) com contagens de NCs e OSs com SLA vencido; clique navega para listagem filtrada
11. Notificações de SLA vencido exibidas no sino (ADR-013) com severidade CRITICAL

---

## Sprint 23 ⬜
**Objetivo**: Multi-plant support — dimensão de planta/unidade produtiva
**ADR**: ADR-020
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-063 | Cadastro de plantas e associação de usuários | 4 | ⬜ pendente |
| US-064 | Filtro de dados por planta em todos os módulos | 5 | ⬜ pendente |

---

#### US-063 — Cadastro de plantas (4 pts)

**Backend**
1. `POST /api/v1/admin/plants` cria `Plant` (ADMIN); campos: `code` (único), `name`, `address`, `timezone`; retorna `201`
2. `GET /api/v1/admin/plants` lista plantas ativas (ADMIN)
3. `PUT /api/v1/admin/plants/{id}` atualiza; `PUT /{id}/deactivate` desativa; `204`
4. `PUT /api/v1/admin/users/{id}/plants` vincula usuário a lista de plantas (ADMIN); body: `{ "plantIds": [uuid, uuid] }`
5. Seed: planta default `{ code: "HQ", name: "Matriz", isDefault: true }` no `DataInitializer`
6. Migration: tabela `plant`, tabela `user_plant`, colunas `plant_id NOT NULL` em `equipment`, `non_conformance`, `import_batch` (preenchidas com planta default via `UPDATE`)

**Frontend**
7. Rota `/admin/plants` (ADMIN): tabela de plantas + formulário de criação
8. Na página de detalhe do usuário (`/admin/users/{id}`): seção "Plantas" com checkboxes para vincular/desvincular

---

#### US-064 — Filtro de dados por planta (5 pts)

**Backend**
1. `PlantContextFilter` (OncePerRequestFilter): resolve planta(s) do usuário autenticado via `UserPlantRepository`; injeta em `PlantContext` (ThreadLocal)
2. ADMIN: vê todas as plantas (sem filtro); OPERATOR/SUPERVISOR: veem apenas suas plantas
3. Todos os use cases de listagem consultam `PlantContext.currentPlantIds()` para filtrar queries
4. Query param `?plantId=<uuid>` em endpoints de listagem permite que ADMINs filtrem por planta específica
5. `WorkOrder.plant` derivado do `equipment.plant` na criação — não aceito no request body

**Frontend**
6. Selector de planta no nav (ADMIN): dropdown com todas as plantas; seleção persiste em `localStorage`
7. OPERATOR/SUPERVISOR: se vinculado a 1 planta, planta exibida no nav sem dropdown; se 2+, exibe dropdown
8. Chip de planta em cards de NC, OS e equipamentos (omitido quando usuário tem acesso a apenas 1 planta)

---

## Sprint 24 ⬜
**Objetivo**: OEE Benchmarking — comparativo entre trabalhadores, turnos e tipos de equipamento
**ADR**: ADR-026
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-075 | Endpoints de benchmarking OEE (worker, shift, equipment-type) | 3 | ⬜ pendente |
| US-076 | Frontend de benchmarking com charts comparativos | 3 | ⬜ pendente |

---

#### US-075 — Endpoints de benchmarking (3 pts)

**Backend**
1. `GET /api/v1/analytics/oee/benchmark/workers?from=&to=` retorna `BenchmarkResponse` com ranking por trabalhador: `{ ranking: [{ dimension, oeeAvg, sampleCount, stdDev }], best, worst, overallAvg }` (SUPERVISOR+)
2. `GET /api/v1/analytics/oee/benchmark/shifts?from=&to=` ranking por turno (requer Sprint 17 — `shiftId` em `ImportBatch`); retorna campos null para dados sem turno associado
3. `GET /api/v1/analytics/oee/benchmark/equipment-type?from=&to=` ranking por `EquipmentType`
4. `GET /api/v1/analytics/oee/benchmark/period-comparison?from=&to=` retorna `{ current: TimeSeriesResponse, previous: TimeSeriesResponse, improvementPct: double }`
5. Período máximo: 90 dias (validado no use case); excedido retorna `400`
6. `stdDev` calculado em Java; `null` quando `sampleCount < 3`

---

#### US-076 — Frontend de benchmarking (3 pts)

1. Nova aba "Benchmark" na rota `/analytics/oee`
2. Bar chart horizontal: ranking de trabalhadores por OEE DESC (barra colorida: verde ≥ 65%, amarela 50–64%, vermelha < 50%)
3. Bar chart: ranking por tipo de equipamento
4. Cards "Melhor" e "Pior" performer com nome, OEE % e desvio padrão
5. Section "Comparação de Período": dois line charts sobrepostos (período atual em azul, anterior em cinza tracejado); card com `improvementPct` (verde se positivo, vermelho se negativo)
6. Toggle "Linha de referência OEE Classe Mundial (85%)" adiciona linha horizontal em todos os charts

---

## Sprint 25 ⬜
**Objetivo**: LGPD compliance — retenção, anonimização e direito ao esquecimento
**ADR**: ADR-022
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-067 | Job de retenção e anonimização automática de dados | 4 | ⬜ pendente |
| US-068 | Direito ao esquecimento + exportação de dados pessoais | 3 | ⬜ pendente |

---

#### US-067 — Job de retenção automática (4 pts)

**Backend**
1. `DataRetentionJob` com `@Scheduled(cron = "0 0 2 1 * *")` (1º de cada mês às 2h)
2. Anonimiza usuários desativados há > 2 anos: `username → "[usuario-{id8chars}]"`, `email → null`, `password → BCrypt("*invalid*")`
3. Anonimiza `AuditLog` com `timestamp` > 5 anos: `username → "[anonimizado]"`, `ipAddress → null`, `details → null`
4. Anonimiza campos pessoais em `NonConformance` e `WorkOrder` com data > 5 anos
5. Deleta fisicamente `Notification` com `createdAt` > 90 dias
6. Cada bloco de anonimização em transação independente — falha de um não cancela os demais; log de ERROR por bloco com falha
7. `POST /api/v1/admin/data-retention/run-now` (ADMIN) dispara job manualmente; retorna estatísticas `{ "anonymizedUsers": N, "anonymizedAuditLogs": M, "deletedNotifications": K }`
8. `GET /api/v1/admin/data-retention/preview` (ADMIN) lista candidatos sem executar

**Entidade `ConsentRecord`**
9. Tabela `consent_record` criada; seed de registro de consentimento para todos os usuários existentes com versão "v1.0"

---

#### US-068 — Direito ao esquecimento e exportação (3 pts)

**Backend**
1. `POST /api/v1/admin/users/{id}/anonymize` (ADMIN): anonimização imediata do usuário e dados associados; retorna `200 { "anonymized": true, "affectedEntities": {...} }`
2. Operação registrada no `AuditLog` com `action = USER_ANONYMIZED` (irreversível)
3. `GET /api/v1/users/me/data-export` (autenticado): exporta JSON com dados pessoais do próprio usuário: perfil, NCs abertas por ele, OSs criadas por ele, entradas de audit log com seu username

**Frontend**
4. Rota `/admin/lgpd` (ADMIN): tabela paginada de candidatos à anonimização (usuários inativos > 2 anos, registros antigos), botão "Executar Retenção"
5. Botão "Anonimizar" por linha (usuário): dialog com texto de confirmação digitável "CONFIRMAR" antes de executar
6. Link "Exportar meus dados" no menu de perfil do usuário (qualquer autenticado): dispara download do JSON de dados pessoais

---

## Sprint 26 ⬜
**Objetivo**: Progressive Web App — instalação, cache offline e fila de NCs offline
**ADR**: ADR-023
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-069 | Service Worker, manifest e cache de leitura offline | 3 | ⬜ pendente |
| US-070 | Fila offline para criação de NC + banner de atualização | 3 | ⬜ pendente |

---

#### US-069 — Service Worker e cache offline (3 pts)

1. `ng add @angular/pwa` executado; `manifest.webmanifest` com `theme_color: "#0099B8"`, `display: "standalone"`, `start_url: "/dashboard"`
2. `ngsw-config.json` configurado com strategy `freshness` para: `/api/v1/maintenance/equipment`, `/api/v1/kpi/summary`, `/api/v1/qms/non-conformances` (timeout 3–5s; fallback para cache)
3. Service worker ativo apenas em `production` build (`serviceWorker: true` em `angular.json`)
4. `SwUpdate` service: quando nova versão detectada, exibe banner no topo da página "Nova versão disponível" com botão "Recarregar"
5. Ícones PWA gerados em 72, 96, 128, 144, 152, 192, 384, 512 px
6. Botão "Instalar App" no nav: detecta `beforeinstallprompt`, exibe apenas quando instalação disponível

---

#### US-070 — Fila offline para NC (3 pts)

1. `OfflineQueueService` armazena `POST /api/v1/qms/non-conformances` em IndexedDB quando network falha
2. `navigator.onLine` + `fromEvent(window, 'online')` monitora conectividade; ao retornar online, drena a fila sequencialmente
3. Snackbar persistente "Sem conexão. NC salva localmente — será enviada quando a conexão retornar."
4. Fila exibida no menu do nav: badge "N pendentes" quando há NCs offline; lista de NCs aguardando sync
5. Outras mutações (PUT, DELETE) falham com snackbar "Operação indisponível offline"
6. Após sync bem-sucedido: snackbar "N NC(s) sincronizadas com sucesso"

---

## Sprint 27 ⬜
**Objetivo**: Outbound webhooks para integração com ERP e ferramentas externas
**ADR**: ADR-024
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-071 | Gerenciamento de subscriptions de webhook | 3 | ⬜ pendente |
| US-072 | Entrega de eventos e retry automático | 4 | ⬜ pendente |

---

#### US-071 — Webhook subscriptions (3 pts)

**Backend**
1. `POST /api/v1/admin/webhooks` cria `WebhookSubscription` (ADMIN); campos: `url`, `secret` (optional), `events` (set de `WebhookEvent`), `description`
2. Enums `WebhookEvent`: `NC_CREATED`, `NC_STATUS_CHANGED`, `NC_CRITICAL_OPENED`, `WORK_ORDER_CREATED`, `WORK_ORDER_STATUS_CHANGED`, `EQUIPMENT_DECOMMISSIONED`, `SLA_BREACHED`
3. `GET /api/v1/admin/webhooks` lista subscriptions (ADMIN)
4. `PUT /api/v1/admin/webhooks/{id}` atualiza; `DELETE /{id}` remove
5. `POST /api/v1/admin/webhooks/{id}/test` envia payload de teste imediato; retorna `{ "responseCode": 200, "durationMs": 245 }`
6. `GET /api/v1/admin/webhooks/{id}/deliveries` retorna últimas 50 entregas com status, responseCode, attempt

**Frontend**
7. Rota `/admin/webhooks` (ADMIN): tabela com URL, eventos (chips), status última entrega (verde/vermelho), botão "Testar"
8. Formulário de criação: URL, secret (opcional, masked), checkboxes de eventos, descrição

---

#### US-072 — Entrega de eventos e retry (4 pts)

**Backend**
1. `WebhookDispatchService.dispatch(event, payload)` com `@Async`: busca subscriptions ativas para o evento, envia via `RestTemplate` com timeout 5s
2. Payload: `{ "event": "NC_CRITICAL_OPENED", "timestamp": "...", "payload": { ...campos da entidade } }`
3. Se `secret` presente: header `X-Hub-Signature-256: sha256=<hmac-sha256>` calculado sobre payload JSON
4. Resposta não-2xx: agenda retry com backoff exponencial (5s → 30s → 2min); após 3 falhas: `active = false`, `Notification` ADMIN "Webhook {url} desativado após 3 falhas"
5. `WebhookDelivery` entity persistida por tentativa: `webhookId`, `event`, `attempt`, `responseCode`, `durationMs`, `createdAt`
6. Integração: `WebhookDispatchService.dispatch()` chamado nos use cases: `CreateNonConformanceUseCase`, `TransitionNcStatusUseCase`, `CreateWorkOrderUseCase`, `TransitionWorkOrderStatusUseCase`, `EscalationUseCase`

---

## Sprint 28 ⬜
**Objetivo**: Dashboard customizável por usuário com widgets drag-and-drop
**ADR**: ADR-027
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-077 | Backend de persistência de layout de dashboard | 2 | ⬜ pendente |
| US-078 | Frontend de dashboard com widgets personalizáveis | 5 | ⬜ pendente |

---

#### US-077 — Backend de layout persistido (2 pts)

**Backend**
1. `GET /api/v1/users/me/dashboard` retorna `{ "widgetsJson": "[...]" }` — layout salvo; se não existir, retorna layout default calculado com base no role do usuário
2. `PUT /api/v1/users/me/dashboard` salva `widgetsJson` (TEXT, max 10 KB); retorna `200`
3. `DELETE /api/v1/users/me/dashboard` remove layout personalizado (reset para default); retorna `204`
4. Entidade `UserDashboardConfig` criada; `username` com `UNIQUE` constraint
5. Migration: tabela `user_dashboard_config`

---

#### US-078 — Frontend de dashboard customizável (5 pts)

1. Rota `/dashboard` renderiza widgets em CSS Grid 3 colunas; layout carregado via `GET /api/v1/users/me/dashboard`
2. Botão "Personalizar" no canto superior direito ativa modo de edição: widgets exibem handles de drag (⠿) e botão "×"
3. Drag-and-drop via HTML5 Drag-and-Drop API: arrastar widget para nova posição reordena o grid em tempo real
4. Catálogo lateral em modo de edição: lista de widgets disponíveis para o role do usuário não presentes no dashboard atual; clique adiciona widget
5. Widgets disponíveis por role: OPERATOR (6 KPI cards), SUPERVISOR+ (+ OEE Trend, NC Pareto); cada widget é um standalone component que busca dados independentemente
6. Botão "Salvar Layout" no modo de edição chama `PUT /api/v1/users/me/dashboard`; botão "Resetar" chama `DELETE` e restaura default
7. Widgets carregam independentemente — skeleton loader por widget; falha de um widget não impede os demais
8. Layout persistido no servidor — consistente entre dispositivos e sessões

---

## Backlog de Sprints

| Sprint | Objetivo | US | ADR |
|--------|----------|----|-----|
| ✅ Sprint 1 | OEE core: import + cálculo de disponibilidade | US-001, US-002 | ADR-005 |
| ✅ Sprint 2 | OEE extensions: dashboard, análise, resumo | US-003, US-004, US-005, US-006, US-007, US-008, US-009 | ADR-006 |
| ✅ Sprint 3 | OEE read: worker directory, process efficiency, CSV export | US-010, US-011, US-012 | ADR-005/006 |
| ✅ Sprint 4 | Autenticação JWT | US-013, US-014 | ADR-004 |
| ✅ Sprint 5 | QMS: cadastro e ciclo de vida de NCs | US-021, US-022, US-023 | ADR-007 |
| ✅ Sprint 6 | QMS: planos de ação corretiva (CAP) + email | US-024, US-025, US-026 | ADR-007 |
| 🔄 Sprint 7 | Maintenance: equipment registration + work orders | US-027, US-028 | ADR-008 |
| ⬜ Sprint 8 | Maintenance: MTTR + metrics | US-029 | ADR-008 |
| ⬜ Sprint 9 | Cross-module KPI dashboard + weekly report | US-030, US-031 | ADR-009 |
| ⬜ Sprint 10 | Audit trail, E2E (Playwright), health, performance | US-033, US-034, US-035, US-036 | ADR-009 |
| ⬜ Sprint 11 | API Security Hardening (rate limiting, headers, CORS) | US-065, US-066 | ADR-021 |
| ⬜ Sprint 12 | User management UI + self-service password | US-037, US-038, US-039 | ADR-010 |
| ⬜ Sprint 13 | Análise de causa raiz — 5-Porquês em NCs | US-052, US-053 | ADR-015 |
| ⬜ Sprint 14 | Gestão de fornecedores + score de qualidade | US-057, US-058 | ADR-017 |
| ⬜ Sprint 15 | Preventive maintenance scheduling + calendar | US-040, US-041, US-042 | ADR-011 |
| ⬜ Sprint 16 | Advanced analytics: OEE trend, NC pareto, MTTR trend | US-043, US-044, US-045 | ADR-012 |
| ⬜ Sprint 17 | Planned Downtime — paradas planejadas no OEE | US-073, US-074 | ADR-025 |
| ⬜ Sprint 18 | Threshold alerts + central de notificações in-app | US-046, US-047, US-048 | ADR-013 |
| ⬜ Sprint 19 | Gestão de turnos + rastreabilidade por turno | US-054, US-055, US-056 | ADR-016 |
| ⬜ Sprint 20 | Peças e insumos (spare parts inventory) | US-049, US-050, US-051 | ADR-014 |
| ⬜ Sprint 21 | Anexos — upload de documentos e imagens | US-059, US-060 | ADR-018 |
| ⬜ Sprint 22 | SLA e escalação automática | US-061, US-062 | ADR-019 |
| ⬜ Sprint 23 | Multi-plant support (dimensão de planta/unidade) | US-063, US-064 | ADR-020 |
| ⬜ Sprint 24 | OEE Benchmarking — comparativo por trabalhador/turno | US-075, US-076 | ADR-026 |
| ⬜ Sprint 25 | LGPD compliance e data retention | US-067, US-068 | ADR-022 |
| ⬜ Sprint 26 | Progressive Web App (PWA + offline queue) | US-069, US-070 | ADR-023 |
| ⬜ Sprint 27 | Outbound webhooks para integração com sistemas externos | US-071, US-072 | ADR-024 |
| ⬜ Sprint 28 | Dashboard customizável por usuário (widgets drag-and-drop) | US-077, US-078 | ADR-027 |
| ⬜ Sprint 29 | Production module: importação do Dynamics (produtos, estoque, OPs, tempos) | US-079, US-080, US-081 | ADR-028 |
| ⬜ Sprint 30 | Acompanhamento visual de produção e tracking de OPs por família | US-082, US-083 | ADR-029 |
| ⬜ Sprint 31 | Gestão de cargas de esterilização (Hub-managed) | US-084 | ADR-029 |
| ⬜ Sprint 32 | Motor MRP, planejamento por família e staffing por OP | US-085, US-086, US-087 | ADR-030 |

---

## Sprint 29 ⬜
**Objetivo**: Production module — importação de dados do Dynamics (catálogo, estoque, OPs, tempos de ciclo e lead times)
**ADR**: ADR-028
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-079 | Importação de catálogo de produtos e famílias do Dynamics | 4 | ⬜ pendente |
| US-080 | Importação de OPs e snapshots de estoque do Dynamics | 4 | ⬜ pendente |
| US-081 | Importação de tempos de ciclo e lead times + histórico de importações | 3 | ⬜ pendente |

---

#### US-079 — Importação de catálogo de produtos e famílias (4 pts)

**Backend**
1. `POST /api/v1/production/import/products` (multipart, ADMIN) importa planilha com colunas: `dynamics_code`, `name`, `type` (FINISHED/INTERMEDIATE/RAW_MATERIAL), `family_code`, `family_name`, `unit`, `requires_sterilization`
2. Upsert por `dynamics_code`: produto existente é atualizado; novo produto é criado; `ProductFamily` criada automaticamente se `family_code` não existe
3. Retorna `ImportProductionBatch` com `createdRecords`, `updatedRecords`, `errorRecords`; erros por linha reportados (ex: `type` inválido)
4. `StockLevel` criado com `qty = 0` para produto novo (evitar null pointer no MRP)
5. `GET /api/v1/production/families` lista famílias ativas (OPERATOR+)
6. `GET /api/v1/production/products` lista produtos com filtros: `family`, `type`, `active` (OPERATOR+)
7. `GET /api/v1/production/products/{id}` retorna detalhe com estoque atual e tempo de ciclo mais recente (OPERATOR+)

**Frontend**
8. Rota `/production/import` (ADMIN): abas por tipo de importação; cada aba tem área de drop de arquivo + botão de upload + tabela de resultado (criados/atualizados/erros)
9. Rota `/production/products`: tabela com código, nome, família, tipo (chip), `requiresSterilization` (ícone)
10. Painel de detalhe do produto com: estoque atual, mínimo, lead time, tempo de ciclo vigente

---

#### US-080 — Importação de OPs e estoque (4 pts)

**Backend**
1. `POST /api/v1/production/import/stock` (SUPERVISOR+) importa planilha: `dynamics_code`, `qty`, `snapshot_date`; upsert por `(product, snapshotDate)` — não sobrescreve snapshots de datas diferentes
2. `POST /api/v1/production/import/production-orders` (SUPERVISOR+) importa planilha: `op_number`, `dynamics_code`, `status`, `planned_qty`, `produced_qty`, `start_date`, `due_date`
3. Upsert de OPs por `dynamics_order_number` — atualiza campos do Dynamics, **preserva** `sterilizationLoad`, `plannedPeople`, `peopleOverridden`
4. OPs com `dynamics_code` não encontrado no catálogo: reportadas como erro (não bloqueiam o restante da importação)
5. `GET /api/v1/production/production-orders` lista OPs com filtros: `family`, `status`, `productType`, `overdue` (OPERATOR+)
6. `GET /api/v1/production/stock` lista posição mais recente de estoque por produto com flag `belowMin` (SUPERVISOR+)

---

#### US-081 — Importação de ciclos e lead times + histórico (3 pts)

**Backend**
1. `POST /api/v1/production/import/cycle-times` (ADMIN) importa: `dynamics_code`, `seconds_per_unit`, `effective_date`; cria novo `CycleTime` versionado se valor diferente do vigente para o produto
2. `POST /api/v1/production/import/lead-times` (ADMIN) importa: `dynamics_code`, `lead_time_days`, `min_stock_qty`, `batch_size`; atualiza campos do `Product`
3. `GET /api/v1/production/import/history` retorna lista de `ImportProductionBatch` com tipo, arquivo, data, contagens (SUPERVISOR+); paginado, size=20

**Frontend**
4. Aba "Tempos de Ciclo" na tela de importação: upload + resultado + tabela histórica de ciclos por produto (versões)
5. Aba "Lead Times" na tela de importação: upload + tabela resultante atualizada
6. Aba "Histórico" na tela de importação: tabela de todos os imports com tipo, arquivo, data, usuário, registros criados/atualizados/erros

---

## Sprint 30 ⬜
**Objetivo**: Acompanhamento visual de produção — tracking de OPs por família e por status
**ADR**: ADR-029
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-082 | Backend de tracking visual por família (display status calculado) | 3 | ⬜ pendente |
| US-083 | Frontend de acompanhamento — kanban de OPs por família e status | 5 | ⬜ pendente |

---

#### US-082 — Backend de tracking visual (3 pts)

**Backend**
1. `GET /api/v1/production/tracking/families` retorna `List<FamilyTrackingResponse>` com OPs não-concluídas agrupadas por família (OPERATOR+)
2. `ProductionOrderDisplayStatus` calculado em Java por OP (não persistido): lógica baseada em `status` do Dynamics + `sterilizationLoad.status` do Hub
3. `GET /api/v1/production/tracking/orders` lista OPs com `displayStatus`, `completionPct` (`producedQty/plannedQty*100`), `overdue` (`dueDate < today && displayStatus != DONE`), `plannedPeople`; filtros: `family`, `displayStatus`, `overdue`, `productType`
4. `GET /api/v1/production/tracking/summary` retorna contagens por status:
   ```json
   { "inProgress": 12, "pendingSterilization": 5, "sterilizing": 3, "overdue": 2, "doneThisWeek": 8 }
   ```
5. `lastSyncAt` da última importação de OPs retornado em todos os responses — frontend exibe defasagem

---

#### US-083 — Frontend de acompanhamento visual (5 pts)

1. Rota `/production/tracking`: cards por família com aba de seleção
2. Dentro de cada família: colunas kanban por `displayStatus` (PLANNED, RELEASED, IN_PROGRESS, PENDING_STERILIZATION, IN_LOAD, STERILIZING, DONE)
3. Card de OP: número da OP, produto, barra de progresso (producedQty/plannedQty), prazo (vermelho se overdue), chip de status colorido, badge de pessoas (plannedPeople)
4. Filtro global acima do kanban: família, tipo (FINISHED/INTERMEDIATE), mostrar apenas atrasadas
5. Card de resumo no topo: total em produção, aguardando esterilização, esterilizando, atrasadas
6. Banner "Última sincronização: há N minutos" com botão "Atualizar" (dispara nova importação de OPs — SUPERVISOR+)
7. Clique em card de OP abre panel lateral com todos os campos da OP + histórico de cargas (se aplicável)

---

## Sprint 31 ⬜
**Objetivo**: Gestão de cargas de esterilização — criação, alocação de OPs e ciclo completo
**ADR**: ADR-029
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-084 | Backend e frontend de cargas de esterilização (ciclo OPEN → RELEASED) | 8 | ⬜ pendente |

---

#### US-084 — Cargas de esterilização (8 pts)

**Backend**
1. `POST /api/v1/production/sterilization-loads` cria carga (SUPERVISOR+); campos: `sterilizerId` (UUID, nullable), `method` (enum), `sterilizationDate` (optional), `notes`; `loadNumber` gerado sequencialmente "CARGA-{ANO}-{NNN}"
2. `GET /api/v1/production/sterilization-loads` lista cargas (OPERATOR+); filtros: `status`, `method`, `dateFrom`, `dateTo`
3. `GET /api/v1/production/sterilization-loads/{id}` detalhe + lista de OPs alocadas + quantidades totais (OPERATOR+)
4. `GET /api/v1/production/sterilization-loads/pending-orders` retorna OPs com `status=DONE` no Dynamics + `requiresSterilization=true` + `sterilizationLoad=null` — ordenadas por `dueDate ASC` (SUPERVISOR+)
5. `POST /api/v1/production/sterilization-loads/{id}/orders` adiciona OP à carga (SUPERVISOR+); valida: `status=DONE`, `requiresSterilization=true`, OP não alocada em outra carga ativa → `409` se violação
6. `DELETE /api/v1/production/sterilization-loads/{id}/orders/{opId}` remove OP (SUPERVISOR+, apenas quando `loadStatus=OPEN`)
7. `PUT /api/v1/production/sterilization-loads/{id}/status` transições (SUPERVISOR+):
   - `OPEN → CLOSED`: congela lista de OPs; atualiza `closedAt`; se `sterilizerId` configurado: `Equipment.status → UNDER_MAINTENANCE` (mesma `@Transactional`)
   - `CLOSED → STERILIZING`: registra início da esterilização
   - `STERILIZING → RELEASED`: `releasedAt = now()`; `Equipment.status → OPERATIONAL`; exibe confirmação de que estoque deve ser atualizado no Dynamics manualmente
   - `STERILIZING → REJECTED`: OPs da carga voltam para `sterilizationLoad = null` (fila de pendentes)
8. Transição inválida retorna `422` com mensagem

**Frontend**
9. Rota `/production/sterilization-loads`: cards de carga agrupados por status com badge de quantidade de OPs
10. Carga OPEN: lista de OPs alocadas + painel lateral "Aguardando Carga" com OPs pendentes; botão "+ Adicionar" por OP; botão "Fechar Carga" (SUPERVISOR+) com confirmação
11. Detalhe da carga: tabela de OPs (produto, nº OP, quantidade, família), totais, esterilizador, método, datas; botões de transição com confirmação por dialog
12. Ao liberar carga (RELEASED): dialog informa "Lembre-se de atualizar o estoque no Dynamics para essas OPs"
13. Status de carga com cores: OPEN=azul, CLOSED=âmbar, STERILIZING=roxo, RELEASED=verde, REJECTED=vermelho

---

## Sprint 32 ⬜
**Objetivo**: Motor MRP, staffing por OP (tempo de ciclo → pessoas) e board de planejamento por família
**ADR**: ADR-030
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-085 | Motor MRP — dry-run, run e gerenciamento de sugestões | 5 | ⬜ pendente |
| US-086 | Cálculo de staffing por OP (ciclo → pessoas) + edição por SUPERVISOR | 3 | ⬜ pendente |
| US-087 | Board de planejamento por família + timeline (Gantt simplificado) | 5 | ⬜ pendente |

---

#### US-085 — Motor MRP (5 pts)

**Backend**
1. `POST /api/v1/production/mrp/dry-run` (SUPERVISOR+): executa MRP em memória sem persistir; retorna `MrpRunResult` com sugestões, necessidades de compra e alertas
2. `POST /api/v1/production/mrp/run` (SUPERVISOR+): executa MRP e persiste `MrpPlannedOrder` com `status=SUGGESTED`; retorna `MrpRunResult`
3. Algoritmo Net Change 2 níveis: FINISHED → intermediários via BOM (se BOM importado) → RAW_MATERIAL em purchaseNeeds
4. `openOrdersQty` inclui OPs Dynamics abertas + `MrpPlannedOrders` SUGGESTED/ACCEPTED — evita geração dupla
5. `suggestedQty = ceil(netNeed / batchSize) * batchSize`; `dueDate = today + leadTimeDays`
6. `GET /api/v1/production/mrp/runs` histórico de runs (SUPERVISOR+); paginado
7. `GET /api/v1/production/mrp/suggested-orders` lista sugestões com `status=SUGGESTED` (SUPERVISOR+)
8. `PUT /api/v1/production/mrp/suggested-orders/{id}/accept` aceita sugestão; body opcional `{ "adjustedQty": N }`; `status → ACCEPTED`
9. `PUT /api/v1/production/mrp/suggested-orders/{id}/reject` rejeita com `{ "reason": "..." }`; `status → REJECTED`
10. `PUT /api/v1/production/mrp/suggested-orders/{id}/convert` marca que a OP foi criada no Dynamics; `status → CONVERTED`

---

#### US-086 — Staffing por OP (3 pts)

**Backend**
1. `StaffingConfig` singleton com `shiftHours=8` e `shiftsPerDay=1` default; `GET /api/v1/production/staffing-config` (OPERATOR+); `PUT` (ADMIN)
2. Cálculo automático: `peopleNeeded = ceil((plannedQty * cycleTime) / (shiftHours * shiftsPerDay * 3600 * businessDays))`; executado na importação de OPs para OPs sem `peopleOverridden=true`
3. `PUT /api/v1/production/production-orders/{id}/staffing` body `{ "plannedPeople": N }` (SUPERVISOR+); seta `plannedPeople=N`, `peopleOverridden=true`
4. `DELETE /api/v1/production/production-orders/{id}/staffing` (SUPERVISOR+): recalcula `plannedPeople` do zero; seta `peopleOverridden=false`
5. OPs sem `CycleTime` cadastrado: `plannedPeople=null` (exibido como "—" no frontend)

**Frontend**
6. Coluna "Pessoas" nas listagens de OP com ícone de lápis (SUPERVISOR+): clique abre input inline + botões salvar/cancelar
7. Ícone de calculadora ao lado do valor: tooltip "Calculado automaticamente: X pessoas. Clique para resetar" (SUPERVISOR+)
8. Card de OP no tracking (US-083) exibe badge de pessoas com destaque visual se `peopleOverridden=true`

---

#### US-087 — Board de planejamento e timeline (5 pts)

**Backend**
1. `GET /api/v1/production/planning/families` (SUPERVISOR+): retorna `List<FamilyPlanningBoard>` com `ProductPlanningRow` por produto — estoque, OPs abertas, necessidade líquida, `planningStatus`, `totalPlannedPeople`
2. `GET /api/v1/production/planning/timeline?familyCode=X&weeks=8` (SUPERVISOR+): retorna `List<TimelineEntry>` com OPs Dynamics abertas + sugestões MRP da família, ordenadas por `dueDate`
3. `GET /api/v1/production/planning/purchase-needs` (SUPERVISOR+): retorna necessidades de matéria-prima do último `MrpRun` com `status=SUGGESTED/ACCEPTED`

**Frontend**
4. Rota `/production/planning`: board com card por família; cada card expande para tabela de produtos com colunas: código, estoque atual, mínimo, OPs abertas (qtd), sugestões MRP (qtd), necessidade líquida, status (chip OK/ALERT/CRITICAL), pessoas planejadas total
5. Botão "Executar MRP" (SUPERVISOR+) no topo: → chama dry-run → modal com tabela de sugestões (produto, qtd, prazo) com opção de ajustar qtd por linha → botão "Confirmar e Gerar" chama run → toast "X sugestões geradas"
6. Rota `/production/planning/timeline?family=X`: grade CSS Grid com semanas em colunas; barras por OP; azul=Dynamics, laranja=sugestão MRP; barra vermelha se `overdue=true`
7. Clique em barra da timeline: abre panel lateral com detalhes da OP/sugestão + botões aceitar/rejeitar (para sugestões)
8. Painel "Necessidades de Compra" (SUPERVISOR+): tabela de RAW_MATERIAL a comprar com quantidade calculada pelo último MRP run
