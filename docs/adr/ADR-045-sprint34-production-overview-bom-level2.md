## ADR-045: Sprint 34 — Painel Executivo de Produção + BOM Nível 2 + Tech Debt S33

**Status**: Aprovado
**Data**: 2026-06-01
**US relacionadas**: US-104, US-105, US-106

---

### Contexto

Sprint 34 tem três objetivos complementares:

1. **US-104**: Visão executiva consolidada — gestores precisam de uma tela única com KPIs de BOM coverage, MRP fulfillment e tendência de eficiência, sem navegar entre múltiplas telas.
2. **US-105**: Completar a explosão BOM do MRP (ADR-044 Decisão 3 diferiu INTERMEDIATE para Sprint 34). Com `bomByParent` pré-carregado, a extensão para nível 2 reutiliza o mesmo Map sem novas queries.
3. **US-106**: Liquidar o tech debt diferido do Sprint 33 (SEC-119 CSV injection, WebhookUrlValidatorTest instabilidade de CI, npm audit).

---

### Decisão 1 — ProductionOverviewUseCase: agregação em memória sem query nativa

`GetProductionOverviewUseCase` carrega as entidades necessárias via repositórios existentes e agrega em Java:

- **BOM coverage**: `productRepository.findAll()` filtrado por `FINISHED + active` → conta quantos têm `bomByParent` não vazio (reutiliza `componentRepository.findAllActive()` já presente)
- **MRP fulfillment**: `mrpPlannedOrderRepository.findAll()` → agrupa por status; `fulfillmentPct = accepted / (total - rejected) * 100`, dividindo por zero → `null`
- **OPs por status**: `productionOrderRepository.findAll()` → `Collectors.groupingBy(ProductionOrder::getStatus, Collectors.counting())`
- **Efficiency trend**: query JPQL agregada por data via `ProductionOrderRepository.findDailyEfficiency(from, to)`

Alternativa descartada: query nativa com múltiplos CTEs — dificulta manutenção, viola ADR principal (JPQL only).

```java
// ProductionOrderRepository
@Query("SELECT new com.industrialhub.backend.production.application.dto.DailyEfficiencyDto(" +
       "  CAST(o.updatedAt AS LocalDate), " +
       "  AVG(CASE WHEN o.plannedQty > 0 THEN o.producedQty * 100.0 / o.plannedQty ELSE NULL END)) " +
       "FROM ProductionOrder o " +
       "WHERE o.status = 'COMPLETED' " +
       "AND o.updatedAt >= :from AND o.updatedAt <= :to " +
       "GROUP BY CAST(o.updatedAt AS LocalDate) " +
       "ORDER BY CAST(o.updatedAt AS LocalDate)")
List<DailyEfficiencyDto> findDailyEfficiency(@Param("from") LocalDate from, @Param("to") LocalDate to);
```

---

### Decisão 2 — BOM Nível 2: reutilização do Map pré-carregado (sem queries adicionais)

A extensão de `explodePurchaseNeeds()` para nível 2 reutiliza o `bomByParent` já carregado antes do loop MRP:

```java
// MrpCalculationService — extensão de explodePurchaseNeeds()
private List<CalculationOutput.PurchaseNeed> explodePurchaseNeeds(
        Product product, int suggestedQty,
        Map<String, List<ProductComponent>> bomByParent) {

    List<ProductComponent> level1 = bomByParent.getOrDefault(product.getDynamicsCode(), List.of());
    if (level1.isEmpty()) return List.of();

    List<CalculationOutput.PurchaseNeed> needs = new ArrayList<>();

    for (ProductComponent comp : level1) {
        if (comp.getComponentProduct().getType() == ProductType.RAW_MATERIAL) {
            // Nível 1 RAW_MATERIAL — já existia no Sprint 33
            needs.add(new CalculationOutput.PurchaseNeed(
                comp.getComponentProduct().getDynamicsCode(),
                comp.getComponentProduct().getName(),
                (int) Math.ceil(suggestedQty * comp.getQuantity()),
                comp.getUnit() != null ? comp.getUnit() : "UN"
            ));
        } else if (comp.getComponentProduct().getType() == ProductType.INTERMEDIATE) {
            // Nível 2: busca BOM do INTERMEDIATE no mesmo Map (sem query adicional)
            List<ProductComponent> level2 = bomByParent
                .getOrDefault(comp.getComponentProduct().getDynamicsCode(), List.of());
            if (level2.isEmpty()) continue;

            for (ProductComponent sub : level2) {
                if (sub.getComponentProduct().getType() == ProductType.RAW_MATERIAL) {
                    double combinedQty = suggestedQty * comp.getQuantity() * sub.getQuantity();
                    needs.add(new CalculationOutput.PurchaseNeed(
                        sub.getComponentProduct().getDynamicsCode(),
                        sub.getComponentProduct().getName(),
                        (int) Math.ceil(combinedQty),
                        sub.getUnit() != null ? sub.getUnit() : "UN"
                    ));
                } else {
                    // Nível 3+ ignorado no MVP
                    log.warn("BOM com profundidade > 2 detectado para {}: componente {} ignorado",
                        product.getDynamicsCode(), sub.getComponentProduct().getDynamicsCode());
                }
            }
        }
    }
    return needs;
}
```

