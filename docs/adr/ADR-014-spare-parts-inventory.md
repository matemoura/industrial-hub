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

### Decisão 5 — Frontend

- Rota `/maintenance/spare-parts`: tabela de peças com badge vermelho nas linhas com estoque abaixo do mínimo
- Página de detalhe da OS exibe seção "Peças Utilizadas" com botão "+ Adicionar Peça" (SUPERVISOR+)
- Dialog de consumo: autocomplete de peça por código/nome + campo de quantidade
- Painel "Estoque Crítico" no dashboard de manutenção: lista de peças com `stockQty < minStockQty`

---

### Consequências
✅ Sem estoque negativo — validação no use case antes de decrementar
✅ Integração com NotificationService (ADR-013) para alertas de estoque — sem duplicar lógica de notificação
⚠️ Ajuste manual de estoque (PUT stock) gera risco de inconsistência — logar via AuditService (US-033)
⚠️ Histórico de movimentações de estoque fora do escopo desta sprint — apenas saldo atual
