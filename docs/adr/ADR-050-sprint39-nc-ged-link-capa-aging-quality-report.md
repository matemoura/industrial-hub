## ADR-050: Sprint 39 — NC↔GED Link, CAPA Aging Dashboard, Relatório Executivo de Qualidade

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-115, US-116, US-117

---

### Contexto

O módulo QMS possui, após os Sprints 36–38, dois domínios maduros mas desconectados: documentos controlados (GED, `qms/ged/`) e não-conformidades com CAPAs (`qms/`). Este sprint adiciona três capacidades:

1. **US-115** — Vinculação formal NC↔Documento GED: ISO 13485 §4.2 exige rastreabilidade do documento vigente no momento de uma NC (qual procedimento estava em vigor?) e da referência usada na ação corretiva. Atualmente não há vínculo estruturado entre `NonConformance` e `Document`.

2. **US-116** — Dashboard de aging de CAPAs: CAPAs sem `dueDate` ou vencidas acumulam silenciosamente. ISO 13485 §8.5.2 exige monitoramento da eficácia temporal. O endpoint `GET /api/v1/qms/capas` (ADR-048) lista CAPAs mas não oferece visão analítica de aging.

3. **US-117** — Relatório executivo de qualidade exportável PDF/Excel: gestores precisam de relatório consolidado periódico para reuniões de qualidade e auditorias regulatórias. iText não está no `pom.xml` — necessita adição. Apache POI 5.3.0 já está disponível (adicionado Sprint 33, ADR-044).

---

### Decisão 1 — Entidade `NcDocumentLink` para vinculação NC↔GED (US-115)

A vinculação NC↔Documento é modelada como entidade de relacionamento explícita — não como `@ManyToMany` direto — para suportar o campo `linkType` (semântica do vínculo) e auditoria do `linkedBy/linkedAt`.

```java
// qms/domain/NcDocumentLink.java
@Entity
@Table(
    name = "nc_document_link",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_nc_document_link",
            columnNames = {"non_conformance_id", "document_id"}
        )
    },
    indexes = {
        @Index(name = "idx_nc_doc_link_nc",  columnList = "non_conformance_id"),
        @Index(name = "idx_nc_doc_link_doc", columnList = "document_id")
    }
)
public class NcDocumentLink {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "non_conformance_id", nullable = false, updatable = false)
    private NonConformance nonConformance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NcDocumentLinkType linkType;

    @Column(nullable = false, updatable = false)
    private String linkedBy;            // username JWT

    @Column(nullable = false, updatable = false)
    private LocalDateTime linkedAt;     // set on creation
}
```

```java
// qms/domain/NcDocumentLinkType.java
public enum NcDocumentLinkType {
    PROCEDURE_AT_OCCURRENCE,   // documento vigente quando a NC ocorreu
    CORRECTIVE_REFERENCE,      // documento referenciado na ação corretiva
    OTHER                      // outros vínculos documentais
}
```

**Package**: `qms/domain/` — não `qms/ged/domain/`, pois `NcDocumentLink` é a entidade de cruzamento entre os dois sub-domínios. `Document` está em `qms/ged/domain/`; `NonConformance` está em `qms/domain/`. A entidade de junção pertence ao domínio que faz o vínculo (QMS principal), não ao sub-módulo GED.

**`updatable = false` em todos os campos de identidade** — o vínculo é imutável após criação. Para mudar o tipo, remove-se e recria-se o link.

Alternativa descartada: `@ManyToMany` com `@JoinTable` direto em `NonConformance.documents` — inviabiliza o campo `linkType` e a auditoria de `linkedBy/linkedAt` sem entidade intermediária.

---

### Decisão 2 — Use cases e repositório para NC↔GED Link (US-115)

```
qms/
├── application/
│   └── usecase/
│       ├── LinkNcToDocumentUseCase.java
│       ├── ListNcDocumentLinksUseCase.java
│       └── UnlinkNcFromDocumentUseCase.java
└── infrastructure/
    └── NcDocumentLinkRepository.java
```