**Complexidade**: O(N₁ × N₂) por produto, onde N₁ e N₂ são o número de componentes por nível. Nenhuma query extra — o `bomByParent` é shared entre `calculate()` e `explodePurchaseNeeds()`.

---

### Decisão 3 — Spring Cache para o endpoint de visão geral

O endpoint `GET /production/overview` agrega 4 repositórios. Para evitar recálculo a cada refresh em um painel:

```java
@Service
public class GetProductionOverviewUseCase {

    @Cacheable(value = "production-overview", key = "'overview'")
    public ProductionOverviewDto getOverview() { ... }
}
```

- Cache configurado com TTL de 5 minutos via `CaffeineCacheManager` (já presente no projeto)
- Evict manual não necessário (dado muda com nova importação — aceitável staleness de 5 min)
- Alternativa descartada: polling SSE — overkill para um painel de gestão sem SLA de tempo real

---

### Decisão 4 — CSV Formula Injection: prefixo `\t` (tab)

```java
// GetPlanningSummaryUseCase.escapeCsv() — extensão SEC-119
private static final Set<Character> FORMULA_PREFIXES = Set.of('=', '+', '-', '@');

private String escapeCsv(String value) {
    if (value == null) return "";
    // SEC-119: neutraliza formula injection prefixando tab (inerte no Excel, quebra parse de fórmula)
    if (!value.isEmpty() && FORMULA_PREFIXES.contains(value.charAt(0))) {
        value = "\t" + value;
    }
    if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
}
```

Alternativa descartada: prefixo `'` (apóstrofo) — visível na célula ao exportar para outros formatos. Tab é invisível e universalmente aceito.

---

### Decisão 5 — WebhookUrlValidatorTest: isolamento via JUnit 5 @Tag

```java
@Tag("requires-network")
class WebhookUrlValidatorTest { ... }
```

Configuração surefire para CI (pom.xml profile `ci`):
```xml
<configuration>
    <excludedGroups>requires-network</excludedGroups>
</configuration>
```

Execução local completa (sem exclusão): `./mvnw test`  
Execução CI (sem DNS): `./mvnw test -Pci`

---

### Contrato de API

| Método | Endpoint | Auth | Status HTTP | Response |
|--------|----------|------|-------------|----------|
| GET | `/api/v1/production/overview` | SUPERVISOR+ | 200 | `ProductionOverviewDto` |

```
ProductionOverviewDto {
  bomCoverage: {
    totalFinishedProducts: int,
    withBom: int,
    withoutBom: int,
    coveragePct: Double (null se totalFinishedProducts = 0)
  },
  mrpFulfillment: {
    totalSuggestions: int,
    accepted: int,
    rejected: int,
    pending: int,
    fulfillmentPct: Double (null se total - rejected = 0)
  },
  efficiencyTrend: List<{ date: LocalDate, avgEfficiency: Double }>,
  opsByStatus: Map<String, Long>
}
```

---

### Consequências

✅ BOM nível 2 reutiliza `bomByParent` sem queries adicionais — O(0) impacto no MRP run
✅ Cache de 5 min no overview evita recálculo desnecessário em painel com múltiplos usuários
✅ SEC-119 corrigida com mínima mudança cirúrgica em `escapeCsv()`
✅ WebhookUrlValidatorTest estabilizado sem remover o teste (preserva cobertura quando DNS disponível)
✅ Visão executiva única reduz navegação para gestores (UX)

⚠️ BOM nível 3+ silenciosamente ignorado — log de warning adicionado; se clientes tiverem BOMs com 3+ níveis, precisará de refatoração para explosão recursiva (Sprint 35+)
⚠️ `Spring Cache (Caffeine)` introduz eventual consistency no painel: dados podem ter até 5 min de defasagem após nova importação
⚠️ `findDailyEfficiency()` query usa `CAST(updatedAt AS LocalDate)` — comportamento pode variar entre H2 (testes) e PostgreSQL (produção); testar ambos antes do deploy
⚠️ `npm audit fix` pode atualizar versões transitivas — verificar que nenhum comportamento de teste muda após a atualização
