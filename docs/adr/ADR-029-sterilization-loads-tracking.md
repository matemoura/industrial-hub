## ADR-029: Sterilization Loads & Visual Production Tracking — Cargas de Esterilização e Acompanhamento Visual
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-082, US-083, US-084

### Contexto

As ordens de produção importadas do Dynamics (ADR-028) precisam de dois tratamentos no Hub:

1. **Cargas de esterilização**: OPs de produtos FINISHED que `requiresSterilization = true` precisam ser agrupadas em **cargas** antes de ir para o esterilizador. Esse agrupamento **é gerenciado no Hub** (não existe no Dynamics da forma que o MSB precisa para o rastreamento).

2. **Acompanhamento visual**: OPs de produtos INTERMEDIATE e FINISHED precisam de uma visão de acompanhamento — o que está em produção, o que está aguardando esterilização, o que foi concluído — com rastreabilidade de onde cada OP está no fluxo.

---

### Decisão 1 — Entidade `SterilizationLoad` (gerenciada pelo Hub)

```java
@Entity
@Table(name = "sterilization_load", indexes = {
    @Index(name = "idx_load_status",     columnList = "status"),
    @Index(name = "idx_load_sterilizer", columnList = "sterilizer_id"),
    @Index(name = "idx_load_date",       columnList = "sterilization_date")
})
public class SterilizationLoad {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String loadNumber;   // "CARGA-2026-001" — gerado pelo Hub sequencialmente

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoadStatus status;   // OPEN | CLOSED | STERILIZING | RELEASED | REJECTED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sterilizer_id")
    private Equipment sterilizer;    // equipamento (esterilizador) do módulo Maintenance — nullable

    @Enumerated(EnumType.STRING)
    private SterilizationMethod method;  // EO_GAS | GAMMA | STEAM | OTHER

    private LocalDate sterilizationDate; // data planejada/executada de esterilização
    private LocalDate releaseDate;       // data de liberação da quarentena
    private String batchCode;            // código do lote de esterilização (para rastreabilidade ANVISA)

    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private LocalDateTime releasedAt;
}

public enum LoadStatus   { OPEN, CLOSED, STERILIZING, RELEASED, REJECTED }
public enum SterilizationMethod { EO_GAS, GAMMA, STEAM, OTHER }
```

---

### Decisão 2 — Fluxo completo: OP → Carga → Liberação

```
[Dynamics]                           [Hub overlay]
  OP status = IN_PROGRESS
       ↓
  OP status = DONE (importado)  →  Hub identifica: product.requiresSterilization = true
                                         ↓
                                  OP aguarda alocação em carga
                                  (exibida em fila "Aguardando Carga")
                                         ↓
                             SUPERVISOR cria/abre SterilizationLoad
                                         ↓
                             SUPERVISOR adiciona OP à carga (Hub)
                                         ↓
                             SUPERVISOR fecha a carga → CLOSED
                                         ↓
                             SUPERVISOR inicia esterilização → STERILIZING
                             (Equipment.status → UNDER_MAINTENANCE se configurado)
                                         ↓
                             SUPERVISOR libera → RELEASED
                             (Equipment.status → OPERATIONAL)
                                   OU
                             SUPERVISOR rejeita → REJECTED
                             (OPs voltam à fila "Aguardando Carga")
```

**OPs de intermediários** (INTERMEDIATE, `requiresSterilization = false`):
- Não entram em carga de esterilização
- São acompanhadas visualmente pelo status importado do Dynamics
- Hub exibe: PLANNED → RELEASED → IN_PROGRESS → DONE (refletindo Dynamics)

---

### Decisão 3 — Status "agregado" de OP no Hub

Como o status base vem do Dynamics e o Hub adiciona um overlay de esterilização, a lógica de exibição é:

```java
// Determinado em Java no use case de tracking — não persistido
public enum ProductionOrderDisplayStatus {
    PLANNED,               // Dynamics: PLANNED
    RELEASED,              // Dynamics: RELEASED
    IN_PROGRESS,           // Dynamics: IN_PROGRESS
    PENDING_STERILIZATION, // Dynamics: DONE + requiresSterilization + sem carga
    IN_LOAD,               // Dynamics: DONE + requiresSterilization + carga OPEN/CLOSED
    STERILIZING,           // Dynamics: DONE + carga STERILIZING
    DONE                   // Dynamics: DONE + (sem esterilização OU carga RELEASED)
}
```

