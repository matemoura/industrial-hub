## ADR-042: Sprint 31 — Implementação das Cargas de Esterilização
**Status**: Aprovado
**Data**: 2026-05-29
**US relacionadas**: US-084, US-099

---

### Contexto

O ADR-029 definiu a arquitetura e o modelo de dados das cargas de esterilização (`SterilizationLoad`), o fluxo de status e o contrato de API. Este ADR complementa com as **decisões de implementação específicas do Sprint 31** que o ADR-029 deixou em aberto: geração do número de carga, mecânica dos efeitos colaterais sobre `Equipment`, alocação de OPs no frontend, tratamento de erros de transição e comportamento da fila de pendentes com cargas REJECTED. Também cobre a ordem de execução do tech debt diferido do Sprint 30 (US-099).

---

### Decisão 1 — Geração do `loadNumber` ("CARGA-{ANO}-{NNN}")

**Abordagem**: contagem via query JPQL no repositório, sem `SEQUENCE` de banco separado.

```java
// SterilizationLoadRepository.java
@Query("SELECT COALESCE(MAX(CAST(SUBSTRING(sl.loadNumber, 12) AS int)), 0) + 1 " +
       "FROM SterilizationLoad sl " +
       "WHERE sl.loadNumber LIKE CONCAT('CARGA-', :year, '-%')")
int nextSequenceForYear(@Param("year") int year);
```

```java
// CreateSterilizationLoadUseCase.java — dentro de @Transactional
int year = LocalDate.now().getYear();
int seq  = loadRepository.nextSequenceForYear(year);
String loadNumber = "CARGA-%d-%03d".formatted(year, seq);
```

**Justificativa**: o MSB cria poucas dezenas de cargas por ano; não há risco de colisão com `@Transactional` + `PESSIMISTIC_WRITE` implícito do Spring Data no contexto de escrita. Evita DDL adicional (`CREATE SEQUENCE`) que exigiria migração de banco. Se o volume crescer, migrar para `@GeneratedValue(strategy=SEQUENCE)` com `allocationSize=1`.

**Constraint de unicidade**: `@Column(unique = true)` em `SterilizationLoad.loadNumber` — qualquer colisão de corrida resulta em `DataIntegrityViolationException` → GlobalExceptionHandler → 409.

---

### Decisão 2 — Relacionamento `ProductionOrder` ↔ `SterilizationLoad`

**ManyToOne nullable em `ProductionOrder`**:

```java
// ProductionOrder.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "sterilization_load_id", nullable = true)
private SterilizationLoad sterilizationLoad;
```

- Uma OP pode estar em **no máximo uma** carga ativa ao mesmo tempo.
- Ao adicionar OP à carga (`POST /{id}/orders`): `productionOrder.setSterilizationLoad(load)` + `save()`.
- Ao remover OP da carga (`DELETE /{id}/orders/{opId}`): `productionOrder.setSterilizationLoad(null)` + `save()`.
- Ao rejeitar carga (`STERILIZING → REJECTED`): todas as OPs da carga têm `sterilizationLoad = null` em bulk:
  ```java
  orderRepository.clearLoadForAllOrdersInLoad(loadId);
  // JPQL: UPDATE ProductionOrder po SET po.sterilizationLoad = null WHERE po.sterilizationLoad.id = :loadId
  ```

