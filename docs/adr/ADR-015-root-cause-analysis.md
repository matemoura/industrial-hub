## ADR-015: Root Cause Analysis — 5-Porquês vinculado a NC
**Status**: Aprovado
**Data**: 2026-05-15
**US relacionadas**: US-052, US-053

### Contexto

O módulo QMS registra NCs e ações corretivas, mas não estrutura a análise da causa raiz. A técnica dos 5-Porquês é o método mais simples e amplamente adotado em ambientes industriais (ISO 9001, TPM). Esta ADR adiciona uma entidade de análise vinculada à NC, com até 5 iterações "Por quê → Resposta".

---

### Decisão 1 — Entidade `RootCauseAnalysis`

Colunas fixas (why1–why5) em vez de coleção `@OneToMany`. Máximo de 5 iterações é constraint de negócio; colunas fixas simplificam queries e evitam JOIN extra.

```java
@Entity
@Table(name = "root_cause_analysis")
public class RootCauseAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nc_id", nullable = false, unique = true)
    private NonConformance nonConformance;

    @Column(nullable = false, length = 500)
    private String why1;
    @Column(length = 500)
    private String answer1;
    @Column(length = 500)
    private String why2;
    @Column(length = 500)
    private String answer2;
    @Column(length = 500)
    private String why3;
    @Column(length = 500)
    private String answer3;
    @Column(length = 500)
    private String why4;
    @Column(length = 500)
    private String answer4;
    @Column(length = 500)
    private String why5;
    @Column(length = 500)
    private String answer5;

    @Column(length = 1000)
    private String rootCause;

    @Column(nullable = false, length = 50)
    private String createdBy;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`NonConformance` recebe mapeamento inverso (lazy, não cascadeado):
```java
@OneToOne(mappedBy = "nonConformance", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
private RootCauseAnalysis rca;
```

---

### Decisão 2 — Regras de negócio

- RCA só pode ser criada em NCs com `status = IN_ANALYSIS` ou `CLOSED` — `OPEN` retorna `422`
- Uma NC pode ter no máximo 1 RCA — POST em NC com RCA existente retorna `409`
- `why1` e `answer1` obrigatórios; `why2`–`why5`/`answer2`–`answer5` opcionais
- RCA pode ser atualizada via PUT (substituição completa) enquanto NC não estiver `CLOSED`
- NC `CLOSED` → PUT retorna `422 { "message": "RCA não pode ser alterada após o fechamento da NC" }`

---

### Decisão 3 — Package e classes novas

```
qms/
├── domain/
│   ├── RootCauseAnalysis.java           (nova entidade)
│   └── RcaNotFoundException.java        (nova exceção — 404)
├── application/dto/
│   ├── CreateRcaRequest.java            (novo record)
│   └── RcaResponse.java                 (novo record)
├── application/usecase/
│   ├── CreateRcaUseCase.java            (novo)
│   ├── UpdateRcaUseCase.java            (novo)
│   └── GetRcaByNcUseCase.java           (novo)
├── infrastructure/
│   └── RootCauseAnalysisRepository.java (novo)
└── presentation/
    └── QmsController.java               (existente — adicionar endpoints RCA)
```

---

### Decisão 4 — DTOs

```java
// CreateRcaRequest.java
public record CreateRcaRequest(
    @NotBlank @Size(max = 500) String why1,
    @NotBlank @Size(max = 500) String answer1,
    @Size(max = 500) String why2,
    @Size(max = 500) String answer2,
    @Size(max = 500) String why3,
    @Size(max = 500) String answer3,
    @Size(max = 500) String why4,
    @Size(max = 500) String answer4,
    @Size(max = 500) String why5,
    @Size(max = 500) String answer5,
    @Size(max = 1000) String rootCause
) {}

// RcaResponse.java
public record RcaResponse(
    UUID id,
    UUID ncId,
    String why1, String answer1,
    String why2, String answer2,
    String why3, String answer3,
    String why4, String answer4,
    String why5, String answer5,
    String rootCause,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static RcaResponse from(RootCauseAnalysis rca) {
        return new RcaResponse(
            rca.getId(), rca.getNonConformance().getId(),
            rca.getWhy1(), rca.getAnswer1(),
            rca.getWhy2(), rca.getAnswer2(),
            rca.getWhy3(), rca.getAnswer3(),
            rca.getWhy4(), rca.getAnswer4(),
            rca.getWhy5(), rca.getAnswer5(),
            rca.getRootCause(),
            rca.getCreatedBy(), rca.getCreatedAt(), rca.getUpdatedAt()
        );
    }
}
```

`NcResponse` ganha campo `rca` nullable (backward compatible — campo novo, valor `null` para NCs sem RCA):
```java
public record NcResponse(
    UUID id, String title, String description,
    NcType type, NcSeverity severity, NcStatus status,
    String reportedBy, LocalDateTime reportedAt,
    LocalDateTime closedAt, String closedBy,
    List<ActionResponse> actions,
    RcaResponse rca          // novo — nullable
) {
    public static NcResponse from(NonConformance nc) {
        return new NcResponse(
            nc.getId(), nc.getTitle(), nc.getDescription(),
            nc.getType(), nc.getSeverity(), nc.getStatus(),
            nc.getReportedBy(), nc.getReportedAt(),
            nc.getClosedAt(), nc.getClosedBy(),
            nc.getActions().stream().map(ActionResponse::from).toList(),
            nc.getRca() != null ? RcaResponse.from(nc.getRca()) : null
        );
    }
}
```

`GetNcDetailUseCase` deve usar `@EntityGraph` para carregar `rca` junto com `actions`:
```java
@EntityGraph(attributePaths = {"actions", "rca"})
Optional<NonConformance> findById(UUID id);  // no NonConformanceRepository
```

---

### Decisão 5 — Exceções e respostas de erro

| Classe | HTTP | Mensagem |
|--------|------|----------|
| `RcaNotFoundException` | 404 | "Análise de causa raiz não encontrada para esta NC" |
| `RcaAlreadyExistsException` | 409 | "Esta NC já possui uma análise de causa raiz" |
| `RcaNotAllowedException` | 422 | mensagem contextual (NC OPEN ou NC CLOSED em PUT) |

Todas mapeadas pelo `GlobalExceptionHandler` existente em `common/`.

---

### Decisão 6 — Contrato de API

| Método | Endpoint | Auth | Status HTTP |
|--------|----------|------|-------------|
| POST | /api/v1/qms/non-conformances/{ncId}/rca | SUPERVISOR+ | 201 / 409 / 422 |
| GET | /api/v1/qms/non-conformances/{ncId}/rca | OPERATOR+ | 200 / 404 |
| PUT | /api/v1/qms/non-conformances/{ncId}/rca | SUPERVISOR+ | 200 / 404 / 422 |
| GET | /api/v1/qms/non-conformances/{id} | OPERATOR+ | 200 (campo `rca` nullable) |

---

### Decisão 7 — Audit

`AuditAction` enum recebe duas novas constantes:
```java
RCA_CREATED,
RCA_UPDATED
```

`CreateRcaUseCase` e `UpdateRcaUseCase` chamam `AuditService.log()` após persistência (assíncrono, falha não aborta operação).

---

### Decisão 8 — Frontend: interfaces e métodos no `QmsService`

Adicionar ao `qms.service.ts` existente:

```typescript
export interface RcaResponse {
  id: string;
  ncId: string;
  why1: string; answer1: string | null;
  why2: string | null; answer2: string | null;
  why3: string | null; answer3: string | null;
  why4: string | null; answer4: string | null;
  why5: string | null; answer5: string | null;
  rootCause: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateRcaPayload {
  why1: string; answer1?: string;
  why2?: string; answer2?: string;
  why3?: string; answer3?: string;
  why4?: string; answer4?: string;
  why5?: string; answer5?: string;
  rootCause?: string;
}

// NcResponse estendido:
export interface NcResponse extends NcSummaryItem {
  description: string | null;
  closedAt: string | null;
  closedBy: string | null;
  actions: ActionResponse[];
  rca: RcaResponse | null;  // novo campo
}
```

Métodos novos no `QmsService`:
```typescript
createRca(ncId: string, payload: CreateRcaPayload): Observable<RcaResponse>
updateRca(ncId: string, payload: CreateRcaPayload): Observable<RcaResponse>
getRca(ncId: string): Observable<RcaResponse>
```

---

### Decisão 9 — Frontend: componente `nc-rca`

Novo componente standalone inserido **abaixo das ações corretivas** na página de detalhe da NC. Não criar rota nova — é uma seção da página de detalhe.

```
qms/
└── nc-detail/
    ├── nc-detail.component.ts   (existente — adicionar @ViewChild ou sinal para o RCA)
    ├── nc-rca/
    │   ├── nc-rca.component.ts
    │   ├── nc-rca.component.html
    │   └── nc-rca.component.scss
```

`NcDetailComponent` passa `nc()` como `input()` para `NcRcaComponent`, que gerencia estado RCA internamente com signals. `NcRcaComponent` recebe `input<NcResponse>()` e usa `computed()` para derivar permissões.

Lógica de wizard:
- `activePairs = signal(1)` — número de pares visíveis (1 a 5)
- `+ Adicionar próximo Porquê` só aparece se `activePairs() < 5` e `why{n}` e `answer{n}` do par atual estão preenchidos
- `Salvar RCA` envia POST (sem RCA anterior) ou PUT (com RCA anterior)
- NC `CLOSED` ou role `OPERATOR`: campos `[disabled]`, botão salvar oculto

---

### Consequências
✅ Colunas fixas (why1–why5) — sem JOIN adicional, constraint de negócio intencional
✅ `@OneToOne unique` — 1 NC → 0 ou 1 RCA; banco garante integridade
✅ Campo `rca` nullable em `NcResponse` — backward compatible, nenhum cliente existente quebra
✅ `@EntityGraph` em `GetNcDetailUseCase` — sem N+1 ao carregar NC com RCA e ações
⚠️ Modelo não suporta mais de 5 porquês — constraint intencional alinhada à metodologia
⚠️ `cascade = CascadeType.ALL` em `NonConformance.rca` — delete de NC cascadeia RCA automaticamente
⚠️ RCA somente leitura quando NC fechada — PUT bloqueado via use case, não via banco; garantir cobertura de teste
