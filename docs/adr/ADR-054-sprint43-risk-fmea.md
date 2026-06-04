## ADR-054: Sprint 43 — Gestão de Risco / FMEA

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-127, US-128, US-129

---

### Contexto

ISO 14971 exige que fabricantes de dispositivos médicos identifiquem perigos, estimem e avaliem riscos, implementem medidas de controle e monitorem a eficácia dessas medidas ao longo do ciclo de vida do produto. A técnica FMEA (Failure Mode and Effects Analysis) é o método padrão da indústria para essa análise.

O Industrial Hub possui NCs (`qms/`), CAPAs (`qms/`), Auditorias (`qms/audit/`), mas nenhum módulo de gestão de risco formal. Este sprint adiciona `qms/risk/` cobrindo:

1. **US-127** — Registro de itens de risco FMEA com ações de mitigação
2. **US-128** — Matriz de risco 10×10 e rastreabilidade NC-Risco
3. **US-129** — Frontend: cadastro FMEA, matriz visual e painel de riscos

---

### Decisão 1 — Sub-pacote `qms/risk/` como subdomínio de QMS

Gestão de risco é uma capacidade do SGQ (Quality Management System). Colocar em `qms/risk/` segue o padrão de `qms/audit/` (ADR-053) e `qms/ged/` (ADR-047):

```
com.industrialhub.backend.qms.risk/
├── domain/
│   ├── RiskItem.java
│   ├── RiskMitigationAction.java
│   ├── RiskLevel.java          (enum: LOW, MEDIUM, HIGH, CRITICAL)
│   ├── RiskStatus.java         (enum: IDENTIFIED, BEING_MITIGATED, MITIGATED, ACCEPTED)
│   └── MitigationStatus.java   (enum: PLANNED, IN_PROGRESS, COMPLETED)
├── application/
│   ├── dto/
│   │   ├── RiskItemResponse.java
│   │   ├── RiskItemSummary.java
│   │   ├── RiskMitigationActionResponse.java
│   │   ├── RiskMatrixResponse.java
│   │   └── RiskSummary.java
│   └── usecase/
│       ├── CreateRiskItemUseCase.java
│       ├── GetRiskListUseCase.java
│       ├── GetRiskDetailUseCase.java
│       ├── UpdateRiskItemUseCase.java
│       ├── TransitionRiskStatusUseCase.java
│       ├── AddMitigationActionUseCase.java
│       ├── UpdateMitigationActionUseCase.java
│       ├── GetRiskMatrixUseCase.java
│       └── GetRiskSummaryUseCase.java
├── infrastructure/
│   ├── RiskItemRepository.java
│   └── RiskMitigationActionRepository.java
└── presentation/
    └── RiskController.java    — @RestController @RequestMapping("/api/v1/qms/risks")
```

---

### Decisão 2 — Entidade `RiskItem` com RPN calculado e persistido

```java
// qms/risk/domain/RiskItem.java
@Entity
@Table(name = "risk_item",
    indexes = {
        @Index(name = "idx_risk_status",     columnList = "status"),
        @Index(name = "idx_risk_level",      columnList = "risk_level"),
        @Index(name = "idx_risk_linked_nc",  columnList = "linked_nc_id")
    })
public class RiskItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String process;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String failureMode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String failureEffect;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String failureCause;

    @Column(nullable = false)
    @Min(1) @Max(10)
    private Integer severity;

    @Column(nullable = false)
    @Min(1) @Max(10)
    private Integer occurrence;

    @Column(nullable = false)
    @Min(1) @Max(10)
    private Integer detectability;

    @Column(nullable = false)
    private Integer rpn;               // calculado e persistido: severity × occurrence × detectability

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RiskLevel riskLevel;       // calculado e persistido pelo use case

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskStatus status;

    @Column(nullable = false, length = 100)
    private String owner;

    private UUID linkedNcId;           // FK leve — validada no use case
    private UUID linkedProductCode;    // pode ser null

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Método de domínio
    public void recalculateRpn() {
        this.rpn = this.severity * this.occurrence * this.detectability;
        this.riskLevel = RiskLevel.fromRpn(this.rpn);
    }
}
```

**RPN calculado e persistido** (não coluna gerada pelo banco): permite queries com `ORDER BY rpn DESC` e filtros `WHERE risk_level = 'CRITICAL'` eficientemente, sem coluna calculada SQL. A responsabilidade de recalcular RPN fica no método de domínio `recalculateRpn()` — chamado a cada criação e atualização.