Esse display status é calculado em `GetProductionTrackingUseCase` e retornado no DTO — nunca persiste no banco.

---

### Decisão 4 — Endpoints de cargas de esterilização

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/production/sterilization-loads | SUPERVISOR+ | criar carga |
| GET | /api/v1/production/sterilization-loads | OPERATOR+ | listar cargas (filtro: status, date) |
| GET | /api/v1/production/sterilization-loads/{id} | OPERATOR+ | detalhe + OPs da carga |
| POST | /api/v1/production/sterilization-loads/{id}/orders | SUPERVISOR+ | adicionar OP à carga |
| DELETE | /api/v1/production/sterilization-loads/{id}/orders/{opId} | SUPERVISOR+ | remover OP (só OPEN) |
| PUT | /api/v1/production/sterilization-loads/{id}/status | SUPERVISOR+ | transição de status |

**Regras de validação ao adicionar OP:**
- OP deve ter `status = DONE` no Dynamics (importado)
- `product.requiresSterilization = true`
- OP não pode já estar em outra carga com status ≠ REJECTED

---

### Decisão 5 — Acompanhamento visual de OPs por família

`GET /api/v1/production/tracking/families` retorna visão de todas as famílias com suas OPs agrupadas por display status:

```java
public record FamilyTrackingResponse(
    UUID familyId,
    String familyCode,
    String familyName,
    List<OrderTrackingEntry> orders  // todas as OPs não-DONE/CANCELLED da família
) {}

public record OrderTrackingEntry(
    String dynamicsOrderNumber,
    String productCode,
    String productName,
    ProductType productType,
    Integer plannedQty,
    Integer producedQty,
    Double completionPct,          // producedQty / plannedQty * 100
    LocalDate dueDate,
    boolean overdue,               // dueDate < today && displayStatus != DONE
    ProductionOrderDisplayStatus displayStatus,
    String loadNumber,             // null se não alocada
    LoadStatus loadStatus,         // null se não alocada
    Integer plannedPeople          // do staffing (ADR-030)
) {}
```

---

### Decisão 6 — Fila "Aguardando Carga"

`GET /api/v1/production/sterilization-loads/pending-orders` retorna OPs com:
- `status = DONE` no Dynamics
- `product.requiresSterilization = true`
- `sterilizationLoad = null`

Ordenadas por `dueDate ASC` (mais urgente primeiro). Exibidas em painel lateral no frontend para o SUPERVISOR alocar às cargas abertas.

---

### Decisão 7 — Frontend: tela de acompanhamento

**Rota `/production/tracking`**: visão principal de acompanhamento

Layout em abas ou seções por família:
- **Cabeçalho da família**: nome, total de OPs abertas, OPs atrasadas (badge vermelho)
- **Cards de OP** (kanban-style por display status): cada card mostra número da OP, produto, progresso (barra), data prevista, chip de status colorido
  - Cores: PLANNED=cinza, RELEASED=azul-claro, IN_PROGRESS=azul, PENDING_STERILIZATION=laranja, IN_LOAD=âmbar, STERILIZING=roxo, DONE=verde
- OPs atrasadas têm borda vermelha
- Filtro por família + filtro por status + filtro de data

**Rota `/production/sterilization-loads`**: gestão de cargas
- Cards de carga agrupados por status
- Carga OPEN mostra lista de OPs + botão "+ Adicionar OP"
- Painel lateral "Aguardando Carga" com OPs disponíveis para alocar (drag-and-drop para adicionar à carga aberta)

**Rota `/production/sterilization-loads/{id}`**: detalhe da carga
- Lista de OPs, quantidades totais, esterilizador, método, datas
- Chip de status da carga + botões de transição (SUPERVISOR+)

---

### Consequências
✅ `ProductionOrderDisplayStatus` calculado em Java — sem campo extra no banco, sem inconsistência
✅ Fila de pending orders é uma query simples — sem entidade extra
✅ FK nullable para `Equipment` (esterilizador) — módulo funciona sem Maintenance configurado
✅ `batchCode` para rastreabilidade ANVISA — lote de esterilização rastreável por carga
⚠️ Hub não atualiza o Dynamics quando carga é liberada — o operador deve atualizar o Dynamics separadamente; Hub e Dynamics ficam em sync somente na próxima importação de OPs
⚠️ Drag-and-drop no frontend para alocar OP a carga depende de ADR-027 (HTML5 D&D); se não implementado antes, usar dialog de seleção como fallback
