## ADR-046: Sprint 35 — Spring Cache Caffeine + NgxCharts + Tech Debt Security

**Status**: Aprovado
**Data**: 2026-06-01
**US relacionadas**: US-107, US-108, US-109

---

### Contexto

Sprint 35 resolve três lacunas identificadas no Sprint 34:

1. **US-107**: `GetProductionOverviewUseCase.getOverview()` agrega 4 repositórios a cada request. ADR-045 Decisão 3 previu cache Caffeine TTL 5 min, diferido por falta de `CaffeineCacheManager`. Com múltiplos gestores abrindo o painel simultaneamente, o custo de recálculo é multiplicado sem necessidade — os dados mudam somente após nova importação.

2. **US-108**: US-104 entregou tabela HTML como equivalente funcional ao gráfico `NgxChartsLineChartComponent` (AC-7), pois `@swimlane/ngx-charts` não estava instalado. A tabela é funcional mas não oferece a visualização de tendência solicitada pelo PO.

3. **US-109**: Três achados de segurança sem sprint definida — SEC-112 (stack trace em response), SEC-113 (campo `importedBy` exposto), SEC-069 (null check morto) — acumulados desde Sprints anteriores.

---

### Decisão 1 — Spring Cache Caffeine: configuração centralizada em `common/`

Criar bean `CaffeineCacheManager` em `common/config/CacheConfig.java`. Não usar `application.properties` para definir caches (dificulta testes de unidade com `@SpyBean`).

```java
// common/config/CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100));
        return manager;
    }
}
```

Anotação no use case:

```java
// GetProductionOverviewUseCase.java
@Cacheable(value = "production-overview", key = "'overview'")
public ProductionOverviewDto getOverview() { ... }
```

**Key fixa `'overview'`**: o endpoint não tem parâmetros; uma única entrada de cache é suficiente. Evict manual não necessário — staleness de até 5 min é aceitável para um painel executivo.

**Teste de cache**:

```java
@ExtendWith(MockitoExtension.class)
class GetProductionOverviewUseCaseCacheTest {

    @Test
    void secondCall_shouldNotInvokeRepositories_whenCacheIsActive() {
        // Chamar getOverview() duas vezes via Spring proxy
        // verify(productRepository, times(1)).findAll() — não 2 vezes
    }
}
```

> Nota: testes de cache com `@Cacheable` requerem contexto Spring (não Mockito puro). Usar `@SpringBootTest` com `@SpyBean` para os 4 repositórios e verificar que a segunda chamada não dispara novas queries.

Alternativa descartada: `@CacheEvict` em `ImportProductionOrdersUseCase` — acoplamento desnecessário; a defasagem de 5 min é aceitável.

---

### Decisão 2 — NgxCharts: instalação e integração OnPush

#### Versão e instalação

`@swimlane/ngx-charts` não tem suporte oficial declarado para Angular 21 ainda. Usar `--legacy-peer-deps` na instalação. Testar a build antes de commitar.

```bash
npm install @swimlane/ngx-charts --legacy-peer-deps
```

#### Configuração do componente

`NgxChartsLineChartModule` exporta `LineChartComponent` como standalone. Importar diretamente no componente:

```typescript
// production-overview.component.ts
import { LineChartComponent } from '@swimlane/ngx-charts';

@Component({
  imports: [LineChartComponent, ...],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductionOverviewComponent {

  readonly chartData = computed(() => [{
    name: 'Eficiência',
    series: this.trendRows().map(r => ({
      name: r.date,         // string ISO no eixo X
      value: r.avgEfficiency,
    })),
  }]);
}
```

Template:

```html
<ngx-charts-line-chart
  [results]="chartData()"
  [xAxis]="true"
  [yAxis]="true"
  [yScaleMin]="0"
  [yScaleMax]="100"
  [animations]="false"
  [referenceLines]="[{ value: 80, name: 'Meta 80%' }]"
  [showRefLines]="true"
  [autoScale]="false">
</ngx-charts-line-chart>
```

**`[animations]="false"` é obrigatório** — NgxCharts usa `BrowserAnimationsModule` internamente; com `OnPush`, animações causam detecção de mudança não disparada → gráfico estático ou corrompido.

