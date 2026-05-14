## ADR-008: Maintenance Module (TPM) — Equipment & Work Orders
**Status**: Aprovado
**Data**: 2026-05-11
**US relacionadas**: US-027, US-028, US-029

### Contexto
Terceiro domínio do Industrial Hub: Manutenção (Total Productive Maintenance). O módulo gerencia o cadastro de equipamentos e o ciclo de vida de ordens de serviço (OS) corretivas e preventivas, com cálculo automático de MTTR. Segue os padrões estabelecidos em ADR-005/006/007.

---

### Decisão 1 — Package structure: `maintenance/`

```
src/main/java/com/industrialhub/backend/maintenance/
├── domain/
│   ├── Equipment.java               (entidade JPA)
│   ├── WorkOrder.java               (entidade JPA)
│   ├── EquipmentType.java           (enum)
│   ├── EquipmentStatus.java         (enum)
│   ├── WorkOrderType.java           (enum)
│   ├── WorkOrderPriority.java       (enum)
│   └── WorkOrderStatus.java         (enum)
├── application/
│   ├── dto/
│   │   ├── CreateEquipmentRequest.java
│   │   ├── EquipmentResponse.java
│   │   ├── CreateWorkOrderRequest.java
│   │   ├── WorkOrderResponse.java
│   │   └── WorkOrderMetricsResponse.java
│   └── usecase/
│       ├── CreateEquipmentUseCase.java
│       ├── UpdateEquipmentUseCase.java
│       ├── CreateWorkOrderUseCase.java
│       ├── TransitionWorkOrderStatusUseCase.java
│       └── GetWorkOrderMetricsUseCase.java
├── infrastructure/
│   ├── EquipmentRepository.java
│   └── WorkOrderRepository.java
└── presentation/
    └── MaintenanceController.java
```

Frontend: `src/app/maintenance/` com `maintenance.service.ts` e sub-pastas `equipment/`, `work-orders/`.

---

### Decisão 2 — Entidades e relacionamentos

```java
// Equipment
@Entity
@Table(name = "equipment", indexes = {
    @Index(name = "idx_equipment_code",   columnList = "code", unique = true),
    @Index(name = "idx_equipment_status", columnList = "status"),
    @Index(name = "idx_equipment_type",   columnList = "type")
})
public class Equipment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true, nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 100)
    private String location;
    @Enumerated(EnumType.STRING)
    private EquipmentType type;        // MACHINE | TOOL | VEHICLE | INFRASTRUCTURE
    @Enumerated(EnumType.STRING)
    private EquipmentStatus status;    // OPERATIONAL | UNDER_MAINTENANCE | DECOMMISSIONED
    private LocalDate acquiredAt;
    private boolean active = true;
}

// WorkOrder
@Entity
@Table(name = "work_order", indexes = {
    @Index(name = "idx_wo_equipment_status", columnList = "equipment_id, status"),
    @Index(name = "idx_wo_opened_at",        columnList = "openedAt"),
    @Index(name = "idx_wo_priority",         columnList = "priority")
})
public class WorkOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;
    @Enumerated(EnumType.STRING)
    private WorkOrderType type;        // CORRECTIVE | PREVENTIVE
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    private WorkOrderPriority priority; // LOW | MEDIUM | HIGH | URGENT
    @Enumerated(EnumType.STRING)
    private WorkOrderStatus status;    // OPEN | IN_PROGRESS | DONE | CANCELLED
    private String assignedTo;
    private String openedBy;           // JWT username
    private LocalDateTime openedAt;
    private LocalDateTime startedAt;   // null until IN_PROGRESS
    private LocalDateTime closedAt;    // null until DONE or CANCELLED
}
```

**Sem cascade** em Equipment → WorkOrder: OS são entidades independentes. Delete de Equipment é bloqueado se existirem OS abertas (verificação no use case).

---

### Decisão 3 — Máquina de estados da Ordem de Serviço

```
OPEN ──► IN_PROGRESS ──► DONE
  │            │
  └────────────┴──► CANCELLED
```

| De | Para | Efeito colateral | Quem |
|---|---|---|---|
| OPEN | IN_PROGRESS | `startedAt = now()` | SUPERVISOR+ |
| IN_PROGRESS | DONE | `closedAt = now()`; se CORRECTIVE: checar outras OS abertas do equipamento → se nenhuma, `equipment.status = OPERATIONAL` | SUPERVISOR+ |
| OPEN | CANCELLED | `closedAt = now()` | SUPERVISOR+ |
| IN_PROGRESS | CANCELLED | `closedAt = now()` | SUPERVISOR+ |

**Efeito colateral de criação de OS CORRECTIVE:**
`equipment.status → UNDER_MAINTENANCE` imediatamente ao criar a OS (dentro do mesmo `@Transactional`).

**Restauração de OPERATIONAL:**
Só ocorre quando a OS DONE é a **última OS CORRECTIVE com status ≠ DONE/CANCELLED** para aquele equipamento. Verificar via `workOrderRepository.existsByEquipmentAndTypeAndStatusIn(equipment, CORRECTIVE, [OPEN, IN_PROGRESS])`.

---

### Decisão 4 — Cálculo de MTTR

