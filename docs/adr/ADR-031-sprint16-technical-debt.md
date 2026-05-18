## ADR-031: Technical Debt Sprint 16 — Auditoria, Validações e Isolamento Transacional
**Status**: Aprovado
**Data**: 2026-05-20
**US relacionadas**: US-043, US-044, US-045 (Sprint 16 — cleanup obrigatório junto com a funcionalidade principal)

### Contexto

Ao longo das Sprints 13–15 acumulou-se um conjunto de itens diferidos pelos revisores Helena (code review), Beatriz (security review) e Maiana (QA). Todos os itens estão marcados para Sprint 16. Como a Sprint 16 é de Advanced Analytics (ADR-012 — funcionalidade nova sem dependências de banco/domínio existente), os itens de débito técnico podem ser resolvidos em paralelo ou como histórias separadas sem risco de conflito.

Esta ADR consolida as decisões arquiteturais para os itens diferidos, define a prioridade de cada um e especifica como cada correção deve ser implementada para manter consistência com as convenções do projeto.

---

### Decisão 1 — Auditoria de operações de update: SCHEDULE_UPDATED e SUPPLIER_UPDATED

**Problema**: `UpdateScheduleUseCase` e `UpdateSupplierUseCase` não registram no `AuditLog`. Cria assimetria com os pares CREATE/DEACTIVATE de cada módulo. Reportado como SEC-051 (Beatriz) e SEC-050 (Beatriz), confirmado como SH-38 (Helena).

Adicionalmente, `UpdateUserRoleUseCase` (SEC-045) e `ReactivateUserUseCase` (SEC-046) também carecem de auditoria — mesmo padrão.

**Decisão**: Adicionar as quatro ações ao enum `AuditAction` e instrumentar os use cases correspondentes.

```java
// common/domain/AuditAction.java — adicionar:
SCHEDULE_UPDATED,
SUPPLIER_UPDATED,
USER_ROLE_UPDATED,
USER_REACTIVATED
```

**Instrumentação dos use cases**:

```java
// UpdateScheduleUseCase.execute(UUID id, UpdateScheduleRequest request, String username)
// Após schedule = scheduleRepository.save(schedule):
auditService.log(AuditAction.SCHEDULE_UPDATED, username, schedule.getId().toString(),
    Map.of("recurrence", schedule.getRecurrence().name(), "title", schedule.getTitle()));

// UpdateSupplierUseCase.execute(UUID id, UpdateSupplierRequest request, String username)
// Após supplier = supplierRepository.save(supplier):
auditService.log(AuditAction.SUPPLIER_UPDATED, username, supplier.getId().toString(),
    Map.of("name", supplier.getName(), "contactEmail", supplier.getContactEmail()));

// UpdateUserRoleUseCase.execute(UUID id, Role newRole, String username)
auditService.log(AuditAction.USER_ROLE_UPDATED, username, id.toString(),
    Map.of("newRole", newRole.name()));

// ReactivateUserUseCase.execute(UUID id, String username)
auditService.log(AuditAction.USER_REACTIVATED, username, id.toString(), Map.of());
```

**Impacto em assinaturas**: `UpdateScheduleUseCase.execute()` atualmente não recebe `username` — a assinatura deve ser atualizada para `execute(UUID id, UpdateScheduleRequest request, String username)` e o `MaintenanceController` deve passar `principal.getName()`.

---

### Decisão 2 — Validação de bounds em parâmetros de analytics de fornecedores

**Problema**: `GET /api/v1/qms/suppliers/{id}/quality-score?days=N` e `GET /api/v1/qms/suppliers/quality-ranking?days=N` aceitam `Integer.MAX_VALUE` como `days`, podendo retornar todo o histórico de NCs e causar memory pressure. Reportado como SEC-049.

**Decisão**: Adicionar `@Min` e `@Max` com `@Validated` no controller.

```java
// SupplierController.java
@GetMapping("/{id}/quality-score")
public SupplierQualityScore getQualityScore(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "90") @Min(1) @Max(730) int days) { ... }

@GetMapping("/quality-ranking")
public List<SupplierQualityScore> getRanking(
        @RequestParam(defaultValue = "90") @Min(1) @Max(730) int days) { ... }
```

