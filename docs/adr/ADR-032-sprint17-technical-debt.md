## ADR-032: Technical Debt Sprint 17 — Validação em AnalyticsController, Query JPQL em WO Summary e Correções de UI
**Status**: Aprovado
**Data**: 2026-05-20
**US relacionadas**: US-073, US-074 (Sprint 17 — cleanup obrigatório junto com a funcionalidade principal)

### Contexto

Ao final da Sprint 16 (Advanced Analytics), os revisores Helena (code review) e Beatriz (security review) identificaram itens que foram diferidos para a Sprint 17. A Sprint 17 é de Planned Downtime (ADR-025), com escopo limitado e sem dependências de domínio compartilhadas com os itens diferidos — o cleanup pode ser feito em paralelo ou como histórias separadas sem risco de conflito.

Esta ADR consolida as decisões arquiteturais para os itens diferidos da Sprint 16, define a abordagem de cada correção e especifica como cada fix deve ser implementado para manter consistência com as convenções do projeto.

---

### Decisão 1 — @Validated + @Min/@Max no AnalyticsController

**Problema**: `AnalyticsController` não tem `@Validated` na classe, e os parâmetros `weeks`, `months` e `days` não têm anotações `@Min`/`@Max`. A validação de bounds existe apenas dentro dos use cases via `IllegalArgumentException` → handler 400. Embora o comportamento atual seja funcionalmente correto, a ausência de `@Validated` é uma armadilha silenciosa: qualquer desenvolvedor que adicione `@Min`/`@Max` em `@RequestParam` no futuro não verá as anotações disparando. Reportado como SEC-055 (Beatriz) e confirmado como SH-42 (Helena).

**Decisão**: Adicionar `@Validated` na classe e `@Min`/`@Max` nos parâmetros dos endpoints de analytics — alinhando ao padrão já estabelecido em `SupplierController` (ADR-031 Decisão 2).

```java
// common/presentation/AnalyticsController.java
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
@Validated
public class AnalyticsController {

    @GetMapping("/oee/trend")
    public OeeTrendResponse getOeeTrend(
            @RequestParam(defaultValue = "12") @Min(4) @Max(52) int weeks) { ... }

    @GetMapping("/nc/pareto")
    public NcParetoResponse getNcPareto(
            @RequestParam(defaultValue = "30") @Min(1) @Max(180) int days) { ... }

    @GetMapping("/nc/trend")
    public TimeSeriesResponse getNcTrend(
            @RequestParam(defaultValue = "12") @Min(4) @Max(52) int weeks) { ... }

    @GetMapping("/maintenance/mttr-trend")
    public MttrTrendResponse getMttrTrend(
            @RequestParam(defaultValue = "6") @Min(3) @Max(24) int months) { ... }

    // wo-summary não tem parâmetros de range — não requer @Min/@Max
}
```

**Nota**: Com `@Validated` ativo, violações de `@Min`/`@Max` em `@RequestParam` lançam `ConstraintViolationException` (não `MethodArgumentNotValidException`). O `GlobalExceptionHandler` já tem handler para `ConstraintViolationException` desde a ADR-031 Decisão 2 — sem alterações necessárias no handler.

**Redundância deliberada**: Os bounds do use case (`MIN_WEEKS=4, MAX_WEEKS=52`, etc.) podem ser mantidos como defesa em profundidade. A validação no controller é a primeira barreira; a validação no use case é a segunda.

---

### Decisão 2 — Substituir findAll() por query JPQL com agregação em executeWoSummary()

**Problema**: `GetMaintenanceAnalyticsUseCase.executeWoSummary()` usa `workOrderRepository.findAll()` sem filtro, carregando todas as ordens de serviço em memória para agrupamento em Java. Em produção com anos de histórico de OSs, isso representa risco de OOM e latência crescente. Reportado como SEC-056 (Beatriz) e elevado a MUST FIX como MF-5 (Helena).

**Decisão**: Substituir `findAll()` por queries JPQL que retornam apenas contagens agregadas via interface projections — padrão já estabelecido no projeto (ADR-003 Decisão 5, ADR-008).

