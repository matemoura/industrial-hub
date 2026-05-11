## ADR-007: QMS Module — Non-Conformance & Corrective Action Plan
**Status**: Aprovado
**Data**: 2026-05-11
**US relacionadas**: US-021, US-022, US-023, US-024, US-025, US-026

### Contexto
Lançamento do segundo domínio do Industrial Hub: Quality Management System (QMS). O módulo gerencia não-conformidades (NCs) com ciclo de vida estruturado e planos de ação corretiva (CAP). Decisões arquiteturais precisam garantir consistência com o padrão feature-first já adotado no módulo OEE.

---

### Decisão 1 — Package structure: `qms/`

Seguir exatamente o mesmo padrão feature-first do módulo `oee/`:

```
src/main/java/com/industrialhub/backend/qms/
├── domain/
│   ├── NonConformance.java          (entidade JPA)
│   ├── CorrectiveAction.java        (entidade JPA)
│   ├── NcStatus.java                (enum)
│   ├── NcType.java                  (enum)
│   ├── NcSeverity.java              (enum)
│   └── ActionStatus.java            (enum)
├── application/
│   ├── dto/
│   │   ├── CreateNcRequest.java
│   │   ├── NcResponse.java
│   │   ├── NcSummaryResponse.java
│   │   ├── CreateActionRequest.java
│   │   └── ActionResponse.java
│   └── usecase/
│       ├── CreateNcUseCase.java
│       ├── TransitionNcStatusUseCase.java
│       ├── CreateCorrectiveActionUseCase.java
│       ├── CompleteCorrectiveActionUseCase.java
│       └── GetNcSummaryUseCase.java
├── infrastructure/
│   ├── NonConformanceRepository.java
│   └── CorrectiveActionRepository.java
└── presentation/
    └── QmsController.java
```

Frontend: `src/app/qms/` com `qms.service.ts` e sub-pastas `non-conformances/`.

---

### Decisão 2 — Máquina de estados do NC

```
OPEN ──────────► IN_ANALYSIS ──────────► CLOSED
  ▲                   │
  └───────────────────┘  (re-open)
```

Transições permitidas:
| De | Para | Quem |
|---|---|---|
| OPEN | IN_ANALYSIS | SUPERVISOR, ADMIN |
| IN_ANALYSIS | CLOSED | SUPERVISOR, ADMIN (ou auto via CAP) |
| IN_ANALYSIS | OPEN | SUPERVISOR, ADMIN |

**Auto-close:** quando a última `CorrectiveAction` de um NC em `IN_ANALYSIS` transita para `DONE`, o NC fecha automaticamente (lógica no `CompleteCorrectiveActionUseCase`).

Transições inválidas retornam `422 Unprocessable Entity` com body:
```json
{ "message": "Invalid transition from IN_ANALYSIS to OPEN", "allowedNext": ["CLOSED", "OPEN"] }
```

---

### Decisão 3 — Entidades e relacionamentos

```java
// NonConformance
@Entity
@Table(name = "non_conformance")
public class NonConformance {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String title;                          // varchar(200), NOT NULL
    @Column(columnDefinition = "TEXT")
    private String description;                    // nullable
    @Enumerated(EnumType.STRING)
    private NcType type;                           // NOT NULL
    @Enumerated(EnumType.STRING)
    private NcSeverity severity;                   // NOT NULL
    @Enumerated(EnumType.STRING)
    private NcStatus status;                       // NOT NULL, default OPEN
    private String reportedBy;                     // JWT username, NOT NULL
    private LocalDateTime reportedAt;              // set on creation
    private LocalDateTime closedAt;                // null until CLOSED
    private String closedBy;                       // null until CLOSED

    @OneToMany(mappedBy = "nonConformance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CorrectiveAction> actions = new ArrayList<>();
}

// CorrectiveAction
@Entity
@Table(name = "corrective_action")
public class CorrectiveAction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "non_conformance_id", nullable = false)
    private NonConformance nonConformance;
    @Column(columnDefinition = "TEXT")
    private String description;                    // NOT NULL
    private String responsible;                    // username, NOT NULL
    private LocalDate dueDate;                     // NOT NULL
    @Enumerated(EnumType.STRING)
    private ActionStatus status;                   // PENDING | DONE
    private LocalDateTime completedAt;             // null until DONE
    private String completedBy;                    // null until DONE
}
```

Indexes obrigatórios:
```java
@Table(name = "non_conformance", indexes = {
    @Index(name = "idx_nc_status",   columnList = "status"),
    @Index(name = "idx_nc_severity", columnList = "severity"),
    @Index(name = "idx_nc_reported", columnList = "reportedAt")
})
```

---

### Decisão 4 — Paginação: Spring Data `Pageable`

`GET /api/v1/qms/non-conformances` usa `Page<NcResponse>` com envelope:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 47,
  "totalPages": 3
}
```

Controller recebe `@PageableDefault(size = 20, sort = "reportedAt", direction = DESC) Pageable pageable`.

---

### Decisão 5 — Email: Spring Mail + `@Async`

Emails são disparados de forma assíncrona para nunca bloquear o response HTTP. `@EnableAsync` adicionado em `BackendApplication`.

```java
// QmsEmailService.java (common/application ou qms/application)
@Service
public class QmsEmailService {
    @Async
    public void notifyCriticalNc(NonConformance nc, List<String> supervisorEmails) { ... }

    @Async
    public void notifyNcClosed(NonConformance nc, String reporterEmail) { ... }
}
```

`User` entity recebe coluna `email varchar(255)` nullable (ddl-auto=update cria a coluna sem migration manual).

Configuração via environment variables:
```properties
spring.mail.host=${MAIL_HOST:smtp.example.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
mail.enabled=${MAIL_ENABLED:false}   # false em dev/test
```

---

### Contrato de API — módulo QMS

| Método | Endpoint | Auth | Status |
|---|---|---|---|
| POST | /api/v1/qms/non-conformances | OPERATOR+ | 201 / 400 |
| GET | /api/v1/qms/non-conformances | OPERATOR+ | 200 (paginado) |
| GET | /api/v1/qms/non-conformances/{id} | OPERATOR+ | 200 / 404 |
| PUT | /api/v1/qms/non-conformances/{id}/status | SUPERVISOR+ | 200 / 422 |
| GET | /api/v1/qms/non-conformances/summary | OPERATOR+ | 200 |
| GET | /api/v1/qms/non-conformances/export | SUPERVISOR+ | 200 (CSV) |
| POST | /api/v1/qms/non-conformances/{id}/actions | SUPERVISOR+ | 201 / 404 / 422 |
| GET | /api/v1/qms/non-conformances/{id}/actions | OPERATOR+ | 200 |
| PUT | /api/v1/qms/non-conformances/{id}/actions/{aid}/complete | SUPERVISOR+ | 200 / 404 |
| DELETE | /api/v1/qms/non-conformances/{id}/actions/{aid} | SUPERVISOR+ | 204 / 422 |

---

### Consequências
✅ Package structure consistente com `oee/` — sem curva de aprendizado para novos devs no módulo
✅ Auto-close via CAP elimina NC "esquecido em IN_ANALYSIS" com todas as ações concluídas
✅ Email async com `mail.enabled=false` — nenhum teste unitário precisa de SMTP real
⚠️ `@EnableAsync` em `BackendApplication` — verificar que `@Async` não engole exceções silenciosamente (configurar `AsyncUncaughtExceptionHandler`)
⚠️ Cascade ALL em `NonConformance.actions` — delete de NC cascadeia ações; garantir que `DELETE` de NC só ocorra via import management (não há endpoint delete de NC no QMS)