**`NcDocumentLinkRepository`** — três métodos JPQL; todos usam projeção de interface para evitar carregamento de entidade completa:

```java
public interface NcDocumentLinkRepository extends JpaRepository<NcDocumentLink, UUID> {

    // Endpoint GET /non-conformances/{ncId}/documents
    @Query("""
        SELECT
            l.id          AS linkId,
            d.id          AS documentId,
            d.code        AS documentCode,
            d.title       AS documentTitle,
            d.category    AS documentCategory,
            d.status      AS documentStatus,
            l.linkType    AS linkType,
            l.linkedBy    AS linkedBy,
            l.linkedAt    AS linkedAt
        FROM NcDocumentLink l
        JOIN l.document d
        WHERE l.nonConformance.id = :ncId
        ORDER BY l.linkedAt DESC
    """)
    List<NcDocumentLinkSummary> findByNcId(@Param("ncId") UUID ncId);

    // Endpoint GET /ged/documents/{documentId}/non-conformances
    @Query("""
        SELECT
            l.id              AS linkId,
            nc.id             AS ncId,
            nc.code           AS ncCode,
            nc.title          AS ncTitle,
            nc.severity       AS ncSeverity,
            nc.status         AS ncStatus,
            l.linkType        AS linkType,
            l.linkedAt        AS linkedAt
        FROM NcDocumentLink l
        JOIN l.nonConformance nc
        WHERE l.document.id = :documentId
        ORDER BY l.linkedAt DESC
    """)
    List<DocumentNcLinkSummary> findByDocumentId(@Param("documentId") UUID documentId);

    // Verificação de existência para o DELETE
    Optional<NcDocumentLink> findByNonConformanceIdAndDocumentId(UUID ncId, UUID documentId);
}
```

**`LinkNcToDocumentUseCase`** — valida que o `Document` existe e não está `OBSOLETE` antes de criar o link. Documentos obsoletos não devem ser vinculados a novas NCs (seria referenciar documentação descontinuada):

```java
@Service
@Transactional
public class LinkNcToDocumentUseCase {

    public NcDocumentLinkResponse execute(UUID ncId, LinkNcToDocumentRequest req, String principal) {
        NonConformance nc = nonConformanceRepository.findById(ncId)
            .orElseThrow(() -> new NcNotFoundException(ncId));

        Document doc = documentRepository.findById(req.documentId())
            .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado"));

        if (doc.getStatus() == DocumentStatus.OBSOLETE)
            throw new IllegalArgumentException(
                "Não é possível vincular um documento OBSOLETO a uma NC.");

        // Unique constraint (nc + document) capturada como exceção de domínio
        try {
            NcDocumentLink link = new NcDocumentLink(nc, doc, req.linkType(), principal);
            return NcDocumentLinkResponse.from(ncDocumentLinkRepository.save(link));
        } catch (DataIntegrityViolationException e) {
            throw new NcDocumentLinkAlreadyExistsException(ncId, req.documentId());
        }
    }
}
```

`NcDocumentLinkAlreadyExistsException` → `GlobalExceptionHandler` → `409 CONFLICT` (mesmo padrão de `DocumentCodeAlreadyExistsException`, ADR-049 Decisão 5).

**`UnlinkNcFromDocumentUseCase`** — DELETE idempotente: se o link não existir, retorna 404 com mensagem clara. Não usa `deleteById` por UUID de link — usa `findByNonConformanceIdAndDocumentId` para URL semântica.

---

### Decisão 3 — Contrato de API NC↔GED Link (US-115)

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | `/api/v1/qms/non-conformances/{ncId}/documents` | SUPERVISOR+ | 201 | Cria vínculo NC→Documento |
| GET | `/api/v1/qms/non-conformances/{ncId}/documents` | OPERATOR+ | 200 | Lista documentos vinculados à NC |
| DELETE | `/api/v1/qms/non-conformances/{ncId}/documents/{documentId}` | SUPERVISOR+ | 204 | Remove vínculo |
| GET | `/api/v1/qms/ged/documents/{documentId}/non-conformances` | OPERATOR+ | 200 | Lista NCs vinculadas ao documento |

