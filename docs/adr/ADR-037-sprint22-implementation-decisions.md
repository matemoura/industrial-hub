## ADR-037: Sprint 22 — Decisões de Implementação (SLA/Escalação + Tech Debt Sprint 21)
**Status**: Aprovado
**Data**: 2026-05-21
**US relacionadas**: US-061, US-062

---

### Contexto

O ADR-019 define a arquitetura principal do módulo SLA/escalação (entidade `SlaRule`, flag `slaBreached`, job horário, integração com `NotificationService`). Este ADR complementa o ADR-019 com:

1. Decisões de implementação não cobertas: DTOs, validação do `EscalationUseCase`, estratégia de query para NCs e OSs elegíveis, resposta do `run-now`, package do `EscalationJob`
2. Endereçamento dos itens de tech debt de segurança diferidos do Sprint 21 por Beatriz: SEC-067, SEC-068, SEC-070, SEC-071, SEC-072, SEC-073, SEC-074

---

### Decisão 1 — DTOs do módulo SLA

```java
// CreateSlaRuleRequest.java
public record CreateSlaRuleRequest(
    @NotNull SlaEntityType entityType,
    @NotBlank @Size(max = 30) String classifierValue,
    @NotNull @Min(1) @Max(8760) Integer slaHours,   // máx 1 ano em horas
    boolean escalateByEmail
) {}

// UpdateSlaRuleRequest.java
public record UpdateSlaRuleRequest(
    @NotNull @Min(1) @Max(8760) Integer slaHours,
    boolean escalateByEmail
) {}

// SlaRuleResponse.java
public record SlaRuleResponse(
    UUID id,
    SlaEntityType entityType,
    String classifierValue,
    int slaHours,
    boolean escalateByEmail,
    boolean active
) {
    public static SlaRuleResponse from(SlaRule r) { ... }
}

// EscalationRunResponse.java
public record EscalationRunResponse(int breachedNcs, int breachedWorkOrders) {}
```

**Justificativa**: `@Max(8760)` impede regras absurdas (> 1 ano); `classifierValue` limitado a 30 chars alinhado com `@Column(length=30)` da entidade (ADR-019 Decisão 1).

---

### Decisão 2 — Queries do `EscalationUseCase`

`EscalationUseCase` não usa `@Scheduled` diretamente — é um `@Service` puro chamado pelo `EscalationJob` e pelo endpoint `run-now`. Isso facilita testes unitários sem mocking de timers.

```java
// EscalationJob.java — pacote common/application/
@Component
@RequiredArgsConstructor
public class EscalationJob {

    private final EscalationUseCase escalationUseCase;

    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    public void run() {
        escalationUseCase.execute();
    }
}
```

```java
// EscalationUseCase.java
@Service
@Transactional
@RequiredArgsConstructor
public class EscalationUseCase {

    private final SlaRuleRepository slaRuleRepository;
    private final NonConformanceRepository ncRepository;
    private final WorkOrderRepository workOrderRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public EscalationRunResponse execute() {
        List<SlaRule> rules = slaRuleRepository.findByActiveTrue();
        int breachedNcs = 0;
        int breachedWorkOrders = 0;
        LocalDateTime now = LocalDateTime.now();

        for (SlaRule rule : rules) {
            LocalDateTime deadline = now.minusHours(rule.getSlaHours());

            if (rule.getEntityType() == SlaEntityType.NC) {
                List<NonConformance> breached = ncRepository
                    .findBreachCandidates(rule.getClassifierValue(), deadline);
                for (NonConformance nc : breached) {
                    nc.setSlaBreached(true);
                    nc.setSlaBreachedAt(now);
                    breachedNcs++;
                    notifySlaBreached("NC", nc.getId().toString(),
                        nc.getTitle(), rule, now);
                }
            } else {
                List<WorkOrder> breached = workOrderRepository
                    .findBreachCandidates(rule.getClassifierValue(), deadline);
                for (WorkOrder wo : breached) {
                    wo.setSlaBreached(true);
                    wo.setSlaBreachedAt(now);
                    breachedWorkOrders++;
                    notifySlaBreached("Work Order", wo.getId().toString(),
                        wo.getTitle(), rule, now);
                }
            }
        }
        return new EscalationRunResponse(breachedNcs, breachedWorkOrders);
    }
}
```

