## ADR-011: Preventive Maintenance Scheduling — Planos Recorrentes de Manutenção Preventiva
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-040, US-041, US-042

### Contexto

ADR-008 explicitamente deferiu a modelagem de recorrência em `WorkOrder` para evitar YAGNI ("Manutenção preventiva com recorrência é out-of-scope"). Esta ADR introduce o modelo. O sistema precisa que supervisores configurem planos de manutenção recorrente (semanal, mensal) por equipamento, e que o sistema crie automaticamente as ordens de serviço preventivas no prazo.

---

### Decisão 1 — Nova entidade `MaintenanceSchedule`

```java
@Entity
@Table(name = "maintenance_schedule", indexes = {
    @Index(name = "idx_schedule_equipment", columnList = "equipment_id"),
    @Index(name = "idx_schedule_next_run",  columnList = "next_run_at"),
    @Index(name = "idx_schedule_active",    columnList = "active")
})
public class MaintenanceSchedule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderPriority priority;   // reutiliza enum de WorkOrder

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleRecurrence recurrence; // DAILY | WEEKLY | MONTHLY

    private Integer dayOfWeek;   // 1=SEG … 7=DOM; nulo quando recurrence != WEEKLY
    private Integer dayOfMonth;  // 1–28; nulo quando recurrence != MONTHLY

    private LocalDate nextRunAt;  // próxima data de execução
    private LocalDate lastRunAt;  // última data em que gerou uma OS (null se nunca rodou)

    private boolean active = true;
    private String createdBy;     // username do criador (JWT)
    private LocalDateTime createdAt;
}

// Novo enum em maintenance/domain/
public enum ScheduleRecurrence { DAILY, WEEKLY, MONTHLY }
```

**Sem relacionamento inverso** em `Equipment` → `MaintenanceSchedule`: consulta sempre via `scheduleRepository.findByEquipmentId(id)`.

---

### Decisão 2 — `WorkOrder` ganha FK opcional para o plano de origem

```java
// migration: adicionar coluna nullable em work_order
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "schedule_id")
private MaintenanceSchedule schedule;  // null para OSs manuais
```

OSs criadas pelo scheduler têm `schedule` preenchido. OSs criadas manualmente têm `schedule = null`. Campo `scheduleId` exposto em `WorkOrderResponse` (nullable UUID).

---

### Decisão 3 — Package e classes

```
maintenance/
├── domain/
│   ├── MaintenanceSchedule.java       (novo)
│   └── ScheduleRecurrence.java        (novo enum)
├── application/
│   ├── dto/
│   │   ├── CreateScheduleRequest.java  (novo)
│   │   ├── UpdateScheduleRequest.java  (novo)
│   │   └── ScheduleResponse.java       (novo)
│   └── usecase/
│       ├── CreateScheduleUseCase.java       (novo)
│       ├── GetScheduleListUseCase.java      (novo)
│       ├── UpdateScheduleUseCase.java       (novo)
│       ├── DeactivateScheduleUseCase.java   (novo)
│       └── RunDueSchedulesUseCase.java      (novo — chamado pelo scheduler)
├── infrastructure/
│   └── MaintenanceScheduleRepository.java  (novo)
└── presentation/
    └── MaintenanceController.java           (existente — adicionar endpoints de schedule)
```

---

### Decisão 4 — Cálculo de `nextRunAt`

```java
// RunDueSchedulesUseCase — chamado diariamente às 06:00
LocalDate today = LocalDate.now();
List<MaintenanceSchedule> due = scheduleRepo.findByActiveTrueAndNextRunAtLessThanEqual(today);
for (MaintenanceSchedule s : due) {
    // cria WorkOrder via CreateWorkOrderUseCase (reutiliza lógica existente)
    workOrderRepo.save(buildWorkOrder(s));
    s.setLastRunAt(today);
    s.setNextRunAt(calculateNext(today, s.getRecurrence(), s.getDayOfWeek(), s.getDayOfMonth()));
    scheduleRepo.save(s);
}

// calculateNext
LocalDate calculateNext(LocalDate from, ScheduleRecurrence rec, Integer dow, Integer dom) {
    return switch (rec) {
        case DAILY   -> from.plusDays(1);
        case WEEKLY  -> from.with(DayOfWeek.of(dow)).plusWeeks(from.getDayOfWeek().getValue() >= dow ? 1 : 0);
        case MONTHLY -> from.plusMonths(1).withDayOfMonth(Math.min(dom, from.plusMonths(1).lengthOfMonth()));
    };
}
```

**`@Scheduled` em `MaintenanceSchedulerJob`** — bean separado do use case para testabilidade:

```java
@Component
public class MaintenanceSchedulerJob {
    @Scheduled(cron = "0 0 6 * * *", zone = "${app.maintenance.timezone:America/Sao_Paulo}")
    public void runDueSchedules() {
        runDueSchedulesUseCase.execute();
    }
}
```

---

### Decisão 5 — Endpoints de schedules

| Método | Endpoint | Auth | Retorno |
|--------|----------|------|---------|
| POST | /api/v1/maintenance/schedules | SUPERVISOR+ | 201 ScheduleResponse |
| GET | /api/v1/maintenance/schedules | OPERATOR+ | List<ScheduleResponse> |
| GET | /api/v1/maintenance/schedules/{id} | OPERATOR+ | ScheduleResponse / 404 |
| PUT | /api/v1/maintenance/schedules/{id} | SUPERVISOR+ | 200 ScheduleResponse / 404 |
| PUT | /api/v1/maintenance/schedules/{id}/deactivate | SUPERVISOR+ | 204 / 404 |
| POST | /api/v1/admin/maintenance/schedules/run-now | ADMIN | 200 `{ "created": N }` |

`run-now` dispara `RunDueSchedulesUseCase` imediatamente para testar sem aguardar o cron.

---

### Decisão 6 — Regras de validação do plano

- `recurrence = WEEKLY` → `dayOfWeek` obrigatório (1–7); `dayOfMonth` ignorado
- `recurrence = MONTHLY` → `dayOfMonth` obrigatório (1–28); `dayOfWeek` ignorado; máximo 28 evita problemas com fevereiro
- `recurrence = DAILY` → ambos ignorados
- Equipamento com `active = false` → retorna `422` com `{ "message": "Equipamento inativo não pode receber plano" }`

---

### Decisão 7 — Frontend: visão de calendário

Calendário de grade mensal (semanas em linhas, dias em colunas) implementado sem dependência externa — renderizado com `@for` e `@if` sobre uma matriz de `LocalDate[][]` gerada no componente. Cada dia exibe badges de planos programados para aquela data. Clique no badge abre o detalhe do plano.

**Sem biblioteca de calendário** — manter consistência com Angular Material e evitar dependência extra.

---

### Consequências
✅ `MaintenanceSchedule` é entidade independente de `WorkOrder` — sem herança problemática
✅ `calculateNext` em Java puro — testável sem banco
✅ `@Scheduled` separado do use case — testável com mocks sem disparar o cron
✅ `dayOfMonth` limitado a 28 — elimina edge case de meses curtos
⚠️ Migration necessária: tabela `maintenance_schedule` + coluna `schedule_id` nullable em `work_order`
⚠️ Se o scheduler falhar em um dia (restart, manutenção), OSs do dia perdido **não** são recuperadas retroativamente — comportamento aceitável para o escopo atual; logging de `ERROR` obrigatório em caso de falha
