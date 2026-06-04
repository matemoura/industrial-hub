## ADR-051: Sprint 40 — Gestão de Treinamentos e Competências

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-118, US-119, US-120

---

### Contexto

ISO 13485 §6.2 exige que a organização determine a competência do pessoal que realiza trabalho que afeta a qualidade do produto, forneça treinamento, avalie a eficácia do treinamento e mantenha registros. O Industrial Hub não possui módulo de RH/Treinamentos — a rastreabilidade de capacitações é feita externamente. Este sprint cria o módulo `training/` do zero, cobrindo:

1. **US-118** — CRUD de cursos, registros por colaborador e matriz de competências
2. **US-119** — Avaliação de eficácia dos treinamentos e alertas automáticos de certificações vencendo
3. **US-120** — Frontend: catálogo, registros, dashboard de compliance e matriz

O módulo deve integrar-se ao `StorageService` (MinIO, Sprint 21) para certificados PDF e ao `NotificationService` (Sprint 18) para alertas de vencimento.

---

### Decisão 1 — Novo pacote top-level `training/`

O módulo de treinamentos é independente do `maintenance/` e do `qms/`. Não há entidade de domínio em comum que justifique colocá-lo como sub-pacote de outro módulo. A estrutura segue o padrão feature-first estabelecido em ADR-001:

```
com.industrialhub.backend.training/
├── domain/
│   ├── TrainingCourse.java
│   ├── TrainingRecord.java
│   ├── TrainingCategory.java      (enum)
│   └── EffectivenessResult.java   (enum)
├── application/
│   ├── dto/
│   │   ├── TrainingCourseResponse.java
│   │   ├── TrainingRecordResponse.java
│   │   ├── CompetencyMatrixRow.java
│   │   └── TrainingComplianceSummary.java
│   └── usecase/
│       ├── CreateTrainingCourseUseCase.java
│       ├── GetTrainingCourseListUseCase.java
│       ├── DeactivateTrainingCourseUseCase.java
│       ├── CreateTrainingRecordUseCase.java
│       ├── GetTrainingRecordListUseCase.java
│       ├── GetMyTrainingRecordsUseCase.java
│       ├── AssessEffectivenessUseCase.java
│       ├── GetCompetencyMatrixUseCase.java
│       └── GetTrainingComplianceSummaryUseCase.java
├── infrastructure/
│   ├── TrainingCourseRepository.java
│   └── TrainingRecordRepository.java
└── presentation/
    └── TrainingController.java
```

Job de alerta em `training/application/usecase/TrainingExpiryAlertJob.java` (com `@Scheduled`), separado dos use cases de CRUD.

---

### Decisão 2 — Entidades `TrainingCourse` e `TrainingRecord`

```java
// training/domain/TrainingCourse.java
@Entity
@Table(name = "training_course",
    uniqueConstraints = @UniqueConstraint(name = "uk_training_course_code", columnNames = "code"))
public class TrainingCourse {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingCategory category;     // GMP, QUALITY, SAFETY, REGULATORY, TECHNICAL, OTHER

    @Column(nullable = false)
    private Integer durationHours;

    private Integer validityMonths;         // null = sem vencimento

    @ElementCollection
    @CollectionTable(name = "training_course_roles")
    private Set<String> requiredForRoles = new HashSet<>();

    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

```java
// training/domain/TrainingRecord.java
@Entity
@Table(name = "training_record",
    indexes = {
        @Index(name = "idx_training_record_username",   columnList = "username"),
        @Index(name = "idx_training_record_course",     columnList = "course_id"),
        @Index(name = "idx_training_record_expires_at", columnList = "expires_at")
    })