O endpoint `GET /ged/documents/{documentId}/non-conformances` é adicionado ao `GedController` existente (não cria controller novo) — é uma vista inversa do mesmo vínculo, naturalmente pertencente ao controller GED.

Os endpoints do lado NC são adicionados ao `QmsController` existente — seguindo o padrão de sub-recursos (`/{ncId}/actions`, `/{ncId}/rca`, `/{ncId}/documents`).

**`LinkNcToDocumentRequest`**:
```json
{
  "documentId": "uuid",
  "linkType": "PROCEDURE_AT_OCCURRENCE"
}
```

**`NcDocumentLinkResponse`**:
```json
{
  "linkId": "uuid",
  "documentId": "uuid",
  "documentCode": "SOP-001",
  "documentTitle": "Procedimento de Assepsia",
  "documentCategory": "SOP",
  "documentStatus": "PUBLISHED",
  "linkType": "PROCEDURE_AT_OCCURRENCE",
  "linkedAt": "2026-06-04T10:30:00"
}
```

`linkedBy` **não é exposto** no response — dado de autoria, conforme ADR-049 Decisão 4 (padrão do projeto: dados de autoria não são expostos a OPERATOR+; permanecem na entidade para auditoria via `AuditLog`).

---

### Decisão 4 — Campo `dueDate` em `CorrectiveAction` e estrutura do aging (US-116)

O campo `dueDate` já existe na entidade `CorrectiveAction` (ADR-007 Decisão 3). A ADR-048 também o referencia na projeção `CAPASummaryProjection`. **Nenhuma alteração de schema é necessária** para US-116 — o campo está presente e é nullable.

O endpoint de aging é um **agregador analítico** — não retorna lista paginada, mas sim totais e buckets calculados a partir de `dueDate` e `LocalDate.now()`:

```java
// CapaAgingResponse.java — DTO record
public record CapaAgingResponse(
    long totalOpen,                  // ações com status PENDING ou PENDING_EFFECTIVENESS
    long overdueCount,               // dueDate não-nulo < hoje
    long noDueDateCount,             // dueDate == null
    AgingBucket bucket0to7,          // dueDate >= hoje, delta [0-7 dias]
    AgingBucket bucket8to15,         // delta [8-15 dias]
    AgingBucket bucket16to30,        // delta [16-30 dias]
    AgingBucketOver30 bucketOver30,  // delta > 30 dias
    List<OverdueBySeverity> overdueByNcSeverity  // cross-cut: overdue × severidade da NC
) {}

public record AgingBucket(long count, String label) {}

public record AgingBucketOver30(long count, String label, long overdueCount) {}

public record OverdueBySeverity(String severity, long overdueCount) {}
```

**Cálculo no use case, não em JPQL**: os buckets dependem de `LocalDate.now()` como referência — uma variável dinâmica que torna difícil escrever JPQL parametrizado limpo. O use case carrega as ações abertas com `dueDate` via projeção leve e agrupa em Java:

```java
// GetCapaAgingUseCase.java
@Service
@Transactional(readOnly = true)
public class GetCapaAgingUseCase {

    public CapaAgingResponse execute() {
        LocalDate today = LocalDate.now();
        List<CapaAgingProjection> open = correctiveActionRepository.findOpenCapasForAging();

        long totalOpen = open.size();
        long overdueCount = open.stream()
            .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today))
            .count();
        long noDueDateCount = open.stream()
            .filter(a -> a.getDueDate() == null)
            .count();

        // Buckets para dueDate >= hoje (não vencidas ainda)
        long b0to7   = countInRange(open, today, 0, 7);
        long b8to15  = countInRange(open, today, 8, 15);
        long b16to30 = countInRange(open, today, 16, 30);
        long bOver30 = countInRange(open, today, 31, Long.MAX_VALUE);

        List<OverdueBySeverity> bySeverity = open.stream()
            .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today))
            .collect(Collectors.groupingBy(CapaAgingProjection::getNcSeverity,
                     Collectors.counting()))
            .entrySet().stream()
            .map(e -> new OverdueBySeverity(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(OverdueBySeverity::severity))
            .toList();

        return new CapaAgingResponse(totalOpen, overdueCount, noDueDateCount,
            new AgingBucket(b0to7, "0–7 dias"),
            new AgingBucket(b8to15, "8–15 dias"),
            new AgingBucket(b16to30, "16–30 dias"),
            new AgingBucketOver30(bOver30, ">30 dias", bOver30),
            bySeverity);
    }

    private long countInRange(List<CapaAgingProjection> list, LocalDate today,
                              long minDays, long maxDays) {
        return list.stream()
            .filter(a -> a.getDueDate() != null && !a.getDueDate().isBefore(today))
            .filter(a -> {
                long delta = ChronoUnit.DAYS.between(today, a.getDueDate());
                return delta >= minDays && (maxDays == Long.MAX_VALUE || delta <= maxDays);
            })
            .count();
    }
}
```

