## ADR-029: Production Orders & Sterilization Loads — Ordens de Produção e Cargas de Esterilização
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-082, US-083, US-084

### Contexto

O fluxo de produção do MSB tem dois tipos principais de ordem:
1. **Ordens de produtos intermediários**: fabricam componentes (cilindros, êmbolos, agulhas nuas) que abastecem outras ordens. Não requerem esterilização.
2. **Ordens de produtos acabados**: fabricam o produto final (ex: seringa montada) que **deve ser esterilizado antes de ser liberado ao estoque**. Produtos acabados são agrupados em **cargas de esterilização** (`SterilizationLoad`) para processamento em lote no esterilizador.

Esta ADR modela o ciclo de vida das ordens de produção (OP) e a gestão de cargas de esterilização, incluindo a integração com o módulo de Maintenance (`Equipment` como esterilizador).

---

### Decisão 1 — Entidade `ProductionOrder`

```java
@Entity
@Table(name = "production_order", indexes = {
    @Index(name = "idx_po_product",    columnList = "product_id"),
    @Index(name = "idx_po_status",     columnList = "status"),
    @Index(name = "idx_po_due_date",   columnList = "due_date"),
    @Index(name = "idx_po_family",     columnList = "family_id"),
    @Index(name = "idx_po_load",       columnList = "sterilization_load_id")
})
public class ProductionOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String orderNumber;    // gerado sequencialmente: "OP-2026-00001"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private ProductFamily family;  // desnormalizado para facilitar filtros de planejamento

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionOrderStatus status;

    @Column(nullable = false)
    private Integer plannedQty;    // quantidade planejada

    private Integer producedQty;   // quantidade efetivamente produzida (preenchida ao concluir)

    private LocalDate dueDate;     // data prevista de conclusão
    private LocalDate startDate;   // data prevista de início (dueDate - leadTimeDays)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sterilization_load_id")
    private SterilizationLoad sterilizationLoad; // null para intermediários e OPs ainda não alocadas

    // rastreabilidade
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;   // quando passou para IN_PROGRESS
    private LocalDateTime completedAt;  // quando passou para DONE

    // origem da OP
    @Enumerated(EnumType.STRING)
    private OrderOrigin origin;   // MANUAL | MRP_PLANNED
}

public enum ProductionOrderStatus {
    PLANNED,         // gerada pelo MRP ou manualmente — aguarda liberação
    RELEASED,        // liberada para produção — materiais reservados
    IN_PROGRESS,     // produção iniciada
    PENDING_STERILIZATION,  // produção concluída, aguarda alocação em carga (apenas FINISHED)
    STERILIZING,     // alocada em carga e carga em processo de esterilização
    DONE,            // concluída e estoque atualizado
    CANCELLED        // cancelada
}

public enum OrderOrigin { MANUAL, MRP_PLANNED }
```

**Número sequencial `orderNumber`**: gerado via `SELECT MAX(order_number) + 1` ou sequence no banco — formato `OP-{ANO}-{NNNNN}` (ex: `OP-2026-00001`). Usar sequence nativa do PostgreSQL para evitar race condition: `CREATE SEQUENCE production_order_seq START 1`.

---

### Decisão 2 — Máquina de estados da OP

```
PLANNED ──► RELEASED ──► IN_PROGRESS ──► [PENDING_STERILIZATION] ──► (via carga) ──► DONE
    │            │              │                                                        ▲
    └────────────┴──────────────┴──► CANCELLED                                         │
                                                                    INTERMEDIATE: DONE direto
```

| Transição | Quem | Efeito colateral |
|-----------|------|-----------------|
| PLANNED → RELEASED | SUPERVISOR+ | Verifica disponibilidade de estoque de componentes (não bloqueia, apenas alerta) |
| RELEASED → IN_PROGRESS | SUPERVISOR+ | `releasedAt = now()` |
| IN_PROGRESS → PENDING_STERILIZATION | SUPERVISOR+ | Apenas para `product.requiresSterilization = true`; preenche `producedQty` |
| IN_PROGRESS → DONE | SUPERVISOR+ | Apenas para `product.requiresSterilization = false` (intermediários); `completedAt = now()`; incrementa `StockLevel` |
| PENDING_STERILIZATION → STERILIZING | automático | Quando `SterilizationLoad` é fechada (a carga assume o controle) |
| STERILIZING → DONE | automático | Quando `SterilizationLoad` é liberada; incrementa `StockLevel` |
| qualquer → CANCELLED | SUPERVISOR+ | `completedAt = now()`; não afeta estoque |

---

### Decisão 3 — Entidade `SterilizationLoad` (Carga de Esterilização)