```java
// qms/risk/domain/RiskLevel.java
public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static RiskLevel fromRpn(int rpn) {
        if (rpn <= 30)  return LOW;
        if (rpn <= 100) return MEDIUM;
        if (rpn <= 200) return HIGH;
        return CRITICAL;
    }
}
```

Thresholds: `LOW ≤ 30`, `MEDIUM 31–100`, `HIGH 101–200`, `CRITICAL > 200`. Estes thresholds são constantes de domínio — se precisarem mudar, o `RiskLevel.fromRpn()` é o único ponto de alteração.

---

### Decisão 3 — `RiskMitigationAction` com RPN residual

```java
// qms/risk/domain/RiskMitigationAction.java
@Entity
@Table(name = "risk_mitigation_action",
    indexes = @Index(name = "idx_mitigation_risk", columnList = "risk_item_id"))
public class RiskMitigationAction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_item_id", nullable = false)
    private RiskItem riskItem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String responsible;

    private LocalDate targetDate;
    private LocalDate completedAt;

    @Min(1) @Max(10) private Integer residualSeverity;
    @Min(1) @Max(10) private Integer residualOccurrence;
    @Min(1) @Max(10) private Integer residualDetectability;
    private Integer residualRpn;       // calculado quando COMPLETED com residuais fornecidos

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MitigationStatus status;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

**`residualRpn` calculado no use case** ao transitar para `COMPLETED` quando os três componentes residuais são informados:

```java
// UpdateMitigationActionUseCase.java
if (req.status() == MitigationStatus.COMPLETED
        && req.residualSeverity() != null
        && req.residualOccurrence() != null
        && req.residualDetectability() != null) {
    action.setResidualRpn(
        req.residualSeverity() * req.residualOccurrence() * req.residualDetectability());
}
```

---

### Decisão 4 — State machine de `RiskStatus`

```
IDENTIFIED ──────► BEING_MITIGATED
BEING_MITIGATED ─► MITIGATED
MITIGATED ───────► ACCEPTED

CRITICAL → ACCEPTED bloqueado sem mitigação com residualRpn ≤ 100
```

Regra especial para `CRITICAL → ACCEPTED`:

```java
// TransitionRiskStatusUseCase.java
if (risk.getRiskLevel() == RiskLevel.CRITICAL && newStatus == RiskStatus.ACCEPTED) {
    // Verifica se existe ao menos uma mitigação COMPLETED com residualRpn ≤ 100
    boolean hasValidMitigation = mitigationRepository
        .existsByRiskItemIdAndStatusAndResidualRpnLessThanEqual(
            risk.getId(), MitigationStatus.COMPLETED, 100);
    if (!hasValidMitigation) {
        throw new IllegalStateException(
            "Riscos críticos devem ser mitigados antes de aceitar " +
            "(residualRpn ≤ 100 exigido)");
    }
}
```

Esta regra implementa o requisito ISO 14971 §6: riscos inaceitáveis devem ter medidas de controle efetivas antes de serem aceitos.

---

### Decisão 5 — Matriz de risco 10×10 calculada no use case

A matriz de risco é um grid `severity (1–10) × occurrence (1–10)`. Apenas riscos com `status IN (IDENTIFIED, BEING_MITIGATED)` aparecem na matriz — riscos `MITIGATED` e `ACCEPTED` não são exibidos:

```java
// GetRiskMatrixUseCase.java
@Service
@Transactional(readOnly = true)
public class GetRiskMatrixUseCase {

