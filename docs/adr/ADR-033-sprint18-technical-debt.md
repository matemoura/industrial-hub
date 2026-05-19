## ADR-033: Technical Debt Sprint 18 — Auditoria de Planned Downtime, Validação de Description, SecurityConfig URL-level e Refatorações de Calendário
**Status**: Aprovado
**Data**: 2026-05-20
**US relacionadas**: US-075 (Sprint 18 — tech debt obrigatório, a ser criada por Athos)

### Contexto

Ao final das Sprints 15, 16 e 17, os revisores Helena (code review) e Beatriz (security review) identificaram itens diferidos que não puderam ser endereçados nas respectivas sprints por conflito de escopo ou baixa prioridade relativa. A Sprint 18 é a janela natural para liquidar esses itens acumulados — em especial os três achados de segurança MEDIUM da Sprint 17 (SEC-057, SEC-058, SEC-059) que envolvem auditoria incompleta de operações de escrita em `PlannedDowntime`, um módulo que afeta diretamente os cálculos de OEE reportados.

Os itens diferidos de Sprints 15–16 (SH-39, SH-40, SEC-030) são de menor impacto imediato, mas acumulam dívida técnica de leitura e UX que cresce proporcionalmente ao uso do sistema.

Esta ADR consolida as decisões arquiteturais para todos os itens diferidos, define a abordagem de cada correção e especifica a ordem de implementação para minimizar conflitos de merge.

---

### Decisão 1 — Auditoria completa para operações CRUD de PlannedDowntime (SEC-057, SEC-058)

**Problema**: `CreatePlannedDowntimeUseCase` e `DeletePlannedDowntimeUseCase` não chamam `auditService.log()`, apesar de `DOWNTIME_CREATED` e `DOWNTIME_DELETED` já existirem no enum `AuditAction`. `UpdatePlannedDowntimeUseCase` também não audita, e o valor `DOWNTIME_UPDATED` sequer existe no enum. Um SUPERVISOR pode criar, alterar e excluir paradas planejadas — que afetam diretamente os cálculos de disponibilidade e OEE — sem deixar rastro forense no `AuditLog`. Reportado como SEC-057 (Beatriz) e SEC-058 (Beatriz).

**Decisão**: Adicionar `DOWNTIME_UPDATED` ao enum `AuditAction`, injetar `AuditService` nos três use cases e registrar auditoria em cada operação de escrita.

```java
// common/domain/AuditAction.java — adicionar:
DOWNTIME_UPDATED
// DOWNTIME_CREATED e DOWNTIME_DELETED já existem — não recriar
```

**Instrumentação dos use cases**:

```java
// CreatePlannedDowntimeUseCase.java
// Campo injetado via construtor:
private final AuditService auditService;

// No execute(), após downtime = plannedDowntimeRepository.save(downtime):
auditService.log(AuditAction.DOWNTIME_CREATED, registeredBy, downtime.getId().toString(),
    Map.of(
        "date", downtime.getDate().toString(),
        "durationMinutes", String.valueOf(downtime.getDurationMinutes()),
        "reason", downtime.getReason().name(),
        "equipmentId", downtime.getEquipment() != null
            ? downtime.getEquipment().getId().toString()
            : "plant-wide"
    ));
```

```java
// DeletePlannedDowntimeUseCase.java
// Campo injetado via construtor:
private final AuditService auditService;

// No execute(), após confirmação de existência e antes de deleteById:
// Buscar dados antes de deletar para incluir no log
PlannedDowntime toDelete = plannedDowntimeRepository.findById(id)
    .orElseThrow(() -> new PlannedDowntimeNotFoundException(id));
plannedDowntimeRepository.deleteById(id);
auditService.log(AuditAction.DOWNTIME_DELETED, username, id.toString(),
    Map.of(
        "date", toDelete.getDate().toString(),
        "durationMinutes", String.valueOf(toDelete.getDurationMinutes()),
        "reason", toDelete.getReason().name()
    ));
```

**Nota sobre DeletePlannedDowntimeUseCase**: o use case atual usa o padrão `existsById + deleteById` (2 roundtrips). A auditoria exige conhecer os dados antes da deleção. Substituir por `findById + deleteById` (busca a entidade primeiro, registra o audit, depois deleta) — mantém 2 roundtrips mas com semântica mais rica. O método `existsById` é removido.

```java
// UpdatePlannedDowntimeUseCase.java
// Campo injetado via construtor:
private final AuditService auditService;

// O execute() atualmente não recebe username — a assinatura deve ser atualizada:
// execute(UUID id, UpdatePlannedDowntimeRequest request, String username)
// PlannedDowntimeController deve passar principal.getName()

// No execute(), após plannedDowntimeRepository.save(downtime):
auditService.log(AuditAction.DOWNTIME_UPDATED, username, downtime.getId().toString(),
    Map.of(
        "date", downtime.getDate().toString(),
        "durationMinutes", String.valueOf(downtime.getDurationMinutes()),
        "reason", downtime.getReason().name(),
        "equipmentId", downtime.getEquipment() != null
            ? downtime.getEquipment().getId().toString()
            : "plant-wide"
    ));
```

