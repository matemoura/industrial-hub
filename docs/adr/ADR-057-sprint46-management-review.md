## ADR-057: Sprint 46 — Análise Crítica pela Direção

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-135, US-136

---

### Contexto

ISO 13485 §5.6 exige que a Alta Direção realize análises críticas do SGQ em intervalos planejados para assegurar que o sistema continua adequado, suficiente e eficaz. A análise crítica deve incluir avaliação de oportunidades de melhoria e necessidade de mudanças no SGQ, incluindo política e objetivos da qualidade.

O Sprint 46 é o sprint de encerramento do SGQ. Após os Sprints 40–45, todos os módulos de conformidade estão operacionais (Treinamentos, Calibração, Auditorias, FMEA/Risco, Mudanças, Reclamações). Este sprint consolida todos os indicadores em:

1. **US-135** — Backend: endpoint de indicadores agregados com cache 30 min e PDF para direção
2. **US-136** — Frontend: dashboard executivo com `SemaphoreChipComponent` e exportação PDF

**Dependências críticas**: US-135 depende de todos os Sprints 40–45 — é o único sprint do projeto com dependência de todos os módulos de domínio.

---

### Decisão 1 — `ManagementReviewController` em `common/presentation/`

O módulo de análise crítica é transversal a todos os domínios — não pertence a nenhum sub-módulo específico. Localização: `common/presentation/` (mesma de `KpiController`, `AlertThresholdController`, `AnalyticsController`):

```
com.industrialhub.backend.common.presentation/
├── KpiController.java               (Sprint 9)
├── AlertThresholdController.java    (Sprint 18)
├── AnalyticsController.java         (Sprint 16)
└── ManagementReviewController.java  (Sprint 46 — novo)
    — @RequestMapping("/api/v1/management-review")
```

```
com.industrialhub.backend.common.application.usecase/
├── GetKpiSummaryUseCase.java         (Sprint 9)
├── GetManagementReviewDataUseCase.java  (Sprint 46 — novo)
└── GenerateManagementReviewPdfUseCase.java  (Sprint 46 — novo)
```

`GetManagementReviewDataUseCase` é o use case de maior número de dependências no projeto — injeta repositórios ou use cases de todos os módulos de domínio. Esta é uma agregação explícita de dados (sem nova entidade JPA).

---

### Decisão 2 — Sem nova entidade JPA: `ManagementReviewData` é um DTO calculado

Não há tabela `management_review` no banco. A análise crítica é gerada sob demanda — o resultado é calculado a partir dos dados existentes nos módulos. Um registro histórico poderia ser útil, mas adiciona complexidade sem demanda imediata (implementar em sprint futura se necessário):

```java
// ManagementReviewData.java — DTO record (não entidade JPA)
public record ManagementReviewData(
    LocalDate from,
    LocalDate to,
    NcSummaryData ncSummary,
    CapaSummaryData capaSummary,
    ComplaintSummaryData complaintSummary,
    AuditSummaryData auditSummary,
    CalibrationSummaryData calibrationSummary,
    TrainingSummaryData trainingSummary,
    RiskSummaryData riskSummary,
    ChangeSummaryData changeSummary,
    KpiSummaryData kpiSummary
) {}
```

Cada `*SummaryData` é um record aninhado com os campos específicos do módulo — estrutura fechada e tipada, sem `Map<String, Object>`.

---

### Decisão 3 — Cache Caffeine 30 min para `ManagementReviewData`

`GetManagementReviewDataUseCase` é a consulta mais pesada do sistema — agrega dados de 9+ módulos. Cache com TTL 30 min é adequado para reuniões de análise crítica (que tipicamente duram 1-2h):

```java
// GetManagementReviewDataUseCase.java
@Service
@Transactional(readOnly = true)
public class GetManagementReviewDataUseCase {

    @Cacheable(value = "management-review", key = "#from.toString() + '-' + #to.toString()")
    public ManagementReviewData execute(LocalDate from, LocalDate to) {
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new IllegalArgumentException(
                "Período máximo de 366 dias para análise crítica");
        }

        return new ManagementReviewData(
            from, to,
            collectNcSummary(from, to),
            collectCapaSummary(),         // aging não filtra por período — usa dados atuais
            collectComplaintSummary(from, to),
            collectAuditSummary(from, to),
            collectCalibrationSummary(),  // calibrações vencidas são ponto-no-tempo
            collectTrainingSummary(),     // compliance é ponto-no-tempo
            collectRiskSummary(from, to),
            collectChangeSummary(from, to),
            getKpiSummaryUseCase.execute() // reutiliza use case existente (Sprint 9)
        );
    }
}
```