**Queries JPQL nos repositórios**:

```java
// NonConformanceRepository.java
@Query("""
    SELECT nc FROM NonConformance nc
    WHERE nc.severity = :classifierValue
      AND nc.status NOT IN ('CLOSED')
      AND nc.slaBreached = false
      AND nc.reportedAt <= :deadline
    """)
List<NonConformance> findBreachCandidates(
    @Param("classifierValue") String classifierValue,
    @Param("deadline") LocalDateTime deadline);

// WorkOrderRepository.java
@Query("""
    SELECT wo FROM WorkOrder wo
    WHERE wo.priority = :classifierValue
      AND wo.status NOT IN ('DONE', 'CANCELLED')
      AND wo.slaBreached = false
      AND wo.openedAt <= :deadline
    """)
List<WorkOrder> findBreachCandidates(
    @Param("classifierValue") String classifierValue,
    @Param("deadline") LocalDateTime deadline);
```

**Justificativa**: comparar `classifierValue` (string) com o valor do enum em JPQL é válido quando os enums usam `@Enumerated(EnumType.STRING)` — sem necessidade de cast nativo. Idempotência garantida pelo filtro `slaBreached = false`.

---

### Decisão 3 — Notificação de SLA vencido

O `EscalationUseCase` usa `NotificationService` (ADR-013) para criar broadcast a todos os SUPERVISOR e ADMIN. A busca de destinatários aproveita `UserRepository.findByRoleInAndActiveTrue(List.of(Role.SUPERVISOR, Role.ADMIN))` — já existente no projeto.

```java
private void notifySlaBreached(String entityLabel, String entityId,
                                String title, SlaRule rule,
                                LocalDateTime now) {
    String msg = String.format("SLA vencido: %s '%s' ultrapassou %dh",
        entityLabel, title, rule.getSlaHours());
    notificationService.broadcastToSupervisors(msg, NotificationSeverity.CRITICAL);
    // e-mail assíncrono, se habilitado — trata falha silenciosamente via log.warn
    if (rule.isEscalateByEmail()) {
        emailEscalator.sendAsync(entityLabel, entityId, title, rule);
    }
}
```

E-mail assíncrono implementado via `@Async` + `JavaMailSender` em `EmailEscalationService` — falha não interrompe o loop de escalação (try-catch com `log.warn`).

---

### Decisão 4 — Filtros `slaBreached` nas listagens existentes

Os endpoints `GET /api/v1/qms/non-conformances` e `GET /api/v1/maintenance/work-orders` ganham parâmetro `?slaBreached=true` (Boolean, opcional).

Padrão de filtro condicional JPQL (mesmo padrão já adotado em ADR-034 Decisão 5 para `shiftId`):

```jpql
-- NonConformanceRepository.findWithFilters (trecho adicionado)
AND (:slaBreached IS NULL OR nc.slaBreached = :slaBreached)
```

Os use cases de listagem existentes recebem o novo parâmetro `Boolean slaBreached` com valor `null` como padrão (sem filtro).

---

### Decisão 5 — Package completo do módulo SLA

```
common/
├── domain/
│   ├── SlaRule.java             (ADR-019 Decisão 1)
│   └── SlaEntityType.java       (NC | WORK_ORDER)
├── application/
│   ├── dto/
│   │   ├── SlaRuleResponse.java
│   │   ├── CreateSlaRuleRequest.java
│   │   ├── UpdateSlaRuleRequest.java
│   │   └── EscalationRunResponse.java
│   └── usecase/
│       ├── CreateSlaRuleUseCase.java
│       ├── GetSlaRuleListUseCase.java
│       ├── UpdateSlaRuleUseCase.java
│       ├── DeleteSlaRuleUseCase.java
│       └── EscalationUseCase.java
├── infrastructure/
│   └── SlaRuleRepository.java
├── presentation/
│   └── SlaRuleController.java   (/api/v1/admin/sla-rules)
└── EscalationJob.java           — @Component com @Scheduled
```