```java
@Entity
@Table(name = "sterilization_load", indexes = {
    @Index(name = "idx_load_status",      columnList = "status"),
    @Index(name = "idx_load_sterilizer",  columnList = "sterilizer_id"),
    @Index(name = "idx_load_date",        columnList = "sterilization_date")
})
public class SterilizationLoad {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String loadNumber;   // "CARGA-2026-00001"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sterilizer_id")
    private Equipment sterilizer;   // FK para Equipment (módulo Maintenance); nullable (pode não haver esterilizador cadastrado)

    @Enumerated(EnumType.STRING)
    private SterilizationMethod method;  // EO_GAS | GAMMA | STEAM | OTHER

    private LocalDate sterilizationDate;  // data planejada/executada de esterilização
    private LocalDate releaseDate;        // data de liberação (após quarentena/validação)

    private String notes;    // observações de processo (ex: parâmetros do ciclo)
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private LocalDateTime releasedAt;
}

public enum LoadStatus {
    OPEN,           // aceitando OPs
    CLOSED,         // carga completa — enviada para esterilização; OPs viram STERILIZING
    STERILIZING,    // em processo de esterilização
    RELEASED,       // esterilização concluída e validada; OPs viram DONE + estoque atualizado
    REJECTED        // falha no processo; OPs voltam para PENDING_STERILIZATION para realocação
}

public enum SterilizationMethod { EO_GAS, GAMMA, STEAM, OTHER }
```

---

### Decisão 4 — Fluxo de carga de esterilização

```
1. SUPERVISOR cria SterilizationLoad (status = OPEN)
2. SUPERVISOR adiciona OPs com status PENDING_STERILIZATION à carga
3. SUPERVISOR fecha a carga (OPEN → CLOSED):
   - Todas as OPs da carga: PENDING_STERILIZATION → STERILIZING
4. Carga inicia esterilização (CLOSED → STERILIZING):
   - Registra sterilizationDate
5. Carga é liberada (STERILIZING → RELEASED):
   - Todas as OPs da carga: STERILIZING → DONE
   - Para cada OP DONE: StockLevel.currentQty += OP.producedQty
   - releaseDate registrado
6. Se REJECTED (STERILIZING → REJECTED):
   - Todas as OPs da carga: STERILIZING → PENDING_STERILIZATION (aguardam nova carga)
```

**Invariante**: OP só pode estar em uma carga por vez. Tentativa de adicionar OP já alocada retorna `409`.

---

### Decisão 5 — Integração com Maintenance (Equipment como esterilizador)

`SterilizationLoad.sterilizer` é FK **nullable** para `Equipment`. Esterilizadores são cadastrados com `EquipmentType = INFRASTRUCTURE` no módulo de manutenção. A FK é nullable para não bloquear o uso do módulo MRP caso o esterilizador não esteja cadastrado.

Quando `sterilizer` preenchido: `Equipment.status` é mudado para `UNDER_MAINTENANCE` automaticamente quando a carga entra em `STERILIZING` e retorna para `OPERATIONAL` quando `RELEASED` ou `REJECTED` — dentro do mesmo `@Transactional`.

---

### Decisão 6 — Endpoints

**Ordens de Produção:**
| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/mrp/production-orders | SUPERVISOR+ | criar OP manual |
| GET | /api/v1/mrp/production-orders | OPERATOR+ | listar (filtros: status, family, product, dueDate) |
| GET | /api/v1/mrp/production-orders/{id} | OPERATOR+ | detalhe |
| PUT | /api/v1/mrp/production-orders/{id}/status | SUPERVISOR+ | transição de status |
| PUT | /api/v1/mrp/production-orders/{id} | SUPERVISOR+ | atualizar dueDate e plannedQty (apenas PLANNED) |

**Cargas de Esterilização:**
| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/mrp/sterilization-loads | SUPERVISOR+ | criar carga |
| GET | /api/v1/mrp/sterilization-loads | OPERATOR+ | listar cargas |
| GET | /api/v1/mrp/sterilization-loads/{id} | OPERATOR+ | detalhe + OPs da carga |
| POST | /api/v1/mrp/sterilization-loads/{id}/orders | SUPERVISOR+ | adicionar OP à carga |
| DELETE | /api/v1/mrp/sterilization-loads/{id}/orders/{opId} | SUPERVISOR+ | remover OP (só quando OPEN) |
| PUT | /api/v1/mrp/sterilization-loads/{id}/status | SUPERVISOR+ | transição: OPEN→CLOSED→STERILIZING→RELEASED/REJECTED |

---

### Consequências
✅ Separação limpa: OP intermediária fecha direto em DONE; OP de produto final passa por PENDING_STERILIZATION → carga
✅ FK nullable para `Equipment` (esterilizador) — módulo MRP funciona mesmo sem Maintenance configurado
✅ REJECTED na carga — OPs voltam para PENDING_STERILIZATION para realocação em nova carga (processo real de reprocesso)
✅ Incremento de estoque apenas quando carga é RELEASED — garante que nenhum produto não esterilizado entra no estoque
⚠️ Sequence nativa do PostgreSQL para `orderNumber` — em H2 (testes) usar `@GeneratedValue` com `GenerationType.SEQUENCE` e dialect compatível
⚠️ Race condition em `StockLevel` ao liberar carga com muitas OPs — usar `@Lock(LockModeType.PESSIMISTIC_WRITE)` ao incrementar estoque no `ReleaseLoadUseCase`
⚠️ Carga REJECTED: definir política de quantas vezes pode reprocessar (out-of-scope agora — campo `reprocessCount` pode ser adicionado futuramente)