public class TrainingRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private TrainingCourse course;

    @Column(nullable = false, length = 100)
    private String username;               // referência leve — sem FK física para User

    @Column(nullable = false)
    private LocalDate completedAt;

    private LocalDate expiresAt;           // calculado: completedAt + validityMonths; null se sem vencimento

    @Column(length = 200)
    private String instructorName;

    @Min(0) @Max(100)
    private Integer score;

    @Column(nullable = false)
    private boolean passed;

    @Column(length = 500)
    private String certificateStoragePath; // chave MinIO, nullable

    @Column(nullable = false, length = 100)
    private String recordedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    // Campos de eficácia — adicionados via migration V{N+2} (US-119)
    private LocalDate effectivenessAssessedAt;

    @Column(length = 100)
    private String effectivenessAssessedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EffectivenessResult effectivenessResult; // EFFECTIVE, PARTIALLY_EFFECTIVE, NOT_EFFECTIVE

    @Column(columnDefinition = "TEXT")
    private String effectivenessNotes;
}
```

**Referência leve a `User`**: `TrainingRecord.username` é uma `String` — sem `@ManyToOne` para a entidade `User`. Esta decisão alinha ao padrão do projeto (e.g., `CorrectiveAction.responsible` também é String) e evita FK física que complicaria testes e impediria deletar usuários históricos.

**`expiresAt` calculado no use case** (não no banco): `CreateTrainingRecordUseCase` calcula `completedAt.plusMonths(validityMonths)` quando `validityMonths != null`. Mantém a lógica de negócio no domínio Java, não em triggers SQL.

---

### Decisão 3 — Validação de arquivo de certificado via `GedFileValidator`

O upload de certificado PDF em `POST /api/v1/training/records` (multipart/form-data) reutiliza o `GedFileValidator` (ADR-049 Decisão 2) para validação MIME com Apache Tika:

```java
// CreateTrainingRecordUseCase.java
@Service
@Transactional
public class CreateTrainingRecordUseCase {

    public TrainingRecordResponse execute(CreateTrainingRecordRequest req,
                                          MultipartFile file,
                                          String principal) throws IOException {
        TrainingCourse course = courseRepository.findById(req.courseId())
            .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));

        if (file != null && !file.isEmpty()) {
            gedFileValidator.validate(file); // lança InvalidGedFileException (422) se inválido
            String key = "training/%s/%s_%s".formatted(
                req.username(),
                UUID.randomUUID(),
                GedFileValidator.sanitizeFilename(file.getOriginalFilename())
            );
            storageService.upload(key, file.getInputStream(), file.getContentType(), file.getSize());
            // storageKey salvo na entidade
        }

        LocalDate expiresAt = course.getValidityMonths() != null
            ? req.completedAt().plusMonths(course.getValidityMonths())
            : null;

        TrainingRecord record = new TrainingRecord(/* ... */);
        record = trainingRecordRepository.save(record);
        auditService.log(principal, AuditAction.TRAINING_RECORD_CREATED, "TrainingRecord",
            record.getId(), Map.of("courseCode", course.getCode(), "username", req.username()));

        return TrainingRecordResponse.from(record, course);
    }
}
```

**`GedFileValidator.sanitizeFilename()` é `public static`** (ADR-049 Decisão 2) — reúso sem injeção.

---

### Decisão 4 — Matriz de competências: cálculo em Java com projeções leves

A matriz de competências é um relatório analítico por usuário × curso obrigatório. O cálculo do status (`VALID`, `EXPIRING`, `EXPIRED`, `MISSING`) depende de `LocalDate.now()` — variável dinâmica que dificulta JPQL. O use case:

1. Carrega todos os usuários ativos via `UserRepository.findAllByActiveTrue()` (projeção `id, username, role`)
2. Carrega todos os cursos ativos com `requiredForRoles` contendo o role do usuário
3. Carrega todos os `TrainingRecord` relevantes em uma única query (sem N+1)
4. Agrupa e calcula status em Java com `LocalDate.now()` como referência

```java
// CompetencyStatus enum
public enum CompetencyStatus { VALID, EXPIRING, EXPIRED, MISSING }

