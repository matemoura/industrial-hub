## ADR-014: Spare Parts & Inventory Management
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-049, US-050, US-051

### Contexto

Ordens de serviço de manutenção consomem peças e insumos. Sem rastreabilidade de estoque, o time não sabe quais peças estão disponíveis nem quando repor. Esta ADR modela o catálogo de peças, o consumo por OS e os alertas de estoque mínimo.

---

### Decisão 1 — Entidades

```java
// Catálogo de peças
@Entity
@Table(name = "spare_part", indexes = {
    @Index(name = "idx_part_code",     columnList = "code", unique = true),
    @Index(name = "idx_part_category", columnList = "category")
})
public class SparePart {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;          // código interno (ex: "ROL-6205")

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;      // ex: "Rolamentos", "Correias", "Filtros"

    private String unit;          // ex: "un", "m", "litros"
    private Integer stockQty;     // quantidade atual em estoque
    private Integer minStockQty;  // quantidade mínima antes de alerta
    private boolean active = true;
}

// Consumo de peça em uma OS
@Entity
@Table(name = "work_order_part")
public class WorkOrderPart {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spare_part_id", nullable = false)
    private SparePart sparePart;

    @Column(nullable = false)
    private Integer quantity;     // quantidade consumida

    private String addedBy;       // username do técnico
    private LocalDateTime addedAt;
}
```

---

### Decisão 2 — Atualização de estoque

O consumo de peça é registrado via `POST /api/v1/maintenance/work-orders/{id}/parts`. Dentro do mesmo `@Transactional`:
1. Cria `WorkOrderPart`
2. Decrementa `sparePart.stockQty -= quantity`
3. Se `stockQty < minStockQty`, dispara notificação de estoque baixo (via `NotificationService` de ADR-013) — **não** lança exceção, apenas alerta

**Sem estoque negativo**: se `stockQty - quantity < 0`, retorna `422` com `{ "message": "Estoque insuficiente: disponível N, solicitado M" }`.

---

### Decisão 3 — Package

```
maintenance/
├── domain/
│   ├── SparePart.java
│   └── WorkOrderPart.java
├── application/dto/
│   ├── CreateSparePartRequest.java
│   ├── SparePartResponse.java
│   ├── AddWorkOrderPartRequest.java
│   └── WorkOrderPartResponse.java
├── application/usecase/
│   ├── CreateSparePartUseCase.java
│   ├── GetSparePartListUseCase.java
│   ├── UpdateSparePartStockUseCase.java   // ajuste manual de estoque (ADMIN)
│   └── AddWorkOrderPartUseCase.java
└── infrastructure/
    ├── SparePartRepository.java
    └── WorkOrderPartRepository.java
```

Endpoints adicionados em `MaintenanceController`.

---

### Decisão 4 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/maintenance/spare-parts | ADMIN | criar peça |
| GET | /api/v1/maintenance/spare-parts | OPERATOR+ | listar (filtro: category, belowMin) |
| GET | /api/v1/maintenance/spare-parts/{id} | OPERATOR+ | detalhe |
| PUT | /api/v1/maintenance/spare-parts/{id} | ADMIN | atualizar dados |
| PUT | /api/v1/maintenance/spare-parts/{id}/stock | ADMIN | ajustar estoque manualmente |
| POST | /api/v1/maintenance/work-orders/{id}/parts | SUPERVISOR+ | adicionar consumo de peça |
| GET | /api/v1/maintenance/work-orders/{id}/parts | OPERATOR+ | listar peças da OS |
| DELETE | /api/v1/maintenance/work-orders/{id}/parts/{partId} | SUPERVISOR+ | remover (restaura estoque) |

`GET /api/v1/maintenance/spare-parts?belowMin=true` retorna apenas peças com `stockQty < minStockQty` — usado para painel de reposição.

---

### Decisão 5 — Debounce de notificação de estoque baixo

Após decrementar o estoque, `AddWorkOrderPartUseCase` verifica debounce de 24h antes de disparar a notificação de estoque baixo. A query de debounce é feita diretamente no `NotificationRepository` por título exato, sem dependência do campo `metric` (que pertence a `AlertThreshold`, não a `Notification`):

```java
// NotificationRepository.java — adicionar:
boolean existsByTitleAndCreatedAtAfter(String title, LocalDateTime since);
```

```java
// AddWorkOrderPartUseCase.java — após decrementar:
if (sparePart.getStockQty() < sparePart.getMinStockQty()) {
    String title = "Estoque baixo: " + sparePart.getName();
    LocalDateTime since = LocalDateTime.now().minusHours(24);
    if (!notificationRepository.existsByTitleAndCreatedAtAfter(title, since)) {
        notificationService.broadcast(title,
            "Estoque atual: " + sparePart.getStockQty() + " " + sparePart.getUnit(),
            NotificationSeverity.WARNING);
    }
}
```

