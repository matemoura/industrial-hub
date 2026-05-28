## ADR-041: Production Kanban Tracking — Acompanhamento Visual de Ordens de Produção
**Status**: Aprovado
**Data**: 2026-05-28
**US relacionadas**: US-082, US-083

---

### Contexto

Com as Ordens de Produção (OPs) importadas do Dynamics (ADR-028) e o modelo de status agregado definido em ADR-029 (`ProductionOrderDisplayStatus`), o Sprint 30 entrega a camada de **visualização interativa** dessas OPs.

O MSB precisa acompanhar em tempo real onde cada OP está no fluxo de produção — do planejamento à esterilização — sem criar ou modificar dados do Dynamics. O Hub é somente leitura para dados de Dynamics; o SUPERVISOR pode apenas transitar status de carga de esterilização (Hub-managed).

Decisões a tomar neste ADR:
1. Quais endpoints o backend expõe para o tracking (e com quais filtros)
2. Como o kanban organiza as colunas (`ProductionOrderDisplayStatus`)
3. Transição de status: quais mutações são permitidas e quem pode fazê-las
4. Paginação/limite por coluna de kanban
5. Atualização de dados: polling vs. SSE vs. WebSocket (decisão para Sprint 30)
6. Tech debt diferido do Sprint 29 (SEC-107 a SEC-111) — ordem de implementação

---

### Decisão 1 — Endpoints de tracking e contrato de API

Os endpoints de tracking (`/api/v1/production/tracking/**`) já foram arquitetados no ADR-029 (Decisão 5). Este ADR detalha os filtros e paginação.

**`GET /api/v1/production/tracking/families`** — retorna `List<FamilyTrackingResponse>` (OPERATOR+)

Filtros via `@RequestParam`:
- `familyCode` (String, opcional) — filtrar por família específica
- `displayStatus` (enum `ProductionOrderDisplayStatus`, opcional) — filtrar por status de exibição
- `overdue` (boolean, opcional) — `true` = somente OPs com `dueDate < today` e `displayStatus != DONE`
- `productType` (enum `ProductType`, opcional) — `FINISHED` / `INTERMEDIATE`

```java
@GetMapping("/tracking/families")
public List<FamilyTrackingResponse> getFamilyTracking(
    @RequestParam(required = false) String familyCode,
    @RequestParam(required = false) ProductionOrderDisplayStatus displayStatus,
    @RequestParam(required = false) Boolean overdue,
    @RequestParam(required = false) ProductType productType
) { ... }
```

**`GET /api/v1/production/tracking/orders`** — retorna `Page<OrderTrackingEntry>` (OPERATOR+)

Filtros: `familyCode`, `displayStatus`, `overdue`, `productType`; paginação: `@PageableDefault(size = 20)`.

**`GET /api/v1/production/tracking/summary`** — retorna `TrackingSummaryResponse` (OPERATOR+)

```java
public record TrackingSummaryResponse(
    long inProgress,
    long pendingSterilization,
    long sterilizing,
    long overdue,
    long doneThisWeek,
    LocalDateTime lastSyncAt  // timestamp do ImportProductionBatch mais recente
) {}
```

`lastSyncAt` é obtido via `ImportProductionBatchRepository.findTopByTypeOrderByImportedAtDesc(PRODUCTION_ORDERS)`.

---

### Decisão 2 — Colunas do kanban e mapeamento de `ProductionOrderDisplayStatus`

O frontend renderiza um kanban com **7 colunas fixas**, na ordem do fluxo:

| Coluna | `ProductionOrderDisplayStatus` | Cor (ADR-029 Decisão 7) |
|--------|-------------------------------|------------------------|
| Planejado | `PLANNED` | cinza |
| Liberado | `RELEASED` | azul-claro |
| Em Produção | `IN_PROGRESS` | azul |
| Ag. Esterilização | `PENDING_STERILIZATION` | laranja |
| Na Carga | `IN_LOAD` | âmbar |
| Esterilizando | `STERILIZING` | roxo |
| Concluído | `DONE` | verde |

`DONE` exibe apenas OPs concluídas **na semana atual** (`dueDate >= monday` ou `releasedAt >= monday`), para evitar acúmulo visual. OPs CANCELLED são excluídas do kanban (apenas visíveis no histórico de importação).

O `ProductionOrderDisplayStatus` é **calculado em Java** no `GetProductionTrackingUseCase` (ADR-029 Decisão 3) — nunca persiste no banco. A lógica de cálculo **não muda** neste Sprint; este ADR não redefine a máquina de estados de `ProductionOrderDisplayStatus`.

---

### Decisão 3 — Transições de status permitidas no Sprint 30

O Hub **não altera** `ProductionOrder.status` (esse campo é read-only, vem do Dynamics). O único estado mutável que afeta o `displayStatus` de OPs elegíveis à esterilização é a transição de `SterilizationLoad.status` — já definida no ADR-029.

