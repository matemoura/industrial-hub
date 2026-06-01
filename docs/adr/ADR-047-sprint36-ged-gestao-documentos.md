## ADR-047: Sprint 36 — Gestão de Documentos (GED)

**Status**: Aprovado
**Data**: 2026-06-01
**US relacionadas**: US-110, US-111

---

### Contexto

ISO 13485 §4.2 exige controle documental formal: documentos devem ser aprovados antes de uso, protegidos de alteração não autorizada e ter histórico de revisões rastreável. O sistema já possui:

- `StorageService` (MinIO/S3, ADR-018) para upload e geração de URLs pré-assinadas
- `FileAttachment` genérico (Sprint 21) para evidências avulsas em NCs e OSs

O `FileAttachment` é um anexo de evidência — não é um documento controlado. GED é um módulo separado com semântica de controle de versão, ciclo de vida e imutabilidade de revisões.

---

### Decisão 1 — Package: `qms/ged/` como sub-módulo do QMS

O GED é parte do domínio de qualidade (QMS), não da produção nem do common. Sub-pacote `qms/ged/` mantém a coesão sem misturar com NCs, RCA e fornecedores.

```
src/main/java/com/industrialhub/backend/qms/ged/
├── domain/
│   ├── Document.java
│   ├── DocumentRevision.java
│   ├── DocumentCategory.java   (enum: SOP, FORM, POLICY, WORK_INSTRUCTION, RECORD)
│   └── DocumentStatus.java     (enum: DRAFT, PUBLISHED, OBSOLETE)
├── application/
│   ├── dto/
│   │   ├── CreateDocumentRequest.java
│   │   ├── DocumentResponse.java
│   │   ├── DocumentSummaryResponse.java   (projeção para lista)
│   │   ├── DocumentRevisionResponse.java
│   │   └── DownloadUrlResponse.java
│   └── usecase/
│       ├── UploadDocumentUseCase.java
│       ├── AddRevisionUseCase.java
│       ├── TransitionDocumentStatusUseCase.java
│       ├── ListDocumentsUseCase.java
│       └── GetDocumentDownloadUrlUseCase.java
├── infrastructure/
│   ├── DocumentRepository.java
│   └── DocumentRevisionRepository.java
└── presentation/
    └── GedController.java
```

Frontend: `src/app/qms/ged/` com `ged.service.ts` e sub-pastas `ged-list/`, `ged-detail/`.

Alternativa descartada: package `common/ged/` — GED é específico do domínio de qualidade; não é infraestrutura cross-cutting.

---

### Decisão 2 — DocumentRevision é imutável após criação

Uma vez criada, uma `DocumentRevision` nunca é alterada. Novas versões criam novas revisões com `revisionNumber` auto-incrementado. Esta invariante garante auditabilidade regulatória.

```java
@Entity
@Table(name = "document_revision")
public class DocumentRevision {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Column(nullable = false, updatable = false)
    private String revisionNumber;      // "1.0", "2.0", ...

    @Column(nullable = false, updatable = false, length = 500)
    private String storagePath;         // chave MinIO: ged/{code}/{uuid}_{filename}

    @Column(nullable = false, updatable = false)
    private String originalFileName;

    @Column(nullable = false, updatable = false)
    private Long fileSizeBytes;

    @Column(nullable = false, updatable = false)
    private String uploadedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false, updatable = false, length = 1000)
    private String changeReason;
}
```

`updatable = false` em todos os campos garante que nenhum UPDATE acidental altere revisões — o JPA simplesmente ignora a coluna em statements de update.

Incremento do `revisionNumber`: `AddRevisionUseCase` busca o maior número de revisão da mesma `Document` e incrementa em 1.0:

```java
String nextRevision = documentRevisionRepository
    .findMaxRevisionNumberByDocumentId(documentId)
    .map(r -> String.format("%.1f", Double.parseDouble(r) + 1.0))
    .orElse("1.0");
```

Alternativa descartada: semantic versioning (major.minor) — desnecessário; a semântica de GED médico é simplesmente "versão nova = nova revisão numerada sequencialmente".

---

### Decisão 3 — Reutilização do StorageService; bucket e path dedicados ao GED

O `StorageService` de ADR-018 é compatível com S3 (MinIO em dev). Em vez de criar novo bucket, usar sub-path `ged/` dentro do bucket existente `industrial-hub-attachments`:

```
Bucket: industrial-hub-attachments
Path:   ged/{documentCode}/{uuid}_{originalFileName}

Exemplos:
  ged/SOP-001/a3f2c1d0_procedimento-assepsia.pdf
  ged/SOP-001/b8e9f2a1_procedimento-assepsia-v2.pdf
  ged/FORM-015/c1d3e4f5_formulario-inspecao.xlsx
```

O `{uuid}` no path evita colisões entre revisões com o mesmo nome original.

```java
// UploadDocumentUseCase.java
String storagePath = String.format("ged/%s/%s_%s",
    document.getCode(),
    UUID.randomUUID(),
    originalFileName);

storageService.upload(storagePath, inputStream, contentType, fileSize);
```

Alternativa descartada: bucket separado `ged-documents` — complexidade operacional desnecessária; sub-path é suficiente para isolamento lógico.

---

### Decisão 4 — Máquina de estados: DRAFT → PUBLISHED → OBSOLETE (sem retrocesso)

