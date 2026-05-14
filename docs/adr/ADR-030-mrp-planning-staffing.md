## ADR-030: MRP Planning Engine, Staffing & Family Planning Board
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-085, US-086, US-087

### Contexto

Com os dados de produção importados do Dynamics (ADR-028), o Hub precisa de:
1. **Motor MRP**: calcular necessidade líquida de produção por produto e sugerir novas OPs planejadas com base em estoque atual, OPs abertas e lead times.
2. **Cálculo de pessoal por OP**: dado o tempo de ciclo importado, calcular quantas pessoas são necessárias para cumprir a OP dentro do prazo. O SUPERVISOR pode editar manualmente o valor calculado.
3. **Board de planejamento por família**: visão consolidada de planejamento — estoque, OPs abertas, necessidade líquida e plano de pessoal — por família de produto.

---

### Decisão 1 — Entidade `MrpPlannedOrder` (sugestão do Hub, não existe no Dynamics)

```java
@Entity
@Table(name = "mrp_planned_order", indexes = {
    @Index(name = "idx_mrp_product",   columnList = "product_id"),
    @Index(name = "idx_mrp_family",    columnList = "family_id"),
    @Index(name = "idx_mrp_status",    columnList = "status"),
    @Index(name = "idx_mrp_run",       columnList = "mrp_run_id")
})
public class MrpPlannedOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private ProductFamily family;

    @Column(nullable = false)
    private Integer suggestedQty;      // qtd sugerida pelo MRP (múltiplo de batchSize)

    private LocalDate suggestedStartDate;
    private LocalDate suggestedDueDate; // today + leadTimeDays

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MrpOrderStatus status;     // SUGGESTED | ACCEPTED | REJECTED | CONVERTED

    private Integer adjustedQty;       // se SUPERVISOR ajustou a quantidade antes de aceitar
    private String rejectionReason;    // preenchido quando REJECTED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mrp_run_id")
    private MrpRun mrpRun;

    private String reviewedBy;
    private LocalDateTime reviewedAt;
}

public enum MrpOrderStatus { SUGGESTED, ACCEPTED, REJECTED, CONVERTED }
// CONVERTED = SUPERVISOR aceitou e marcou que já criou no Dynamics
```

---

### Decisão 2 — Entidade `MrpRun` (histórico de execuções)

```java
@Entity
@Table(name = "mrp_run")
public class MrpRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDateTime runAt;
    private String runBy;
    private boolean isDryRun;         // true = simulação, sem persistir sugestões

    // snapshots usados nessa execução (para auditoria)
    private LocalDate stockSnapshotDate;
    private LocalDate ordersSnapshotDate;

    private Integer productsAnalyzed;
    private Integer suggestionsGenerated;
    private Integer alreadyOk;        // produtos com estoque OK, sem necessidade

    @Column(columnDefinition = "TEXT")
    private String purchaseNeedsJson; // raw materials a comprar (JSON)
    @Column(columnDefinition = "TEXT")
    private String messagesJson;      // alertas e observações do run (JSON)
}
```

---

### Decisão 3 — Algoritmo MRP (Net Change, 2 níveis)

```
Para cada Product FINISHED ativo:
  stockCurrent  = StockSnapshot mais recente (qty)
  openOrdersQty = soma de plannedQty das ProductionOrders com status IN [PLANNED, RELEASED, IN_PROGRESS]
                  + MrpPlannedOrders com status IN [SUGGESTED, ACCEPTED]
  netNeed       = max(0, minStockQty - stockCurrent - openOrdersQty)

  se netNeed > 0:
    suggestedQty  = ceil(netNeed / batchSize) * batchSize
    dueDate       = today + leadTimeDays
    startDate     = dueDate - leadTimeDays (mesmo valor no MVP — sem calendário de turnos)
    → criar MrpPlannedOrder(status=SUGGESTED)

  Para cada componente INTERMEDIATE no BOM do produto (se BOM importado):
    componentNeed = suggestedQty * bomItem.quantity
    componentNet  = max(0, componentNeed - componentStock - componentOpenOrders)
    se componentNet > 0:
      → criar MrpPlannedOrder para o intermediário
      → adicionar ao purchaseNeeds os RAW_MATERIAL envolvidos

RAW_MATERIAL não gera OP — apenas entra em purchaseNeeds para o relatório de compras.
```