**Atualização do controller**:

```java
// PlannedDowntimeController.java
// Endpoint PUT — passar username ao use case:
@PutMapping("/{id}")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public PlannedDowntimeResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePlannedDowntimeRequest request,
        Principal principal) {
    return updatePlannedDowntimeUseCase.execute(id, request, principal.getName());
}

// Endpoint DELETE — o use case já recebia username? Verificar; se não, adicionar:
@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public void delete(@PathVariable UUID id, Principal principal) {
    deletePlannedDowntimeUseCase.execute(id, principal.getName());
}
```

---

### Decisão 2 — @Size(max=500) em description dos DTOs de PlannedDowntime (SEC-059)

**Problema**: `CreatePlannedDowntimeRequest` e `UpdatePlannedDowntimeRequest` têm campo `description` sem `@Size(max=500)`, embora a coluna do banco seja `@Column(length=500)`. Uma string maior que 500 caracteres causa `DataIntegrityViolationException` no flush do JPA, que não tem handler dedicado no `GlobalExceptionHandler` e cai no `handleGeneric` — retornando HTTP 500 com "Erro interno" ao invés de HTTP 400 com mensagem descritiva para o cliente. Reportado como SEC-059 (Beatriz).

**Decisão**: Adicionar `@Size(max=500)` em ambos os DTOs.

```java
// CreatePlannedDowntimeRequest.java
public record CreatePlannedDowntimeRequest(
    @NotNull LocalDate date,
    @NotNull @Min(1) @Max(1440) Integer durationMinutes,
    @NotNull DowntimeReason reason,
    UUID equipmentId,
    @Size(max = 500, message = "description deve ter no máximo 500 caracteres") String description
) {}

// UpdatePlannedDowntimeRequest.java
public record UpdatePlannedDowntimeRequest(
    @NotNull LocalDate date,
    @NotNull @Min(1) @Max(1440) Integer durationMinutes,
    @NotNull DowntimeReason reason,
    UUID equipmentId,
    @Size(max = 500, message = "description deve ter no máximo 500 caracteres") String description
) {}
```

**Nota**: `@Size` de Bean Validation em records Java requer que o campo seja anotado diretamente. O `@Valid` já está presente nos endpoints POST e PUT do `PlannedDowntimeController` — as anotações serão avaliadas automaticamente. Violação de `@Size` em `@RequestBody` lança `MethodArgumentNotValidException`, que já tem handler no `GlobalExceptionHandler` retornando 400.

---

### Decisão 3 — Renomear findById com @EntityGraph para nome explícito em MaintenanceScheduleRepository (SH-39)

**Problema**: `MaintenanceScheduleRepository.findById(UUID id)` está anotado com `@EntityGraph(attributePaths = {"equipment"})`, sobrescrevendo o método padrão do `JpaRepository`. Toda chamada a `scheduleRepository.findById(id)` — inclusive em contextos onde `equipment` não é necessário (`ScheduleProcessorService`, lógica de existência) — executa JOIN FETCH de `equipment` silenciosamente. O comportamento é funcionalmente correto mas semanticamente enganoso: o nome `findById` não indica o eager load implícito. Reportado como SH-39 (Helena).

**Decisão**: Renomear para `findWithEquipmentById` e atualizar todos os callers. O `findById` padrão do `JpaRepository` fica disponível sem EntityGraph para contextos que não precisam do equipment.

```java
// MaintenanceScheduleRepository.java — renomear:
@EntityGraph(attributePaths = {"equipment"})
Optional<MaintenanceSchedule> findWithEquipmentById(UUID id);
// Remover a declaração com @EntityGraph de findById(UUID id)
```

**Callers a atualizar**:

```
UpdateScheduleUseCase.execute()      → scheduleRepository.findWithEquipmentById(id)
DeactivateScheduleUseCase.execute()  → scheduleRepository.findWithEquipmentById(id)
                                       (acessa schedule.getEquipment() no audit log)
GetScheduleDetailUseCase (se existir)→ scheduleRepository.findWithEquipmentById(id)
ScheduleProcessorService.processOne()→ recebe MaintenanceSchedule já carregado — sem findById direto
```

**Impacto em testes**: mocks que usam `when(scheduleRepository.findById(id))` devem ser migrados para `when(scheduleRepository.findWithEquipmentById(id))` nos testes dos use cases de detalhe/update/deactivate.

