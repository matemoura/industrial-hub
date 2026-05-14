## ADR-030: MRP Planning Engine & Family Planning Board — Motor MRP e Quadro de Planejamento por Família
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-085, US-086, US-087

### Contexto

Com o catálogo de produtos (ADR-028) e o ciclo de vida de OPs (ADR-029) definidos, esta ADR modela o motor MRP e o quadro de planejamento por família de produto. O motor MRP calcula a necessidade líquida de produção e gera OPs planejadas. O quadro de planejamento é a interface de trabalho diária do planejador.

---

### Decisão 1 — Filosofia do motor MRP: Necessidade Líquida por Produto

O motor MRP do MSB é **make-to-stock** com estoque mínimo como driver de demanda (sem ordens de venda no escopo atual):

```
Necessidade Bruta   = max(0, minStockQty - currentStock)
Ordens em Aberto    = soma de plannedQty de OPs com status IN [PLANNED, RELEASED, IN_PROGRESS, PENDING_STERILIZATION]
Necessidade Líquida = max(0, NecessidadeBruta - OrdensEmAberto)
```

Se `NecessidadeLíquida > 0`: gerar OP planejada com `plannedQty = ceil(NecessidadeLíquida / batchSize) * batchSize` (arredondado para múltiplo do lote padrão) e `dueDate = today + leadTimeDays`.

**Sem cálculo de capacidade** no MVP — OPs são geradas sem verificar horas disponíveis. Capacidade é visível via OEE (tempo de trabalhadores), mas não é constraint no MRP agora.

---

### Decisão 2 — Explosão de BOM para necessidade de componentes

O motor processa dois níveis:

**Nível 1 — Produtos FINISHED:**
1. Calcula necessidade líquida para cada produto acabado ativo
2. Gera OPs planejadas para os que têm `NecessidadeLíquida > 0`

**Nível 2 — Produtos INTERMEDIATE (explosão BOM):**
Para cada OP gerada no nível 1:
1. Lê o BOM do produto acabado
2. Para cada componente `INTERMEDIATE`: calcula quantidade necessária = `plannedQty * bom.quantity`
3. Subtrai estoque disponível e OPs abertas do intermediário
4. Se necessidade > 0: gera OP planejada para o intermediário com `dueDate = OP_FINISHED.dueDate - intermediario.leadTimeDays`

**RAW_MATERIAL**: calculado e reportado como necessidade de compra (não gera OP — é responsabilidade do suprimentos).

---

### Decisão 3 — `MrpRunResult` — resultado da execução

```java
public record MrpRunResult(
    LocalDateTime runAt,
    int ordersCreated,              // OPs FINISHED geradas
    int intermediateOrdersCreated,  // OPs INTERMEDIATE geradas
    List<MaterialNeed> purchaseNeeds, // RAW_MATERIAL a comprar
    List<MrpMessage> messages       // alertas: estoque negativo, lead time no passado, etc.
) {}

public record MaterialNeed(
    String productCode,
    String productName,
    Double quantityNeeded,
    String unit
) {}

public record MrpMessage(
    String severity,   // "WARNING" | "INFO"
    String productCode,
    String message
) {}
```

---

### Decisão 4 — Endpoint de execução do MRP

```
POST /api/v1/mrp/run          → ADMIN; executa o motor MRP e retorna MrpRunResult
POST /api/v1/mrp/dry-run      → ADMIN; simula a execução sem persistir OPs (retorna MrpRunResult com ordersCreated mas sem salvar)
GET  /api/v1/mrp/runs         → SUPERVISOR+; histórico de execuções com timestamps e contagens
```

`dry-run` permite o planejador revisar o que seria gerado antes de confirmar. `run` persiste as OPs com `origin = MRP_PLANNED`.

---

### Decisão 5 — Quadro de Planejamento por Família (`/mrp/planning`)

O quadro é a tela central do planejador. Para cada família de produto, exibe um painel com:

```
FAMÍLIA: Seringas
├── [SER-5ML]  Seringa 5mL           estoque: 8.500  mínimo: 5.000  OP aberta: 3.000  status: OK
├── [SER-10ML] Seringa 10mL          estoque: 1.200  mínimo: 3.000  OP aberta: 0      status: CRITICO ⚠
└── [SER-20ML] Seringa 20mL          estoque: 4.800  mínimo: 4.000  OP aberta: 2.000  status: ALERTA
```

**Status por produto no quadro:**
- `OK` → `currentStock + openOrderQty >= minStockQty`
- `ALERTA` → `currentStock < minStockQty` mas `currentStock + openOrderQty >= minStockQty`
- `CRITICO` → `currentStock + openOrderQty < minStockQty`

---

### Decisão 6 — Endpoint do Quadro de Planejamento

```
GET /api/v1/mrp/planning/families         → SUPERVISOR+
```

Retorna `List<FamilyPlanningResponse>`:

```java
public record FamilyPlanningResponse(
    UUID familyId,
    String familyName,
    List<ProductPlanningEntry> products
) {}

public record ProductPlanningEntry(
    UUID productId,
    String code,
    String name,
    ProductType type,
    int currentStock,
    int minStockQty,
    int openOrderQty,          // soma de plannedQty de OPs abertas
    int pendingSterilizationQty, // OPs em PENDING_STERILIZATION ou STERILIZING
    PlanningStatus planningStatus, // OK | ALERT | CRITICAL
    LocalDate nextDueDate,     // dueDate da próxima OP aberta (null se nenhuma)
    int leadTimeDays
) {}

public enum PlanningStatus { OK, ALERT, CRITICAL }
```

Calculado em Java a partir dos repositórios — sem tabela de materialização.

---

### Decisão 7 — Timeline de OPs por família (Gantt simplificado)

```
GET /api/v1/mrp/planning/timeline?familyId=<uuid>&weeks=8  → SUPERVISOR+
```

Retorna OPs abertas da família ordenadas por `dueDate`, com `startDate` e `dueDate` para renderizar barras de Gantt simples no frontend (sem biblioteca — CSS Grid por semana).

---

### Decisão 8 — Integração com OEE (leitura de capacidade)

`GET /api/v1/mrp/planning/capacity?weeks=4` retorna capacidade disponível por semana (baseada no OEE):

```java
public record WeeklyCapacityResponse(
    String weekLabel,            // "2026-W22"
    Double availableHours,       // soma de availableTimeMinutes / 60 dos ImportBatch da semana
    Double oeePercent            // OEE médio da semana
) {}
```

Exibido como card informativo no quadro de planejamento — o planejador usa visualmente para entender se há capacidade para as OPs planejadas, mas não há cálculo automático de constraint de capacidade.

---

### Consequências
✅ Motor MRP net-change em Java puro — sem stored procedures, sem framework MRP externo
✅ `dry-run` antes do `run` — planejador não é surpreendido por OPs inesperadas
✅ Quadro de planejamento calculado em tempo real — sem tabela snapshot (volumes de produto são pequenos)
✅ Integração com OEE é leitura não-invasiva — OEE module não é alterado
⚠️ MRP sem capacidade finita — gera OPs mesmo sem horas disponíveis; planejador ajusta manualmente. Constraint de capacidade é Sprint futura
⚠️ Explosão de BOM de 2 níveis em Java — para BOMs com 5+ componentes intermediários o loop pode ser lento; limitar a 3 níveis de profundidade e logar warning se ultrapassar
⚠️ `openOrderQty` no quadro inclui OPs PLANNED+RELEASED+IN_PROGRESS+PENDING_STERILIZATION — certificar que a query não ignora nenhum status relevante