```
DRAFT ──────────▶ PUBLISHED ──────────▶ OBSOLETE
         (ADMIN)             (ADMIN)
```

Regras:
- `DRAFT → PUBLISHED`: requer que `currentRevision` não seja nulo
- `PUBLISHED → OBSOLETE`: transição de arquivamento; não pode ser revertida
- `OBSOLETE → *`: proibido — qualquer tentativa retorna `422`
- Apenas ADMIN pode realizar transições de status

```java
// TransitionDocumentStatusUseCase.java
public DocumentResponse transition(UUID id, DocumentStatus newStatus, String principal) {
    Document doc = documentRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado"));

    boolean valid = switch (doc.getStatus()) {
        case DRAFT      -> newStatus == DocumentStatus.PUBLISHED;
        case PUBLISHED  -> newStatus == DocumentStatus.OBSOLETE;
        case OBSOLETE   -> false;
    };

    if (!valid) throw new IllegalStateException(
        "Transição inválida: " + doc.getStatus() + " → " + newStatus);

    doc.setStatus(newStatus);
    return DocumentResponse.from(documentRepository.save(doc));
}
```

Alternativa descartada: transição OBSOLETE → DRAFT (re-ativação) — proibido por ISO 13485; documentos obsoletos devem ser descontinuados permanentemente.

---

### Decisão 5 — Download via URL pré-assinada (TTL 15 min); sem streaming pelo backend

O backend não serve bytes de arquivo — apenas gera URLs pré-assinadas com TTL de 15 minutos. O cliente faz download direto do MinIO/S3.

Motivação:
- Evita consumo de memória/thread no backend para arquivos grandes (laudos PDF, planilhas)
- MinIO/S3 são otimizados para serving de objetos — latência e throughput superiores
- TTL de 15 min é suficiente para iniciar o download sem expor a URL indefinidamente

```java
// GetDocumentDownloadUrlUseCase.java
public DownloadUrlResponse getDownloadUrl(UUID documentId, UUID revisionId) {
    DocumentRevision revision = documentRevisionRepository.findById(revisionId)
        .orElseThrow(() -> new EntityNotFoundException("Revisão não encontrada"));

    if (!revision.getDocument().getId().equals(documentId))
        throw new IllegalArgumentException("Revisão não pertence ao documento");

    String presignedUrl = storageService.generatePresignedUrl(
        revision.getStoragePath(), Duration.ofMinutes(15));

    return new DownloadUrlResponse(presignedUrl, 900L); // 900s = 15min
}
```

Alternativa descartada: streaming via `ResponseEntity<Resource>` — adequado para arquivos pequenos, mas cria gargalo de I/O no backend para PDFs grandes (laudos de 50+ MB).

---

### Contrato de API

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| POST | `/api/v1/qms/ged/documents` | SUPERVISOR+ | 201 | Cria documento + primeira revisão (multipart) |
| POST | `/api/v1/qms/ged/documents/{id}/revisions` | SUPERVISOR+ | 201 | Adiciona nova revisão (multipart) |
| PUT | `/api/v1/qms/ged/documents/{id}/status` | ADMIN | 200 | Transição de status |
| GET | `/api/v1/qms/ged/documents` | OPERATOR+ | 200 | Lista paginada (`?category=&status=`) |
| GET | `/api/v1/qms/ged/documents/{id}` | OPERATOR+ | 200 | Detalhe com histórico de revisões |
| GET | `/api/v1/qms/ged/documents/{id}/revisions/{revId}/download` | OPERATOR+ | 200 | URL pré-assinada (TTL 15 min) |

**DocumentSummaryResponse** (lista):
```json
{
  "id": "uuid",
  "code": "SOP-001",
  "title": "Procedimento de Assepsia",
  "category": "SOP",
  "status": "PUBLISHED",
  "currentRevisionNumber": "2.0",
  "updatedAt": "2026-06-01T14:00:00"
}
```

**DownloadUrlResponse**:
```json
{
  "url": "http://minio:9000/industrial-hub-attachments/ged/SOP-001/...?X-Amz-Signature=...",
  "expiresInSeconds": 900
}
```

---

### Consequências

✅ Imutabilidade da `DocumentRevision` garante rastreabilidade completa exigida pelo ISO 13485 §4.2.4
✅ Reutilização do `StorageService` (ADR-018) — zero nova infraestrutura; apenas sub-path dedicado
✅ URL pré-assinada elimina pressão de I/O no backend para downloads de arquivos grandes
✅ Package `qms/ged/` isolado — mudanças no GED não afetam NCs, RCA nem fornecedores
✅ `DocumentCategory` enum cobre os 5 tipos de documento obrigatórios pela ISO 13485

⚠️ `StorageService.generatePresignedUrl()` pode não estar implementado na versão atual — verificar ADR-018 antes de iniciar US-110; implementar se necessário
⚠️ URLs pré-assinadas expiram em 15 min — clientes com download lento em conexão ruim podem receber 403 do MinIO; considerar re-geração automática no frontend se necessário
⚠️ Sem busca full-text no título/conteúdo do documento — apenas filtros por `category` e `status`; busca avançada é escopo futuro
⚠️ `revisionNumber` como String ("1.0") implica ordenação lexicográfica — garantir que repositório ordene por `uploadedAt DESC`, não por `revisionNumber`