`EscalationJob` fica no pacote `common/` (raiz) por ser infraestrutura de execução, não lógica de negócio.

---

### Decisão 6 — Tech Debt SEC-067: `@Size(max=50)` em `CreateSparePartRequest.code`

**Problema**: `CreateSparePartRequest.code` tem apenas `@NotBlank` sem bound de tamanho. A entidade `SparePart.code` tem `@Column(length=50)`. Um código com > 50 chars dispara `DataIntegrityViolationException`, que o `try-catch` de `CreateSparePartUseCase` converte incorretamente em `SparePartDuplicateCodeException` (409) — cliente recebe 409 em vez de 400.

**Correção**:

```java
// CreateSparePartRequest.java — ANTES
@NotBlank String code,

// CreateSparePartRequest.java — DEPOIS
@NotBlank @Size(max = 50) String code,
```

Bean Validation intercepta o erro antes de chegar ao banco, retornando 400 com `{ "message": "..." }` via `GlobalExceptionHandler`. O `try-catch(DataIntegrityViolationException)` existente em `CreateSparePartUseCase` passa a ser acionado apenas pelo conflito de `UNIQUE` constraint real.

---

### Decisão 7 — Tech Debt SEC-068: bound de tamanho em `SparePart.unit`

**Problema**: `SparePart.unit` não tem `@Column(length=...)` — Hibernate gera `VARCHAR(255)` por padrão. `CreateSparePartRequest.unit` não tem `@Size`. Input acima de 255 chars causaria `DataTruncationException` silenciosa ou erro de DB.

**Correção**:

```java
// SparePart.java — ANTES
private String unit;

// SparePart.java — DEPOIS
@Column(length = 50)
private String unit;
```

```java
// CreateSparePartRequest.java — ANTES
String unit,

// CreateSparePartRequest.java — DEPOIS
@Size(max = 50) String unit,
```

```java
// UpdateSparePartRequest.java — verificar e adicionar se ausente
@Size(max = 50) String unit,
```

Migration necessária: `ALTER TABLE spare_part ALTER COLUMN unit TYPE VARCHAR(50);` — sem perda de dados (campo controlado por formulário, valores existentes são curtos como "kg", "un", "L").

---

### Decisão 8 — Tech Debt SEC-070: path traversal via `originalFilename` na S3 key

**Problema**: a chave S3 é construída como `entityType.toLowerCase() + "/" + entityId + "/" + UUID.randomUUID() + "-" + safeFilename`. Embora `safeFilename = new File(originalFilename).getName()` remova separadores de diretório, caracteres especiais (espaços, `#`, `?`, `+`) podem causar problemas em presigned URLs.

**Correção — sanitização adicional do nome de arquivo**:

```java
// UploadAttachmentUseCase.java — substituir bloco de safeFilename
String safeFilename = sanitizeFilename(new java.io.File(originalFilename).getName());

// método privado
private static String sanitizeFilename(String name) {
    // mantém apenas [a-zA-Z0-9._-], substitui o resto por underscore
    String sanitized = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    // limita tamanho para evitar keys excessivamente longas no S3
    return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
}
```

A chave S3 final continua sendo `entityType/entityId/UUID-safeFilename` — o prefixo UUID garante unicidade mesmo após sanitização. Logs de auditoria continuam usando o `originalName` (nome legível ao usuário) armazenado na entidade.

---

### Decisão 9 — Tech Debt SEC-071: verificação de magic bytes com Apache Tika

**Problema**: o `Content-Type` HTTP enviado pelo cliente é trivialmente spoofável (ex: renomear `script.js` para `document.pdf` e enviar `Content-Type: application/pdf`). O use case confia apenas no `file.getContentType()` declarado.