---

### Decisão 4 — Teto visual de planos por célula no MaintenanceCalendarComponent (SH-40)

**Problema**: A lógica `scheduleFallsOnDate` para recorrência `DAILY` retorna `true` para todos os dias a partir de `nextRunAt` (`return date >= nextRun`). Com múltiplos planos DAILY ativos, cada célula do grid de 6×7 dias exibe todos os planos repetidos. Uma instalação com 5 planos DAILY ativos renderiza 5 badges em cada uma das 42 células — sobrecarregando visualmente o calendário e tornando difícil identificar planos com menor frequência (WEEKLY, MONTHLY). Reportado como SH-40 (Helena).

**Decisão**: Manter a lógica de `scheduleFallsOnDate` inalterada (comportamento DAILY é semanticamente correto — plano diário realmente ocorre todos os dias). Aplicar teto visual de 3 badges por célula no template, com indicador "+N mais" quando excedido.

```typescript
// maintenance-calendar.component.ts
// Adicionar computed para obter planos visíveis e overflow por dia:
readonly calendarDays = computed(() => {
    return this.rawCalendarDays().map(day => ({
        ...day,
        visibleSchedules: day.schedules.slice(0, 3),
        overflowCount: Math.max(0, day.schedules.length - 3),
    }));
});
```

```html
<!-- maintenance-calendar.component.html -->
<!-- Substituir @for (s of day.schedules) por: -->
@for (s of day.visibleSchedules; track s.id) {
    <span class="schedule-badge" [class]="'badge-' + s.recurrence.toLowerCase()">
        {{ s.title }}
    </span>
}
@if (day.overflowCount > 0) {
    <span class="schedule-overflow">+{{ day.overflowCount }} mais</span>
}
```

```scss
/* maintenance-calendar.component.scss */
.schedule-overflow {
    font-size: 0.65rem;
    color: #6B7280;
    cursor: default;
    padding: 0 2px;
}
```

**Impacto em testes**: `maintenance-calendar.component.spec.ts` deve adicionar cenário verificando que célula com >3 planos exibe exatamente 3 badges + indicador "+N mais".

---

### Decisão 5 — Adicionar URL-level rules para /analytics, /maintenance e /kpi no SecurityConfig (SEC-030)

**Problema**: `SecurityConfig` tem regras URL-level explícitas apenas para `/api/v1/oee/**`, `/api/v1/qms/**`, `/api/v1/admin/users/**` e actuators. Os endpoints `/api/v1/analytics/**`, `/api/v1/maintenance/**`, `/api/v1/kpi/**`, `/api/v1/suppliers/**` e `/api/v1/oee/planned-downtimes/**` dependem exclusivamente de `@PreAuthorize` method-level. O padrão `.anyRequest().authenticated()` garante que nenhum endpoint é acessível sem autenticação, mas a ausência de regras URL-level explícitas para os módulos mais recentes cria assimetria de documentação e configuração — um dev que leia apenas o `SecurityConfig` não consegue mapear quais roles acessam quais módulos. Reportado como SEC-030 (Beatriz). **Sem impacto de segurança imediato** — method-level `@PreAuthorize` é suficiente e é a primeira linha de defesa.

**Decisão**: Adicionar regras URL-level explícitas para todos os módulos, espelhando as roles definidas nos `@PreAuthorize` dos respectivos controllers. As regras URL-level são uma segunda camada declarativa — não substituem `@PreAuthorize`, que continua como fonte primária de controle de acesso.

```java
// SecurityConfig.java — authorizeHttpRequests, após as regras de QMS existentes:

// Maintenance reads: OPERATOR and above
.requestMatchers(HttpMethod.GET, "/api/v1/maintenance/**")
    .hasAnyRole("OPERATOR", "SUPERVISOR", "ADMIN")
// Maintenance writes: SUPERVISOR and above
.requestMatchers(HttpMethod.POST, "/api/v1/maintenance/**",
                                  "/api/v1/maintenance/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
.requestMatchers(HttpMethod.PUT, "/api/v1/maintenance/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/v1/maintenance/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
// OEE Planned Downtime writes: SUPERVISOR and above (PUT/DELETE para /api/v1/oee/**)
.requestMatchers(HttpMethod.PUT, "/api/v1/oee/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/v1/oee/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
// Analytics: SUPERVISOR and above
.requestMatchers("/api/v1/analytics/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
// KPI: SUPERVISOR and above
.requestMatchers("/api/v1/kpi/**")
    .hasAnyRole("SUPERVISOR", "ADMIN")
// Suppliers: follows QMS pattern (GET OPERATOR+, writes SUPERVISOR+)
.requestMatchers(HttpMethod.GET, "/api/v1/qms/suppliers/**")
    .hasAnyRole("OPERATOR", "SUPERVISOR", "ADMIN")
// (já coberto pela regra GET /api/v1/qms/** existente)
```

