## ADR-026: OEE Benchmarking — Comparativo entre Trabalhadores, Turnos e Equipamentos
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-075, US-076

### Contexto

O dashboard de OEE mostra a evolução temporal mas não permite comparar performance entre diferentes dimensões: qual turno tem melhor OEE? qual trabalhador apresenta maior variabilidade? qual tipo de equipamento gera mais paradas? O benchmarking responde essas perguntas sem exigir novas entidades — agrega os dados já existentes.

---

### Decisão 1 — Endpoints de benchmarking (sem entidades novas)

Todos os endpoints calculam médias e rankings em Java sobre os dados já persistidos.

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/analytics/oee/benchmark/workers | SUPERVISOR+ | OEE médio por trabalhador |
| GET | /api/v1/analytics/oee/benchmark/shifts | SUPERVISOR+ | OEE médio por turno |
| GET | /api/v1/analytics/oee/benchmark/equipment-type | SUPERVISOR+ | OEE médio por tipo de equipamento |
| GET | /api/v1/analytics/oee/benchmark/period-comparison | SUPERVISOR+ | comparação mês atual vs mês anterior |

Todos suportam `?from=<date>&to=<date>` e `?plantId=<uuid>` (após Sprint 21).

---

### Decisão 2 — Formato de resposta de benchmark

```java
// Resposta genérica para rankings
public record BenchmarkEntry(
    String dimension,    // nome do trabalhador, do turno, do tipo
    Double oeeAvg,       // 0.0–1.0
    Integer sampleCount, // número de importações
    Double stdDev        // desvio padrão (null se sampleCount < 3)
) {}

public record BenchmarkResponse(
    List<BenchmarkEntry> ranking,   // ordenado DESC por oeeAvg
    BenchmarkEntry best,
    BenchmarkEntry worst,
    Double overallAvg
) {}
```

`stdDev` calculado em Java com `DoubleSummaryStatistics` + `Math.sqrt(variance)`.

---

### Decisão 3 — Comparação de períodos

`period-comparison` retorna dois conjuntos de dados: `current` (período solicitado) e `previous` (mesmo intervalo imediatamente anterior):

```java
public record PeriodComparisonResponse(
    TimeSeriesResponse current,
    TimeSeriesResponse previous,
    Double improvementPct  // (current.avg - previous.avg) / previous.avg * 100
) {}
```

---

### Decisão 4 — Frontend

- Nova aba "Benchmark" na rota `/analytics/oee`
- Bar chart horizontal: ranking de trabalhadores por OEE (ordena DESC)
- Bar chart: ranking por turno (requer Sprint 17 — `shiftId` no `ImportBatch`)
- Cards "Melhor" e "Pior" com destaque visual
- Comparação de período: dois line charts sobrepostos (atual em azul, anterior em cinza)
- Toggle "Normalizar para OEE de classe mundial (85%)" adiciona linha de referência

---

### Consequências
✅ Zero entidades novas — agrega dados existentes em Java
✅ `stdDev` expõe variabilidade — útil para identificar inconsistências por turno ou trabalhador
⚠️ Benchmark por turno depende de ADR-016 (Sprint 17) — só funciona para dados criados após Sprint 17
⚠️ Para > 500k registros de `time_record`, agregação Java pode ser lenta — limite de `?from/to` de 90 dias obrigatório, validado no use case