**Solução adotada**: verificação de magic bytes com Apache Tika (`tika-core`) — biblioteca leve (sem `tika-parsers`) que detecta o MIME type real lendo os primeiros bytes do arquivo.

**Dependência Maven**:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

**Implementação em `UploadAttachmentUseCase`**:

```java
private static final Tika TIKA = new Tika();
private static final Set<String> ALLOWED_TYPES = Set.of(
    "image/jpeg", "image/png", "image/webp", "application/pdf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel"
);

// No método execute(), substituir a validação de contentType:
String detectedType;
try {
    detectedType = TIKA.detect(file.getInputStream(), safeFilename);
} catch (IOException ex) {
    throw new StorageException("Falha ao ler arquivo para validação", ex);
}

if (!ALLOWED_TYPES.contains(detectedType)) {
    throw new InvalidFileTypeException(detectedType);
}

// Usar detectedType (não file.getContentType()) como contentType
// para persistir e enviar ao storage
String contentType = detectedType;
```

**Justificativa**: `tika-core` é thread-safe, a instância `static final Tika` é reaproveitada. Leitura dos primeiros bytes (até 8 KB) é suficiente para detecção — sem carregar o arquivo completo em memória duas vezes; o `InputStream` de `MultipartFile` é consumido uma vez e o arquivo já foi bufferizado pelo multipart resolver do Spring.

**Atenção**: `MultipartFile.getInputStream()` pode ser chamado múltiplas vezes se o arquivo estiver em disco (Spring copia para temp file quando > `spring.servlet.multipart.file-size-threshold`). Para arquivos pequenos (< threshold) em memória, a segunda chamada após consumo lança `IOException`. **Estratégia**: chamar `TIKA.detect()` **antes** do `storageService.upload()` usando o mesmo stream, ou usar `file.getBytes()` com `new ByteArrayInputStream()` para garantir reusabilidade:

```java
byte[] bytes = file.getBytes(); // carrega uma vez
String detectedType = TIKA.detect(bytes, safeFilename);
if (!ALLOWED_TYPES.contains(detectedType)) {
    throw new InvalidFileTypeException(detectedType);
}
// upload usa ByteArrayInputStream — sem problema de stream consumido
storageService.upload(key, new ByteArrayInputStream(bytes), contentType, bytes.length);
```

**Limite de 10 MB** já existente garante que `file.getBytes()` não excede heap disponível.

---

### Decisão 10 — Tech Debt SEC-072: `entityType` sem enum tipado no use case

**Problema**: o controller tem `@Pattern(regexp = "^(WORK_ORDER|NON_CONFORMANCE|SPARE_PART)$")` em `entityType`, mas o parâmetro chega aos use cases como `String entityType` — sem type safety. Um bug no controller poderia deixar passar valores arbitrários.

**Solução**: introduzir `AttachmentEntityType` enum no domínio e converter no controller antes de passar ao use case.

```java
// common/domain/AttachmentEntityType.java
public enum AttachmentEntityType {
    WORK_ORDER, NON_CONFORMANCE, SPARE_PART;

    public String toStoragePrefix() {
        return name().toLowerCase().replace("_", "-");
    }
}
```

**Controller** — substituir `@Pattern` + `String` por conversão explícita:

```java
// AttachmentController.java — ANTES
@RequestParam @Pattern(regexp = "^(WORK_ORDER|NON_CONFORMANCE|SPARE_PART)$", ...) String entityType,

// AttachmentController.java — DEPOIS
@RequestParam String entityType,  // conversão abaixo; Spring não converte enum de @RequestParam por padrão
```

No corpo do método:

```java
AttachmentEntityType type;
try {
    type = AttachmentEntityType.valueOf(entityType.toUpperCase());
} catch (IllegalArgumentException ex) {
    throw new jakarta.validation.ConstraintViolationException(
        "entityType must be WORK_ORDER, NON_CONFORMANCE or SPARE_PART", Set.of());
}
return uploadAttachment.execute(type, entityId, file, auth.getName());
```