```java
// maintenance/infrastructure/WorkOrderRepository.java — adicionar:

public interface WoCountByStatus {
    WorkOrderStatus getStatus();
    long getCount();
}

public interface WoCountByType {
    WorkOrderType getType();
    long getCount();
}

@Query("SELECT w.status AS status, COUNT(w) AS count FROM WorkOrder w GROUP BY w.status")
List<WoCountByStatus> countGroupedByStatus();

@Query("SELECT w.type AS type, COUNT(w) AS count FROM WorkOrder w GROUP BY w.type")
List<WoCountByType> countGroupedByType();
```

```java
// GetMaintenanceAnalyticsUseCase.executeWoSummary() — refatorar:
public WoSummaryResponse executeWoSummary() {
    // Inicializar com zeros para garantir todos os valores presentes
    Map<String, Long> byStatus = new LinkedHashMap<>();
    for (WorkOrderStatus s : WorkOrderStatus.values()) byStatus.put(s.name(), 0L);
    for (WorkOrderType t : WorkOrderType.values()) byStatus.put(t.name(), 0L);

    Map<String, Long> byStatusResult = new LinkedHashMap<>();
    for (WorkOrderStatus s : WorkOrderStatus.values()) byStatusResult.put(s.name(), 0L);
    workOrderRepository.countGroupedByStatus()
        .forEach(row -> byStatusResult.put(row.getStatus().name(), row.getCount()));

    Map<String, Long> byTypeResult = new LinkedHashMap<>();
    for (WorkOrderType t : WorkOrderType.values()) byTypeResult.put(t.name(), 0L);
    workOrderRepository.countGroupedByType()
        .forEach(row -> byTypeResult.put(row.getType().name(), row.getCount()));

    return new WoSummaryResponse(byStatusResult, byTypeResult);
}
```

**Impacto em testes**: `GetMaintenanceAnalyticsUseCaseTest` precisa ser atualizado para mockar `countGroupedByStatus()` e `countGroupedByType()` no lugar de `findAll()`. Os cenários de teste permanecem os mesmos (`woSummary_shouldReturnAllZeros_whenNoOrders`, `woSummary_shouldReturnCorrectDistribution`, etc.) — apenas a configuração dos mocks muda.

---

### Decisão 3 — Remover constante ISO_WEEK morta em GetOeeTrendUseCase

**Problema**: `GetOeeTrendUseCase` declara `private static final WeekFields ISO_WEEK = WeekFields.of(Locale.forLanguageTag("pt-BR")).ISO` mas nunca usa essa constante — o método `toIsoWeekLabel` usa `WeekFields.ISO` diretamente. Código morto que confunde quem mantém o arquivo. Reportado como SH-43 (Helena).

**Decisão**: Remover a constante `ISO_WEEK` do arquivo. Sem impacto funcional — é remoção pura.

---

### Decisão 4 — Tornar onboardedAt imutável em UpdateSupplierUseCase

**Problema**: `UpdateSupplierUseCase.execute()` chama `supplier.setOnboardedAt(request.onboardedAt())`, permitindo alterar retroativamente a data de integração do fornecedor. O campo `onboardedAt` é histórico — marca quando o fornecedor entrou no sistema. Sua mutabilidade não tem rastro no AuditLog (o log registra apenas `name` e `contactEmail`). Reportado como SH-44 (Helena).

**Decisão**: Remover `supplier.setOnboardedAt(...)` de `UpdateSupplierUseCase`. O campo passa a ser imutável via PUT — pode ser definido apenas na criação (`POST /suppliers`). Relação com SH-36 (Sprint 14, pendente): quando `UpdateSupplierRequest` separado for criado, o campo `onboardedAt` deve ser omitido do record.

```java
// UpdateSupplierUseCase.execute() — remover a linha:
// supplier.setOnboardedAt(request.onboardedAt());  // remover

// Campos atualizáveis via PUT permanecem: name, contactEmail, contactPhone, address
```

Se a UI precisar exibir o `onboardedAt` atual no formulário de edição (readonly), o `SupplierResponse` já inclui o campo — o frontend deve apenas exibi-lo como texto, não como input editável.

---

### Decisão 5 — Corrigir cores de severidade em QmsAnalyticsComponent (BUG-2)