`@Validated` deve ser adicionado na anotação da classe `SupplierController`. Violação de `@Min`/`@Max` em `@RequestParam` lança `ConstraintViolationException` (não `MethodArgumentNotValidException`). O `GlobalExceptionHandler` deve ter handler para `ConstraintViolationException` retornando 400:

```java
@ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
public ResponseEntity<Map<String, Object>> handleConstraintViolation(
        jakarta.validation.ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
            .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
            .collect(Collectors.joining("; "));
    return ResponseEntity.badRequest().body(Map.of(
            "message", message.isBlank() ? "Parâmetro inválido" : message,
            "timestamp", Instant.now().toString()
    ));
}
```

Os endpoints de analytics da Sprint 16 (`weeks` e `months`) devem usar o mesmo padrão:
- `weeks`: `@Min(1) @Max(52)`
- `months`: `@Min(1) @Max(24)`
- `days`: `@Min(1) @Max(730)` (para NC pareto)

---

### Decisão 3 — @Email em CreateSupplierRequest

**Problema**: `contactEmail` em `CreateSupplierRequest` aceita strings arbitrárias, permitindo valores com query params que seriam renderizados em links `mailto:`. Reportado como SEC-048.

**Decisão**: Adicionar `@Email` na anotação do campo.

```java
// CreateSupplierRequest.java
public record CreateSupplierRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Email @Size(max = 100) String contactEmail,  // adicionar @Email
    @Size(max = 50) String contactPhone,
    @Size(max = 300) String address,
    LocalDate onboardedAt
) {}
```

O `@Email` do Bean Validation rejeita strings com query params (`email?subject=...`). Mensagem de erro padrão já é adequada; opcionalmente: `@Email(message = "contactEmail deve ser um endereço de email válido")`.

---

### Decisão 4 — Isolamento transacional em RunDueSchedulesUseCase

**Problema**: `RunDueSchedulesUseCase.execute()` processa todos os planos em uma única transação JPA. Um erro de banco que marque a transação como `rollback-only` (ex: `DeadlockLoserDataAccessException`, `ConstraintViolationException` no flush) reverte todas as OSs criadas na sessão — mesmo as que individualmente tiveram sucesso. O Javadoc do método afirma isolamento por plano, mas isso é falso para erros detectados no flush/commit. Reportado como MF-4 (Helena).

**Decisão**: Extrair o processamento de cada plano para um `@Service` auxiliar `ScheduleProcessorService` com transação `REQUIRES_NEW`.

```java
// maintenance/application/usecase/ScheduleProcessorService.java
@Service
@RequiredArgsConstructor
public class ScheduleProcessorService {

    private final WorkOrderRepository workOrderRepository;
    private final MaintenanceScheduleRepository scheduleRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(MaintenanceSchedule schedule, LocalDate today) {
        WorkOrder wo = WorkOrder.builder()
                .equipment(schedule.getEquipment())
                .type(WorkOrderType.PREVENTIVE)
                .title(schedule.getTitle())
                .priority(schedule.getPriority())
                .status(WorkOrderStatus.OPEN)
                .openedBy("scheduler")
                .openedAt(today.atStartOfDay())
                .schedule(schedule)
                .build();
        workOrderRepository.save(wo);
        schedule.setLastRunAt(today);
        schedule.setNextRunAt(ScheduleRecurrenceHelper.calculateNext(schedule, today));
        scheduleRepository.save(schedule);
    }
}

// RunDueSchedulesUseCase.java — remover @Transactional da classe/método
@Service
@RequiredArgsConstructor
public class RunDueSchedulesUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;
    private final ScheduleProcessorService scheduleProcessorService;

    public int execute() {
        LocalDate today = LocalDate.now();
        List<MaintenanceSchedule> due = scheduleRepository.findDueSchedules(today);
        int created = 0;
        for (MaintenanceSchedule schedule : due) {
            try {
                scheduleProcessorService.processOne(schedule, today);
                created++;
            } catch (Exception e) {
                log.error("Falha ao processar plano {}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
        return created;
    }
}
```