MTTR (Mean Time To Repair) = média de `(closedAt − startedAt)` em horas para OS do tipo CORRECTIVE com status DONE.

```java
// WorkOrderMetricsResponse
public record WorkOrderMetricsResponse(
    Double mttr,          // null se nenhuma OS DONE existe; horas decimais
    long totalOrders,
    long openOrders
) {}
```

Query via JPQL:
```java
@Query("""
    SELECT AVG(TIMESTAMPDIFF(SECOND, w.startedAt, w.closedAt)) / 3600.0
    FROM WorkOrder w
    WHERE w.equipment.id = :equipmentId
      AND w.type = 'CORRECTIVE'
      AND w.status = 'DONE'
      AND w.startedAt IS NOT NULL
""")
Double calculateMttrByEquipment(@Param("equipmentId") UUID equipmentId);
```

`TIMESTAMPDIFF` é suportado por H2 (testes) e PostgreSQL 16 via função equivalente. **Risco:** H2 usa `TIMESTAMPDIFF(SECOND, ...)` mas PostgreSQL usa `EXTRACT(EPOCH FROM ...)`. Usar `ChronoUnit.SECONDS.between()` em Java ao invés de SQL nativo para portabilidade:

```java
// No use case — sem SQL nativo
List<WorkOrder> done = repository.findByEquipmentIdAndTypeAndStatus(id, CORRECTIVE, DONE);
OptionalDouble mttr = done.stream()
    .filter(wo -> wo.getStartedAt() != null && wo.getClosedAt() != null)
    .mapToLong(wo -> ChronoUnit.SECONDS.between(wo.getStartedAt(), wo.getClosedAt()))
    .average()
    .stream()
    .map(seconds -> seconds / 3600.0)
    .findFirst(); // horas decimais
```

---

### Decisão 5 — Soft delete de Equipment

`DELETE /api/v1/maintenance/equipment/{id}` não remove fisicamente — seta `active = false`. Bloqueio se existirem OS abertas:

```java
boolean hasOpenOrders = workOrderRepository.existsByEquipmentIdAndStatusIn(
    id, List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS)
);
if (hasOpenOrders) throw new EquipmentHasOpenOrdersException(...); // → 409
```

Listagem default filtra `active = true`:
```java
// EquipmentRepository
List<Equipment> findByActiveTrueOrderByNameAsc();
List<Equipment> findByActiveTrueAndTypeAndStatusOrderByNameAsc(...);
```

---

### Contrato de API — módulo Manutenção

| Método | Endpoint | Auth | Status |
|---|---|---|---|
| POST | /api/v1/maintenance/equipment | ADMIN | 201 / 409 |
| GET | /api/v1/maintenance/equipment | OPERATOR+ | 200 |
| GET | /api/v1/maintenance/equipment/{id} | OPERATOR+ | 200 / 404 |
| PUT | /api/v1/maintenance/equipment/{id} | ADMIN | 200 / 404 / 422 |
| DELETE | /api/v1/maintenance/equipment/{id} | ADMIN | 204 / 409 |
| POST | /api/v1/maintenance/work-orders | SUPERVISOR+ | 201 |
| GET | /api/v1/maintenance/work-orders | OPERATOR+ | 200 (paginado) |
| PUT | /api/v1/maintenance/work-orders/{id}/status | SUPERVISOR+ | 200 / 422 |
| GET | /api/v1/maintenance/work-orders/metrics | OPERATOR+ | 200 |

---

### Decisão 6 — Campos imutáveis e DTO de atualização

`PUT /api/v1/maintenance/equipment/{id}` aceita `UpdateEquipmentRequest` com apenas: `name`, `location`, `type`, `acquiredAt`.

Os campos `code` e `status` são **imutáveis via API**:
- `code` é chave de negócio única — alteração exigiria auditoria fora do escopo atual
- `status` só muda via criação/transição de OS (efeito colateral do use case)

```java
public record UpdateEquipmentRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String location,
    @NotNull EquipmentType type,
    LocalDate acquiredAt               // nullable
) {}
```

Tentativa de alterar `code` via PUT retorna `400` (campo ignorado ou rejeitado via `@JsonIgnoreProperties`).

---

### Decisão 7 — Scope por sprint

| Sprint | US | Endpoints incluídos |
|--------|----|---------------------|
| 7 | US-027, US-028 | Todos exceto `/metrics` |
| 8 | US-029 | `GET /api/v1/maintenance/work-orders/metrics` |

`GetWorkOrderMetricsUseCase` e `WorkOrderMetricsResponse` são criados na Sprint 8.

---

### Consequências
✅ Cálculo MTTR em Java (sem SQL nativo) — funciona igual em H2 e PostgreSQL
✅ Soft delete com verificação de OS abertas — garante integridade referencial sem FK desnecessária
✅ Efeito colateral de status do equipamento dentro do mesmo `@Transactional` — atomicidade garantida
✅ `code` e `status` imutáveis via PUT elimina edge cases de consistência
⚠️ `existsByEquipmentAndTypeAndStatusIn` — verificar suporte do Spring Data para `StatusIn` com lista de enum; usar `@Query` explícito se necessário
⚠️ Manutenção preventiva com recorrência é out-of-scope — não modelar `recurrenceRule` na entidade agora para evitar YAGNI; adicionar em sprint futura via migration