**`CapaAgingProjection`** — projeção leve (sem carregar entidade completa):

```java
public interface CapaAgingProjection {
    UUID getActionId();
    LocalDate getDueDate();
    String getStatus();
    String getNcSeverity();   // severity da NC pai
}
```

```java
// CorrectiveActionRepository.java — query adicional
@Query("""
    SELECT
        a.id         AS actionId,
        a.dueDate    AS dueDate,
        a.status     AS status,
        nc.severity  AS ncSeverity
    FROM CorrectiveAction a
    JOIN a.nonConformance nc
    WHERE a.status IN ('PENDING', 'PENDING_EFFECTIVENESS')
""")
List<CapaAgingProjection> findOpenCapasForAging();
```

**Exportação CSV** — segue o padrão de ADR-044 Decisão 6: UTF-8 BOM + separador `;` + vírgula decimal para Excel pt-BR.

```java
// GetCapaAgingUseCase.exportCsv()
public byte[] exportCsv() {
    // CSV com cabeçalho + 1 linha por CAPA aberta
    // Colunas: ncCode; ncSeverity; description; responsible; dueDate; diasParaVencer; status
}
```

---

### Decisão 5 — Contrato de API CAPA Aging (US-116)

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| GET | `/api/v1/qms/capas/aging` | SUPERVISOR+ | 200 | Totais + buckets de aging |
| GET | `/api/v1/qms/capas/aging/export` | SUPERVISOR+ | 200 | CSV para download |

Ambos adicionados ao `CapaController` existente (ADR-048 Decisão 4) — respeita o SRP estabelecido de separar CAPAs do `QmsController`.

**`CapaAgingResponse` simplificado** (exemplo):
```json
{
  "totalOpen": 23,
  "overdueCount": 5,
  "noDueDateCount": 3,
  "bucket0to7":  { "count": 4, "label": "0–7 dias" },
  "bucket8to15": { "count": 6, "label": "8–15 dias" },
  "bucket16to30": { "count": 3, "label": "16–30 dias" },
  "bucketOver30": { "count": 2, "label": ">30 dias", "overdueCount": 0 },
  "overdueByNcSeverity": [
    { "severity": "CRITICAL", "overdueCount": 2 },
    { "severity": "HIGH",     "overdueCount": 3 }
  ]
}
```

**Frontend**: página `/qms/capas/aging` com:
- 4 cards (totalOpen, overdueCount, noDueDateCount, maior bucket)
- `BarChartComponent` existente (Sprint 24, ADR-039 Decisão 6) reutilizado com dados dos 4 buckets
- Tabela de CAPAs vencidas com severity chips

O `BarChartComponent` já está disponível no projeto (`src/app/shared/` ou reutilizado em `/analytics`) — **não instalar nova biblioteca de gráficos**.

---

### Decisão 6 — Geração de relatório PDF: adição de iText 7 ao pom.xml (US-117)

**iText não está no `pom.xml`** (verificado em 2026-06-04). A tarefa menciona "iText 2.1.7" — essa versão é de 2009 (licença MPL/LGPL desatualizada e sem suporte). **A versão correta a adicionar é iText 7 Community (7.x)** sob licença AGPL, que é free para uso open source e adequada para ambiente interno (~53 usuários sem redistribuição pública).