Os use cases recebem `AttachmentEntityType type` em vez de `String entityType`. A coluna `entity_type` em `Attachment` continua `VARCHAR(50)` — persiste `type.name()`.

**Alternativa considerada e rejeitada**: converter diretamente via `@RequestParam AttachmentEntityType entityType` — Spring tentaria conversão automática mas lançaria `MethodArgumentTypeMismatchException` com mensagem técnica. A conversão manual permite retornar a mensagem amigável padrão do projeto `{ "message": "..." }` via `GlobalExceptionHandler`.

---

### Decisão 11 — Tech Debt SEC-073: validação de tamanho no `UploadAttachmentUseCase`

**Problema**: a validação `file.size <= 10 MB` existe apenas no frontend Angular. O backend confia no `spring.servlet.multipart.max-file-size=10MB` do application.properties, mas não valida explicitamente no use case.

**Correção**: adicionar validação explícita no use case, antes de qualquer leitura do arquivo:

```java
// UploadAttachmentUseCase.execute() — logo após validar filename
if (file.getSize() > MAX_SIZE) {
    throw new FileTooLargeException(file.getSize(), MAX_SIZE);
}
```

```java
// common/domain/FileTooLargeException.java
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(long actual, long max) {
        super(String.format("Arquivo muito grande: %d bytes (máx %d bytes)", actual, max));
    }
}
```

```java
// GlobalExceptionHandler.java — adicionar handler
@ExceptionHandler(FileTooLargeException.class)
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public Map<String, String> handle(FileTooLargeException ex) {
    return Map.of("message", ex.getMessage());
}
```

**Nota**: o `MultipartException` do Spring (quando o arquivo excede `max-file-size`) é capturado pelo `GlobalExceptionHandler` existente e retorna 413. A validação no use case é uma segunda linha de defesa para upload programático que bypasse o multipart resolver (ex: teste de integração).

---

### Decisão 12 — Tech Debt SEC-074: `window.open` sem `noopener,noreferrer`

**Problema**: o botão "Download" no `attachment-list.component.ts` usa `window.open(res.url, '_blank')` — sem `noopener,noreferrer`. A nova aba pode acessar `window.opener` da aba pai (tabnapping).

**Correção**:

```typescript
// attachment-list.component.ts — ANTES
window.open(res.url, '_blank');

// attachment-list.component.ts — DEPOIS
window.open(res.url, '_blank', 'noopener,noreferrer');
```

Mudança de uma linha — sem impacto funcional. URLs pré-assinadas do S3/MinIO expiram em 15 min, então o risco já é baixo, mas a correção é trivial e elimina o achado.

---

### Decisão 13 — Tech Debt SEC-065 (diferido do Sprint 20): auditoria dos shift use cases

**Problema**: `CreateShiftUseCase`, `UpdateShiftUseCase` e `DeactivateShiftUseCase` não registram entradas no `AuditLog`. Os valores `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DEACTIVATED` já existem em `AuditAction` (adicionados no Sprint 20 via ADR-035 Decisão 3), mas os use cases não injetam `AuditService`.

**Correção**: injetar `AuditService` nos três use cases e chamar `auditService.log()` após `save()`/`deactivate()`, seguindo o padrão de `CreateSparePartUseCase`:

```java
// CreateShiftUseCase.java — trecho
auditService.log(createdBy, AuditAction.SHIFT_CREATED, "Shift",
    saved.getId().toString(), Map.of("name", saved.getName()));

// UpdateShiftUseCase.java — trecho
auditService.log(updatedBy, AuditAction.SHIFT_UPDATED, "Shift",
    saved.getId().toString(), Map.of("name", saved.getName()));

// DeactivateShiftUseCase.java — trecho
auditService.log(deactivatedBy, AuditAction.SHIFT_DEACTIVATED, "Shift",
    id.toString(), Map.of());
```

`createdBy`/`updatedBy`/`deactivatedBy` são passados ao use case como `String username` pelo controller via `principal.getName()` — já é o padrão do projeto.

---

### Contrato de API