    public RiskMatrixResponse execute() {
        List<RiskMatrixProjection> active = riskRepository.findActiveForMatrix();

        // Grid 10×10
        Map<String, RiskMatrixCell> cells = new HashMap<>();
        for (RiskMatrixProjection r : active) {
            String key = r.getSeverity() + "x" + r.getOccurrence();
            cells.computeIfAbsent(key, k -> new RiskMatrixCell(
                r.getSeverity(), r.getOccurrence(),
                RiskLevel.fromRpn(r.getSeverity() * r.getOccurrence() * 5) // detectability=5 para coloração
            )).incrementCount();
        }

        return new RiskMatrixResponse(cells.values().stream().toList());
    }
}
```

**Cor da célula** usa `severity × occurrence × 5` (detectability fixo em 5) para fins de visualização da matriz — a cor representa o nível de risco inerente (sem detectabilidade), prática padrão em matrizes de risco ISO 14971.

**Projeção leve**:
```java
public interface RiskMatrixProjection {
    Integer getSeverity();
    Integer getOccurrence();
}
```

---

### Decisão 6 — `GET /non-conformances/{ncId}/risks` adicionado ao `QmsController`

A rastreabilidade NC-Risco é uma query de leitura sobre `RiskItem.linkedNcId`. O endpoint é adicionado ao `QmsController` existente (não ao `RiskController`) — é um sub-recurso da NC, análogo a `/{ncId}/documents` e `/{ncId}/rca`:

```java
// QmsController.java — endpoint adicional
@GetMapping("/{ncId}/risks")
@PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
public List<RiskItemSummary> getRisksLinkedToNc(@PathVariable UUID ncId) {
    return getRisksLinkedToNcUseCase.execute(ncId);
}
```

O `NcResponse` de detalhe (`GET /non-conformances/{id}`) inclui `linkedRisks: List<RiskItemSummary>` — campo adicionado no Sprint 43, nullable para retrocompatibilidade.

---

### Contrato de API — Sprint 43

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | `/api/v1/qms/risks` | SUPERVISOR+ | 201 | Cria item de risco FMEA |
| GET | `/api/v1/qms/risks` | OPERATOR+ | 200 | Lista paginada por RPN DESC |
| GET | `/api/v1/qms/risks/{id}` | OPERATOR+ | 200 | Detalhe com mitigações |
| PUT | `/api/v1/qms/risks/{id}` | SUPERVISOR+ | 200 | Atualiza FMEA; recalcula RPN |
| PUT | `/api/v1/qms/risks/{id}/status` | SUPERVISOR+ | 200 | Transição de status |
| POST | `/api/v1/qms/risks/{id}/mitigation-actions` | SUPERVISOR+ | 201 | Adiciona ação de mitigação |
| PUT | `/api/v1/qms/risks/{id}/mitigation-actions/{actionId}` | SUPERVISOR+ | 200 | Atualiza ação |
| GET | `/api/v1/qms/risks/matrix` | SUPERVISOR+ | 200 | Matriz 10×10 |
| GET | `/api/v1/qms/risks/summary` | SUPERVISOR+ | 200 | Resumo com top 5 riscos |
| GET | `/api/v1/qms/non-conformances/{ncId}/risks` | OPERATOR+ | 200 | Riscos vinculados a NC |

---

### Consequências

✅ RPN calculado como método de domínio `recalculateRpn()` na entidade — único ponto de cálculo, testável unitariamente sem contexto JPA
✅ `RiskLevel.fromRpn()` como factory method do enum — thresholds centralizados, mudança em um único lugar
✅ Regra `CRITICAL → ACCEPTED` bloqueada sem mitigação válida — implementa ISO 14971 §6 de aceitação de risco somente após controle efetivo
✅ Matriz 10×10 calculada no use case com projeção leve — sem carregar entidades completas; lógica de cor da célula separada da lógica de RPN do registro
✅ Endpoint NC↔Risco em `QmsController` — sub-recurso de NC, consistente com `/{ncId}/documents` e `/{ncId}/rca`
✅ Sub-pacote `qms/risk/` com `RiskController` dedicado — SRP, mesma organização de `qms/audit/` e `qms/ged/`

⚠️ RPN persistido no banco pode ficar desatualizado se thresholds de `RiskLevel.fromRpn()` mudarem sem re-calcular registros existentes — qualquer mudança nos thresholds exige migration de `UPDATE risk_item SET risk_level = ...`
⚠️ `linkedNcId` como FK leve — deletar uma NC não limpa o vínculo em `RiskItem`; registros órfãos são possíveis (mesmo risco documentado em ADR-050/052/053 para padrão FK leve)
⚠️ `GET /qms/risks` paginado com `ORDER BY rpn DESC` sem índice em `rpn` — adicionar `@Index(columnList = "rpn")` na migration para suportar ordenação eficiente em volume maior
⚠️ Frontend com sliders 1–10 para S/O/D pode ser UX complexo — garantir que o preview de RPN ao vivo seja suficientemente claro para evitar erro de input
