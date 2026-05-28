## ADR-043: Sprint 32 — Implementação do Motor MRP, Staffing e Board de Planejamento
**Status**: Aprovado
**Data**: 2026-05-29
**US relacionadas**: US-085, US-086, US-087, US-100

---

### Contexto

O ADR-030 definiu a arquitetura dos módulos MRP, Staffing e Planning Board — entidades, algoritmo Net Change, endpoints e estrutura de resposta. Este ADR cobre as **decisões de implementação específicas do Sprint 32** que o ADR-030 deixou em aberto:

- Bootstrap do singleton `StaffingConfig` (como garantir exatamente um registro)
- Fronteira transacional entre `dry-run` e `run` (o que se persiste e quando)
- Idempotência do MRP: comportamento quando executado repetidamente sem mudanças no estoque
- Cálculo de dias úteis (`businessDays()`) sem biblioteca de calendário
- `totalOrders` no `SterilizationLoadSummary` (US-100 — gap AC#12 do Sprint 31) via `@Formula`
- Frontend: timeline CSS Grid sem biblioteca de Gantt
- Endpoint `/convert` ausente da tabela de API do ADR-030 Decisão 8 — adicionado aqui
- Estrutura de pacotes para os novos use cases de MRP, staffing e planning

---

### Decisão 1 — Bootstrap do `StaffingConfig` singleton

O `StaffingConfig` deve existir como registro único. Em vez de forçar via DDL (`CHECK (id = fixed_uuid)`), o bootstrap ocorre via **`@PostConstruct` no use case** que acessa a configuração:

```java
// GetStaffingConfigUseCase.java
@Service
public class GetStaffingConfigUseCase {

    private final StaffingConfigRepository repository;

    @Transactional
    public StaffingConfig getOrCreate() {
        return repository.findFirst()
                .orElseGet(() -> {
                    StaffingConfig defaults = new StaffingConfig();
                    defaults.setShiftHours(8);
                    defaults.setShiftsPerDay(1);
                    defaults.setUpdatedAt(LocalDateTime.now());
                    defaults.setUpdatedBy("system");
                    return repository.save(defaults);
                });
    }
}

// StaffingConfigRepository.java
public interface StaffingConfigRepository extends JpaRepository<StaffingConfig, UUID> {
    @Query("SELECT sc FROM StaffingConfig sc ORDER BY sc.updatedAt ASC")
    Optional<StaffingConfig> findFirst(Pageable pageable);

    default Optional<StaffingConfig> findFirst() {
        return findFirst(PageRequest.of(0, 1));
    }
}
```

**Justificativa**: dados de configuração não justificam migration SQL com valores default. Bootstrap lazy é consistente com o padrão `findFirst().orElseGet(create)` já adotado em `ShiftManagement` (ADR-034) e `AlertThreshold` (ADR-013). O registro único é garantido pela aplicação, não por constraint de banco — aceitável para o volume e concorrência do MSB (~53 usuários).

---

### Decisão 2 — Fronteira transacional: dry-run vs. run

`dry-run` e `run` usam o **mesmo método de cálculo** (`MrpCalculationService`), diferenciando-se apenas pela persistência:

```java
// MrpCalculationService.java — @Service sem @Transactional (stateless helper)
public MrpRunResult calculate(boolean isDryRun, String username) { ... }

// RunMrpUseCase.java
@Service
public class RunMrpUseCase {
    @Transactional  // persiste MrpRun + MrpPlannedOrders
    public MrpRunResult execute(String username) {
        MrpRunResult result = calculationService.calculate(false, username);
        mrpRunRepository.save(result.run());
        mrpPlannedOrderRepository.saveAll(result.suggestions());
        auditService.log(username, AuditAction.MRP_RUN_EXECUTED, ...);
        return result;
    }
}

// DryRunMrpUseCase.java
@Service
public class DryRunMrpUseCase {
    // Sem @Transactional — leitura pura, nada é persistido
    public MrpRunResult execute(String username) {
        return calculationService.calculate(true, username);
    }
}
```

**`MrpRunResult` DTO** (record):
```java
public record MrpRunResult(
    MrpRunResponse run,                        // metadados do run
    List<MrpPlannedOrderResponse> suggestions, // sugestões geradas
    List<PurchaseNeedResponse> purchaseNeeds,  // RAW_MATERIAL a comprar
    List<String> messages,                     // alertas do run
    boolean isDryRun
) {}
```

**Justificativa**: separar `MrpCalculationService` como helper stateless evita duplicação de lógica entre dry-run e run. O `@Transactional` apenas no `RunMrpUseCase` garante que dry-run nunca acidentalmente persiste dados (ausência de transação de escrita → qualquer `save()` chamado acidentalmente lançaria exceção).

---

### Decisão 3 — Idempotência do MRP: invalidação de sugestões anteriores

Ao executar `POST /mrp/run`, sugestões `SUGGESTED` do run anterior são automaticamente **marcadas como `SUPERSEDED`** (novo status) antes de gerar novas:

```java
// RunMrpUseCase.java — dentro de @Transactional
@Modifying
@Query("UPDATE MrpPlannedOrder o SET o.status = 'SUPERSEDED' " +
       "WHERE o.status = 'SUGGESTED'")
void supersedePendingSuggestions();
```

**`MrpOrderStatus` atualizado**: `SUGGESTED | ACCEPTED | REJECTED | CONVERTED | SUPERSEDED`

**Justificativa**: executar MRP duas vezes sem cancelar as sugestões anteriores duplicaria ordens. `SUPERSEDED` é mais informativo que `REJECTED` (que implica decisão humana) e permite auditoria. Sugestões `ACCEPTED` **não são supersedidas** — o SUPERVISOR já tomou uma decisão explícita.

---

### Decisão 4 — Cálculo de dias úteis (`businessDays`)

Implementado como método utilitário em `production/application/util/BusinessDaysCalculator.java`:

```java
public final class BusinessDaysCalculator {

    private BusinessDaysCalculator() {}

    /**
     * Conta dias úteis (seg–sex) entre hoje e dueDate, exclusive.
     * Feriados são out-of-scope (ADR-030 Consequências).
     * Retorna mínimo 1 para evitar divisão por zero.
     */
    public static int workdaysUntil(LocalDate today, LocalDate dueDate) {
        if (!dueDate.isAfter(today)) return 1;
        int days = 0;
        LocalDate d = today;
        while (d.isBefore(dueDate)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) days++;
            d = d.plusDays(1);
        }
        return Math.max(1, days);
    }
}
```

**Justificativa**: implementação simples sem dependência de biblioteca de calendário. O loop é O(N) nos dias — para o intervalo típico do MSB (OPs com `dueDate` ≤ 90 dias), O(90) é negligenciável. Feriados ficam fora de scope conforme ADR-030 — o usuário ajusta o `leadTimeDays` manualmente como folga.

---

### Decisão 5 — `totalOrders` em `SterilizationLoadSummary` via `@Formula` (US-100)

Para exibir o badge de OPs alocadas nos cards da lista sem N+1 queries:

```java
// SterilizationLoad.java — campo calculado
@Formula("(SELECT COUNT(*) FROM production_order po WHERE po.sterilization_load_id = id)")
private Integer totalOrders;
```

```java
// SterilizationLoadResponse.java — record atualizado
public record SterilizationLoadResponse(
    UUID id,
    String loadNumber,
    LoadStatus status,
    SterilizationMethod method,
    String sterilizerName,
    LocalDate sterilizationDate,
    String batchCode,
    String notes,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime closedAt,
    LocalDateTime releasedAt,
    Integer totalOrders  // novo — US-100
) {
    public static SterilizationLoadResponse from(SterilizationLoad sl) {
        return new SterilizationLoadResponse(
            ...,
            sl.getTotalOrders() != null ? sl.getTotalOrders() : 0
        );
    }
}
```

**Justificativa**: `@Formula` dispara subquery SQL inline na mesma query de carregamento da entidade — sem JOIN extra, sem N+1. Alternativa considerada (JOIN + `@Query` com projeção) é mais verbosa e quebra o padrão `SterilizationLoadResponse.from()`. `@Formula` é SQL nativo (`COUNT(*)`), mas é somente leitura e simples o suficiente para não justificar JPQL alternativo (ADR-001 permite SQL nativo para agregações simples).

---

### Decisão 6 — Frontend: Timeline CSS Grid (sem biblioteca de Gantt)

A timeline (US-087 AC#7) é implementada com **CSS Grid puro** — sem `gantt.js`, `frappe-gantt` ou similar.

**Estrutura do grid**:
```
[semana 1] [semana 2] [semana 3] ... [semana N]   ← colunas (X)
[OP-001  ] [=======barra=======]                  ← linhas (Y)
[OP-002  ] [=barra=]
[MRP-abc ] [              ===barra===]
```

```scss
// planning-timeline.component.scss
.timeline-grid {
  display: grid;
  grid-template-columns: 180px repeat(var(--weeks), minmax(80px, 1fr));
  grid-auto-rows: 48px;
  overflow-x: auto;
}

.timeline-bar {
  grid-row: var(--row);
  grid-column: var(--col-start) / var(--col-end);
  background: var(--bar-color);  // #0099B8 (Dynamics) ou #F97316 (MRP)
  border-radius: 4px;
  margin: 8px 2px;
  cursor: pointer;
  transition: filter 0.15s;

  &--overdue { border: 2px solid #DC2626; }
  &:hover    { filter: brightness(0.9); }
}
```

```typescript
// planning-timeline.component.ts
readonly bars = computed(() => {
  return this.entries().map((entry, i) => ({
    ...entry,
    row: i + 2,  // row 1 = header de semanas
    colStart: this.weekIndex(entry.startDate) + 2,  // col 1 = label OP
    colEnd:   this.weekIndex(entry.dueDate) + 3,
    color: entry.isMrpSuggestion ? '#F97316' : '#0099B8',
  }));
});
```

**Justificativa**: bibliotecas de Gantt geralmente não suportam Angular 21 + signals de forma nativa; adicionam bundle size significativo para 53 usuários. A timeline do MSB é simples (barras horizontais por semana, sem dependências entre barras, sem arrastar). CSS Grid resolve com ~50 linhas de CSS e sem dependências externas — consistente com ADR-023 (PWA sem frameworks externos desnecessários).

---

### Decisão 7 — Endpoint `/convert` (US-085 AC#10)

Adicionado à tabela de API do ADR-030 Decisão 8 (ausência era omissão, não decisão contrária):

```java
// ProductionController.java
@PutMapping("/mrp/suggested-orders/{id}/convert")
@ResponseStatus(HttpStatus.OK)
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public MrpPlannedOrderResponse convertSuggestion(
        @PathVariable UUID id,
        Authentication authentication) {
    return convertMrpSuggestion.execute(id, authentication.getName());
}
```

**Regras**: apenas `status = ACCEPTED` pode ser convertido → `CONVERTED`; `status != ACCEPTED` → 409 com `{ "message": "Sugestão não está aceita" }`. Audita `MRP_SUGGESTION_CONVERTED`.

---

### Decisão 8 — Estrutura de pacotes para os novos use cases

```
production/
├── application/
│   ├── dto/
│   │   ├── MrpRunResponse.java
│   │   ├── MrpRunResult.java
│   │   ├── MrpPlannedOrderResponse.java
│   │   ├── PurchaseNeedResponse.java
│   │   ├── StaffingConfigResponse.java
│   │   ├── ProductionOrderStaffingResponse.java
│   │   ├── FamilyPlanningBoardResponse.java
│   │   └── TimelineEntryResponse.java
│   ├── usecase/
│   │   ├── RunMrpUseCase.java
│   │   ├── DryRunMrpUseCase.java
│   │   ├── GetMrpRunsUseCase.java
│   │   ├── GetMrpSuggestionsUseCase.java
│   │   ├── AcceptMrpSuggestionUseCase.java
│   │   ├── RejectMrpSuggestionUseCase.java
│   │   ├── ConvertMrpSuggestionUseCase.java
│   │   ├── GetStaffingConfigUseCase.java
│   │   ├── UpdateStaffingConfigUseCase.java
│   │   ├── UpdateOrderStaffingUseCase.java
│   │   ├── ResetOrderStaffingUseCase.java
│   │   ├── GetPlanningBoardUseCase.java
│   │   ├── GetPlanningTimelineUseCase.java
│   │   └── GetPurchaseNeedsUseCase.java
│   └── util/
│       └── BusinessDaysCalculator.java   ← novo
├── domain/
│   ├── MrpRun.java
│   ├── MrpPlannedOrder.java
│   ├── MrpOrderStatus.java               ← inclui SUPERSEDED
│   └── StaffingConfig.java
└── infrastructure/
    ├── MrpRunRepository.java
    ├── MrpPlannedOrderRepository.java
    └── StaffingConfigRepository.java
```

`MrpCalculationService` fica em `production/application/` (não em `usecase/`) por ser um helper stateless compartilhado por `RunMrpUseCase` e `DryRunMrpUseCase`.

---

### Contrato de API — Sprint 32

**MRP:**
| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | /api/v1/production/mrp/dry-run | SUPERVISOR+ | 200 | Simula MRP sem persistir |
| POST | /api/v1/production/mrp/run | SUPERVISOR+ | 201 | Executa MRP e gera sugestões |
| GET | /api/v1/production/mrp/runs | SUPERVISOR+ | 200 | Histórico paginado de runs |
| GET | /api/v1/production/mrp/suggested-orders | SUPERVISOR+ | 200 | Sugestões SUGGESTED pendentes |
| PUT | /api/v1/production/mrp/suggested-orders/{id}/accept | SUPERVISOR+ | 200 | Aceitar (com ajuste opcional de qty) |
| PUT | /api/v1/production/mrp/suggested-orders/{id}/reject | SUPERVISOR+ | 200 | Rejeitar com motivo |
| PUT | /api/v1/production/mrp/suggested-orders/{id}/convert | SUPERVISOR+ | 200 | Marcar como criada no Dynamics |

**Staffing:**
| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| GET | /api/v1/production/staffing-config | OPERATOR+ | 200 | Configuração de turno |
| PUT | /api/v1/production/staffing-config | ADMIN | 200 | Atualizar horas/turno e turnos/dia |
| PUT | /api/v1/production/production-orders/{id}/staffing | SUPERVISOR+ | 200 | Editar pessoas manualmente |
| DELETE | /api/v1/production/production-orders/{id}/staffing | SUPERVISOR+ | 200 | Resetar para cálculo automático |

**Planning Board:**
| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| GET | /api/v1/production/planning/families | SUPERVISOR+ | 200 | Board completo por família |
| GET | /api/v1/production/planning/timeline | SUPERVISOR+ | 200 | Timeline Gantt por família (`?familyCode=X&weeks=8`) |
| GET | /api/v1/production/planning/purchase-needs | SUPERVISOR+ | 200 / 404 | Matérias-primas do último MRP run |

**Erros esperados:**
- `404` — sugestão, OP ou config não encontrada
- `409` — sugestão com status incompatível para a operação (ex: convert em SUGGESTED)
- `400` — body inválido (`@Valid`)

---

### Consequências

✅ `MrpCalculationService` stateless separado — lógica de negócio testável sem banco; dry-run e run usam exatamente o mesmo algoritmo
✅ `StaffingConfig` singleton via bootstrap lazy — sem DDL extra; consistente com padrões anteriores do projeto
✅ `SUPERSEDED` evita acúmulo de sugestões obsoletas após múltiplos runs — board de sugestões sempre reflete o último cálculo
✅ `@Formula` para `totalOrders` — subquery inline, sem N+1, sem JOIN extra na query de listagem
✅ Timeline CSS Grid puro — zero dependências externas, bundle size preservado, Angular signals-friendly
⚠️ `SUPERSEDED` é um 5º status em `MrpOrderStatus` — Mateus deve garantir que queries existentes que filtram por `SUGGESTED` não precisem ser atualizadas para excluir `SUPERSEDED` explicitamente
⚠️ `@Formula` usa SQL nativo — se o nome da tabela ou coluna mudar em uma migration futura, o `@Formula` não é verificado pelo Hibernate em tempo de compilação; documentar em comentário inline
⚠️ CSS Grid timeline sem biblioteca — navegação por setas (avançar/retroceder semanas) requer recálculo de `colStart/colEnd` no componente; testar em viewports de 1024px (mínimo suportado no MSB)
⚠️ `businessDays()` conta apenas seg–sex, sem feriados — o SUPERVISOR deve adicionar folga manual no `leadTimeDays` do produto; documentar no tooltip da coluna "Prazo sugerido" no board