**Fallback acessível**: manter tabela HTML com `class="sr-only"` para leitores de tela (WCAG AA). O gráfico SVG não é acessível por padrão.

```html
<table class="sr-only" aria-label="Dados de eficiência por dia">
  @for (row of trendRows(); track row.date) {
    <tr><td>{{ row.date }}</td><td>{{ row.avgEfficiency }}%</td></tr>
  }
</table>
```

Alternativa descartada: Chart.js via `ng2-charts` — dependência adicional sem vantagem; NgxCharts já é escolha do PO.

---

### Decisão 3 — SEC-112: exceções completas no log, mensagem genérica no response

Padrão já estabelecido em ADR-044 (SEC-108): catch com mensagem genérica + log com exceção completa.

```java
// Antes (problemático — vaza stack trace ou mensagem interna via getMessage())
} catch (Exception e) {
    log.warn("Erro ao processar item {}: {}", code, e.getMessage());
    errors++;
}

// Depois (correto — detalhe no log, genérico no response)
} catch (Exception e) {
    log.warn("Erro ao processar item {}: {}", code, e.getMessage(), e); // stack trace no log
    errors++;  // response nunca recebe e.getMessage()
}
```

Os 5 use cases a corrigir são identificados pelo achado SEC-112 de Beatriz. A mudança é cirúrgica: adicionar `, e` ao `log.warn` e garantir que nenhum response body receba `e.getMessage()` diretamente.

---

### Decisão 4 — SEC-113: campo `importedBy` removido de DTOs públicos

Padrão estabelecido em ADR-029 (SEC-109): `importedBy` é campo de auditoria, visível apenas em endpoints ADMIN.

```java
// CycleTimeResponse (ou DTO equivalente) — antes
public record CycleTimeResponse(String productCode, double cycleTime, String importedBy) {}

// Depois — remover importedBy do record público
public record CycleTimeResponse(String productCode, double cycleTime) {}
```

Se o dado de auditoria for necessário, criar endpoint separado `/admin/cycle-times/audit` com `@PreAuthorize("hasRole('ADMIN')")`.

---

### Decisão 5 — SEC-069: null check morto de Principal

O `Principal` não pode ser null em endpoints com `@PreAuthorize` ou em endpoints com `SecurityContextHolder` ativo. Um null check morto (`if (principal == null) return`) mascara um possível erro de configuração de segurança.

Abordagem:
- Se o endpoint **deve ser autenticado**: remover null check, deixar Spring Security rejeitar com 401
- Se o endpoint **pode ser público**: adicionar `permitAll()` na `SecurityConfig` e documentar a intenção explicitamente com comentário

Não criar nova camada de null-safety no código de negócio para compensar configuração de segurança ausente.

---

### Contrato de API

Nenhum endpoint novo. US-107 e US-109 são mudanças internas. US-108 é mudança de frontend.

Endpoint existente afetado por US-107:

| Método | Endpoint | Auth | Comportamento |
|--------|----------|------|---------------|
| GET | `/api/v1/production/overview` | SUPERVISOR+ | Cache TTL 5 min adicionado; resposta idêntica |

---

### Consequências

✅ `@Cacheable` no overview elimina recálculo desnecessário — 4 `findAll()` chamados apenas 1×/5 min por instância
✅ NgxCharts entrega a visualização de tendência solicitada pelo PO (AC-7 de US-104 completo)
✅ Fallback tabela sr-only mantém acessibilidade WCAG AA
✅ SEC-112/113/069 liquidam backlog de segurança acumulado desde Sprints 26–30
✅ `common/config/CacheConfig` centraliza toda configuração de cache — escalável para futuros casos

⚠️ NgxCharts com `--legacy-peer-deps` pode conflitar com futuras atualizações do Angular; monitorar em cada `npm update`
⚠️ `@Cacheable` com `@SpringBootTest` em testes é mais lento que Mockito puro; usar `@DirtiesContext` se necessário para isolar testes de cache
⚠️ Remover `importedBy` de DTO público (SEC-113) é breaking change para qualquer cliente que consuma esse campo — verificar se há integração externa antes de remover
⚠️ `CaffeineCacheManager` sem `@CacheEvict` significa que dados do painel podem ter até 5 min de defasagem após nova importação via Dynamics; aceitável para uso gerencial
