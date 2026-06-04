## ADR-056: Sprint 45 — Reclamações de Clientes e MDR

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-132, US-133, US-134

---

### Contexto

ISO 13485 §8.2.1 exige que a organização implemente um procedimento documentado para receber e investigar reclamações de clientes. ANVISA RDC 665/2022 determina que dispositivos médicos sujeitos a eventos adversos graves devem ser notificados à ANVISA via MDR (Medical Device Report). O não cumprimento sujeita o fabricante a sanções regulatórias.

O Industrial Hub possui NCs e CAPAs, mas não há módulo formal de reclamações de clientes com rastreabilidade de notificações regulatórias. Este sprint cria `qms/complaints/` cobrindo:

1. **US-132** — CRUD de `CustomerComplaint`, investigação e vínculos com NC/CAPA
2. **US-133** — Indicadores de reclamação e relatório MDR PDF (iText 7)
3. **US-134** — Frontend: registro, investigação e dashboard de reclamações

---

### Decisão 1 — Sub-pacote `qms/complaints/` como subdomínio de QMS

Reclamações de clientes são um subdomínio do SGQ, análogo a `qms/audit/` (ADR-053) e `qms/risk/` (ADR-054):

```
com.industrialhub.backend.qms.complaints/
├── domain/
│   ├── CustomerComplaint.java
│   ├── ComplaintSource.java     (enum: CLIENT, DISTRIBUTOR, REGULATORY_BODY, INTERNAL)
│   ├── ComplaintStatus.java     (enum: RECEIVED, UNDER_INVESTIGATION, INVESTIGATION_COMPLETED, CLOSED)
├── application/
│   ├── dto/
│   │   ├── ComplaintResponse.java
│   │   ├── ComplaintIndicators.java
│   │   └── ComplaintSummary.java
│   └── usecase/
│       ├── CreateComplaintUseCase.java
│       ├── GetComplaintListUseCase.java
│       ├── GetComplaintDetailUseCase.java
│       ├── UpdateComplaintUseCase.java
│       ├── TransitionComplaintStatusUseCase.java
│       ├── LinkNcToComplaintUseCase.java
│       ├── LinkCapaToComplaintUseCase.java
│       ├── RegisterAnvisaReportUseCase.java
│       ├── GetComplaintIndicatorsUseCase.java
│       └── GenerateMdrReportUseCase.java
├── infrastructure/
│   └── CustomerComplaintRepository.java
└── presentation/
    └── ComplaintController.java   — @RequestMapping("/api/v1/qms/complaints")
```

`NcSeverity` é reutilizado em `CustomerComplaint.severity` — mesma escala (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), sem duplicação de enum dentro do bounded context QMS.

---

### Decisão 2 — Entidade `CustomerComplaint` com campos MDR

```java
// qms/complaints/domain/CustomerComplaint.java
@Entity
@Table(name = "customer_complaint",
    uniqueConstraints = @UniqueConstraint(name = "uk_complaint_code", columnNames = "code"),
    indexes = {
        @Index(name = "idx_complaint_status",           columnList = "status"),
        @Index(name = "idx_complaint_reported_date",    columnList = "reported_date"),
        @Index(name = "idx_complaint_product_code",     columnList = "product_code"),
        @Index(name = "idx_complaint_reported_anvisa",  columnList = "reported_to_anvisa")
    })
public class CustomerComplaint {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;                     // "REC-{ANO}-{NNN}"

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintSource source;

    @Column(length = 100)
    private String productCode;

    @Column(length = 50)
    private String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NcSeverity severity;             // reutiliza NcSeverity

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status;          // RECEIVED na criação

    @Column(nullable = false)
    private LocalDate reportedDate;

    @Column(nullable = false, length = 100)
    private String reportedBy;               // nome do cliente/distribuidor

    @Column(nullable = false, length = 100)
    private String assignedTo;              // username interno responsável

    @Column(columnDefinition = "TEXT")
    private String investigationSummary;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String correctiveAction;

    @Column(nullable = false)
    private boolean reportedToAnvisa = false;

    private LocalDate anvisaReportDate;

    @Column(length = 50)
    private String anvisaReportNumber;

    private UUID linkedNcId;                 // FK leve
    private UUID linkedCapaId;              // FK leve

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime closedAt;          // preenchido ao transitar para CLOSED
}
```

