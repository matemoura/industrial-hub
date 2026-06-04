## ADR-055: Sprint 44 — Controle de Mudanças

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-130, US-131

---

### Contexto

ISO 13485 §4.1 exige que mudanças nos processos do SGQ sejam controladas. ANVISA RDC 665/2022 reforça que mudanças não controladas são causa frequente de não-conformidade em auditorias. O Industrial Hub não possui módulo de controle de mudanças — mudanças de processo, documento, equipamento e software são feitas sem fluxo formal de aprovação.

Este sprint adiciona o módulo `common/changes/` com:

1. **US-130** — Backend: CRUD de `ChangeRequest`, fluxo 6-estados com aprovação multi-nível e vínculos com NC, GED, Equipamento e RiskItem
2. **US-131** — Frontend: solicitação, timeline de aprovação, rastreabilidade e badge de pendências no nav

O módulo é `common/changes/` (não `qms/changes/`) porque controle de mudanças é transversal a todos os módulos (afeta Manutenção, QMS, Produção), não apenas QMS.

---

### Decisão 1 — Pacote `common/changes/`

```
com.industrialhub.backend.common.changes/
├── domain/
│   ├── ChangeRequest.java
│   ├── ChangeRequestLink.java
│   ├── ChangeType.java          (enum: PROCESS, DOCUMENT, EQUIPMENT, SOFTWARE, REGULATORY, OTHER)
│   ├── ChangeStatus.java        (enum: DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, IMPLEMENTED)
│   └── ChangeEntityType.java    (enum: NON_CONFORMANCE, DOCUMENT, EQUIPMENT, RISK_ITEM)
├── application/
│   ├── dto/
│   │   ├── ChangeRequestResponse.java
│   │   ├── ChangeRequestLinkResponse.java
│   │   └── ChangeRequestSummary.java
│   └── usecase/
│       ├── CreateChangeRequestUseCase.java
│       ├── SubmitChangeRequestUseCase.java
│       ├── ReviewChangeRequestUseCase.java
│       ├── ApproveChangeRequestUseCase.java
│       ├── ImplementChangeRequestUseCase.java
│       ├── GetChangeRequestListUseCase.java
│       ├── GetChangeRequestDetailUseCase.java
│       ├── UpdateChangeRequestUseCase.java
│       ├── AddChangeRequestLinkUseCase.java
│       ├── RemoveChangeRequestLinkUseCase.java
│       └── CountPendingForMeUseCase.java
├── infrastructure/
│   ├── ChangeRequestRepository.java
│   └── ChangeRequestLinkRepository.java
└── presentation/
    └── ChangeRequestController.java   — @RequestMapping("/api/v1/changes")
```

---

### Decisão 2 — Entidades do módulo de mudanças

