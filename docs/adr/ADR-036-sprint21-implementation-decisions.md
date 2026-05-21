## ADR-036: Sprint 21 — Decisões de Implementação (Anexos + Tech Debt Sprint 20)
**Status**: Aprovado
**Data**: 2026-05-21
**US relacionadas**: US-059, US-060, US-049 (SH-48), US-050 (SH-49, SH-50)

### Contexto

O ADR-018 define a arquitetura principal do módulo de anexos (entidade, storage, endpoints, frontend). Esta ADR complementa o ADR-018 com decisões de implementação não cobertas originalmente: contrato da interface `StorageService`, estratégia de atomicidade upload/delete, hierarquia de exceções, e correções de débito técnico do Sprint 20 identificadas por Helena (SH-48, SH-49, SH-50) que possuem impacto arquitetural.

---

### Decisão 1 — `StorageService`: interface e implementação

```java
// common/infrastructure/StorageService.java
public interface StorageService {

    /**
     * Faz upload de um objeto ao bucket configurado.
     * @throws StorageException se o storage estiver indisponível
     */
    void upload(String key, InputStream content, String contentType, long sizeBytes);

    /**
     * Gera URL pré-assinada com TTL configurável.
     * @throws StorageException se não for possível gerar a URL
     */
    String generatePresignedUrl(String key, Duration ttl);

    /**
     * Remove um objeto do bucket. Best-effort — não lança exceção se o objeto
     * não existir (idempotente); lança StorageException apenas em falha de
     * conectividade.
     */
    void delete(String key);
}
```

```java
// common/infrastructure/S3StorageService.java
@Service
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${app.storage.bucket}")
    private String bucket;

    // ... injeção via constructor com S3Config @Configuration

    @Override
    public void upload(String key, InputStream content, String contentType, long sizeBytes) {
        try {
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(contentType).contentLength(sizeBytes).build(),
                RequestBody.fromInputStream(content, sizeBytes));
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("Falha no upload para o storage", ex);
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration ttl) {
        try {
            PresignedGetObjectRequest req = presigner.presignGetObject(b -> b
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(bucket).key(key)));
            return req.url().toString();
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("Falha ao gerar URL pré-assinada", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket).key(key).build());
        } catch (NoSuchKeyException ignored) {
            // idempotente — objeto já removido
        } catch (S3Exception | SdkClientException ex) {
            throw new StorageException("Falha ao deletar do storage", ex);
        }
    }
}
```

**Testes**: injetar implementação mock `InMemoryStorageService implements StorageService` nos testes unitários — sem dependência de S3 real.

---

### Decisão 2 — Atomicidade upload: best-effort cleanup

O upload de um anexo envolve dois passos não-atômicos: (1) `storageService.upload()` e (2) `attachmentRepository.save()`. Não há `@Transactional` possível sobre o S3.

**Estratégia adotada**:
- Se S3 falha → não persiste no banco → nenhum objeto órfão. ✅
- Se S3 sucede mas DB falha → executa `storageService.delete(key)` no bloco `catch` antes de propagar a exceção.

```java
// UploadAttachmentUseCase.java
String key = buildKey(entityType, entityId, file.getOriginalFilename());
try {
    storageService.upload(key, file.getInputStream(), file.getContentType(), file.getSize());
} catch (StorageException ex) {
    throw ex; // propagado como 502
}

Attachment attachment = Attachment.builder()
    .entityType(entityType).entityId(entityId.toString())
    .storageKey(key).originalName(file.getOriginalFilename())
    .contentType(file.getContentType()).fileSizeBytes(file.getSize())
    .uploadedBy(uploadedBy).uploadedAt(LocalDateTime.now())
    .build();
try {
    Attachment saved = attachmentRepository.save(attachment);
    auditService.log(uploadedBy, AuditAction.ATTACHMENT_UPLOADED, "Attachment",
        saved.getId().toString(),
        Map.of("entityType", entityType, "entityId", entityId.toString(),
               "file", file.getOriginalFilename()));
    return AttachmentResponse.from(saved);
} catch (Exception ex) {
    // Best-effort: tenta remover o objeto do S3 para evitar objeto órfão
    try { storageService.delete(key); } catch (StorageException ignored) {}
    throw ex;
}
```

**Justificativa**: objetos órfãos no S3 são indesejáveis mas não críticos (storage MinIO/S3 é barato). A abordagem best-effort é simples e evita a complexidade de um saga pattern desnecessário para 53 usuários internos.

---

### Decisão 3 — Atomicidade delete: S3 delete é best-effort

Na deleção de um anexo, se `storageService.delete(key)` falhar (S3 indisponível), o processo continua e remove o registro do banco.

**Justificativa**: é preferível ter um objeto órfão no S3 (sem referência no banco) do que ter um registro no banco apontando para um arquivo inacessível. Objetos órfãos podem ser limpos por job de reconciliação em sprint futura.