#### Endpoints novos — módulo SLA (ADR-019 Decisão 5, confirmados)

| Método | Endpoint | Auth | Status HTTP |
|--------|----------|------|-------------|
| GET | /api/v1/admin/sla-rules | ADMIN | 200 `List<SlaRuleResponse>` |
| POST | /api/v1/admin/sla-rules | ADMIN | 201 `SlaRuleResponse` |
| PUT | /api/v1/admin/sla-rules/{id} | ADMIN | 200 `SlaRuleResponse` |
| DELETE | /api/v1/admin/sla-rules/{id} | ADMIN | 204 |
| POST | /api/v1/admin/sla-rules/run-now | ADMIN | 200 `EscalationRunResponse` |

#### Filtros adicionados a endpoints existentes

| Método | Endpoint | Parâmetro novo | Descrição |
|--------|----------|----------------|-----------|
| GET | /api/v1/qms/non-conformances | `slaBreached` (Boolean, opcional) | Filtra NCs com SLA vencido |
| GET | /api/v1/maintenance/work-orders | `slaBreached` (Boolean, opcional) | Filtra OSs com SLA vencido |

#### Migration de banco

1. Nova tabela `sla_rule` (conforme ADR-019 Decisão 1)
2. `ALTER TABLE non_conformance ADD COLUMN sla_breached BOOLEAN NOT NULL DEFAULT FALSE`
3. `ALTER TABLE non_conformance ADD COLUMN sla_breached_at TIMESTAMP`
4. `ALTER TABLE work_order ADD COLUMN sla_breached BOOLEAN NOT NULL DEFAULT FALSE`
5. `ALTER TABLE work_order ADD COLUMN sla_breached_at TIMESTAMP`
6. `ALTER TABLE spare_part ALTER COLUMN unit TYPE VARCHAR(50)` (SEC-068)

---

### Consequências

✅ `EscalationUseCase` como `@Service` puro (sem `@Scheduled`) — testável sem contexto Spring completo; `EscalationJob` é a única classe com `@Scheduled`
✅ Verificação de magic bytes com Tika elimina content-type spoofing — arquivo `.js` renomeado para `.pdf` é rejeitado com 400
✅ `AttachmentEntityType` enum introduz type safety no domínio — erros de entityType detectados em compile time nos use cases
✅ `@Size(max=50)` em `CreateSparePartRequest.code` elimina bug onde código > 50 chars gerava 409 (duplicado falso) em vez de 400
✅ `FileTooLargeException` com 413 explícito no use case garante resposta correta mesmo em testes de integração que bypassa multipart resolver
✅ SEC-073 e SEC-074 são correções de uma linha — custo de implementação negligível, risco eliminado
✅ Auditoria dos shift use cases (SEC-065) finalmente endereçada — rastreabilidade completa de turnos no `AuditLog`
⚠️ Apache Tika adiciona ~1.5 MB ao fat JAR (`tika-core` sem parsers) — aceitável; verificado via `./mvnw dependency:analyze` antes do merge
⚠️ `file.getBytes()` para validação Tika carrega arquivo inteiro em memória uma segunda vez — com limite de 10 MB e 53 usuários, sem impacto de heap relevante; revisar se volume crescer
⚠️ Migration `ALTER TABLE spare_part ALTER COLUMN unit TYPE VARCHAR(50)` pode falhar se houver registros com `unit` > 50 chars — improvável (campo preenchido via formulário), mas executar com `UPDATE spare_part SET unit = LEFT(unit, 50) WHERE LENGTH(unit) > 50` preventivamente
⚠️ `classifierValue` continua como string (decisão do ADR-019) — se novos valores de `NcSeverity` ou `WorkOrderPriority` forem adicionados, regras SLA correspondentes precisam ser criadas manualmente via ADMIN; sem FK, não há cascade automático
⚠️ `window.open(..., 'noopener,noreferrer')` com URL pré-assinada — alguns proxies corporativos bloqueiam abertura de nova aba com esses atributos; improvável no contexto de 53 usuários internos, mas documentar como risco operacional
