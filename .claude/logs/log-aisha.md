# Log Aisha

## [2026-06-04] Sincronização Sprints 40–46 — ADRs 051–057

Fontes lidas:
  - CLAUDE.md (raiz)
  - docs/adr/ADR-051-sprint40-training-competency.md
  - docs/adr/ADR-052-sprint41-calibration-msa.md
  - docs/adr/ADR-053-sprint42-internal-audits.md
  - docs/adr/ADR-054-sprint43-risk-fmea.md
  - docs/adr/ADR-055-sprint44-change-control.md
  - docs/adr/ADR-056-sprint45-customer-complaints-mdr.md
  - docs/adr/ADR-057-sprint46-management-review.md
  - .claude/sprint-atual.md (seções Sprint 40–46 e tabela de roadmap)
  - .claude/logs/log-aisha.md (histórico)

Mudanças feitas:
  - CLAUDE.md: Domain Modules — Sprint 46: pacote corrigido de `common/` para `common/presentation/`, `common/application/` (conforme ADR-057 Decisão 1 — `ManagementReviewController` em `common/presentation/`)
  - CLAUDE.md: Key Conventions — 14 novas convenções dos ADRs 051–057 adicionadas:
    - `training/` como pacote top-level; referência leve `username: String` em `TrainingRecord`; `expiresAt` calculado no use case
    - `GedFileValidator` reutilizado em `training/` para certificados PDF
    - `TrainingExpiryAlertJob` — job semanal com debounce; notificação pessoal (não broadcast)
    - Calibração em `maintenance/domain/` — sem novo pacote; `certificateDocumentId` vs `certificateStoragePath` mutuamente exclusivos
    - NC automática `OUT_OF_TOLERANCE` com `recordedBy = "system"` na mesma `@Transactional`
    - `qms/audit/` sub-pacote; `InternalAudit` (nome explícito); `AuditController` dedicado; `NcSeverity` reutilizado em `AuditFinding`
    - Padrão de código sequencial `AUD-/CR-/REC-{ANO}-{NNN}` com unique constraint
    - `qms/risk/`; RPN calculado e persistido via `recalculateRpn()`; `RiskLevel.fromRpn()` factory; `CRITICAL→ACCEPTED` bloqueado
    - `common/changes/` transversal; regra de identidade `requestedBy == principal` no use case
    - `qms/complaints/`; notificação ANVISA exclusiva ADMIN; MDR disponível apenas quando `reportedToAnvisa=true AND CLOSED`
    - `ManagementReviewData` como DTO record (sem entidade JPA); cache 30 min; `SemaphoreChipComponent` em `shared/`
  - sprint-atual.md: US-135 AC#2 — período máximo corrigido de "365 dias" para "366 dias" (`ChronoUnit.DAYS.between(from, to) > 366`) — ADR-057 especifica `> 366` para suportar anos bissextos; mensagem de erro também corrigida
  - sprint-atual.md: US-135 AC#7 — teste "(b) período > 365 dias" corrigido para "período > 366 dias" — alinhado ao ADR-057

Inconsistências encontradas (ADR prevalece):
  1. CLAUDE.md Sprint 46: pacote `common/` genérico demais — ADR-057 especifica `common/presentation/` e `common/application/`; corrigido
  2. sprint-atual.md US-135 AC#2: "período máximo 365 dias" — ADR-057 usa `> 366` (equivalente a máximo 366 dias, comportamento diferente); corrigido
  3. sprint-atual.md US-135 AC#7: teste referenciava "365 dias" consistente com AC#2 incorreto; corrigido

Verificações sem inconsistência:
  - Emojis dos headers Sprints 40–46: todos `⬜` (correto para planejado)
  - ADRs referenciados: ADR-051 a ADR-057 linkados corretamente em cada sprint
  - Tabela de roadmap: linhas Sprints 40–46 presentes com emojis `⬜` e ADRs corretos
  - Sprint 40: pacote `training/` e US-118/119/120 alinhados com ADR-051
  - Sprint 41: entidades em `maintenance/domain/` e US-121/122/123 alinhados com ADR-052
  - Sprint 42: sub-pacote `qms/audit/`, `AuditController`, `InternalAudit` alinhados com ADR-053; transição `IN_PROGRESS→CANCELLED` bloqueada (422) correta
  - Sprint 43: sub-pacote `qms/risk/`, RPN calculado, `CRITICAL→ACCEPTED` sem mitigação=422 alinhados com ADR-054
  - Sprint 44: pacote `common/changes/`, 6 estados, segregação de papéis alinhados com ADR-055
  - Sprint 45: `qms/complaints/`, `ComplaintController`, notificação ANVISA ADMIN-only alinhados com ADR-056
  - Sprint 46: `ManagementReviewController` em `common/presentation/`, cache 30 min, `SemaphoreChipComponent` alinhados com ADR-057 (exceto período já corrigido)

---

## [2026-06-04] Sincronização Sprint 39 — ADR-050 vs sprint-atual.md
Fontes lidas:
  - CLAUDE.md (raiz)
  - docs/adr/ADR-050-sprint39-nc-ged-link-capa-aging-quality-report.md
  - .claude/sprint-atual.md (seção Sprint 39)
  - .claude/logs/log-aisha.md (histórico)

Mudanças feitas:
  - CLAUDE.md: Domain Modules — nova linha Sprint 39 (`NC↔GED Link + CAPA Aging Dashboard + Relatório Executivo de Qualidade (iText 7) | qms/, qms/ged/ | 39 | 🚧 em andamento`)
  - CLAUDE.md: Key Conventions — 4 novas convenções: `NcDocumentLink` em `qms/domain/`, DELETE semântico `{documentId}`, iText 7 Community (não iText 2.1.7), `QmsReportController` dedicado
  - sprint-atual.md: `ADR-050 (a criar)` → `ADR-050` (ADR já aprovado desde 2026-06-04)
  - sprint-atual.md US-115 AC#4: removido `linkedBy` (não exposto via API — padrão ADR-049 §4) e `documentRevisionNumber`; adicionados `documentCategory`, `documentStatus` conforme ADR-050 §3
  - sprint-atual.md US-115 AC#6: URL corrigida de `{linkId}` para `{documentId}` — URL semântica conforme ADR-050 §2
  - sprint-atual.md US-115 AC#7: auth level corrigido de SUPERVISOR+ para OPERATOR+ conforme ADR-050 §3 (tabela de contratos)
  - sprint-atual.md US-116 AC#1: corrigido — dueDate já existe na entidade (ADR-007/ADR-048); nenhuma migration necessária conforme ADR-050 §4
  - sprint-atual.md US-116 AC#4: shape do `CapaAgingResponse` reescrito — campos `totalOpen`, `overdueCount`, `noDueDateCount`, buckets nomeados (`bucket0to7`, `bucket8to15`, `bucket16to30`, `bucketOver30`) conforme ADR-050 §4/§5
  - sprint-atual.md US-117 AC#1: dependência corrigida de `com.lowagie:itext:2.1.7` para `com.itextpdf:itext7-core:7.2.6` (iText 7 Community AGPL) conforme ADR-050 §6
  - sprint-atual.md sequência de desenvolvimento item 5: removida referência a JasperReports (descartado no ADR-050 §6)

Inconsistências encontradas (ADR-050 prevalece):
  1. ADR referenciado como "(a criar)" — ADR já existia e estava aprovado
  2. `linkedBy` indevidamente exposto no response (violava padrão ADR-049 §4) — removido
  3. `documentRevisionNumber` incluído no response sem previsão no ADR — removido
  4. DELETE URL usava `{linkId}` (UUID interno) — corrigido para `{documentId}` (semântica de URL, conforme ADR-050 §2 UnlinkNcFromDocumentUseCase)
  5. GET inverso `/ged/.../non-conformances` com auth SUPERVISOR+ — corrigido para OPERATOR+ (ADR-050 §3 tabela)
  6. US-116: migration de `dueDate` prevista quando campo já existe — AC corrigido
  7. US-116: `CapaAgingResponse` com shape divergente (totalPending/totalDone/avgResolutionDays) — alinhado ao record Java do ADR-050 §4
  8. US-117: iText 2.1.7 (obsoleto, MPL) especificado em vez de iText 7 Community 7.2.6 (AGPL) — ADR-050 §6 descarta explicitamente a versão antiga
  9. US-117: JasperReports listado como opção — descartado no ADR-050 §6

---

