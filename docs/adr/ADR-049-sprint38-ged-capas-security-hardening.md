## ADR-049: Sprint 38 — GED & CAPAS Security Hardening

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-113, US-114

---

### Contexto

Beatriz identificou 6 achados de segurança diferidos dos Sprints 36 (GED) e 37 (CAPAS) que não puderam ser corrigidos antes do commit:

| ID | Severidade | Origem | Descrição resumida |
|----|-----------|--------|--------------------|
| SEC-125 | HIGH | Sprint 36 | Upload GED sem validação de MIME type real (Tika magic bytes) |
| SEC-126 | HIGH | Sprint 36 | Path traversal via `getOriginalFilename()` + `code` sem regex |
| SEC-127 | MEDIUM | Sprint 36 | `GedController` sem `@Validated`; `changeReason` sem constraints |
| SEC-128 | MEDIUM | Sprint 36 | `DocumentRevisionResponse.uploadedBy` exposto para OPERATOR+ |
| SEC-129 | MEDIUM | Sprint 36 | Race condition `existsByCode + save` sem handler |
| SEC-139 | LOW | Sprint 37 | TOCTOU no auto-close NC em `VerifyEffectivenessUseCase` |

Os dois HIGH são bloqueadores de produção — upload de HTML mascarado como PDF já é exploração real com URLs pré-assinadas MinIO. Esta ADR define as decisões arquiteturais para liquidar todo o débito.

---

### Decisão 1 — MIME type validation via Apache Tika (SEC-125)

Apache Tika já está no `pom.xml` (adicionado em Sprint 29, ADR-046, para `ExcelFileValidator`). Cria-se `GedFileValidator` seguindo o mesmo padrão estabelecido.

**Tipos permitidos para documentos controlados**:

```java
// qms/ged/application/usecase/GedFileValidator.java
@Component
public class GedFileValidator {

    private static final Tika TIKA = new Tika();
    private static final Set<String> ALLOWED_MIME = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",   // .docx
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"          // .xlsx
    );
    private static final long MAX_SIZE_BYTES = 50L * 1024 * 1024; // 50 MB

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new InvalidGedFileException("Arquivo não pode ser vazio.");

        if (file.getSize() > MAX_SIZE_BYTES)
            throw new InvalidGedFileException(
                "Arquivo excede o tamanho máximo permitido (50 MB).");

        try {
            String detectedMime = TIKA.detect(file.getBytes(), file.getOriginalFilename());
            if (!ALLOWED_MIME.contains(detectedMime))
                throw new InvalidGedFileException(
                    "Tipo de arquivo não permitido: " + detectedMime
                    + ". Aceitos: PDF, DOCX, XLSX.");
        } catch (IOException e) {
            throw new InvalidGedFileException("Não foi possível verificar o tipo do arquivo.");
        }
    }
}
```

`InvalidGedFileException` é nova exceção de domínio em `qms/ged/domain/`; `GlobalExceptionHandler` a mapeia para `422 Unprocessable Entity`.

`GedFileValidator.validate(file)` é chamado como **primeira instrução** em `UploadDocumentUseCase.execute()` e `AddRevisionUseCase.execute()` — antes de qualquer acesso ao `StorageService`.

**Frontend (SEC-135)**: adicionar `accept=".pdf,.docx,.xlsx"` nos dois `<input type="file">` — melhora UX sem substituir validação backend.

Alternativa descartada: validar apenas `Content-Type` do request (declarado pelo cliente) — trivialmente bypassável.

---

### Decisão 2 — Prevenção de path traversal (SEC-126)

Dois vetores independentes; corrigidos em camadas distintas.

**Vetor 1 — `originalFilename` na composição do `storagePath`**:

```java
// em UploadDocumentUseCase e AddRevisionUseCase — antes de compor storagePath
String safeFilename = Optional.ofNullable(file.getOriginalFilename())
    .map(name -> Paths.get(name).getFileName())
    .map(Path::toString)
    .filter(s -> !s.isBlank())
    .orElse("file");

String storagePath = "ged/" + sanitizedCode + "/" + UUID.randomUUID() + "_" + safeFilename;
```

`Paths.get(name).getFileName()` extrai apenas o componente final do nome — `../../../secret.pdf` → `secret.pdf`. Mesmo padrão do `ExcelFileValidator` (SEC-107, ADR-046).

**Vetor 2 — campo `code` da requisição**:

```java
// CreateDocumentRequest.java
public record CreateDocumentRequest(
    @NotBlank
    @Size(max = 20)
    @Pattern(
        regexp = "^[A-Z0-9\\-]{3,20}$",
        message = "Código deve conter apenas letras maiúsculas, números e hífens (3-20 caracteres)"
    )
    String code,
    // ...
) {}
```

Regex `^[A-Z0-9\\-]{3,20}$` bloqueia qualquer tentativa de traversal via `code` — `../etc` falha na validação Bean antes de atingir o use case.

**`GedController` precisa de `@Validated`** (ver Decisão 3) para que Bean Validation nos `@RequestBody` records seja ativada.

---

### Decisão 3 — @Validated no GedController + constraints em changeReason (SEC-127)

Sem `@Validated` na classe do controller, `@Pattern` e `@Size` em `@RequestParam` lançam `ConstraintViolationException` sem ser capturada pelo `GlobalExceptionHandler` (ADR-031). `@RequestBody` com `@Valid` continua funcionando independentemente — porém é boas-práticas adicionar `@Validated` na classe para cobrir ambos os casos.

```java
// GedController.java
@RestController
@RequestMapping("/api/v1/qms/ged/documents")
@Validated                          // ← adicionado (ADR-031)
public class GedController { ... }
```

`changeReason` no endpoint de nova revisão:

```java
@PostMapping(value = "/{id}/revisions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public ResponseEntity<DocumentRevisionResponse> addRevision(
        @PathVariable UUID id,
        @RequestParam @NotBlank @Size(max = 1000) String changeReason,
        @RequestPart MultipartFile file,
        Principal principal) { ... }
```

`ConstraintViolationException` → 400 pelo `GlobalExceptionHandler` (ADR-031 já trata).

---

### Decisão 4 — Mascaramento de uploadedBy em DocumentRevisionResponse (SEC-128)

`DocumentRevision.uploadedBy` armazena o username de quem fez o upload — dado de autoria interna. Expô-lo para OPERATOR+ viola ADR-041 Decisão 7 (padrão estabelecido desde Sprint 29: dados de autoria são internos; não devem aparecer em responses acessíveis a roles não-admin).

```java
// DocumentRevisionResponse.java — ANTES
public record DocumentRevisionResponse(
    UUID id, String revisionNumber, String originalFileName,
    Long fileSizeBytes, String uploadedBy,    // ← remove
    LocalDateTime uploadedAt, String changeReason
) { ... }

// DocumentRevisionResponse.java — DEPOIS
public record DocumentRevisionResponse(
    UUID id, String revisionNumber, String originalFileName,
    Long fileSizeBytes,
    LocalDateTime uploadedAt, String changeReason
) { ... }
```

O campo `uploadedBy` permanece na entidade `DocumentRevision` (persistido no banco para rastreabilidade) e disponível via `AuditLog` para ADMIN.

Alternativa descartada: endpoint ADMIN-only separado para retornar `uploadedBy` — over-engineering para o volume (~53 usuários internos); log de auditoria cobre o caso de uso real de rastreabilidade.

---

### Decisão 5 — Handler de DataIntegrityViolationException para código duplicado (SEC-129)

`existsByCode(code) → save` não é atômico — em concorrência, dois threads passam pelo check e ambos tentam inserir o mesmo `code`, gerando `DataIntegrityViolationException` (índice único `ged_document.code`) → 500 ISE sem tratamento.

Fix em duas camadas:

```java
// UploadDocumentUseCase.execute() — wrap do save
try {
    Document saved = documentRepository.save(document);
    // ... (criar revision, etc.)
    return DocumentResponse.from(saved);
} catch (DataIntegrityViolationException e) {
    throw new DocumentCodeAlreadyExistsException(request.code());
}
```

```java
// qms/ged/domain/DocumentCodeAlreadyExistsException.java
public class DocumentCodeAlreadyExistsException extends RuntimeException {
    public DocumentCodeAlreadyExistsException(String code) {
        super("Já existe um documento com o código: " + code);
    }
}
```

```java
// GlobalExceptionHandler.java — novo handler
@ExceptionHandler(DocumentCodeAlreadyExistsException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public ErrorResponse handleDocumentCodeAlreadyExists(DocumentCodeAlreadyExistsException ex) {
    return new ErrorResponse(ex.getMessage());
}
```

409 CONFLICT é semântica correta para "recurso com esse identificador já existe".

Alternativa descartada: lock pessimista na query `existsByCode` — mais complexo e desnecessário quando o índice único do banco já garante atomicidade; capturar a exceção de integridade é a abordagem padrão Spring.

---