**`dry-run`**: executa o algoritmo, retorna `MrpRunResult` mas não persiste nenhum `MrpPlannedOrder`.

---

### Decisão 4 — Cálculo de Staffing por OP (tempo de ciclo → pessoas)

Para cada OP importada do Dynamics (ou `MrpPlannedOrder` aceita), o Hub calcula:

```
cycleTime      = CycleTime mais recente para o produto (segundos/unidade)
totalSeconds   = plannedQty * cycleTime
workdaySeconds = shiftHours * 3600           (padrão: 8h = 28.800s; configurável)
workdays       = max(1, businessDays(today, dueDate))   // dias úteis até o prazo
peopleNeeded   = ceil(totalSeconds / (workdaySeconds * workdays))
```

Persiste em `ProductionOrder.plannedPeople`. Se `peopleOverridden = false`, recalcula automaticamente em cada importação de OPs. Se `true`, preserva o valor editado pelo SUPERVISOR.

**Edição pelo SUPERVISOR**: `PUT /api/v1/production/production-orders/{id}/staffing` aceita `{ "plannedPeople": N }` — seta `plannedPeople = N` e `peopleOverridden = true` (SUPERVISOR+).

**Reset**: `DELETE /api/v1/production/production-orders/{id}/staffing` recalcula o valor a partir do tempo de ciclo (SUPERVISOR+).

---

### Decisão 5 — Entidade auxiliar `StaffingConfig`

```java
@Entity
@Table(name = "staffing_config")
public class StaffingConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer shiftHours = 8;      // horas de trabalho por turno (padrão)

    @Column(nullable = false)
    private Integer shiftsPerDay = 1;    // turnos por dia útil (1 ou 2 ou 3)

    private LocalDateTime updatedAt;
    private String updatedBy;
}
```

Registro único (singleton). `effectiveWorkdaySeconds = shiftHours * shiftsPerDay * 3600`. Editável via `PUT /api/v1/production/staffing-config` (ADMIN).

---

### Decisão 6 — Board de Planejamento por Família

`GET /api/v1/production/planning/families` — resposta consolidada para o board:

```java
public record FamilyPlanningBoard(
    UUID familyId,
    String familyCode,
    String familyName,
    List<ProductPlanningRow> products
) {}

public record ProductPlanningRow(
    String productCode,
    String productName,
    ProductType type,
    // estoque
    Integer currentStock,
    Integer minStockQty,
    LocalDate stockSnapshotDate,
    // ordens abertas
    Integer openOrdersQty,        // OPs abertas no Dynamics
    Integer suggestedOrdersQty,   // MrpPlannedOrders SUGGESTED
    // necessidade
    Integer netNeed,              // max(0, minStockQty - stock - openOrders - suggested)
    PlanningStatus planningStatus, // OK | ALERT | CRITICAL
    // staffing
    Integer totalPlannedPeople,   // soma de plannedPeople das OPs abertas do produto
    Integer totalOpsOpen,
    // lead time
    Integer leadTimeDays,
    LocalDate earliestDueDate     // dueDate da OP aberta mais próxima do vencimento
) {}

public enum PlanningStatus { OK, ALERT, CRITICAL }
```

---

### Decisão 7 — Timeline de OPs por família (visão Gantt simplificada)

`GET /api/v1/production/planning/timeline?familyCode=SER&weeks=8`

Retorna OPs abertas da família (do Dynamics) + MrpPlannedOrders SUGGESTED/ACCEPTED, ordenadas por data:

```java
public record TimelineEntry(
    String orderNumber,       // dynamicsOrderNumber ou "MRP-{uuid.substring(0,8)}" para sugestões
    String productCode,
    LocalDate startDate,
    LocalDate dueDate,
    Integer qty,
    String statusLabel,       // "Em Produção", "Planejado (MRP)", etc.
    boolean isMrpSuggestion,
    boolean overdue
) {}
```

Frontend renderiza barras horizontais por semana (CSS Grid, sem biblioteca de Gantt).

---

### Decisão 8 — Endpoints

**MRP:**
| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/production/mrp/dry-run | SUPERVISOR+ | simula MRP sem persistir |
| POST | /api/v1/production/mrp/run | SUPERVISOR+ | executa MRP e gera sugestões |
| GET | /api/v1/production/mrp/runs | SUPERVISOR+ | histórico de execuções |
| GET | /api/v1/production/mrp/suggested-orders | SUPERVISOR+ | lista sugestões pendentes |
| PUT | /api/v1/production/mrp/suggested-orders/{id}/accept | SUPERVISOR+ | aceita sugestão (com ajuste opcional de qty) |
| PUT | /api/v1/production/mrp/suggested-orders/{id}/reject | SUPERVISOR+ | rejeita sugestão |

**Staffing:**
| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| PUT | /api/v1/production/production-orders/{id}/staffing | SUPERVISOR+ | editar pessoas manualmente |
| DELETE | /api/v1/production/production-orders/{id}/staffing | SUPERVISOR+ | resetar para cálculo automático |
| GET | /api/v1/production/staffing-config | OPERATOR+ | configuração de turno |
| PUT | /api/v1/production/staffing-config | ADMIN | atualizar horas/turno e turnos/dia |

**Planning Board:**
| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/production/planning/families | SUPERVISOR+ | board completo por família |
| GET | /api/v1/production/planning/timeline | SUPERVISOR+ | timeline de OPs por família |
| GET | /api/v1/production/planning/purchase-needs | SUPERVISOR+ | matérias-primas a comprar (do último run) |

---

### Decisão 9 — Frontend: telas de planejamento

**Rota `/production/planning`** — board de planejamento:
- Cards por família com tabela de produtos: estoque, OPs abertas, necessidade líquida, status (chip OK/ALERT/CRITICAL)
- Coluna "Pessoas": soma de `plannedPeople` das OPs abertas + ícone de lápis para SUPERVISOR editar por OP
- Botão "Executar MRP" → chama dry-run → exibe modal de revisão das sugestões → confirmar → run
- Painel "Sugestões MRP" com lista de ordens sugeridas: produto, qtd, prazo; botões aceitar/ajustar/rejeitar

**Rota `/production/planning/timeline?family=X`** — Gantt simplificado:
- Grade de semanas (colunas) × ordens (linhas)
- Barras coloridas: azul=Dynamics, laranja=sugestão MRP
- Linhas em vermelho se `overdue = true`

**Edição de staffing inline**: clique no número de pessoas em qualquer OP abre input inline com botões salvar/cancelar (SUPERVISOR+); ícone de "calculadora" ao lado reseta para automático.

---

### Consequências
✅ `dry-run` antes do `run` — planejador revisa sugestões antes de confirmar
✅ `peopleOverridden` preserva decisão do SUPERVISOR mesmo após reimportação de OPs
✅ `MrpPlannedOrder.status = CONVERTED` — rastreia que a OP foi criada no Dynamics sem link direto
✅ Board calculado em tempo real — sem snapshot (volumes de produto pequenos para 53 usuários)
⚠️ Sem calendário de dias úteis — `businessDays()` usa dias corridos menos fins de semana; feriados são out-of-scope (usar `leadTimeDays` com folga manual)
⚠️ MRP sem restrição de capacidade — gera sugestões sem verificar horas disponíveis; integração com OEE para constraint de capacidade é sprint futura
⚠️ `purchaseNeedsJson` em `MrpRun` é JSON bruto — não normalizado; aceitável para exibição de relatório, não para queries