**Cache registrado em `CacheConfig`** (ADR-046 Decisão 1) — adicionar entrada `management-review` ao bean `CacheManager` existente com TTL 30 min:

```java
// CacheConfig.java — atualização
.expireAfterWrite(30, TimeUnit.MINUTES) // para management-review
```

Implementação: configuração por `CaffeineSpec` por cache name — `CacheConfig` já usa essa abordagem para o cache de 5 min do Sprint 35. Adicionar spec separada para `management-review` com `maximumSize=10, expireAfterWrite=30m` (10 combinações de período diferentes em simultâneo é mais que suficiente para 53 usuários).

---

### Decisão 4 — Thresholds de semáforo no `ManagementReviewPdfRenderer`

Os thresholds de semáforo (verde/âmbar/vermelho) são constantes de domínio definidas no renderer e reutilizadas no frontend via `SemaphoreChipComponent`:

| Indicador | Verde | Âmbar | Vermelho |
|-----------|-------|-------|----------|
| NCs críticas abertas | = 0 | 1–3 | > 3 |
| CAPAs vencidas | = 0 | 1–5 | > 5 |
| Calibrações vencidas | = 0 | — | > 0 |
| Compliance treinamentos (%) | > 90% | 75–90% | < 75% |
| Riscos críticos abertos | = 0 | — | > 0 |
| OEE 30 dias (%) | ≥ 65% | 50–64% | < 50% |

```java
// ManagementReviewPdfRenderer.java — helper de semáforo
private SemaphoreStatus ncCriticalStatus(int criticalOpen) {
    if (criticalOpen == 0)   return SemaphoreStatus.GREEN;
    if (criticalOpen <= 3)   return SemaphoreStatus.AMBER;
    return SemaphoreStatus.RED;
}

private Color semaphoreColor(SemaphoreStatus status) {
    return switch (status) {
        case GREEN -> new DeviceRgb(0x3F, 0xA6, 0x6A); // #3FA66A
        case AMBER -> new DeviceRgb(0xE8, 0xA9, 0x3C); // #E8A93C
        case RED   -> new DeviceRgb(0xD2, 0x4A, 0x4A); // #D24A4A
    };
}
```

**Cores do semáforo alinhadas ao CLAUDE.md**: `ok = #3FA66A`, `warn = #E8A93C`, `danger = #D24A4A` — identidade visual MSB aplicada ao PDF.

---

### Decisão 5 — `SemaphoreChipComponent` Angular reutilizável

`SemaphoreChipComponent` é um componente standalone `OnPush` novo, reutilizável em todas as seções do dashboard:

```typescript
// shared/semaphore-chip/semaphore-chip.component.ts
@Component({
  selector: 'app-semaphore-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="semaphore-chip" [class]="status()">
      <mat-icon>{{ iconName() }}</mat-icon>
      <ng-content></ng-content>
    </span>
  `
})
export class SemaphoreChipComponent {
  readonly status = input.required<'green' | 'amber' | 'red'>();
  readonly iconName = computed(() => {
    switch (this.status()) {
      case 'green': return 'check_circle';
      case 'amber': return 'warning';
      case 'red':   return 'error';
    }
  });
}
```

Localização: `apps/frontend/src/app/shared/semaphore-chip/` — componente genérico de utilidade, não acoplado ao módulo de análise crítica. Pode ser reutilizado em outros dashboards futuros (e.g., dashboard de qualidade do Sprint 39).

---

### Decisão 6 — PDF da Análise Crítica: estrutura consolidada

O PDF é o documento formal para reunião com a Alta Direção. Estrutura:

```
1. CAPA
   - Logo MSB (wordmark)
   - "ANÁLISE CRÍTICA PELA DIREÇÃO — ISO 13485 §5.6"
   - Período: {from} a {to}
   - Data de emissão
   - Gerado por: {username}

