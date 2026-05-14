## ADR-012: Advanced Analytics & Reports — Trend Charts e Exportação
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-043, US-044, US-045

### Contexto

O sistema atualmente expõe apenas resumos estáticos (totais) e exportação CSV simples. Gestores precisam de visão temporal: evolução do OEE semana a semana, pareto de causas de NCs, tendência de MTTR mensal. Esta ADR define como o backend agrega e expõe dados de série temporal e como o frontend os renderiza.

---

### Decisão 1 — Estratégia de agregação: Java para tabelas pequenas, JPQL para `time_record`

Seguindo ADR-009 Decisão 6:

| Dados | Estratégia | Justificativa |
|-------|-----------|---------------|
| OEE por semana (time_record) | JPQL GROUP BY `WEEK(periodDate)` | Tabela pode ter > 10k rows |
| NC por tipo/severidade (non_conformance) | Java stream | Tabela pequena |
| MTTR por mês (work_order) | Java stream | Tabela pequena |

Para OEE trend, a query JPQL usa `FUNCTION('WEEK', w.periodDate)` — compatível com H2 e PostgreSQL via `YEARWEEK`. **Alternativa portável**: calcular ISO week number em Java filtrando por período e agrupando com `Collectors.groupingBy`.

**Decisão final**: usar Java-side grouping para todos os módulos (consistência com ADR-008/009, evitar SQL nativo). Buscar registros do período via JPQL simples (`findByPeriodDateBetween`) e agregar em Java.

---

### Decisão 2 — Endpoints de analytics

Todos os endpoints de analytics ficam em `common/presentation/AnalyticsController` para centralizar o acesso cross-module. Cada módulo contribui com um "analytics use case" próprio.

```
common/
└── presentation/
    └── AnalyticsController.java     (novo — /api/v1/analytics/*)

oee/application/usecase/
    └── GetOeeTrendUseCase.java      (novo)

qms/application/usecase/
    └── GetNcAnalyticsUseCase.java   (novo)

maintenance/application/usecase/
    └── GetMaintenanceAnalyticsUseCase.java  (novo)
```

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/analytics/oee/trend | SUPERVISOR+ | OEE médio semanal (últimas N semanas) |
| GET | /api/v1/analytics/nc/pareto | SUPERVISOR+ | Contagem de NCs por tipo e severidade |
| GET | /api/v1/analytics/nc/trend | SUPERVISOR+ | Novas NCs por semana (últimas N semanas) |
| GET | /api/v1/analytics/maintenance/mttr-trend | SUPERVISOR+ | MTTR médio mensal (últimos N meses) |
| GET | /api/v1/analytics/maintenance/wo-summary | SUPERVISOR+ | Distribuição de OSs por status e tipo |

Query params comuns: `weeks` (default 12, max 52) para endpoints semanais; `months` (default 6, max 24) para mensais.

---

### Decisão 3 — Formato de resposta: séries nomeadas

```java
// Resposta genérica de série temporal
public record TimeSeriesResponse(
    List<String> labels,       // ex: ["2026-W01", "2026-W02", ...]
    List<Double> values        // um por label; null onde sem dados
) {}

// Resposta de OEE trend
public record OeeTrendResponse(
    List<String> weekLabels,      // "YYYY-Www" (ISO week)
    List<Double> oeeValues,       // percentuais 0.0–1.0; null onde sem dados
    List<Integer> sampleCounts    // número de importações na semana
) {}

// Resposta de pareto de NCs
public record NcParetoResponse(
    Map<String, Long> byType,      // { "PROCESS": 12, "PRODUCT": 8, ... }
    Map<String, Long> bySeverity   // { "CRITICAL": 3, "HIGH": 10, ... }
) {}

// Resposta de MTTR trend
public record MttrTrendResponse(
    List<String> monthLabels,    // "YYYY-MM"
    List<Double> mttrValues      // horas; null onde sem OS concluídas
) {}
```

---

### Decisão 4 — Biblioteca de gráficos: ng2-charts (Chart.js)

```bash
npm install ng2-charts chart.js
```

Escolhida por:
- Wrapper oficial Angular para Chart.js (amplamente mantido)
- Tipos TypeScript nativos
- Compatível com standalone components e OnPush

Gráficos usados:
- **Line chart**: OEE trend, NC trend, MTTR trend
- **Bar chart**: pareto de NCs (por tipo e severidade)
- **Doughnut chart**: distribuição de OSs por status

Componentes de gráfico em `shared/charts/`:
```
shared/
└── charts/
    ├── line-chart.component.ts    (standalone, reutilizável — inputs: labels[], values[])
    ├── bar-chart.component.ts     (standalone — inputs: labels[], datasets[])
    └── doughnut-chart.component.ts (standalone — inputs: labels[], data[])
```

Cada componente é **genérico** — recebe dados via `input()`. A lógica de busca fica no componente pai (página de analytics).

---

### Decisão 5 — Exportação: sem PDF gerado no backend

**Sem iText/PDFBox no backend** — gera acoplamento pesado e complexidade de templates.

**Estratégia**: frontend usa `window.print()` com CSS `@media print` otimizado para imprimir a página de analytics. Alternativa futura: `jsPDF + html2canvas` como devDependency se demanda surgir.

CSVs já existentes (NCs) são mantidos. Nenhum novo endpoint CSV nesta sprint — os gráficos suprem a necessidade imediata.

---

### Decisão 6 — Rota de analytics no frontend

```
/analytics/oee         — trend OEE (line chart + tabela de semanas)
/analytics/qms         — pareto NCs (bar chart) + trend semanal (line chart)
/analytics/maintenance — MTTR mensal (line chart) + distribuição OSs (doughnut)
```

Link "Analytics" adicionado ao nav (visível para SUPERVISOR+). Para OPERATOR, link oculto via `*ngIf` (ou `@if`) verificando role.

---

### Consequências
✅ Java-side aggregation mantém consistência com ADR-008/009 — zero SQL nativo adicional
✅ Componentes de gráfico genéricos em `shared/charts/` — reutilizáveis em dashboard (US-030) futuramente
✅ Sem geração de PDF no backend — sem dependência pesada, sem template HTML de email a manter
✅ ng2-charts + Chart.js — maturidade comprovada, tipos TypeScript nativos
⚠️ `window.print()` gera PDF de qualidade variável por browser — aceitável para MVP; documentar limitação para usuários
⚠️ Busca `findByPeriodDateBetween` com 52 semanas pode retornar muitos records; adicionar `@PageableDefault` ou limitar `weeks` a 52 no use case