**Validação de alocação** (US-084 AC#5): a OP não pode estar em outra carga com `status ≠ REJECTED`. Query de verificação:
```java
// SterilizationLoadRepository.java
boolean existsActiveAllocationForOrder(UUID orderId);
// JPQL: SELECT COUNT(po) > 0 FROM ProductionOrder po
//       WHERE po.id = :orderId
//       AND po.sterilizationLoad IS NOT NULL
//       AND po.sterilizationLoad.status <> 'REJECTED'
```

---

### Decisão 3 — Efeitos colaterais em `Equipment` nas transições de status

O módulo `production` referencia o módulo `maintenance` via FK (`sterilizer_id` → `equipment.id`). A interação ocorre **apenas** em `TransitionLoadStatusUseCase` e é mediada pelo `EquipmentRepository` (Spring Data JPA) — sem dependência de serviço cross-module.

```java
// TransitionLoadStatusUseCase.java
@Transactional
public SterilizationLoadResponse execute(UUID loadId, LoadStatus targetStatus) {
    SterilizationLoad load = loadRepository.findById(loadId)
        .orElseThrow(() -> new SterilizationLoadNotFoundException(loadId));

    validateTransition(load.getStatus(), targetStatus);  // lança InvalidLoadTransitionException se inválida

    switch (targetStatus) {
        case CLOSED -> {
            load.setClosedAt(LocalDateTime.now());
            if (load.getSterilizer() != null) {
                Equipment eq = load.getSterilizer();
                eq.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
                equipmentRepository.save(eq);
            }
        }
        case STERILIZING -> { /* sem efeito colateral adicional */ }
        case RELEASED -> {
            load.setReleasedAt(LocalDateTime.now());
            if (load.getSterilizer() != null) {
                Equipment eq = load.getSterilizer();
                eq.setStatus(EquipmentStatus.OPERATIONAL);
                equipmentRepository.save(eq);
            }
        }
        case REJECTED -> {
            orderRepository.clearLoadForAllOrdersInLoad(loadId);
        }
    }
    load.setStatus(targetStatus);
    return SterilizationLoadResponse.from(loadRepository.save(load));
}
```

**Transições válidas**:

| De | Para | Válida |
|----|------|--------|
| OPEN | CLOSED | ✅ |
| CLOSED | STERILIZING | ✅ |
| STERILIZING | RELEASED | ✅ |
| STERILIZING | REJECTED | ✅ |
| Qualquer outro | Qualquer | ❌ 422 |

```java
private void validateTransition(LoadStatus current, LoadStatus target) {
    boolean valid = switch (current) {
        case OPEN        -> target == LoadStatus.CLOSED;
        case CLOSED      -> target == LoadStatus.STERILIZING;
        case STERILIZING -> target == LoadStatus.RELEASED || target == LoadStatus.REJECTED;
        default          -> false;
    };
    if (!valid) throw new InvalidLoadTransitionException(current, target);
}
```

```java
// InvalidLoadTransitionException → GlobalExceptionHandler → 422
public class InvalidLoadTransitionException extends RuntimeException {
    public InvalidLoadTransitionException(LoadStatus from, LoadStatus to) {
        super("Transição inválida: %s → %s".formatted(from, to));
    }
}
```

---

### Decisão 4 — Frontend: alocação de OPs via dialog (não drag-and-drop)

**Decisão**: usar **dialog de confirmação** para alocar OPs à carga aberta, não drag-and-drop.

**Justificativa**: ADR-029 menciona D&D como alternativa e dialog como fallback. O ADR-027 (dashboard) usou HTML5 D&D para reordenar widgets — implementação presente, mas projetada para grid 2D. A fila de OPs pendentes é uma lista 1D; o ganho de UX do D&D é baixo frente à complexidade de adaptar o mecanismo. Dado que o MSB tem 53 usuários internos em desktops industriais (não necessariamente com mouse preciso), o dialog é mais robusto.

**Fluxo UX**:
```
[Tela de carga OPEN]
├── Seção "OPs Alocadas": tabela com botão "×" por linha (remove OP — apenas OPEN)
└── Painel lateral "Aguardando Carga":
    ├── Lista de OPs pendentes (produto, nº OP, dueDate, badge "ATRASADA" se overdue)
    └── Botão "+ Adicionar" por OP → dialog:
        "Adicionar OP {dynamicsOrderNumber} ({productName}) à carga {loadNumber}?"
        [Cancelar] [Confirmar]
```

---

### Decisão 5 — Fila `pending-orders` com cargas REJECTED

Quando uma carga vai para REJECTED, as OPs voltam para `sterilizationLoad = null` (Decisão 2). Consequentemente, **essas OPs reaparecem automaticamente** no endpoint `GET /pending-orders` sem nenhuma lógica extra — a query filtra por `sterilizationLoad IS NULL`, o que já inclui OPs de cargas rejeitadas.

Não há campo de "histórico de rejeição" por OP — o SUPERVISOR usa o endpoint `GET /api/v1/production/sterilization-loads` com filtro `status=REJECTED` para consultar cargas rejeitadas. Rastreabilidade de qual carga cada OP estava é obtida via `AuditLog` (ADMIN-only).

---

### Decisão 6 — Estrutura de pacotes e use cases do Sprint 31

**Novos use cases** em `production/application/usecase/`:

| Use Case | Responsabilidade |
|----------|-----------------|
| `CreateSterilizationLoadUseCase` | POST /sterilization-loads |
| `ListSterilizationLoadsUseCase` | GET /sterilization-loads |
| `GetSterilizationLoadDetailUseCase` | GET /sterilization-loads/{id} |
| `GetPendingOrdersForLoadUseCase` | GET /sterilization-loads/pending-orders |
| `AddOrderToLoadUseCase` | POST /sterilization-loads/{id}/orders |
| `RemoveOrderFromLoadUseCase` | DELETE /sterilization-loads/{id}/orders/{opId} |
| `TransitionLoadStatusUseCase` | PUT /sterilization-loads/{id}/status |

**Novos domínios** em `production/domain/`:
- `SterilizationLoad.java` (conforme ADR-029 Decisão 1)
- `LoadStatus.java` (enum: OPEN, CLOSED, STERILIZING, RELEASED, REJECTED)
- `SterilizationMethod.java` (enum: EO_GAS, GAMMA, STEAM, OTHER)
- `SterilizationLoadNotFoundException.java`
- `InvalidLoadTransitionException.java`
- `OrderAlreadyAllocatedException.java` (conflito de alocação → 409)

**Repositório**: `SterilizationLoadRepository.java` em `production/infrastructure/`.

**Controller**: endpoints adicionados a `ProductionController.java` (mantém convenção de único controller por feature).

---

### Decisão 7 — Tech debt Sprint 30 (US-099) — ordem de implementação

| Ordem | Item | Arquivo(s) | Esforço |
|-------|------|-----------|---------|
| 1 | SEC-115 — `@PreAuthorize` explícito nos endpoints de tracking | `ProductionController.java` | Baixo |
| 2 | SEC-060 — URL `markAllRead()` no frontend | `notifications.service.ts` | Baixo |
| 3 | SEC-074 — `window.open` sem `noopener,noreferrer` | `file-attachments.component.html` | Baixo |
| 4 | SEC-069 — `Principal` null check nos controllers | `DashboardController`, `AlertThresholdController` | Médio |

**SEC-115**: substituir `isAuthenticated()` por `hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')` nos endpoints de tracking — torna a permissão explícita e alinhada ao padrão do projeto; sem impacto funcional (todos os roles autenticados já tinham acesso).

---

### Contrato de API — Sprint 31

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| POST | /api/v1/production/sterilization-loads | SUPERVISOR+ | 201 | Criar carga |
| GET | /api/v1/production/sterilization-loads | OPERATOR+ | 200 | Listar cargas |
| GET | /api/v1/production/sterilization-loads/{id} | OPERATOR+ | 200 | Detalhe + OPs |
| GET | /api/v1/production/sterilization-loads/pending-orders | SUPERVISOR+ | 200 | Fila de OPs aguardando |
| POST | /api/v1/production/sterilization-loads/{id}/orders | SUPERVISOR+ | 204 | Adicionar OP à carga |
| DELETE | /api/v1/production/sterilization-loads/{id}/orders/{opId} | SUPERVISOR+ | 204 | Remover OP da carga |
| PUT | /api/v1/production/sterilization-loads/{id}/status | SUPERVISOR+ | 200 | Transição de status |

**Erros esperados**:
- `404` — carga ou OP não encontrada
- `409` — OP já alocada em outra carga ativa
- `422` — transição de status inválida
- `400` — body inválido (Spring `@Valid`)

---

### Consequências

✅ Geração de `loadNumber` por JPQL sem `SEQUENCE` dedicado — sem DDL extra; adequado ao volume do MSB
✅ `ManyToOne` nullable em `ProductionOrder` — modelo limpo, sem tabela de join extra; `sterilizationLoad IS NULL` é a query de fila de pendentes
✅ Efeitos colaterais em `Equipment` em mesma `@Transactional` — consistência de estado garantida; sem risco de esterilizador ficar `UNDER_MAINTENANCE` sem carga correspondente
✅ Dialog de alocação — UX robusta em desktops industriais; sem complexidade de D&D
✅ OPs de cargas REJECTED reaparecem automaticamente em `pending-orders` — sem lógica extra de re-queuing
⚠️ `nextSequenceForYear` usa `MAX(seq)` — em caso extremo de carga deletada manualmente do banco, pode reutilizar número; `@Column(unique=true)` garante falha de integridade, não silêncio
⚠️ `clearLoadForAllOrdersInLoad` em REJECTED é um UPDATE bulk — se uma carga tiver muitas OPs (> 100), pode ser lento; adequado para o volume do MSB mas monitorar em produção
⚠️ Frontend sem D&D para alocação — usuários acostumados com kanban podem estranhar o dialog; avaliar migração em sprint futura se houver feedback negativo de UX