## [2026-06-04] Sincronização pós-Sprint 38
Fontes lidas:
  - CLAUDE.md
  - ADR-049 (Sprint 38 — GED & CAPAS Security Hardening)
  - sprint-atual.md (seção Sprint 38)
  - log-mateus.md, log-tadeu.md, log-maiana.md, log-beatriz.md, log-maite.md, log-athos.md, log-atlas.md

Mudanças feitas:
  - CLAUDE.md: Domain Modules — nova linha Sprint 38 (`GED & CAPAS Security Hardening | qms/ged/, qms/ | 38 | ✅ done`)
  - CLAUDE.md: Key Conventions — 4 novas notas: `GedFileValidator` pattern, PII masking em `DocumentRevisionResponse`, `DataIntegrityViolationException` → domain exception 409, TOCTOU fix via `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `findByIdForUpdate`
  - sprint-atual.md: header `## Sprint 38 🔄` → `## Sprint 38 ✅` (inconsistência com `Status: concluída`)
  - sprint-atual.md: roadmap — adicionada linha `✅ Sprint 38 | GED & CAPAS Security Hardening | US-113, US-114 | ADR-049` (ausente)

Inconsistências encontradas:
  - Sprint 38 tinha `🔄` no header mas `Status: concluída` no corpo — corrigido para `✅`
  - Linha do Sprint 38 ausente da tabela de roadmap — adicionada

Tech debt pendente: nenhum — todos os itens SEC-125 a SEC-139 encerrados pelo Sprint 38 (confirmado por Beatriz)

---

## [2026-06-04] Sincronização pós-Sprint 36 e 37
Fontes lidas:
  - CLAUDE.md
  - ADR-047 (GED), ADR-048 (CAPAS)
  - log-mateus.md, log-tadeu.md, log-maiana.md, log-beatriz.md, log-maite.md

Mudanças feitas:
  - CLAUDE.md: Domain Modules — Sprint 36 (GED) e Sprint 37 (CAPAS Formal): `⬜ planned` → `✅ done`
  - CLAUDE.md: Key Conventions — adicionadas 3 notas relevantes para devs: estrutura do sub-módulo `qms/ged/`, renomeação `GedGetDownloadUrlUseCase` (conflito de bean), e `CapaController` separado do `QmsController`
  - sprint-atual.md: nenhuma alteração necessária (Sprint 36 e 37 já estavam como `✅ concluída` com US marcadas como `✅ concluído`)
  - Inconsistências encontradas: nenhuma

Tech debt pendente (segurança):
  - SEC-125 (HIGH) — GED: upload sem validação MIME type via magic bytes (Apache Tika) em UploadDocumentUseCase e AddRevisionUseCase — previsto Sprint 38
  - SEC-126 (HIGH) — GED: path traversal no storagePath via getOriginalFilename() e code não sanitizados — previsto Sprint 38
  - SEC-138 (MEDIUM) — CAPAS: SubmitForEffectivenessUseCase lança IllegalArgumentException→400 em vez de 422 — previsto Sprint 38
  - SEC-139 (LOW) — TOCTOU auto-close NC em VerifyEffectivenessUseCase — previsto Sprint 38

Observações:
  - sprint-atual.md não possui seção "Tech Debt diferido" para os Sprints 36 e 37; conforme protocolo, não foi criada (SEC-125/126/138/139 registrados apenas neste log)
  - log-beatriz.md Sprint 36 lista também SEC-127/128/129 diferidos "para Sprint 37" — porém o log de Beatriz do Sprint 37 (US-112) não menciona esses itens como resolvidos; confirmação de resolução pendente com Beatriz/Mateus

---

## [2026-06-01] Sincronização Sprints 36 e 37 — GED e CAPAS Formal
Fontes lidas: CLAUDE.md, sprint-atual.md, log-athos.md, log-atlas.md, ADR-047, ADR-048
Mudanças feitas:
  - `CLAUDE.md` — Domain Modules: adicionadas linhas Sprint 36 (GED, `qms/ged/`, ⬜ planned) e Sprint 37 (CAPAS Formal, `qms/`, ⬜ planned)
  - `sprint-atual.md` — roadmap: adicionadas linhas ⬜ Sprint 36 e ⬜ Sprint 37 (já feito pelo Athos)
  - Consistência verificada: ADR-047 referencia US-110/US-111 ✓; ADR-048 referencia US-112 ✓; sprint-atual.md referencia ADR-047/ADR-048 ✓

## [2026-06-01] Sincronização pós-Sprint 35 + Design System MSB
Fontes lidas: CLAUDE.md, sprint-atual.md, log-mateus.md, log-tadeu.md, log-maiana.md, log-maite.md, log-beatriz.md, log-athos.md
Mudanças feitas:
  - `sprint-atual.md` — roadmap: Sprint 35 `⬜` → `✅`
  - `sprint-atual.md` — header "## Sprint 35 ⬜" → "## Sprint 35 ✅"; Status: `pendente` → `concluída`
  - `sprint-atual.md` — US-107, US-108, US-109: `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md` — adicionada seção "Tech Debt diferido do Sprint 35": SEC-123 INFO (@swimlane/ngx-charts alpha, monitorar upgrade para release estável)
  - `CLAUDE.md` — Domain Modules: adicionada linha Sprint 35 (Cache Caffeine + NgxCharts + SEC-112/113/069)
  - `CLAUDE.md` — Visual Identity: caminhos dos logos corrigidos SVG → PNG (public/), adicionada nota sobre shell layout (topbar/sidebar)
Observações extras (entregas fora do ciclo de sprints):
  - Design System MSB implementado: logos PNG substituídos em nav e login; tokens de cor do Brand Manual aplicados globalmente
  - Shell SPShell: topbar com gradiente + sidebar dark com seções OPERAÇÃO/PRODUÇÃO/ADMIN/CONTA; hamburger drawer mobile (≤900px)
  - Responsividade adicionada em kpi-dashboard, qms/nc-list, oee/dashboard; sidebar vira overlay no mobile
  - Breadcrumb simplificado: "Planta SP-01 / PageTitle" → apenas "PageTitle"
  - Limpeza de código: DashboardService removido (órfão), polling duplicado de MaintenanceService removido do NavComponent, kpi-dashboard.spec.ts reescrito para KpiService atual, ShellStateService criado para coordenar estado do sidebar

---

## [2026-06-01] Sincronização pós-Sprint 34
Fontes lidas: CLAUDE.md, sprint-atual.md, log-mateus.md, log-tadeu.md, log-maiana.md, log-maite.md, log-beatriz.md, log-athos.md, log-atlas.md
Mudanças feitas:
  - `.claude/sprint-atual.md` — roadmap: Sprint 34 `⬜` → `✅`
  - `.claude/sprint-atual.md` — header Sprint 34: `⬜` → `✅`, Status: `pendente` → `concluída`
  - `.claude/sprint-atual.md` — US-104, US-105, US-106: `⬜ pendente` → `✅ concluído`
  - `.claude/sprint-atual.md` — adicionado bloco "Tech Debt Sprint 34" com 2 itens diferidos (Caffeine cache + NgxCharts) para Sprint 35
  - `CLAUDE.md` — Domain Modules: adicionada linha `Production Overview + BOM Level 2 MRP | production/ | 34 | ✅ done`

---

## [2026-06-01] Sincronização pós-Sprint 33
Fontes lidas: CLAUDE.md, sprint-atual.md, log-mateus.md, log-tadeu.md, log-maiana.md, log-maite.md, log-beatriz.md
Mudanças feitas:
  - sprint-atual.md: roadmap linha Sprint 33 `⬜` → `✅`
  - sprint-atual.md: header "## Sprint 33 ⬜" → "## Sprint 33 ✅"
  - sprint-atual.md: status `pendente` → `concluída`
  - sprint-atual.md: US-101, US-102, US-103 → `⬜ pendente` → `✅ concluído`
  - sprint-atual.md: seção "Tech Debt diferido do Sprint 33" adicionada — SEC-119 (LOW, CSV formula injection, Sprint 34) + 2 ⚠️ aceitáveis (INTERMEDIATE BOM + CSV content test)
  - CLAUDE.md: tabela Domain Modules — nova linha: "BOM Import + Planning Report | production/ | 33 | ✅ done"