Para Sprint 30, o endpoint `PUT /api/v1/production/sterilization-loads/{id}/status` (SUPERVISOR+) é o único ponto de mutação de estado. Ele não é novo — está especificado no ADR-029 Decisão 4.

**Movimento de "card" no kanban**: não há drag-and-drop entre colunas para OPs. O SUPERVISOR transita o status da **carga**, não da OP individualmente. A OP muda de coluna reflexivamente quando a carga associada muda de status.

```
Ação SUPERVISOR                          Efeito no displayStatus da OP
───────────────────────────────────────  ─────────────────────────────────────
Adicionar OP à carga OPEN               PENDING_STERILIZATION → IN_LOAD
Carga OPEN → CLOSED                     IN_LOAD → IN_LOAD (sem mudança visual)
Carga CLOSED → STERILIZING              IN_LOAD → STERILIZING
Carga STERILIZING → RELEASED            STERILIZING → DONE
Carga STERILIZING → REJECTED            STERILIZING → PENDING_STERILIZATION
```

**Permissões**: OPERATOR pode visualizar todas as colunas; SUPERVISOR+ pode transitar cargas (e indiretamente mover OPs entre colunas).

---

### Decisão 4 — Limite por coluna de kanban (sem paginação por coluna)

**Decisão**: limite fixo de **50 OPs por coluna** no endpoint `GET /api/v1/production/tracking/families`.

**Justificativa**: o MSB tem ~53 usuários internos e operações de médio porte. Volumes realistas por coluna não excedem 30–40 OPs simultaneamente. Paginação por coluna adicionaria complexidade de estado no Angular (scroll infinito ou paginadores por coluna) sem benefício prático.

Se uma coluna exceder 50 OPs, o campo `truncated: true` é retornado no `FamilyTrackingResponse`:

```java
public record FamilyTrackingResponse(
    UUID familyId,
    String familyCode,
    String familyName,
    Map<ProductionOrderDisplayStatus, List<OrderTrackingEntry>> ordersByStatus,
    boolean truncated,        // true se alguma coluna foi limitada a 50
    LocalDateTime lastSyncAt
) {}
```

Para volume maior, o usuário usa `GET /api/v1/production/tracking/orders` com paginação (`Page<T>`, `size=20`).

---

### Decisão 5 — Atualização de dados: polling no frontend (sem SSE/WebSocket no Sprint 30)

**Decisão**: polling simples a cada **60 segundos** via `interval(60_000)` no Angular.

**Alternativas consideradas**:
- **SSE (Server-Sent Events)**: adequado para atualizações push; porém, a fonte de dados são importações manuais do Dynamics — a taxa de mudança real é baixa (importações ocorrem algumas vezes ao dia). SSE exigiria `SseEmitter` no backend e gerenciamento de conexões, com ganho mínimo.
- **WebSocket**: descartado por over-engineering para o volume e frequência atuais (~53 usuários, atualização manual).
- **Polling**: simples de implementar, sem estado de conexão adicional no servidor, adequado à frequência de atualização real.

**Implementação Angular**:

```typescript
// production-tracking.component.ts
private readonly destroyRef = inject(DestroyRef);

ngOnInit(): void {
  interval(60_000).pipe(
    startWith(0),
    switchMap(() => this.productionService.getTrackingSummary()),
    takeUntilDestroyed(this.destroyRef)
  ).subscribe(summary => this.summary.set(summary));
}
```