**Dependência a adicionar no `pom.xml`**:
```xml
<!-- PDF generation — iText 7 Community -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>7.2.6</version>
    <type>pom</type>
</dependency>
```

Alternativa descartada: Apache PDFBox — API de baixo nível; geração de relatórios tabulados é muito mais trabalhosa que iText 7 (`Table`, `Cell`, `Paragraph` nativos).

Alternativa descartada: iText 2.1.7 — versão obsoleta de 2009, sem suporte a Unicode completo, sem API de tabelas modernas, licença MPL desatualizada. A task provavelmente referia-se a iText por nome histórico; iText 7 é o successor direto.

---

### Decisão 7 — Estrutura do relatório executivo e use case de geração (US-117)

O relatório é gerado sob demanda (não agendado) via `POST` — o body define o período, formato e seções desejadas. A geração é síncrona: para o volume do MSB (~53 usuários, relatórios mensais), a geração em-request é suficiente sem job assíncrono.

```java
// common/presentation/ ou qms/presentation/ — endpoint dedicado
POST /api/v1/qms/reports/quality
```

**`QualityReportRequest`**:
```java
public record QualityReportRequest(
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from,

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to,

    @NotNull
    ReportFormat format,    // enum: PDF, EXCEL

    @NotEmpty
    Set<ReportSection> sections  // enum: NC_SUMMARY, CAPA_STATUS, AGING, GED_METRICS
) {}

public enum ReportFormat { PDF, EXCEL }

public enum ReportSection {
    NC_SUMMARY,      // totais de NC por status/severidade
    CAPA_STATUS,     // totais de CAPA por status/tipo
    AGING,           // buckets de aging (dados do GetCapaAgingUseCase)
    GED_METRICS      // documentos por categoria/status
}
```

**Separação de responsabilidades** — dois services internos, um use case orquestrador:

```
qms/application/usecase/
└── GenerateQualityReportUseCase.java   ← @Service, orquestra coleta + renderização

qms/application/service/
├── QualityReportDataService.java       ← coleta dados (reutiliza use cases existentes)
├── QualityReportPdfRenderer.java       ← iText 7: produz byte[]
└── QualityReportExcelRenderer.java     ← Apache POI: produz byte[]
```

`QualityReportDataService` **não acessa repositórios diretamente** — delega para os use cases já existentes:
- `GetNcSummaryUseCase` — seção NC_SUMMARY
- `GetCapaAgingUseCase` — seção AGING
- dados de CAPA_STATUS via `CorrectiveActionRepository` (projeção existente)
- dados de GED_METRICS via `DocumentRepository` (nova projeção de count por categoria/status)

**`QualityReportPdfRenderer`** — estrutura iText 7:

```java
@Component
public class QualityReportPdfRenderer {

    public byte[] render(QualityReportData data, QualityReportRequest req) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document doc = new Document(pdf, PageSize.A4);

        // Cabeçalho MSB
        addHeader(doc, data.period());

        // Seções condicionais
        if (req.sections().contains(ReportSection.NC_SUMMARY))
            addNcSummarySection(doc, data.ncSummary());

        if (req.sections().contains(ReportSection.CAPA_STATUS))
            addCapaStatusSection(doc, data.capaStatus());

        if (req.sections().contains(ReportSection.AGING))
            addAgingSection(doc, data.aging());

        if (req.sections().contains(ReportSection.GED_METRICS))
            addGedMetricsSection(doc, data.gedMetrics());

        // Rodapé com data de geração
        addFooter(doc);

        doc.close();
        return baos.toByteArray();
    }

    private void addNcSummarySection(Document doc, NcSummaryData summary) {
        doc.add(new Paragraph("Não-Conformidades").setBold().setFontSize(14));
        Table table = new Table(UnitValue.createPercentArray(new float[]{4, 2, 2, 2}))
            .useAllAvailableWidth();
        // header row
        Stream.of("Severidade", "Abertas", "Em Análise", "Fechadas")
            .forEach(h -> table.addHeaderCell(new Cell().add(new Paragraph(h).setBold())));
        // data rows — iterable over summary.bySeverity()
        summary.bySeverity().forEach(row ->
            Stream.of(row.severity(), str(row.open()), str(row.inAnalysis()), str(row.closed()))
                  .forEach(v -> table.addCell(new Cell().add(new Paragraph(v)))));
        doc.add(table);
    }
}
```

