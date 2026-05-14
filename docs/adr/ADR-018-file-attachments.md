## ADR-018: File Attachments — Upload de Documentos e Imagens
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-059, US-060

### Contexto

Técnicos e supervisores precisam anexar evidências (fotos de defeito, relatórios PDF, laudos) a NCs e ordens de serviço. Sem suporte a anexos, estas evidências ficam em emails ou drives externos, desconectadas do sistema. Esta ADR modela o upload de arquivos com armazenamento compatível com S3 (MinIO em desenvolvimento, AWS S3 em produção).

---

### Decisão 1 — Armazenamento: MinIO em dev, S3-compatible em prod

```yaml
# docker-compose.yml — novo serviço
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  ports:
    - "9000:9000"
    - "9001:9001"
```

Bucket único: `industrial-hub-attachments`. Sub-pastas por entidade: `ncs/{ncId}/`, `work-orders/{workOrderId}/`.

**SDK**: `software.amazon.awssdk:s3` (AWS SDK v2) com endpoint configurável via `application.properties`:
```properties
app.storage.endpoint=http://localhost:9000
app.storage.bucket=industrial-hub-attachments
app.storage.access-key=minioadmin
app.storage.secret-key=minioadmin
```

---

### Decisão 2 — Entidade `Attachment`

```java
@Entity
@Table(name = "attachment", indexes = {
    @Index(name = "idx_attach_entity", columnList = "entity_type, entity_id")
})
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String entityType;   // "NonConformance" | "WorkOrder"
    private String entityId;     // UUID as string

    @Column(nullable = false, length = 500)
    private String storageKey;   // path no bucket (ex: "ncs/abc123/foto.jpg")

    @Column(nullable = false, length = 255)
    private String originalName; // nome original do arquivo

    @Column(nullable = false, length = 100)
    private String contentType;  // "image/jpeg", "application/pdf", etc.

    private Long fileSizeBytes;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
```

**Sem FK para `NonConformance` ou `WorkOrder`** — referência por `entityType + entityId` (string), igual ao padrão `AuditLog`. Evita acoplamento entre módulos.

---

### Decisão 3 — Fluxo de upload

```
1. Cliente → POST /api/v1/attachments (multipart/form-data)
             body: entityType, entityId, file
2. Backend → valida tamanho (max 10 MB) e tipo (whitelist: image/jpeg, image/png, image/webp, application/pdf)
3. Backend → S3Client.putObject(bucket, key, stream)
4. Backend → persiste Attachment entity
5. Backend → retorna AttachmentResponse com { id, originalName, contentType, fileSizeBytes, url }
```

**URL de download**: gerada via `S3Presigner.presignGetObject()` com TTL de 15 minutos. URL não é persistida — gerada sob demanda no `GET /api/v1/attachments/{id}/download-url`.

---

### Decisão 4 — Package e endpoints

```
common/
├── domain/
│   └── Attachment.java
├── application/dto/
│   ├── AttachmentResponse.java
│   └── DownloadUrlResponse.java    // { "url": "https://...", "expiresAt": "..." }
├── application/usecase/
│   ├── UploadAttachmentUseCase.java
│   ├── GetAttachmentsUseCase.java
│   ├── GetDownloadUrlUseCase.java
│   └── DeleteAttachmentUseCase.java
├── infrastructure/
│   ├── AttachmentRepository.java
│   └── StorageService.java         // abstração sobre S3Client
└── presentation/
    └── AttachmentController.java   (/api/v1/attachments)
```

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/attachments | OPERATOR+ | upload (multipart) |
| GET | /api/v1/attachments?entityType=X&entityId=Y | OPERATOR+ | listar anexos de uma entidade |
| GET | /api/v1/attachments/{id}/download-url | OPERATOR+ | URL temporária (15 min) |
| DELETE | /api/v1/attachments/{id} | SUPERVISOR+ | remove do storage e do banco |

---

### Decisão 5 — Frontend

- Seção "Anexos" na página de detalhe de NC e de OS
- Botão "Anexar Arquivo" (OPERATOR+): abre file picker filtrado para imagens e PDF; limite visual de 10 MB
- Lista de anexos: ícone de tipo (PDF/imagem), nome original, tamanho, data de upload, botão download
- Clique em "Download" → `GET /download-url` → `window.open(url)` (abre em nova aba)
- SUPERVISOR+ vê botão de lixeira por anexo; confirmação antes de deletar
- Imagens exibem thumbnail via URL pré-assinada

---

### Consequências
✅ `StorageService` abstrai S3Client — testável com mock em unidade; troca MinIO→S3 sem mudar use case
✅ URLs pré-assinadas com TTL — arquivos nunca expostos publicamente
✅ Referência por `entityType + entityId` — zero acoplamento entre módulos (padrão de ADR-009 audit)
⚠️ TTL de 15 min nas URLs — frontend deve regenerar URL se o usuário deixar a aba aberta
⚠️ `software.amazon.awssdk:s3` adiciona ~10 MB ao JAR — aceitável; usar `software.amazon.awssdk:bom` para gerenciar versões
⚠️ Migration: apenas tabela `attachment` nova