**Nota sobre ordem das regras**: Spring Security avalia `requestMatchers` na ordem de declaração — a primeira regra que casa vence. As regras mais específicas (ex: `/api/v1/qms/non-conformances` POST) devem preceder as mais genéricas (`/api/v1/qms/**` GET). A ordenação final deve ser revisada para garantir que regras específicas não sejam eclipsadas por regras genéricas anteriores.

---

### Decisão 6 — Ordem de implementação na Sprint 18

Para minimizar conflitos de merge e garantir consistência:

| Ordem | Item | Tipo | Impacto |
|-------|------|------|---------|
| 1 | `AuditAction.DOWNTIME_UPDATED` no enum (SEC-057/058) | Backend shared | Base para Decisão 1 |
| 2 | `@Size(max=500)` nos DTOs de PlannedDowntime (SEC-059) | Backend OEE | Isolado, sem impacto em outros módulos |
| 3 | Auditoria em `CreatePlannedDowntimeUseCase` e `DeletePlannedDowntimeUseCase` (SEC-057) | Backend OEE | Requer item 1 |
| 4 | Auditoria em `UpdatePlannedDowntimeUseCase` + assinatura com `username` (SEC-058) | Backend OEE | Requer item 1; atualiza assinatura do use case e controller |
| 5 | Renomear `findById` → `findWithEquipmentById` + atualizar callers (SH-39) | Backend Maintenance | Isolado; requer atualização de todos os testes que mockam `findById` nos use cases de schedule |
| 6 | Teto visual de 3 badges no `MaintenanceCalendarComponent` (SH-40) | Frontend Maintenance | Isolado; adicionar computed + template + spec |
| 7 | URL-level rules no `SecurityConfig` (SEC-030) | Backend Security | Isolado; testar com `@SpringBootTest` ou `MockMvc` para garantir que regras não quebram endpoints existentes |

---

### Contrato de API — novos comportamentos

| Situação | Antes (buggy) | Depois (corrigido) |
|----------|---------------|-------------------|
| `POST /oee/planned-downtimes` (SUPERVISOR) | sem entrada no AuditLog | `DOWNTIME_CREATED` registrado com date, durationMinutes, reason, equipmentId |
| `DELETE /oee/planned-downtimes/{id}` (SUPERVISOR) | sem entrada no AuditLog | `DOWNTIME_DELETED` registrado com dados da parada deletada |
| `PUT /oee/planned-downtimes/{id}` (SUPERVISOR) | sem entrada no AuditLog | `DOWNTIME_UPDATED` registrado com novos dados |
| `POST /oee/planned-downtimes` com `description` > 500 chars | HTTP 500 "Erro interno" | HTTP 400 `{ "message": "description deve ter no máximo 500 caracteres" }` |
| `PUT /oee/planned-downtimes/{id}` com `description` > 500 chars | HTTP 500 "Erro interno" | HTTP 400 `{ "message": "description deve ter no máximo 500 caracteres" }` |
| Calendário de manutenção com 5 planos DAILY ativos | 5 badges por célula × 42 células | máximo 3 badges por célula + "+N mais" |

---

### Consequências
✅ Auditoria completa para todas as operações de escrita de `PlannedDowntime` — elimina ponto cego de rastreabilidade forense em módulo que impacta métricas de OEE
✅ `@Size(max=500)` nos DTOs alinha validação do DTO com a constraint do banco, convertendo erro 500 silencioso em 400 descritivo para o cliente
✅ `findWithEquipmentById` torna explícito o comportamento de JOIN FETCH — elimina surpresa para futuros mantenedores do `MaintenanceScheduleRepository`
✅ Teto de 3 badges no calendário melhora legibilidade para instalações com muitos planos DAILY ativos sem alterar a lógica de negócio subjacente
✅ URL-level rules no `SecurityConfig` para `/analytics`, `/maintenance` e `/kpi` permitem auditar a política de acesso sem precisar ler todos os controllers
⚠️ Renomear `findById` para `findWithEquipmentById` requer atualizar todos os mocks em testes dos use cases de schedule — risco de teste quebrado se algum caller for esquecido
⚠️ URL-level rules no `SecurityConfig` duplicam informação que já está nos `@PreAuthorize` — qualquer mudança de role em um controller deve ser espelhada no `SecurityConfig` (duas fontes de verdade); documentar no CLAUDE.md que `@PreAuthorize` é fonte primária e SecurityConfig é documentação declarativa secundária
⚠️ `UpdatePlannedDowntimeUseCase` terá assinatura alterada (`username` adicionado) — verificar se há testes existentes do use case que precisam ser atualizados para passar o parâmetro adicional