**`QualityReportExcelRenderer`** — Apache POI (padrão do projeto, ADR-044):

```java
@Component
public class QualityReportExcelRenderer {

    public byte[] render(QualityReportData data, QualityReportRequest req) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Uma Sheet por seção selecionada
            if (req.sections().contains(ReportSection.NC_SUMMARY))
                fillNcSummarySheet(wb.createSheet("NC Summary"), data.ncSummary());
            if (req.sections().contains(ReportSection.CAPA_STATUS))
                fillCapaStatusSheet(wb.createSheet("CAPA Status"), data.capaStatus());
            if (req.sections().contains(ReportSection.AGING))
                fillAgingSheet(wb.createSheet("Aging"), data.aging());
            if (req.sections().contains(ReportSection.GED_METRICS))
                fillGedSheet(wb.createSheet("GED"), data.gedMetrics());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
```

---

### Decisão 8 — Contrato de API do Relatório Executivo (US-117)

| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | `/api/v1/qms/reports/quality` | SUPERVISOR+ | 200 | Gera e retorna o relatório (PDF ou Excel como blob) |

**Response**: binário com `Content-Type` e `Content-Disposition` adequados ao formato:

```java
// QmsReportController.java (controller dedicado: qms/presentation/QmsReportController.java)
@PostMapping(value = "/quality")
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public ResponseEntity<byte[]> generateQualityReport(
        @Valid @RequestBody QualityReportRequest req) throws IOException {

    byte[] bytes = generateQualityReportUseCase.execute(req);
    String contentType = req.format() == ReportFormat.PDF
        ? "application/pdf"
        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    String filename = String.format("relatorio-qualidade-%s-%s.%s",
        req.from(), req.to(), req.format() == ReportFormat.PDF ? "pdf" : "xlsx");

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, contentType)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + filename + "\"")
        .body(bytes);
}
```

**`QmsReportController`** é controller dedicado em `qms/presentation/` — separado do `QmsController` (NCs) e `CapaController` (CAPAs) para respeitar SRP. Prefixo: `/api/v1/qms/reports`.

**Frontend**: formulário Angular com `MatDateRangePicker` (Angular Material), checkboxes para seções e `MatRadioGroup` para formato. Download via `Blob`:

```typescript
// qms.service.ts
generateQualityReport(req: QualityReportRequest): Observable<Blob> {
  return this.http.post('/api/v1/qms/reports/quality', req, {
    responseType: 'blob'
  });
}

// quality-report.component.ts
download(): void {
  this.qmsService.generateQualityReport(this.form.value).subscribe(blob => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `relatorio-qualidade-${this.form.value.from}-${this.form.value.to}.${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  });
}
```

---

### Decisão 9 — Localização do `QmsReportController` e ausência de `QmsController` sobrescrito

O `QmsController` atual (`qms/presentation/`) já tem ~10 endpoints de NC. Adicionar relatório nele violaria SRP e tornaria a classe difícil de manter.

Padrão adotado (consistente com ADR-048 para `CapaController`):

```
qms/presentation/
├── QmsController.java          — NCs + ações corretivas (existente)
├── CapaController.java         — visão cross-NC de CAPAs (Sprint 37)
├── GedController.java          — documentos GED (Sprint 36) ← endpoint inverse-link adicionado aqui
└── QmsReportController.java    — relatórios executivos (Sprint 39, novo)
```

A adição do endpoint `GET /ged/documents/{documentId}/non-conformances` ao `GedController` (US-115) não quebra o SRP — é uma query de leitura sobre recursos GED, com filtro de relacionamento.

---

### Contrato de API consolidado — Sprint 39

**US-115 NC↔GED Link:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/qms/non-conformances/{ncId}/documents` | SUPERVISOR+ | 201 |
| GET | `/api/v1/qms/non-conformances/{ncId}/documents` | OPERATOR+ | 200 |
| DELETE | `/api/v1/qms/non-conformances/{ncId}/documents/{documentId}` | SUPERVISOR+ | 204 |
| GET | `/api/v1/qms/ged/documents/{documentId}/non-conformances` | OPERATOR+ | 200 |