**Impacto em testes**: `RunDueSchedulesUseCaseTest` precisa ser atualizado para:
1. Injetar `ScheduleProcessorService` (mock ou real com contexto de Spring)
2. Adicionar `verify(scheduleRepository, times(1)).save(any(MaintenanceSchedule.class))` para o cenário de falha parcial (G41 de Helena)

---

### Decisão 5 — Gaps de cobertura de testes (Maiana G16, G17)

**Problema**: `UpdateScheduleUseCase`, `DeactivateScheduleUseCase` e o fluxo de submit bem-sucedido do `ScheduleFormComponent` estão sem testes unitários. Reportado como G16 e G17 por Maiana.

**Decisão**: Adicionar os testes como parte do cleanup da Sprint 16, não como US separada. Escopo mínimo:

**Backend**:
```
UpdateScheduleUseCaseTest:
  - shouldUpdateScheduleFields_andRecalculateNextRunAt()
  - shouldThrow_whenScheduleNotFound()

DeactivateScheduleUseCaseTest:
  - shouldSetActiveToFalse_andAudit()
  - shouldThrow_whenScheduleNotFound()
```

**Frontend**:
```
ScheduleFormComponent.spec.ts:
  - it('should navigate to list with toast on successful create')
  - it('should navigate to list with toast on successful update')
```

---

### Decisão 6 — Ordem de implementação na Sprint 16

Para minimizar conflitos de merge e garantir que o débito técnico não bloqueie a funcionalidade principal:

| Ordem | Item | Tipo | Impacto |
|-------|------|------|---------|
| 1 | `AuditAction` + `GlobalExceptionHandler` (`ConstraintViolationException`) | Backend shared | Base para todos os outros |
| 2 | `@Email` em `CreateSupplierRequest` + `@Min`/`@Max` no `SupplierController` | Backend QMS | Isolado, sem impacto em outros módulos |
| 3 | `UpdateScheduleUseCase` + assinatura com `username` + auditoria | Backend Maintenance | Requer `AuditAction.SCHEDULE_UPDATED` (item 1) |
| 4 | `UpdateSupplierUseCase` + auditoria; `UpdateUserRoleUseCase`/`ReactivateUserUseCase` + auditoria | Backend multi-módulo | Requer `AuditAction.SUPPLIER_UPDATED` etc. (item 1) |
| 5 | `ScheduleProcessorService` + refatoração de `RunDueSchedulesUseCase` | Backend Maintenance | Não depende de outros itens desta lista |
| 6 | Testes unitários (US-gaps) | Test | Após itens 3 e 5 |
| 7 | US-043, US-044, US-045 (Analytics) | Funcionalidade nova | Independente; pode rodar em paralelo com itens 1–6 |

---

### Contrato de API — novos comportamentos

| Situação | Antes (buggy) | Depois (corrigido) |
|----------|---------------|-------------------|
| `days=2147483647` em quality-score | 200 OK (memory pressure) | 400 Bad Request `{ "message": "days: deve ser menor que ou igual a 730" }` |
| `weeks=100` em analytics OEE | 200 OK (> 52 semanas) | 400 Bad Request |
| `contactEmail="foo?bar=baz"` no POST supplier | 201 Created | 400 Bad Request `{ "message": "contactEmail deve ser um endereço de email válido" }` |
| PUT /schedules/{id} sem username no use case | sem auditoria | `SCHEDULE_UPDATED` no AuditLog |

---

### Consequências
✅ Auditoria completa em todas as operações de escrita de todos os módulos (Schedule, Supplier, User)
✅ Bounds nos parâmetros de tempo evitam memory pressure e são reutilizados nos novos endpoints de analytics
✅ `ScheduleProcessorService` com `REQUIRES_NEW` garante o contrato documentado no Javadoc do scheduler
✅ `ConstraintViolationException` no `GlobalExceptionHandler` fecha lacuna de tratamento de erro em `@RequestParam` validados
⚠️ `ScheduleProcessorService` requer ajuste nos testes existentes de `RunDueSchedulesUseCase` — a injeção muda de `workOrderRepository` diretamente para o service auxiliar
⚠️ `@Validated` no `SupplierController` precisa ser propagado para quaisquer novos controllers que usem `@Min`/`@Max` em `@RequestParam` — convenção a documentar no CLAUDE.md