O botão "Atualizar" (US-083 AC#6) dispara `refresh$.next()` imediatamente, sem aguardar o intervalo.

**Reconhecimento**: se em sprints futuras o volume de OPs ou a frequência de importação aumentar significativamente, migrar para SSE é a evolução natural — o contrato de API de tracking não muda.

---

### Decisão 6 — Projeção de interface para tracking (sem entidade nova)

Os dados de tracking são retornados via **interface projection** no `ProductionOrderRepository`, evitando carregar a entidade completa com todos os seus relacionamentos:

```java
// ProductionOrderRepository.java
public interface ProductionOrderTrackingView {
    String getDynamicsOrderNumber();
    String getProductCode();        // product.dynamicsCode
    String getProductName();        // product.name
    String getFamilyCode();         // family.code
    ProductType getProductType();   // product.type
    Integer getPlannedQty();
    Integer getProducedQty();
    LocalDate getDueDate();
    UUID getSterilizationLoadId();
    LoadStatus getSterilizationLoadStatus();
    Integer getPlannedPeople();
}

@Query("""
    SELECT
        po.dynamicsOrderNumber   AS dynamicsOrderNumber,
        p.dynamicsCode           AS productCode,
        p.name                   AS productName,
        f.code                   AS familyCode,
        p.type                   AS productType,
        po.plannedQty            AS plannedQty,
        po.producedQty           AS producedQty,
        po.dueDate               AS dueDate,
        sl.id                    AS sterilizationLoadId,
        sl.status                AS sterilizationLoadStatus,
        po.plannedPeople         AS plannedPeople
    FROM ProductionOrder po
    JOIN po.product p
    JOIN po.family f
    LEFT JOIN po.sterilizationLoad sl
    WHERE po.status NOT IN ('DONE', 'CANCELLED')
      AND (:familyCode IS NULL OR f.code = :familyCode)
      AND (:productType IS NULL OR p.type = :productType)
    ORDER BY po.dueDate ASC NULLS LAST
    """)
List<ProductionOrderTrackingView> findForTracking(
    @Param("familyCode") String familyCode,
    @Param("productType") ProductType productType
);
```

O `GetProductionTrackingUseCase` aplica o cálculo de `ProductionOrderDisplayStatus` em Java sobre o resultado da query — mantém consistência com ADR-029 Decisão 3.

---

### Decisão 7 — Tech debt Sprint 29 — ordem de implementação

Os 5 itens de tech debt (SEC-107 a SEC-111) são resolvidos **antes** das US-082 e US-083:

| Ordem | Item | Arquivo(s) afetado(s) |
|-------|------|-----------------------|
| 1 | SEC-111 — URLs frontend corrigidas | `production.service.ts` |
| 2 | SEC-110 — `clearFailedTitles()` no logout | `auth.service.ts` |
| 3 | SEC-108 — `e.getMessage()` substituído por msg genérica | 5 use cases de import |
| 4 | SEC-107 — validação MIME com `ExcelFileValidator` | `ProductionController.java` + use cases |
| 5 | SEC-109 — decisão de produto sobre `importedBy` | `ImportProductionBatchResponse` |

**SEC-109** — decisão adotada: **opção (b)** — omitir `importedBy` da `ImportProductionBatchResponse` para SUPERVISOR; mantê-lo acessível apenas via `GET /api/v1/admin/audit-log` (ADMIN). Consistente com o padrão de `AuditLog`.

```java
// ImportProductionBatchResponse.java — campo condicional
public record ImportProductionBatchResponse(
    UUID id,
    ProductionImportType type,
    String fileName,
    LocalDateTime importedAt,
    // importedBy REMOVIDO — use audit log (ADMIN) para rastreabilidade
    Integer totalRecords,
    Integer createdRecords,
    Integer updatedRecords,
    Integer errorRecords
) {}
```

---

### Decisão 8 — Auditoria de visualização (não auditada)

Consultas de tracking (`GET`) **não são auditadas** — seguem o padrão do projeto (auditoria apenas em mutações de estado relevantes para compliance). O `lastSyncAt` retornado em todos os responses de tracking serve como indicador de frescor dos dados, sem necessidade de log adicional.

---

### Contrato de API — Sprint 30

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| GET | /api/v1/production/tracking/families | OPERATOR+ | 200 `List<FamilyTrackingResponse>` | Kanban de OPs por família e status |
| GET | /api/v1/production/tracking/orders | OPERATOR+ | 200 `Page<OrderTrackingEntry>` | Listagem paginada de OPs com filtros |
| GET | /api/v1/production/tracking/summary | OPERATOR+ | 200 `TrackingSummaryResponse` | Contagens por status + lastSyncAt |

**Nota**: `PUT /api/v1/production/sterilization-loads/{id}/status` (ADR-029) já existe — Sprint 30 **não adiciona** endpoints de mutação de estado.

---

### Consequências

✅ `ProductionOrderDisplayStatus` calculado em Java (ADR-029 Decisão 3) — mantido sem mudança; nenhum campo novo no banco
✅ Limite fixo de 50 OPs por coluna evita sobrecarga no frontend sem prejudicar operações reais do MSB
✅ Polling a 60s é adequado à frequência de importação manual do Dynamics; implementação Angular simples e sem estado de conexão no servidor
✅ Interface projection `ProductionOrderTrackingView` carrega apenas campos necessários — evita N+1 em relacionamentos `product`, `family`, `sterilizationLoad`
✅ Tech debt SEC-107–SEC-111 resolvido antes das US funcionais — sprint começa sem débito acumulado
✅ `importedBy` removido de `ImportProductionBatchResponse` — alinha ao padrão de privacidade do `AuditLog` (ADMIN-only)
⚠️ Polling a 60s pode mostrar estado desatualizado entre importações — o campo `lastSyncAt` é exibido no frontend para que o usuário saiba a defasagem; sem garantia de tempo real
⚠️ Limite de 50 OPs por coluna pode ser atingido se o volume do MSB crescer — campo `truncated: true` sinaliza a condição, mas não resolve automaticamente; upgrade para paginação por coluna ou SSE em sprint futura se necessário
⚠️ Sem SSE/WebSocket no Sprint 30 — se múltiplos SUPERVISORs transitam cargas simultaneamente, cada usuário verá a atualização somente no próximo ciclo de polling (até 60s de defasagem entre sessões)