```java
// DeleteAttachmentUseCase.java
@Transactional
public void execute(UUID id, String deletedBy) {
    Attachment attachment = attachmentRepository.findById(id)
        .orElseThrow(() -> new AttachmentNotFoundException(id));
    try {
        storageService.delete(attachment.getStorageKey());
    } catch (StorageException ex) {
        log.warn("Falha ao remover objeto S3 key={}: {}", attachment.getStorageKey(), ex.getMessage());
        // continua — remove do banco mesmo assim
    }
    attachmentRepository.delete(attachment);
    auditService.log(deletedBy, AuditAction.ATTACHMENT_DELETED, "Attachment", id.toString(),
        Map.of("entityType", attachment.getEntityType(), "file", attachment.getOriginalName()));
}
```

---

### Decisão 4 — Tech debt Sprint 20 com impacto arquitetural

#### SH-48 — `CreateSparePartUseCase`: `auditService.log()` dentro do `try-catch(DataIntegrityViolationException)`

Mover `auditService.log()` e `return` para fora do `try-catch`, mantendo apenas `save()` + `flush()` dentro:

```java
SparePart saved;
try {
    saved = sparePartRepository.save(part);
    sparePartRepository.flush();
} catch (DataIntegrityViolationException ex) {
    throw new SparePartDuplicateCodeException(request.code());
}
auditService.log(username, AuditAction.PART_CREATED, "SparePart",
    saved.getId().toString(), Map.of("code", saved.getCode()));
return SparePartResponse.from(saved);
```

#### SH-49 — `RemoveWorkOrderPartUseCase`: lazy load extra em `wop.getWorkOrder().getId()`

Adicionar query ao `WorkOrderPartRepository`:

```java
// WorkOrderPartRepository.java
@Query("SELECT wop.workOrder.id FROM WorkOrderPart wop WHERE wop.id = :partId")
Optional<UUID> findWorkOrderIdByPartId(@Param("partId") UUID partId);
```

E usar no use case para evitar o `SELECT` extra no `WorkOrder`:

```java
UUID actualWorkOrderId = workOrderPartRepository.findWorkOrderIdByPartId(partId)
    .orElseThrow(() -> new WorkOrderPartNotFoundException(partId));
if (!actualWorkOrderId.equals(workOrderId)) {
    throw new WorkOrderPartNotFoundException(partId);
}
// busca o WOP novamente para ter o objeto completo (já sabemos que existe)
WorkOrderPart wop = workOrderPartRepository.findById(partId).orElseThrow();
```

#### SH-50 — `WorkOrderDetailComponent`: necessidade de `GET /work-orders/{id}`

O componente frontend `work-order-detail` atualmente carrega a página 0 da listagem e filtra por ID localmente — não encontra WOs além da posição 20.

Adicionar endpoint `GET /api/v1/maintenance/work-orders/{id}`:

```java
// MaintenanceController.java — novo endpoint
@GetMapping("/work-orders/{id}")
@PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
public WorkOrderResponse getWorkOrder(@PathVariable UUID id) {
    return getWorkOrderDetail.execute(id);
}
```

```java
// GetWorkOrderDetailUseCase.java
@Service
public class GetWorkOrderDetailUseCase {
    public WorkOrderResponse execute(UUID id) {
        return workOrderRepository.findById(id)
            .map(WorkOrderResponse::from)
            .orElseThrow(() -> new WorkOrderNotFoundException(id));
    }
}
```

Frontend: `work-order-detail.component.ts` substitui `listWorkOrders().pipe(map(p => p.content.find(...)))` por chamada direta `maintenanceService.getWorkOrder(id)`.

---

### Contrato de API — Módulo de Anexos

| Método | Endpoint | Auth | Status de sucesso |
|--------|----------|------|-------------------|
| POST | /api/v1/attachments | OPERATOR+ | 201 AttachmentResponse |
| GET | /api/v1/attachments?entityType=X&entityId=Y | OPERATOR+ | 200 List\<AttachmentResponse\> |
| GET | /api/v1/attachments/{id}/download-url | OPERATOR+ | 200 DownloadUrlResponse |
| DELETE | /api/v1/attachments/{id} | SUPERVISOR+ | 204 |
| **GET** | **/api/v1/maintenance/work-orders/{id}** | OPERATOR+ | 200 WorkOrderResponse |

---

### Consequências
✅ `StorageService` interface com `InMemoryStorageService` mock — testes unitários sem S3 real, sem `@SpringBootTest` obrigatório
✅ Best-effort cleanup no upload evita objetos órfãos sem complexidade de saga; best-effort no delete elimina risco de registro sem arquivo acessível
✅ `GET /work-orders/{id}` resolve SH-50 definitivamente — detalhes de qualquer OS acessíveis independente da paginação
✅ SH-48/49 corrigidos dentro do Sprint 21 como parte do primeiro commit de backend — sem sprint extra de tech debt
⚠️ Objetos S3 órfãos (upload sucede, DB falha) são possíveis — best-effort mitiga mas não elimina; job de reconciliação recomendado para Sprint futura se volume crescer
⚠️ `GetWorkOrderDetailUseCase` duplica parcialmente o `GetWorkOrderListUseCase` — aceitável; list use case suporta paginação e filtros enquanto o detail retorna apenas um item por ID
⚠️ URLs pré-assinadas têm TTL de 15 min — thumbnails em listas longas podem expirar se o usuário deixar a aba aberta; frontend deve regenerar na reativação da aba (fora do escopo desta sprint)
