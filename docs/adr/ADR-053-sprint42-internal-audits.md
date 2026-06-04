## ADR-053: Sprint 42 — Auditorias Internas

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-124, US-125, US-126

---

### Contexto

ISO 13485 §8.2.4 exige que a organização conduza auditorias internas em intervalos planejados para determinar se o SGQ está conforme com os requisitos e efetivamente implementado e mantido. Os resultados das auditorias e achados devem ser registrados e vinculados a ações corretivas (CAPAs).

O Industrial Hub possui NCs (`qms/`), CAPAs (`qms/`), GED (`qms/ged/`), mas nenhum módulo formal de auditorias internas. Este sprint adiciona o sub-módulo `qms/audit/` cobrindo:

1. **US-124** — Planejamento de auditorias, checklists por processo e achados vinculáveis a NCs/CAPAs
2. **US-125** — Relatório de auditoria PDF (iText 7, disponível desde Sprint 39) e dashboard de conformidade
3. **US-126** — Frontend: lista, detalhes com checklist interativo e achados

**Colisão de nomenclatura com `common/domain/AuditLog`**: a palavra "audit" é usada tanto para trilha de auditoria de sistema (`AuditLog`) quanto para auditorias ISO do SGQ (`InternalAudit`). Os pacotes devem ser explicitamente distintos para não causar confusão.

---

### Decisão 1 — Sub-pacote `qms/audit/` para evitar colisão com `common/domain/AuditLog`

A entidade de auditoria ISO é `InternalAudit` (nome explícito). O pacote é `qms/audit/` — sub-pacote de `qms/`, não top-level. Essa decisão é consistente com `qms/ged/` (Sprint 36, ADR-047) e `qms/risk/` (Sprint 43, ADR-054):

```
com.industrialhub.backend.qms.audit/
├── domain/
│   ├── InternalAudit.java
│   ├── AuditChecklistItem.java
│   ├── AuditFinding.java
│   ├── AuditType.java          (enum: INTERNAL, SUPPLIER, PROCESS, SYSTEM)
│   ├── AuditStatus.java        (enum: PLANNED, IN_PROGRESS, COMPLETED, CANCELLED)
│   ├── ChecklistResponse.java  (enum: CONFORMING, NON_CONFORMING, OBSERVATION, NOT_APPLICABLE)
│   └── FindingType.java        (enum: NON_CONFORMANCE, OBSERVATION, OPPORTUNITY_FOR_IMPROVEMENT)
├── application/
│   ├── dto/
│   │   ├── InternalAuditResponse.java
│   │   ├── AuditChecklistItemResponse.java
│   │   ├── AuditFindingResponse.java
│   │   └── AuditComplianceDashboard.java
│   └── usecase/
│       ├── CreateInternalAuditUseCase.java
│       ├── GetAuditListUseCase.java
│       ├── GetAuditDetailUseCase.java
│       ├── TransitionAuditStatusUseCase.java
│       ├── UpdateAuditUseCase.java
│       ├── AddChecklistItemsUseCase.java
│       ├── UpdateChecklistItemUseCase.java
│       ├── AddAuditFindingUseCase.java
│       ├── DeleteAuditFindingUseCase.java
│       ├── GenerateAuditReportUseCase.java
│       └── GetAuditComplianceDashboardUseCase.java
├── infrastructure/
│   ├── InternalAuditRepository.java
│   ├── AuditChecklistItemRepository.java
│   └── AuditFindingRepository.java
└── presentation/
    └── AuditController.java      — @RestController @RequestMapping("/api/v1/qms/audits")
```

`AuditController` é um controller dedicado — segue o padrão SRP de `CapaController` (ADR-048) e `GedController` (ADR-047).

---

### Decisão 2 — Entidades do domínio de auditoria

```java
// qms/audit/domain/InternalAudit.java
@Entity
@Table(name = "internal_audit",
    uniqueConstraints = @UniqueConstraint(name = "uk_audit_code", columnNames = "code"),
    indexes = {
        @Index(name = "idx_audit_status",       columnList = "status"),
        @Index(name = "idx_audit_planned_date", columnList = "planned_date")
    })
public class InternalAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;               // auto-gerado: "AUD-{ANO}-{NNN}"

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditType auditType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status;        // PLANNED na criação

    @Column(nullable = false)
    private LocalDate plannedDate;

    private LocalDate completedDate;   // obrigatório ao transitar para COMPLETED

    @Column(nullable = false, length = 100)
    private String leadAuditor;

    @ElementCollection
    @CollectionTable(name = "audit_auditee")
    private List<String> auditees = new ArrayList<>();

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

```java
// qms/audit/domain/AuditFinding.java
@Entity
@Table(name = "audit_finding",
    indexes = @Index(name = "idx_audit_finding_audit", columnList = "audit_id"))