// Lógica de cálculo
private CompetencyStatus computeStatus(TrainingRecord record) {
    if (record == null) return CompetencyStatus.MISSING;
    if (!record.isPassed()) return CompetencyStatus.MISSING;
    if (record.getExpiresAt() == null) return CompetencyStatus.VALID;
    LocalDate today = LocalDate.now();
    if (record.getExpiresAt().isBefore(today)) return CompetencyStatus.EXPIRED;
    if (!record.getExpiresAt().isAfter(today.plusDays(30))) return CompetencyStatus.EXPIRING;
    return CompetencyStatus.VALID;
}
```

---

### Decisão 5 — Job de alertas e debounce

`TrainingExpiryAlertJob` executa toda segunda às 08h (America/Sao_Paulo):

```java
@Scheduled(cron = "0 0 8 * * MON", zone = "America/Sao_Paulo")
public void runWeekly() { alertUseCase.execute(); }
```

**Debounce via `NotificationRepository`**: reutiliza o método `existsByTitleAndCreatedAtAfter(String title, LocalDateTime since)` (Sprint 20, ADR-035 Decisão de debounce de estoque) — mesma estratégia. Certificações vencendo: debounce 144h. Certificações vencidas: debounce 24h (mais urgentes, alertas mais frequentes).

Notificação pessoal — usa `notificationService.createForUser(username, title, body, severity)` (não broadcast), pois o alerta é específico ao colaborador afetado.

---

### Contrato de API — Sprint 40

**US-118 — Cursos:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/training/courses` | ADMIN | 201 |
| GET | `/api/v1/training/courses` | OPERATOR+ | 200 |
| GET | `/api/v1/training/courses/{id}` | OPERATOR+ | 200 |
| PUT | `/api/v1/training/courses/{id}` | ADMIN | 200 |
| PUT | `/api/v1/training/courses/{id}/deactivate` | ADMIN | 204 |

**US-118 — Registros:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/training/records` (multipart) | SUPERVISOR+ | 201 |
| GET | `/api/v1/training/records` | SUPERVISOR+ | 200 |
| GET | `/api/v1/training/records/me` | autenticado | 200 |
| GET | `/api/v1/training/records/{id}/certificate` | OPERATOR+ | 200 (URL pré-assinada) |
| DELETE | `/api/v1/training/records/{id}` | ADMIN | 204 |
| GET | `/api/v1/training/competency-matrix` | SUPERVISOR+ | 200 |

**US-119:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/training/records/{id}/effectiveness` | SUPERVISOR+ | 200 |
| GET | `/api/v1/training/compliance-summary` | SUPERVISOR+ | 200 |
| POST | `/api/v1/admin/training/alerts/run-now` | ADMIN | 200 |

O `TrainingController` em `training/presentation/` concentra todos os endpoints. Não há controller separado por subdomínio — o volume de endpoints não justifica divisão.

---

### Consequências

✅ Módulo `training/` isolado e autocontido — não polui `qms/` nem `maintenance/`, facilita manutenção
✅ Referência leve `username: String` em `TrainingRecord` — evita FK física para User, mesma estratégia de outros módulos (CAPAs, AuditLog)
✅ `GedFileValidator` reutilizado para validação MIME de certificados PDF — sem duplicação de lógica de validação (ADR-049 Decisão 2)
✅ `expiresAt` calculado no use case com `validityMonths` — regra de negócio testável sem depender de função de banco
✅ Cálculo de CompetencyMatrix em Java — `LocalDate.now()` como referência, testável via mock de relógio
✅ Debounce de alertas reutiliza `existsByTitleAndCreatedAtAfter` já disponível — sem novo mecanismo de deduplicação
✅ Notificações pessoais por colaborador (não broadcast) — alerta relevante apenas para o afetado

⚠️ `GET /api/v1/training/competency-matrix` carrega todos os usuários ativos e todos os registros de treinamento em memória — para equipes > 500 pessoas, avaliar paginação ou cache `@Cacheable` com TTL 5 min (padrão `CacheConfig` ADR-046)
⚠️ `TrainingExpiryAlertJob` roda toda segunda às 08h — para certificações vencidas entre segunda e a próxima segunda, o alerta aparece com até 7 dias de atraso; mover para diário se necessário em sprint futura
⚠️ Soft-delete de `TrainingCourse` bloqueado quando existem registros recentes — definir "recentes" no use case (sugerido: registros nos últimos 12 meses); caso contrário, histórico orphan é impossível pois `course_id` é FK não-nullable
⚠️ `certificateStoragePath` armazenado diretamente na entidade (sem `Attachment` genérico de Sprint 21) — decisão consciente para simplicidade; se a política de storage for alterada, dois mecanismos precisarão ser migrados