### Decisão 6 — TOCTOU fix no auto-close NC via lock pessimista (SEC-139)

`VerifyEffectivenessUseCase` lê `existsByNonConformanceIdAndStatusIn(ncId, ...)` após salvar a ação como `DONE`. Em concorrência, dois requests podem ler `hasOpen=false` e ambos fecham a NC — `closedBy` do segundo sobrescreve o do primeiro, gerando inconsistência de auditoria.

Fix: substituir `findById` por `findByIdForUpdate` com lock pessimista na leitura da ação antes da transição de status — serializa as verificações concorrentes ao nível do banco.

```java
// CorrectiveActionRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM CorrectiveAction a WHERE a.id = :id")
Optional<CorrectiveAction> findByIdForUpdate(@Param("id") UUID id);
```

```java
// VerifyEffectivenessUseCase.execute()
CorrectiveAction action = actionRepository.findByIdForUpdate(actionId)   // ← lock
        .orElseThrow(() -> new ActionNotFoundException(actionId));
```

O lock `PESSIMISTIC_WRITE` (SELECT FOR UPDATE no PostgreSQL) garante que o segundo request aguarda o commit do primeiro antes de prosseguir. Dentro da `@Transactional`, ao final do primeiro request o lock é liberado — o segundo request lê `hasOpen=true` e não fecha a NC novamente.

Alternativa descartada: `@Transactional(isolation = SERIALIZABLE)` em toda a transação — custo de lock maior do que necessário; o lock pontual na linha da ação é suficiente.

---

### Contrato de API

Nenhum endpoint novo é adicionado. Mudanças de contrato:

| Endpoint | Mudança | Impacto |
|----------|---------|---------|
| `POST /documents` | `code` agora validado com `@Pattern` → 400 se inválido | Breaking para clientes que enviavam `code` com chars especiais |
| `POST /{id}/revisions` | `changeReason` agora `@NotBlank @Size(max=1000)` → 400 se vazio/longo | Breaking para clientes sem `changeReason` |
| `GET /documents/{id}` | `DocumentRevisionResponse` sem campo `uploadedBy` | Breaking para clientes que consumiam o campo |
| `POST /documents` | Duplicate `code` → 409 CONFLICT (era 500) | Correção de comportamento (não breaking semântico) |

**`InvalidGedFileException` response** (novo, 422):
```json
{ "message": "Tipo de arquivo não permitido: text/html. Aceitos: PDF, DOCX, XLSX." }
```

**`DocumentCodeAlreadyExistsException` response** (novo, 409):
```json
{ "message": "Já existe um documento com o código: SOP-001" }
```

---

### Consequências

✅ SEC-125/126 (HIGH) liquidados — upload de arquivo malicioso mascarado como PDF é bloqueado em `GedFileValidator.validate()` antes de qualquer operação de storage
✅ Path traversal via `originalFilename` e `code` cobertos por duas camadas independentes (sanitização no use case + Bean Validation no DTO)
✅ `@Validated` + `changeReason` constraints eliminam truncamento silencioso e erros 500 ISE por dados inválidos
✅ `uploadedBy` removido do response — consistente com ADR-041 Decisão 7 (padrão do projeto para dados de autoria)
✅ Race condition no upload → 409 determinístico em vez de 500 ISE (melhor experiência + rastreabilidade)
✅ TOCTOU no auto-close NC eliminado com lock pessimista cirúrgico — sem impacto de performance em fluxos normais (operação de baixa frequência)
✅ `GedFileValidator` segue exatamente o padrão do `ExcelFileValidator` (SEC-107) — consistência de implementação de segurança no projeto

⚠️ `accept=".pdf,.docx,.xlsx"` no frontend melhora UX mas **não substitui** validação backend — clientes API sem frontend continuam podendo enviar qualquer MIME (bloqueado pelo Tika no backend)
⚠️ Lock `PESSIMISTIC_WRITE` em `findByIdForUpdate` adiciona latência de banco em verificações de eficácia concorrentes — aceitável dado o volume (~53 usuários; verificações de eficácia são operações raras)
⚠️ Remoção de `uploadedBy` de `DocumentRevisionResponse` é breaking change — consumidores existentes do endpoint precisam atualizar; risco baixo (módulo novo, Sprint 36, sem integrações externas conhecidas)
⚠️ `@Pattern(regexp = "^[A-Z0-9\\-]{3,20}$")` em `code` pode rejeitar códigos legítimos já existentes no banco se forem reenviados via API — apenas criação é afetada (não leitura); documentos existentes com `code` fora do padrão permanecem válidos