**Índice em `reported_to_anvisa`**: o filtro `?reportedToAnvisa=false` (reclamações não reportadas) é um caso de uso crítico para compliance — o card "Não Reportadas ANVISA" no dashboard deve responder rapidamente mesmo com alto volume.

---

### Decisão 3 — State machine de `ComplaintStatus`

```
RECEIVED ──────────────────► UNDER_INVESTIGATION
UNDER_INVESTIGATION ────────► INVESTIGATION_COMPLETED
INVESTIGATION_COMPLETED ────► CLOSED (requer investigationSummary + rootCause)
```

A transição para `CLOSED` é a mais restrita — exige que a investigação esteja documentada:

```java
// TransitionComplaintStatusUseCase.java
if (newStatus == ComplaintStatus.CLOSED) {
    if (complaint.getInvestigationSummary() == null
            || complaint.getInvestigationSummary().isBlank()) {
        throw new IllegalArgumentException(
            "Resumo da investigação é obrigatório para fechar a reclamação");
    }
    if (complaint.getRootCause() == null || complaint.getRootCause().isBlank()) {
        throw new IllegalArgumentException(
            "Causa raiz é obrigatória para fechar a reclamação");
    }
    complaint.setClosedAt(LocalDateTime.now());
}
```

Não há transição reversa permitida (CLOSED é terminal). Reclamações reabrem-se criando nova reclamação vinculada — evita complexidade de re-abertura.

---

### Decisão 4 — Controle de acesso ADMIN exclusivo para notificação ANVISA

A notificação ANVISA (`PUT /complaints/{id}/anvisa-report`) é exclusiva para ADMIN:

```java
@PutMapping("/{id}/anvisa-report")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ComplaintResponse> registerAnvisaReport(
        @PathVariable UUID id,
        @Valid @RequestBody AnvisaReportRequest req,
        Principal principal) {
    return ResponseEntity.ok(
        registerAnvisaReportUseCase.execute(id, req, principal.getName()));
}
```

SUPERVISOR que tentar acessar recebe 403. Rationale: notificação ANVISA tem consequências regulatórias e legais; exige autorização do nível máximo do sistema.

---

### Decisão 5 — Relatório MDR PDF via iText 7: pré-condições estritas

O relatório MDR (`POST /complaints/{id}/mdr-report`) está disponível apenas quando:
- `reportedToAnvisa = true` — a notificação foi registrada
- `status = CLOSED` — a investigação foi concluída

Se qualquer condição faltar → `422 { "message": "..." }`:

```java
// GenerateMdrReportUseCase.java
public byte[] execute(UUID id, String principal) throws IOException {
    CustomerComplaint complaint = repository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Reclamação não encontrada"));

    if (!complaint.isReportedToAnvisa()) {
        throw new IllegalStateException(
            "Relatório MDR disponível apenas para reclamações notificadas à ANVISA");
    }
    if (complaint.getStatus() != ComplaintStatus.CLOSED) {
        throw new IllegalStateException(
            "Relatório MDR disponível apenas para reclamações FECHADAS");
    }

    byte[] pdf = mdrPdfRenderer.render(complaint);
    auditService.log(principal, AuditAction.MDR_REPORT_GENERATED,
        "CustomerComplaint", id, Map.of("code", complaint.getCode()));
    return pdf;
}
```

`MdrPdfRenderer` em `qms/complaints/application/service/` — componente dedicado, consistente com `AuditReportPdfRenderer` (ADR-053 Decisão 5). Cabeçalho do PDF: **"NOTIFICAÇÃO ADVERSA — ANVISA MDR"** com aviso de confidencialidade.

---

### Decisão 6 — Indicadores calculados em Java com `Collectors`

`GetComplaintIndicatorsUseCase` carrega todas as reclamações do período via projeção leve e calcula em Java — mesma estratégia de `GetCapaAgingUseCase` (ADR-050 Decisão 4):

