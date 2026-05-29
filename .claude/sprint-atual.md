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

## Sprint 13 ✅
**Objetivo**: Análise de Causa Raiz — 5-Porquês vinculado a NCs
**ADR**: ADR-015
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-052 | Backend de Análise de Causa Raiz (5-Porquês) | 3 | ✅ concluído |
| US-053 | Frontend de 5-Porquês — wizard interativo | 3 | ✅ concluído |

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
3. Wizard vertical: par "Por quê / Resposta" exibido como cards sequenciais; botão "+ Adicionar próximo Por quê" habilita par seguinte (máximo 5 pares); disponível somente após preencher tanto "Por quê" quanto "Resposta" do par atual
4. Campo "Causa Raiz Identificada" aparece após preencher `why1 + answer1`
5. Botão "Salvar RCA" envia POST ou PUT dependendo de existência de RCA anterior
6. NC fechada (`CLOSED`): wizard em modo somente leitura (campos desabilitados, sem botão salvar)
7. OPERATOR: visualização somente leitura independente do status da NC

---

## Sprint 14 ✅
**Objetivo**: Gestão de fornecedores + score de qualidade por fornecedor
**ADR**: ADR-017
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-057 | Cadastro de fornecedores (Supplier CRUD) | 3 | ✅ concluído |
| US-058 | Score de qualidade e ranking de fornecedores | 3 | ✅ concluído |

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

## Sprint 15 ✅
**Objetivo**: Manutenção preventiva — planos recorrentes e auto-geração de OSs
**ADR**: ADR-011
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-040 | Planos de manutenção preventiva — CRUD | 5 | ✅ concluído |
| US-041 | Auto-geração de OSs preventivas pelo scheduler | 3 | ✅ concluído |
| US-042 | Calendário de manutenção preventiva (frontend) | 3 | ✅ concluído |

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
8. `PUT /api/v1/maintenance/schedules/{id}` atualiza `title`, `description`, `priority`, `recurrence`, `dayOfWeek`, `dayOfMonth`; as mesmas validações de recorrência do AC#3 se aplicam; `nextRunAt` é recalculado a partir de `LocalDate.now()` como data base; retorna `200` ou `404` (SUPERVISOR+)
9. `PUT /api/v1/maintenance/schedules/{id}/deactivate` seta `active = false`; retorna `204` (SUPERVISOR+)
10. Entidade `MaintenanceSchedule` e enum `ScheduleRecurrence` criados conforme ADR-011
11. Migration: tabela `maintenance_schedule` + coluna nullable `schedule_id` em `work_order`

**Frontend** _(parte de US-042)_

---

#### US-041 — Auto-geração de OSs preventivas (3 pts)

**Backend**
1. `WorkOrder.java` recebe campo `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_id") MaintenanceSchedule schedule` (nullable); `WorkOrderResponse` recebe campo `scheduleId` (UUID nullable) no factory `from()` — pré-requisito para este US
2. `MaintenanceSchedulerJob` com `@Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")` chama `RunDueSchedulesUseCase.execute()`
3. `RunDueSchedulesUseCase` busca todos os planos ativos com `nextRunAt <= hoje`; para cada um, cria `WorkOrder` do tipo `PREVENTIVE` com `openedBy = "scheduler"`, campo `schedule` preenchido
4. Após criar cada OS, avança `nextRunAt` conforme regra de recorrência e persiste (`lastRunAt = hoje`)
5. `@EnableScheduling` adicionado em `BackendApplication`
6. Falha ao criar uma OS loga `ERROR` e continua processando os demais planos (não aborta o job)
7. `POST /api/v1/admin/maintenance/schedules/run-now` (ADMIN) dispara `RunDueSchedulesUseCase` imediatamente; retorna `200 { "created": N }` com número de OSs criadas
8. `WorkOrderResponse` expõe `scheduleId` (UUID nullable) para rastreabilidade

---

#### US-042 — Calendário de manutenção preventiva (3 pts)

**Frontend**
1. Rota `/maintenance/schedules` lista planos ativos em tabela: equipamento, título, recorrência, próxima execução, status (ativo/inativo)
2. Botão "Novo Plano" (SUPERVISOR+) navega para `/maintenance/schedules/new` com formulário completo; em `maintenance.routes.ts`, a rota `schedules/new` deve ser declarada **antes** de `schedules/:id` para evitar que Angular interprete "new" como UUID
3. Formulário mostra/oculta campos `dayOfWeek` e `dayOfMonth` dinamicamente conforme recorrência selecionada
4. Criação bem-sucedida → snackbar "Plano criado" + redirect para listagem
5. Rota `/maintenance/calendar` exibe grade mensal (semanas em linhas, dias em colunas)
6. Cada dia exibe badges com os planos programados para aquela data (calculados no frontend com base em `nextRunAt` e recorrência)
7. Clique em um badge abre painel lateral com detalhe do plano + botão "Desativar" (SUPERVISOR+)
8. Calendário suporta navegação mensal (mês anterior / próximo) via botões no topo
9. Calendário implementado sem biblioteca externa — renderizado com `@for` sobre matriz de `LocalDate` gerada no componente

---

## Sprint 16 ✅
**Objetivo**: Advanced analytics — trend charts e pareto por módulo + hardening de tech debt das sprints anteriores
**ADRs**: ADR-012, ADR-031
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-043 | Analytics de OEE — trend semanal + gráficos | 4 | ✅ concluído |
| US-044 | Analytics de QMS — pareto e trend de NCs | 3 | ✅ concluído |
| US-045 | Analytics de Manutenção — MTTR mensal + distribuição de OSs | 3 | ✅ concluído |
| US-059 | Tech debt — auditoria, validação e qualidade de código (sprints 12–15) | 2 | ✅ concluído |

**Total**: 12 pontos

### Dependências
- US-043, US-044, US-045 são independentes entre si mas todas dependem dos componentes `shared/charts/` (criar no início da sprint antes das páginas)
- US-059 é independente das demais — pode ser desenvolvida em paralelo com as analytics

---

#### US-043 — Analytics de OEE — trend semanal + gráficos (4 pts)

**Backend**
1. `GET /api/v1/analytics/oee/trend?weeks=12` retorna `OeeTrendResponse` (SUPERVISOR+):
   ```json
   { "weekLabels": ["2026-W01", "2026-W02"], "oeeValues": [0.82, null], "sampleCounts": [5, 0] }
   ```
2. `weeks` é query param opcional: default `12`, mínimo `4`, máximo `52`; valor fora do range retorna `400` com `{ "message": "weeks deve ser entre 4 e 52" }`
3. Cada semana é identificada no formato ISO `"YYYY-Www"` (ex: `"2026-W03"`); semanas sem dado retornam `oeeValues[i] = null` e `sampleCounts[i] = 0`
4. OEE semanal = média de `(availableTimeMinutes / totalTimeMinutes)` de todos os registros com `periodDate` na semana ISO; busca via `findByPeriodDateBetween` e agrupamento em Java com `Collectors.groupingBy` (conforme ADR-012 Decisão 1) — sem SQL nativo
5. `GetOeeTrendUseCase` criado em `oee/application/usecase/`; endpoint adicionado em `AnalyticsController` em `common/presentation/` (conforme ADR-012 Decisão 2)
6. Endpoint protegido: `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`
7. Teste unitário `GetOeeTrendUseCaseTest`: cobre semana com dados, semana sem dados (null), semana parcial, e limite de `weeks`

**Frontend**
8. Link "Analytics" adicionado ao nav, visível apenas para SUPERVISOR+ (`@if (role() === 'SUPERVISOR' || role() === 'ADMIN')`)
9. Instalação: `npm install ng2-charts chart.js` adicionado a `apps/frontend/package.json` (conforme ADR-012 Decisão 4)
10. Componentes genéricos criados em `shared/charts/` (standalone, `OnPush`, inputs via `input()`):
    - `LineChartComponent`: inputs `labels: InputSignal<string[]>`, `values: InputSignal<(number | null)[]>`, `referenceValue?: InputSignal<number>` (linha horizontal de referência), `yAxisLabel?: InputSignal<string>`
    - `BarChartComponent`: inputs `labels: InputSignal<string[]>`, `datasets: InputSignal<{ label: string; data: number[]; color: string }[]>`
    - `DoughnutChartComponent`: inputs `labels: InputSignal<string[]>`, `data: InputSignal<number[]>`, `colors: InputSignal<string[]>`
11. Rota `/analytics/oee` (lazy-loaded) exibe:
    - Line chart via `LineChartComponent`: OEE % por semana (eixo Y: 0–100%); linha de referência vermelha pontilhada em 65%
    - Tabela abaixo do gráfico: colunas semana, OEE %, nº de importações; linhas com `oeeValue = null` exibem "—"
12. Dropdown de período: opções 4, 8, 12, 26, 52 semanas; mudança recarrega os dados via signal
13. Botão "Imprimir" chama `window.print()`; CSS `@media print` no componente oculta nav, sidebar e dropdown de período
14. Skeleton loader exibido enquanto os dados carregam; erro de API exibe snackbar persistente

---

#### US-044 — Analytics de QMS — pareto e trend de NCs (3 pts)

**Backend**
1. `GET /api/v1/analytics/nc/pareto?days=30` retorna `NcParetoResponse` (SUPERVISOR+):
   ```json
   { "byType": { "PROCESS": 12, "PRODUCT": 5, "SUPPLIER": 3 }, "bySeverity": { "CRITICAL": 3, "HIGH": 8, "MEDIUM": 6, "LOW": 3 } }
   ```
2. `days` é query param opcional: default `30`; valores aceitos: `30`, `90`, `180`; valor não permitido retorna `400` com `{ "message": "days deve ser 30, 90 ou 180" }`
3. Contagens calculadas via Java stream sobre NCs com `reportedAt >= LocalDateTime.now().minusDays(days)` — sem SQL nativo (ADR-012 Decisão 1); todos os tipos e severidades sem NCs no período aparecem no map com value `0`
4. `GET /api/v1/analytics/nc/trend?weeks=12` retorna `TimeSeriesResponse` (SUPERVISOR+):
   ```json
   { "labels": ["2026-W01", "2026-W02"], "values": [4.0, 7.0] }
   ```
5. `weeks` com mesmas regras de US-043 AC#2; contagem de NCs criadas (`reportedAt`) na semana; `values[i] = 0.0` onde sem NC (nunca null para trend de NCs — 0 é informação válida)
6. `GetNcAnalyticsUseCase` criado em `qms/application/usecase/`; ambos os endpoints adicionados ao `AnalyticsController`
7. Ambos os endpoints protegidos: `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`
8. Teste unitário `GetNcAnalyticsUseCaseTest`: cobre pareto com dados mistos, pareto com período sem NCs (todos zeros), trend com NCs em múltiplas semanas, trend com semana vazia (zero)