public class AuditFinding {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false)
    private InternalAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_item_id")
    private AuditChecklistItem checklistItem;  // nullable

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FindingType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, length = 20)
    private String isoClause;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NcSeverity severity;               // reutiliza NcSeverity de qms/domain/

    private UUID linkedNcId;                   // FK leve — validada no use case
    private UUID linkedCapaId;                 // FK leve — validada no use case

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

**`NcSeverity` reutilizado em `AuditFinding`**: a severidade do achado segue a mesma escala que a severidade de NC (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). Importar o enum de `qms/domain/` é aceitável — ambos estão no mesmo bounded context QMS. Não criar enum duplicado.

**Referências leves `linkedNcId` e `linkedCapaId`**: FK leves sem `@ManyToOne` — mesma decisão de `NcDocumentLink` e `CalibrationRecord`. Validação de existência ocorre no use case; desempenho e isolamento de módulos valem mais que FK física.

---

### Decisão 3 — State machine de auditoria

```
PLANNED ──────────────────────────────────► CANCELLED  (allowed)
PLANNED ──────► IN_PROGRESS
IN_PROGRESS ──► COMPLETED  (requer completedDate no body)
IN_PROGRESS ──► CANCELLED  (BLOQUEADO — 422)
```

Transição `IN_PROGRESS → CANCELLED` é bloqueada: uma auditoria em andamento deve ser completada (mesmo que parcialmente) para preservar os achados já registrados. Se não houve achados, o auditor pode fechar em `COMPLETED` com checklist vazio.

```java
// TransitionAuditStatusUseCase.java
public InternalAuditResponse execute(UUID auditId, AuditStatus newStatus,
                                     LocalDate completedDate, String principal) {
    InternalAudit audit = auditRepository.findById(auditId)
        .orElseThrow(() -> new AuditNotFoundException(auditId));

    AuditStatus current = audit.getStatus();
    if (!isAllowed(current, newStatus)) {
        throw new InvalidStateTransitionException(current, newStatus,
            "Transição não permitida: " + current + " → " + newStatus);
    }
    if (newStatus == AuditStatus.COMPLETED && completedDate == null) {
        throw new IllegalArgumentException("completedDate é obrigatório ao concluir auditoria");
    }
    audit.setStatus(newStatus);
    if (newStatus == AuditStatus.COMPLETED) audit.setCompletedDate(completedDate);
    auditService.log(principal, AuditAction.INTERNAL_AUDIT_STATUS_CHANGED,
        "InternalAudit", auditId, Map.of("from", current, "to", newStatus));
    return InternalAuditResponse.from(auditRepository.save(audit));
}

private boolean isAllowed(AuditStatus from, AuditStatus to) {
    return switch (from) {
        case PLANNED    -> to == AuditStatus.IN_PROGRESS || to == AuditStatus.CANCELLED;
        case IN_PROGRESS -> to == AuditStatus.COMPLETED; // CANCELLED bloqueado
        default         -> false;
    };
}
```

---

### Decisão 4 — Auto-geração de código `AUD-{ANO}-{NNN}`

Segue o mesmo padrão de `CR-{ANO}-{NNN}` (Sprint 44, ADR-055) e `REC-{ANO}-{NNN}` (Sprint 45, ADR-056). Implementação:

```java
// CreateInternalAuditUseCase.java
private String generateCode() {
    int year = LocalDate.now().getYear();
    long count = auditRepository.countByCodeStartingWith("AUD-" + year);
    return "AUD-%d-%03d".formatted(year, count + 1);
}
```

Não atômico por padrão — se dois usuários criarem simultaneamente, pode haver colisão. A unique constraint `uk_audit_code` captura a violação; o handler converte `DataIntegrityViolationException` → `409` (padrão ADR-049 §5). Em volume baixo (MSB ~53 usuários), a probabilidade é mínima.

---

### Decisão 5 — Relatório PDF via iText 7 (US-125)

iText 7 já está no `pom.xml` desde Sprint 39 (ADR-050 Decisão 6). O relatório de auditoria é gerado apenas quando `status = COMPLETED`:

```java
// GenerateAuditReportUseCase.java
@Service
@Transactional(readOnly = true)
public class GenerateAuditReportUseCase {

    public byte[] execute(UUID auditId, String principal) throws IOException {
        InternalAudit audit = auditRepository.findById(auditId)
            .orElseThrow(() -> new AuditNotFoundException(auditId));

        if (audit.getStatus() != AuditStatus.COMPLETED) {
            throw new IllegalStateException(
                "Relatório disponível apenas para auditorias COMPLETADAS");
        }

        List<AuditChecklistItem> items = checklistItemRepository.findByAuditIdOrderByOrder(auditId);
        List<AuditFinding> findings = findingRepository.findByAuditId(auditId);

        byte[] pdf = auditReportPdfRenderer.render(audit, items, findings);
        auditService.log(principal, AuditAction.AUDIT_REPORT_GENERATED,
            "InternalAudit", auditId, Map.of("code", audit.getCode()));
        return pdf;
    }
}
```