**Justificativa da abordagem por título**: a entidade `Notification` não tem campo `metric` (`AlertMetric` pertence a `AlertThreshold`). Usar o título como chave de debounce é consistente com o que já é exibido ao usuário e não exige campo extra na entidade. Período de 24h (vs. 60 min dos alertas de threshold de ADR-013 Decisão 3) porque consumo de estoque é menos frequente e o alerta de reposição tem urgência mais baixa.

---

### Decisão 6 — `addedBy` no `WorkOrderPart`

O campo `addedBy` é preenchido pelo **controller** a partir do `Principal` do Spring Security, e passado ao use case via `AddWorkOrderPartRequest` (ou parâmetro separado). O use case não acessa o `Principal` diretamente — segue o padrão do projeto onde o controller extrai o username e passa via DTO ou parâmetro.

```java
// MaintenanceController.java:
@PostMapping("/{id}/parts")
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public ResponseEntity<WorkOrderPartResponse> addPart(
        @PathVariable UUID id,
        @Valid @RequestBody AddWorkOrderPartRequest request,
        Principal principal) {
    return ResponseEntity.status(201)
        .body(addWorkOrderPartUseCase.execute(id, request, principal.getName()));
}
```

```java
// AddWorkOrderPartUseCase.execute(UUID workOrderId, AddWorkOrderPartRequest request, String addedBy)
```

---

### Decisão 7 — Remoção de consumo (`DELETE .../parts/{partId}`)

A restauração de estoque no `DELETE` deve ocorrer no **mesmo `@Transactional`** que remove o `WorkOrderPart`, garantindo consistência: se a remoção da entidade falhar, o estoque não é restaurado, e vice-versa.

```java
// RemoveWorkOrderPartUseCase.java:
@Transactional
public void execute(UUID workOrderId, UUID partId) {
    WorkOrderPart wop = workOrderPartRepository.findById(partId)
        .orElseThrow(() -> new WorkOrderPartNotFoundException(partId));
    if (!wop.getWorkOrder().getId().equals(workOrderId)) {
        throw new WorkOrderPartNotFoundException(partId); // 404 se a OS não bate
    }
    SparePart part = wop.getSparePart();
    part.setStockQty(part.getStockQty() + wop.getQuantity()); // restaura estoque
    sparePartRepository.save(part);
    workOrderPartRepository.delete(wop);
}
```

**Sem validação de estoque máximo** na restauração — o ajuste reverso pode ultrapassar `minStockQty` positivamente (caso normal se o registro foi criado por engano).

---

### Decisão 8 — `UpdateSparePartUseCase` — campos permitidos

`PUT /api/v1/maintenance/spare-parts/{id}` atualiza apenas: `name`, `category`, `unit`, `minStockQty`. O campo `stockQty` é **imutável** via este endpoint — qualquer ajuste de saldo deve usar `PUT /spare-parts/{id}/stock`. O campo `code` também é imutável após criação (index único; alteração exigiria auditoria de todas as referências).

```java
// UpdateSparePartRequest.java (record):
public record UpdateSparePartRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100)           String category,
                               String unit,
    @Min(0)                    Integer minStockQty
) {}
```

---

### Decisão 9 — Frontend

- Rota `/maintenance/spare-parts`: tabela de peças com badge vermelho nas linhas com estoque abaixo do mínimo
- Página de detalhe da OS exibe seção "Peças Utilizadas" com botão "+ Adicionar Peça" (SUPERVISOR+)
- Dialog de consumo: autocomplete de peça por código/nome + campo de quantidade
- Painel "Estoque Crítico" no dashboard de manutenção: lista de peças com `stockQty < minStockQty`

---

### Consequências
✅ Sem estoque negativo — validação no use case antes de decrementar
✅ Integração com NotificationService (ADR-013) para alertas de estoque — sem duplicar lógica de notificação
✅ Restauração de estoque atômica no `DELETE` — sem risco de saldo órfão em caso de falha parcial
✅ Debounce de 24h por título evita flood de notificações de estoque baixo sem adicionar campo extra à entidade `Notification`
⚠️ Ajuste manual de estoque (PUT stock) gera risco de inconsistência — logar via AuditService (US-033)
⚠️ Histórico de movimentações de estoque fora do escopo desta sprint — apenas saldo atual
⚠️ Debounce por título é frágil a renomeação de peças — se `SparePart.name` mudar, notificações antigas não serão mais encontradas pelo debounce; aceitável pois renomear peças é operação rara (ADMIN)