**US-116 CAPA Aging:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| GET | `/api/v1/qms/capas/aging` | SUPERVISOR+ | 200 |
| GET | `/api/v1/qms/capas/aging/export` | SUPERVISOR+ | 200 (CSV) |

**US-117 Relatório Executivo:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/qms/reports/quality` | SUPERVISOR+ | 200 (blob PDF/XLSX) |

---

### Consequências

✅ `NcDocumentLink` com unique constraint `(nc_id, document_id)` garante rastreabilidade sem duplicatas — ISO 13485 §4.2 atendido para vinculação de procedimentos a ocorrências de NC
✅ Projeções JPQL em ambas as direções (NC→docs e doc→NCs) — sem N+1; queries únicas com JOIN
✅ `NcDocumentLinkType` captura a semântica do vínculo (procedimento vigente vs. referência corretiva vs. outro) — informação auditável para conformidade ISO 13485
✅ Cálculo de aging em Java (não em JPQL) com `LocalDate.now()` — lógica de negócio testável via `@Service` sem depender de data do banco
✅ `GetCapaAgingUseCase` reutiliza `CapaAgingProjection` leve — sem carregar entidades `CorrectiveAction` completas com todas as relações LAZY
✅ `BarChartComponent` existente reutilizado para aging — zero nova dependência de gráfico
✅ Separação `QualityReportPdfRenderer` / `QualityReportExcelRenderer` como `@Component` — cada renderer é testável isoladamente com dados mockados
✅ `QualityReportDataService` delega para use cases existentes — reutiliza lógica já testada de NC summary, CAPA aging e GED metrics
✅ `QmsReportController` dedicado — SRP mantido, sem sobrecarga do `QmsController` ou `CapaController`
✅ `reportType: 'blob'` no Angular + `URL.createObjectURL` — download sem passar bytes pelo DOM nem carregar em memória Angular

⚠️ **iText 7 Community (AGPL)** deve ser validado juridicamente antes de deploy externo — para uso interno (~53 usuários, sem redistribuição), AGPL não impõe obrigação copyleft; confirmar com responsável legal antes de qualquer distribuição do sistema
⚠️ Geração de relatório PDF/Excel **é síncrona** — para períodos > 1 ano ou muitas seções, pode exceder o timeout padrão do Spring Boot (30s); adicionar `@Validated` com validação de range máximo de `to - from <= 366 dias` no request
⚠️ `UnlinkNcFromDocumentUseCase` usa `findByNonConformanceIdAndDocumentId` para semântica de URL (`DELETE .../documents/{documentId}`) — não usa UUID interno do link; consistente com UI onde o usuário não conhece o `linkId`
⚠️ Documentos `OBSOLETE` não podem ser vinculados a novas NCs — **links existentes criados antes da obsolescência permanecem válidos**; auditores podem questionar vinculação com documento que foi posteriormente obsoletado; considerar campo `documentStatusAtLinkTime` em sprint futura
⚠️ Aging calculado **sem cache** — `findOpenCapasForAging()` executa query a cada request; para 53 usuários, o volume de CAPAs abertas dificilmente ultrapassa centenas de registros; se crescer, aplicar `@Cacheable` com TTL 5 min (padrão `CacheConfig`, ADR-046 Decisão 1)
⚠️ `QualityReportExcelRenderer` gera o `XSSFWorkbook` inteiro em memória antes de serializar — para relatórios com muitas linhas (>10k), considerar `SXSSFWorkbook` (streaming POI); volume MSB não deve atingir esse limiar