```java
// common/changes/domain/ChangeRequest.java
@Entity
@Table(name = "change_request",
    uniqueConstraints = @UniqueConstraint(name = "uk_cr_code", columnNames = "code"),
    indexes = {
        @Index(name = "idx_cr_status",       columnList = "status"),
        @Index(name = "idx_cr_requested_by", columnList = "requested_by")
    })
public class ChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;                // "CR-{ANO}-{NNN}"

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeType changeType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(columnDefinition = "TEXT")
    private String impactAssessment;    // preenchido pelo SUPERVISOR na revisão

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeStatus status;        // DRAFT na criação

    @Column(nullable = false, length = 100)
    private String requestedBy;         // username JWT

    private LocalDateTime submittedAt;
    @Column(length = 100) private String reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length = 100) private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime implementedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

```java
// common/changes/domain/ChangeRequestLink.java
@Entity
@Table(name = "change_request_link")
public class ChangeRequestLink {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "change_request_id", nullable = false)
    private ChangeRequest changeRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeEntityType entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(length = 500)
    private String linkNote;
}
```

---

### Decisão 3 — State machine com 6 estados e aprovação multi-nível

```
DRAFT ──────────► SUBMITTED (pelo requestedBy)
SUBMITTED ───────► UNDER_REVIEW (SUPERVISOR+ faz revisão)
UNDER_REVIEW ────► APPROVED (ADMIN aprova)
UNDER_REVIEW ────► REJECTED (ADMIN rejeita — requer rejectionReason)
APPROVED ────────► IMPLEMENTED (SUPERVISOR+ implementa)
```

**Separação de papéis**:
- `SUBMITTED → UNDER_REVIEW`: SUPERVISOR faz revisão de impacto (`impactAssessment`) — pode ser o próprio solicitante se for SUPERVISOR, mas boa prática exige revisão de outro
- `UNDER_REVIEW → APPROVED/REJECTED`: exclusivo para ADMIN — nível mais alto de aprovação
- `APPROVED → IMPLEMENTED`: SUPERVISOR confirma implementação física

**Autorização por identidade em `SubmitChangeRequestUseCase`**:

```java
// SubmitChangeRequestUseCase.java
public ChangeRequestResponse execute(UUID id, String principal) {
    ChangeRequest cr = repository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Change Request não encontrada"));

    if (!cr.getRequestedBy().equals(principal)) {
        throw new AccessDeniedException(
            "Somente o solicitante pode submeter a Change Request");
    }
    if (cr.getStatus() != ChangeStatus.DRAFT) {
        throw new InvalidStateTransitionException(cr.getStatus(), ChangeStatus.SUBMITTED, null);
    }
    cr.setStatus(ChangeStatus.SUBMITTED);
    cr.setSubmittedAt(LocalDateTime.now());
    auditService.log(principal, AuditAction.CR_SUBMITTED, "ChangeRequest", id, Map.of());
    return ChangeRequestResponse.from(repository.save(cr));
}
```

`@PreAuthorize` no controller garante autenticação, mas a regra de identidade (`requestedBy == principal`) é verificada no use case — não pode ser expressa em SpEL simples sem injeção de beans.

**`ApproveChangeRequestUseCase`** verifica role ADMIN no controller via `@PreAuthorize("hasRole('ADMIN')")` — SUPERVISOR que tentar acessa 403 diretamente do Spring Security, antes de entrar no use case.

---

### Decisão 4 — Auto-geração de código `CR-{ANO}-{NNN}`

Mesma estratégia de `AUD-{ANO}-{NNN}` (ADR-053 Decisão 4). Unique constraint captura colisão; `GlobalExceptionHandler` converte para 409:

```java
private String generateCode() {
    int year = LocalDate.now().getYear();
    long count = repository.countByCodeStartingWith("CR-" + year);
    return "CR-%d-%03d".formatted(year, count + 1);
}
```

---

### Decisão 5 — Vínculos com entidades externas via `ChangeEntityType` + FK leve

`ChangeRequestLink.entityId` é uma UUID genérica apontando para qualquer entidade dos tipos definidos em `ChangeEntityType`. A validação de existência ocorre no use case:

```java
// AddChangeRequestLinkUseCase.java
private void validateEntityExists(ChangeEntityType type, UUID entityId) {
    switch (type) {
        case NON_CONFORMANCE -> nonConformanceRepository.findById(entityId)
            .orElseThrow(() -> new EntityNotFoundException("NC não encontrada: " + entityId));
        case DOCUMENT -> documentRepository.findById(entityId)
            .orElseThrow(() -> new EntityNotFoundException("Documento GED não encontrado"));
        case EQUIPMENT -> equipmentRepository.findById(entityId)
            .orElseThrow(() -> new EntityNotFoundException("Equipamento não encontrado"));
        case RISK_ITEM -> riskItemRepository.findById(entityId)
            .orElseThrow(() -> new EntityNotFoundException("RiskItem não encontrado"));
    }
}
```

Sem FK física no banco — `ChangeRequestLink.entity_id` é coluna UUID sem constraint referencial. Esta é a decisão mais pragmática para entidade genérica que aponta para 4 tabelas diferentes. Alternativa descartada: `@ManyToOne` polimórfico com `@Inheritance` — adiciona complexidade de mapeamento JPA sem benefício real para consultas.

---

### Decisão 6 — Badge de pendências no `NavComponent` via polling

O badge de "CRs pendentes" no nav é calculado por role:
- SUPERVISOR: CRs em `SUBMITTED` (precisam de revisão)
- ADMIN: CRs em `UNDER_REVIEW` (precisam de aprovação)
- OPERATOR: CRs em qualquer status criadas por si mesmo (`requestedBy = me`)

```java
// CountPendingForMeUseCase.java
public int execute(String username, String role) {
    return switch (role) {
        case "ADMIN"      -> (int) repository.countByStatus(ChangeStatus.UNDER_REVIEW);
        case "SUPERVISOR" -> (int) repository.countByStatus(ChangeStatus.SUBMITTED);
        default           -> (int) repository.countByRequestedByAndStatusIn(username,
                                List.of(ChangeStatus.DRAFT, ChangeStatus.SUBMITTED,
                                        ChangeStatus.UNDER_REVIEW, ChangeStatus.APPROVED));
    };
}
```

Polling a cada 5 minutos no frontend (mesmo padrão de `unreadCount` de notificações, Sprint 18, e `belowMinCount` de estoque, Sprint 20).

---

### Contrato de API — Sprint 44

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | `/api/v1/changes` | autenticado | 201 | Cria CR em DRAFT |
| GET | `/api/v1/changes` | OPERATOR+ | 200 | Lista paginada com filtros |
| GET | `/api/v1/changes/{id}` | OPERATOR+ | 200 | Detalhe com links |
| PUT | `/api/v1/changes/{id}` | autenticado | 200 | Atualiza (apenas DRAFT, pelo requestedBy) |
| POST | `/api/v1/changes/{id}/submit` | autenticado | 200 | DRAFT → SUBMITTED (requestedBy) |
| PUT | `/api/v1/changes/{id}/review` | SUPERVISOR+ | 200 | SUBMITTED → UNDER_REVIEW |
| PUT | `/api/v1/changes/{id}/approve` | ADMIN | 200 | UNDER_REVIEW → APPROVED/REJECTED |
| PUT | `/api/v1/changes/{id}/implement` | SUPERVISOR+ | 200 | APPROVED → IMPLEMENTED |
| POST | `/api/v1/changes/{id}/links` | SUPERVISOR+ | 201 | Adiciona vínculo |
| DELETE | `/api/v1/changes/{id}/links/{linkId}` | SUPERVISOR+ | 204 | Remove vínculo |
| GET | `/api/v1/changes/pending-count` | autenticado | 200 | Contagem de pendências por role |

---

### Consequências

✅ Módulo `common/changes/` — transversal a todos os domínios; não acoplado a `qms/` ou `maintenance/`
✅ Fluxo 6-estados com papéis segregados — SUPERVISOR revisa, ADMIN aprova; sem "self-approval" em nível alto
✅ Regra de identidade `requestedBy == principal` no use case — não em SpEL, testável
✅ `ChangeEntityType` com FK leve — sem FK física em tabela de vínculo genérico; flexibilidade para adicionar novos tipos sem migration
✅ Validação de existência de entidade vinculada no use case — integridade referencial garantida em código, sem FK física
✅ Badge de pendências com lógica por role — SUPERVISOR vê o que precisa revisar, ADMIN o que precisa aprovar

⚠️ Fluxo não suporta devolução para o solicitante (`UNDER_REVIEW → SUBMITTED`) — se o revisor precisar de mais informações, deve comentar externamente ou rejeitar e solicitar nova CR; considerar estado `RETURNED` em sprint futura
⚠️ `ChangeRequestLink.entity_id` sem FK física — `entityId` pode referenciar entidade deletada sem cascade; soft-delete nas entidades vinculadas minimiza o risco, mas não elimina
⚠️ Auto-geração de código `CR-{ANO}-{NNN}` não é atômica — unique constraint captura colisão; volume baixo torna risco aceitável (mesmo padrão de ADR-053)
⚠️ OPERATOR pode criar CRs mas não pode executar nenhuma ação de fluxo — UX deve deixar claro que a CR ficará aguardando SUPERVISOR para revisão; risco de abandono de CRs em DRAFT sem acompanhamento