2. SUMÁRIO EXECUTIVO
   - Tabela de indicadores com semáforo visual por área

3. SEÇÃO POR MÓDULO (ordem determinística):
   3.1 Qualidade — NCs, CAPAs, Reclamações
   3.2 Conformidade Operacional — Calibrações, Treinamentos, Auditorias
   3.3 Gestão de Risco — FMEA/Riscos, Mudanças
   3.4 Produção — OEE, OS abertas

4. RODAPÉ
   - "Industrial Hub — MSB — Confidencial — Página N de M"
```

`ManagementReviewPdfRenderer` em `common/application/service/`. Este é o quinto renderer PDF do projeto — a oportunidade de extrair `AbstractMsbPdfRenderer` com métodos `addMsbHeader()`, `addSectionTitle()`, `addFooterWithPageNumbers()`, `addTable()` está clara após os Sprints 39–46. Registrado como spawn task.

---

### Contrato de API — Sprint 46

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| GET | `/api/v1/management-review/indicators` | ADMIN | 200 | Dados agregados de todos os módulos |
| GET | `/api/v1/management-review/indicators/export` | ADMIN | 200 (PDF) | Relatório PDF para direção |

**ADMIN exclusivo**: análise crítica é um documento estratégico de nível diretivo. SUPERVISOR não acessa — diferente dos dashboards operacionais acessíveis a SUPERVISOR+.

O endpoint de export usa `GET` (não `POST`) — os parâmetros `from` e `to` são suficientes como query params; sem body necessário. Consistente com o padrão de export do projeto (`GET /capas/aging/export`, `GET /non-conformances/export`).

---

### Consequências

✅ `ManagementReviewData` como DTO record (sem entidade JPA) — consulta sob demanda, sem acúmulo de dados históricos desnecessários
✅ Cache Caffeine 30 min no `CacheConfig` existente — sem nova infraestrutura; TTL adequado para sessões de reunião de direção
✅ `GetKpiSummaryUseCase` reutilizado para `kpiSummary` — reúso de lógica existente (Sprint 9) sem duplicação
✅ `SemaphoreChipComponent` standalone reutilizável em `shared/` — não acoplado ao módulo de análise crítica; disponível para outros dashboards
✅ Thresholds de semáforo como constantes no renderer — único ponto de verdade para critérios de avaliação; cores alinhadas à identidade visual MSB
✅ Estrutura de PDF determinística (4 seções em ordem fixa) — relatório consistente entre execuções; auditores familiarizados saberão onde encontrar cada indicador
✅ ADMIN exclusivo para análise crítica — controle de acesso adequado ao nível estratégico do documento

⚠️ `GetManagementReviewDataUseCase` injeta dependências de todos os módulos — é o use case com maior fan-out de injeção do sistema; se um novo módulo for adicionado no futuro, este use case precisará ser atualizado; registrar como ponto de manutenção
⚠️ Cache por chave `"from-to"` — para 53 usuários com períodos idênticos (padrão: último ano), cache hit é frequente; mas cada usuário ADMIN pode definir períodos diferentes, reduzindo eficiência do cache; `maximumSize=10` suficiente para uso real
⚠️ Sem histórico de análises críticas — cada geração recalcula; gestores não podem comparar análises de períodos anteriores sem gerar PDFs e armazenar externamente; implementar persistência de `ManagementReviewSnapshot` em sprint futura se necessário
⚠️ Cinco renderers PDF no projeto (Sprint 39, 42, 43, 45, 46) sem abstração comum — proliferação de boilerplate iText 7; extração de `AbstractMsbPdfRenderer` é altamente recomendada antes do Sprint 47 (registrado como spawn task separado)
⚠️ `GenerateManagementReviewPdfUseCase` chama `GetManagementReviewDataUseCase.execute()` — sem cache no PDF (cache só na chamada de `indicators`); para gerar PDF em sessão diferente do carregamento dos dados, a query é re-executada; avaliar aceitar esse comportamento ou passar o `ManagementReviewData` diretamente ao endpoint de export