Observações:
  - Sprint 33 passou por 2 ciclos de review: blocker inicial (MrpCalculationServiceTest + RejectMrpSuggestionRequestTest ausentes) → Mateus corrigiu → Maiana re-aprovou
  - Tadeu corrigiu window.open sem noopener,noreferrer em exportCsv() (alinhamento com padrão SEC-074)
  - Suite final: 479 testes backend, 653 testes frontend — 0 falhas; BUILD SUCCESS em ambos
  - WebhookUrlValidatorTest (DNS timeout, Sprint 27) passou nesta execução — flaky por ambiente, documentado
  - Beatriz: SEC-119 (LOW) diferido Sprint 34; SEC-116/117/118 todos corrigidos e fechados
  - Maitê: ready-to-deploy; 2 moderate audit frontend (qs + ws via jsdom/vitest devDeps) pré-existentes
  - ADR-044 criado por Atlas: ProductComponent entity, BOM import strategy, BOM×MRP integration, Excel template, Planning report endpoint, CSV export, SEC-116/117/118 fixes

## [2026-05-30] Sincronização pós-Sprint 32
Fontes lidas: CLAUDE.md, sprint-atual.md, log-mateus.md, log-tadeu.md, log-maiana.md, log-maite.md, log-beatriz.md, log-atlas.md, log-athos.md
Mudanças feitas:
  - sprint-atual.md: roadmap linha Sprint 32 `⬜` → `✅`
  - sprint-atual.md: header "## Sprint 32 ⬜" → "## Sprint 32 ✅"; status `pendente` → `concluída`
  - sprint-atual.md: US-085, US-086, US-087, US-100 → `⬜ pendente` substituído por `✅ concluído`
  - sprint-atual.md: seção "Tech Debt diferido do Sprint 32" adicionada ao final — SEC-116 (LOW) + INFO SEC-117/SEC-118
  - CLAUDE.md: tabela Domain Modules — nova linha adicionada: "MRP Engine + Staffing + Planning Board | production/ | 32 | ✅ done"
Observações:
  - BUG-1 (ImportProductionOrdersUseCase sem cálculo de staffing para novas OPs) e BUG-2 (findSuggestionFromEntry usando UUID truncado) eram blockers — corrigidos por Mateus e Tadeu antes da aprovação final de Maiana
  - BUG-3 (NoMrpRunException com mensagem confusa de UUID zerado) era não-blocker — corrigido por Mateus com exception específica
  - Suite final: 466 testes backend, 642 testes frontend — 0 falhas
  - Maitê: ready-to-deploy; Beatriz: aprovado sem CRITICAL/HIGH; Maiana: re-revisão aprovada pós-fix
  - ADR-043 criado por Atlas cobrindo: StaffingConfig singleton lazy, MrpCalculationService stateless, SUPERSEDED como 5º status, BusinessDaysCalculator em application/util/, @Formula para totalOrders, Timeline CSS Grid puro, /convert endpoint, estrutura de pacotes
  - SEC-116 (LOW — @Size em reason) diferido para Sprint 33; SEC-117/SEC-118 são INFO sem ação urgente
  - Inconsistência ADR-030 (endpoint /convert ausente) resolvida pelo ADR-043 Decisão 7 — sem necessidade de correção no sprint-atual.md (ADR-043 prevalece como complemento de implementação)

## [2026-05-29] Sincronização pós-Sprint 31
Fontes lidas: CLAUDE.md, sprint-atual.md, log-mateus.md, log-tadeu.md, log-maiana.md, log-maite.md
Mudanças feitas:
  - sprint-atual.md: roadmap linha Sprint 31 → ⬜ substituído por ✅
  - sprint-atual.md: header "## Sprint 31 ⬜" → "## Sprint 31 ✅"; status "pendente" → "concluída"
  - sprint-atual.md: US-084 e US-099 → ⬜ pendente substituído por ✅ concluída
  - CLAUDE.md: tabela Domain Modules — 2 novas linhas adicionadas: "Production Tracking (kanban por família)" Sprint 30 e "Sterilization Loads (Hub-managed)" Sprint 31, ambas ✅ done
Observações:
  - BUG-1 (audit "from" field) corrigido por Mateus; teste de regressão adicionado → suite backend 447 testes
  - AC#15 (released reminder dialog) e AC#17 (3 testes Vitest) implementados por Tadeu → suite frontend 603 testes
  - Gap AC#12 (badge totalOrders nos cards da lista) documentado por Maiana — diferido para Sprint 32 (sugestão: adicionar totalOrders ao SterilizationLoadResponse)
  - Maitê ainda não validou Sprint 31 — sincronização feita com base nos logs de Mateus/Tadeu e na revisão final de Maiana (blockers fechados)

## [2026-05-29] Sincronização pós-Sprint 30
Fontes lidas:
- `CLAUDE.md` (raiz)
- `docs/adr/ADR-041-production-kanban-tracking.md` (novo — Atlas, Sprint 30)
- `.claude/sprint-atual.md`
- `.claude/logs/log-mateus.md` (entradas Sprint 30: US-082, US-098, blockers Maiana+Beatriz)
- `.claude/logs/log-tadeu.md` (entradas Sprint 30: US-083, US-098 frontend, blocker SEC-114)
- `.claude/logs/log-maite.md` (pipeline Sprint 30: 434 backend + 553 frontend — READY-TO-DEPLOY)

Mudanças feitas:
- `.claude/sprint-atual.md`: Sprint 30 marcada ✅ na tabela de visão geral e no cabeçalho da seção
- `.claude/sprint-atual.md`: `**ADR**` da Sprint 30 atualizado de `ADR-029` para `ADR-029, ADR-041` (ADR-041 criado por Atlas para kanban tracking)
- `.claude/sprint-atual.md`: Status da Sprint 30 alterado de `pendente` → `concluída`
- `.claude/sprint-atual.md`: US-082, US-083, US-098 marcadas `✅ concluído`

Observações:
- SEC-115 (INFO) diferido para Sprint 31 por Beatriz — não representa blocker; sem ação documental nesta sincronização
- ADR-041 cobre decisões de kanban: 7 colunas, 50 OPs/coluna com flag `truncated`, polling 60s via RxJS `interval().pipe(takeUntilDestroyed())`, `displayStatus` calculado em Java (nunca persiste)
- Sprint 31 permanece ⬜ (planejamento a cargo de Athos)

---