```java
// GetComplaintIndicatorsUseCase.java
public ComplaintIndicators execute(LocalDate from, LocalDate to) {
    List<ComplaintIndicatorProjection> complaints =
        repository.findByReportedDateBetween(from, to);

    long totalReceived = complaints.size();
    Map<ComplaintStatus, Integer> byStatus = complaints.stream()
        .collect(Collectors.groupingBy(ComplaintIndicatorProjection::getStatus,
                 Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

    OptionalDouble avgResolutionDays = complaints.stream()
        .filter(c -> c.getStatus() == ComplaintStatus.CLOSED && c.getClosedAt() != null)
        .mapToLong(c -> ChronoUnit.DAYS.between(
            c.getReportedDate(), c.getClosedAt().toLocalDate()))
        .average();

    List<ProductCount> byProduct = complaints.stream()
        .filter(c -> c.getProductCode() != null)
        .collect(Collectors.groupingBy(ComplaintIndicatorProjection::getProductCode,
                 Collectors.counting()))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(5)
        .map(e -> new ProductCount(e.getKey(), e.getValue()))
        .toList();

    return new ComplaintIndicators(totalReceived, byStatus, byProduct,
        avgResolutionDays.isPresent() ? avgResolutionDays.getAsDouble() : null, /* ... */);
}
```

---

### Contrato de API — Sprint 45

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | `/api/v1/qms/complaints` | SUPERVISOR+ | 201 | Cria reclamação em RECEIVED |
| GET | `/api/v1/qms/complaints` | SUPERVISOR+ | 200 | Lista paginada com filtros |
| GET | `/api/v1/qms/complaints/{id}` | SUPERVISOR+ | 200 | Detalhe |
| PUT | `/api/v1/qms/complaints/{id}` | SUPERVISOR+ | 200 | Atualiza (exceto CLOSED) |
| PUT | `/api/v1/qms/complaints/{id}/status` | SUPERVISOR+ | 200 | Transição de status |
| PUT | `/api/v1/qms/complaints/{id}/link-nc` | SUPERVISOR+ | 200 | Vincula NC |
| PUT | `/api/v1/qms/complaints/{id}/link-capa` | SUPERVISOR+ | 200 | Vincula CAPA |
| PUT | `/api/v1/qms/complaints/{id}/anvisa-report` | ADMIN | 200 | Registra notificação ANVISA |
| GET | `/api/v1/qms/complaints/indicators` | SUPERVISOR+ | 200 | Indicadores de reclamação |
| POST | `/api/v1/qms/complaints/{id}/mdr-report` | ADMIN | 200 (PDF) | Gera relatório MDR |

**OPERATOR excluído de todos os endpoints de reclamação** — dados de reclamação de clientes são sensíveis e exigem SUPERVISOR+ para acesso. Diferente de NCs (OPERATOR+ pode criar), reclamações envolvem dados de clientes identificáveis.

---

### Consequências

✅ Sub-pacote `qms/complaints/` com `ComplaintController` dedicado — SRP mantido; não sobrecarrega `QmsController`
✅ `NcSeverity` reutilizado — sem enum duplicado dentro do bounded context QMS
✅ `reportedToAnvisa` com índice próprio — queries de compliance (reclamações não reportadas) performáticas
✅ Geração MDR com pré-condições estritas (`reportedToAnvisa=true` AND `CLOSED`) — previne emissão de relatório incompleto
✅ Notificação ANVISA exclusiva para ADMIN — controle de autorização máxima para ações com consequências regulatórias
✅ `avg ResolutionDays` em Java via `ChronoUnit.DAYS.between` — sem SQL nativo; testável

⚠️ Sem transição reversa de `CLOSED` — reclamações reabrem-se criando nova reclamação; pode dificultar rastreabilidade se o fluxo exigir reabertura frequente; documentar na UI que a reabertura requer nova reclamação vinculada
⚠️ `linkedNcId` e `linkedCapaId` como FKs leves sem cascade — deleção de NC/CAPA não limpa o vínculo; soft-delete existente nas entidades minimiza o risco
⚠️ `MdrPdfRenderer` é o quarto renderer PDF no projeto (após `QualityReportPdfRenderer`, `AuditReportPdfRenderer`, e o futuro renderer de Sprint 46) — considerar abstração `AbstractPdfRenderer` em `common/` para compartilhar cabeçalho MSB, rodapé, fontes e estilos; eliminaria duplicação de código boilerplate iText 7
⚠️ Relatório MDR exige `ADMIN` — se o ADMIN estiver ausente, nenhum MDR pode ser gerado; avaliar se SUPERVISOR pode solicitar e ADMIN revisar/aprovar o relatório (workflow em sprint futura)