**Problema**: O método `severityColor()` em `qms-analytics.component.ts` usa valores de cor incorretos (ex: `CRITICAL=#E53E3E` em vez de `CRITICAL=#EF4444`). O AC da US-044 especifica cores fixas por severidade; a implementação usa valores próximos mas diferentes dos especificados. Além disso, há risco de um valor de enum não mapeado cair no fallback teal. Reportado como BUG-2 (Maiana) e confirmado como SUG-25 (Helena).

**Decisão**: Corrigir `severityColor()` para usar as cores exatas do AC, verificando que todos os valores de `NcSeverity` estejam mapeados.

```typescript
// qms-analytics.component.ts
private severityColor(severity: string): string {
    const map: Record<string, string> = {
        CRITICAL: '#EF4444',  // red-500
        HIGH:     '#F97316',  // orange-500
        MEDIUM:   '#EAB308',  // yellow-500
        LOW:      '#22C55E',  // green-500
    };
    return map[severity] ?? '#9CA3AF';  // fallback: gray em vez de teal (evita confusão com OPEN)
}
```

O spec correspondente em `qms-analytics.component.spec.ts` deve ser atualizado para validar as cores corretas conforme o AC.

---

### Decisão 6 — Ordem de implementação na Sprint 17

Para minimizar conflitos de merge e garantir que o débito técnico não bloqueie a funcionalidade principal:

| Ordem | Item | Tipo | Impacto |
|-------|------|------|---------|
| 1 | Remoção de `ISO_WEEK` morta (SH-43) | Backend OEE | Isolado, sem impacto em outros módulos |
| 2 | `setOnboardedAt` removido de `UpdateSupplierUseCase` (SH-44) | Backend QMS | Isolado, sem impacto em outros módulos |
| 3 | `severityColor()` corrigida + spec atualizado (BUG-2) | Frontend QMS | Isolado, sem impacto em outros componentes |
| 4 | Query JPQL em `executeWoSummary()` + interface projections (MF-5/SEC-056) | Backend Maintenance | Requer atualização de mocks nos testes |
| 5 | `@Validated` + `@Min`/`@Max` no `AnalyticsController` (SEC-055/SH-42) | Backend Analytics | Verificar que bounds são consistentes com use cases |
| 6 | US-073 + US-074 — Planned Downtime (funcionalidade principal) | Backend + Frontend OEE | Independente dos itens de tech debt acima |

---

### Contrato de API — novos comportamentos

| Situação | Antes (buggy) | Depois (corrigido) |
|----------|---------------|-------------------|
| `weeks=100` em `/analytics/oee/trend` | 400 via `IllegalArgumentException` no use case | 400 via `ConstraintViolationException` no controller (mesmo resultado, caminho mais curto) |
| `months=0` em `/analytics/maintenance/mttr-trend` | 400 via `IllegalArgumentException` no use case | 400 via `ConstraintViolationException` no controller |
| `GET /analytics/maintenance/wo-summary` (10k OSs) | carrega todas as entidades em memória | retorna apenas counts agrupados (sem carregar entidades) |
| `PUT /suppliers/{id}` com `onboardedAt` diferente | aceita e persiste alteração silenciosamente | `onboardedAt` ignorado — campo imutável via PUT |

---

### Consequências
✅ `@Validated` no `AnalyticsController` fecha lacuna de armadilha silenciosa para próximos endpoints
✅ Query JPQL com GROUP BY em `executeWoSummary()` elimina risco de OOM em produção com histórico crescente de OSs
✅ `onboardedAt` imutável em `UpdateSupplierUseCase` preserva integridade histórica do dado de integração de fornecedor
✅ Cores de severidade corrigidas para seguir o AC — doughnut de QMS visualmente correto
⚠️ `@Validated` no controller com `@Min`/`@Max` requer atualização dos testes de controller existentes para cobrir o novo caminho de validação (se tests de controller existirem — atualmente apenas code review)
⚠️ Remoção de `setOnboardedAt` pode gerar confusão se o formulário frontend de edição exibir o campo como editável — verificar template `supplier-form.component.html` e tornar o campo readonly no modo edição