## [2026-05-28] Sincronização pós-Sprint 29
Fontes lidas:
- `CLAUDE.md` (raiz — tabela Domain Modules)
- `.claude/sprint-atual.md` (estado atual do backlog)
- `.claude/logs/log-mateus.md` (Sprint 29 — US-079/080/081/097 backend + blockers Beatriz/Maiana; 420 testes)
- `.claude/logs/log-maiana.md` (Sprint 29 — 56/58 ACs cobertos; blocker US-081 AC#6 corrigido por Mateus; aprovado 58/58)
- `.claude/logs/log-maite.md` (Sprint 29 — ready-to-deploy; 420 backend, 540 frontend; aprovado Maitê)
- `.claude/logs/log-beatriz.md` (Sprint 29 — aprovado com ressalvas; SEC-101/102/103/104/105/106 CORRIGIDOS; SEC-107 HIGH, SEC-108/109 MEDIUM, SEC-110 LOW, SEC-111 INFO diferidos Sprint 30)

Mudanças feitas:
- `CLAUDE.md`: nova linha na tabela Domain Modules — `Production (Dynamics import) | production/ | 29 | ✅ done`
- `.claude/sprint-atual.md`: Sprint 29 `⬜` → `✅`; status `pendente` → `concluída`
- `.claude/sprint-atual.md`: US-079, US-080, US-081, US-097 `⬜ pendente` → `✅ concluído`
- `.claude/sprint-atual.md`: roadmap linha Sprint 29 `⬜` → `✅`
- `.claude/sprint-atual.md`: seção "Tech Debt diferido do Sprint 29 (Beatriz — SEC-107 a SEC-111)" adicionada antes das User Stories do Sprint 30 com tabela descritiva (severidade, descrição, fix proposto)

Inconsistências encontradas:
- SEC-111 (INFO de Beatriz): discrepância de URLs entre `ProductionService` (frontend) e `ProductionController` (backend) nas chamadas de import. Documentado no tech debt Sprint 30 para correção. Não altera os ACs do Sprint 29 (Maiana aprovou com justificativa de review de código para SEC-111 como INFO, não blocker).
- Sprint 29 já tinha ADR-028 referenciado corretamente no roadmap e na seção de sprint — nenhuma correção adicional necessária.
- SEC-109 é o único item novo com impacto de produto (decisão de exposição de `importedBy` para SUPERVISOR) — diferido com nota de decisão necessária para Sprint 30.

## [2026-05-26] Sincronização pós-Sprint 28 (pós-commit)
Fontes lidas:
- `CLAUDE.md` (raiz — tabela Domain Modules)
- `.claude/sprint-atual.md` (estado atual do backlog)
- `.claude/logs/log-mateus.md` (Sprint 28 — US-077 backend + US-096 backend; 381 testes)
- `.claude/logs/log-tadeu.md` (Sprint 28 — US-078 frontend + US-096 frontend; 515 testes)
- `.claude/logs/log-maite.md` (Sprint 28 — ready-to-deploy; 381 backend, 515 frontend; aprovado Maitê)
- `.claude/logs/log-maiana.md` (Sprint 28 — 35/35 ACs cobertos após fixes dos 7 blockers; aprovado Maiana)
- `.claude/logs/log-beatriz.md` (Sprint 28 — aprovado com ressalvas; 6 itens diferidos SEC-101 a SEC-106 para Sprint 29)
- `docs/adr/` — todos os ADRs verificados; ADR-027 (dashboard) existente e linkado corretamente no Sprint 28

Mudanças feitas:
- `CLAUDE.md`: linha `Cross-module KPI + Reports + Analytics` — coluna Sprints atualizada de `9–10, 16, 24` para `9–10, 16, 24, 28` (Sprint 28 adicionou dashboard customizável com persistência de layout, que expande o módulo KPI/dashboard existente)
- `.claude/sprint-atual.md`: seção "Tech Debt diferido do Sprint 28 (Beatriz — SEC-101 a SEC-106)" adicionada antes das User Stories do Sprint 29, com tabela descritiva de cada item (severidade, descrição, fix proposto)

Inconsistências encontradas:
- Sprint 28 já estava corretamente marcada como ✅ com ADR-027, USs como ✅ concluído e linha do roadmap como ✅ — nenhuma correção necessária nesse ponto.
- ADR-027 existe em `docs/adr/ADR-027-customizable-dashboard.md` e está linkado corretamente no sprint-atual.md — consistente.
- Os itens SEC-101 a SEC-106 diferidos por Beatriz não estavam documentados no sprint-atual.md na Sprint 29; adicionados para rastreabilidade (padrão das sincronizações anteriores).
- `CLAUDE.md` não tinha referência à Sprint 28 no módulo KPI/Analytics; o dashboard customizável (US-077/078) é extensão do módulo `common/kpi/` e `common/presentation/` existente — Sprint 28 adicionada.

## [2026-05-26] Sincronização pós-Sprint 28
Fontes lidas:
- `.claude/logs/log-mateus.md` (Sprint 28 — US-077 backend + US-096 backend)
- `.claude/logs/log-tadeu.md` (Sprint 28 — US-078 frontend + US-096 frontend)
Mudanças feitas:
- `.claude/sprint-atual.md` — Sprint 28 marcada ✅; US-077, US-078, US-096 → ✅ concluído; linha da tabela de roadmap Sprint 28 ⬜ → ✅

## [2026-05-26] Sincronização pós-Sprint 27
Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 27 — US-071, US-072; Sprint 28 — US-077, US-078)
- `CLAUDE.md` (tabela Domain Modules)
- `.claude/logs/log-maite.md` (Sprint 27 — ready-to-deploy; 355 backend, 511 frontend; BUG-S27-01/02/03 corrigidos)
- `.claude/logs/log-beatriz.md` (Sprint 27 — aprovado com ressalvas; SEC-092/095 CORRIGIDOS em `fa3dc19`; SEC-088/089/090/093/094/096/097 diferidos Sprint 28)
- `.claude/logs/log-maiana.md` (Sprint 27 — 34/38 ACs; BUG-S27-01/02/03 corrigidos; gaps aceitáveis documentados)
- `.claude/logs/log-athos.md` (Sprint 27 planejamento; Sprint 28 US-096 novo)
- `.claude/logs/log-atlas.md` (ADR-040 criado — Sprint 27 implementation decisions)
- `docs/adr/ADR-040-sprint27-implementation-decisions.md` (criado nesta sessão)
- Commits recentes: `fa3dc19` (SSRF + audit logging fixes), `e887575` (Angular alignment), `0f578f1` (US-072 dispatch), `f8b1ed0` (US-071 subscription management)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 27 `⬜` → `✅`; status `backend concluído` → `concluída`
  - `sprint-atual.md`: US-071 e US-072 `✅ backend concluído` → `✅ concluído`
  - `sprint-atual.md`: `**ADR**: ADR-024` → `**ADRs**: ADR-024, ADR-040` (ADR-040 criado pelo Atlas nesta sessão)
  - `sprint-atual.md`: seção "Tech Debt diferido do Sprint 27" adicionada com SEC-088/089/090/093/094/096/097 pendentes e SEC-092/095 marcados como ✅ CORRIGIDOS em `fa3dc19`
  - `sprint-atual.md`: roadmap linha Sprint 27 `⬜` → `✅`; ADR-040 adicionado na linha do roadmap
  - `sprint-atual.md`: Sprint 28 — US-096 (tech debt Sprint 27, 3 pts) adicionado à tabela; total atualizado para 10 pontos
  - `sprint-atual.md`: Sprint 28 — objetivo atualizado para incluir "liquidação do tech debt de segurança Sprints 26–27"
  - `sprint-atual.md`: Sprint 28 — US-077 expandido de 5 para 10 ACs detalhados e verificáveis
  - `sprint-atual.md`: Sprint 28 — US-078 expandido de 8 para 13 ACs detalhados (carregamento, edit mode, drag-and-drop, catálogo lateral, salvar/resetar, specs)
  - `sprint-atual.md`: Sprint 28 — US-096 adicionado com 22 ACs cobrindo SEC-088 a SEC-100

Inconsistências verificadas:
  - **SEC-092 e SEC-095 já corrigidos**: o log de Beatriz listava ambos como "diferidos Sprint 28", mas o commit `fa3dc19` ("add SSRF protection, webhook audit logging") aplicou os fixes ainda na Sprint 27. Sprint-atual.md reflete o estado real: SEC-092/095 marcados como ✅ CORRIGIDOS, apenas SEC-088/089/090/093/094/096/097 permanecem pendentes.
  - **ADR-040 ausente**: referenciado na Sprint 27 como ADR de implementação (padrão dos ADRs de sprint — ADR-031 a ADR-039). Criado pelo Atlas nesta sessão em `docs/adr/ADR-040-sprint27-implementation-decisions.md`. Sprint-atual.md atualizado com a referência.
  - **CLAUDE.md Domain Modules**: webhooks é feature cross-cutting (não módulo isolado); não foi adicionada linha nova — consistente com decisão anterior de não listar cada sub-feature como módulo separado (ex: SLA/escalação está em `common/`, não em linha própria além do módulo principal).

## [2026-05-25] Sincronização pós-Sprint 26
Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 26 — US-069, US-070, US-095)
- `CLAUDE.md` (tabela Domain Modules)
- Contexto de implementação fornecido: US-069 (PWA com service worker, install prompt, update banner), US-070 (offline NC queue com IndexedDB, sync automático, online guard em nc-detail e work-order-detail), US-095 (content-disposition sanitization, console.error removal, retention audit log)
- Commit de referência: 17151b6 (Sprint 26 — pushed, concluída)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 26 header `🔄` → `✅`; status `em andamento` → `concluída`
  - `sprint-atual.md`: US-069, US-070, US-095 `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md`: roadmap linha Sprint 26 `🔄` → `✅`
  - `CLAUDE.md`: nova linha na tabela Domain Modules — `PWA / Offline | apps/frontend/ | 26 | ✅ done`

Inconsistências verificadas:
  - Nenhuma inconsistência identificada. Sprint 26 não possui ADR adicional além do ADR-023 já referenciado — mantido.
  - US-095 liquida os itens SEC-083/084/085/086/087 diferidos do Sprint 25 (content-disposition sanitization em exportação CSV, remoção de console.error com objeto HTTP bruto em work-order-list, e audit log de motivo de retenção). Todos os ACs marcados como concluídos.

---