**Frontend**
9. Rota `/analytics/qms` (lazy-loaded) exibe três seções:
   - Bar chart "NCs por Tipo" via `BarChartComponent`: barras ordenadas por contagem DESC; cor única MSB `#0099B8`
   - Bar chart "NCs por Severidade" via `BarChartComponent`: cores fixas por severidade (CRITICAL=#EF4444, HIGH=#F97316, MEDIUM=#EAB308, LOW=#22C55E)
   - Line chart "Novas NCs por Semana" via `LineChartComponent`: sem linha de referência; eixo Y começa em 0
10. Dropdown de período para pareto: 30 / 90 / 180 dias (independente do trend)
11. Dropdown de período para trend: 4 / 8 / 12 semanas (independente do pareto)
12. Skeleton loaders independentes por seção; erro de API exibe snackbar e mantém seção anterior (sem tela em branco)

---

#### US-045 — Analytics de Manutenção — MTTR mensal + distribuição de OSs (3 pts)

**Backend**
1. `GET /api/v1/analytics/maintenance/mttr-trend?months=6` retorna `MttrTrendResponse` (SUPERVISOR+):
   ```json
   { "monthLabels": ["2026-01", "2026-02", "2026-03"], "mttrValues": [4.2, null, 3.8] }
   ```
2. `months` é query param opcional: default `6`, mínimo `3`, máximo `24`; valor fora do range retorna `400`
3. MTTR mensal = média de `(closedAt − startedAt)` em horas das OSs `CORRECTIVE + DONE` com `startedAt` não nulo, agrupadas por mês de `closedAt`; calculado em Java via `Collectors.groupingBy` com `YearMonth.from(wo.getClosedAt())`; mês sem OS retorna `mttrValues[i] = null`
4. `GET /api/v1/analytics/maintenance/wo-summary` retorna `WoSummaryResponse` (SUPERVISOR+):
   ```json
   { "byStatus": { "OPEN": 5, "IN_PROGRESS": 2, "DONE": 30, "CANCELLED": 1 }, "byType": { "CORRECTIVE": 20, "PREVENTIVE": 18 } }
   ```
5. `wo-summary` agrega todas as OSs (sem filtro de período); todos os status e tipos aparecem no map, mesmo com contagem zero
6. `GetMaintenanceAnalyticsUseCase` criado em `maintenance/application/usecase/`; ambos os endpoints adicionados ao `AnalyticsController`
7. Ambos os endpoints protegidos: `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`
8. Teste unitário `GetMaintenanceAnalyticsUseCaseTest`: cobre MTTR com meses com e sem OS concluída (null), `wo-summary` com distribuição completa, `wo-summary` com OSs apenas de um status (restantes = 0)

**Frontend**
9. Rota `/analytics/maintenance` (lazy-loaded) exibe três seções:
   - Line chart "MTTR Médio Mensal (h)" via `LineChartComponent`: eixo Y em horas com 1 decimal; pontos nulos exibidos como gap na linha (propriedade `spanGaps: false` do Chart.js)
   - Doughnut chart "OSs por Status" via `DoughnutChartComponent`: cores OPEN=#F59E0B, IN_PROGRESS=#3B82F6, DONE=#22C55E, CANCELLED=#9CA3AF
   - Doughnut chart "OSs por Tipo" via `DoughnutChartComponent`: CORRECTIVE=#EF4444, PREVENTIVE=#0099B8
10. Dropdown de período para MTTR: 3 / 6 / 12 / 24 meses
11. Doughnut charts sem controle de período — sempre refletem todas as OSs do histórico
12. Skeleton loaders independentes por seção; erro exibe snackbar persistente

---

#### US-059 — Tech debt — auditoria, validação e qualidade de código (2 pts)

Consolida os itens diferidos das revisões de Helena (SH-38, SH-41, SUG-23), Beatriz (SEC-045, SEC-046, SEC-048, SEC-049, SEC-050, SEC-051, SEC-052) e Maiana (G16, G17) das Sprints 12–15.

**Backend — Auditoria (SEC-045, SEC-046, SEC-050, SEC-051 / SH-38)**
1. `AuditAction` enum recebe três novas constantes: `ROLE_CHANGED`, `USER_REACTIVATED`, `SUPPLIER_UPDATED`, `SCHEDULE_UPDATED`
2. `UpdateUserRoleUseCase.execute()` chama `auditService.log(username, AuditAction.ROLE_CHANGED, "User", userId, Map.of("newRole", role.name()))` após o save; assinatura do método atualizada para receber `String adminUsername` se necessário
3. `ReactivateUserUseCase.execute()` chama `auditService.log(adminUsername, AuditAction.USER_REACTIVATED, "User", userId, Map.of())` após o save
4. `UpdateSupplierUseCase.execute()` chama `auditService.log(username, AuditAction.SUPPLIER_UPDATED, "Supplier", supplierId, Map.of("name", supplier.getName()))` após o save; assinatura do método recebe `String adminUsername` se ausente
5. `UpdateScheduleUseCase.execute()` chama `auditService.log(username, AuditAction.SCHEDULE_UPDATED, "MaintenanceSchedule", scheduleId, Map.of("recurrence", request.recurrence().name()))` após o save; assinatura do método atualizada para receber `String username` e o `MaintenanceController` passa `principal.getName()`

**Backend — Validação (SEC-048, SEC-049, SEC-052)**
6. `CreateSupplierRequest` recebe anotação `@Email(message = "contactEmail deve ser um endereço de email válido")` no campo `contactEmail` — sem alterar campos existentes
7. `SupplierController`: endpoints `GET /quality-score` e `GET /quality-ranking` recebem `@Min(1) @Max(730)` no parâmetro `days`; `@Validated` adicionado na classe do controller
8. `GlobalExceptionHandler` recebe handler para `IllegalArgumentException`:
   ```java
   @ExceptionHandler(IllegalArgumentException.class)
   public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
       return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
   }
   ```
   Isso cobre o cenário de `ScheduleRecurrenceHelper.validate()` retornando 400 em vez de 500 (SEC-052 / MF-3 de Helena)

**Backend — Testes (G16 de Maiana, SH-41 de Helena)**
9. `UpdateScheduleUseCaseTest` criado cobrindo: (a) atualização bem-sucedida recalcula `nextRunAt`; (b) schedule não encontrado lança exceção 404; (c) chamada a `auditService.log` verificada com `eq(AuditAction.SCHEDULE_UPDATED)`
10. `DeactivateScheduleUseCaseTest` criado cobrindo: (a) soft-delete bem-sucedido (`active = false`); (b) schedule não encontrado lança exceção 404
11. `RunDueSchedulesUseCaseTest` recebe asserção adicional: `verify(scheduleRepository, times(1)).save(any(MaintenanceSchedule.class))` no teste `shouldProcessAllSchedules_evenIfOneFails` (SH-41)

**Frontend — Qualidade (SUG-23 de Helena, G17 de Maiana)**
12. `schedule-list.component.ts` e `maintenance-calendar.component.ts`: getter `isSupervisor` convertido para `computed()`:
    ```typescript
    readonly isSupervisor = computed(() => this.role() === 'SUPERVISOR' || this.role() === 'ADMIN');
    ```
13. `schedule-form.component.spec.ts` recebe teste `should navigate to list with toast on successful create`: mock de `maintenanceService.createSchedule` retornando `of(mockSchedule)`, verificação de que `router.navigate` foi chamado com `['/maintenance/schedules']` e state `{ toast: 'Plano criado com sucesso' }` (G17)

---

## Sprint 17 ✅
**Objetivo**: Planned Downtime — separação de paradas planejadas no cálculo de OEE + correção residual de bug visual da Sprint 16
**ADRs**: ADR-025, ADR-032
**Status**: pendente

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-073 | Backend de registro de paradas planejadas | 3 | ✅ concluído |
| US-074 | Integração com cálculo de OEE e frontend | 3 | ✅ concluído |
| US-060 | Tech debt residual — BUG-2 (cores de severidade) + alinhamento ADR-031 | 1 | ✅ concluído |

**Total**: 7 pontos

### Dependências
- US-074 depende de US-073 (entidade `PlannedDowntime` e repositório necessários)
- US-060 é independente de US-073 e US-074 — pode ser desenvolvida em paralelo

---

#### US-073 — Registro de paradas planejadas (3 pts)

**Backend**
1. Entidade `PlannedDowntime` criada no pacote `oee/domain/` com campos: `id` (UUID), `equipment` (`@ManyToOne LAZY`, nullable — null = parada de planta inteira), `date` (`LocalDate`), `durationMinutes` (`Integer`), `reason` (`DowntimeReason` enum), `description` (`String`, nullable), `registeredBy` (`String`), `registeredAt` (`LocalDateTime`); tabela `planned_downtime` com índices em `equipment_id` e `date` (conforme ADR-025 Decisão 1)
2. Enum `DowntimeReason` criado com valores: `PREVENTIVE_MAINTENANCE`, `SCHEDULED_SETUP`, `HOLIDAY`, `OTHER`
3. `PlannedDowntimeRepository` criado em `oee/infrastructure/` com método `findByDateAndEquipmentIdOrEquipmentIsNull(LocalDate date, UUID equipmentId)` para consulta eficiente na etapa de cálculo (sem `findAll`)
4. `POST /api/v1/oee/planned-downtimes` cria `PlannedDowntime` (SUPERVISOR+); campos obrigatórios: `date`, `durationMinutes` (min 1, max 1440), `reason`; `equipmentId` é opcional (null = planta inteira); `description` é opcional; `registeredBy` preenchido do JWT; retorna `201 PlannedDowntimeResponse`
5. Campos de validação: `date` não nula; `durationMinutes` com `@Min(1) @Max(1440)`; `reason` com enum válido; `equipmentId` quando informado deve referenciar equipamento existente — caso contrário `404`
6. `GET /api/v1/oee/planned-downtimes` lista paradas (OPERATOR+); filtros opcionais `?date=<ISO_DATE>` e `?equipmentId=<UUID>`; sem filtro retorna todas (máximo 90 dias retroativos via query param `?from=`); retorna `List<PlannedDowntimeResponse>`
7. `PUT /api/v1/oee/planned-downtimes/{id}` atualiza `date`, `durationMinutes`, `reason`, `description`, `equipmentId` (SUPERVISOR+); parada inexistente retorna `404`; retorna `200 PlannedDowntimeResponse`
8. `DELETE /api/v1/oee/planned-downtimes/{id}` remove permanentemente a parada (SUPERVISOR+); retorna `204`; parada inexistente retorna `404`
9. `PlannedDowntimeResponse` é Java record com campos: `id`, `equipmentId`, `equipmentCode` (nullable), `date`, `durationMinutes`, `reason`, `description`, `registeredBy`, `registeredAt`
10. Endpoint `POST` e `PUT` protegidos com `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`; `GET` com `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`
11. Teste unitário `CreatePlannedDowntimeUseCaseTest` cobre: (a) criação bem-sucedida com `equipmentId` nulo (planta inteira); (b) criação com equipamento existente; (c) criação com equipamento inexistente lança exceção 404; (d) `durationMinutes` inválido retorna 400 via Bean Validation

---

#### US-074 — OEE com paradas planejadas (3 pts)

**Backend**
1. `CalculateOeeUseCase` (ou use case equivalente de cálculo de OEE) recebe parâmetro booleano `excludePlannedDowntime`; quando `true`, subtrai `plannedDowntimeMinutes` do denominador: `Disponibilidade = availableTimeMinutes / (totalTimeMinutes - plannedDowntimeMinutes)`; quando `false`, comportamento atual inalterado
2. `plannedDowntimeMinutes` para um `ImportBatch` = soma de `durationMinutes` de `PlannedDowntime` onde `date = importBatch.profileDate` e (`equipment = importBatch.equipment` OR `equipment IS NULL`); calculado via chamada ao `PlannedDowntimeRepository.findByDateAndEquipmentIdOrEquipmentIsNull(date, equipmentId)` — sem `findAll()` para evitar N+1
3. `totalTimeMinutes - plannedDowntimeMinutes` nunca pode ser ≤ 0; caso o resultado seja ≤ 0, usa `totalTimeMinutes` sem subtração (fallback silencioso) — evita divisão por zero ou OEE incoerente
4. `GET /api/v1/oee/dashboard` recebe query param `?excludePlannedDowntime=false` (default `false` — comportamento atual preservado; retrocompatível com todos os clientes existentes)
5. `GET /api/v1/analytics/oee/trend` também suporta `?excludePlannedDowntime=false` com mesma semântica; o parâmetro é repassado ao `GetOeeTrendUseCase`
6. Teste unitário `CalculateOeeUseCaseTest` (ou equivalente) recebe novos casos: (a) `excludePlannedDowntime=false` — sem alteração no valor calculado; (b) `excludePlannedDowntime=true` com parada de planta inteira — denominator reduzido, OEE maior; (c) `excludePlannedDowntime=true` sem paradas cadastradas na data — mesmo resultado que `false`; (d) paradas somadas excedem `totalTimeMinutes` — fallback preserva denominator original

**Frontend**
7. Toggle "Excluir paradas planejadas do denominador" no `OeeDashboardComponent` e no `OeeAnalyticsComponent` (`/analytics/oee`); estado do toggle controlado por `signal<boolean>`; mudança dispara reconsulta imediata dos endpoints
8. Tooltip nos cards de OEE exibe modo atual: "Calculado incluindo paradas planejadas" / "Calculado excluindo paradas planejadas"; implementado com `[matTooltip]` do Angular Material
9. Rota `/oee/planned-downtimes` (lazy-loaded, SUPERVISOR+): exibe calendário mensal com badges por dia indicando o número de paradas registradas (paradas de equipamento e de planta inteira somadas); clique no badge filtra a lista abaixo para o dia selecionado
10. Formulário de registro na mesma rota: campos equipamento (select com opção "Toda a planta"), data (datepicker), duração em minutos (input numérico), razão (select), descrição (textarea opcional); validação client-side; botão "Registrar" desabilitado se formulário inválido
11. Submit bem-sucedido exibe snackbar "Parada planejada registrada" e atualiza o calendário sem reload da página; erro 400/404 exibe mensagem da API em snackbar de erro
12. Botão "Excluir" por linha na lista de paradas do dia (SUPERVISOR+) com dialog de confirmação `MatDialog`; após exclusão atualiza a lista e o calendário
13. Link "Paradas Planejadas" adicionado ao submenu de OEE no `NavComponent`, visível apenas para SUPERVISOR+

---

#### US-060 — Tech debt residual Sprint 16 — BUG-2 + alinhamento ADR-031 (1 pt)

> **Nota de escopo (2026-05-20):** Os itens MF-5, MF-6, BUG-1, SH-42 (SEC-055), SH-43 e SH-44 foram corrigidos por Mateus antes do commit final da Sprint 16 (confirmado nos logs de Helena, Mateus e Maiana). O escopo desta US foi reduzido de 3 para 1 ponto — apenas o item genuinamente pendente (BUG-2) e o alinhamento de nomenclatura do ADR-031 permanecem.

**Frontend — BUG-2 (cores de severidade divergem do AC em qms-analytics)**
1. `qms-analytics.component.ts`: método `severityColor()` corrigido para usar os valores especificados nos ACs originais (CRITICAL=#EF4444, HIGH=#F97316, MEDIUM=#EAB308, LOW=#22C55E):
   ```typescript
   private severityColor(severity: string): string {
     const map: Record<string, string> = {
       CRITICAL: '#EF4444', HIGH: '#F97316', MEDIUM: '#EAB308', LOW: '#22C55E'
     };
     return map[severity] ?? '#0099B8';
   }
   ```
   Implementação atual usa `#E53E3E`, `#DD6B20`, `#D69E2E`, `#38A169` — divergência visual sem impacto funcional
2. Spec `qms-analytics.component.spec.ts`: asserções de cores atualizadas para os valores corretos; os testes estavam validando as cores erradas, mascarando o bug

**Documentação — inconsistência de nomenclatura ADR-031**
3. `docs/adr/ADR-031-sprint16-technical-debt.md` Decisão 1: renomear `USER_ROLE_UPDATED` para `ROLE_CHANGED` para alinhar ao nome real da constante implementada em `AuditAction.java`; sem impacto no comportamento do sistema — é estritamente correção de documentação

---

## Sprint 18 ✅
**Objetivo**: Tech debt Sprint 17 (auditoria PlannedDowntime, validação, correções UX) + threshold alerts configuráveis + central de notificações in-app
**ADR**: ADR-013, ADR-033
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-088 | Tech debt Sprint 17 — auditoria PlannedDowntime, validação e correções de UX | 2 | ✅ concluído |
| US-046 | Configuração de thresholds de alerta | 3 | ✅ concluído |
| US-047 | Motor de avaliação de alertas + notificação email | 4 | ✅ concluído |
| US-048 | Central de notificações in-app (sino no nav) | 3 | ✅ concluído |

### Dependências
- US-088 independente das demais
- US-046 independente
- US-047 depende de US-046 (entidade `AlertThreshold` e enum `AlertMetric`)
- US-048 depende de US-047 (entidade `Notification` e repositório)

---

#### US-088 — Tech debt Sprint 17 (2 pts)

> Cobre: SEC-057, SEC-058 (auditoria PlannedDowntime), SEC-059 (`@Size` em description), SEC-030 (SecurityConfig URL-level), SH-40 (DAILY fora do mês no calendário), SUG-30 (equipmentOptions via API). ADR de referência: ADR-033.

**Backend — Auditoria (SEC-057, SEC-058)**
1. `AuditAction` enum: adicionar `DOWNTIME_UPDATED`; `DOWNTIME_CREATED` e `DOWNTIME_DELETED` (já declarados) passam a ser invocados pelos use cases
2. `CreatePlannedDowntimeUseCase`: injetar `AuditService`; após `save()` chamar `auditService.log(username, DOWNTIME_CREATED, "PlannedDowntime", id, Map.of("reason", ..., "durationMinutes", ...))`
3. `UpdatePlannedDowntimeUseCase`: injetar `AuditService`; assinatura `execute(id, request, username)`; chamar `auditService.log(username, DOWNTIME_UPDATED, ...)`
4. `DeletePlannedDowntimeUseCase`: injetar `AuditService`; migrar `existsById+deleteById` para `findById+delete` (captura dados antes da exclusão); chamar `auditService.log(username, DOWNTIME_DELETED, ...)`
5. `PlannedDowntimeController`: passar `principal.getName()` para os três use cases
6. `CreatePlannedDowntimeUseCaseTest`: asserção `verify(auditService).log(..., DOWNTIME_CREATED, ...)` no happy path; `verify(auditService, never())` no path de equipamento inativo
7. Criar `DeletePlannedDowntimeUseCaseTest`: (a) deleção com auditoria — `verify(auditService).log(..., DOWNTIME_DELETED, ...)`; (b) id inexistente sem auditoria — `PlannedDowntimeNotFoundException` antes do log

**Backend — Validação (SEC-059)**
8. `CreatePlannedDowntimeRequest.description`: adicionar `@Size(max = 500, message = "Descrição não pode exceder 500 caracteres")`
9. `UpdatePlannedDowntimeRequest.description`: idem
10. Violação retorna `400` com mensagem descritiva (em vez de `500` via `DataIntegrityViolationException`)

**Backend — SecurityConfig (SEC-030)**
11. `SecurityConfig`: adicionar regras URL-level explícitas — `GET /api/v1/oee/planned-downtimes/**` → `hasAnyRole("OPERATOR","SUPERVISOR","ADMIN")`; `POST/PUT/DELETE /api/v1/oee/planned-downtimes/**` → `hasAnyRole("SUPERVISOR","ADMIN")`
12. `@PreAuthorize` permanece nos métodos como segunda barreira (ADR-033 Decisão 5)

**Frontend — DAILY fora do mês (SH-40)**
13. `MaintenanceCalendarComponent`: schedules `DAILY` não devem exibir badge em dias de padding do grid 6×7 (dias fora do mês corrente)
14. Guard: `if (!day.inCurrentMonth) return false` antes de `scheduleFallsOnDate`
15. Spec: caso (a) dia de padding anterior ao mês → schedule DAILY sem badge; caso (b) dia de padding posterior ao mês → sem badge; caso (c) dia do mês corrente → badge exibido

**Frontend — equipmentOptions via API (SUG-30)**
16. `PlannedDowntimeCalendarComponent.equipmentOptions`: substituir `computed()` derivado das paradas por `signal<EquipmentOption[]>([])` populado por `MaintenanceService.listEquipment()` na inicialização via `forkJoin` junto ao carregamento das paradas
17. Falha em `listEquipment()` não bloqueia o calendário — `equipmentOptions` permanece `[]` e formError exibe aviso
18. Spec: mock de `listEquipment()` retornando 3 equipamentos; assert que o select exibe os 3 independentemente de paradas existentes

---

#### US-046 — Configuração de thresholds de alerta (3 pts)

**Backend**
1. Entidade `AlertThreshold` em `common/domain/` — campos: `id` (UUID), `metric` (enum `AlertMetric`, `@Column(unique=true)`), `threshold` (Double, `@NotNull @Positive`), `emailEnabled` (boolean, default false), `active` (boolean, default true), `createdBy`, `updatedAt`; tabela `alert_threshold`
2. Enum `AlertMetric`: `OEE_AVG_BELOW`, `NC_CRITICAL_ABOVE`, `WO_URGENT_PENDING_HOURS`
3. `POST /api/v1/admin/alert-thresholds` (ADMIN) — `metric` duplicado em threshold ativo retorna `409 { "message": "Já existe threshold ativo para esta métrica" }`; retorna `201 AlertThresholdResponse`
4. `GET /api/v1/admin/alert-thresholds` (ADMIN) — lista todos os ativos, ordenados por `metric`
5. `PUT /api/v1/admin/alert-thresholds/{id}` (ADMIN) — atualiza `threshold` e `emailEnabled`; campo `metric` imutável; retorna `200` ou `404`
6. `DELETE /api/v1/admin/alert-thresholds/{id}` (ADMIN) — soft delete (`active=false`); retorna `204` ou `404`
7. Pacotes: `AlertThresholdController` em `common/presentation/`; use cases em `common/application/usecase/`; `AlertThresholdRepository` em `common/infrastructure/`
8. Seed: 3 defaults — `OEE_AVG_BELOW threshold=0.65`, `NC_CRITICAL_ABOVE threshold=3`, `WO_URGENT_PENDING_HOURS threshold=4` — todos `emailEnabled=false`
9. `AlertThresholdResponse` record: `id`, `metric`, `threshold`, `emailEnabled`, `active`, `updatedAt`

**Frontend**
10. Rota `/admin/alert-thresholds` (ADMIN, lazy-loaded) — tabela: métrica (label PT-BR), valor, email (ícone), ações
11. Labels PT-BR: `OEE_AVG_BELOW` → "OEE médio abaixo de (%)", `NC_CRITICAL_ABOVE` → "NCs críticas abertas acima de (un.)", `WO_URGENT_PENDING_HOURS` → "OSs urgentes abertas há mais de (h)"
12. "Novo Threshold": dialog com select de métrica (só sem threshold ativo), input numérico, toggle email; sucesso → fecha + snackbar + lista atualiza
13. "Editar" por linha: dialog pré-preenchido; métrica readonly; `PUT`
14. "Excluir" por linha: confirmação + snackbar "Threshold removido"
15. Link no nav admin — visível apenas para ADMIN
16. `ChangeDetectionStrategy.OnPush`, standalone, signals

---

#### US-047 — Motor de avaliação de alertas + notificação email (4 pts)

**Backend — Entidade Notification**
1. Entidade `Notification` em `common/domain/` — campos: `id` (UUID), `username` (String, nullable — null = broadcast), `title`, `body`, `severity` (enum `NotificationSeverity`: `INFO`/`WARNING`/`CRITICAL`), `metric` (String, nullable), `createdAt`, `readAt` (nullable); tabela `notification` com índices em `username`, `read_at`, `created_at`
2. `NotificationRepository.existsByMetricAndCreatedAtAfter(String metric, LocalDateTime since)` — consulta de debounce

**Backend — Motor de avaliação**
3. `AlertEvaluatorJob` com `@Scheduled(cron = "0 0/30 * * * *", zone = "America/Sao_Paulo")` chama `AlertEvaluatorUseCase.execute()`
4. Para `OEE_AVG_BELOW`: calcula OEE médio dos últimos 30 dias; dispara se < threshold; severity `CRITICAL`
5. Para `NC_CRITICAL_ABOVE`: count de NCs `severity=CRITICAL` e `status != CLOSED`; dispara se > threshold; severity `WARNING`
6. Para `WO_URGENT_PENDING_HOURS`: count de OSs `priority=URGENT`, `status=OPEN`, `openedAt + threshold h < now()`; dispara se > 0; severity `WARNING`
7. Debounce: `existsByMetricAndCreatedAtAfter(metric.name(), now().minusMinutes(60))` → se true, não cria nem envia
8. Notification criada com `username=null` (broadcast), `metric` preenchido
9. `emailEnabled=true`: busca usuários SUPERVISOR+ via `UserRepository`; envia por `JavaMailSender` `@Async`; falha logada sem abortar loop
10. `POST /api/v1/admin/alert-thresholds/evaluate-now` (ADMIN) — dispara avaliação imediata; retorna `200 { "evaluated": N }`
11. `AlertEvaluatorUseCaseTest`: 5 cenários — disparo, não-disparo (abaixo do threshold), debounce, email desabilitado, falha de email isolada (demais thresholds continuam)

---

#### US-048 — Central de notificações in-app (3 pts)

**Backend**
1. `GET /api/v1/notifications` (autenticado) — `Page<NotificationResponse>` com `username = <atual>` OR `username IS NULL` (broadcasts); não lidas primeiro, depois `createdAt DESC`; `size=20`
2. `GET /api/v1/notifications/unread-count` (autenticado) — `{ "count": N }` (pessoais + broadcasts não lidos)
3. `PUT /api/v1/notifications/{id}/read` (autenticado) — `readAt = now()`; `200` ou `404`; `403` se `username != null && username != current`
4. `PUT /api/v1/notifications/read-all` (autenticado) — marca todas pessoais + broadcasts não lidos; `204`
5. `NotificationResponse` record: `id`, `username`, `title`, `body`, `severity`, `createdAt`, `readAt`

**Frontend**
6. `NavComponent`: sino `mat-icon` + `MatBadge` com `unreadCount()`; `[matBadgeHidden]="unreadCount() === 0"`
7. Polling: `interval(60_000).pipe(startWith(0), switchMap(() => notificationService.getUnreadCount()), takeUntilDestroyed())` alimenta `unreadCount = signal<number>(0)`
8. Clique abre `MatMenu` com últimas 10 notificações (carregadas ao abrir)
9. Card de notificação: ícone de severidade colorido (`CRITICAL=#EF4444`, `WARNING=#F97316`, `INFO=#0099B8`), título bold se não lida, body truncado 80 chars, data formatada
10. "Marcar todas como lidas": `PUT /read-all` + `unreadCount.set(0)` (optimistic update)
11. Clique em item: `PUT /{id}/read` + `unreadCount.update(n => Math.max(0, n-1))` + fecha menu
12. Spec `nav.component.spec.ts`: (a) badge visível quando `count > 0`; (b) badge oculto quando `count = 0`; (c) "marcar todas" → `PUT /read-all` chamado + badge zerado

---

## Sprint 19 ✅
**Objetivo**: Gestão de turnos e rastreabilidade de registros por turno
**ADR**: ADR-016, ADR-034
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-054 | Cadastro de turnos (Shift CRUD) | 2 | ✅ concluído |
| US-055 | Associação automática de OSs e importações OEE a turno | 3 | ✅ concluído |
| US-056 | Filtro e analytics por turno | 3 | ✅ concluído |
| US-089 | Tech debt Sprint 18 — rate limiting + audit no evaluate-now (SEC-061) | 1 | ✅ concluído |

**Total**: 9 pontos

### Dependências
- US-055 depende de US-054 (entidade `Shift` e `ShiftResolverService` precisam existir)
- US-056 depende de US-054 (necessita IDs de turno reais para os filtros e frontend)
- US-089 independente das demais — pode ser desenvolvida em paralelo

---

#### US-054 — Cadastro de turnos (2 pts)

**Backend**
1. Entidade `Shift` criada no pacote `common/domain/` com campos: `id` (UUID), `name` (String, max 50, não nulo), `startTime` (`LocalTime`), `endTime` (`LocalTime`), `overnight` (boolean), `active` (boolean, default `true`); tabela `shift` (conforme ADR-016 Decisão 1)
2. `POST /api/v1/admin/shifts` cria `Shift` (ADMIN); campos obrigatórios: `name`, `startTime` (formato ISO time `HH:mm`), `endTime` (formato ISO time `HH:mm`), `overnight` (boolean); retorna `201 ShiftResponse`
3. `name` em branco ou nulo retorna `400 { "message": "nome é obrigatório" }`; `startTime` ou `endTime` ausentes retornam `400 { "message": "..." }` (Bean Validation)
4. Sobreposição entre o novo turno e qualquer turno ativo existente retorna `422 { "message": "Turno sobrepõe turno existente: {nome}" }` — validada no use case antes do `save()` (conforme ADR-016 Consequências); turno noturno (`overnight=true`) tem lógica própria: cobre `[startTime, 24:00)` ∪ `[00:00, endTime)` — sobreposição considera essa extensão
5. Turno noturno explícito: quando `overnight=true`, `endTime < startTime` é o estado esperado (ex: `startTime=22:00`, `endTime=06:00`); when `overnight=false`, `endTime <= startTime` retorna `400 { "message": "endTime deve ser posterior a startTime para turno não-noturno" }`
6. `GET /api/v1/admin/shifts` retorna `List<ShiftResponse>` de todos os turnos ativos, ordenados por `startTime` (OPERATOR+); `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`
7. `PUT /api/v1/admin/shifts/{id}` atualiza `name`, `startTime`, `endTime`, `overnight`; as mesmas validações de sobreposição e coerência de horário se aplicam; turno inexistente retorna `404`; retorna `200 ShiftResponse` (ADMIN)
8. `PUT /api/v1/admin/shifts/{id}/deactivate` seta `active = false`; retorna `204`; turno inexistente retorna `404` (ADMIN)
9. `ShiftResponse` é Java record com campos: `id`, `name`, `startTime`, `endTime`, `overnight`, `active`
10. Pacotes conforme ADR-016 Decisão 3: `CreateShiftUseCase`, `GetShiftListUseCase`, `UpdateShiftUseCase`, `DeactivateShiftUseCase` em `common/application/usecase/`; `ShiftController` em `common/presentation/`; `ShiftRepository` em `common/infrastructure/`
11. `CreateShiftUseCase`: `@PreAuthorize("hasRole('ADMIN')")` no controller; `DeactivateShiftUseCase` e `UpdateShiftUseCase` idem
12. Teste unitário `CreateShiftUseCaseTest`: (a) criação de turno normal (diurno) bem-sucedida — sem sobreposição; (b) criação de turno noturno overnight (`22:00–06:00`) bem-sucedida; (c) sobreposição com turno ativo existente lança exceção 422; (d) `overnight=false` com `endTime <= startTime` lança exceção 400

**Frontend**
13. Rota `/admin/shifts` (ADMIN, lazy-loaded): tabela com colunas nome, início, fim, tipo (chip "Noturno" ícone lua / "Diurno" ícone sol), status (chip Ativo/Inativo); exibe mensagem "Nenhum turno cadastrado" quando lista vazia
14. Botão "Novo Turno" (ADMIN) abre formulário inline ou modal: `name` (input text), `startTime` (input `type="time"`), `endTime` (input `type="time"`), toggle "Turno noturno (passa meia-noite)"; quando toggle ativo, exibe nota "O turno se estende além da meia-noite (ex: 22:00–06:00)"
15. Validação client-side: `name` obrigatório; `startTime` e `endTime` obrigatórios; quando `overnight=false`, `endTime` deve ser posterior a `startTime` (validador custom no `FormGroup`); botão "Salvar" desabilitado enquanto formulário inválido
16. Criação bem-sucedida: snackbar "Turno criado" + lista atualizada sem reload
17. Erro `422` da API exibe mensagem retornada pela API em snackbar de erro
18. Botão "Desativar" por linha (ADMIN) com dialog de confirmação `MatDialog`; após confirmação envia `PUT .../deactivate`; snackbar "Turno desativado"
19. Chip de horário por linha: exibe "HH:mm – HH:mm" (ex: "06:00 – 14:00"); turno noturno exibe "22:00 – 06:00 ✦" com ícone/sufixo indicando overnight
20. Link "Turnos" adicionado ao submenu de administração no `NavComponent`, visível apenas para ADMIN
21. `ChangeDetectionStrategy.OnPush`, standalone, signals
22. Spec `shift-list.component.spec.ts`: (a) tabela exibe 3 turnos mockados; (b) formulário desabilitado enquanto `name` vazio; (c) erro 422 exibe snackbar com mensagem da API; (d) chip "Noturno" exibido apenas quando `overnight=true`

---

#### US-055 — Associação automática de OSs e importações OEE a turno (3 pts)

**Backend**
1. `ShiftResolverService` criado no pacote `common/application/usecase/` (utilitário compartilhado, não um use case isolado — conforme ADR-016 Decisão 3); método `resolveCurrentShift(List<Shift> activeShifts, LocalTime now): Optional<Shift>`; recebe a lista de turnos como parâmetro (sem chamada ao banco diretamente — testável sem contexto JPA)
2. Lógica de resolução: para turno normal (`overnight=false`), cobre `[startTime, endTime)`; para turno noturno (`overnight=true`), cobre `[startTime, 24:00)` ∪ `[00:00, endTime)`; retorna o primeiro turno ativo que cobre `now`; se nenhum cobre, retorna `Optional.empty()`
3. `CreateWorkOrderUseCase`: antes do `save()`, chama `shiftRepository.findAllByActiveTrue()` e depois `shiftResolverService.resolveCurrentShift(activeShifts, LocalTime.now())`; resultado (nullable) é associado à OS criada; `shift = null` não bloqueia criação (graceful degradation — conforme ADR-016 Decisão 2)
4. `ProcessOeeImportUseCase` (ou use case equivalente de importação OEE): mesma lógica — associa `Shift` ao `ImportBatch` criado a partir do horário do servidor no momento da importação
5. Migration: coluna `shift_id UUID NULLABLE REFERENCES shift(id)` adicionada à tabela `work_order`; coluna `shift_id UUID NULLABLE REFERENCES shift(id)` adicionada à tabela `import_batch`; retrocompatível — registros existentes terão `shift_id = NULL`
6. Entidade `WorkOrder`: campo `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "shift_id") Shift shift` adicionado (nullable)
7. Entidade `ImportBatch`: campo `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "shift_id") Shift shift` adicionado (nullable)
8. `WorkOrderResponse` record: campos `shiftId` (UUID nullable) e `shiftName` (String nullable) adicionados; derivados do `shift` da entidade (null-safe)
9. `GET /api/v1/maintenance/work-orders` passa a aceitar query param opcional `?shiftId=<uuid>` — quando informado, filtra OSs pelo turno; quando ausente, retorna todas (comportamento anterior preservado); `WorkOrderRepository` recebe método de query com `shiftId` opcional
10. Teste unitário `ShiftResolverServiceTest`: (a) turno diurno 06:00–14:00, `now=09:00` → turno encontrado; (b) turno diurno 06:00–14:00, `now=14:00` → não encontrado (endTime exclusivo); (c) turno noturno 22:00–06:00, `now=23:30` → turno encontrado; (d) turno noturno 22:00–06:00, `now=03:00` → turno encontrado; (e) turno noturno 22:00–06:00, `now=10:00` → não encontrado; (f) lista de turnos vazia → `Optional.empty()`
11. Teste unitário `CreateWorkOrderUseCaseTest`: (a) criação com turno ativo cobrindo o horário → `workOrder.getShift()` não nulo; (b) criação sem nenhum turno ativo → `workOrder.getShift()` nulo (sem exceção)

**Frontend**
12. `WorkOrderResponse` interface TypeScript recebe campos `shiftId: string | null` e `shiftName: string | null`
13. Listagem de OSs (`/maintenance/work-orders`): chip de turno (ex: mat-chip "Tarde") exibido em cada card/linha quando `shiftName` não nulo; quando nulo, célula/espaço permanece vazio (sem texto "N/A")
14. Página de detalhe da OS: campo "Turno" exibido na seção de informações; valor "—" quando `shiftName` nulo
15. Loading state: skeleton loader exibido enquanto a listagem de OSs carrega; erro de API exibe snackbar
16. Spec `work-order-list.component.spec.ts`: (a) OS com `shiftName = "Manhã"` exibe chip "Manhã"; (b) OS com `shiftName = null` não exibe chip de turno

---

#### US-056 — Filtro e analytics por turno (3 pts)

**Backend**
1. `GET /api/v1/analytics/oee/trend` passa a aceitar query param opcional `?shiftId=<uuid>`; quando informado, filtra `ImportBatch` pelo turno antes de calcular o OEE semanal; quando ausente, comportamento anterior (todos os batches) preservado; `GetOeeTrendUseCase` recebe parâmetro `shiftId` opcional (conforme ADR-016 Decisão 5)
2. `GET /api/v1/analytics/maintenance/wo-summary` passa a aceitar query param opcional `?shiftId=<uuid>`; quando informado, distribui apenas OSs do turno especificado; `GetMaintenanceAnalyticsUseCase` recebe `shiftId` opcional (conforme ADR-016 Decisão 5)
3. `shiftId` inexistente (UUID válido mas sem turno cadastrado) retorna distribuição vazia (zeros) ou trend com todos os valores `null` — sem `404` (filtro sem resultado não é erro)
4. `shiftId` com UUID malformado retorna `400 { "message": "shiftId inválido" }` via `MethodArgumentTypeMismatchException` handler no `GlobalExceptionHandler`
5. `WorkOrderRepository`: método de query para `wo-summary` com `shiftId` opcional (JPQL com `(:shiftId IS NULL OR wo.shift.id = :shiftId)`)
6. Ambos os endpoints mantêm `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`
7. Teste unitário `GetOeeTrendUseCaseTest` — novos casos: (a) `shiftId` informado — apenas batches do turno entram no cálculo; (b) `shiftId` nulo — todos os batches (comportamento anterior)
8. Teste unitário `GetMaintenanceAnalyticsUseCaseTest` — novos casos: (a) `shiftId` informado — distribuição apenas do turno; (b) `shiftId` nulo — todas as OSs

**Frontend**
9. Dropdown "Turno" adicionado aos filtros da página `/maintenance/work-orders`: opções carregadas via `GET /api/v1/admin/shifts` na inicialização do componente; primeira opção: "Todos os turnos" (sem filtro); mudança de seleção recarrega a lista de OSs com `?shiftId=<uuid>`
10. Dropdown "Turno" adicionado à barra de filtros da página `/analytics/oee`: mesma fonte de dados (`GET /api/v1/admin/shifts`); primeira opção "Todos os turnos"; mudança dispara reconsulta imediata com `shiftId` no payload do `GetOeeTrendUseCase`
11. Quando nenhum turno estiver cadastrado (`/admin/shifts` retorna lista vazia), o dropdown não é exibido (componente usa `@if (shifts().length > 0)`)
12. Falha ao carregar turnos não bloqueia a página — dropdown permanece oculto, demais filtros funcionam normalmente; erro logado em console
13. `ChangeDetectionStrategy.OnPush`, standalone, signals; `shifts = signal<ShiftOption[]>([])`
14. Spec `oee-analytics.component.spec.ts` — novos casos: (a) dropdown exibido quando `shifts()` tem 2 itens mockados; (b) dropdown oculto quando `shifts()` vazio; (c) seleção de turno dispara chamada ao serviço com `shiftId` correto
15. Spec `work-order-list.component.spec.ts` — novo caso: seleção de turno no dropdown filtra a lista (mock do serviço chamado com `shiftId`)

---

#### US-089 — Tech debt Sprint 18 — SEC-061: rate limiting + audit no evaluate-now (1 pt)

> Cobre: SEC-061 (identificado por Beatriz na revisão da Sprint 18) — o endpoint `POST /api/v1/admin/alert-thresholds/evaluate-now` não possui rate limiting nem registro de auditoria.

**Backend — Rate Limiting**
1. `POST /api/v1/admin/alert-thresholds/evaluate-now` recebe anotação de rate limiting (Bucket4j, conforme ADR-021 Decisão 1 — bucket em memória com Caffeine); limite: 5 requisições por minuto por usuário (identificado pelo `username` do JWT); excedido retorna `429 Too Many Requests` com body `{ "message": "Muitas requisições. Aguarde antes de acionar novamente." }`
2. `RateLimiterInterceptor` (ou configuração Bucket4j existente da Sprint 11) deve ser extendido para cobrir a rota `/api/v1/admin/alert-thresholds/evaluate-now`; sem duplicação de código — reutilizar a infraestrutura de rate limiting já existente
3. Teste de integração ou unitário: chamada ao endpoint 6× em sequência pelo mesmo usuário → 5 primeiras retornam `200`; 6ª retorna `429`

**Backend — Auditoria**
4. `AuditAction` enum: adicionar constante `ALERT_EVALUATED`
5. `AlertEvaluatorUseCase.execute()` (invocado pelo endpoint `evaluate-now`) chama `auditService.log(username, ALERT_EVALUATED, "AlertThreshold", null, Map.of("triggeredBy", "manual", "evaluated", N))` após a avaliação; `username` passado pelo `EvaluateNowController` via `principal.getName()`; falha do auditService não aborta a execução (padrão `@Async` já aplicado ao `AuditService`)
6. `EvaluateNowController` (ou método no `AlertThresholdController`): assinatura do método de handler atualizada para receber `Principal principal` e repassar `principal.getName()` ao use case
7. Teste unitário `AlertEvaluatorUseCaseTest` — novos casos: (a) disparo manual com `N` thresholds avaliados → `verify(auditService).log(eq("admin"), eq(ALERT_EVALUATED), ...)` confirmado; (b) `auditService` lança exceção → avaliação continua (sem propagação — `@Async` isola a falha)

---

## Sprint 20 ✅
**Objetivo**: Peças e insumos — catálogo, consumo em OSs e alertas de reposição
**ADR**: ADR-014, ADR-035
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-049 | Cadastro de peças (catálogo) | 3 | ✅ concluído |
| US-050 | Consumo de peças em ordens de serviço | 4 | ✅ concluído |
| US-051 | Alertas de estoque mínimo | 2 | ✅ concluído |
| US-090 | Tech debt Sprint 19 | 1 | ✅ concluído |

**Total**: 10 pontos

### Dependências
- US-050 depende de US-049 (entidade `SparePart` e `SparePartRepository` precisam existir)
- US-051 depende de US-050 (o disparo de alerta ocorre dentro do `AddWorkOrderPartUseCase`)
- US-090 independente — pode ser desenvolvida em paralelo

---

#### US-049 — Cadastro de peças (3 pts)

**Backend**
1. Entidades `SparePart` e `WorkOrderPart` criadas no pacote `maintenance/domain/` conforme ADR-014 Decisão 1; migration `V{N}__spare_parts.sql` cria tabelas `spare_part` (com índice único em `code` e índice em `category`) e `work_order_part` (FK para `spare_part` e `work_order`)
2. `POST /api/v1/maintenance/spare-parts` cria `SparePart` (ADMIN); `@PreAuthorize("hasRole('ADMIN')")`; `CreateSparePartRequest` record com: `@NotBlank String code`, `@NotBlank String name`, `String category`, `String unit`, `@NotNull @Min(0) Integer stockQty`, `@NotNull @Min(0) Integer minStockQty`; retorna `201 SparePartResponse`
3. `code` duplicado (violação de `@Column(unique=true)`) retorna `409 { "message": "Já existe uma peça com o código informado." }`
4. Campos `@NotBlank`/`@NotNull` ausentes ou inválidos retornam `400 { "message": "..." }` via `MethodArgumentNotValidException` no `GlobalExceptionHandler`; `stockQty` ou `minStockQty` negativos retornam `400 { "message": "Quantidade não pode ser negativa" }` (`@Min(0)`)
5. `GET /api/v1/maintenance/spare-parts` lista peças ativas (`active=true`); `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`; aceita query params opcionais: `?category=<string>` (filtro por categoria, case-insensitive `LOWER(p.category) = LOWER(:category)`) e `?belowMin=true` (retorna apenas peças com `stockQty < minStockQty`); os dois filtros podem ser combinados; retorna `List<SparePartResponse>`
6. `GET /api/v1/maintenance/spare-parts/{id}` retorna `SparePartResponse` ou `404 { "message": "Peça não encontrada" }` (OPERATOR+)
7. `PUT /api/v1/maintenance/spare-parts/{id}` atualiza `name`, `category`, `unit`, `minStockQty` (ADMIN); `@PreAuthorize("hasRole('ADMIN')")`; campo `stockQty` **não** é atualizado por este endpoint; `minStockQty` aceita apenas `@Min(0)`; peça inexistente retorna `404`; peça inativa retorna `422 { "message": "Peça inativa não pode ser editada" }`; retorna `200 SparePartResponse`
8. `PUT /api/v1/maintenance/spare-parts/{id}/stock` ajuste manual de estoque (ADMIN); `@PreAuthorize("hasRole('ADMIN')")`; `UpdateSparePartStockRequest` record com: `@NotNull Integer quantity` (positivo = entrada, negativo = saída), `@NotBlank String reason`; resultado `stockQty + quantity < 0` retorna `422 { "message": "Estoque resultante seria negativo: atual N, ajuste M" }`; ajuste bem-sucedido registra `auditService.log(username, PART_STOCK_ADJUSTED, "SparePart", id, Map.of("delta", quantity, "reason", reason))`; retorna `200 SparePartResponse` com novo saldo
9. `AuditAction` enum: adicionar constantes `PART_CREATED` e `PART_STOCK_ADJUSTED`; `CreateSparePartUseCase` chama `auditService.log(username, PART_CREATED, "SparePart", id, Map.of("code", code))`
10. `SparePartResponse` record: `id` (UUID), `code`, `name`, `category`, `unit`, `stockQty`, `minStockQty`, `active`, `belowMin` (calculado: `stockQty < minStockQty`); `belowMin` evita cálculo no frontend
11. Pacotes conforme ADR-014 Decisão 3: `CreateSparePartUseCase`, `GetSparePartListUseCase`, `UpdateSparePartUseCase`, `UpdateSparePartStockUseCase` em `maintenance/application/usecase/`; `SparePartRepository`, `WorkOrderPartRepository` em `maintenance/infrastructure/`; endpoints em `MaintenanceController` (já existente)
12. Teste unitário `CreateSparePartUseCaseTest`: (a) criação bem-sucedida — `save()` chamado, `auditService.log(PART_CREATED)` confirmado; (b) `code` duplicado — `DataIntegrityViolationException` propagada como `409`; (c) `stockQty` negativo — `400` antes do `save()`

**Frontend**
13. Rota `/maintenance/spare-parts` (OPERATOR+, lazy-loaded): tabela com colunas código, nome, categoria, unidade, estoque atual, estoque mínimo, status do estoque; linhas com `belowMin=true` recebem cor de fundo `#FEE2E2` (vermelho claro) e ícone de alerta `mat-icon warning` na coluna de estoque; estado de loading: skeleton loader (3 linhas) enquanto `isLoading()=true`; empty state: "Nenhuma peça cadastrada" com botão "Nova Peça" quando lista vazia
14. Filtros na barra superior: input de busca por código/nome (filtra na lista carregada, sem nova requisição), select de categoria (valores únicos extraídos da lista), toggle "Somente abaixo do mínimo" (chama `?belowMin=true`); botão "Limpar filtros" reseta todos
15. Botão "Nova Peça" (ADMIN, `@if (isAdmin())`): abre `MatDialog` com formulário reactive — campos: código (`@NotBlank`, trim), nome (`@NotBlank`), categoria (input livre), unidade (input livre), estoque inicial (`@Min(0)`), estoque mínimo (`@Min(0)`); botão "Salvar" desabilitado enquanto inválido ou `isSaving()`; sucesso: fecha dialog + snackbar "Peça criada com sucesso" + lista recarregada
16. Botão "Editar" por linha (ADMIN): dialog pré-preenchido sem campos de estoque; mesmas validações do formulário de criação exceto `code` (somente leitura no edit)
17. Botão "Ajustar Estoque" por linha (ADMIN): dialog com campo quantidade (aceita valores negativos) e motivo (`@NotBlank`); exibe saldo atual no cabeçalho do dialog ("Estoque atual: N un"); preview do resultado calculado em tempo real ("Saldo após ajuste: X"); quantity que resultaria em negativo mostra aviso inline antes de submeter; erro `422` da API exibe snackbar com mensagem retornada
18. Erros HTTP `400`/`409`/`422` retornados pela API são exibidos em snackbar de erro (cor `#EF4444`); erros `500` exibem mensagem genérica "Erro inesperado. Tente novamente."
19. `ChangeDetectionStrategy.OnPush`, standalone, signals; `spareParts = signal<SparePartResponse[]>([])`, `isLoading = signal(false)`, `isAdmin = computed(() => ...)`
20. Spec `spare-part-list.component.spec.ts`: (a) tabela exibe 3 peças mockadas com colunas corretas; (b) linha com `belowMin=true` tem classe CSS de alerta verificada; (c) empty state exibido quando lista vazia; (d) erro `409` exibe snackbar com mensagem da API; (e) botão "Nova Peça" oculto para role OPERATOR

---

#### US-050 — Consumo de peças em OSs (4 pts)

**Backend**
1. `POST /api/v1/maintenance/work-orders/{id}/parts` registra consumo (SUPERVISOR+); `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`; `AddWorkOrderPartRequest` record com: `@NotNull UUID sparePartId`, `@NotNull @Min(1) Integer quantity`; `quantity=0` retorna `400 { "message": "Quantidade deve ser maior que zero" }` (`@Min(1)`)
2. Dentro do mesmo `@Transactional`: (1) valida existência da OS (`WorkOrderRepository.findById`) — `404` se ausente; (2) valida existência da peça (`SparePartRepository.findById`) — `404 { "message": "Peça não encontrada" }` se ausente; (3) valida `sparePart.isActive()=true` — `422 { "message": "Peça inativa não pode ser consumida" }` se inativa; (4) valida `sparePart.stockQty - quantity >= 0` — `422 { "message": "Estoque insuficiente: disponível N, solicitado M" }` se insuficiente; (5) cria `WorkOrderPart` (com `addedBy = username`, `addedAt = LocalDateTime.now()`); (6) decrementa `sparePart.stockQty -= quantity`; retorna `201 WorkOrderPartResponse`
3. `WorkOrderPartResponse` record: `id` (UUID), `sparePartId`, `sparePartCode`, `sparePartName`, `quantity`, `addedBy`, `addedAt`; campos `sparePartCode` e `sparePartName` populados via projeção ou `sparePart` carregado — sem N+1 (usar `@Query` com JOIN FETCH ou projeção de interface)
4. `GET /api/v1/maintenance/work-orders/{id}/parts` lista peças consumidas da OS (OPERATOR+); `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`; OS inexistente retorna `404`; retorna `List<WorkOrderPartResponse>` (pode ser vazia — `[]`)
5. `DELETE /api/v1/maintenance/work-orders/{id}/parts/{partId}` remove consumo e restaura estoque (SUPERVISOR+); `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`; dentro do mesmo `@Transactional`: (1) valida existência da OS e do `WorkOrderPart` (pertencente à OS) — `404` se ausente; (2) restaura `sparePart.stockQty += workOrderPart.quantity`; (3) deleta `WorkOrderPart`; retorna `204`
6. `AuditAction` enum: adicionar `PART_CONSUMED` e `PART_CONSUMPTION_REMOVED`; `AddWorkOrderPartUseCase` chama `auditService.log(username, PART_CONSUMED, "WorkOrderPart", id, Map.of("workOrderId", ..., "sparePartId", ..., "quantity", ...))`; remoção idem com `PART_CONSUMPTION_REMOVED`
7. Teste unitário `AddWorkOrderPartUseCaseTest`: (a) consumo bem-sucedido — `save(workOrderPart)` + `stockQty` decrementado + `auditService.log(PART_CONSUMED)` confirmados; (b) estoque insuficiente (`stockQty=2, quantity=3`) → `422` sem `save()`; (c) OS inexistente → `404` antes de qualquer operação no estoque; (d) peça inexistente → `404`; (e) peça inativa → `422 "Peça inativa"`; (f) `quantity=0` → `400` (Bean Validation antes de entrar no use case)

**Frontend**
8. Seção "Peças Utilizadas" adicionada à página de detalhe da OS (`/maintenance/work-orders/{id}`): tabela com colunas peça (código + nome), quantidade, adicionada por, data; estado de loading: skeleton de 2 linhas; empty state: "Nenhuma peça registrada nesta OS"
9. Botão "+ Adicionar Peça" (SUPERVISOR+, `@if (canEdit())`): abre `MatDialog` com autocomplete de peça (`MatAutocomplete`) que busca por código ou nome via `GET /api/v1/maintenance/spare-parts` com debounce de 300 ms; cada opção exibe `[{code}] {name} — Estoque: {stockQty} {unit}`; campo quantidade (`@Min(1)`); saldo atual da peça selecionada exibido abaixo do campo de quantidade como lembrete; botão "Adicionar" desabilitado enquanto inválido ou `isSaving()`; sucesso: fecha dialog + snackbar "Peça adicionada" + seção "Peças Utilizadas" recarregada
10. Erro `422` (estoque insuficiente) exibido em snackbar de erro com a mensagem literal da API ("Estoque insuficiente: disponível N, solicitado M"); erro `404` (peça removida entre seleção e submit) exibe "Peça não encontrada. Recarregue a página."
11. Botão lixeira por linha (SUPERVISOR+): `MatDialog` de confirmação "Remover peça {nome} (qtd: {N}) da OS? O estoque será restaurado."; confirmar → `DELETE`; snackbar "Consumo removido e estoque restaurado"; linha removida da tabela (optimistic removal após confirmação)
12. `ChangeDetectionStrategy.OnPush`, standalone, signals; `parts = signal<WorkOrderPartResponse[]>([])`, `isLoadingParts = signal(false)`, `canEdit = computed(() => role === 'SUPERVISOR' || role === 'ADMIN')`
13. Spec `work-order-detail.component.spec.ts` — casos para a seção de peças: (a) tabela exibe 2 peças mockadas; (b) empty state quando `parts()` vazio; (c) erro `422` exibe snackbar com mensagem da API; (d) botão "+ Adicionar Peça" oculto para role OPERATOR; (e) clique em lixeira abre dialog de confirmação

---

#### US-051 — Alertas de estoque mínimo (2 pts)

**Backend**
1. `AddWorkOrderPartUseCase`: após decrementar `sparePart.stockQty`, se `stockQty < minStockQty`, chama `notificationService.broadcast(title, body, severity)` com: `title = "Estoque baixo: " + sparePart.getName()`, `body = "Estoque atual: " + stockQty + " " + unit + " (mínimo: " + minStockQty + ")"`, `severity = WARNING`; o disparo de notificação **não** está no `@Transactional` do consumo — chama `notificationService` após o commit (via `@TransactionalEventListener` ou chamada após `save()` fora da transação, conforme padrão já estabelecido em ADR-013)
2. Debounce de 24 h: antes de chamar `notificationService.broadcast()`, verifica `notificationRepository.existsByTitleAndCreatedAtAfter("Estoque baixo: " + sparePart.getName(), LocalDateTime.now().minusHours(24))`; se `true`, pula a notificação sem erro; `NotificationRepository` já possui `existsByMetricAndCreatedAtAfter` — adicionar método análogo `existsByTitleAndCreatedAtAfter(String title, LocalDateTime since)` (JPQL simples)
3. `UpdateSparePartStockUseCase` (ajuste manual): após atualizar `stockQty`, aplica a mesma lógica de debounce + `notificationService.broadcast()` se o resultado ainda estiver abaixo do mínimo — garante alerta mesmo em saídas manuais
4. `GET /api/v1/maintenance/spare-parts?belowMin=true` retorna apenas peças ativas com `stockQty < minStockQty`; `SparePartRepository` deve ter método `List<SparePart> findAllByActiveTrueAndStockQtyLessThanMinStockQty()` (ou JPQL equivalente); endpoint já coberto pelo AC#5 da US-049 — confirmar que o filtro `belowMin=true` está implementado na query
5. Teste unitário `AddWorkOrderPartUseCaseTest` — casos adicionais para US-051: (a) consumo que resulta em `stockQty < minStockQty` sem notificação recente → `notificationService.broadcast()` chamado uma vez; (b) consumo abaixo do mínimo com notificação nas últimas 24 h → `notificationService.broadcast()` **não** chamado (debounce); (c) consumo que mantém `stockQty >= minStockQty` → `notificationService.broadcast()` não chamado

**Frontend**
6. Card "Estoque Crítico" adicionado à rota `/maintenance/dashboard` (seção superior, antes dos KPIs de OSs): exibe lista de `SparePartResponse` com `belowMin=true` carregados via `GET /api/v1/maintenance/spare-parts?belowMin=true` na inicialização; cada item: código + nome, barra de progresso `[stockQty / minStockQty]` (ex: "3 / 10 un"), cor da barra vermelha (`#EF4444`); empty state do card: "Nenhuma peça abaixo do estoque mínimo" com ícone check verde; card com `isLoading()` exibe skeleton de 3 linhas
7. Badge numérico "Estoque Crítico" no item "Peças" do submenu de Manutenção no `NavComponent`: `MatBadge` com a contagem de peças `belowMin=true`; polling a cada 5 minutos via `interval(300_000).pipe(startWith(0), switchMap(() => maintenanceService.countBelowMin()), takeUntilDestroyed())`; `[matBadgeHidden]="belowMinCount() === 0"`; `belowMinCount = signal<number>(0)`
8. `MaintenanceService`: adicionar método `listBelowMin(): Observable<SparePartResponse[]>` (`GET ?belowMin=true`) e `countBelowMin(): Observable<number>` (chama `listBelowMin()` e retorna `arr.length`)
9. Erro ao carregar card "Estoque Crítico" exibe mensagem "Não foi possível carregar o estoque crítico" dentro do card — não bloqueia o restante do dashboard
10. `ChangeDetectionStrategy.OnPush`, standalone, signals; `belowMinParts = signal<SparePartResponse[]>([])`, `isLoadingCritical = signal(false)`, `belowMinCount = signal<number>(0)`
11. Spec `maintenance-dashboard.component.spec.ts`: (a) card exibe 2 peças mockadas com `belowMin=true`; (b) empty state exibido quando lista vazia; (c) badge no nav exibido quando `belowMinCount() > 0`; (d) badge oculto quando `belowMinCount() = 0`

---

#### US-090 — Tech debt Sprint 19 (1 pt)

> Cobre 5 itens identificados por Beatriz (SEC) e Helena (SH) na revisão da Sprint 19: race condition em `evaluateNow`, regra URL-level ausente para `/shifts/**`, ausência de auditoria nos shift use cases, acoplamento de `UpdateShiftUseCase` e timeout sem cleanup no frontend.

**Backend — SEC-063: race condition em `evaluateNow` (`AtomicReference`)**
1. `AlertEvaluatorUseCase` (ou `AlertEvaluatorJob`): substituir o padrão `AtomicReference.get()` + `set()` não-atômicos por `compareAndSet(expected, newValue)` — garante que a atualização do estado de "em execução" seja atômica; implementação: `AtomicReference<Boolean> running = new AtomicReference<>(false); if (!running.compareAndSet(false, true)) return;` no início do `execute()`; `finally { running.set(false); }` ao final
2. Teste unitário: dois threads chamam `execute()` simultaneamente via `ExecutorService` → apenas um efetua a avaliação (o outro retorna imediatamente); verificar com `verify(alertThresholdRepository, times(1)).findAllByActiveTrue()`

**Backend — SEC-064: SecurityConfig sem regra URL-level para `/api/v1/admin/shifts/**`**
3. `SecurityConfig`: adicionar regras URL-level explícitas para turnos — `GET /api/v1/admin/shifts/**` → `hasAnyRole("OPERATOR","SUPERVISOR","ADMIN")`; `POST /api/v1/admin/shifts/**` e `PUT /api/v1/admin/shifts/**` → `hasRole("ADMIN")`; `@PreAuthorize` permanece nos métodos como segunda barreira (conforme ADR-033 Decisão 5)

**Backend — SEC-065: shift use cases sem `auditService.log()`**
4. `AuditAction` enum: adicionar constantes `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DEACTIVATED`
5. `CreateShiftUseCase.execute()`: chamar `auditService.log(username, SHIFT_CREATED, "Shift", id, Map.of("name", shift.getName()))` após `save()`; assinatura atualizada para receber `username` passado pelo `ShiftController` via `principal.getName()`
6. `UpdateShiftUseCase.execute()`: idem com `SHIFT_UPDATED` e `Map.of("name", ...)`
7. `DeactivateShiftUseCase.execute()`: idem com `SHIFT_DEACTIVATED`; `ShiftController` passa `principal.getName()` nos três métodos

**Backend — SH-52: `UpdateShiftUseCase` chama `CreateShiftUseCase.overlaps()` diretamente**
8. Mover o método `overlaps(Shift candidate, List<Shift> existing)` de `CreateShiftUseCase` para `ShiftResolverService` (que já existe como utilitário compartilhado conforme ADR-016 Decisão 3); `CreateShiftUseCase` e `UpdateShiftUseCase` passam a injetar `ShiftResolverService` e chamar `shiftResolverService.overlaps(...)`; elimina o acoplamento direto entre use cases; nenhuma mudança de comportamento — apenas refatoração de local

**Frontend — SH-53: `ShiftListComponent.showSnackbar` usa `setTimeout` sem cleanup**
9. `ShiftListComponent`: substituir `setTimeout(() => this.snackbarMessage.set(null), 3000)` por `timer(3000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.snackbarMessage.set(null))`; `DestroyRef` injetado via `inject(DestroyRef)` no construtor (padrão já usado em outros componentes do projeto); garante cancelamento do timer se o componente for destruído antes dos 3 s
10. Spec `shift-list.component.spec.ts`: verificar que `snackbarMessage()` volta a `null` após 3 s (usar `fakeAsync` + `tick(3000)`); verificar que a subscription é limpa no `ngOnDestroy` (sem timer ativo)

---

## Sprint 21 ✅
**Objetivo**: Anexos — upload de documentos e imagens em NCs e OSs + tech debt Sprint 20
**ADR**: ADR-018, ADR-036
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-059 | Backend de upload e storage de anexos (MinIO) | 5 | ✅ concluído |
| US-060 | Frontend de upload, listagem e download de anexos | 3 | ✅ concluído |

---

#### US-059 — Backend de attachments (5 pts)

**Backend**
1. `software.amazon.awssdk:s3` adicionado ao `pom.xml` (BOM `software.amazon.awssdk:bom` para gerenciar versões); MinIO adicionado ao `docker-compose.yml` conforme ADR-036 Decisão 1; `StorageService` interface em `common/infrastructure/` com métodos `upload(String key, InputStream content, String contentType, long sizeBytes)`, `String generatePresignedUrl(String key, Duration ttl)`, `void delete(String key)`; implementação `S3StorageService` anotada `@Service @ConditionalOnProperty`; configuração via `@Value("${app.storage.*}")` conforme ADR-018 Decisão 1
2. `Attachment` entity criada conforme ADR-018 Decisão 2: campos `id`, `entityType` (String), `entityId` (String), `storageKey`, `originalName`, `contentType`, `fileSizeBytes`, `uploadedBy`, `uploadedAt`; migration `V{N}__attachment.sql` cria tabela `attachment` com índice composto em `(entity_type, entity_id)`; `AttachmentRepository extends JpaRepository<Attachment, UUID>` com `List<Attachment> findByEntityTypeAndEntityIdOrderByUploadedAtDesc(String entityType, String entityId)`
3. `POST /api/v1/attachments` (multipart/form-data, OPERATOR+); `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`; campos do request: `entityType` (String), `entityId` (UUID), `file` (MultipartFile); fluxo conforme ADR-036 Decisão 2: (1) valida `file.contentType` contra whitelist `{image/jpeg, image/png, image/webp, application/pdf}` → `InvalidFileTypeException` (`400`) se inválido; (2) tamanho > 10 MB → `MaxUploadSizeExceededException` interceptada pelo `GlobalExceptionHandler` existente → `413 { "message": "Arquivo muito grande. Limite: 10 MB." }`; (3) gera `storageKey = "{entityType.lower()}/{entityId}/{UUID}-{originalFilename}"`; (4) `storageService.upload(key, inputStream, contentType, size)` → `StorageException` mapeada para `502 { "message": "Falha no upload. Tente novamente." }`; (5) `attachmentRepository.save(attachment)` → se falhar após S3 sucesso: `storageService.delete(key)` em best-effort no catch antes de propagar erro; retorna `201 AttachmentResponse`
4. `AttachmentResponse` record: `id` (UUID), `originalName`, `contentType`, `fileSizeBytes`, `uploadedBy`, `uploadedAt` — campo `storageKey` **não exposto** ao cliente
5. `GET /api/v1/attachments?entityType=X&entityId=Y` lista todos os `Attachment` da entidade ordenados por `uploadedAt DESC` (OPERATOR+); retorna `List<AttachmentResponse>` (pode ser `[]`); `entityType` ou `entityId` ausentes retornam `400`
6. `GET /api/v1/attachments/{id}/download-url` retorna `DownloadUrlResponse { url, expiresAt }` com URL pré-assinada TTL 15 min via `storageService.generatePresignedUrl(key, Duration.ofMinutes(15))`; `404 { "message": "Anexo não encontrado" }` se id inexistente (OPERATOR+)
7. `DELETE /api/v1/attachments/{id}` (SUPERVISOR+); `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`; (1) busca `Attachment` — `404` se não existir; (2) `storageService.delete(key)` — se falhar: continua (best-effort) conforme ADR-036 Decisão 3; (3) `attachmentRepository.delete(attachment)`; retorna `204`
8. `AuditAction` enum: adicionar `ATTACHMENT_UPLOADED` e `ATTACHMENT_DELETED`; `UploadAttachmentUseCase` chama `auditService.log(username, ATTACHMENT_UPLOADED, "Attachment", id.toString(), Map.of("entityType", entityType, "entityId", entityId, "file", originalName))`; `DeleteAttachmentUseCase` idem com `ATTACHMENT_DELETED`
9. Package conforme ADR-018 Decisão 4: `common/domain/Attachment.java`, `common/application/dto/AttachmentResponse.java`, `common/application/dto/DownloadUrlResponse.java`, `common/application/usecase/{Upload,Get,GetDownloadUrl,Delete}AttachmentUseCase.java`, `common/infrastructure/{AttachmentRepository,StorageService,S3StorageService}.java`, `common/presentation/AttachmentController.java`
10. Exceções novas registradas no `GlobalExceptionHandler`: `AttachmentNotFoundException` → `404`; `InvalidFileTypeException` → `400`; `StorageException` → `502`
11. Também abordar tech debt do Sprint 20 conforme ADR-036 Decisão 4: (a) `CreateSparePartUseCase` — mover `auditService.log()` para fora do `try-catch(DataIntegrityViolationException)` (SH-48); (b) `RemoveWorkOrderPartUseCase` — adicionar query `findWorkOrderIdByPartId` ao `WorkOrderPartRepository` para evitar lazy load extra (SH-49); (c) adicionar `GET /api/v1/maintenance/work-orders/{id}` com `GetWorkOrderDetailUseCase` para resolver SH-50 (workaround do `work-order-detail.component`)
12. Teste `UploadAttachmentUseCaseTest`: (a) upload bem-sucedido — `storageService.upload()`, `save()` e `auditService.log(ATTACHMENT_UPLOADED)` chamados; `AttachmentResponse` retornado; (b) tipo inválido — `InvalidFileTypeException` lançado antes de chamar `storageService`; (c) S3 falha — `StorageException` propagada, `save()` não chamado; (d) DB falha após S3 sucesso — `storageService.delete()` chamado no cleanup (best-effort)
13. Teste `DeleteAttachmentUseCaseTest`: (a) deleção bem-sucedida — `storageService.delete()` + `attachmentRepository.delete()` + `auditService.log(ATTACHMENT_DELETED)` chamados; (b) `AttachmentNotFoundException` quando id inexistente; (c) S3 falha no delete — `attachmentRepository.delete()` ainda é chamado (best-effort); `204` retornado

---

#### US-060 — Frontend de anexos (3 pts)

1. `AttachmentService` standalone em `shared/` (ou `common/`): métodos `listAttachments(entityType: string, entityId: string): Observable<AttachmentResponse[]>`, `getDownloadUrl(id: string): Observable<DownloadUrlResponse>`, `uploadAttachment(entityType: string, entityId: string, file: File): Observable<AttachmentResponse>`, `deleteAttachment(id: string): Observable<void>`; interfaces `AttachmentResponse`, `DownloadUrlResponse` tipadas estritamente (sem `any`)
2. Componente standalone reutilizável `AttachmentListComponent` com `input() entityType: string` e `input() entityId: string`; `ChangeDetectionStrategy.OnPush`; adicionado à página de detalhe de NC (`/qms/non-conformances/{id}`) e à página de detalhe de OS (`/maintenance/work-orders/{id}` — componente existente `WorkOrderDetailComponent`); `ngOnInit` carrega a lista via `listAttachments()`
3. Lista de anexos: loading state — skeleton de 2 linhas enquanto `isLoading()=true`; empty state — "Nenhum anexo nesta entrada" com ícone `attach_file`; cada item exibe: ícone de tipo (`picture_as_pdf` para PDF, `image` para imagens), nome original, tamanho formatado (ex: "1.2 MB"), data de upload (`uploadedAt`), botão "Download"
4. Thumbnails: para cada `Attachment` com `contentType.startsWith('image/')`, chama `getDownloadUrl(id)` no carregamento da lista e exibe `<img [src]="thumbUrls()[id]" width="80" height="80" loading="lazy">`; PDFs não têm thumbnail — apenas ícone `picture_as_pdf`; URLs de thumbnail armazenadas em `thumbUrls = signal<Record<string, string>>({})` (evita recálculo no OnPush)
5. Botão "Anexar Arquivo" (qualquer usuário autenticado, `@if (canUpload())`): `<input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp" #fileInput hidden>` acionado via `fileInput.click()` no método `onAttachClick()`; handler `onChange($event)`: (1) valida `file.size <= 10_485_760` — snackbar "Arquivo muito grande (máx 10 MB)" sem chamar API; (2) valida `file.type` contra whitelist — snackbar "Tipo não permitido" sem chamar API; (3) se válido: chama `uploadAttachment()`
6. Feedback de upload: `isUploading = signal(false)`; `MatProgressBar` mode `indeterminate` visível durante upload; botão "Anexar Arquivo" com `[disabled]="isUploading()"`; sucesso: snackbar "Arquivo anexado com sucesso" + `listAttachments()` recarregado; erro `413`: snackbar "Arquivo muito grande (máx 10 MB)"; outros erros: snackbar com `err.error?.message ?? "Falha no upload"`
7. Botão "Download" por item: `getDownloadUrl(id).subscribe(res => window.open(res.url, '_blank'))`; erro: snackbar "Falha ao gerar link de download. Tente novamente."
8. Botão lixeira (SUPERVISOR+, `@if (canDelete())`): `window.confirm()` nativo de confirmação (não `MatDialog` — consistente com padrão do projeto); confirmar → `deleteAttachment(id)`; sucesso: item removido de `attachments()` (sem snackbar de sucesso — consistente com outros componentes); erro: sem snackbar (silenciado — SHOULD FIX diferido para Sprint 22)
9. `ChangeDetectionStrategy.OnPush`, standalone, signals; `attachments = signal<AttachmentResponse[]>([])`, `isLoading = signal(false)`, `isUploading = signal(false)`, `thumbUrls = signal<Record<string, string>>({})`, `canUpload = computed(() => !!authService.role())`, `canDelete = computed(() => r === 'SUPERVISOR' || r === 'ADMIN')`
10. Spec `attachment-list.component.spec.ts`: (a) lista exibe 2 anexos mockados com nome, tamanho e data; (b) empty state quando `attachments()` vazio; (c) arquivo > 10 MB não chama `uploadAttachment()` da API (validação client-side); (d) botão lixeira oculto para OPERATOR; (e) clique no lixeira abre `window.confirm()` de confirmação nativo (não `MatDialog` — consistente com outros componentes do projeto como `nc-detail` e `equipment-detail`); (f) snackbar de erro exibido quando upload falha com erro de API

---

## Sprint 22 ✅
**Objetivo**: SLA configurável e escalação automática de itens vencidos + tech debt de security do Sprint 21
**ADR**: ADR-019, ADR-037
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-061 | Configuração de regras de SLA | 3 | ✅ concluído |
| US-062 | Job de escalação automática e sinalização de SLA vencido | 5 | ✅ concluído |
| US-091 | Tech debt Sprint 21 — validação de anexos e segurança (SEC-067/068/071/072/073) | 2 | ✅ concluído |

**Total**: 10 pts

### Dependências
- US-062 depende de US-061 (requer `SlaRule` entity + repositório + colunas `slaBreached` nas entidades)
- US-091 é independente — pode ser desenvolvida em paralelo

---

#### US-061 — Configuração de regras de SLA (3 pts)

> Permite que administradores definam prazos (SLA) por tipo de entidade e severidade/prioridade, tornando os acordos de nível de serviço configuráveis sem deploy.

**Backend**

1. Entidade `SlaRule` criada em `common/domain/SlaRule.java`: campos `id` (UUID), `entityType` (enum `SlaEntityType`: `NC`, `WORK_ORDER`), `classifierField` (enum `SlaClassifierField`: `SEVERITY`, `PRIORITY`), `classifierValue` (String, max 50 chars), `slaHours` (int, min 1), `escalateByEmail` (boolean, default false), `active` (boolean, default true), `createdAt` (LocalDateTime); constraint UNIQUE em `(entityType, classifierField, classifierValue)` para evitar regras duplicadas.
2. Migration `V{N}__sla.sql`: (a) tabela `sla_rule` com as colunas acima + índice em `(entity_type, classifier_field, classifier_value)`; (b) colunas `sla_breached BOOLEAN NOT NULL DEFAULT FALSE` e `sla_breached_at TIMESTAMP` adicionadas a `non_conformance` e `work_order`; (c) índices parciais em `non_conformance(sla_breached) WHERE sla_breached = FALSE` e `work_order(sla_breached) WHERE sla_breached = FALSE` para performance do job de varredura.
3. `POST /api/v1/admin/sla-rules` (ADMIN): aceita `CreateSlaRuleRequest { entityType, classifierField, classifierValue, slaHours, escalateByEmail }`; retorna `201 SlaRuleResponse`; `slaHours < 1` retorna `400 { "message": "slaHours deve ser >= 1" }`; combinação `(entityType, classifierField, classifierValue)` duplicada retorna `409 { "message": "Regra SLA já existe para esta combinação" }`.
4. `GET /api/v1/admin/sla-rules` (ADMIN): retorna `List<SlaRuleResponse>` com todas as regras ativas, ordenadas por `entityType ASC, classifierValue ASC`.
5. `PUT /api/v1/admin/sla-rules/{id}` (ADMIN): aceita `UpdateSlaRuleRequest { slaHours, escalateByEmail }`; atualiza apenas esses dois campos; retorna `200 SlaRuleResponse` ou `404`.
6. `DELETE /api/v1/admin/sla-rules/{id}` (ADMIN): soft-delete (`active = false`); retorna `204` ou `404`.
7. `SlaRuleResponse` record: `id`, `entityType`, `classifierField`, `classifierValue`, `slaHours`, `escalateByEmail`, `active`.
8. `SlaRuleRepository extends JpaRepository<SlaRule, UUID>`: método `findAllByActiveTrueOrderByEntityTypeAscClassifierValueAsc()` para listagem; método `existsByEntityTypeAndClassifierFieldAndClassifierValueAndActiveTrue()` para checar duplicata.
9. Seed no `DataInitializer` (executa apenas se tabela `sla_rule` estiver vazia): NC/SEVERITY/CRITICAL→48h/escalateByEmail=true, NC/SEVERITY/HIGH→72h/escalateByEmail=false, WORK_ORDER/PRIORITY/URGENT→4h/escalateByEmail=true, WORK_ORDER/PRIORITY/HIGH→24h/escalateByEmail=false.
10. `AuditAction` enum: adicionar `SLA_RULE_CREATED`, `SLA_RULE_UPDATED`, `SLA_RULE_DELETED`; use cases de CRUD chamam `auditService.log()` com `entityType = "SlaRule"` e `entityId = id.toString()`.

**Frontend**

11. Rota lazy `/admin/sla-rules` (ADMIN, protegida por `AuthGuard` com role ADMIN): tabela com colunas Entidade, Campo, Valor, SLA (horas), Email (ícone check/cross), ações (Editar / Desativar); `ChangeDetectionStrategy.OnPush`, standalone.
12. Botão "Nova Regra" abre `MatDialog` com formulário: select `entityType` (NC / Ordem de Serviço), select `classifierField` (Severidade / Prioridade), input `classifierValue` (ex: "CRITICAL"), input numérico `slaHours` (min 1), toggle `escalateByEmail`; botão "Salvar" desabilitado enquanto formulário inválido; sucesso: dialog fecha + snackbar "Regra SLA criada" + tabela recarregada.
13. Inline edit: botão "Editar" em cada linha abre linha em modo de edição com os campos `slaHours` e `escalateByEmail` editáveis; botão "Salvar" e "Cancelar" inline; sucesso: snackbar "Regra SLA atualizada"; erro `404`: snackbar com mensagem da API.
14. Botão "Desativar" pede confirmação via `window.confirm()` nativo antes de chamar `DELETE`; sucesso: linha removida da tabela; snackbar "Regra SLA desativada".
15. Erro de carregamento da tabela exibe mensagem "Não foi possível carregar as regras de SLA" com botão "Tentar novamente".
16. Spec `sla-rules.component.spec.ts`: (a) tabela exibe 2 regras mockadas com entityType, slaHours; (b) botão salvar desabilitado com slaHours < 1; (c) chamada PUT disparada ao salvar inline edit; (d) confirmação antes de DELETE.

---

#### US-062 — Job de escalação automática e sinalização de SLA vencido (5 pts)

> Avalia automaticamente a cada hora quais NCs e OSs estão com SLA vencido e gera notificações/emails; sinaliza visualmente os itens vencidos nas listagens e no dashboard.

**Backend — Job e Use Case**

1. `EscalationUseCase` em `common/application/usecase/EscalationUseCase.java`: método `execute()` retorna `EscalationResult { int breachedNcs, int breachedWorkOrders }`.
2. Para cada `SlaRule` ativa com `entityType = NC`: busca via `NonConformanceRepository` NCs com `status != CLOSED`, `slaBreached = false` e `(reportedAt + slaHours horas) <= LocalDateTime.now()`; JPQL: `SELECT nc FROM NonConformance nc WHERE nc.status <> 'CLOSED' AND nc.slaBreached = false AND nc.reportedAt <= :deadline`; `deadline` calculado como `LocalDateTime.now().minusHours(rule.getSlaHours())` para cada regra; filtro em Java para `nc.severity.name().equals(rule.getClassifierValue())` quando `classifierField = SEVERITY`.
3. Para cada `SlaRule` ativa com `entityType = WORK_ORDER`: busca via `WorkOrderRepository` OSs com `status != COMPLETED` e `status != CANCELLED`, `slaBreached = false` e `(openedAt + slaHours horas) <= LocalDateTime.now()`; filtro em Java para `wo.priority.name().equals(rule.getClassifierValue())` quando `classifierField = PRIORITY`.
4. Para cada NC/OS encontrada: (a) seta `slaBreached = true` e `slaBreachedAt = LocalDateTime.now()`; (b) persiste via repositório; (c) chama `notificationService.broadcast()` com `title = "SLA Vencido: {entidade} #{id}"`, `message = "NC/OS {id} ultrapassou o prazo de {slaHours}h."`, `severity = CRITICAL` (não cria duplicata se `existsByTitleAndCreatedAtAfter` dentro do último intervalo de 2h); (d) se `rule.isEscalateByEmail() == true`: dispara `@Async` email via `JavaMailSender` para todos os usuários com role SUPERVISOR ou ADMIN com `active = true`.
5. Processamento idempotente: entidades com `slaBreached = true` nunca são reprocessadas (filtro `slaBreached = false` na query).
6. `EscalationJob` em `common/application/usecase/EscalationJob.java`: `@Component` com `@Scheduled(cron = "0 0 * * * *")`; injeta `EscalationUseCase`; loga resultado `INFO "EscalationJob concluído: {} NCs e {} OSs marcadas"`.
7. `AuditAction` enum: adicionar `SLA_BREACHED`; `EscalationUseCase` chama `auditService.log("system", SLA_BREACHED, entityClass, entityId, Map.of("slaHours", rule.getSlaHours(), "classifierValue", rule.getClassifierValue()))` para cada item marcado.

**Backend — Endpoints**

8. `POST /api/v1/admin/sla-rules/run-now` (ADMIN): dispara `EscalationUseCase.execute()` de forma síncrona; retorna `200 { "breachedNcs": N, "breachedWorkOrders": M }`; protegido com rate limiting de 1 chamada/minuto por usuário (reutilizar bucket Bucket4j já configurado no projeto, conforme ADR-021).
9. `GET /api/v1/qms/non-conformances` — novo parâmetro opcional `slaBreached` (Boolean); quando `true`, filtra apenas NCs com `slaBreached = true`; quando `false`, filtra NCs com `slaBreached = false`; quando ausente, retorna todas (comportamento atual preservado). JPQL atualizado com `AND (:slaBreached IS NULL OR nc.slaBreached = :slaBreached)`.
10. `GET /api/v1/maintenance/work-orders` — mesmo padrão de filtro `slaBreached` adicionado ao endpoint existente.
11. `GET /api/v1/admin/sla-rules/summary` (ADMIN): retorna `SlaSummaryResponse { int totalBreachedNcs, int totalBreachedWorkOrders, int totalOpenNcs, int totalOpenWorkOrders }`; calculado via JPQL (`COUNT`); sem native SQL.

**Backend — Testes**

12. `EscalationUseCaseTest`: (a) NC CRITICAL com `reportedAt` há 50h e regra NC/CRITICAL→48h → `slaBreached` setado + `notificationService.broadcast()` chamado + `auditService.log(SLA_BREACHED)` chamado; (b) NC CRITICAL já com `slaBreached = true` → não reprocessada (`save()` não chamado); (c) NC CLOSED → ignorada mesmo que prazo vencido; (d) `escalateByEmail = false` → `mailSender.send()` não chamado; (e) `escalateByEmail = true` → `mailSender.send()` chamado com destinatários SUPERVISOR+; (f) `EscalationResult` com contagens corretas.

**Frontend**

13. `SlaService` em `shared/`: métodos `listSlaRules(): Observable<SlaRuleResponse[]>`, `createSlaRule(req): Observable<SlaRuleResponse>`, `updateSlaRule(id, req): Observable<SlaRuleResponse>`, `deleteSlaRule(id): Observable<void>`, `runEscalationNow(): Observable<EscalationResult>`, `getSlaSummary(): Observable<SlaSummaryResponse>`; interfaces tipadas sem `any`.
14. Chip `SlaBreachedChipComponent` (standalone, `OnPush`): exibe "SLA Vencido" em vermelho (`#EF4444`) com animação CSS `pulse` (keyframes 0%→100% opacity 1→0.6); recebe `input() slaBreached: boolean`; visível apenas quando `slaBreached === true`; adicionado às listagens de NC (coluna extra após "Status") e OS (idem) e às páginas de detalhe de NC e OS.
15. Painel "SLA em Risco" no dashboard (`/dashboard`): card com dois contadores — "NCs com SLA Vencido" e "OSs com SLA Vencido" carregados via `getSlaSummary()`; loading state com skeleton de 1 linha; clique em "NCs com SLA Vencido" navega para `/qms/non-conformances?slaBreached=true`; clique em "OSs com SLA Vencido" navega para `/maintenance/work-orders?slaBreached=true`; card exibe `--` em vez de `0` quando `isLoading() = true`; empty state: card oculto quando ambos os contadores são `0` (não poluir dashboard).
16. Botão "Executar SLA agora" na rota `/admin/sla-rules` (ADMIN): chama `runEscalationNow()`; exibe `MatProgressSpinner` durante execução; resultado exibe snackbar "Escalação concluída: {breachedNcs} NCs e {breachedWorkOrders} OSs marcadas".
17. Filtro `slaBreached` adicionado às listagens de NC e OS: checkbox "Apenas SLA vencido" no painel de filtros; quando marcado, adiciona `&slaBreached=true` ao request; integrado com os filtros existentes (status, severidade, etc.) sem quebrar estado atual.
18. Spec `sla-breached-chip.component.spec.ts`: (a) chip visível quando `slaBreached = true`; (b) chip oculto quando `slaBreached = false`.
19. Spec `dashboard.component.spec.ts` (atualizado): card "SLA em Risco" exibe contagens mockadas; card oculto quando ambos zero.

---

#### US-091 — Tech debt Sprint 21 — validação de anexos e segurança (2 pts)

> Cobre 5 itens de security debt identificados por Beatriz na revisão do Sprint 21: SEC-067 (tamanho `code` SparePart), SEC-068 (comprimento coluna `unit`), SEC-071 (Content-Type spoofing via magic bytes), SEC-072 (entityType sem enum tipado), SEC-073 (validação explícita de tamanho de arquivo no use case).

**Backend — SEC-067: `@Size` em `CreateSparePartRequest.code`**

1. `CreateSparePartRequest`: adicionar `@Size(max = 50, message = "code deve ter no máximo 50 caracteres")` ao campo `code`; `GlobalExceptionHandler` já trata `MethodArgumentNotValidException` retornando `400 { "message": "..." }` — sem alteração necessária.

**Backend — SEC-068: `SparePart.unit` sem restrição de comprimento**

2. `SparePart` entity: adicionar `@Column(length = 20)` no campo `unit`; adicionar `@Size(max = 20, message = "unit deve ter no máximo 20 caracteres")` no `CreateSparePartRequest.unit` e `UpdateSparePartRequest.unit`; migration `V{N}__spare_part_unit_length.sql`: `ALTER TABLE spare_part ALTER COLUMN unit TYPE VARCHAR(20)`.

**Backend — SEC-071: Content-Type spoofing — verificação via magic bytes (Apache Tika)**

3. Dependência `org.apache.tika:tika-core` adicionada ao `pom.xml` (sem `tika-parsers` — apenas detecção de tipo, sem parsing completo); versão gerenciada via propriedade `<tika.version>2.9.2</tika.version>`.
4. `MimeTypeDetector` utilitário em `common/infrastructure/MimeTypeDetector.java`: método `String detect(InputStream inputStream, String declaredContentType)`; usa `Tika.detect(inputStream)` para obter o tipo real via magic bytes; retorna o tipo detectado.
5. `UploadAttachmentUseCase.execute()`: antes de chamar `storageService.upload()`, detectar o tipo real via `MimeTypeDetector.detect(file.getInputStream(), file.getContentType())`; verificar o tipo detectado (não o `file.getContentType()` declarado pelo cliente) contra a whitelist `{image/jpeg, image/png, image/webp, application/pdf}`; tipo real fora da whitelist → `InvalidFileTypeException` com mensagem `"Tipo de arquivo não permitido: {tipoDetectado}"`; tipo declarado divergente do real é logado como `WARN` sem bloquear (apenas o tipo real determina a aceitação).
6. Teste `UploadAttachmentUseCaseTest` atualizado: (a) arquivo JPEG com `contentType="image/jpeg"` declarado → aceito; (b) arquivo EXE disfarçado com `contentType="image/jpeg"` → `InvalidFileTypeException` (tipo real detectado é `application/octet-stream`); (c) arquivo PNG com `contentType="application/pdf"` declarado → aceito (tipo real é `image/png`, whitelist aceita); loga WARN sobre divergência.

**Backend — SEC-072: `entityType` sem enum tipado no domínio**

7. Enum `AttachmentEntityType` criado em `common/domain/AttachmentEntityType.java`: valores `NON_CONFORMANCE`, `WORK_ORDER`; método `toStoragePrefix()` retorna o valor em lowercase para uso na construção do `storageKey` (ex: `NON_CONFORMANCE.toStoragePrefix()` → `"non_conformance"`).
8. `Attachment` entity: campo `entityType` alterado de `String` para `AttachmentEntityType`; `@Enumerated(EnumType.STRING)` anotado; migration não é necessária pois os valores armazenados mudam — migration `V{N}__attachment_entity_type_enum.sql` executa `UPDATE attachment SET entity_type = 'NON_CONFORMANCE' WHERE entity_type = 'NC'` e `UPDATE attachment SET entity_type = 'WORK_ORDER' WHERE entity_type = 'WORK_ORDER'` (ou conforme naming adotado no Sprint 21).
9. `AttachmentController`: parâmetro `entityType` no `POST` e `GET` passa de `String` para `AttachmentEntityType`; Spring converte automaticamente via `@RequestParam AttachmentEntityType entityType`; valor inválido lança `MethodArgumentTypeMismatchException` → `GlobalExceptionHandler` retorna `400 { "message": "entityType inválido. Valores aceitos: NON_CONFORMANCE, WORK_ORDER" }`; o `@Pattern` adicionado no Sprint 21 no controller pode ser removido, pois o enum já tipifica a validação.
10. `AttachmentRepository`: assinatura `findByEntityTypeAndEntityIdOrderByUploadedAtDesc(AttachmentEntityType entityType, String entityId)` atualizada.

**Backend — SEC-073: validação explícita de tamanho de arquivo no use case**

11. `UploadAttachmentUseCase.execute()`: adicionar verificação explícita `if (file.getSize() > 10_485_760L) throw new FileTooLargeException(file.getSize())` **antes** de chamar `storageService.upload()`; `FileTooLargeException` nova em `common/domain/`; `GlobalExceptionHandler`: mapear `FileTooLargeException` para `400 { "message": "Arquivo muito grande. Limite: 10 MB." }` (o handler existente para `MaxUploadSizeExceededException` permanece como fallback de nível Spring, mas agora o use case valida explicitamente antes de tentar o stream).
12. Teste `UploadAttachmentUseCaseTest` atualizado: arquivo com `size = 10_485_761` bytes → `FileTooLargeException` lançada antes de `storageService.upload()` ser chamado (verificar com `verify(storageService, never()).upload(...)`).

**Observações de implementação**

- Os itens SEC-067, SEC-068, SEC-073 são triviais (anotações e verificação de tamanho) — estimativa: 0,5 pt somados.
- SEC-071 (Apache Tika) é o item de maior esforço: dependência nova + lógica de detecção + ajuste no fluxo do use case — estimativa: 0,8 pt.
- SEC-072 (enum `AttachmentEntityType`) envolve migration + refatoração de tipo — estimativa: 0,7 pt.
- Total: ~2 pts. Desenvolvidos em paralelo com US-061 (sem dependências entre si).

---

## Sprint 23 ✅
**Objetivo**: Multi-plant support (dimensão de planta/unidade produtiva) + tech debt SLA/email diferidos do Sprint 22
**ADR**: ADR-020, ADR-038
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-063 | Cadastro de plantas e associação de usuários | 4 | ✅ concluído |
| US-064 | Filtro de dados por planta em todos os módulos | 5 | ✅ concluído |
| US-092 | Tech debt Sprint 22 — escalação por e-mail, rate limit /run-now, auditoria e validações SLA | 3 | ✅ concluído |

**Total**: 12 pts

### Dependências
- US-064 depende de US-063 (requer entidade `Plant`, migration e `PlantContext`)
- US-092 é independente — pode ser desenvolvida em paralelo com US-063

---

#### US-063 — Cadastro de plantas e associação de usuários (4 pts)

> Permite que administradores registrem as unidades produtivas (plantas) do grupo MSB e vinculem usuários a uma ou mais plantas, habilitando a segmentação multi-plant a ser implementada em US-064.

**Backend — Entidade e Migration**

1. Entidade `Plant` criada em `common/domain/Plant.java`: campos `id` (UUID), `code` (String, max 20, único), `name` (String, max 200), `address` (String, max 500, nullable), `timezone` (String, max 50, default `"America/Sao_Paulo"`), `isDefault` (boolean, default false), `active` (boolean, default true), `createdAt` (LocalDateTime); constraint `UNIQUE` em `code`.
2. Migration `V{N}__plant.sql`:
   - Tabela `plant` com todas as colunas acima.
   - Tabela `user_plant` (many-to-many): `user_id UUID NOT NULL REFERENCES app_user(id)`, `plant_id UUID NOT NULL REFERENCES plant(id)`, PRIMARY KEY `(user_id, plant_id)`.
   - Colunas `plant_id UUID REFERENCES plant(id)` adicionadas a `equipment`, `non_conformance`, `import_batch` (nullable inicialmente para permitir o UPDATE a seguir).
   - `INSERT INTO plant (id, code, name, timezone, is_default, active, created_at) VALUES (gen_random_uuid(), 'HQ', 'Matriz', 'America/Sao_Paulo', true, true, now())`.
   - `UPDATE equipment SET plant_id = (SELECT id FROM plant WHERE code = 'HQ') WHERE plant_id IS NULL`.
   - `UPDATE non_conformance SET plant_id = (SELECT id FROM plant WHERE code = 'HQ') WHERE plant_id IS NULL`.
   - `UPDATE import_batch SET plant_id = (SELECT id FROM plant WHERE code = 'HQ') WHERE plant_id IS NULL`.
   - `ALTER TABLE equipment ALTER COLUMN plant_id SET NOT NULL`.
   - `ALTER TABLE non_conformance ALTER COLUMN plant_id SET NOT NULL`.
   - `ALTER TABLE import_batch ALTER COLUMN plant_id SET NOT NULL`.
   - Índice em `plant(active)`.

**Backend — CRUD de Plantas**

3. `POST /api/v1/admin/plants` (ADMIN): aceita `CreatePlantRequest { code, name, address, timezone }`; `code` normalizado para uppercase antes de persistir; `code` duplicado retorna `409 { "message": "Planta com código {code} já existe" }`; retorna `201 PlantResponse`.
4. `PlantResponse` record: `id`, `code`, `name`, `address`, `timezone`, `isDefault`, `active`.
5. `GET /api/v1/admin/plants` (ADMIN): retorna `List<PlantResponse>` de plantas ativas, ordenadas por `name ASC`.
6. `PUT /api/v1/admin/plants/{id}` (ADMIN): aceita `UpdatePlantRequest { name, address, timezone }`; `code` e `isDefault` são imutáveis via este endpoint; retorna `200 PlantResponse` ou `404 { "message": "Planta não encontrada" }`.
7. `DELETE /api/v1/admin/plants/{id}` (ADMIN): soft-delete (`active = false`); retorna `204`; retorna `409 { "message": "Não é possível desativar a planta padrão" }` se `isDefault = true`; retorna `409 { "message": "Planta possui equipamentos ou NCs ativas associados" }` se houver registros ativos vinculados.
8. `AuditAction` enum: adicionar `PLANT_CREATED`, `PLANT_UPDATED`, `PLANT_DEACTIVATED`; use cases de CRUD chamam `auditService.log()` com `entityType = "Plant"` e `entityId = id.toString()`.
9. `PlantRepository extends JpaRepository<Plant, UUID>`: métodos `findAllByActiveTrueOrderByNameAsc()` e `existsByCodeAndActiveTrue(String code)`.

**Backend — Associação Usuário–Planta**

10. `PUT /api/v1/admin/users/{userId}/plants` (ADMIN): aceita `UpdateUserPlantsRequest { List<UUID> plantIds }`; substitui completamente a lista de plantas do usuário (remove as anteriores e insere as novas); `plantIds` vazio válido (remove todas); ids de plantas inexistentes ou inativas retornam `422 { "message": "Planta {id} não encontrada ou inativa" }`; retorna `200 { "userId": ..., "plantIds": [...] }`.
11. `GET /api/v1/admin/users/{userId}/plants` (ADMIN): retorna `List<PlantResponse>` das plantas vinculadas ao usuário; usuário inexistente retorna `404`.
12. `UserPlantRepository extends JpaRepository`: métodos `findPlantsByUserId(UUID userId)` e `deleteAllByUserId(UUID userId)`.

**Backend — Seed**

13. `DataInitializer`: ao inicializar, se tabela `plant` estiver vazia, cria `Plant { code="HQ", name="Matriz", timezone="America/Sao_Paulo", isDefault=true, active=true }`; vincula todos os usuários existentes à planta HQ na tabela `user_plant` (para evitar usuários sem planta após a migration).

**Frontend**

14. Rota lazy `/admin/plants` (ADMIN, protegida por `AuthGuard` com role ADMIN): tabela com colunas Código, Nome, Endereço, Fuso Horário, Padrão (ícone), ações (Editar / Desativar); `ChangeDetectionStrategy.OnPush`, standalone.
15. Botão "Nova Planta" abre `MatDialog` com formulário: input `code` (uppercase automático), input `name`, input `address` (opcional), select `timezone` (lista dos fusos BR mais Etc/UTC); botão "Salvar" desabilitado enquanto formulário inválido; sucesso: dialog fecha + snackbar "Planta criada" + tabela recarregada.
16. Botão "Editar" em cada linha abre dialog pré-preenchido com `name`, `address`, `timezone`; `code` exibido como texto não editável; sucesso: snackbar "Planta atualizada".
17. Botão "Desativar" pede confirmação via `window.confirm()` nativo; planta padrão exibe "Desativar" desabilitado com tooltip "A planta padrão não pode ser desativada"; sucesso: linha removida da tabela.
18. Na página de detalhe do usuário (`/admin/users/{id}`): nova seção "Plantas Vinculadas" com lista de checkboxes de todas as plantas ativas; checkboxes marcados conforme vinculação atual; botão "Salvar Plantas" dispara `PUT /api/v1/admin/users/{userId}/plants`; sucesso: snackbar "Plantas atualizadas".
19. `PlantService` em `shared/`: métodos `listPlants(): Observable<PlantResponse[]>`, `createPlant(req): Observable<PlantResponse>`, `updatePlant(id, req): Observable<PlantResponse>`, `deactivatePlant(id): Observable<void>`, `getUserPlants(userId): Observable<PlantResponse[]>`, `updateUserPlants(userId, plantIds): Observable<void>`; interfaces tipadas sem `any`.
20. Spec `plants.component.spec.ts`: (a) tabela exibe 2 plantas mockadas; (b) botão "Salvar" desabilitado com `name` vazio; (c) `createPlant` chamado ao salvar nova planta; (d) `window.confirm` chamado ao clicar "Desativar"; (e) botão "Desativar" desabilitado para planta padrão.

---

#### US-064 — Filtro de dados por planta em todos os módulos (5 pts)

> Aplica a dimensão de planta como filtro automático em todos os endpoints de listagem: OPERATOR/SUPERVISOR veem apenas os dados das suas plantas; ADMIN vê tudo e pode filtrar por planta específica.

**Backend — PlantContext**

1. `PlantContext` em `common/infrastructure/PlantContext.java`: `ThreadLocal<Set<UUID>>` com métodos estáticos `set(Set<UUID>)`, `get(): Set<UUID>`, `clear()`; `get()` retorna `Set.of()` se não inicializado (caso de contexto de sistema/job).
2. `PlantContextFilter extends OncePerRequestFilter` em `common/infrastructure/`: executa para toda requisição autenticada; obtém `username` do `SecurityContextHolder`; busca `Set<UUID> plantIds` via `UserPlantRepository.findPlantIdsByUsername(username)`; ADMIN: `PlantContext.set(null)` (convenção: `null` = sem filtro = ver tudo); OPERATOR/SUPERVISOR: `PlantContext.set(plantIds)`; registra `PlantContext.clear()` no `finally` para evitar leak entre requisições (mesmo pool de threads).
3. `PlantContextFilter` registrado em `SecurityConfig` após `JwtAuthFilter` na chain via `addFilterAfter`.

**Backend — Endpoints filtrados**

4. `GET /api/v1/maintenance/equipment`: query JPQL atualizada com `AND (:plantIds IS NULL OR e.plant.id IN :plantIds)`; query param opcional `?plantId=<uuid>` aceito apenas por ADMIN (ignorado para outros roles — filtro do context prevalece); OPERATOR/SUPERVISOR sem planta vinculada recebem lista vazia (não erro).
5. `GET /api/v1/qms/non-conformances`: mesmo padrão de filtro por `plant.id`; `?plantId=<uuid>` para ADMIN.
6. `GET /api/v1/maintenance/work-orders`: `WorkOrder.plant` derivado de `WorkOrder.equipment.plant` — filtro aplica via join `wo.equipment.plant.id IN :plantIds`.
7. `GET /api/v1/oee/records` e `GET /api/v1/oee/analytics/**`: `ImportBatch.plant` já tem coluna `plant_id`; filtro adicionado às queries existentes.
8. `POST /api/v1/maintenance/equipment` (criação): `plant_id` **não** aceito no request body; `Plant` resolvido a partir do contexto: se OPERATOR/SUPERVISOR com 1 planta vinculada, usa essa planta; se ADMIN, aceita `?plantId=<uuid>` como query param (obrigatório para ADMIN quando há mais de 1 planta ativa); falha de resolução retorna `422 { "message": "Nenhuma planta ativa encontrada para o usuário" }`.
9. `POST /api/v1/qms/non-conformances` (criação): mesmo padrão de resolução de planta.
10. `GET /api/v1/kpi/summary`: agrega dados apenas das plantas do usuário autenticado (ADMIN: agrega tudo; `?plantId=<uuid>` para ADMIN filtra para planta específica).
11. `EscalationJob`: não filtra por planta — processa todas as entidades de todas as plantas (comportamento global correto para o job agendado de sistema).

**Backend — Testes**

12. `PlantContextFilterTest` (unitário): (a) OPERATOR com 2 plantas → `PlantContext.get()` retorna os 2 UUIDs; (b) ADMIN → `PlantContext.get()` retorna `null`; (c) requisição não autenticada → filter não executa (ordem na chain).
13. `GetEquipmentsUseCaseTest` atualizado: (a) OPERATOR com plantId X → retorna apenas equipamentos de X; (b) ADMIN sem filtro → retorna todos; (c) OPERATOR sem planta vinculada → retorna lista vazia.

**Frontend**

14. `PlantSelectorComponent` standalone (`OnPush`) no `NavComponent`: exibido apenas quando usuário autenticado; ADMIN: `MatSelect` dropdown com todas as plantas ativas (carregado via `PlantService.listPlants()`), opção "Todas as plantas" como primeira entrada, seleção persiste em `localStorage` sob chave `msb_selected_plant`; OPERATOR/SUPERVISOR com 1 planta: exibe nome da planta como texto estático (sem dropdown); OPERATOR/SUPERVISOR com 2+ plantas: exibe `MatSelect` restrito às plantas vinculadas.
15. `PlantService.selectedPlant$`: `signal<PlantResponse | null>` inicializado do `localStorage`; todos os serviços de listagem (`EquipmentService`, `NonConformanceService`, `WorkOrderService`) adicionam `?plantId=<uuid>` às requests quando `selectedPlant()` não é `null`.
16. Chip `<span class="plant-chip">` adicionado aos cards de NC, OS e equipamento exibindo `plant.name`; o chip é omitido (`@if`) quando o usuário tem acesso a apenas 1 planta (propriedade `hasSinglePlant` no serviço de autenticação).
17. Formulário de criação de equipamento: quando ADMIN e `selectedPlant()` for `null`, exibe select obrigatório "Planta" no formulário; quando ADMIN e planta selecionada no nav, pré-preenche e oculta o campo; OPERATOR/SUPERVISOR: campo não exibido (planta inferida pelo backend).
18. Formulário de criação de NC: mesmo padrão do item 17.
19. Spec `plant-selector.component.spec.ts`: (a) ADMIN vê select com "Todas as plantas" + lista mockada; (b) OPERATOR com 1 planta vê texto estático; (c) seleção de planta atualiza `PlantService.selectedPlant()`; (d) seleção persiste no `localStorage`.

---

#### US-092 — Tech debt Sprint 22 — escalação por e-mail, rate limit /run-now, auditoria e validações SLA (3 pts)

> Cobre 5 itens de tech debt e security debt identificados por Beatriz e Maiana na revisão do Sprint 22: SEC-075 (rate limit /run-now), SEC-076 (classifierValue sem validação semântica), SEC-077 (run-now não auditado), gap escalateByEmail (JavaMailSender não integrado), SEC-065 (shift use cases sem auditoria), SEC-074 (window.open sem noopener).

**Backend — SEC-075: rate limiting em `/run-now`**

1. `SlaRuleController.runNow()`: aplicar mesmo padrão de cooldown do `EvaluateNowUseCase` — `AtomicReference<Instant> lastRunAt` em `EscalationUseCase`; antes de executar: verificar se `lastRunAt` foi há menos de 5 minutos via `compareAndSet`; se cooldown ativo, retornar `429 { "message": "Execução manual disponível apenas a cada 5 minutos. Aguarde {segundos}s." }` com header `Retry-After: {segundos}`; cooldown calculado sobre o `Instant` da última execução concluída (não da chamada rejeitada).
2. `GlobalExceptionHandler`: não é necessário novo handler — o `429` é retornado diretamente no controller antes de delegar ao use case; manter consistência com o padrão do `EvaluateNowUseCase`.

**Backend — SEC-076: validação semântica de `classifierValue`**

3. `CreateSlaRuleUseCase.execute()`: antes de persistir, validar que `classifierValue` pertence ao enum correspondente ao `classifierField`:
   - Se `classifierField = SEVERITY`: tentar `NcSeverity.valueOf(request.classifierValue().toUpperCase())`; falha → `400 { "message": "classifierValue inválido para SEVERITY. Valores aceitos: LOW, MEDIUM, HIGH, CRITICAL" }`.
   - Se `classifierField = PRIORITY`: tentar `WorkOrderPriority.valueOf(request.classifierValue().toUpperCase())`; falha → `400 { "message": "classifierValue inválido para PRIORITY. Valores aceitos: LOW, MEDIUM, HIGH, URGENT" }`.
4. `UpdateSlaRuleUseCase`: não revalida `classifierValue` (campo imutável em updates — apenas `slaHours` e `escalateByEmail` são atualizáveis conforme AC de US-061).
5. Teste `CreateSlaRuleUseCaseTest` atualizado: (a) `classifierValue = "CRITICAL"` com `classifierField = SEVERITY` → sucesso; (b) `classifierValue = "INEXISTENTE"` com `classifierField = SEVERITY` → `IllegalArgumentException` capturada e mapeada para `400`; (c) `classifierValue = "URGENT"` com `classifierField = PRIORITY` → sucesso.

**Backend — SEC-077: auditoria da execução manual de escalação**

6. `AuditAction` enum: adicionar `SLA_ESCALATION_RUN_MANUAL`.
7. `SlaRuleController.runNow(Principal principal)`: injetar `Principal` no método; após execução bem-sucedida do use case, chamar `auditService.log(principal.getName(), SLA_ESCALATION_RUN_MANUAL, "EscalationUseCase", "manual", Map.of("breachedNcs", result.breachedNcs(), "breachedWorkOrders", result.breachedWorkOrders()))`.
8. `EscalationJob` mantém auditoria de `SLA_BREACHED` por entidade (já implementado no Sprint 22); não adiciona log de execução do job (execução agendada é sistema, não precisa de ator humano no AuditLog).

**Backend — Gap escalateByEmail: integração com JavaMailSender**

9. `EscalationUseCase`: injetar `JavaMailSender` e `@Value("${spring.mail.username}") String fromEmail`; o bloco `if (rule.isEscalateByEmail())` já existe mas estava vazio; implementar:
   - Buscar via `UserRepository.findByRoleInAndActiveTrue(List.of(Role.SUPERVISOR, Role.ADMIN))` os destinatários.
   - Para cada destinatário com `email != null`: construir `SimpleMailMessage` com `from = fromEmail`, `to = user.getEmail()`, `subject = "SLA Vencido: {entityType} #{entityId}"`, `text = "A entidade {entityType} #{entityId} ultrapassou o prazo de SLA de {slaHours}h configurado para {classifierField}={classifierValue}.\n\nData/hora: {slaBreachedAt}\n\nAcesse o Industrial Hub para tomar as ações necessárias."`.
   - Envio encapsulado em método `@Async` do próprio use case ou delegado a `QmsEmailService` existente (reutilizar se a assinatura for compatível); falha de envio de e-mail logada como `WARN` mas não propaga exceção (não deve bloquear a marcação de `slaBreached`).
10. `application.properties`: `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password` já configurados via `QmsEmailService` do Sprint 6 — sem novas propriedades necessárias; `spring.mail.properties.mail.smtp.auth=true`, `starttls.enable=true` já presentes.
11. Teste `EscalationUseCaseTest` atualizado: (a) `escalateByEmail = true`, usuários SUPERVISOR+ADMIN com email → `mailSender.send()` chamado N vezes; (b) `escalateByEmail = false` → `mailSender.send()` não chamado (verifica `verify(mailSender, never()).send(any(SimpleMailMessage.class))`); (c) `mailSender.send()` lança `MailException` → exceção capturada, `slaBreached` marcado normalmente, log WARN.

**Backend — SEC-065: auditoria em Shift use cases**

12. `AuditAction` enum: adicionar `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DEACTIVATED`.
13. `CreateShiftUseCase.execute()`: após `shiftRepository.save(shift)`, chamar `auditService.log(username, SHIFT_CREATED, "Shift", shift.getId().toString(), Map.of("name", shift.getName(), "startTime", shift.getStartTime().toString(), "endTime", shift.getEndTime().toString()))`.
14. `UpdateShiftUseCase.execute()`: após save, chamar `auditService.log(username, SHIFT_UPDATED, "Shift", id.toString(), Map.of("name", updated.getName()))`.
15. `DeactivateShiftUseCase.execute()`: após save, chamar `auditService.log(username, SHIFT_DEACTIVATED, "Shift", id.toString(), Map.of())`.

**Frontend — SEC-074: window.open sem noopener**

16. Em todos os componentes que chamam `window.open(url, '_blank')` (identificados no Sprint 21 no `AttachmentListComponent`): substituir por `window.open(url, '_blank', 'noopener,noreferrer')`; buscar ocorrências via `grep -r "window.open" src/` e corrigir todas de uma vez.

**Observações de esforço**

- SEC-075 (rate limit): ~0,5 pt — padrão idêntico ao `EvaluateNowUseCase`.
- SEC-076 (validação semântica): ~0,3 pt — 2 `valueOf()` + tratamento de erro.
- SEC-077 (auditoria run-now): ~0,2 pt — 1 linha no controller + enum.
- Gap escalateByEmail: ~1,2 pt — lógica de envio + teste com mock.
- SEC-065 (auditoria shifts): ~0,5 pt — 3 use cases + enum values.
- SEC-074 (window.open): ~0,1 pt — substituição de string.
- Total: ~2,8 pt → arredondado para **3 pts**.

---

## Sprint 24 ✅
**Objetivo**: OEE Benchmarking — comparativo por trabalhador, turno e tipo de equipamento + liquidação do tech debt de segurança diferido do Sprint 23
**ADR**: ADR-026, ADR-039
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-075 | Endpoints de benchmarking OEE (worker, shift, equipment-type) | 3 | ✅ concluído |
| US-076 | Frontend de benchmarking com charts comparativos | 3 | ✅ concluído |
| US-093 | Tech debt Sprint 23 — validações de planta, From de e-mail e interceptor de login | 2 | ✅ concluído |

**Total**: 8 pts

### Dependências
- US-076 depende de US-075 (consome os endpoints de benchmarking)
- US-093 é independente — pode ser desenvolvida em paralelo com US-075

---

#### US-075 — Endpoints de benchmarking OEE (3 pts)

> Expõe 4 endpoints analíticos de comparação de OEE: ranking por trabalhador, por turno, por tipo de equipamento e comparação entre dois períodos consecutivos. Todos os cálculos são feitos em Java sobre dados já existentes no `ImportBatch` (conforme ADR-026 — zero novas entidades).

**Backend — Estrutura comum**

1. `BenchmarkItemResponse` record em `oee/application/dto/`: `{ String dimension, double oeeAvg, int sampleCount, Double stdDev }`. `stdDev` é `null` quando `sampleCount < 3` (desvio padrão estatisticamente insignificante com menos de 3 amostras).
2. `BenchmarkResponse` record: `{ List<BenchmarkItemResponse> ranking, BenchmarkItemResponse best, BenchmarkItemResponse worst, double overallAvg }`. `best` e `worst` calculados após ordenação do `ranking` por `oeeAvg` (best = maior, worst = menor); `null` quando `ranking` está vazio.
3. `GetOeeBenchmarkUseCase` criado em `oee/application/usecase/`; endpoints adicionados ao `AnalyticsController` em `common/presentation/` (conforme ADR-012 Decisão 2).
4. Período máximo 90 dias: se `ChronoUnit.DAYS.between(from, to) > 90`, lança `IllegalArgumentException("Período máximo de benchmarking é 90 dias")` → `GlobalExceptionHandler` mapeia para `400 { "message": "..." }`.
5. `from` e `to` são `@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate`; `to` default = `LocalDate.now()`, `from` default = `to.minusDays(30)`; se `from` > `to`, retorna `400 { "message": "from deve ser anterior a to" }`.
6. Todos os 4 endpoints protegidos por `@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")`.
7. `AnalyticsController` já tem `@Validated` na classe (corrigido em US-059 da Sprint 16) — nenhum `@Validated` adicional necessário; os parâmetros `from` e `to` não têm constraints Bean Validation, pois a validação de período máximo é feita no use case via `IllegalArgumentException`.

**Backend — Endpoints de ranking**

8. `GET /api/v1/analytics/oee/benchmark/workers?from=&to=`: busca `ImportBatch` com `profileDate` entre `from` e `to` via `findByProfileDateBetween`; agrupa por `workerId` em Java usando `Collectors.groupingBy`; calcula `oeeAvg = média de (availableTimeMinutes / totalTimeMinutes)` e `stdDev` por grupo; retorna `BenchmarkResponse` com `ranking` ordenado por `oeeAvg DESC`; `dimension` = `workerId` do `ImportBatch`.
9. `GET /api/v1/analytics/oee/benchmark/shifts?from=&to=`: agrupa por `shiftId` (campo adicionado em Sprint 19); registros com `shiftId = null` agrupados sob `dimension = "Sem Turno"`; mesmo cálculo de `oeeAvg` e `stdDev`; ranking ordenado por `oeeAvg DESC`.
10. `GET /api/v1/analytics/oee/benchmark/equipment-type?from=&to=`: requer join com `Equipment` via `ImportBatch.equipment`; busca `ImportBatch` com join fetch `equipment`; agrupa por `equipment.type.name()` (String do enum `EquipmentType`); mesmo cálculo; `dimension` = nome do enum (ex: `"MACHINE"`, `"TOOL"`). **Nota de limitação (Sprint 24)**: `ImportBatch` não possui relação com `Equipment` no modelo atual (sem campo `equipment_id` na tabela `import_batch`). Implementado com fallback `label="GERAL"` agrupando todos os registros em uma única entrada. Agrupamento real por tipo de equipamento requer adição de `ImportBatch.equipment` ao modelo — diferido para sprint futura.
11. Teste unitário `GetOeeBenchmarkUseCaseTest`: (a) ranking por worker com 3 workers, dados variados → ordena DESC, `best`/`worst` corretos; (b) período vazio → `ranking = []`, `best = null`, `worst = null`, `overallAvg = 0.0`; (c) período > 90 dias → `IllegalArgumentException`; (d) `stdDev = null` para `sampleCount < 3`; (e) ranking por shift com `shiftId = null` → agrupado em "Sem Turno".

**Backend — Endpoint de comparação de períodos**

12. `GET /api/v1/analytics/oee/benchmark/period-comparison?from=&to=`: calcula `periodDays = ChronoUnit.DAYS.between(from, to)`; período anterior = `[from - periodDays, from)`; retorna `PeriodComparisonResponse` record:
    ```json
    {
      "current":  { "labels": ["2026-W01", "2026-W02"], "values": [0.78, 0.82] },
      "previous": { "labels": ["2025-W49", "2025-W50"], "values": [0.72, 0.75] },
      "improvementPct": 8.5
    }
    ```
    `improvementPct = ((currentAvg - previousAvg) / previousAvg) * 100`; `null` quando `previousAvg = 0` (divisão por zero evitada); negativo indica regressão.
13. `TimeSeriesResponse` (já existente de US-043/044) reutilizado para `current` e `previous`; agrupamento por semana ISO idêntico ao `GetOeeTrendUseCase`.

---

#### US-076 — Frontend de benchmarking OEE (3 pts)

> Adiciona aba "Benchmark" à rota `/analytics/oee` com visualizações comparativas de OEE por trabalhador, turno, tipo de equipamento e comparação de períodos. Reutiliza `BarChartComponent` e `LineChartComponent` já existentes em `shared/charts/`.

**Frontend — Estrutura e roteamento**

1. Aba "Benchmark" adicionada ao componente `/analytics/oee` via `MatTabGroup`; as abas existentes ("Trend") permanecem; rota não muda (sub-abas via `MatTab`, não via roteamento).
2. `OeeBenchmarkComponent` standalone (`OnPush`) criado em `oee/analytics/oee-benchmark/`; adicionado ao template de `/analytics/oee` como content da nova aba.
3. `AnalyticsService` (ou `OeeAnalyticsService`) estendido com métodos: `getBenchmarkWorkers(from, to): Observable<BenchmarkResponse>`, `getBenchmarkShifts(from, to): Observable<BenchmarkResponse>`, `getBenchmarkEquipmentTypes(from, to): Observable<BenchmarkResponse>`, `getPeriodComparison(from, to): Observable<PeriodComparisonResponse>`.
4. Interfaces `BenchmarkItemResponse`, `BenchmarkResponse`, `PeriodComparisonResponse` tipadas no service sem `any`.

**Frontend — Seletores de período**

5. Dois date pickers (`MatDatepicker`) no topo da aba: "De" (`from`) e "Até" (`to`); default: `to = hoje`, `from = hoje - 30 dias`; `to` max = `hoje`; `from` min = `hoje - 90 dias`; mudança de data recarrega todos os 4 charts via `effect()` ou `computed()` sobre `signal<LocalDate>`.
6. Validação client-side: se `from > to`, exibe mensagem "A data inicial deve ser anterior à data final" abaixo dos campos; botão de atualização desabilitado.

**Frontend — Ranking de trabalhadores**

7. `BarChartComponent` horizontal com `ranking` de trabalhadores por `oeeAvg DESC`; cada barra colorida conforme faixa: verde (`#22C55E`) para `oeeAvg ≥ 0.65`, amarela (`#F59E0B`) para `0.50 ≤ oeeAvg < 0.65`, vermelha (`#EF4444`) para `oeeAvg < 0.50`; label = `dimension` (workerId); valor = `oeeAvg * 100` (percentual).
8. Abaixo do chart: dois cards side-by-side "Melhor Performer" e "Pior Performer"; cada card exibe: nome (`dimension`), OEE % em fonte grande, `sampleCount` como texto secundário, `stdDev` formatado como `± N.N%` (oculto se `null`).
9. Quando `ranking` está vazio: mensagem "Sem dados para o período selecionado" no lugar do chart.

**Frontend — Ranking por turno e por tipo de equipamento**

10. Seção "Por Turno": `BarChartComponent` horizontal idêntico ao de workers; `dimension` = nome do turno (ou "Sem Turno" quando `shiftId = null`); mesma lógica de cores.
11. Seção "Por Tipo de Equipamento": `BarChartComponent` horizontal; `dimension` = tipo em português mapeado no componente (`"MACHINE"→"Máquinas"`, `"TOOL"→"Ferramentas"`, `"VEHICLE"→"Veículos"`, `"INFRASTRUCTURE"→"Infraestrutura"`); mesma lógica de cores.

**Frontend — Comparação de períodos**

12. Seção "Comparação de Períodos": `LineChartComponent` com dois datasets sobrepostos: período atual em azul sólido (`#0099B8`), período anterior em cinza tracejado (`#9CA3AF`); eixo X usa labels do período atual; se os arrays tiverem comprimentos diferentes, o período mais curto é exibido com o comprimento que possui.
13. Card "Evolução": exibe `improvementPct` com sinal (ex: `+8,5%`) e cor (verde se ≥ 0, vermelho se < 0); texto "em relação ao período anterior de N dias"; `null` exibe "N/D" em cinza.

**Frontend — Toggle de referência mundial**

14. `MatSlideToggle` "Linha de referência OEE Classe Mundial (85%)": quando ativo, passa `referenceValue=0.85` para todos os `LineChartComponent` e `BarChartComponent` da aba; linha horizontal pontilhada vermelha em 85%; estado do toggle persiste via `signal<boolean>` local (não em localStorage).

**Frontend — Loading e erros**

15. Skeleton loader (`<div class="skeleton-chart">`) para cada seção enquanto dados carregam; erro da API exibe snackbar persistente com mensagem do backend; os demais charts continuam carregando independentemente (falha de um endpoint não bloqueia os outros — cada seção tem `catchError` independente).
16. Spec `oee-benchmark.component.spec.ts`: (a) chart de workers renderiza com dados mockados; (b) período inválido (`from > to`) desabilita atualização e exibe mensagem de validação; (c) `improvementPct = null` exibe "N/D"; (d) toggle de referência passa `referenceValue=0.85` para os charts; (e) ranking vazio exibe mensagem "Sem dados".

---

#### US-093 — Tech debt Sprint 23 — validações de planta, From de e-mail e interceptor de login (2 pts)

> Cobre 4 itens de security debt (todos LOW) identificados por Beatriz na revisão do Sprint 23: SEC-078 (lista de plantIds sem bound), SEC-079 (username SMTP exposto no From dos emails de escalação), SEC-080 (interceptor Angular injeta X-Plant-Id no login), SEC-081 (associação de planta aceita plantas inativas).

**Backend — SEC-078: `@Size(max=100)` em `AssignUserPlantsRequest.plantIds`**

1. `AssignUserPlantsRequest` (DTO em `common/application/dto/`): adicionar `@Size(max = 100, message = "plantIds deve conter no máximo 100 plantas")` ao campo `List<UUID> plantIds`; lista vazia (`[]`) continua válida (semântica de "remover todas as plantas do usuário").
2. `PlantController` (ou `AdminController` — onde `PUT /api/v1/admin/users/{userId}/plants` está mapeado): verificar que a classe tem `@Validated`; se não tiver, adicionar — necessário para que `@Size` no campo do DTO seja validado via Bean Validation; `ConstraintViolationException` → `GlobalExceptionHandler` já retorna `400`.
3. Teste unitário: lista com 101 UUIDs → `400` com mensagem `"plantIds deve conter no máximo 100 plantas"`; lista com 100 UUIDs → aceito; lista vazia → aceito.

**Backend — SEC-079: propriedade `app.mail.from` separada de `spring.mail.username`**

4. `application.properties`: adicionar `app.mail.from=${spring.mail.username}` como valor default (garante retrocompatibilidade — em dev, o valor é o mesmo; em produção, pode ser sobrescrito para `noreply@msb.com.br` ou endereço dedicado).
5. `EmailEscalationService`: substituir `@Value("${spring.mail.username}") String fromAddress` por `@Value("${app.mail.from}") String fromAddress`; nenhuma outra alteração na lógica de envio.
6. `QmsEmailService` (Sprint 6, envio de emails de NC): verificar se também usa `${spring.mail.username}` como From; se sim, aplicar a mesma substituição para consistência; se já usa outra propriedade, manter como está e apenas documentar.

**Frontend — SEC-080: excluir `/auth/` do `plantHeaderInterceptor`**

7. `plantHeaderInterceptor` (Angular `HttpInterceptorFn` em `shared/`): a condição atual `req.url.includes('/api/')` deve ser alterada para `req.url.includes('/api/') && !req.url.includes('/auth/')`. Isso garante que a request `POST /api/v1/auth/login` não receba o header `X-Plant-Id`, eliminando a exposição desnecessária do plant_id da sessão anterior durante o login.
8. Spec `plant-header.interceptor.spec.ts` (novo ou atualizado): (a) request para `/api/v1/maintenance/equipment` com planta selecionada → header `X-Plant-Id` presente; (b) request para `/api/v1/auth/login` → header `X-Plant-Id` ausente mesmo com planta selecionada; (c) request para `/api/v1/auth/refresh` → header ausente.

**Backend — SEC-081: rejeitar plantas inativas em `AssignUserPlantsUseCase`**

9. `AssignUserPlantsUseCase.execute()`: substituir `plantRepository.findById(plantId)` por `plantRepository.findByIdAndActiveTrue(plantId)`; se retornar `Optional.empty()` (planta inexistente **ou** inativa), lançar `422 { "message": "Planta {id} não encontrada ou inativa" }`; a mensagem já era retornada para plantas inexistentes (conforme AC#10 de US-063) — a alteração apenas amplia o mesmo guard para incluir plantas inativas.
10. `PlantRepository`: adicionar método `Optional<Plant> findByIdAndActiveTrue(UUID id)` se ainda não existir (Spring Data deriva a query automaticamente do nome do método).
11. Teste unitário `AssignUserPlantsUseCaseTest` (novo ou atualizado): (a) planta ativa → associação criada; (b) planta inexistente → `422`; (c) planta inativa (`active = false`) → `422` com mesma mensagem de planta inexistente.

**Observações de esforço**

- SEC-078 (@Size + @Validated): ~0,4 pt — 1 anotação no DTO + verificação no controller + teste.
- SEC-079 (app.mail.from): ~0,3 pt — 1 property + 1 substituição de @Value + verificação no QmsEmailService.
- SEC-080 (interceptor login): ~0,3 pt — 1 condição no interceptor + spec.
- SEC-081 (planta inativa): ~0,4 pt — substituição de método + novo método no repositório + teste.
- Total: ~1,4 pt → arredondado para **2 pts** (itens cirúrgicos e independentes).

---

## Sprint 25 ✅
**Objetivo**: LGPD compliance — retenção automática, anonimização, direito ao esquecimento + correção de segurança nos endpoints de benchmark OEE
**ADRs**: ADR-022, ADR-039
**Status**: concluída

### Tech Debt diferido do Sprint 24 (Beatriz — security)
- **SEC-082** (MEDIUM): Endpoints `/oee/benchmark/workers|shifts|equipment-type|period-comparison` expostos a OPERATOR via `@PreAuthorize` de método que sobrescreve o `SUPERVISOR+` da classe. Em particular, `/benchmark/workers` expõe `workerName` com métricas individuais de performance para OPERATOR — implicação de privacidade operacional. **Decisão de produto**: OPERATOR não deve ver rankings de performance individual — dados nominais de colegas são reservados para SUPERVISOR+. Fix: remover os 4 `@PreAuthorize` de método nos 4 endpoints de benchmark, herdando o `SUPERVISOR+` da classe.
- **SEC-083** (LOW): `console.error('Erro ao carregar turnos', err)` em `OeeAnalyticsComponent.loadShifts()` expõe objeto de erro HTTP completo (status, headers, stack trace) no console do browser em produção. Fix: substituir por `this.errorMsg.set('Erro ao carregar turnos.')` sem logar o objeto `err`.

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-067 | Job de retenção e anonimização automática de dados | 4 | ✅ concluído |
| US-068 | Direito ao esquecimento + exportação de dados pessoais | 3 | ✅ concluído |
| US-094 | Tech debt Sprint 24 — SEC-082 (acesso OPERATOR a benchmark) + SEC-083 (console.error) | 1 | ✅ concluído |

**Total**: 8 pontos

### Dependências
- US-067 é independente — `DataRetentionJob` e `ConsentRecord` são novos, sem dependências de Sprint 24
- US-068 depende de US-067 (consome `DataRetentionService` para preview e o `ConsentRecord` já criado)
- US-094 é independente das demais — fix cirúrgico sobre código existente, pode ser desenvolvida em paralelo

---

#### US-067 — Job de retenção automática (4 pts)

**Backend — Job agendado**
1. `DataRetentionJob` com `@Scheduled(cron = "0 0 2 1 * *", zone = "America/Sao_Paulo")` (1º de cada mês às 2h); `@EnableScheduling` já ativo em `BackendApplication` (Sprint 9)
2. Anonimiza usuários desativados há > 2 anos: sobrescreve `username → "[usuario-{primeiros8chars(id)}]"`, `email → null`, `password → BCrypt("*invalid*")`; senha com BCrypt(12) impede qualquer login mesmo que o hash vaze
3. Anonimiza `AuditLog` com `timestamp < now().minusYears(5)`: sobrescreve `username → "[anonimizado]"`, `ipAddress → null`, `details → null`; `id`, `action`, `entityType`, `entityId`, `timestamp` preservados (imutabilidade histórica da ação)
4. Anonimiza campos pessoais em `NonConformance` com `reportedAt < now().minusYears(5)`: `reportedBy → "[anonimizado]"`, `closedBy → "[anonimizado]"` (quando não nulo)
5. Anonimiza campos pessoais em `WorkOrder` com `openedAt < now().minusYears(5)`: `openedBy → "[anonimizado]"`, `assignedTo → "[anonimizado]"` (quando não nulo)
6. Deleta fisicamente `Notification` com `createdAt < now().minusDays(90)` — dados transientes sem valor histórico
7. Cada bloco (usuários, audit, NCs, OSs, notificações) executa em `@Transactional` independente — falha de um bloco loga `ERROR` e continua os demais; `DataRetentionReport` acumula contadores por bloco. **Nota de implementação**: isolamento transacional por bloco implementado via `DataRetentionExecutor` (bean auxiliar `@Service`) injetado em `DataRetentionService`, contornando o problema de self-invocation de proxy Spring identificado por Helena (MF-S25-02).
8. `DataRetentionService` encapsula a lógica de cada bloco e é separado do `DataRetentionJob` para testabilidade — padrão estabelecido em `WeeklyReportService` (Sprint 9)
9. `POST /api/v1/admin/data-retention/run-now` (ADMIN) chama `DataRetentionService.executeAll()` imediatamente; retorna `200` com `DataRetentionReport`:
   ```json
   {
     "anonymizedUsers": 0,
     "anonymizedAuditLogs": 0,
     "anonymizedNonConformances": 0,
     "anonymizedWorkOrders": 0,
     "deletedNotifications": 12,
     "executedAt": "2026-05-23T02:00:00"
   }
   ```
10. `GET /api/v1/admin/data-retention/preview` (ADMIN) retorna contagens de candidatos sem executar anonimização (dry-run); consultas JPQL de contagem, não `findAll()` para evitar carregamento de entidades inteiras:
    ```json
    {
      "usersEligible": 2,
      "auditLogsEligible": 0,
      "nonConformancesEligible": 0,
      "workOrdersEligible": 0,
      "notificationsEligible": 12
    }
    ```
11. `DataRetentionController` em `common/presentation/`; use cases agrupados em `DataRetentionService` em `common/application/`; todos os endpoints com `@PreAuthorize("hasRole('ADMIN')")`

**Entidade `ConsentRecord`**
12. Entidade `ConsentRecord` criada em `common/domain/` com campos: `id` (UUID), `username` (String), `consentVersion` (String, ex: `"v1.0"`), `consentedAt` (LocalDateTime), `ipAddress` (String, nullable)
13. Migration: tabela `consent_record` com índice único em `username`
14. Seed: `DataInitializer` (ou migration de dados) insere `ConsentRecord` para todos os usuários existentes com `consentVersion = "v1.0"`, `consentedAt = now()`, `ipAddress = null` — retrocompatibilidade
15. `AuditAction` enum: adicionar `DATA_RETENTION_EXECUTED` e `USER_ANONYMIZED`

**Backend — Testes**
16. `DataRetentionServiceTest` com os seguintes cenários: (a) usuário inativo há > 2 anos é anonimizado; (b) usuário inativo há < 2 anos não é anonimizado; (c) `AuditLog` com timestamp > 5 anos: `username` e `details` nulos após execução; (d) `Notification` com `createdAt` > 90 dias é deletada; (e) falha em um bloco não cancela os demais — `verify(auditRepository).save(any())` confirmado mesmo após falha forçada no bloco de usuários

---

#### US-068 — Direito ao esquecimento e exportação de dados pessoais (3 pts)

**Backend — Anonimização imediata**
1. `POST /api/v1/admin/users/{id}/anonymize` (ADMIN): executa anonimização imediata do usuário-alvo via `DataRetentionService.anonymizeUser(userId, requesterUsername)` — reutiliza a mesma lógica de anonimização do job; retorna `200` com:
   ```json
   {
     "anonymized": true,
     "affectedEntities": {
       "auditLogs": 5,
       "nonConformances": 2,
       "workOrders": 3
     }
   }
   ```
2. Usuário já anonimizado (`username` começa com `"[usuario-"`) retorna `422 { "message": "Usuário já foi anonimizado" }`
3. Tentativa de anonimizar o próprio usuário autenticado retorna `422 { "message": "Não é possível anonimizar a própria conta" }` — ADMIN não pode se auto-anonimizar
4. Operação registrada no `AuditLog` com `action = USER_ANONYMIZED`, `entityId = userId`, `details = Map.of("requester", adminUsername, "affectedEntities", {...})` — registro irremovível (nenhum endpoint DELETE/UPDATE exposto para `AuditLog`)

**Backend — Exportação de dados pessoais**
5. `GET /api/v1/users/me/data-export` (qualquer usuário autenticado): exporta JSON com todos os dados pessoais do usuário atual:
   ```json
   {
     "exportedAt": "2026-05-23T10:00:00",
     "profile": { "username": "...", "email": "...", "role": "...", "active": true },
     "nonConformancesReported": [...],
     "workOrdersOpened": [...],
     "auditLogEntries": [...]
   }
   ```
6. `nonConformancesReported`: lista de `{ id, title, type, severity, status, reportedAt }` onde `reportedBy = username` atual
7. `workOrdersOpened`: lista de `{ id, title, type, priority, status, openedAt }` onde `openedBy = username` atual
8. `auditLogEntries`: lista de `{ id, action, entityType, entityId, timestamp }` onde `username = atual` — sem campo `details` (pode conter dados de outros usuários referenciados)
9. Resposta com `Content-Type: application/json` e `Content-Disposition: attachment; filename="dados-pessoais-{username}-{date}.json"`
10. `DataExportUseCase` em `common/application/usecase/`; endpoint em `UserController` (já existente em `common/presentation/`)

**Frontend — Painel LGPD (admin)**
11. Rota `/admin/lgpd` (ADMIN, lazy-loaded): dois cards principais — "Candidatos à Anonimização" e "Executar Retenção"
12. Card "Candidatos à Anonimização": tabela com colunas username, motivo (ex: "Inativo há 3 anos"), tipo (Usuário / AuditLog / NC / OS / Notificação); dados carregados via `GET /api/v1/admin/data-retention/preview`; empty state: "Nenhum candidato no momento"
13. Botão "Executar Retenção" (ADMIN): dialog de confirmação com texto "Esta operação é irreversível. Deseja continuar?" + botão "Executar Retenção" (vermelho) + "Cancelar"; executa `POST .../run-now`; após conclusão exibe snackbar com resumo "N usuários anonimizados, M notificações removidas"
14. Coluna "Ações" na tabela de usuários do `/admin/users` (já existente): adicionar botão "Anonimizar" por linha (visível apenas para ADMIN, oculto para o próprio usuário autenticado); abre dialog com campo de confirmação digitável — usuário deve digitar o `username` alvo antes de confirmar; erro `422` exibe mensagem da API em snackbar
15. Link "LGPD" adicionado ao submenu de administração no `NavComponent`, visível apenas para ADMIN
16. `ChangeDetectionStrategy.OnPush`, standalone, signals

**Frontend — Exportação self-service**
17. Link "Exportar meus dados" adicionado ao menu de perfil no `NavComponent` (dropdown do username — visível para todos os usuários autenticados); dispara `GET /api/v1/users/me/data-export`; download do JSON inicia automaticamente via `<a>` com `download` attribute e Blob URL; snackbar "Exportação iniciada" durante o carregamento
18. Spec `admin-lgpd.component.spec.ts`: (a) tabela de candidatos exibe dados do preview mockado; (b) empty state exibido quando preview retorna zeros; (c) dialog de confirmação de "Executar Retenção" exibido ao clicar no botão; (d) snackbar com resumo exibido após execução bem-sucedida

---

#### US-094 — Tech debt Sprint 24 — SEC-082 e SEC-083 (1 pt)

> Cobre os dois itens diferidos identificados por Beatriz na revisão do Sprint 24: exposição indevida dos endpoints de benchmark a OPERATOR (SEC-082) e vazamento de objeto de erro HTTP no console do browser (SEC-083).

**Backend — SEC-082: corrigir acesso OPERATOR a endpoints de benchmark**
1. `OeeBenchmarkController` (ou classe equivalente onde os 4 endpoints de benchmark estão declarados): remover as anotações `@PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")` dos métodos dos 4 endpoints:
   - `GET /api/v1/oee/benchmark/workers`
   - `GET /api/v1/oee/benchmark/shifts`
   - `GET /api/v1/oee/benchmark/equipment-type`
   - `GET /api/v1/oee/benchmark/period-comparison`
2. Após a remoção, os 4 métodos herdam o `@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")` da classe — OPERATOR passa a receber `403 Forbidden` ao tentar acessar qualquer endpoint de benchmark
3. Teste de integração ou unitário de controller: (a) requisição autenticada como OPERATOR em `/benchmark/workers` retorna `403`; (b) requisição autenticada como SUPERVISOR retorna `200`; (c) requisição autenticada como ADMIN retorna `200`
4. `@PreAuthorize` de classe da `OeeBenchmarkController` confirmada como `SUPERVISOR+` — nenhuma outra rota do controller deve ter acesso OPERATOR

**Frontend — SEC-083: remover console.error com objeto de erro em loadShifts()**
5. `OeeAnalyticsComponent` (arquivo `oee-analytics.component.ts`): no método `loadShifts()`, substituir:
   ```typescript
   // ANTES (expõe objeto HTTP bruto):
   console.error('Erro ao carregar turnos', err);
   ```
   por:
   ```typescript
   // DEPOIS (mensagem genérica sem vazar detalhes de infraestrutura):
   this.errorMsg.set('Erro ao carregar turnos.');
   ```
   — sem nenhum `console.error`, `console.warn` ou `console.log` adicionado; padrão consistente com os demais blocos de erro do mesmo componente
6. Confirmar que os outros blocos `catchError` em `OeeAnalyticsComponent` já estão corretos (sem logar o objeto `err`); se algum estiver inconsistente, corrigir junto neste commit
7. Spec `oee-analytics.component.spec.ts`: (a) quando `loadShifts()` falha (serviço retorna `throwError`), `errorMsg()` recebe o texto `'Erro ao carregar turnos.'`; (b) console permanece limpo (sem `spyOn(console, 'error')` disparado)

---

## Sprint 26 ✅
**Objetivo**: Progressive Web App — instalação, cache offline e fila de NCs offline + liquidação do tech debt crítico de LGPD/security diferido do Sprint 25
**ADRs**: ADR-023
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-069 | Service Worker, manifest e cache de leitura offline | 3 | ✅ concluído |
| US-070 | Fila offline para criação de NC + banner de atualização | 3 | ✅ concluído |
| US-095 | Tech debt Sprint 25 — SEC-083/084/085/086/087 | 2 | ✅ concluído |

**Total**: 8 pontos

### Dependências
- US-070 depende de US-069 (service worker e `manifest.webmanifest` precisam estar configurados antes da fila offline)
- US-095 é independente das demais — fix cirúrgico sobre código existente, pode ser desenvolvida em paralelo

---

#### US-069 — Service Worker, manifest e cache de leitura offline (3 pts)

> Configura o Industrial Hub como Progressive Web App instalável, com cache offline para leitura de dados críticos e banner de atualização automático de versão.

**Setup**
1. `ng add @angular/pwa` executado em `apps/frontend/`; `@angular/service-worker` adicionado como dependência em `package.json`
2. `manifest.webmanifest` gerado com: `name: "Industrial Hub"`, `short_name: "MSB Hub"`, `theme_color: "#0099B8"`, `background_color: "#F4F6F9"`, `display: "standalone"`, `start_url: "/dashboard"`, `scope: "/"`
3. Ícones PWA gerados em 72, 96, 128, 144, 152, 192, 384 e 512 px; servidos em `assets/icons/`; todos os tamanhos referenciados no `manifest.webmanifest`
4. `serviceWorker: true` configurado **apenas** no build de produção em `angular.json` — sem service worker em desenvolvimento (evita cache de dados antigos durante dev)
5. `ngsw-worker.js` servido pelo Angular CLI automaticamente no build de produção; `ngsw.json` gerado pela CLI a partir do `ngsw-config.json`

**Cache offline de leitura**
6. `ngsw-config.json` configurado com `assetGroups` para cache dos assets do app (strategy: `prefetch`) e `dataGroups` com strategy `freshness` (rede primeiro, cache como fallback) para:
   - `/api/v1/kpi/summary` — timeout 4s, cache máximo 1h
   - `/api/v1/maintenance/equipment` — timeout 4s, cache máximo 30 min
   - `/api/v1/qms/non-conformances` — timeout 5s, cache máximo 15 min
7. Dados cacheados exibidos normalmente offline; banner "Exibindo dados salvos (sem conexão)" em âmbar exibido nos componentes que dependem desses endpoints quando `!navigator.onLine`

**Banner de atualização de versão**
8. `PwaUpdateService` criado em `shared/` com `@Injectable({ providedIn: 'root' })`; injeta `SwUpdate`
9. `SwUpdate.versionUpdates` monitorado: quando tipo `VERSION_READY`, exibe `MatSnackBar` persistente "Nova versão disponível" com botão "Recarregar" — clique chama `document.location.reload()`
10. `SwUpdate.unrecoverable` monitorado: quando detectado estado irrecuperável do service worker, chama `document.location.reload()` automaticamente (sem confirmação — estado irrecuperável não tem alternativa)
11. `PwaUpdateService` injetado no `AppComponent` e inicializado no `ngOnInit`

**Instalação**
12. `PwaInstallService` criado em `shared/`: captura evento `beforeinstallprompt` e armazena o deferred prompt; expõe signal `canInstall: Signal<boolean>`
13. Botão "Instalar App" exibido no nav apenas quando `canInstall()` é `true`; clique chama `prompt.prompt()` e aguarda `prompt.userChoice`; após escolha do usuário (qualquer decisão), `canInstall` setado para `false`
14. `PwaInstallService.ngOnInit` registra listener de `appinstalled` para ocultar o botão após instalação concluída

---

#### US-070 — Fila offline para criação de NC + banner de atualização (3 pts)

> Garante que operadores possam registrar Não-Conformidades mesmo sem conexão; as NCs são enfileiradas em IndexedDB e sincronizadas automaticamente ao retornar online.

**OfflineQueueService**
1. `OfflineQueueService` criado em `qms/` (`@Injectable({ providedIn: 'root' })`); usa IndexedDB diretamente via `IDBFactory` (sem biblioteca externa) para armazenar `OfflineNcEntry` com campos: `id` (UUID local, `crypto.randomUUID()`), `payload` (objeto `CreateNcRequest`), `createdAt` (ISO string), `attempts` (number)
2. Método `enqueue(payload: CreateNcRequest): Promise<void>` grava entrada no IndexedDB store `offline_ncs`; schema gerado na abertura do banco com `request.onupgradeneeded`
3. Método `getAll(): Promise<OfflineNcEntry[]>` retorna entradas ordenadas por `createdAt ASC`
4. Método `remove(id: string): Promise<void>` remove entrada por ID local após sync bem-sucedido

**Detecção de conectividade e sync**
5. `NetworkStatusService` criado em `shared/`: expõe `isOnline: Signal<boolean>` atualizado via `fromEvent(window, 'online')` e `fromEvent(window, 'offline')` com `toSignal()` e estado inicial de `navigator.onLine`
6. `OfflineQueueService.startSync()` chamado no `AppComponent.ngOnInit`; observa `NetworkStatusService.isOnline()`; quando transita para `true`, chama `drainQueue()`
7. `drainQueue()` itera as entradas da fila **sequencialmente** (não em paralelo) via `for...of` com `await`; para cada entrada: chama `NcService.create(entry.payload)` — se sucesso: `remove(entry.id)`; se falha (4xx/5xx): incrementa `entry.attempts` e persiste; se `entry.attempts >= 3`: remove da fila e emite snackbar de erro individual "NC '{title}' falhou após 3 tentativas — descartada"

**UX de estado offline**
8. Formulário de criação de NC (`/qms/non-conformances/new`): intercepta erro de rede no submit; quando offline (`!navigator.onLine`), chama `offlineQueueService.enqueue(payload)` em vez de retornar erro; exibe `MatSnackBar` persistente "Sem conexão. NC salva localmente — será enviada quando a conexão retornar."
9. Outras mutações (PUT, DELETE, status transitions) quando offline: exibem `MatSnackBar` de erro "Operação indisponível sem conexão" (sem enfileirar)
10. Badge "N pendentes" exibido no ícone de NC no nav quando `offlineQueueService.pendingCount()` > 0; signal `pendingCount` derivado de `getAll()` com polling via `interval(5000)` e `toSignal()`
11. Item "NCs offline (N)" exibido no nav quando `pendingCount() > 0`; clique navega para dialog/painel listando as NCs pendentes com: título, tipo, severidade, `createdAt` local; sem opção de editar ou cancelar (fila é FIFO)

**Feedback de sincronização**
12. Após `drainQueue()` concluir sem erros: `MatSnackBar` "N NC(s) sincronizadas com sucesso" (duração 5s)
13. Após `drainQueue()` com falhas parciais: `MatSnackBar` "N NC(s) sincronizadas; M falharam após 3 tentativas" (duração 8s, botão "Ver detalhes" desabilitado na sprint 26 — reservado para sprint futura)
14. `OfflineQueueService` expõe `lastSyncAt: Signal<Date | null>` atualizado após cada `drainQueue()` bem-sucedido

---

#### US-095 — Tech debt Sprint 25 — SEC-083/084/085/086/087 (2 pts)

> Liquida 5 itens de segurança diferidos da revisão de Beatriz no Sprint 25: um HIGH (proteção de ADMIN na anonimização), dois MEDIUM (auditoria de motivo + rate limiting retention) e dois LOW (console.error residual + sanitização de filename).

**SEC-084 — HIGH: Guard contra anonimização de ADMIN ativo**
1. Em `DataRetentionService.anonymizeUser()`, adicionar guard imediatamente antes da anonimização: `if (user.getRole() == Role.ADMIN && user.isActive()) { throw new CannotAnonymizeActiveAdminException("Não é possível anonimizar um administrador ativo: " + target.getUsername()); }`. A exceção `CannotAnonymizeActiveAdminException` já foi criada por Mateus no pós-fix do Sprint 25 — verificar se está sendo lançada neste ponto
2. `GlobalExceptionHandler` deve ter handler para `CannotAnonymizeActiveAdminException` retornando `422 Unprocessable Entity` com `{ "message": "..." }` — adicionar handler se ausente
3. Teste unitário `DataRetentionServiceTest`: cenário "anonymize active ADMIN throws CannotAnonymizeActiveAdminException" — mock `user.getRole() = ADMIN`, `user.isActive() = true`; verificar que `UserRepository.save()` não é chamado (anonimização não ocorre)

**SEC-085 — MEDIUM: `reason` registrado no AuditLog**
4. Em `DataRetentionService.anonymizeUser(String userId, String requesterUsername, String reason)`, garantir que `reason` está presente na assinatura do método; se `AdminUserController` já passa `request.reason()` (pós-fix Sprint 25), verificar que o `auditService.log()` dentro de `anonymizeUser()` inclui `reason` no `details`: `Map.of("requester", requesterUsername, "reason", reason, "affectedEntities", affectedEntities)` — adicionar se ausente
5. Teste unitário: cenário "anonymize user records reason in AuditLog" — verificar que `auditService.log()` é chamado com `details` contendo chave `"reason"` com o valor passado

**SEC-086 — MEDIUM: Rate limiting no `DataRetentionController.runNow()`**
6. Em `DataRetentionController`, adicionar `AtomicReference<Instant> lastRunTime = new AtomicReference<>(Instant.MIN)` como campo da classe (padrão idêntico ao `SlaRuleController` e `AlertThresholdController` já implementados)
7. Em `runNow()`: calcular `now = Instant.now()`; verificar `lastRunTime.get().plusSeconds(3600).isAfter(now)` — se verdadeiro, calcular `secondsRemaining` e lançar `DataRetentionCooldownException(secondsRemaining)` (bean já criado por Mateus no pós-fix Sprint 25); se falso, usar `compareAndSet` para atualizar `lastRunTime` atomicamente
8. `GlobalExceptionHandler` deve ter handler para `DataRetentionCooldownException` retornando `429 Too Many Requests` com `{ "message": "Aguarde N segundos antes de executar novamente.", "secondsRemaining": N }` — adicionar handler se ausente
9. Teste unitário: cenário "runNow twice within cooldown returns 429" — chamar `runNow()` com `lastRunTime` setado há 30 min; verificar que `DataRetentionCooldownException` é lançada com `secondsRemaining > 0`

**SEC-083 — LOW: `console.error` residual em `work-order-list.component.ts`**
10. Em `work-order-list.component.ts` linha 80: substituir `console.error('Erro ao carregar turnos', err)` por `this.shiftsErrorMsg.set('Erro ao carregar turnos.')` (sem logar o objeto `err`); declarar signal `shiftsErrorMsg = signal<string>('')` no componente se não existir; exibir mensagem no template via `@if (shiftsErrorMsg())` em elemento de texto simples (sem snackbar — erro de carregamento de turno é não-crítico)
11. Spec `work-order-list.component.spec.ts`: cenário "loadShifts error sets shiftsErrorMsg without exposing err object" — verificar que `console.error` não é chamado quando o HTTP retorna erro

**SEC-087 — LOW: Sanitização do filename no `Content-Disposition`**
12. Em `UserController.exportMyData()` (ou onde quer que `Content-Disposition` seja construído com `auth.getName()`): substituir a construção do filename por `String safeUsername = auth.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_")` e usar `safeUsername` na construção do header `Content-Disposition: attachment; filename="dados-pessoais-{safeUsername}-{date}.json"`
13. Teste unitário: cenário "exportMyData filename sanitizes special chars" — mock `auth.getName()` = `"user with spaces"` ou `"user\"test"` ; verificar que o header `Content-Disposition` retornado contém filename sem caracteres especiais (regex `[a-zA-Z0-9_\\-.]` apenas)

---

## Sprint 27 ✅
**Objetivo**: Outbound webhooks para integração com ERP e ferramentas externas
**ADRs**: ADR-024, ADR-040
**Status**: concluída

### Tech Debt diferido do Sprint 27 (Beatriz — security)
- **SEC-088** (MEDIUM): Service Worker cacheia `/api/v1/qms/non-conformances` sem `Cache-Control: no-store` — resposta contém `reportedBy` (dado pessoal LGPD). Fix: header `Cache-Control: no-store` no endpoint de listagem ou remoção do grupo de cache no `ngsw-config.json`.
- **SEC-089** (MEDIUM): `AuthService.logout()` não chama `offlineQueueService.clearAll()` — NCs pendentes de um usuário permanecem no IndexedDB após logout. Fix: aguardar `clearAll()` antes de limpar localStorage.
- **SEC-090** (LOW): `console.warn` em `OfflineSyncService.drainQueue()` loga `entry.payload.title` — dado pessoal exposto no console do browser. Fix: substituir por signal de erro sem logar o payload.
- **SEC-093** (MEDIUM): URL com credenciais embutidas logada em texto plano por `WebhookDispatchService`; `secret` em `WebhookSubscription` sem `@ToString.Exclude`. Fix: sanitizar URL nos logs; adicionar `@ToString.Exclude` em `secret`.
- **SEC-094** (MEDIUM): `WebhookDelivery.errorMessage` populado com `e.getMessage()` pode conter IPs/portas internas. Fix: categorizar o erro com mensagem semântica genérica.
- **SEC-096** (LOW): `UpdateWebhookRequest.url` sem `@NotBlank` — semântica de `null` (sem alteração) vs `""` (string vazia) ambígua. Fix: `@Size(min=1)` ou documentação explícita de PATCH semântico.
- **SEC-097** (LOW): `TestWebhookUseCase` persiste `WebhookDelivery` com `event = NC_CREATED` como placeholder — histórico de testes aparece como entregas reais. Fix: criar `WebhookEvent.TEST`.
- **SEC-092** ✅ CORRIGIDO em `fa3dc19` — SSRF via URL de webhook: `WebhookUrlValidator` implementado com resolução DNS + bloqueio RFC-1918/link-local/loopback.
- **SEC-095** ✅ CORRIGIDO em `fa3dc19` — CRUD de webhook sem auditoria: `AuditAction.WEBHOOK_CREATED/UPDATED/DELETED/ACTIVATED/TESTED` adicionados.

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-071 | Gerenciamento de subscriptions de webhook | 3 | ✅ concluído |
| US-072 | Entrega de eventos e retry automático | 4 | ✅ concluído |

**Total**: 7 pontos

### Dependências
- US-072 depende de US-071 (entidade `WebhookSubscription` e repositório necessários antes do dispatch)
- Integração de US-072 com `EscalationUseCase` depende do evento `SLA_BREACHED` introduzido no Sprint 22 (US-062) ✅

---

#### US-071 — Gerenciamento de subscriptions de webhook (3 pts)

**Backend — Domínio**
1. Entidade `WebhookSubscription` criada em `common/domain/` com campos: `id` (UUID), `url` (`@Column(nullable=false, length=500)`), `secret` (nullable, `length=100`), `events` (`@ElementCollection` → tabela `webhook_event_type`, enum `WebhookEvent`), `description` (nullable, `length=200`), `active` (boolean, default `true`), `createdBy` (String), `createdAt` (LocalDateTime); índice em `active` (conforme ADR-024 Decisão 1)
2. Enum `WebhookEvent` criado com valores: `NC_CREATED`, `NC_STATUS_CHANGED`, `NC_CRITICAL_OPENED`, `WORK_ORDER_CREATED`, `WORK_ORDER_STATUS_CHANGED`, `EQUIPMENT_DECOMMISSIONED`, `SLA_BREACHED`
3. Migration: tabelas `webhook_subscription` e `webhook_event_type` (tabela de coleção com colunas `webhook_subscription_id` UUID + `events` VARCHAR(50))
4. `WebhookSubscriptionRepository` em `common/infrastructure/` com método `findByActiveTrue()` e `findByActiveTrueAndEventsContaining(WebhookEvent event)` para busca eficiente por evento

**Backend — CRUD endpoints**
5. `POST /api/v1/admin/webhooks` cria `WebhookSubscription` (ADMIN); corpo obrigatório: `url` (válida: começa com `https://` ou `http://`, max 500 chars), `events` (lista não vazia de `WebhookEvent`); opcionais: `secret` (max 100 chars), `description` (max 200 chars); `createdBy` preenchido com username do JWT; `createdAt` = UTC agora; retorna `201 WebhookSubscriptionResponse`
6. `url` inválida (não começa com `http://` ou `https://`, ou > 500 chars) retorna `400` com `{ "message": "URL de webhook inválida" }`
7. `events` vazia ou ausente retorna `400` com `{ "message": "Selecione pelo menos um evento" }`
8. `GET /api/v1/admin/webhooks` retorna `List<WebhookSubscriptionResponse>` ordenada por `createdAt DESC` (ADMIN)
9. `PUT /api/v1/admin/webhooks/{id}` atualiza `url`, `secret`, `events`, `description`, `active`; mantém `createdBy` e `createdAt` imutáveis; retorna `200 WebhookSubscriptionResponse` ou `404` com `{ "message": "Webhook não encontrado" }` (ADMIN)
10. `DELETE /api/v1/admin/webhooks/{id}` remove fisicamente a subscription (ADMIN); retorna `204` ou `404`
11. Controller `WebhookAdminController` em `common/presentation/`; use cases em `common/application/usecase/`: `CreateWebhookSubscriptionUseCase`, `UpdateWebhookSubscriptionUseCase`, `DeleteWebhookSubscriptionUseCase`, `ListWebhookSubscriptionsUseCase`
12. Todos os endpoints protegidos com `@PreAuthorize("hasRole('ADMIN')")`; controller deve ter `@Validated` (ADR-031)

**Backend — Endpoint de teste**
13. `POST /api/v1/admin/webhooks/{id}/test` (ADMIN) envia payload de teste síncrono ao URL da subscription; payload fixo `{ "event": "TEST", "timestamp": "...", "payload": { "message": "Webhook test" } }`; se `secret` presente, assina com HMAC-SHA256; retorna `200 { "responseCode": <int>, "durationMs": <long>, "success": <boolean> }` independente do código de resposta do endpoint alvo; timeout de 5s; subscription inexistente retorna `404`

**Backend — Histórico de entregas**
14. `GET /api/v1/admin/webhooks/{id}/deliveries` retorna `List<WebhookDeliveryResponse>` das últimas 50 entregas (`@Query` com `ORDER BY createdAt DESC LIMIT 50`) ordenadas por `createdAt DESC` (ADMIN); subscription inexistente retorna `404`
15. `WebhookDeliveryResponse` contém: `id`, `event`, `attempt`, `responseCode` (nullable — null se timeout), `durationMs`, `success` (boolean: `responseCode` entre 200–299), `createdAt`

**Frontend**
16. Rota `/admin/webhooks` (lazy-loaded, ADMIN only) — link "Webhooks" adicionado ao nav somente para role ADMIN; rota protegida por `AuthGuard` + verificação de role
17. Tabela de subscriptions com colunas: URL (truncada a 60 chars com tooltip completo), Eventos (chips com cor `#0099B8`), Status (chip: "Ativo"=verde / "Inativo"=cinza), Última entrega (código HTTP da entrega mais recente colorido: verde se 2xx, vermelho caso contrário, "—" se sem entregas), Ações (botões "Testar", "Editar", "Excluir")
18. Botão "Nova Subscription" (ADMIN) abre dialog de criação com campos: URL (input obrigatório), Eventos (checkboxes para cada `WebhookEvent` com labels legíveis), Secret (input opcional, type=password com botão de visibilidade), Descrição (textarea opcional); botão "Salvar" desabilitado até `url` + ao menos 1 evento preenchidos
19. Botão "Editar" abre mesmo dialog preenchido com dados atuais; campo Secret exibe placeholder "••••••" sem revelar valor salvo (atualizado apenas se preenchido novo valor)
20. Botão "Excluir" abre dialog de confirmação: "Excluir webhook {url}? Esta ação não pode ser desfeita."; confirmação dispara `DELETE`; snackbar "Webhook removido"
21. Botão "Testar" envia `POST /{id}/test`; durante envio, botão exibe spinner e fica desabilitado; resultado exibido em snackbar: "Sucesso (200 — 245ms)" ou "Falha (503 — 4980ms)" com a cor correspondente
22. Componente `OnPush`, standalone, signals; `subscriptions = signal<WebhookSubscriptionResponse[]>([])`, `isLoading = signal(false)`, `testingId = signal<string | null>(null)` para controle do spinner por linha
23. Spec `webhooks.component.spec.ts`: (a) tabela exibe dados mockados; (b) botão "Testar" desabilitado durante envio; (c) dialog de criação valida URL + evento obrigatórios; (d) confirmação de exclusão exibida antes do DELETE; (e) snackbar de sucesso exibido após criação

---

#### US-072 — Entrega de eventos e retry automático (4 pts)

**Backend — Entidade WebhookDelivery**
1. Entidade `WebhookDelivery` criada em `common/domain/` com campos: `id` (UUID), `webhookSubscription` (`@ManyToOne(fetch=LAZY)`), `event` (enum `WebhookEvent`), `attempt` (int, 1-based), `responseCode` (Integer nullable — null = timeout/erro de rede), `durationMs` (Long), `success` (boolean), `createdAt` (LocalDateTime); índice em `webhook_subscription_id` + `created_at` (conforme ADR-024 Decisão 3)
2. Migration: tabela `webhook_delivery`
3. `WebhookDeliveryRepository` com `findTop50ByWebhookSubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId)` — sem `findAll()`

**Backend — Serviço de dispatch**
4. `WebhookDispatchService` em `common/application/` com método `dispatch(WebhookEvent event, Object entityPayload)` anotado com `@Async`; falha neste método nunca aborta a operação de negócio do chamador (conforme ADR-024 Decisão 4)
5. Busca subscriptions ativas para o evento via `WebhookSubscriptionRepository.findByActiveTrueAndEventsContaining(event)`; se nenhuma encontrada, retorna imediatamente sem operação
6. Para cada subscription encontrada, chama método privado `deliverWithRetry(subscription, event, payload)` de forma assíncrona (dentro do `@Async` já ativo)
7. Payload JSON serializado por `ObjectMapper` no formato:
   ```json
   {
     "event": "NC_CRITICAL_OPENED",
     "timestamp": "<ISO-8601 UTC>",
     "payload": { "<campos da entidade>" }
   }
   ```
8. Se `secret` da subscription não for nulo e não for vazio: header `X-Hub-Signature-256: sha256=<hex(HMAC-SHA256(secret, payloadJson))>` adicionado à requisição; implementação via `javax.crypto.Mac` com algoritmo `HmacSHA256` (sem biblioteca externa)
9. Envio via `RestTemplate` com `ConnectTimeout = 3s` e `ReadTimeout = 5s`; resposta `2xx` = sucesso; qualquer outro status HTTP = falha; `ResourceAccessException` (timeout/erro de rede) = falha com `responseCode = null`
10. `WebhookDelivery` persistida para cada tentativa com `attempt`, `responseCode`, `durationMs` (medido com `System.currentTimeMillis()`), `success`

**Backend — Retry com backoff exponencial**
11. Retry automático: até 3 tentativas com backoff exponencial: tentativa 1 imediata, tentativa 2 após 5s (`Thread.sleep(5_000)` dentro de `@Async`), tentativa 3 após 30s; após 3 falhas consecutivas: nenhum retry adicional
12. Após a 3ª falha consecutiva: `subscription.setActive(false)` persistido; `Notification` ADMIN criada com título "Webhook desativado" e body "O webhook {url} foi desativado após 3 falhas consecutivas de entrega."; criação via `NotificationService.broadcast()` assíncrono (padrão ADR-013)
13. Retry não deve ultrapassar 3 tentativas por evento — cada entrega bem-sucedida (2xx) encerra o ciclo de retry para aquele evento imediatamente
14. `backoff` implementado em método separado para testabilidade; testes unitários usam injeção de `BiConsumer<Integer, Long>` como estratégia de sleep (evita `Thread.sleep` real nos testes)

**Backend — Integração com use cases existentes**
15. `WebhookDispatchService.dispatch()` chamado nos seguintes use cases (após persistência da entidade, dentro da mesma chamada de método mas fora da transação — `@TransactionalEventListener` ou chamada após `save()` + flush):
    - `CreateNonConformanceUseCase`: dispatch de `NC_CREATED`; se `severity == CRITICAL` dispatch adicional de `NC_CRITICAL_OPENED`
    - `TransitionNcStatusUseCase`: dispatch de `NC_STATUS_CHANGED`
    - `CreateWorkOrderUseCase`: dispatch de `WORK_ORDER_CREATED`
    - `TransitionWorkOrderStatusUseCase`: dispatch de `WORK_ORDER_STATUS_CHANGED`
    - `DeleteEquipmentUseCase` (soft-delete / decommission): dispatch de `EQUIPMENT_DECOMMISSIONED` somente quando `equipment.status = DECOMMISSIONED` (não em simples desativação)
    - `EscalationUseCase` (Sprint 22): dispatch de `SLA_BREACHED` após marcar `slaBreached = true`
16. `WebhookDispatchService` injetado como dependência nos use cases listados; ausência de subscriptions para o evento não é erro — noop silencioso
17. Teste unitário `WebhookDispatchServiceTest`: (a) subscription ativa para evento recebe chamada POST; (b) subscription inativa ignorada; (c) subscription para evento diferente ignorada; (d) HMAC-SHA256 header presente quando secret configurado; (e) 3 falhas consecutivas desativam subscription e criam Notification; (f) sucesso na tentativa 2 encerra retry sem tentativa 3; (g) `dispatch()` não lança exceção mesmo quando endpoint está offline

**Backend — Payload shapes por evento**
18. Cada evento inclui campos mínimos no `payload`:
    - `NC_CREATED` / `NC_CRITICAL_OPENED` / `NC_STATUS_CHANGED`: `{ id, title, severity, status, reportedBy, reportedAt }`
    - `WORK_ORDER_CREATED` / `WORK_ORDER_STATUS_CHANGED`: `{ id, title, type, priority, status, equipmentId, openedBy, openedAt }`
    - `EQUIPMENT_DECOMMISSIONED`: `{ id, code, name, type, status }`
    - `SLA_BREACHED`: `{ entityType, entityId, slaRuleId, breachedAt }`
    - Campos serialização: Java records `WebhookNcPayload`, `WebhookWorkOrderPayload`, `WebhookEquipmentPayload`, `WebhookSlaPayload` em `common/application/dto/`

---

## Sprint 28 ✅
**Objetivo**: Dashboard customizável por usuário com widgets drag-and-drop + liquidação do tech debt de segurança Sprints 26–27
**ADR**: ADR-027
**Status**: concluída

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-077 | Backend de persistência de layout de dashboard | 2 | ✅ concluído |
| US-078 | Frontend de dashboard com widgets personalizáveis | 5 | ✅ concluído |
| US-096 | Tech debt Sprints 26–27 — SEC-088/089/090/093/094/096/097/100 | 3 | ✅ concluído |

**Total**: 10 pontos

### Dependências
- US-078 depende de US-077 (endpoints de layout precisam existir antes da integração frontend)
- US-096 é independente das demais — fixes cirúrgicos, pode ser desenvolvida em paralelo

---

#### US-077 — Backend de persistência de layout de dashboard (2 pts)

**Backend**
1. `GET /api/v1/users/me/dashboard` retorna `UserDashboardConfigResponse { widgetsJson: "[...]" }` (qualquer usuário autenticado); se não existir configuração salva, retorna layout default calculado com base no role: OPERATOR recebe 6 widgets KPI; SUPERVISOR+ recebe 8 widgets (+ OEE Trend e NC Pareto)
2. Layout default definido como constante em `DashboardDefaultLayouts` em `common/application/` — sem query de banco para gerar o default; retornado diretamente como `widgetsJson` serializado
3. `PUT /api/v1/users/me/dashboard` aceita `{ "widgetsJson": "[...]" }` e cria ou atualiza `UserDashboardConfig`; `widgetsJson` max 10.240 chars (10 KB); retorna `200 UserDashboardConfigResponse`
4. `widgetsJson` nulo ou vazio retorna `400` com `{ "message": "widgetsJson não pode ser nulo ou vazio" }`
5. `widgetsJson` acima de 10.240 chars retorna `400` com `{ "message": "widgetsJson excede o limite de 10 KB" }`
6. `DELETE /api/v1/users/me/dashboard` remove `UserDashboardConfig` do usuário atual se existir (idempotente — retorna `204` mesmo se não existir); próxima chamada `GET` retornará o layout default do role
7. Entidade `UserDashboardConfig` em `common/domain/` com campos: `id` (UUID), `username` (String, `@Column(unique=true, nullable=false)`), `widgetsJson` (TEXT), `updatedAt` (LocalDateTime — atualizado a cada PUT via `@PreUpdate`)
8. Migration: tabela `user_dashboard_config` com constraint `UNIQUE(username)`
9. Use cases em `common/application/usecase/`: `GetDashboardConfigUseCase`, `SaveDashboardConfigUseCase`, `DeleteDashboardConfigUseCase`; `DashboardController` em `common/presentation/` com `@PreAuthorize("isAuthenticated()")` e `@Validated` (ADR-031)
10. Teste unitário `GetDashboardConfigUseCaseTest`: (a) usuário com config salva → retorna `widgetsJson` persistido; (b) usuário OPERATOR sem config → retorna default com 6 widgets; (c) usuário SUPERVISOR sem config → retorna default com 8 widgets; (d) PUT seguido de DELETE + GET → retorna default (sem resíduo da config deletada)

---

#### US-078 — Frontend de dashboard com widgets personalizáveis (5 pts)

**Carregamento e renderização**
1. Rota `/dashboard` (já existente) carrega layout via `GET /api/v1/users/me/dashboard` em `ngOnInit`; `widgetsJson` parseado como `WidgetConfig[]`; skeleton loader de 3 colunas exibido durante carregamento; erro de carregamento exibe snackbar persistente "Não foi possível carregar o layout do dashboard" sem bloquear o resto da página
2. `WidgetConfig` interface: `{ id: string; type: WidgetType; column: number; row: number; }` onde `WidgetType` é union type: `'oee-avg' | 'nc-open' | 'nc-critical' | 'wo-open' | 'mttr' | 'equipment-count' | 'oee-trend' | 'nc-pareto'`
3. Widgets disponíveis por role: OPERATOR (6): `oee-avg`, `nc-open`, `nc-critical`, `wo-open`, `mttr`, `equipment-count`; SUPERVISOR+ (8): os 6 do OPERATOR mais `oee-trend`, `nc-pareto` — widgets indisponíveis para o role não podem ser adicionados mesmo via manipulação de JSON
4. Cada widget é um standalone component com `ChangeDetectionStrategy.OnPush` que faz sua própria chamada HTTP via `ngOnInit`; falha de um widget exibe ícone de erro no card sem afetar os demais
5. Grid CSS 3 colunas via `display: grid; grid-template-columns: repeat(3, 1fr)`; posicionamento via `grid-column` e `grid-row` derivados de `WidgetConfig.column` e `WidgetConfig.row`

**Modo de edição**
6. Botão "Personalizar" no canto superior direito (visível para todos os usuários autenticados): ativa `editMode = signal(true)`; em edit mode cada widget exibe handle de drag (⠿) no canto superior esquerdo e botão "×" no canto superior direito
7. Drag-and-drop via HTML5 Drag-and-Drop API nativo: `draggable="true"` nos cards; eventos `dragstart`, `dragover`, `drop` gerenciados no `DashboardComponent` pai; ao soltar em nova posição, `WidgetConfig[]` é reordenado em memória e o grid re-renderiza via `@for (widget of widgetConfigs(); track widget.id)`
8. Catálogo lateral em edit mode (painel `position: fixed` à direita, 240px): lista de `WidgetType`s disponíveis para o role do usuário **não** presentes no dashboard atual; clique em widget do catálogo adiciona ao final do grid com `column = (widgets().length % 3) + 1`; catálogo atualiza reativamente via `computed()` com base em `widgetConfigs()`
9. Botão "Salvar Layout" (em edit mode): serializa `WidgetConfig[]` via `JSON.stringify()`, chama `PUT /api/v1/users/me/dashboard`; `isSaving = signal(true)` durante envio; após sucesso: `editMode.set(false)`, snackbar "Layout salvo com sucesso" (3s); em caso de erro: snackbar com mensagem da API
10. Botão "Resetar" (em edit mode): dialog de confirmação "Resetar para o layout padrão? As personalizações serão perdidas." (botão destrutivo vermelho + cancelar); confirmado: chama `DELETE /api/v1/users/me/dashboard`, depois `GET` para recarregar default; `editMode.set(false)` após recarga
11. Botão "×" por widget (edit mode): remove do `widgetConfigs()` signal; catálogo lateral reflete remoção imediatamente via `computed()`

**Estado e specs**
12. `DashboardComponent` com `ChangeDetectionStrategy.OnPush`; signals: `widgetConfigs = signal<WidgetConfig[]>([])`, `editMode = signal(false)`, `isLoading = signal(true)`, `isSaving = signal(false)`
13. Spec `dashboard.component.spec.ts`: (a) `GET` retorna layout → widgets renderizados na ordem correta; (b) clique em "Personalizar" → `editMode()` true, handles visíveis; (c) remover widget via "×" → widget some do grid, aparece no catálogo; (d) clicar em widget no catálogo → widget adicionado ao grid, some do catálogo; (e) salvar → `PUT` chamado com `widgetsJson` serializado, `editMode` volta para false; (f) resetar → `DELETE` + `GET` chamados, layout default restaurado; (g) widget individual com erro HTTP → card exibe ícone de erro sem afetar os demais

---

#### US-096 — Tech debt Sprints 26–27 — SEC-088/089/090/093/094/096/097/100 (3 pts)

> Liquida itens de segurança diferidos das revisões de Beatriz nos Sprints 26 e 27: 2 MEDIUM do PWA (SEC-088/089), 1 LOW PWA (SEC-090), 2 MEDIUM de webhooks (SEC-093/094), 1 LOW webhook semântico (SEC-096), 1 LOW webhook placeholder (SEC-097) e 1 INFO de consistência de entidade (SEC-100).

**SEC-088 — MEDIUM: Cache-Control: no-store no endpoint de listagem de NCs (PWA)**
1. No `QmsController` (ou em `SecurityHeadersFilter`), adicionar header `Cache-Control: no-store` ao response de `GET /api/v1/qms/non-conformances` — o campo `reportedBy` é dado pessoal LGPD e não deve ser cacheado pelo Service Worker em dispositivos compartilhados
2. O Service Worker Angular (`ngsw`) respeita `Cache-Control: no-store` no grupo `dataGroups` com strategy `freshness` — a resposta não será armazenada mesmo que a URL esteja configurada no `ngsw-config.json`
3. Teste de integração ou `MockMvc`: `GET /api/v1/qms/non-conformances` → response inclui header `Cache-Control: no-store`

**SEC-089 — MEDIUM: limpar IndexedDB offline_ncs no logout (PWA)**
4. Em `AuthService.logout()` (ou `auth.service.ts`): chamar `await offlineQueueService.clearAll()` antes de `localStorage.removeItem('msb_token')` e do redirect para `/login` — garante que NCs pendentes de outro usuário não persistam no IndexedDB após logout
5. `OfflineQueueService.clearAll(): Promise<void>`: abre transaction `readwrite` na store `offline_ncs` e executa `objectStore.clear()`; resolve a Promise no evento `onsuccess`
6. Spec `auth.service.spec.ts`: cenário "logout clears IndexedDB before localStorage" — verificar via spy que `offlineQueueService.clearAll()` é chamado antes de `localStorage.removeItem('msb_token')`

**SEC-090 — LOW: console.warn em OfflineSyncService (PWA)**
7. Em `OfflineSyncService.drainQueue()`: substituir `console.warn('NC pendente falhou após 3 tentativas:', entry.payload.title)` por `this.failedTitles.update(arr => [...arr, entry.payload.title.substring(0, 30)])` — signal `failedTitles = signal<string[]>([])` acumulado; `AppComponent` observa e exibe snackbar genérico "N NC(s) não puderam ser sincronizadas" sem expor título individual no console
8. Spec: cenário "drainQueue after 3 failures sets failedTitles signal without console.warn" — `spyOn(console, 'warn')` não deve ser chamado; `failedTitles()` deve ter 1 entrada

**SEC-093 — MEDIUM: sanitização de URL em logs de webhook + Lombok toString**
9. Em `WebhookDispatchService` (métodos que logam `sub.getUrl()`): substituir log direto por `sanitizeUrl(sub.getUrl())` — método privado `sanitizeUrl(String url)` remove credenciais embutidas: `url.replaceAll("(https?://)([^@]+@)", "$1***@")`
10. Em `WebhookSubscription.java`: adicionar `@ToString.Exclude` no campo `secret` — protege contra exposição acidental via Lombok `toString()` em logs de DEBUG do Hibernate
11. Teste unitário: `sanitizeUrl("https://user:pass@host.com/hook")` retorna `"https://***@host.com/hook"`; `sanitizeUrl("https://host.com/hook")` retorna inalterado

**SEC-094 — MEDIUM: sanitização de errorMessage em WebhookDelivery**
12. Em `WebhookDispatchService.handleFailure()`: substituir `e.getMessage()` por mensagem categorizada via método privado `categorizeError(Exception e)`:
    ```java
    private String categorizeError(Exception e) {
        if (e instanceof ConnectException) return "Connection error";
        if (e instanceof SocketTimeoutException) return "Timeout";
        if (e instanceof ResourceAccessException rae
                && rae.getCause() instanceof SocketTimeoutException) return "Timeout";
        if (e instanceof ResourceAccessException) return "Network error";
        return "HTTP error";
    }
    ```
13. `WebhookDelivery.errorMessage` persistido com `categorizeError(e)` — sem IPs/portas internas; sem stack traces
14. Teste unitário: `ConnectException` → `"Connection error"`; `SocketTimeoutException` → `"Timeout"`; `RuntimeException("algo interno")` → `"HTTP error"`

**SEC-096 — LOW: `UpdateWebhookRequest.url` sem limite mínimo**
15. Em `UpdateWebhookRequest.java`: adicionar `@Size(min = 1, max = 500)` em vez de apenas `@Size(max = 500)` para rejeitar string vazia `""` explicitamente — `null` continua aceito (PATCH semântico: `null` = sem alteração); adicionar JavaDoc explicitando essa semântica
16. Teste unitário: `UpdateWebhookRequest` com `url = ""` → `ConstraintViolationException`; com `url = null` → válido (sem alteração); com `url = "https://host.com"` → válido

**SEC-097 — LOW: `WebhookEvent.TEST` para entregas de teste**
17. Em `WebhookEvent` enum: adicionar `TEST` como último valor — representa entregas de teste manual, distinguindo-as de eventos reais de negócio no histórico de deliveries
18. Em `TestWebhookUseCase.execute()`: substituir `WebhookEvent.NC_CREATED` (placeholder hardcoded) por `WebhookEvent.TEST` ao criar a `WebhookDelivery` de teste
19. Frontend `webhooks.component.ts`: chip do evento `TEST` exibido com label "Teste Manual" e cor cinza; demais eventos seguem o mapeamento existente
20. Spec: "test delivery exibe evento TEST no histórico" — verificar que `WebhookDeliveryResponse.event == 'TEST'` após ação de teste

**SEC-100 — INFO: deliveries PENDING_RETRY órfãs ao reativar subscription**
21. Em `ActivateWebhookSubscriptionUseCase.execute()`: após `subscription.setActive(true)` e antes do `save()`, buscar `deliveryRepository.findBySubscriptionIdAndStatus(subscriptionId, "PENDING_RETRY")` e para cada uma: setar `errorMessage = "Retry cancelado: subscription reativada"` e `status = FAILED` — deliveries órfãs são encerradas explicitamente sem poluir o histórico com entregas "fantasma"
22. Teste unitário: cenário "activate subscription marks PENDING_RETRY deliveries as FAILED" — mock 2 deliveries PENDING_RETRY; verificar que ambas são salvas com `status = FAILED` e `errorMessage` correto

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
| ✅ Sprint 7 | Maintenance: equipment registration + work orders | US-027, US-028 | ADR-008 |
| ✅ Sprint 8 | Maintenance: MTTR + metrics | US-029 | ADR-008 |
| ✅ Sprint 9 | Cross-module KPI dashboard + weekly report | US-030, US-031 | ADR-009 |
| ✅ Sprint 10 | Audit trail, E2E (Playwright), health, performance | US-033, US-034, US-035, US-036 | ADR-009 |
| ✅ Sprint 11 | API Security Hardening (rate limiting, headers, CORS) | US-065, US-066 | ADR-021 |
| ✅ Sprint 12 | User management UI + self-service password | US-037, US-038, US-039 | ADR-010 |
| ✅ Sprint 13 | Análise de causa raiz — 5-Porquês em NCs | US-052, US-053 | ADR-015 |
| ✅ Sprint 14 | Gestão de fornecedores + score de qualidade | US-057, US-058 | ADR-017 |
| ✅ Sprint 15 | Preventive maintenance scheduling + calendar | US-040, US-041, US-042 | ADR-011 |
| ✅ Sprint 16 | Advanced analytics: OEE trend, NC pareto, MTTR trend + tech debt | US-043, US-044, US-045, US-059 | ADR-012, ADR-031 |
| ✅ Sprint 17 | Planned Downtime + correção residual BUG-2 (qms-analytics) | US-073, US-074, US-060 | ADR-025, ADR-032 |
| ✅ Sprint 18 | Tech debt Sprint 17 + threshold alerts + notificações in-app | US-088, US-046, US-047, US-048 | ADR-013, ADR-033 |
| ✅ Sprint 19 | Gestão de turnos + rastreabilidade por turno | US-054, US-055, US-056, US-089 | ADR-016, ADR-034 |
| ✅ Sprint 20 | Peças e insumos (spare parts inventory) | US-049, US-050, US-051, US-090 | ADR-014, ADR-035 |
| ✅ Sprint 21 | Anexos — upload de documentos e imagens + tech debt S20 | US-059, US-060 | ADR-018, ADR-036 |
| ✅ Sprint 22 | SLA e escalação automática + tech debt security S21 | US-061, US-062, US-091 | ADR-019, ADR-037 |
| ✅ Sprint 23 | Multi-plant support + tech debt SLA/email/shifts | US-063, US-064, US-092 | ADR-020, ADR-038 |
| ✅ Sprint 24 | OEE Benchmarking + tech debt multi-plant | US-075, US-076, US-093 | ADR-026, ADR-039 |
| ✅ Sprint 25 | LGPD compliance e data retention + tech debt benchmark OEE | US-067, US-068, US-094 | ADR-022, ADR-039 |
| ✅ Sprint 26 | Progressive Web App (PWA + offline queue) + tech debt LGPD/security | US-069, US-070, US-095 | ADR-023 |
| ✅ Sprint 27 | Outbound webhooks para integração com sistemas externos | US-071, US-072 | ADR-024, ADR-040 |
| ✅ Sprint 28 | Dashboard customizável por usuário (widgets drag-and-drop) | US-077, US-078, US-096 | ADR-027 |
| ✅ Sprint 29 | Production module: importação do Dynamics (produtos, estoque, OPs, tempos) + tech debt S28 | US-079, US-080, US-081, US-097 | ADR-028 |
| ✅ Sprint 30 | Acompanhamento visual de produção e tracking de OPs por família + tech debt S29 | US-082, US-083, US-098 | ADR-029, ADR-041 |
| ✅ Sprint 31 | Gestão de cargas de esterilização (Hub-managed) + tech debt S30 | US-084, US-099 | ADR-029, ADR-042 |
| ✅ Sprint 32 | Motor MRP, planejamento por família e staffing por OP + tech debt S31 | US-085, US-086, US-087, US-100 | ADR-030, ADR-043 |
| ✅ Sprint 33 | BOM import do Dynamics + relatório de planejamento + tech debt MRP | US-101, US-102, US-103 | ADR-044 |
| ✅ Sprint 34 | Painel executivo de produção + BOM nível 2 no MRP + tech debt S33 | US-104, US-105, US-106 | ADR-045 |
| ⬜ Sprint 35 | Cache Caffeine no painel executivo + gráfico NgxCharts + liquidação tech debt security | US-107, US-108, US-109 | ADR-046 |

---

## Sprint 29 ✅
**Objetivo**: Production module — importação de dados do Dynamics (catálogo de produtos, snapshots de estoque, ordens de produção, tempos de ciclo e lead times) + liquidação do tech debt de segurança diferido do Sprint 28
**ADR**: ADR-028
**Status**: concluída

### Tech Debt diferido do Sprint 28 (Beatriz — SEC-101 a SEC-106)

| ID | Severidade | Descrição | Fix |
|----|-----------|-----------|-----|
| SEC-101 | MEDIUM | `TestWebhookUseCase` usa `e.getMessage()` bruto no `errorMessage` (expõe em `WebhookTestResponse` e AuditLog) | Substituir por `categorizeError()` já existente no `WebhookDispatchService` |
| SEC-102 | MEDIUM | `widgetsJson` salvo sem validação de JSON válido no backend; `JSON.parse()` no frontend sem try-catch | Backend: validar com `objectMapper.readTree()` no use case; Frontend: envolver `JSON.parse()` em try-catch com fallback para layout padrão |
| SEC-103 | MEDIUM | `DashboardController.get()` deriva role via `stream().findFirst()` em vez do padrão `hasRole()` — inconsistência com outros controllers | Usar lógica explícita com `anyMatch("ROLE_ADMIN" || "ROLE_SUPERVISOR")` ou `@AuthenticationPrincipal UserDetails` |
| SEC-104 | LOW | `SaveDashboardConfigUseCase` e `DeleteDashboardConfigUseCase` sem auditoria | Adicionar `DASHBOARD_CONFIG_SAVED`, `DASHBOARD_CONFIG_RESET` ao `AuditAction`; chamar `auditService.log()` nos dois use cases |
| SEC-105 | LOW | `failedTitles` signal em `OfflineSyncService` acumula sem limite e não é limpo no logout | Limitar array com `.slice(-10)`; limpar signal no logout via `offlineSyncService.failedTitles.set([])` |
| SEC-106 | INFO | `WebhookUrlValidator.validate()` tem resolução DNS bloqueante na thread do use case (pode causar lentidão com DNS lento) | Envolver `InetAddress.getByName()` com `ExecutorService.submit().get(2, TimeUnit.SECONDS)` para timeout explícito |

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-079 | Importação de catálogo de produtos e famílias do Dynamics | 4 | ✅ concluído |
| US-080 | Importação de snapshots de estoque e ordens de produção | 4 | ✅ concluído |
| US-081 | Importação de tempos de ciclo e lead times + histórico de importações | 3 | ✅ concluído |
| US-097 | Tech debt Sprint 28 — SEC-101 a SEC-106 (dashboard JSON, auditoria, webhooks) | 2 | ✅ concluído |

**Total**: 13 pontos

### Dependências
- US-079 deve ser entregue antes de US-080 (importação de OPs exige `dynamics_code` existente no catálogo de produtos)
- US-079 deve ser entregue antes de US-081 (importação de tempos de ciclo referencia `Product` por `dynamics_code`)
- US-097 é independente das demais — pode ser desenvolvida em paralelo

---

#### US-079 — Importação de catálogo de produtos e famílias (4 pts)

> Cria o módulo `production/` do zero (pacotes, entidades, migrations) e implementa o pipeline de importação do catálogo de produtos do Dynamics — base obrigatória para todas as outras importações da sprint.

**Backend — Módulo e entidades**
1. Pacote `production/` criado no caminho `com.industrialhub.backend.production/` com subpacotes `domain/`, `application/dto/`, `application/usecase/`, `infrastructure/`, `presentation/` conforme ADR-028 Decisão 1; nenhum código de negócio em outros módulos referencia `production/` diretamente — dependências cruzadas ocorrem apenas via `common/`
2. Entidade `ProductFamily` criada em `production/domain/` com campos `id` (UUID), `code` (unique, max 50), `name` (max 200), `active` (default `true`); migration `V{N}__product_family.sql` cria tabela `product_family` com índice único em `code`
3. Entidade `Product` criada em `production/domain/` com campos conforme ADR-028 Decisão 3: `id` (UUID), `dynamicsCode` (unique, max 100), `name` (max 200), `type` (`ProductType` enum: `FINISHED`, `INTERMEDIATE`, `RAW_MATERIAL`), `family` (`@ManyToOne LAZY`, nullable para `RAW_MATERIAL`), `unit` (nullable), `leadTimeDays` (nullable), `minStockQty` (nullable), `batchSize` (nullable), `requiresSterilization` (boolean), `active` (default `true`), `lastSyncAt` (`LocalDateTime`); migration `V{N}__product.sql` com índices em `dynamics_code` (unique), `family_id` e `type`
4. Entidade `ImportProductionBatch` criada em `production/domain/` com campos: `id` (UUID), `type` (`ProductionImportType` enum: `PRODUCT_CATALOG`, `STOCK`, `PRODUCTION_ORDERS`, `CYCLE_TIMES`, `LEAD_TIMES`), `fileName`, `importedAt`, `importedBy`, `totalRecords`, `createdRecords`, `updatedRecords`, `errorRecords`; migration `V{N}__import_production_batch.sql`

**Backend — Importação**
5. `POST /api/v1/production/import/products` (multipart/form-data, ADMIN) aceita arquivo Excel com colunas `dynamics_code`, `name`, `type`, `family_code`, `family_name`, `unit`, `requires_sterilization`; retorna `201 ImportProductionBatchResponse` com todos os contadores (`createdRecords`, `updatedRecords`, `errorRecords`) e lista `errors` descrevendo linhas com problema (ex: `{ "line": 3, "message": "type inválido: INVALID_TYPE" }`)
6. Upsert por `dynamics_code`: produto existente tem `name`, `type`, `unit`, `requiresSterilization` e `lastSyncAt` atualizados; campos gerenciados pelo Hub (`leadTimeDays`, `minStockQty`, `batchSize`) **não** são sobrescritos na importação de catálogo — preservados entre importações; produto novo é criado com todos os campos da linha; `ProductFamily` criada automaticamente quando `family_code` é novo (inserção atômica — upsert da família antes do produto); `family` pode ser null para `type = RAW_MATERIAL` mesmo com `family_code` na planilha (campo ignorado)
7. Linha com `type` inválido ou `dynamics_code` ausente/nulo é registrada em `errorRecords` e incluída na lista `errors`; a importação **não é abortada** por linhas com erro — linhas válidas são processadas normalmente; `errorRecords = 0` indica sucesso total
8. `ImportProductCatalogUseCase` em `production/application/usecase/`; usa `ProductFamilyRepository.findByCode()` + upsert com `ProductRepository.findByDynamicsCode()`; todo o processamento em **única transação** — falha total da transação em caso de erro inesperado de banco (não por erro de linha individual)
9. Auditoria: `AuditAction` enum recebe `PRODUCTION_IMPORT`; `ImportProductCatalogUseCase` chama `auditService.log(username, PRODUCTION_IMPORT, "ImportProductionBatch", batchId, Map.of("type", "PRODUCT_CATALOG", "created", N, "updated", M, "errors", E))`
10. Teste unitário `ImportProductCatalogUseCaseTest`: (a) planilha com 2 produtos novos e 1 produto existente → `createdRecords=2`, `updatedRecords=1`; (b) família nova criada automaticamente se `family_code` não existe no banco; (c) linha com `type` inválido → `errorRecords=1`, linha válida da mesma planilha processada; (d) `dynamics_code` duplicado na mesma planilha → segunda ocorrência conta como `updatedRecords`; (e) campos Hub (`leadTimeDays`, `minStockQty`) de produto existente não são sobrescritos

**Backend — Leitura**
11. `GET /api/v1/production/families` retorna `List<ProductFamilyResponse>` com famílias ativas ordenadas por `name ASC` (OPERATOR+); `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")`
12. `GET /api/v1/production/products` retorna `Page<ProductSummaryResponse>` (OPERATOR+); filtros opcionais: `?familyCode=<string>`, `?type=<FINISHED|INTERMEDIATE|RAW_MATERIAL>`, `?active=<boolean>` (default: apenas ativos); `@PageableDefault(size=20)`; projeção de interface para evitar carregar `family` completo — campos: `id`, `dynamicsCode`, `name`, `type`, `familyName` (nullable), `unit`, `requiresSterilization`, `active`, `lastSyncAt`
13. `GET /api/v1/production/products/{id}` retorna `ProductDetailResponse` completo (OPERATOR+): todos os campos da entidade + `currentStockQty` (resultado de `StockSnapshotRepository.findTopByProductIdOrderBySnapshotDateDesc().getQty()` — null se nenhum snapshot) + `currentCycleTimeSeconds` (resultado de `CycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc().getSecondsPerUnit()` — null se nenhum ciclo); produto inexistente retorna `404`

**Frontend**
14. Módulo `production/` criado em `apps/frontend/src/app/production/`; rota lazy-loaded `/production` adicionada ao `app.routes.ts`; link "Produção" adicionado ao `NavComponent` visível para OPERATOR+
15. Rota `/production/import` (ADMIN, lazy-loaded) exibe página com `MatTabGroup`; primeira aba "Produtos": área drag-and-drop de arquivo Excel (componente `MatCard` com `(dragover)` e `(drop)`), botão "Selecionar arquivo" alternativo, nome do arquivo selecionado exibido; botão "Importar" desabilitado enquanto nenhum arquivo selecionado ou `isImporting()=true`; `MatProgressBar indeterminate` durante importação
16. Resultado da importação exibido em card abaixo do upload: contadores `Criados: N`, `Atualizados: M`, `Erros: E`; quando `errors.length > 0`, tabela expansível exibe `line` e `message` de cada erro; card não aparece antes da primeira importação (`hasResult = signal(false)`)
17. Rota `/production/products` (OPERATOR+, lazy-loaded) exibe tabela com colunas: código Dynamics, nome, família, tipo (chip colorido: FINISHED=`#0099B8`, INTERMEDIATE=`#F59E0B`, RAW_MATERIAL=`#9CA3AF`), unidade, esterilização (ícone `check` verde se `requiresSterilization=true`); paginação `MatPaginator` com `pageSize=20`; filtros: input de busca por código/nome (debounce 300ms), select de família, select de tipo; skeleton loader de 5 linhas enquanto `isLoading()=true`
18. Clique na linha navega para `/production/products/{id}` — página de detalhe com: todos os campos do produto, card "Estoque Atual" (quantidade ou "Sem snapshot" se null), card "Tempo de Ciclo" (`secondsPerUnit` formatado como "X s/un" ou "Não importado" se null), chip de status ativo/inativo
19. `ChangeDetectionStrategy.OnPush`, standalone, signals; `ProductionImportService` com métodos `importProducts(file: File): Observable<ImportBatchResponse>`, `listFamilies(): Observable<ProductFamilyResponse[]>`, `listProducts(params): Observable<Page<ProductSummaryResponse>>`, `getProduct(id): Observable<ProductDetailResponse>`
20. Spec `product-import.component.spec.ts`: (a) botão "Importar" desabilitado enquanto nenhum arquivo selecionado; (b) após importação com sucesso, card de resultado exibe `createdRecords` e `updatedRecords`; (c) quando `errorRecords > 0`, tabela de erros é exibida; (d) erro HTTP exibe snackbar com mensagem da API

---

#### US-080 — Importação de snapshots de estoque e ordens de produção (4 pts)

> Depende de US-079 (entidade `Product` e `ProductFamilyRepository` precisam existir).

**Backend — Estoque**
1. Entidade `StockSnapshot` criada em `production/domain/` com campos: `id` (UUID), `product` (`@ManyToOne LAZY`), `qty` (Integer), `snapshotDate` (LocalDate), `importedAt` (LocalDateTime), `importedBy` (String), `importBatch` (`@ManyToOne LAZY`); migration `V{N}__stock_snapshot.sql` com índices em `product_id` e `snapshot_date` (conforme ADR-028 Decisão 5)
2. `POST /api/v1/production/import/stock` (multipart, SUPERVISOR+) aceita planilha com colunas `dynamics_code`, `qty`, `snapshot_date`; upsert por par `(product, snapshotDate)` — se já existe snapshot para o mesmo produto e mesma data, atualiza `qty`; datas diferentes geram **novo** registro (histórico preservado); retorna `201 ImportProductionBatchResponse` com contadores
3. `dynamics_code` não encontrado no catálogo → linha reportada em `errorRecords` e `errors` (não bloqueia restante); `qty` negativo → linha reportada como erro; `snapshot_date` ausente → linha reportada como erro
4. `GET /api/v1/production/stock` (SUPERVISOR+) retorna `List<StockPositionResponse>` com **posição mais recente** por produto: `productId`, `productCode`, `productName`, `familyCode`, `currentQty`, `minStockQty` (campo do `Product`), `belowMin` (boolean calculado: `currentQty < minStockQty`), `lastSnapshotDate`; calculado via `StockSnapshotRepository.findLatestPerProduct()` (JPQL com `GROUP BY product_id` + `MAX(snapshot_date)`) — sem N+1; filtro opcional `?belowMin=true`
5. Teste unitário `ImportStockSnapshotUseCaseTest`: (a) produto + data nova → `createdRecords=1`; (b) produto + mesma data já existente → `updatedRecords=1` (qty atualizado, snapshot_date inalterado); (c) produto + data diferente → `createdRecords=1` (segundo registro criado, histórico preservado); (d) `dynamics_code` inexistente → `errorRecords=1`, processamento continua

**Backend — Ordens de Produção**
6. Entidade `ProductionOrder` criada em `production/domain/` conforme ADR-028 Decisão 4: `id` (UUID), `dynamicsOrderNumber` (unique, max 50), `product` (`@ManyToOne LAZY`), `family` (`@ManyToOne LAZY`, desnormalizado), `status` (`ProductionOrderStatus` enum: `PLANNED`, `RELEASED`, `IN_PROGRESS`, `DONE`, `CANCELLED`), `plannedQty`, `producedQty`, `startDate`, `dueDate`, `importBatch` (`@ManyToOne LAZY`), `sterilizationLoad` (nullable — campos Hub preservados), `plannedPeople` (nullable), `peopleOverridden` (boolean); migration `V{N}__production_order.sql` com índices em `dynamics_order_number` (unique), `product_id`, `family_id`, `status`, `due_date`
7. `POST /api/v1/production/import/production-orders` (multipart, SUPERVISOR+) aceita planilha com colunas `op_number`, `dynamics_code`, `status`, `planned_qty`, `produced_qty`, `start_date`, `due_date`; upsert por `dynamicsOrderNumber` — campos do Dynamics atualizados: `status`, `producedQty`, `plannedQty`, `dueDate`, `startDate`; campos Hub **preservados** sem modificação: `sterilizationLoad`, `plannedPeople`, `peopleOverridden` (conforme ADR-028 Decisão 4 regra de upsert)
8. `dynamics_code` não encontrado no catálogo → OP reportada em `errorRecords`; `status` com valor inválido → linha reportada como erro; OP nova com `dynamicsOrderNumber` novo → `createdRecords++`; OP existente (upsert) → `updatedRecords++`
9. `GET /api/v1/production/production-orders` (OPERATOR+) retorna `Page<ProductionOrderSummaryResponse>` com `@PageableDefault(size=20, sort="dueDate")`; filtros opcionais: `?familyCode=<string>`, `?status=<enum>`, `?productType=<FINISHED|INTERMEDIATE|RAW_MATERIAL>`, `?overdue=true` (filtra OPs com `dueDate < today AND status NOT IN ('DONE','CANCELLED')`); campos retornados: `id`, `dynamicsOrderNumber`, `productName`, `familyCode`, `status`, `plannedQty`, `producedQty`, `completionPct` (calculado: `producedQty / plannedQty * 100`, null se `plannedQty=0`), `dueDate`, `overdue` (boolean)
10. Teste unitário `ImportProductionOrdersUseCaseTest`: (a) OP nova → `createdRecords=1`, todos os campos persistidos corretamente; (b) OP existente com upsert → `updatedRecords=1`, `sterilizationLoad` e `plannedPeople` preservados, `status` Dynamics atualizado; (c) `dynamics_code` inexistente → `errorRecords=1`; (d) planilha com OPs de diferentes produtos, uma com `dynamics_code` inválido → processa válidas, reporta inválida; (e) importação com 0 erros → `errorRecords=0`, `errors=[]`

**Frontend**
11. Aba "Estoque" na página `/production/import`: mesmo padrão de upload da aba "Produtos" — drag-and-drop + botão + resultado; card de resultado exibe contadores e erros expansíveis
12. Aba "Ordens de Produção" na página `/production/import`: mesmo padrão; resultado adicional: chip informativo "X OPs preservaram dados de esterilização" quando `updatedRecords > 0` (texto informacional sem ação — lembra o usuário que os dados Hub foram preservados)
13. Rota `/production/production-orders` (OPERATOR+, lazy-loaded) exibe tabela com colunas: número da OP, produto, família, status (chip colorido: PLANNED=cinza, RELEASED=azul-claro, IN_PROGRESS=`#0099B8`, DONE=verde, CANCELLED=cinza escuro), planejado vs. produzido, barra de progresso `completionPct`, prazo (vermelho + ícone `warning` se `overdue=true`); filtros: família (select), status (select), tipo de produto (select), toggle "Apenas atrasadas"; paginação com `pageSize=20`
14. Card informativo no topo da rota `/production/production-orders` exibe data da última importação de OPs (`lastSyncAt` da última `ImportProductionBatch` de tipo `PRODUCTION_ORDERS`) com texto "Última sincronização com Dynamics: {data formatada}"; exibe "Nunca sincronizado" se sem importação anterior
15. Rota `/production/stock` (SUPERVISOR+, lazy-loaded) exibe tabela com colunas: produto, família, estoque atual, mínimo, status (chip "OK" verde ou "Abaixo do mínimo" vermelho baseado em `belowMin`), data do snapshot; toggle "Apenas abaixo do mínimo" acima da tabela
16. `ChangeDetectionStrategy.OnPush`, standalone, signals; spec `production-orders-list.component.spec.ts`: (a) OPs com `overdue=true` exibem data vermelha; (b) toggle "Apenas atrasadas" reconsulta API com `?overdue=true`; (c) card de última sincronização exibe data correta quando `lastSyncAt` está presente; (d) tabela exibe skeleton loader durante carregamento

---

#### US-081 — Importação de tempos de ciclo e lead times + histórico (3 pts)

> Depende de US-079 (entidade `Product` precisa existir). Completam os 5 tipos de importação do ADR-028.

**Backend — Tempos de Ciclo**
1. Entidade `CycleTime` criada em `production/domain/` conforme ADR-028 Decisão 6: `id` (UUID), `product` (`@ManyToOne LAZY`), `secondsPerUnit` (Double), `effectiveDate` (LocalDate), `importedBy`, `importedAt`; constraint única em `(product_id, effective_date)` — migration `V{N}__cycle_time.sql`
2. `POST /api/v1/production/import/cycle-times` (multipart, ADMIN) aceita planilha com colunas `dynamics_code`, `seconds_per_unit`, `effective_date`; por produto: se já existe `CycleTime` com a mesma `effectiveDate` → atualiza `secondsPerUnit` (`updatedRecords++`); se `effectiveDate` é nova → cria registro (`createdRecords++`), preservando histórico; se `dynamics_code` não existe → `errorRecords++`; `seconds_per_unit <= 0` → `errorRecords++`
3. `GET /api/v1/production/products/{id}` (já em US-079 AC#13) já expõe `currentCycleTimeSeconds`; adicionalmente, `GET /api/v1/production/products/{id}/cycle-times` retorna `List<CycleTimeResponse>` com todo o histórico de tempos de ciclo do produto ordenados por `effectiveDate DESC` (OPERATOR+)
4. Teste unitário `ImportCycleTimesUseCaseTest`: (a) produto com nova `effectiveDate` → `createdRecords=1` (histórico do ciclo anterior preservado); (b) produto + mesma `effectiveDate` com novo valor → `updatedRecords=1`; (c) `dynamics_code` inexistente → `errorRecords=1`; (d) `seconds_per_unit=0` → `errorRecords=1`; (e) produto com 2 ciclos importados em datas diferentes → `findTopByProductIdOrderByEffectiveDateDesc()` retorna o mais recente

**Backend — Lead Times**
5. `POST /api/v1/production/import/lead-times` (multipart, ADMIN) aceita planilha com colunas `dynamics_code`, `lead_time_days`, `min_stock_qty`, `batch_size`; **atualiza** campos `leadTimeDays`, `minStockQty`, `batchSize` do `Product` existente — não cria nova entidade (esses campos são do `Product` conforme ADR-028 Decisão 3); se `dynamics_code` não existe → `errorRecords++`; campos com valor nulo na planilha são ignorados (campos Hub não são zerados por planilha vazia); retorna `ImportProductionBatchResponse` com contadores; todos os registros atualizados contam como `updatedRecords`
6. Teste unitário `ImportLeadTimesUseCaseTest`: (a) produto existente → `leadTimeDays`, `minStockQty`, `batchSize` atualizados, `updatedRecords=1`; (b) campo nulo na planilha → campo no `Product` não alterado; (c) `dynamics_code` inexistente → `errorRecords=1`; (d) `lead_time_days < 0` → `errorRecords=1` (validação de valor negativo)

**Backend — Histórico de Importações**
7. `GET /api/v1/production/import/history` (SUPERVISOR+) retorna `Page<ImportProductionBatchResponse>` com `@PageableDefault(size=20, sort="importedAt", direction=DESC)`; filtros opcionais: `?type=<ProductionImportType>`; campos: `id`, `type`, `fileName`, `importedAt`, `importedBy`, `totalRecords`, `createdRecords`, `updatedRecords`, `errorRecords`
8. `GET /api/v1/production/import/history/{id}` (SUPERVISOR+) retorna detalhe do batch incluindo `errors` completo — campo `errors` não exposto na listagem paginada para não sobrecarregar o response; batch inexistente retorna `404`

**Frontend**
9. Aba "Tempos de Ciclo" na página `/production/import`: área de upload + card de resultado; abaixo do resultado, tabela "Histórico de ciclos importados nesta sessão" exibida após importação bem-sucedida mostrando produto, data de vigência, segundos/unidade dos registros do batch atual
10. Aba "Lead Times" na página `/production/import`: área de upload + card de resultado; abaixo, tabela dos produtos atualizados no último import com nome, lead time, estoque mínimo, tamanho de lote
11. Aba "Histórico" na página `/production/import` (SUPERVISOR+): tabela paginada com colunas tipo (chip), arquivo, data, usuário, criados, atualizados, erros (chip vermelho se `errorRecords > 0`); paginação `MatPaginator`; filtro por tipo via select acima da tabela; clique na linha expande painel abaixo com lista de erros do batch (chamada a `GET .../history/{id}`)
12. Painel de detalhe do produto (`/production/products/{id}`, US-079 AC#18) ganha seção "Histórico de Tempos de Ciclo": tabela com colunas "Vigente a partir de" e "Segundos por unidade", ordenada por data desc; exibe até 5 registros com link "Ver todos" oculto se ≤ 5
13. Spec `production-import.component.spec.ts`: (a) aba "Histórico" exibe chip vermelho em batch com `errorRecords > 0`; (b) clique no batch chama `getImportBatch(id)` e exibe erros no painel; (c) aba "Histórico" filtrada por tipo "STOCK" oculta batches de outros tipos; (d) paginação com `totalElements > 20` exibe `MatPaginator` ativo

---

#### US-097 — Tech debt Sprint 28 — SEC-101 a SEC-106 (2 pts)

> Liquida os 6 itens de segurança diferidos da revisão de Beatriz no Sprint 28: 3 MEDIUM (SEC-101, SEC-102, SEC-103), 2 LOW (SEC-104, SEC-105) e 1 INFO (SEC-106). Todos são cirúrgicos e independentes entre si, podem ser desenvolvidos em paralelo com as feature USs.

**SEC-101 — MEDIUM: `TestWebhookUseCase` expõe `e.getMessage()` bruto**
1. Em `TestWebhookUseCase.execute()`: substituir `e.getMessage()` por chamada ao método `categorizeError(Exception e)` já existente em `WebhookDispatchService` (ou extraí-lo para classe utilitária `WebhookErrorCategorizer` em `common/application/` se `WebhookDispatchService` não for injetável no contexto do use case de teste); `WebhookTestResponse.errorMessage` jamais expõe stack trace, IP, porta ou mensagem interna; no `AuditLog`, o campo `details` também deve usar a mensagem categorizada
2. Teste unitário: `TestWebhookUseCase` com endpoint que lança `ConnectException` → `WebhookTestResponse.errorMessage` retorna `"Connection error"` (não a mensagem raw da exceção); `RuntimeException("internal db error")` → `"HTTP error"`

**SEC-102 — MEDIUM: `widgetsJson` sem validação de JSON no backend + `JSON.parse` sem try-catch no frontend**
3. Em `SaveDashboardConfigUseCase.execute()`: antes do `save()`, validar que `widgetsJson` é JSON array válido com `objectMapper.readTree(widgetsJson)` — se lançar `JsonProcessingException`, retornar `400` com `{ "message": "widgetsJson contém JSON inválido" }`; `ObjectMapper` injetado via construtor (já disponível no contexto Spring)
4. Teste unitário `SaveDashboardConfigUseCaseTest`: (a) `widgetsJson = "[{\"id\":\"a\"}]"` → salvo com sucesso; (b) `widgetsJson = "not json"` → `IllegalArgumentException` mapeada para `400`; (c) `widgetsJson = "{}"` (objeto, não array) → pode ser aceito (ADR-027 não restringe a array) — não rejeitar, apenas validar sintaxe JSON
5. Em `dashboard.component.ts`: envolver todo uso de `JSON.parse(widgetsJson)` em `try-catch`; bloco `catch`: `this.widgetConfigs.set(this.defaultLayout())` + snackbar "Layout salvo corrompido, restaurando padrão" (3s); método `defaultLayout()` já existe (calcula layout default baseado no role) — reutilizar sem duplicação
6. Spec `dashboard.component.spec.ts`: cenário "JSON malformado retornado pelo backend → defaultLayout é usado e snackbar exibido" — mock do `GET /api/v1/users/me/dashboard` retornando `widgetsJson = "{{invalid"` → `widgetConfigs()` iguais ao default; snackbar visível

**SEC-103 — MEDIUM: derivação de role inconsistente em `DashboardController.get()`**
7. Em `DashboardController.get()`: substituir `authentication.getAuthorities().stream().findFirst()...` por uso de `@AuthenticationPrincipal UserDetails userDetails` injetado no método handler e verificação explícita: `boolean isSupervisorOrAdmin = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR") || a.getAuthority().equals("ROLE_ADMIN"))`; consistente com o padrão `hasAnyRole()` usado nos `@PreAuthorize` de outros controllers
8. Teste de integração `DashboardControllerTest` (ou `MockMvc`): (a) usuário ADMIN sem config salva → layout default de 8 widgets retornado; (b) usuário OPERATOR sem config salva → layout default de 6 widgets retornado; (c) usuário com config salva → `widgetsJson` persistido retornado independente do role

**SEC-104 — LOW: `SaveDashboardConfigUseCase` e `DeleteDashboardConfigUseCase` sem auditoria**
9. `AuditAction` enum: adicionar constantes `DASHBOARD_CONFIG_SAVED` e `DASHBOARD_CONFIG_RESET`
10. `SaveDashboardConfigUseCase.execute()`: após `save()`, chamar `auditService.log(username, DASHBOARD_CONFIG_SAVED, "UserDashboardConfig", config.getId().toString(), Map.of("widgetCount", widgetCount))`; `widgetCount` derivado do tamanho do array `widgetsJson` (parse já feito no AC#3 acima — reutilizar sem segundo parse)
11. `DeleteDashboardConfigUseCase.execute()`: após `delete()` (idempotente — logar apenas quando config existia): `auditService.log(username, DASHBOARD_CONFIG_RESET, "UserDashboardConfig", username, Map.of())`; quando config não existia (idempotente), não chamar `auditService` (sem log desnecessário)
12. Teste unitário: (a) `SaveDashboardConfigUseCaseTest` — `verify(auditService).log(eq(DASHBOARD_CONFIG_SAVED), ...)` no happy path; (b) `DeleteDashboardConfigUseCaseTest` — `verify(auditService).log(eq(DASHBOARD_CONFIG_RESET), ...)` quando config existia; `verify(auditService, never()).log(...)` quando config não existia

**SEC-105 — LOW: `failedTitles` signal acumula sem limite e não é limpo no logout**
13. Em `OfflineSyncService.drainQueue()`: substituir `.update(arr => [...arr, entry.payload.title.substring(0, 30)])` por `.update(arr => [...arr, entry.payload.title.substring(0, 30)].slice(-10))` — mantém no máximo os últimos 10 títulos; `.slice(-10)` é O(n) sobre array ≤ 10+1 — custo desprezível
14. Em `AuthService.logout()`: após `await offlineQueueService.clearAll()` (já garantido em SEC-089 de US-096 Sprint 28), adicionar `this.offlineSyncService.failedTitles.set([])` — limpa o signal ao deslogar; garantir que `OfflineSyncService` é injetável em `AuthService` (sem dependência circular: `AuthService` → `OfflineSyncService`, verificar que `OfflineSyncService` não injeta `AuthService`)
15. Spec `offline-sync.service.spec.ts`: (a) 15 falhas consecutivas → `failedTitles().length === 10` (limite respeitado); (b) logout → `failedTitles()` vazio após chamada a `logout()`

**SEC-106 — INFO: resolução DNS bloqueante em `WebhookUrlValidator`**
16. Em `WebhookUrlValidator.validate()`: substituir a chamada direta `InetAddress.getByName(host)` por execução via `ExecutorService.submit(() -> InetAddress.getByName(host)).get(2, TimeUnit.SECONDS)`; usar `executorService` injetável (via construtor ou campo `@Value`-configurável) com `Executors.newCachedThreadPool()` como default — permite substituição por executor mockado nos testes; capturar `TimeoutException` e `InterruptedException` como resultado `false` (URL inválida); limitar o overhead: `ExecutorService` é compartilhado (não criar um novo por chamada)
17. Teste unitário `WebhookUrlValidatorTest`: (a) `InetAddress.getByName` leva 3s (simulado via mock que dorme) → `validate()` retorna `false` em ≤ 2,5s; (b) host inexistente → `validate()` retorna `false` sem lançar exceção; (c) URL válida com host resolvível → `validate()` retorna `true` dentro do timeout

---

## Sprint 30 ✅
**Objetivo**: Acompanhamento visual de produção — tracking de OPs em kanban por família + liquidação do tech debt de segurança do Sprint 29 (SEC-107 a SEC-111)
**ADR**: ADR-029, ADR-041
**Status**: concluída
**Pontos totais**: 11 pts (US-082: 3 + US-083: 5 + US-098: 3)

### Tech Debt diferido do Sprint 29 (Beatriz — SEC-107 a SEC-111)

| ID | Severidade | Descrição | Fix |
|----|-----------|-----------|-----|
| SEC-107 | HIGH | Endpoints de import Excel sem validação de MIME type via magic bytes (Tika); ZIP bomb mitigado pelo POI default mas limites não configurados explicitamente | Chamar `ExcelFileValidator` (já criado) antes de `WorkbookFactory.create()`; verificar MIME `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` / `vnd.ms-excel`; lançar `InvalidFileTypeException` com 415 se inválido |
| SEC-108 | MEDIUM | `e.getMessage()` de exceções inesperadas exposto diretamente em `ImportErrorDto.message` e retornado ao chamador via HTTP 201 nos 5 use cases de import | Substituir `e.getMessage()` por mensagem genérica `"Erro ao processar linha"` no catch genérico; logar detalhes apenas via `log.warn()` |
| SEC-109 | MEDIUM | `ImportProductionBatchResponse` expõe `importedBy` (username) no endpoint `GET /api/v1/production/import/history` acessível a SUPERVISOR+; inconsistente com o padrão do `AuditLog` (ADMIN-only) | Decisão de produto necessária: (a) restringir `/import/history` a ADMIN-only, ou (b) omitir `importedBy` do response para SUPERVISOR |
| SEC-110 | LOW | `OfflineSyncService.clearFailedTitles()` implementado mas não chamado no logout; `failedTitles` signal persiste entre sessões no mesmo tab | Chamar `offlineSyncService.clearFailedTitles()` em `AuthService.logout()`, analogamente ao `offlineQueue.clearAll()` já presente (SEC-089) |
| SEC-111 | INFO | `ProductionService` (frontend) usa URLs `/products/import`, `/stock/import`, `/orders/import` mas o controller backend mapeia `/import/products`, `/import/stock`, `/import/production-orders` — chamadas de import retornam 404 | Corrigir URLs em `production.service.ts` para alinhar com o controller |

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-082 | Backend de tracking visual por família (display status calculado) | 3 | ✅ concluído |
| US-083 | Frontend de acompanhamento — kanban de OPs por família e status | 5 | ✅ concluído |
| US-098 | Tech debt Sprint 29 — SEC-107 a SEC-111 (validação MIME, sanitização de erros, importedBy, logout, URLs) | 3 | ✅ concluído |

### Dependências
- US-082 é pré-requisito bloqueante de US-083 (frontend consome os endpoints de tracking)
- US-098 é totalmente independente — pode ser desenvolvida em paralelo desde o início da sprint
- US-083 depende do enum `ProductionOrderDisplayStatus` definido em US-082 para tipagem do frontend

### Sequência de entrega sugerida
1. US-098 backend (SEC-107/108/109 — fixes cirúrgicos nos use cases de import, sem novas entidades)
2. US-082 backend (enum `ProductionOrderDisplayStatus`, use cases, controller, testes)
3. US-098 frontend (SEC-110/111 — `AuthService.logout` + URLs do `ProductionService`)
4. US-083 frontend (kanban completo com componente de card, painel lateral, filtros, spec)

---

#### US-082 — Backend de tracking visual por família (3 pts)

**Contexto**: Expõe os dados de OPs agrupados por família com o `ProductionOrderDisplayStatus` calculado em Java (nunca persistido), conforme ADR-029 Decisão 3. Pré-requisito direto para o kanban do US-083.

**Backend**
1. Enum `ProductionOrderDisplayStatus` criado em `production/domain/` com os valores: `PLANNED`, `RELEASED`, `IN_PROGRESS`, `PENDING_STERILIZATION`, `IN_LOAD`, `STERILIZING`, `DONE` — cada valor corresponde à lógica: `PLANNED` = Dynamics PLANNED; `RELEASED` = Dynamics RELEASED; `IN_PROGRESS` = Dynamics IN_PROGRESS; `PENDING_STERILIZATION` = Dynamics DONE + `requiresSterilization=true` + `sterilizationLoad=null`; `IN_LOAD` = Dynamics DONE + `requiresSterilization=true` + carga em status OPEN ou CLOSED; `STERILIZING` = Dynamics DONE + carga STERILIZING; `DONE` = Dynamics DONE + (`requiresSterilization=false` OU carga RELEASED)
2. `GetProductionTrackingUseCase` em `production/application/usecase/`: método `execute()` que busca todas as OPs ativas (não canceladas), calcula `displayStatus` por OP em Java via método privado `resolveDisplayStatus(ProductionOrder op)`, e retorna agrupado por família
3. `GET /api/v1/production/tracking/families` (OPERATOR+): retorna `List<FamilyTrackingResponse>` — cada família contém somente OPs com `displayStatus != DONE` e `status != CANCELLED`; famílias sem OPs ativas são omitidas do response
4. `FamilyTrackingResponse` record: `familyId` (UUID), `familyCode` (String), `familyName` (String), `overdueCount` (int — OPs com `overdue=true`), `orders` (List<OrderTrackingEntry>)
5. `OrderTrackingEntry` record: `dynamicsOrderNumber` (String), `productCode` (String), `productName` (String), `productType` (ProductType enum), `plannedQty` (Integer), `producedQty` (Integer), `completionPct` (Double — `producedQty / plannedQty * 100`, null se `plannedQty == 0 ou null`), `dueDate` (LocalDate), `overdue` (boolean — `dueDate != null && dueDate.isBefore(LocalDate.now()) && displayStatus != DONE`), `displayStatus` (ProductionOrderDisplayStatus), `loadNumber` (String nullable), `loadStatus` (LoadStatus nullable), `plannedPeople` (Integer nullable)
6. `GET /api/v1/production/tracking/orders` (OPERATOR+): lista flat de OPs com filtros opcionais via `@RequestParam`: `familyCode` (String), `displayStatus` (ProductionOrderDisplayStatus), `overdue` (boolean), `productType` (ProductType); retorna `List<OrderTrackingEntry>` sem paginação (volume controlado: 53 usuários, OPs abertas tipicamente < 200); controller com `@Validated`
7. `GET /api/v1/production/tracking/summary` (OPERATOR+): retorna `ProductionTrackingSummaryResponse` record com: `inProgress` (int), `pendingSterilization` (int), `inLoad` (int), `sterilizing` (int), `overdue` (int), `doneThisWeek` (int — OPs com `displayStatus=DONE` e `updatedAt >= início da semana ISO atual`); campos calculados iterando sobre todas as OPs em memória no use case (sem SQL nativo, conforme padrão do projeto)
8. `lastSyncAt` (LocalDateTime nullable) incluído em `FamilyTrackingResponse`, na response de `/tracking/orders` como campo raiz, e em `ProductionTrackingSummaryResponse`: valor = `MAX(createdAt)` do `ImportProductionBatch` com `batchType = PRODUCTION_ORDERS` — calculado via JPQL `@Query("SELECT MAX(b.createdAt) FROM ImportProductionBatch b WHERE b.batchType = 'PRODUCTION_ORDERS'")` no `ImportProductionBatchRepository`
9. Nenhuma query com SQL nativo — apenas JPQL e cálculo em Java; relacionamento `ProductionOrder → SterilizationLoad` carregado via `@EntityGraph` ou `JOIN FETCH` no repositório para evitar N+1 ao calcular `displayStatus`
10. Testes unitários em `GetProductionTrackingUseCaseTest`: (a) OP com Dynamics=DONE, `requiresSterilization=false` → `DONE`; (b) OP com Dynamics=DONE, `requiresSterilization=true`, `sterilizationLoad=null` → `PENDING_STERILIZATION`; (c) OP com carga STERILIZING → `STERILIZING`; (d) OP com carga RELEASED → `DONE`; (e) OP com `dueDate` ontem e displayStatus != DONE → `overdue=true`; (f) OP com carga OPEN → `IN_LOAD`
11. `TrackingController` criado em `production/presentation/` com `@RestController`, `@RequestMapping("/api/v1/production/tracking")`, `@Validated`; os três endpoints delegam para `GetProductionTrackingUseCase`; erros de validação de `@RequestParam` retornam 400 via `GlobalExceptionHandler` (ADR-031)

---

#### US-083 — Frontend de acompanhamento visual — kanban de OPs (5 pts)

**Contexto**: Tela principal de tracking de produção consumindo os endpoints de US-082. Kanban por família, com cards de OP, filtros, painel lateral de detalhe e banner de sincronização.

**Frontend**
1. Rota `/production/tracking` lazy-loaded em `production.routes.ts`; componente `ProductionTrackingComponent` standalone com `ChangeDetectionStrategy.OnPush`; serviço `ProductionTrackingService` separado em `production/production-tracking.service.ts` com métodos `getFamilies()`, `getOrders(filters)` e `getSummary()` — HTTP calls para os três endpoints de US-082
2. Seção de resumo no topo: 4 cards KPI usando signals: `inProgress` (azul, ícone de engrenagem), `pendingSterilization` (laranja, ícone de ampulheta), `sterilizing` (roxo, ícone de temperatura), `overdue` (vermelho, ícone de alerta); dados carregados via `getSummary()` no `ngOnInit`; skeleton loaders enquanto carrega; erro exibe snackbar não-dismissível
3. Seletor de família: chips horizontais rolável com o nome de cada família + badge com contagem de OPs atrasadas (vermelho); seleção via signal `selectedFamily = signal<string | null>(null)`; "Todas" como opção inicial; troca de família re-filtra o kanban sem nova chamada HTTP (apenas `computed()` sobre o signal de dados)
4. Filtros adicionais acima do kanban: dropdown "Tipo" (FINISHED / INTERMEDIATE / Todos), toggle "Apenas atrasadas"; aplicados via `computed()` sobre os dados carregados — sem chamadas HTTP adicionais para filtros locais
5. Kanban: 7 colunas em `display: grid; grid-template-columns: repeat(7, minmax(220px, 1fr))`, scroll horizontal; cada coluna tem header com nome do status (traduzido: PLANNED=Planejada, RELEASED=Liberada, IN_PROGRESS=Em Produção, PENDING_STERILIZATION=Aguard. Esterilização, IN_LOAD=Em Carga, STERILIZING=Esterilizando, DONE=Concluída) e contagem de cards; colunas PENDING_STERILIZATION e IN_LOAD visíveis somente se há OPs do tipo FINISHED; colunas sem OPs exibem placeholder "Nenhuma OP" em cinza
6. Card de OP com `@for (order of columnOrders(status); track order.dynamicsOrderNumber)`: chip de status colorido (PLANNED=cinza, RELEASED=azul-claro, IN_PROGRESS=azul `#0099B8`, PENDING_STERILIZATION=laranja, IN_LOAD=âmbar, STERILIZING=roxo, DONE=verde), número da OP em negrito, código e nome do produto (truncado a 25 chars com tooltip completo), barra de progresso Angular Material `<mat-progress-bar>` com valor `completionPct`, percentual ao lado, data de prazo (vermelho em negrito se `overdue=true`), badge de pessoas (`plannedPeople` ou "—"), borda esquerda vermelha se `overdue=true`
7. Clique no card abre painel lateral `<mat-drawer>` (posição end, largura 400px) com todos os campos da OP: número, produto (código + nome), família, tipo, `plannedQty`, `producedQty`, `completionPct`, `dueDate`, `displayStatus`, `plannedPeople`, `loadNumber` + `loadStatus` (se alocada em carga); link "Ver carga →" navegando para `/production/sterilization-loads/{loadId}` quando `loadNumber` presente; painel fecha com botão X ou clique fora
8. Banner de sincronização abaixo dos filtros: `"Última sincronização: {{lastSyncAgo}}"` calculado via `computed(() => formatDistanceToNow(lastSyncAt()))` (sem dependência externa — implementado com `Date.now() - lastSyncAt.getTime()` e formatação simples em pt-BR: "há N minutos" / "há N horas" / "há N dias"); botão "Atualizar" (visível para SUPERVISOR+) que dispara `POST /api/v1/production/import` para OPs (endpoint existente de US-080) e então recarrega o tracking; spinner no botão durante o reload; se `lastSyncAt=null` exibe "Nunca sincronizado"
9. Auto-refresh: `interval(300_000).pipe(startWith(0), switchMap(() => trackingService.getFamilies()), takeUntilDestroyed())` — recarrega automaticamente a cada 5 minutos sem interação do usuário; spinner discreto no canto superior direito do kanban durante o refresh
10. Responsividade: em telas < 768px o kanban colapsa para uma listagem vertical simples agrupada por status (sem colunas), mantendo todos os filtros; nenhuma quebra de layout em telas grandes
11. Testes em `production-tracking.component.spec.ts`: (a) carrega dados e renderiza N colunas; (b) selecionar família filtra cards sem nova chamada HTTP; (c) toggle "Apenas atrasadas" oculta cards sem `overdue=true`; (d) clique em card abre painel lateral com os campos corretos; (e) botão "Atualizar" desabilitado para OPERATOR (sem role SUPERVISOR); (f) `lastSyncAt=null` exibe "Nunca sincronizado"; (g) OP com `overdue=true` exibe borda vermelha e data em vermelho

---

#### US-098 — Tech debt Sprint 29 — SEC-107 a SEC-111 (3 pts)

**Contexto**: Liquidação dos 5 itens de segurança diferidos da Sprint 29 por Beatriz. SEC-107 é HIGH — tratado com prioridade. SEC-108 e SEC-109 são MEDIUM. SEC-110 e SEC-111 são LOW/INFO e de correção cirúrgica no frontend.

**Backend — SEC-107: validação de MIME type nos imports (HIGH)**
1. `ExcelFileValidator` (já existente em `production/` após Sprint 29) integrado em todos os 5 controllers de import que usam `WorkbookFactory.create()`: `ImportProductsUseCase`, `ImportStockUseCase`, `ImportProductionOrdersUseCase`, `ImportCycleTimesUseCase`, `ImportLeadTimesUseCase`; a chamada `ExcelFileValidator.validate(inputStream)` ocorre antes de `WorkbookFactory.create()` — se falhar, lança `InvalidFileTypeException`
2. `GlobalExceptionHandler` trata `InvalidFileTypeException` retornando `415 Unsupported Media Type` com body `{ "message": "Tipo de arquivo inválido. Envie um arquivo Excel (.xlsx ou .xls)" }` — handler adicionado em `GlobalExceptionHandler.java`; se `InvalidFileTypeException` já existia sem handler dedicado, verificar e adicionar
3. `ExcelFileValidator` deve verificar magic bytes (via Apache Tika `tika-core`, dependência já adicionada em US-091 AC#2) aceitando apenas MIME types `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (xlsx) e `application/vnd.ms-excel` (xls); arquivo com extensão `.xlsx` mas conteúdo ZIP malicioso retorna 415
4. Testes unitários em `ExcelFileValidatorTest` (ou teste de integração no controller mais adequado): (a) arquivo .xlsx legítimo → `validate()` não lança exceção; (b) arquivo PDF com extensão .xlsx renomeada → lança `InvalidFileTypeException`; (c) arquivo ZIP com extensão .xlsx → lança `InvalidFileTypeException`; (d) `InputStream` vazio → lança `InvalidFileTypeException`
5. Teste de integração em `ImportProductsControllerTest` (ou equivalente): `POST /api/v1/production/import/products` com arquivo PDF renomeado → resposta 415 com `{ "message": "..." }`

**Backend — SEC-108: sanitização de mensagens de erro nos imports (MEDIUM)**
6. Nos 5 use cases de import (`ImportProductsUseCase`, `ImportStockUseCase`, `ImportProductionOrdersUseCase`, `ImportCycleTimesUseCase`, `ImportLeadTimesUseCase`): substituir `catch (Exception e) { errors.add(new ImportErrorDto(rowNum, e.getMessage())); }` por `catch (Exception e) { log.warn("Erro inesperado ao processar linha {}: {}", rowNum, e.getMessage(), e); errors.add(new ImportErrorDto(rowNum, "Erro ao processar linha")); }` — mensagem genérica exposta ao cliente, detalhe apenas no log
7. Exceções de domínio esperadas (ex: `EntityNotFoundException`, `DuplicateKeyException`) que já retornam mensagens amigáveis de negócio devem permanecer com suas mensagens originais — apenas o catch genérico de `Exception` é substituído; verificar cada use case para garantir que não haja catch-all mascarando erros de negócio legítimos
8. Teste unitário verifica que, dado um `InputStream` que lança `RuntimeException("stack trace interno")` ao ser processado, o `ImportErrorDto.message` retornado é `"Erro ao processar linha"` (não contém o stack trace ou mensagem interna)

**Backend — SEC-109: exposição de `importedBy` em `/import/history` para SUPERVISOR (MEDIUM)**
9. Decisão de produto adotada: **opção (b) — omitir `importedBy` do response para SUPERVISOR**; motivo: SUPERVISOR precisa de visibilidade do histórico de importações (quais batches foram importados e quando) para operar o módulo de produção, mas não precisa do username de quem importou — isso é dado de auditoria restrito ao ADMIN; solução sem quebrar o endpoint para ADMIN
10. `ImportProductionBatchResponse` record recebe campo `importedBy` como `String` nullable; `GetImportHistoryUseCase` (ou use case equivalente) preenche `importedBy` apenas quando o usuário autenticado tem role ADMIN — usa `SecurityContextHolder.getContext().getAuthentication().getAuthorities()` para verificar; para SUPERVISOR, `importedBy = null` no record retornado
11. Teste unitário `GetImportHistoryUseCaseTest`: (a) autenticado como ADMIN → `importedBy` preenchido com username real; (b) autenticado como SUPERVISOR → `importedBy = null`; endpoint `GET /api/v1/production/import/history` continua acessível para SUPERVISOR+ (sem mudança de `@PreAuthorize`)

**Frontend — SEC-110: `clearFailedTitles()` no logout (LOW)**
12. Em `AuthService.logout()` (arquivo `apps/frontend/src/app/auth/auth.service.ts`): após a chamada existente `this.offlineQueueService.clearAll()` (ou o equivalente SEC-089), adicionar `this.offlineSyncService.clearFailedTitles()` — limpa o signal `failedTitles` ao deslogar; verificar que a injeção de `OfflineSyncService` em `AuthService` não cria dependência circular (se `OfflineSyncService` injeta `AuthService`, usar `inject()` com `{optional: true}` ou mover a limpeza para um `APP_INITIALIZER`/guard de logout); preferir a solução sem circular dependency
13. Teste em `auth.service.spec.ts`: ao chamar `logout()`, `offlineSyncService.clearFailedTitles()` é invocado uma vez; verificar com spy que o signal `failedTitles` fica vazio após o logout

**Frontend — SEC-111: URLs invertidas em `ProductionService` (INFO)**
14. Em `production.service.ts` (arquivo `apps/frontend/src/app/production/production.service.ts`): corrigir as 3 URLs de import:
    - `/products/import` → `/import/products`
    - `/stock/import` → `/import/stock`
    - `/orders/import` → `/import/production-orders`
    (conforme mapeamento do `ProductionController` backend que usa `/api/v1/production/import/{type}`)
15. Após correção, nenhuma chamada de import retorna 404; verificar também se há outras URLs do `ProductionService` com inversão similar (ex: `/cycle-times/import` vs `/import/cycle-times` e `/lead-times/import` vs `/import/lead-times`) e corrigir todas as ocorrências encontradas
16. Teste em `production.service.spec.ts` (ou equivalente): verificar que as URLs construídas pela service batem com os endpoints do controller; se o spec usa `HttpTestingController`, confirmar que os `expectOne()` são chamados com as URLs corretas pós-correção

---

## Sprint 31 ✅
**Objetivo**: Gestão de cargas de esterilização — criação, alocação de OPs e ciclo completo + tech debt SEC-115/069/060/074
**ADR**: ADR-029, ADR-042
**Status**: concluída
**Pontos totais**: 11 pts (US-084: 8 + US-099: 3)

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-084 | Backend e frontend de cargas de esterilização (ciclo OPEN → RELEASED) | 8 | ✅ concluída |
| US-099 | Tech debt Sprint 30 — SEC-115, SEC-069, SEC-060, SEC-074 | 3 | ✅ concluída |

### Dependências
- US-084 e US-099 são independentes — podem ser desenvolvidas em paralelo
- US-099 é prioritária no frontend (SEC-060/SEC-074 são correções pontuais de baixo risco)

### Sequência de entrega sugerida
1. US-099 (tech debt — fixes cirúrgicos, sem entidades novas)
2. US-084 backend (domínio, use cases, controller)
3. US-084 frontend (sterilization-loads list + detail + allocation dialog)

---

#### US-084 — Cargas de esterilização (8 pts)

**Contexto**: Módulo Hub-managed para agrupar OPs DONE+requiresSterilization em lotes físicos de esterilização. Modelo de dados e fluxo de status definidos no ADR-029; decisões de implementação (geração de loadNumber, efeitos colaterais em Equipment, alocação por dialog) no ADR-042.

**Backend**
1. `POST /api/v1/production/sterilization-loads` cria carga (SUPERVISOR+); campos: `sterilizerId` (UUID, nullable), `method` (enum `SterilizationMethod`), `sterilizationDate` (optional `LocalDate`), `batchCode` (nullable — lote ANVISA), `notes`; `loadNumber` gerado sequencialmente "CARGA-{ANO}-{NNN}" via `nextSequenceForYear()` (ADR-042 Decisão 1); resposta 201 com `SterilizationLoadResponse`
2. `GET /api/v1/production/sterilization-loads` lista cargas (OPERATOR+); filtros: `status` (enum), `method` (enum), `dateFrom` (`LocalDate`), `dateTo` (`LocalDate`); paginação `@PageableDefault(size=20)`
3. `GET /api/v1/production/sterilization-loads/{id}` detalhe da carga + lista de OPs alocadas + totais (quantidade total de OPs, quantidade total planejada) (OPERATOR+); 404 se não existir
4. `GET /api/v1/production/sterilization-loads/pending-orders` retorna OPs com `status=DONE` no Dynamics + `product.requiresSterilization=true` + `sterilizationLoad IS NULL` — ordenadas por `dueDate ASC NULLS LAST` (SUPERVISOR+)
5. `POST /api/v1/production/sterilization-loads/{id}/orders` body `{ "productionOrderId": UUID }` (SUPERVISOR+); valida: OP existe, `status=DONE`, `product.requiresSterilization=true`, OP não alocada em outra carga ativa (`status ≠ REJECTED`) → 409 com `{ "message": "OP já alocada na carga CARGA-2026-XXX" }` se violação; 404 se carga/OP não encontrada; 204 em sucesso
6. `DELETE /api/v1/production/sterilization-loads/{id}/orders/{opId}` remove OP da carga (SUPERVISOR+); apenas quando `loadStatus=OPEN` → 422 se carga não está OPEN; 204 em sucesso
7. `PUT /api/v1/production/sterilization-loads/{id}/status` body `{ "targetStatus": LoadStatus }` (SUPERVISOR+); transições válidas e efeitos colaterais conforme ADR-042 Decisão 3:
   - `OPEN → CLOSED`: `closedAt = now()`; se `sterilizer != null` → `equipment.status = UNDER_MAINTENANCE` (mesma `@Transactional`)
   - `CLOSED → STERILIZING`: sem efeito colateral adicional
   - `STERILIZING → RELEASED`: `releasedAt = now()`; se `sterilizer != null` → `equipment.status = OPERATIONAL`
   - `STERILIZING → REJECTED`: todas as OPs da carga → `sterilizationLoad = null` (bulk UPDATE via JPQL)
8. Transição inválida retorna `422` com `{ "message": "Transição inválida: {current} → {target}" }` via `InvalidLoadTransitionException`
9. `AuditLog` registrado em: criação de carga (`STERILIZATION_LOAD_CREATED`), adição de OP (`STERILIZATION_ORDER_ALLOCATED`), transição de status (`STERILIZATION_LOAD_STATUS_CHANGED`) — via `auditService.log()` existente
10. Testes unitários: `CreateSterilizationLoadUseCaseTest` (3 cenários), `AddOrderToLoadUseCaseTest` (4 cenários incluindo 409 e OP já em carga REJECTED re-alocável), `TransitionLoadStatusUseCaseTest` (5 cenários: 4 transições válidas + 1 inválida → 422)

**Frontend**
11. Rota `/production/sterilization-loads` lazy-loaded; componente `SterilizationLoadsComponent` standalone `OnPush`; serviço `SterilizationLoadsService` com métodos: `listLoads(filters?)`, `getLoad(id)`, `createLoad(body)`, `addOrder(loadId, orderId)`, `removeOrder(loadId, orderId)`, `transitionStatus(loadId, target)`, `getPendingOrders()`
12. Listagem: cards de carga agrupados por status com badge de nº de OPs; botão "Nova Carga" (SUPERVISOR+); filtros por status e método no topo
13. Carga OPEN: tabela de OPs alocadas com botão "×" por linha (SUPERVISOR+, remove via `DELETE`); painel lateral "Aguardando Carga" com OPs pendentes ordenadas por dueDate; botão "+ Adicionar" abre dialog de confirmação (ADR-042 Decisão 4); botão "Fechar Carga" (SUPERVISOR+) com confirmação
14. Rota `/production/sterilization-loads/:id` detalhe: tabela de OPs (produto, nº OP, quantidade, família, dueDate), totais, chip de esterilizador, método, datas; botões de transição com confirmação por dialog (SUPERVISOR+); chips de status coloridos: OPEN=azul, CLOSED=âmbar, STERILIZING=roxo, RELEASED=verde, REJECTED=vermelho
15. Ao confirmar RELEASED: dialog adicional com mensagem "Lembre-se de atualizar o estoque no Dynamics para as OPs desta carga"
16. Link "Cargas" adicionado ao grupo "Produção" no nav (SUPERVISOR+ visível; OPERATOR sem botões de ação)
17. Testes Vitest: listagem renderiza cards por status; alocação via dialog chama `addOrder()`; transição OPEN→CLOSED exibe confirmação; RELEASED exibe dialog de reminder Dynamics

---

#### US-099 — Tech debt Sprint 30 — SEC-115, SEC-069, SEC-060, SEC-074 (3 pts)

**Backend**
1. **SEC-115**: em `ProductionController`, substituir `@PreAuthorize("isAuthenticated()")` por `@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")` nos endpoints `GET /tracking/families`, `GET /tracking/orders` e `GET /tracking/summary` — torna a permissão explícita e consistente com o restante do projeto; sem impacto funcional (todos os roles autenticados já tinham acesso, conforme ADR-041 Decisão 8)
2. **SEC-069**: em `DashboardController.get()` e `AlertThresholdController.evaluateNow()`, adicionar null-guard para `Authentication` antes de `.getAuthorities()` / `.getName()` — atualmente `@PreAuthorize` garante que o principal nunca é null em produção, mas o null check morto cria dead code; remover branch morto ou adicionar `Objects.requireNonNull()` com mensagem clara; teste unitário para o caminho `authentication != null`

**Frontend**
3. **SEC-060**: corrigir URL de `markAllRead()` em `notifications.service.ts` — atual: `/api/v1/notifications/mark-all-read`, correto: `/api/v1/notifications/read-all` (alinhado com endpoint backend `@PutMapping("/read-all")` do `NotificationController`); atualizar spec correspondente
4. **SEC-074**: em `file-attachments.component.html`, adicionar `rel="noopener noreferrer"` a todos os elementos `<a>` com `target="_blank"` que abrem anexos; previne `window.opener` access de origem cruzada; teste: verificar que elemento `<a>` possui atributo `rel` com valor correto

---

## Sprint 32 ✅
**Objetivo**: Motor MRP, staffing por OP (tempo de ciclo → pessoas), board de planejamento por família + tech debt S31
**ADR**: ADR-030, ADR-043
**Status**: concluída
**Pontos totais**: 16 pts (US-085: 5 + US-086: 3 + US-087: 5 + US-100: 3)

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-085 | Motor MRP — dry-run, run e gerenciamento de sugestões | 5 | ✅ concluído |
| US-086 | Cálculo de staffing por OP (ciclo → pessoas) + edição por SUPERVISOR | 3 | ✅ concluído |
| US-087 | Board de planejamento por família + timeline (Gantt simplificado) | 5 | ✅ concluído |
| US-100 | Tech debt Sprint 31 — badge totalOrders nas cargas + refinamentos de UX | 3 | ✅ concluído |

### Dependências
- US-085 e US-086 são independentes entre si — podem ser desenvolvidas em paralelo
- US-087 depende de US-085 (motor MRP) e US-086 (staffing); deve ser entregue por último
- US-100 é independente — pode ser desenvolvida em paralelo a qualquer US

### Sequência de entrega sugerida
1. US-086 backend (staffing — mais simples, sem novas entidades complexas)
2. US-085 backend (motor MRP — entidades MrpRun + MrpPlannedOrder)
3. US-086 frontend (inline edit staffing no tracking)
4. US-087 backend + frontend (board e timeline — integra ambos)
5. US-100 (tech debt — fixes pontuais)

---

#### US-085 — Motor MRP (5 pts)

**Backend**
1. `POST /api/v1/production/mrp/dry-run` (SUPERVISOR+): executa MRP em memória sem persistir; retorna `MrpRunResult` com sugestões, necessidades de compra e alertas; 200 OK
2. `POST /api/v1/production/mrp/run` (SUPERVISOR+): executa MRP, persiste `MrpRun` + `MrpPlannedOrder` com `status=SUGGESTED`; retorna `MrpRunResult`; 201 Created
3. Algoritmo Net Change conforme ADR-030 Decisão 3: `netNeed = max(0, minStockQty - stockCurrent - openOrdersQty)`; apenas `netNeed > 0` gera sugestão; produtos sem `minStockQty` definido são ignorados
4. `openOrdersQty` inclui OPs Dynamics com status em `[PLANNED, RELEASED, IN_PROGRESS]` + `MrpPlannedOrders` com status em `[SUGGESTED, ACCEPTED]` — evita duplicação de sugestões
5. `suggestedQty = ceil(netNeed / batchSize) * batchSize`; `suggestedDueDate = today + leadTimeDays`; produtos sem `batchSize` usam `batchSize = 1`
6. `GET /api/v1/production/mrp/runs` histórico de runs (SUPERVISOR+); paginado `@PageableDefault(size=20)`, ordenado por `runAt DESC`; resposta inclui `runAt`, `runBy`, `isDryRun`, `suggestionsGenerated`, `productsAnalyzed`
7. `GET /api/v1/production/mrp/suggested-orders` lista `MrpPlannedOrders` com `status=SUGGESTED` (SUPERVISOR+); inclui `productCode`, `productName`, `familyName`, `suggestedQty`, `adjustedQty`, `suggestedDueDate`
8. `PUT /api/v1/production/mrp/suggested-orders/{id}/accept` (SUPERVISOR+): body opcional `{ "adjustedQty": N }`; `status → ACCEPTED`; se `adjustedQty` fornecido e `> 0`, persiste no campo; 200 + `MrpPlannedOrderResponse`
9. `PUT /api/v1/production/mrp/suggested-orders/{id}/reject` (SUPERVISOR+): body `{ "reason": "..." }`; `status → REJECTED`; `rejectionReason` persistido; 200
10. `PUT /api/v1/production/mrp/suggested-orders/{id}/convert` (SUPERVISOR+): marca sugestão como criada no Dynamics; `status → CONVERTED`; 200
11. Entidades `MrpRun` e `MrpPlannedOrder` conforme ADR-030 Decisões 1 e 2; `MrpOrderStatus` enum: `SUGGESTED | ACCEPTED | REJECTED | CONVERTED`
12. Testes unitários: `RunMrpUseCaseTest` — (a) produto com `netNeed > 0` gera sugestão com `suggestedQty` correto; (b) produto com `estoque >= minStock` não gera sugestão; (c) `dry-run=true` não persiste `MrpPlannedOrder`; (d) `openOrdersQty` inclui `MrpPlannedOrders SUGGESTED`; `AcceptMrpSuggestionUseCaseTest` — (e) aceitar com `adjustedQty` persiste ajuste; (f) status inválido para aceitar (REJECTED) → 409

---

#### US-086 — Staffing por OP (3 pts)

**Backend**
1. `StaffingConfig` singleton conforme ADR-030 Decisão 5: `shiftHours=8`, `shiftsPerDay=1` default; `GET /api/v1/production/staffing-config` (OPERATOR+); `PUT /api/v1/production/staffing-config` (ADMIN) com body `{ "shiftHours": N, "shiftsPerDay": N }`; `updatedAt` e `updatedBy` auditados
2. Cálculo automático ao importar OPs: `peopleNeeded = ceil((plannedQty * cycleTime) / (shiftHours * shiftsPerDay * 3600 * businessDays))`; `businessDays = max(1, workdaysUntil(dueDate))`; executado apenas para OPs com `peopleOverridden = false`
3. `PUT /api/v1/production/production-orders/{id}/staffing` (SUPERVISOR+): body `{ "plannedPeople": N }` onde `N >= 1`; seta `plannedPeople = N`, `peopleOverridden = true`; retorna 200 + `ProductionOrderStaffingResponse`
4. `DELETE /api/v1/production/production-orders/{id}/staffing` (SUPERVISOR+): recalcula `plannedPeople` a partir de `CycleTime` + `StaffingConfig`; seta `peopleOverridden = false`; retorna 200 + `ProductionOrderStaffingResponse`
5. OPs sem `CycleTime` cadastrado: `plannedPeople = null` (não bloqueante); `peopleOverridden` permanece `false`
6. Testes unitários: `CalculateStaffingUseCaseTest` — (a) OP com `cycleTime` e `dueDate` futuro calcula `peopleNeeded` correto; (b) `peopleOverridden = true` preserva valor na reimportação; (c) reset (`DELETE`) recalcula mesmo com `peopleOverridden = true`; (d) OP sem `CycleTime` retorna `null` sem lançar exceção

**Frontend**
7. Coluna/campo "Pessoas" nas listagens de OP (`/production/orders`): exibe valor ou "—"; ícone de lápis (SUPERVISOR+): clique abre input inline com valor atual + botões Salvar / Cancelar; salvar chama `PUT /staffing`; cancelar restaura display
8. Ícone de calculadora ao lado do valor quando `peopleOverridden = true`: clique chama `DELETE /staffing` com confirmação inline "Recalcular automaticamente?" — Sim / Não
9. Testes Vitest: inline edit exibe input ao clicar lápis; salvar chama `updateStaffing()`; cancelar restaura valor; ícone calculadora visível apenas quando `peopleOverridden = true`

---

#### US-087 — Board de planejamento e timeline (5 pts)

**Backend**
1. `GET /api/v1/production/planning/families` (SUPERVISOR+): retorna `List<FamilyPlanningBoard>` conforme ADR-030 Decisão 6; `planningStatus` calculado: `OK` se `netNeed = 0`, `ALERT` se `0 < netNeed <= minStockQty * 0.5`, `CRITICAL` se `netNeed > minStockQty * 0.5`
2. `GET /api/v1/production/planning/timeline?familyCode=X&weeks=8` (SUPERVISOR+): retorna `List<TimelineEntry>` conforme ADR-030 Decisão 7; `weeks` padrão = 8, máx = 26; OPs sem `dueDate` excluídas
3. `GET /api/v1/production/planning/purchase-needs` (SUPERVISOR+): retorna necessidades de matéria-prima do `MrpRun` mais recente com sugestões `SUGGESTED/ACCEPTED`; inclui `productCode`, `productName`, `quantity`, `unit`; 404 se nenhum run executado
4. Testes unitários: `GetPlanningBoardUseCaseTest` — (a) produto com `netNeed = 0` → `planningStatus = OK`; (b) produto com `netNeed > 0` → `ALERT` ou `CRITICAL`; (c) família sem produtos ativos → lista vazia

**Frontend**
5. Rota `/production/planning` lazy-loaded; serviço `PlanningService`; componente `PlanningBoardComponent` standalone `OnPush`; cards colapsáveis por família com tabela interna: código, estoque atual, mínimo, OPs abertas, sugestões MRP, necessidade líquida, chip `OK/ALERT/CRITICAL`, pessoas planejadas
6. Botão "Executar MRP" (SUPERVISOR+): chama `dry-run` → exibe modal `MrpPreviewModalComponent` com tabela de sugestões editáveis (qtd por linha) → "Confirmar e Gerar" chama `run` → toast com "X sugestões geradas"; modal tem botão "Cancelar" sem persistir
7. Rota `/production/planning/timeline/:familyCode` lazy-loaded; componente `PlanningTimelineComponent`; grade CSS Grid — semanas em colunas (X eixo), ordens em linhas (Y eixo); barras coloridas: `#0099B8`=Dynamics, `#F97316`=sugestão MRP; linha vermelha se `overdue=true`; navegação por setas ← → para avançar/retroceder semanas
8. Clique em barra da timeline: abre side panel com detalhes da OP/sugestão; sugestões MRP exibem botões "Aceitar" / "Rejeitar" (SUPERVISOR+) que chamam os endpoints correspondentes
9. Seção "Necessidades de Compra" no board: tabela de RAW_MATERIAL do último MRP run; exibe mensagem "Nenhum MRP executado ainda" se endpoint retornar 404
10. Link "Planejamento" adicionado ao nav (SUPERVISOR+)
11. Testes Vitest: board renderiza cards por família; chip `OK/ALERT/CRITICAL` reflete `planningStatus`; botão MRP exibe modal de preview; modal "Cancelar" não chama `run`; timeline renderiza barras; clique em barra abre side panel

---

#### US-100 — Tech debt Sprint 31 (3 pts)

**Contexto**: gaps documentados pela Maiana na revisão do Sprint 31 — AC#12 (badge `totalOrders` ausente nos cards de listagem de cargas).

**Backend**
1. Adicionar campo `totalOrders` (contagem de OPs alocadas) ao `SterilizationLoadResponse` (summary DTO usado na listagem): novo campo `Integer totalOrders`; calculado via `@Query` no `SterilizationLoadRepository` usando `COUNT(po.id)` com `LEFT JOIN` nas `ProductionOrders` alocadas; 0 se não houver OPs
2. `SterilizationLoadRepository`: nova query `findFilteredWithOrderCount(...)` retornando interface projection ou `@Formula`/`@Transient` — ou alternativa: adicionar `totalOrders` como campo calculado com `@Formula("(SELECT COUNT(*) FROM production_order po WHERE po.sterilization_load_id = id)")`

**Frontend**
3. Interface `SterilizationLoadSummary` em `sterilization-loads.service.ts`: adicionar campo `totalOrders: number`
4. Template `sterilization-loads.component.html`: exibir badge com `totalOrders` no card de cada carga (ex: "3 OPs"), visível para todos os roles; badge com cor neutra (cinza) quando `totalOrders = 0`, teal quando `> 0`
5. Testes Vitest: badge exibido com valor correto; badge cinza quando `totalOrders = 0`; badge teal quando `totalOrders > 0`

---

### Tech Debt diferido do Sprint 32 (Beatriz — SEC-116)

| ID | Severidade | Descrição | Fix proposto | Sprint alvo |
|----|-----------|-----------|-------------|-------------|
| SEC-116 | LOW | `RejectMrpSuggestionRequest.reason` sem `@Size(max=500)` — campo tem `@NotBlank` mas sem limite máximo; `MrpPlannedOrder.rejectionReason` tem `@Column(length=500)`, portanto PostgreSQL lançaria ISE genérico em payloads >500 chars em vez de 400 | Adicionar `@Size(max=500, message="Motivo deve ter no máximo 500 caracteres")` ao DTO `RejectMrpSuggestionRequest` | Sprint 33 |

> **Itens INFO (não blockers, sem ação obrigatória):**
> - SEC-117: `getStaffingConfig.getOrCreate()` e `cycleTimeRepository` chamados dentro do loop de importação de OPs — risco de performance em importações grandes; candidato a refatoração (cache da StaffingConfig antes do loop)
> - SEC-118: `setTimeout(() => toast.set(null), 4000)` sem `clearTimeout` no `PlanningBoardComponent` — Angular ignora silenciosamente no OnPush; boas práticas sugerem cancelar o timeout no `ngOnDestroy`

---

## Sprint 33 ✅
**Objetivo**: BOM import do Dynamics (habilita explosão MRP de 2 níveis completa) + relatório de planejamento planned vs actual + tech debt MRP
**ADR**: ADR-044
**Status**: concluída
**Pontos totais**: 10 pts (US-101: 5 + US-102: 3 + US-103: 2)

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-101 | BOM import do Dynamics — estrutura de componentes por produto | 5 | ✅ concluído |
| US-102 | Relatório de planejamento: planned vs actual + exportação CSV | 3 | ✅ concluído |
| US-103 | Tech debt Sprint 32 — SEC-116 @Size + SEC-117 cache + SEC-118 clearTimeout | 2 | ✅ concluído |

### Dependências
- US-101 é pré-requisito de US-102 para o campo `pendingMrpQty` estar correto (o relatório consolida dados do MRP atualizado)
- US-101 é parcialmente independente de US-102 — o relatório pode ser desenvolvido em paralelo a partir do endpoint existente de OPs/famílias
- US-103 é totalmente independente — pode ser iniciado antes de qualquer US

### Sequência de entrega sugerida
1. US-103 backend (SEC-116 @Size — fix de 1 linha + 1 teste; SEC-117 cache StaffingConfig — refatoração cirúrgica)
2. US-101 backend (entidade `ProductComponent`, `ImportBomUseCase`, endpoint import, atualização do `MrpCalculationService`)
3. US-103 frontend (SEC-118 clearTimeout no `PlanningBoardComponent`)
4. US-102 backend (aggregation query, endpoint de relatório, exportação CSV)
5. US-101 frontend (aba BOM no import, seção BOM no detalhe do produto)
6. US-102 frontend (tela de relatório, filtros, tabela, exportação)

---

#### US-101 — BOM import do Dynamics (5 pts)

**Contexto**: O ADR-030 (Decisão 3) previa explosão de BOM de 2 níveis no MRP, mas o BOM ainda não existe no domínio — `MrpCalculationService` gera apenas `purchaseNeeds` placeholder para RAW_MATERIAL sem quantidade real. Esta US implementa a estrutura de BOM (importação via Excel do Dynamics) e integra ao motor MRP.

**Backend**
1. Entidade `ProductComponent` com campos: `id` (UUID), `parentProduct` (FK → Product, not null), `componentProduct` (FK → Product, not null), `quantity` (Double, not null — unidades do componente por unidade do produto pai), `unit` (VARCHAR 10 — ex: "UN", "KG"), `level` (Integer — 1 para componentes diretos, 2 para sub-componentes), `active` (Boolean, default true); índice em `(parent_product_id)` e unique constraint em `(parent_product_id, component_product_id)`
2. `ProductComponentRepository` com queries: `findByParentProductCode(String code)` retornando `List<ProductComponent>`; `deleteByParentProductCode(String code)` para upsert full (reimportação substitui BOM do produto)
3. `ImportBomUseCase` — upload Excel multipart (`POST /api/v1/production/import/bom`, ADMIN); colunas esperadas: `parent_code`, `component_code`, `quantity`, `unit`; upsert por `(parentProduct, componentProduct)` — atualiza `quantity` e `unit` se já existir; erros por linha sem abortar importação; retorna `BomImportResponse { totalRecords, created, updated, errors, importedBy, importedAt }`
4. `GetProductBomUseCase` + endpoint `GET /api/v1/production/products/{code}/bom` (SUPERVISOR+): retorna `List<BomComponentRow { componentCode, componentName, quantity, unit, level, productType }>`; lista vazia se produto não tem BOM cadastrado
5. `MrpCalculationService` atualizado: para cada produto FINISHED com `netNeed > 0`, explodir nível 1 do BOM — componentes INTERMEDIATE geram `MrpPlannedOrder`; componentes RAW_MATERIAL entram em `purchaseNeeds` com quantidade real (`suggestedQty * bomItem.quantity`); se BOM vazio, comportamento atual mantido (purchaseNeeds com `qty = null` e nota "BOM não cadastrado")
6. Testes unitários: (a) `ImportBomUseCase`: 3 linhas → 2 criadas + 1 atualizada; linha com código de produto inexistente → erro sem abortar; (b) `MrpCalculationService` com BOM: produto FINISHED com 2 RAW_MATERIAL no BOM → purchaseNeeds com quantidades reais calculadas; produto sem BOM → purchaseNeeds com qty = null (comportamento legado)

**Frontend**
7. Aba "BOM" na tela `/production/import`: formulário de upload Excel com link de download de template (colunas `parent_code`, `component_code`, `quantity`, `unit`); exibe `BomImportResponse` após import com contadores de criação/atualização/erro
8. Seção "Estrutura BOM" no detalhe do produto (`/production/products/:code`): tabela com componentes, quantidade, unidade e tipo (chip `INTERMEDIATE` / `RAW_MATERIAL`); mensagem "BOM não cadastrado para este produto" se lista vazia; ADMIN vê botão "Importar BOM" que navega para `/production/import` aba BOM

---

#### US-102 — Relatório de planejamento: planned vs actual (3 pts)

**Contexto**: Com MRP, staffing e BOM implementados, o planejador precisa de uma visão consolidada de eficiência de produção — o quanto foi planejado vs. o quanto foi efetivamente produzido por produto/família em um período.

**Backend**
1. `GET /api/v1/production/reports/planning-summary` (SUPERVISOR+) com params: `familyCode` (opcional), `from` (LocalDate), `to` (LocalDate); agrega por produto: `plannedQty` (soma de `plannedQty` das OPs com `status IN [PLANNED, RELEASED, IN_PROGRESS, DONE]` com `dueDate` no período), `producedQty` (soma de `producedQty` das OPs `DONE` com `dueDate` no período), `efficiency` (producedQty * 100.0 / plannedQty; null se plannedQty = 0), `pendingMrpQty` (soma de `suggestedQty` das `MrpPlannedOrders` SUGGESTED+ACCEPTED para o produto)
2. `GET /api/v1/production/reports/planning-summary/export` (SUPERVISOR+) com os mesmos params: retorna CSV com `Content-Disposition: attachment; filename="planejamento-YYYY-MM-DD.csv"`; charset UTF-8 BOM (`﻿`) para compatibilidade com Excel pt-BR; cabeçalho: `Família;Produto;Código;Qtd Planejada;Qtd Produzida;Eficiência (%);Sugestões MRP Pendentes`
3. Testes: (a) response sem filtro de família retorna todos os produtos com OPs no período; (b) `efficiency = null` quando `plannedQty = 0`; (c) CSV contém BOM UTF-8 e separador ponto-e-vírgula

**Frontend**
4. Nova rota `/production/reports` lazy-loaded; `ProductionReportComponent` standalone `OnPush`; serviço `ProductionReportService` com métodos `getSummary(params)` e `exportCsv(params)` (download via `window.open` ou blob URL)
5. Filtros: seletor de família (todas ou específica via `signal<string | null>`), date range (from/to via `signal<{from: LocalDate, to: LocalDate}>`); padrão: últimos 30 dias; botão "Gerar Relatório" desabilitado enquanto datas inválidas; botão "Exportar CSV" habilitado apenas quando há resultados
6. Tabela de resultado: colunas — família, código do produto, nome do produto, planejado, produzido, eficiência (— quando null), sugestões MRP pendentes; ordenação por família e produto; sem paginação (volume esperado < 200 produtos ativos)
7. Link "Relatórios" adicionado ao nav (SUPERVISOR+)
8. Testes Vitest: componente renderiza tabela com dados; exibe "—" para eficiência nula; botão exportar habilitado após resultado carregado; botão exportar desabilitado sem resultados

---

#### US-103 — Tech debt Sprint 32 (2 pts)

**Backend**
1. **SEC-116** — adicionar `@Size(max=500, message="Motivo deve ter no máximo 500 caracteres")` ao campo `reason` de `RejectMrpSuggestionRequest`; controller classe já tem `@Validated`; `ConstraintViolationException` → 400 via `GlobalExceptionHandler` (ADR-031); teste: request com motivo de 501 chars → 400 com mensagem "Motivo deve ter no máximo 500 caracteres"
2. **SEC-117** — em `ImportProductionOrdersUseCase.execute()`, carregar `StaffingConfig` uma única vez antes do loop de OPs (`StaffingConfig config = staffingConfigUseCase.getOrCreate()`) em vez de chamar `getOrCreate()` a cada iteração; teste: mock de `StaffingConfigRepository.findFirst()` verificado com `verify(repo, times(1))` independente do número de OPs na planilha

**Frontend**
3. **SEC-118** — em `PlanningBoardComponent`: capturar `private toastTimeoutId: ReturnType<typeof setTimeout> | null = null`; no `setTimeout`, atribuir: `this.toastTimeoutId = setTimeout(() => this.toast.set(null), 4000)`; implementar `ngOnDestroy()` com `if (this.toastTimeoutId !== null) clearTimeout(this.toastTimeoutId)`; spec: verificar que `clearTimeout` é chamado ao destruir o componente com toast ativo

### Tech Debt diferido do Sprint 33 (Beatriz — SEC-119)

| ID | Severidade | Descrição | Fix proposto | Sprint alvo |
|----|-----------|-----------|-------------|-------------|
| SEC-119 | LOW | `GetPlanningSummaryUseCase.exportCsv()` — `escapeCsv()` sanitiza `;`, `"` e `\n` mas não neutraliza prefixos `=`, `+`, `-`, `@` que o Excel interpreta como fórmulas (CSV formula injection). Risco baixo: endpoint restrito a SUPERVISOR/ADMIN; dados internos do sistema, sem inserção livre por usuário anônimo. | Adicionar sanitização de prefixos de fórmula em `escapeCsv()`: se o valor começar com `=`, `+`, `-` ou `@`, prefixar com `'` antes de retornar (inerte para o Excel) | Sprint 34 |

> **Itens diferidos sem classificação de blocker (⚠️ aceitáveis — Maiana):**
> - INTERMEDIATE BOM components gerando MrpPlannedOrder próprio — diferido ADR-044 MVP; Sprint 34
> - Exportação CSV com teste de conteúdo (UTF-8 BOM + separador `;`) — E2E scope; Sprint 34

---

## Sprint 34 ✅
**Objetivo**: Painel executivo de produção com KPIs consolidados + explosão BOM nível 2 no MRP (INTERMEDIATE → sub-orders) + liquidação do tech debt de segurança diferido do Sprint 33
**ADR**: ADR-045
**Status**: concluída
**Pontos totais**: 10 pts (US-104: 5 + US-105: 3 + US-106: 2)

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-104 | Painel executivo de produção — KPIs consolidados (BOM coverage, MRP fulfillment, eficiência) | 5 | ✅ concluído |
| US-105 | BOM explosão nível 2 no MRP — INTERMEDIATE gera MrpPlannedOrder de sub-componente | 3 | ✅ concluído |
| US-106 | Tech debt Sprint 33 — SEC-119 CSV injection + WebhookUrlValidatorTest @Tag + npm audit fix | 2 | ✅ concluído |

### Dependências
- US-104 é independente de US-105 e US-106 — pode ser desenvolvida em paralelo
- US-105 depende de US-101 (Sprint 33) — BOM já importado; apenas expande a lógica do MrpCalculationService
- US-106 é totalmente independente — pode ser iniciado a qualquer momento

### Sequência de entrega sugerida
1. US-106 (tech debt — fixes cirúrgicos, baixo risco, desbloqueia pipeline limpo)
2. US-105 backend (extensão de explodePurchaseNeeds para nível 2)
3. US-104 backend (aggregation endpoint de visão geral)
4. US-105 frontend (BOM table com indentação de nível 2)
5. US-104 frontend (painel com cards de KPI e gráfico de tendência)

---

#### US-104 — Painel executivo de produção (5 pts)

**Contexto**: Com MRP, BOM e relatório de planejamento implementados, os gestores precisam de uma visão executiva consolidada — uma única tela que responda "como está a produção hoje?" sem precisar navegar entre telas. O painel agrega BOM coverage, fulfillment MRP e tendência de eficiência.

**Backend**
1. `ProductionOverviewDto` (record): `bomCoverage { totalFinishedProducts, withBom, withoutBom, coveragePct }`, `mrpFulfillment { totalSuggestions, accepted, rejected, pending, fulfillmentPct }`, `efficiencyTrend List<DailyEfficiencyDto { date, avgEfficiency }>` (últimos 30 dias), `opsByStatus Map<String, Integer>`
2. `GetProductionOverviewUseCase` — agrega dados de `ProductRepository`, `ProductComponentRepository`, `MrpPlannedOrderRepository`, `ProductionOrderRepository`; `efficiencyTrend`: agrupa OPs por data de conclusão, calcula `avg(producedQty/plannedQty)*100` por dia (ignora OPs com `plannedQty=0`); `opsByStatus`: conta OPs ativas por `status` enum
3. `GET /api/v1/production/overview` (SUPERVISOR+): sem parâmetros; cache de 5 min via `@Cacheable("production-overview")` (Spring Cache) — evita recalcular a cada refresh
4. Testes: (a) `coveragePct = 0` quando nenhum produto tem BOM; (b) `fulfillmentPct` ignora sugestões REJECTED; (c) `efficiencyTrend` exclui dias sem OPs concluídas

**Frontend**
5. Nova rota `/production/overview` lazy-loaded (SUPERVISOR+); `ProductionOverviewComponent` standalone OnPush
6. Cards de KPI no topo (2×2 grid): **BOM Coverage** `XX% (N/M produtos)`, **MRP Fulfillment** `XX% (N sugestões aceitas)`, **Eficiência Média** `XX%` (últimos 30 dias), **OPs Abertas** `N`
7. Gráfico de tendência de eficiência: `NgxChartsLineChartComponent` com dados dos últimos 30 dias; eixo Y 0–100%; linha de referência em 80% (meta); sem animação em OnPush
8. Tabela "OPs por Status": colunas Status, Quantidade; ordenada por quantidade desc
9. Link "Visão Geral" adicionado ao nav (SUPERVISOR+), antes de "Relatórios"
10. Testes Vitest: cards renderizados com dados mockados; "—" para eficiência nula; tabela de OPs ordenada; loading state exibido durante fetch

---

#### US-105 — BOM explosão nível 2 no MRP (3 pts)

**Contexto**: O Sprint 33 (ADR-044) implementou explosão BOM nível 1 apenas para RAW_MATERIAL → purchaseNeeds. Componentes INTERMEDIATE foram ignorados (MVP). Esta US completa 1 nível adicional: INTERMEDIATE → busca BOM do próprio componente no mapa pré-carregado e adiciona RAW_MATERIAL desse segundo nível como purchaseNeeds adicionais. Não é explosão recursiva completa — apenas 2 níveis totais.

**Backend**
1. `MrpCalculationService.explodePurchaseNeeds()` estendido: após processar componentes nível 1, para cada componente INTERMEDIATE, busca `bomByParent.getOrDefault(component.getDynamicsCode(), List.of())` e processa RAW_MATERIAL de nível 2 com quantidade ajustada (`nivel1.quantity × nivel2.quantity × suggestedQty`); usa o mesmo `bomByParent` pré-carregado (sem queries adicionais — ADR-045 Decisão 2)
2. INTERMEDIATE de nível 2 são ignorados (no MVP apenas 2 níveis — log de warning adicionado: `"BOM com profundidade > 2 detectado para PROD-{code} — nível 3+ ignorado"`)
3. Testes unitários: (a) FINISHED → [INTERMEDIATE(1.0×) → [RAW-A(2.0×)]] → purchaseNeeds RAW-A com `suggestedQty * 1.0 * 2.0`; (b) FINISHED → [RAW-B(0.5×), INTERMEDIATE(1.0×) → [RAW-C(3.0×)]] → 2 purchaseNeeds: RAW-B e RAW-C; (c) INTERMEDIATE sem BOM cadastrado → nenhuma purchaseNeed adicional (sem NPE)

**Frontend**
4. `ProductBomComponent` atualizado: tabela BOM exibe componentes de nível 2 com indentação visual (`padding-left: 24px` para `level === 2`); chip de tipo diferenciado para nível 2 (cor secundária); coluna "Nível" adicionada
5. Teste: componente renderiza nível 2 com indentação; nível 1 sem indentação

---

#### US-106 — Tech debt Sprint 33 (2 pts)

**Backend**
1. **SEC-119** — em `GetPlanningSummaryUseCase.escapeCsv()`: adicionar sanitização de formula injection — se `value` começa com `=`, `+`, `-` ou `@`, prefixar com `\t` (tab) antes do conteúdo; `\t` é inerte visualmente no Excel mas quebra o parse de fórmula; teste: valor `"=SUM(A1)"` → CSV cell `"\t=SUM(A1)"` (não interpretado como fórmula)
2. **WebhookUrlValidatorTest** — adicionar `@Tag("requires-network")` à classe; documentar no `pom.xml` o perfil de exclusão recomendado para CI: `<excludedGroups>requires-network</excludedGroups>` no plugin surefire; teste: verificar que a tag está presente e que o teste `validate_publicUrl_doesNotThrow` é o único na classe (isolado)

**Frontend**
3. **npm audit fix** — executar `npm audit fix` no diretório `apps/frontend/` para corrigir qs (GHSA-q8mj-m7cp-5q26) e ws (GHSA-58qx-3vcg-4xpx); verificar que nenhum componente de produção foi afetado (ambos são devDependencies via jsdom/vitest); registrar versões corrigidas no log

---

### 📋 Tech Debt Sprint 34

> Itens identificados durante implementação/revisão — diferidos para Sprint 35:

- **⚠️ @Cacheable production-overview** — `GetProductionOverviewUseCase.getOverview()` computa a cada request; ADR-045 Decisão 3 prevê TTL 5 min via Spring Cache Caffeine; diferido pois `CaffeineCacheManager` não está configurado no projeto. Sprint 35.
- **⚠️ NgxCharts line chart** — US-104 AC-7 especificou `NgxChartsLineChartComponent` para tendência de eficiência; `@swimlane/ngx-charts` não é dependência do projeto; tabela HTML funcional entregue. Instalar + implementar gráfico interativo em Sprint 35.

---

## Sprint 35 ⬜
**Objetivo**: Performance e UX do painel executivo — Spring Cache Caffeine (TTL 5 min) no `GetProductionOverviewUseCase` + gráfico de tendência NgxCharts + liquidação de tech debt de segurança pendente (SEC-112, SEC-113, SEC-069)
**ADR**: ADR-046
**Status**: pendente
**Pontos totais**: 7 pts (US-107: 2 + US-108: 3 + US-109: 2)

### User Stories
| ID | Título | Pontos | Status |
|----|--------|--------|--------|
| US-107 | Spring Cache Caffeine — TTL 5 min no painel executivo de produção | 2 | ⬜ pendente |
| US-108 | NgxCharts gráfico de tendência de eficiência no painel executivo | 3 | ⬜ pendente |
| US-109 | Tech debt security — SEC-112 (IOException), SEC-113 (importedBy exposto), SEC-069 (null check morto) | 2 | ⬜ pendente |

### Dependências
- US-107 depende de Sprint 34 (GetProductionOverviewUseCase já implementado)
- US-108 depende de Sprint 34 (ProductionOverviewComponent já implementado com tabela; substitui apenas a seção de tendência)
- US-109 é independente — pode ser iniciado a qualquer momento

### Sequência de entrega sugerida
1. US-109 (tech debt security — cirúrgico, sem risco de regressão)
2. US-107 backend (configuração Caffeine + @Cacheable)
3. US-108 frontend (NgxCharts installation + component update)

---

#### US-107 — Spring Cache Caffeine no painel executivo (2 pts)

**Contexto**: `GetProductionOverviewUseCase.getOverview()` agrega 4 repositórios a cada request. Com múltiplos gestores abrindo o painel simultaneamente, o custo é multiplicado. ADR-045 Decisão 3 previu TTL de 5 min via `@Cacheable`, diferido por falta de `CaffeineCacheManager`. Esta US instala e configura o cache.

**Backend**
1. Adicionar dependência `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` ao `pom.xml` (se ainda não presente)
2. Criar `CacheConfig` (`@Configuration @EnableCaching`) em `common/` com bean `CaffeineCacheManager`: cache `"production-overview"` com TTL de 5 minutos e máximo de 100 entradas
3. Anotar `GetProductionOverviewUseCase.getOverview()` com `@Cacheable(value = "production-overview", key = "'overview'")`
4. Testes: (a) segunda chamada a `getOverview()` não invoca repositórios novamente (verificar com `verify(repo, times(1))`); (b) bean `CacheManager` existe no contexto Spring com cache `"production-overview"` configurado

---

#### US-108 — NgxCharts gráfico de tendência de eficiência (3 pts)

**Contexto**: US-104 entregou tabela HTML como equivalente funcional à falta do `@swimlane/ngx-charts`. Esta US substitui a tabela por um `LineChartComponent` interativo com eixo Y 0–100% e linha de referência em 80% (meta de eficiência da fábrica).

**Frontend**
1. Instalar `@swimlane/ngx-charts@latest` compatível com Angular 21 (`npm install @swimlane/ngx-charts --legacy-peer-deps` se necessário); registrar versão instalada no log
2. Atualizar `ProductionOverviewComponent`: substituir tabela de tendência por `<ngx-charts-line-chart>` com propriedades: `[results]="chartData()"`, `[xAxis]="true"`, `[yAxis]="true"`, `[yScaleMin]="0"`, `[yScaleMax]="100"`, `[animations]="false"` (obrigatório em OnPush)
3. Adicionar `computed()` `chartData` que transforma `trendRows()` no formato `[{ name: 'Eficiência', series: [{ name: date, value: avgEfficiency }] }]` esperado pelo NgxCharts
4. Linha de referência em 80%: usar `[referenceLines]="[{ value: 80, name: 'Meta 80%' }]"` e `[showRefLines]="true"`
5. Manter tabela HTML como fallback acessível com `aria-label="Dados de eficiência (tabela)"` — visível somente para leitores de tela (`class="sr-only"`)
6. Testes Vitest: (a) `chartData()` transforma `trendRows` corretamente (série com N entradas); (b) `<ngx-charts-line-chart>` é renderizado quando há dados; (c) tabela sr-only presente mesmo com gráfico ativo

---

#### US-109 — Tech debt security (2 pts)

**Contexto**: Itens diferidos das revisões de Beatriz em Sprints anteriores, todos classificados como MEDIUM/LOW sem sprint definida. Esta US os liquida de forma cirúrgica.

**Backend**
1. **SEC-112** — 5 use cases com `catch (Exception e) { log.warn(..., e.getMessage()) }` — substituir `e.getMessage()` por mensagem genérica no log público e mover detalhe da exceção para `log.warn("...", e)` (passa a exceção completa, não só a mensagem); garante que stack trace aparece apenas no log do servidor, nunca exposto via response; identificar os 5 use cases no log de Beatriz e corrigir todos
2. **SEC-113** — `CycleTimeResponse` (ou DTO equivalente) expõe campo `importedBy` (username de quem importou) no response público; remover campo ou mover para endpoint ADMIN-only; teste: `GET /api/v1/production/cycle-times` retorna DTO sem campo `importedBy` para role OPERATOR

**Backend + Frontend**
3. **SEC-069** — `Principal null check` morto identificado por Beatriz: localizar o check e remover ou converter em `@PreAuthorize` adequado; se o endpoint deveria ser público, documentar; teste: verificar comportamento correto (autenticado vs anônimo)

**Testes**
4. Cada fix deve ter teste unitário ou de integração verificando o comportamento corrigido; nenhum teste existente deve quebrar