`AuditReportPdfRenderer` em `qms/audit/application/service/` — componente dedicado, análogo a `QualityReportPdfRenderer` (ADR-050 Decisão 7). Cada módulo tem seu próprio renderer PDF para não sobrecarregar o renderer genérico do Sprint 39.

---

### Decisão 6 — `conformityRate` calculado no use case

```java
// GetAuditComplianceDashboardUseCase.java
// conformityRate = % itens CONFORMING / total respondidos nos últimos 12 meses
List<AuditChecklistItem> items = checklistItemRepository
    .findRespondedItemsInPeriod(today.minusMonths(12), today);

long conforming = items.stream()
    .filter(i -> i.getResponse() == ChecklistResponse.CONFORMING)
    .count();
long total = items.stream()
    .filter(i -> i.getResponse() != null && i.getResponse() != ChecklistResponse.NOT_APPLICABLE)
    .count();

double rate = total == 0 ? 0.0 : (double) conforming / total;
```

`NOT_APPLICABLE` excluído do denominador — itens não aplicáveis não afetam a taxa de conformidade (prática ISO padrão).

---

### Contrato de API — Sprint 42

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | `/api/v1/qms/audits` | SUPERVISOR+ | 201 | Cria auditoria em PLANNED |
| GET | `/api/v1/qms/audits` | OPERATOR+ | 200 | Lista paginada com filtros |
| GET | `/api/v1/qms/audits/{id}` | OPERATOR+ | 200 | Detalhe com checklist e achados |
| PUT | `/api/v1/qms/audits/{id}/status` | SUPERVISOR+ | 200 | Transição de status |
| PUT | `/api/v1/qms/audits/{id}` | SUPERVISOR+ | 200 | Atualiza (apenas PLANNED) |
| POST | `/api/v1/qms/audits/{id}/checklist` | SUPERVISOR+ | 201 | Cria itens em batch |
| PUT | `/api/v1/qms/audits/{id}/checklist/{itemId}` | SUPERVISOR+ | 200 | Atualiza resposta/evidência |
| POST | `/api/v1/qms/audits/{id}/findings` | SUPERVISOR+ | 201 | Adiciona achado |
| DELETE | `/api/v1/qms/audits/{id}/findings/{findingId}` | SUPERVISOR+ | 204 | Remove achado |
| POST | `/api/v1/qms/audits/{id}/report` | SUPERVISOR+ | 200 (PDF) | Gera relatório |
| GET | `/api/v1/qms/audits/compliance-dashboard` | SUPERVISOR+ | 200 | Dashboard de conformidade |

---

### Consequências

✅ Sub-pacote `qms/audit/` — evita colisão de nomenclatura com `common/domain/AuditLog`, nomeia explicitamente `InternalAudit`
✅ `AuditController` dedicado — SRP mantido, não sobrecarrega `QmsController` (NCs) nem `CapaController`
✅ `NcSeverity` reutilizado em `AuditFinding` — sem duplicação de enum dentro do mesmo bounded context QMS
✅ Referências leves `linkedNcId` e `linkedCapaId` — isolamento entre módulos, validação no use case
✅ `conformityRate` calculado excluindo `NOT_APPLICABLE` — prática ISO; lógica no use case, testável
✅ iText 7 já disponível — sem nova dependência, relatório de auditoria usa o mesmo renderer pattern do Sprint 39

⚠️ `IN_PROGRESS → CANCELLED` bloqueado — gestores que precisem cancelar uma auditoria em andamento devem completá-la primeiro; pode gerar resistência operacional; documentar o motivo da restrição na UI (tooltip explicativo)
⚠️ Auto-geração de código `AUD-{ANO}-{NNN}` não é atômica — unique constraint captura colisão, mas UX exibe 409 ao usuário; volume baixo torna o risco aceitável
⚠️ `AuditReportPdfRenderer` separado por módulo — proliferação de renderers PDF; considerar abstração `AbstractPdfRenderer` com métodos `addHeader`, `addTable`, `addFooter` compartilhados em `common/application/service/` em sprint futura
⚠️ Checklist em batch (`POST /audits/{id}/checklist`) cria todos os itens de uma vez — sem endpoint de adição individual; se o auditor quiser adicionar itens incrementalmente, precisará de `POST` individual ou múltiplos batch calls