## [2026-05-25] Sincronização pós-Sprint 25
Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 25 — US-067, US-068, US-094)
- `CLAUDE.md` (tabela Domain Modules)
- `.claude/logs/log-mateus.md` (Sprint 25 — US-067/068/094 backend: DataRetentionExecutor, DataRetentionService refatorado, campo deactivatedAt, DataExportUseCase guard, fixes BUG-S25-01, MF-S25-01/02, SH-S25-01/02/03; 325 testes)
- `.claude/logs/log-tadeu.md` (Sprint 25 — US-067/068/094 frontend: admin-lgpd component, privacy-export component, user-list Anonimizar button, nav links LGPD + Exportar meus dados, fix BUG-S25-02/03 SEC-083 em oee-analytics; 464 testes)
- `.claude/logs/log-helena.md` (code review Sprint 25 — MF-S25-01/02/03 identificados; todos corrigidos no pós-fix; SH-S25-01/02/03 corrigidos; aprovado após fixes)
- `.claude/logs/log-maiana.md` (QA Sprint 25 — 24/32 ACs na primeira análise; blockers BUG-S25-01/02/03 todos corrigidos; gaps frontend US-068 ACs#11–18 implementados por Tadeu)
- `.claude/logs/log-beatriz.md` (security Sprint 25 — aprovado com ressalvas; SEC-082 CORRIGIDO; SEC-083 parcialmente corrigido em oee-analytics, ainda pendente em work-order-list; SEC-084/085/086/087 novos, diferidos Sprint 26)
- `.claude/logs/log-maite.md` (pipeline Sprint 25 — ready-to-deploy; 322/322 backend, 445/445 frontend; nota: log Mateus pós-fix registra 325 testes com fixes posteriores)
- `docs/adr/ADR-040-sprint25-implementation-decisions.md` — **arquivo não encontrado** (ADR-040 ainda não criado pelo Atlas)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 25 `⬜` → `✅`; status `pendente` → `concluída`
  - `sprint-atual.md`: US-067, US-068, US-094 `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md`: AC#7 de US-067 — nota de implementação adicionada sobre `DataRetentionExecutor` (bean auxiliar para isolamento transacional real, resolvendo MF-S25-02)
  - `sprint-atual.md`: roadmap linha Sprint 25 `⬜` → `✅`
  - `sprint-atual.md`: Sprint 26 — seção "Tech Debt diferido do Sprint 25" adicionada com SEC-083 (pendente em work-order-list), SEC-084 (HIGH), SEC-085, SEC-086, SEC-087
  - `CLAUDE.md`: nova linha na tabela Domain Modules — `LGPD / Privacy | common/ | 25 | ✅ done`

Inconsistências verificadas:
  - **SEC-083 — dois locais**: Beatriz identificou que `console.error` com objeto HTTP bruto persiste em `work-order-list.component.ts` linha 80, além do `oee-analytics.component.ts` já corrigido por Tadeu. O sprint-atual.md original da Sprint 25 listava apenas o local do oee-analytics. A seção de tech debt da Sprint 26 foi anotada com os dois locais (oee-analytics corrigido, work-order-list pendente).
  - **DataRetentionExecutor e AC#7 (US-067)**: o AC#7 especificava `@Transactional` independente por bloco, mas a implementação inicial usava self-invocation (proxy Spring bypassado — MF-S25-02 de Helena). Corrigido por Mateus via `DataRetentionExecutor` bean auxiliar. Nota adicionada diretamente no AC#7 do sprint-atual.md para rastreabilidade.
  - **Gaps frontend US-068 (ACs #11–18)**: Maiana identificou ausência total dos componentes frontend LGPD na primeira análise. Tadeu implementou todos os gaps no pós-fix (admin-lgpd component, privacy-export component, user-list anonimizar button, nav links). Todos os ACs marcados como `✅ concluído` no US-068.
  - **ADR-040**: arquivo referenciado nas instruções de sincronização não existe em `docs/adr/`. Sprint 25 registra ADRs como `ADR-022, ADR-039` — não foi gerado ADR específico de sprint para Sprint 25 ainda. Atlas deverá criar ADR-040 quando disponível.

---

## [2026-05-22] Sincronização pós-Sprint 24
Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 24 — US-075, US-076, US-093)
- `CLAUDE.md` (tabela Domain Modules)
- `docs/adr/ADR-039-sprint24-implementation-decisions.md`
- `.claude/logs/log-mateus.md` (Sprint 24 — US-075/093 backend: BenchmarkCalculator, 4 use cases de benchmark, 4 endpoints em AnalyticsController, fixes MF-S24-01/02 e SH-S24-01/02; 317 testes)
- `.claude/logs/log-tadeu.md` (Sprint 24 — US-076/093 frontend: OeeBenchmarkComponent integrado via tab bar nativa, analytics.service.ts com 4 métodos de benchmark, fixes MF-S24-01 e SH-S24-03; 445 testes)
- `.claude/logs/log-helena.md` (code review Sprint 24 — MF-S24-01/02 identificados; ambos corrigidos antes do commit; SH-S24-01/02/03 corrigidos; aprovado)
- `.claude/logs/log-maiana.md` (QA Sprint 24 — 30/36 ACs cobertos; BUG-S24-01/02 blockers corrigidos por Mateus e Tadeu; gaps aceitáveis documentados)
- `.claude/logs/log-beatriz.md` (security Sprint 24 — aprovado com ressalvas; SEC-078→081 RESOLVIDOS; SEC-082/083 diferidos Sprint 25)
- `.claude/logs/log-maite.md` (pipeline Sprint 24 — ready-to-deploy; 317/317 backend, 441/441 frontend)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 24 `🔄` → `✅`; status `em andamento` → `concluída`
  - `sprint-atual.md`: ADR atualizado de `ADR-026` → `ADR-026, ADR-039`
  - `sprint-atual.md`: US-075 `✅ concluído (backend)` → `✅ concluído`
  - `sprint-atual.md`: US-076 `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md`: US-093 `✅ concluído (backend+frontend parcial)` → `✅ concluído`
  - `sprint-atual.md`: AC#10 de US-075 — nota de limitação adicionada sobre fallback GERAL em `GetOeeBenchmarkByEquipmentTypeUseCase` (ImportBatch sem relação com Equipment no modelo atual)
  - `sprint-atual.md`: roadmap linha Sprint 24 `⬜` → `✅`; ADR-039 adicionado na linha do roadmap
  - `sprint-atual.md`: Sprint 25 — seção "Tech Debt diferido do Sprint 24" adicionada com SEC-082 (MEDIUM) e SEC-083 (LOW) diferidos por Beatriz
  - `CLAUDE.md`: linha `Cross-module KPI + Reports + Analytics` — sprint `24` adicionado ao range (`9–10, 16` → `9–10, 16, 24`); benchmarking OEE é extensão analítica do módulo existente, não funcionalidade distinta

Inconsistências verificadas:
  - **SH-S24-03 (`improvementPct`)**: log Tadeu confirma fix — `div.improvement-card` adicionado com `data-testid="improvement-card"` e pipe `number:'1.1-1'`; backend `PeriodComparisonResponse` recebeu campo `improvementPct` (log Mateus fix MF-S24-02). AC#12 e AC#13 do sprint-atual.md refletem comportamento correto; nenhuma correção adicional necessária.
  - **AC#10 (equipment-type fallback GERAL)**: sprint-atual.md descrevia agrupamento por `equipment.type.name()` via join com Equipment, mas `ImportBatch` não tem relação com Equipment no modelo atual. Implementado com fallback `label="GERAL"`. Adicionada nota de limitação diretamente no AC#10 para rastreabilidade — não altera o comportamento esperado dos outros ACs.
  - **SEC-082/083 diferidos**: Sprint 25 não tinha anotação desses itens — seção "Tech Debt diferido do Sprint 24" adicionada.

---

## [2026-05-22] Sincronização pós-Sprint 23
Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 23 — US-063, US-064, US-092)
- `CLAUDE.md` (tabela Domain Modules)
- `docs/adr/ADR-038-sprint23-implementation-decisions.md`
- `.claude/logs/log-mateus.md` (Sprint 23 — US-063/064/092 backend: Plant, UserPlant, PlantContext, PlantContextFilter, EmailEscalationService, SEC-075/076/077 corrigidos; 312 testes; fixes MF-S23-01/02, BUG-S23-01, AC#11 via AdminUserController)
- `.claude/logs/log-tadeu.md` (Sprint 23 — US-063/064/092 frontend: PlantService, PlantListComponent, PlantContextService, PlantHeaderInterceptor, PlantSelectorComponent, nav integrado, filtros NC/OS/SpareParts; fixes MF-S23-03, BUG-S23-01, AC#18; 430 testes)
- `.claude/logs/log-helena.md` (code review Sprint 23 — MF-S23-01/02/03 identificados; todos corrigidos antes do commit; SH-S23-01/02/03/04 registrados; aprovado)
- `.claude/logs/log-maiana.md` (QA Sprint 23 — 49/52 ACs cobertos; BUG-S23-01 BLOCKER corrigido; gaps aceitáveis documentados; AC#11 implementado via AdminUserController)
- `.claude/logs/log-beatriz.md` (security Sprint 23 — aprovado; SEC-075/076/077 CORRIGIDOS; SEC-078→081 diferidos Sprint 24)
- `.claude/logs/log-maite.md` (pipeline Sprint 23 — ready-to-deploy; 307/307 backend, 420/420 frontend)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 23 `⬜` → `✅`; status `pendente` → `concluída`
  - `sprint-atual.md`: ADR atualizado de `ADR-020` → `ADR-020, ADR-038`
  - `sprint-atual.md`: US-063, US-064, US-092 `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md`: roadmap linha Sprint 23 `⬜` → `✅`; ADR-038 adicionado na linha do roadmap
  - `sprint-atual.md`: Sprint 24 — seção "Tech Debt diferido do Sprint 23" adicionada com SEC-078, SEC-079, SEC-080, SEC-081 (diferidos por Beatriz)
  - `CLAUDE.md`: nova linha na tabela Domain Modules — `Multi-plant Support | common/ | 23 | ✅ done`

Inconsistências verificadas:
  - **Gap `escalateByEmail`**: resolvido no Sprint 23. `EmailEscalationService` criado em `common/application/` com `@Autowired(required=false)` para `JavaMailSender` e feature flag `mail.enabled`. `EscalationUseCase` agora injeta o serviço e chama `notifySlaBreached()` quando `rule.isEscalateByEmail() = true`. Gap registrado na sincronização do Sprint 22 como pendente — encerrado.
  - **ADR-038 Decisão 5 (PlantContext) vs implementação real**: ADR descreve `CURRENT_PLANT_IDS = null` para ADMIN. A implementação final (log Mateus pós-fixes) usa record interno `Context(admin, plantIds)` com `setAdmin()` explícito, para distinguir "ADMIN context" de "ThreadLocal não setado". Divergência intencional e documentada no log — ADR descreve o padrão base; implementação melhora a semântica sem alterar o comportamento externo. sprint-atual.md mantido com os ACs originais (comportamento correto).
  - **US-092 AC#1 (SEC-075 cooldown)**: ADR e spec descrevem cooldown em `EscalationUseCase`; implementação colocou o `AtomicReference` em `SlaRuleController.runNow()` (log Mateus, Beatriz confirmou como correto). Maiana registrou como aceitável. Sem correção no sprint-atual.md — desvio de localização sem impacto funcional.
  - **SEC-078→081**: diferidos pelo log de Beatriz para Sprint 24. Verificado que Sprint 24 não tinha anotação desses itens — adicionada seção "Tech Debt diferido do Sprint 23" na Sprint 24.
  - **US-063 AC#2 (migration SQL formal)**: implementado via `SchemaInitializer` + `ddl-auto=update` + `DataInitializer` (padrão do projeto desde Sprint 1, sem Flyway). Maiana documentou como divergência aceitável pelo padrão estabelecido. Sem correção no sprint-atual.md.

---

## [2026-05-21] Sincronização pós-Sprint 22

Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 22 — US-061, US-062, US-091)
- `CLAUDE.md` (tabela Domain Modules)
- `docs/adr/ADR-037-sprint22-implementation-decisions.md`
- `.claude/logs/log-mateus.md` (Sprint 22 — US-061/062/091 backend: SlaRule, EscalationUseCase, EscalationJob, SlaRuleController, tech debt SEC-065/067/068/070/071/072/073; fixes MF-S22-01/02, SH-S22-01/02/03; 298 testes)
- `.claude/logs/log-tadeu.md` (Sprint 22 — US-061/062/091 frontend: SlaService, SlaRulesComponent, SlaBreachedChipComponent, KPI dashboard SLA, filtros slaBreached em NC e OS; 381 testes)
- `.claude/logs/log-helena.md` (code review Sprint 22 — MF-S22-01/02 identificados; corrigidos por Mateus antes do commit; SH-S22-01/02/03 corrigidos; aprovado)
- `.claude/logs/log-maiana.md` (QA Sprint 22 — 36/36 ACs cobertos; 3 gaps documentados: classifierValue max 30 vs spec 50, escalateByEmail sem email, run-now sem rate limit; aceitáveis)
- `.claude/logs/log-beatriz.md` (security Sprint 22 — aprovado; SEC-075/076/077 diferidos Sprint 23; SEC-066 corrigido nesta sprint)
- `.claude/logs/log-maite.md` (pipeline Sprint 22 — ready-to-deploy; 298/298 backend, 381/381 frontend)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 22 `⬜` → `✅`; status `planejada` → `concluída`
  - `sprint-atual.md`: ADR atualizado de `ADR-019` → `ADR-019, ADR-037`
  - `sprint-atual.md`: US-061, US-062, US-091 `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md`: roadmap linha Sprint 22 `⬜` → `✅`; ADR-037 adicionado na linha do roadmap
  - `CLAUDE.md`: nova linha na tabela Domain Modules — `SLA Rules + Escalation | common/ | 22 | ✅ done`

Inconsistências verificadas:
  - **ADR-037 Decisão 3 vs implementação real**: o ADR descreve envio de email assíncrono via `emailEscalator.sendAsync()` quando `escalateByEmail = true`. A implementação real (log Mateus, log Maiana) não inclui `JavaMailSender` no `EscalationUseCase` — apenas `notificationService.broadcast()` é chamado. Gap documentado por Maiana (US-062 AC#4(d/e)) e Beatriz como SHOULD FIX diferido para Sprint 23. ADR prevalece: sprint-atual.md mantido com os ACs originais (comportamento esperado); gap registrado nos logs dos agentes.
  - **ADR-037 Decisão 11 vs US-091 AC#11**: ADR define `FileTooLargeException` retornando 400 (BAD_REQUEST); implementação usa 413 (PAYLOAD_TOO_LARGE). Log Mateus documenta a decisão deliberada (semanticamente correto); Maiana confirmou como aceitável. Sem correção — desvio intencional documentado.
  - **SEC-075, SEC-076, SEC-077**: identificados por Beatriz, diferidos para Sprint 23 (run-now sem rate limiting, classifierValue sem validação semântica, run-now manual não auditado). Itens presentes no log de Beatriz — não requerem anotação no sprint-atual.md agora (Athos os incluirá ao planejar Sprint 23).
  - **Gap `escalateByEmail`**: email não enviado em Sprint 22 — integração com `QmsEmailService` diferida para Sprint 23. Registrado no log de Maiana.

---

## [2026-05-21] Sincronização pós-Sprint 21

Fontes lidas:
- `.claude/sprint-atual.md` (Sprint 21 — US-059, US-060)
- `CLAUDE.md` (tabela Domain Modules)
- `docs/adr/ADR-036-sprint21-implementation-decisions.md`
- `.claude/logs/log-mateus.md` (Sprint 21 — US-059 backend: Attachment entity, S3StorageService, use cases, fixes MF-01/SH-51/52/53, BUG-S21-01/02, SEC-070/073; 282 testes)
- `.claude/logs/log-tadeu.md` (Sprint 21 — US-060 frontend: AttachmentService, AttachmentListComponent, integração NC e OS, fix SH-54, BUG-S21-01/02, SEC-074; 357 testes)
- `.claude/logs/log-helena.md` (code review Sprint 21 — aprovado; MF-01/SH-51/52/53/54 todos corrigidos por Mateus e Tadeu)
- `.claude/logs/log-maiana.md` (QA Sprint 21 — aprovado; 26/26 ACs cobertos; BUG-S21-01/02 corrigidos; gaps aceitáveis documentados)
- `.claude/logs/log-beatriz.md` (security Sprint 21 — aprovado; SEC-070/074 corrigidos; SEC-071/072/073 diferidos Sprint 22)
- `.claude/logs/log-maite.md` (pipeline Sprint 21 — ready-to-deploy; 279/279 backend, 355/355 frontend)

Mudanças feitas:
  - `sprint-atual.md`: Sprint 21 `⬜` → `✅`; status `pendente` → `concluída`
  - `sprint-atual.md`: US-059 e US-060 `⬜ pendente` → `✅ concluído`
  - `sprint-atual.md`: roadmap linha Sprint 21 `⬜` → `✅`
  - `sprint-atual.md`: US-060 AC#8 corrigido — `MatDialog` substituído por `window.confirm()` nativo (implementação real; consistente com outros componentes do projeto)
  - `sprint-atual.md`: US-060 AC#10(e) corrigido — mesma correção de `MatDialog` → `window.confirm()`
  - `CLAUDE.md`: nova linha na tabela Domain Modules — `File Attachments | common/ | 21 | ✅ done`

Inconsistências verificadas:
  - US-060 AC#8 e AC#10(e): spec original previa `MatDialog` para confirmação de deleção. Implementação (Tadeu) usou `window.confirm()` nativo — consistente com `nc-detail` e `equipment-detail` do projeto (Angular Material não disponível). Maiana confirmou como aceitável por padronização. sprint-atual.md corrigido para refletir a implementação real.
  - US-060 AC#6: spec previa `MatProgressBar` e snackbar de sucesso/erro de upload. Implementação usa spinner CSS nativo e silencia erros (SHOULD FIX diferido Sprint 22). Desvio menor documentado no log de Maiana; sprint-atual.md mantido conforme está (a correção AC#8 foi a única necessária para rastreabilidade).
  - `image/webp` (BUG-S21-01) e `expiresAt` em `DownloadUrlResponse` (BUG-S21-02): ambos previstos nos ACs do sprint-atual.md; implementados inicialmente sem eles mas corrigidos por Mateus/Tadeu antes do commit final. Sprint-atual.md está alinhado.

---

## [2026-05-21] Sincronização pós-Sprint 20

Fontes lidas:
- `CLAUDE.md`
- `docs/adr/ADR-014-spare-parts-inventory.md`
- `docs/adr/ADR-035-sprint20-technical-debt.md`
- `.claude/sprint-atual.md` (Sprint 20 — US-049, US-050, US-051, US-090)
- `.claude/logs/log-mateus.md` (backend Sprint 20: SparePart, WorkOrderPart, use cases, testes — 272/272; correções BUG-01 + GAP-01)
- `.claude/logs/log-tadeu.md` (frontend Sprint 20: spare-part-list, work-order-detail, maintenance-dashboard, nav badge — 342/342)
- `.claude/logs/log-maiana.md` (QA Sprint 20 — BUG-01 + GAP-01 bloqueadores corrigidos; gaps aceitáveis documentados)
- `.claude/logs/log-maite.md` (pipeline Sprint 20 — ready-to-deploy; 272/272 backend, 342/342 frontend)
- `.claude/logs/log-helena.md` (code review Sprint 20 — aprovado; SH-48/49/50/51 SHOULD FIX; SUG-31/32)
- `.claude/logs/log-beatriz.md` (security Sprint 20 — aprovado; SEC-063/064 corrigidos; SEC-067/068/069 diferidos Sprint 21)

Mudanças feitas:
- `.claude/sprint-atual.md` — Sprint 20: `⬜` → `✅`; status: `pendente` → `concluída`; ADR atualizado para `ADR-014, ADR-035`; US-049/050/051/090: `⬜ pendente` → `✅ concluído`
- `.claude/sprint-atual.md` — Roadmap: `⬜ Sprint 20 | ... | US-049, US-050, US-051 | ADR-014` → `✅ Sprint 20 | ... | US-049, US-050, US-051, US-090 | ADR-014, ADR-035` (US-090 e ADR-035 estavam ausentes da linha do roadmap)
- `CLAUDE.md` — Domain Modules: Maintenance (TPM) sprint range `7–8, 15` → `7–8, 15, 20` (Sprint 20 estende o módulo com spare parts inventory)

Inconsistências verificadas:
- `sprint-atual.md` US-051 AC#1 menciona que o disparo de notificação deve ocorrer **fora** da `@Transactional` via `@TransactionalEventListener`. A implementação de Mateus optou por chamar `notificationService.broadcast()` dentro da mesma transação (padrão já estabelecido em `AlertEvaluatorUseCase`). O ADR-014 Decisão 5 documenta a abordagem por título sem mencionar o `@TransactionalEventListener`. Implementação e ADR estão alinhados; o AC#1 do sprint-atual.md descreve uma alternativa não adotada — sem correção no sprint-atual.md pois a consistência com o ADR prevalece (comportamento funcional é o mesmo: notificação é enviada se e somente se o consumo for comitado, pela semântica transacional do Spring).
- Roadmap linha Sprint 20 não incluía US-090 nem ADR-035 — corrigido nesta sincronização.

---

## [2026-05-20] Sincronização pós-Sprint 16

Fontes lidas:
- `CLAUDE.md`
- `docs/adr/ADR-031-sprint16-technical-debt.md`
- `.claude/sprint-atual.md` (Sprint 16 — US-043, US-044, US-045, US-059)
- `.claude/logs/log-mateus.md` (US-043/044/045/059 backend + MF-5/MF-6/BUG-1 corrigidos; 208 testes passando)
- `.claude/logs/log-maiana.md` (QA aprovado com ressalvas — 48/48 ACs cobertos; BUG-1/2/3 identificados)
- `.claude/logs/log-helena.md` (Code review — MF-5, MF-6, BUG-1 elevados a MUST FIX; SH-42/43/44 corrigidos)
- `.claude/logs/log-athos.md` (Sprint 16 abertura — US-043/044/045/059, 12 pts, ADR-012)
- `.claude/logs/log-maite.md` (pipeline Sprint 15 — referência para estado anterior)

Mudanças feitas:
- `.claude/sprint-atual.md` — Sprint 16: `🔄 em andamento` → `✅ concluída`; US-043, US-044, US-045, US-059 `⬜ pendente` → `✅ concluído`; ADRs da sprint atualizados para incluir ADR-031
- `.claude/sprint-atual.md` — Roadmap: `⬜ Sprint 16` → `✅ Sprint 16` com US-059 e ADR-031 incluídos
- `CLAUDE.md` — tabela Domain Modules: `Cross-module KPI + Reports` sprint range atualizado de `9–10` → `9–10, 16` (Sprint 16 estende o módulo com analytics avançado por módulo); nome atualizado para `Cross-module KPI + Reports + Analytics`; package atualizado para incluir `common/presentation/` (AnalyticsController)
- `CLAUDE.md` — seção Key Conventions: adicionada convenção `@Validated` em controllers com `@RequestParam` validados, conforme ADR-031 Decisão 2

Inconsistências verificadas:
- ADR-031 Decisão 1 define `USER_ROLE_UPDATED` mas a implementação (log-mateus) adicionou `ROLE_CHANGED` ao `AuditAction`. O sprint-atual.md AC#1 também usa `ROLE_CHANGED` — alinhado com a implementação efetiva. O ADR-031 usa nomenclatura diferente (`USER_ROLE_UPDATED`) mas o comportamento descrito é idêntico. Sem correção necessária no sprint-atual.md (implementação e AC já consistentes entre si); inconsistência de nomenclatura entre ADR e código é menor e não afeta comportamento.
- MF-5 (findAll → GROUP BY), MF-6 (NcTrendResponse contrato), BUG-1 (COMPLETED→DONE) foram todos corrigidos por Mateus (log-mateus Sprint 16 blockers) e confirmados como resolvidos antes do commit final.

---

## [2026-05-20] Sincronização pós-Sprint 15

Fontes lidas:
- `CLAUDE.md`
- `docs/adr/ADR-011-preventive-maintenance-scheduling.md`
- `.claude/sprint-atual.md` (Sprint 15 — US-040, US-041, US-042)
- `.claude/logs/log-mateus.md` (US-040 + US-041 backend concluídos; MF-3 + MF-4 corrigidos)
- `.claude/logs/log-tadeu.md` (US-042 frontend concluído — 40 testes adicionados)
- `.claude/logs/log-maiana.md` (QA aprovado — 27/27 ACs cobertos; G16/G17 diferidos para Sprint 16)
- `.claude/logs/log-beatriz.md` (Security aprovado — 0 CRITICAL/HIGH/MEDIUM; SEC-051/052 diferidos Sprint 16)
- `.claude/logs/log-helena.md` (Code review — MF-3 + MF-4 corrigidos por Mateus; SHOULD FIX SH-38–41 registrados)
- `.claude/logs/log-maite.md` (pipeline: 184 backend + 187 frontend ✅; ready-to-deploy)

Mudanças feitas:
- `CLAUDE.md` — tabela Domain Modules: Maintenance (TPM) sprint range atualizado de `7–8` → `7–8, 15` (Sprint 15 estende o módulo com planos preventivos recorrentes e calendário)

Sem mudanças em `sprint-atual.md` — Sprint 15 já estava marcada como `✅ concluída` com US-040, US-041 e US-042 `✅ concluído`. Consistência verificada com ADR-011 (sem divergências).

Itens diferidos para Sprint 16 (não requerem doc agora):
- G16: `UpdateScheduleUseCase` e `DeactivateScheduleUseCase` sem testes unitários
- G17: `ScheduleFormComponent` spec sem cobertura de submit bem-sucedido
- SEC-051: `UpdateScheduleUseCase` sem auditoria (`SCHEDULE_UPDATED`)
- SEC-052: `IllegalArgumentException` sem handler dedicado no `GlobalExceptionHandler` (MF-3 corrigiu introduzindo `InvalidScheduleRecurrenceException`; SEC-052 era sobre o estado original pré-fix — resolvido)
- SEC-048/049/050/045/046: diferidos de sprints anteriores (escopo QMS/User Management), mantidos para Sprint 16

---

## [2026-05-20] Sincronização pós-Sprint 14

Fontes lidas:
- `CLAUDE.md`
- `docs/adr/ADR-017-supplier-management.md`
- `.claude/sprint-atual.md` (Sprint 14 ✅ já atualizado por Tadeu)
- `.claude/logs/log-maite.md` (pipeline Sprint 14 — ready-to-deploy, 159 backend + 147 frontend)
- `.claude/logs/log-helena.md` (code review Sprint 14 — aprovado, 0 MUST FIX)

Mudanças feitas:
- `CLAUDE.md` — tabela Domain Modules:
  - QMS: sprint range atualizado de `5–6` → `5–6, 13–14` (Sprints 13 e 14 estendem o módulo QMS com RCA e Supplier Management)
  - Nova linha: `User Management | common/auth/ | 12 | ✅ done` (Sprint 12 estava concluída mas ausente da tabela)

Sem mudanças em `sprint-atual.md` — já sincronizado na sessão anterior (Sprint 14 ✅, roadmap correto).

Itens diferidos para sprints futuras (registrados por Beatriz/Helena, não requerem doc agora):
- SEC-048/049/050, SEC-045/046, SH-33 a SH-37 → Sprint 15

---

## [2026-05-19] Status geral do projeto — leitura de estado

Fontes lidas:
- `.claude/sprint-atual.md` (completo, Sprints 4–32)
- `.claude/logs/log-maite.md` (pipeline Sprint 13 — ready-to-deploy)
- `.claude/logs/log-athos.md` (planejamento Sprints 11–32)
- `git log --oneline -10`

Mudanças feitas:
- Nenhuma (docs já sincronizados pela sessão anterior de 2026-05-15)

Observações:
- Sprint 13 está ✅ concluída e pronta para deploy (Maitê: 149 backend + 109 frontend passando)
- Próxima sprint a ser executada: **Sprint 14** (Suppliers + score de qualidade — ADR-017)
- Sprints 4–13 todas ✅ concluídas; Sprints 14–32 ⬜ pendentes
- Nenhuma inconsistência identificada entre ADRs e sprint-atual.md

---

## [2026-05-15] Sincronização Sprint 13 — US-052, US-053

Fontes lidas:
- `.claude/sprint-atual.md`
- `.claude/logs/log-mateus.md` (US-052 backend concluído, fix answer1)
- `.claude/logs/log-tadeu.md` (US-053 frontend concluído + fix MF-2/SH-29/30/31/32)
- `.claude/logs/log-maiana.md` (QA aprovado após fix)
- `.claude/logs/log-beatriz.md` (Security aprovado)
- `.claude/logs/log-helena.md` (Code review — MF-2 + 4 SHOULD FIX corrigidos)
- `.claude/logs/log-maite.md` (pipeline: 149 backend + 109 frontend, 0 falhas)
- `docs/adr/ADR-015-root-cause-analysis.md`

Mudanças feitas:
- `sprint-atual.md` — Sprint 13 `⬜ pendente` → `✅ concluída`
- `sprint-atual.md` — US-052 e US-053 `⬜ pendente` → `✅ concluído`
- `sprint-atual.md` — AC-3 de US-053 atualizado: botão "+ Adicionar próximo Por quê" agora exige tanto "Por quê" quanto "Resposta" do par atual preenchidos (implementação Tadeu SH-30)
- `sprint-atual.md` — Roadmap: Sprints 7–13 corrigidos de `🔄`/`⬜` para `✅` (estavam desatualizados desde Sprint 7)

Sem inconsistências entre ADR-015 e sprint-atual.md após as correções (Decisão 2 e 4 do ADR foram atualizadas por Tadeu para refletir answer1 obrigatório).

---

## [2026-05-11] Atualização sprint-atual.md — Sprints 3–10

**Tarefa**: Sincronizar docs/sprint-atual.md com planejamento do Athos e ADRs do Atlas
**Alterações**:
- Corrigido objetivo Sprint 3: read-only (não CRUD) de workers
- ADR refs preenchidas para Sprints 3–10 (ADR-005 → ADR-009)
- Removida `retro_antecipada` de Sprint 4
- Sprint 4 consolidada: US-013 (auth backend) + US-014 (auth frontend)
**Arquivos modificados**: docs/sprint-atual.md

---

## [2026-05-13] Atualização docs — Sprint 6 + Sprint 7

**Tarefa**: Sincronizar sprint-atual.md e CLAUDE.md com Sprint 6 (QMS CAP + Email) e Sprint 7 (Maintenance) concluídas.

**Alterações**:
- `sprint-atual.md` Sprint 6: status marcado como ✅ concluída (já estava no arquivo)
- `sprint-atual.md` Sprint 7: status atualizado de `🔄 backend concluído; frontend pendente` → `✅ concluída`; US-027 e US-028 de `🔄` → `✅ concluído`
- `CLAUDE.md` tabela Domain Modules: Maintenance atualizado de `planned` → `🔄 Sprint 7 ✅ / Sprint 8 planned`

**ADRs**: ADR-008 já reflete Sprint 7 completa (Decisão 6 e Decisão 7 adicionadas pelo Atlas durante a sprint).

**Arquivos modificados**: `.claude/sprint-atual.md`, `CLAUDE.md`

---

## [2026-05-15] Atualização docs — Sprint 11 concluída

**Tarefa**: Sincronizar `sprint-atual.md` e `CLAUDE.md` com Sprint 11 (Security Hardening) concluída.

**Alterações**:
- `sprint-atual.md` Sprint 11: status `⬜ pendente` → `✅ concluída`; US-065 e US-066 de `⬜ pendente` → `✅ concluído`
- `CLAUDE.md` tabela Domain Modules: Authentication, QMS, Maintenance e KPI atualizados de status desatualizados → `✅ done`; nova linha `Security Hardening | common/security/ | 11 | ✅ done` adicionada

**Arquivos modificados**: `.claude/sprint-atual.md`, `CLAUDE.md`

## [2026-05-21] Sincronização Sprint 18
Fontes lidas: sprint-atual.md, CLAUDE.md, log-mateus.md, log-tadeu.md, log-helena.md, log-maiana.md, log-beatriz.md, log-maite.md
Mudanças feitas:
  - sprint-atual.md: Sprint 18 marcada ✅ (header + tabela de USs + roadmap)
  - sprint-atual.md: US-088, US-046, US-047, US-048 marcadas como ✅ concluído
  - CLAUDE.md: novo módulo "Alert Thresholds + Notifications" adicionado na tabela Domain Modules (Sprint 18, ✅ done)

## [2026-05-21] Sincronização Sprint 19
Fontes lidas: sprint-atual.md, CLAUDE.md, log-mateus.md, log-tadeu.md, log-helena.md, log-maiana.md, log-beatriz.md, log-maite.md, log-atlas.md, log-athos.md
Mudanças feitas:
  - sprint-atual.md: Sprint 19 marcada ✅ (header: status + ADR-034 adicionado + tabela de USs + roadmap)
  - sprint-atual.md: US-054, US-055, US-056, US-089 marcadas como ✅ concluído
  - sprint-atual.md: roadmap atualizado — US-089 e ADR-034 incluídos na linha da Sprint 19
  - CLAUDE.md: novo módulo "Shift Management" adicionado na tabela Domain Modules (Sprint 19, ✅ done)
