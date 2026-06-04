## ADR-052: Sprint 41 — Gestão de Calibração e MSA

**Status**: Aprovado
**Data**: 2026-06-04
**US relacionadas**: US-121, US-122, US-123

---

### Contexto

ISO 13485 §7.6 exige que a organização determine as medições a serem feitas, estabeleça processos para assegurar que os equipamentos de medição e monitoramento são calibrados em intervalos definidos, e mantenha registros dos resultados da calibração. Equipamentos fora de tolerância devem acionar investigação imediata.

O módulo de Manutenção (`maintenance/`) já possui `Equipment` (Sprint 7) e `MaintenanceSchedule` (Sprint 15). A calibração é modelada como extensão de manutenção — mesma entidade `Equipment` como âncora, mas com entidades próprias de plano e registro de calibração.

Integrações necessárias:
- **GED** (Sprint 36): `CalibrationRecord.certificateDocumentId` referencia `Document.id`
- **NC automática** (Sprint 5): `OUT_OF_TOLERANCE` dispara `CreateNonConformanceUseCase`
- **MinIO/StorageService** (Sprint 21): upload direto de certificado quando sem GED
- **NotificationService** (Sprint 18): alertas de vencimento

---

### Decisão 1 — Entidades em `maintenance/domain/` (não novo pacote)

Calibração é extensão do módulo de manutenção de equipamentos. Colocar em `maintenance/domain/` evita um novo pacote top-level para poucas entidades e mantém coesão com `Equipment`, `WorkOrder` e `MaintenanceSchedule`:

```java
// maintenance/domain/CalibrationSchedule.java
@Entity
@Table(name = "calibration_schedule",
    indexes = {
        @Index(name = "idx_cal_schedule_equipment", columnList = "equipment_id"),
        @Index(name = "idx_cal_schedule_next_due",  columnList = "next_due_at")
    })
public class CalibrationSchedule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private Integer intervalDays;          // min 1

    private LocalDate lastCalibratedAt;    // null antes da primeira calibração

    @Column(nullable = false)
    private LocalDate nextDueAt;           // calculado: today + intervalDays na criação

    @Column(length = 200)
    private String externalProvider;

    private boolean active = true;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

```java
// maintenance/domain/CalibrationRecord.java
@Entity
@Table(name = "calibration_record",
    indexes = {
        @Index(name = "idx_cal_record_schedule",  columnList = "schedule_id"),
        @Index(name = "idx_cal_record_equipment", columnList = "equipment_id"),
        @Index(name = "idx_cal_record_date",      columnList = "calibrated_at")
    })
public class CalibrationRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private CalibrationSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;           // desnormalização intencional para queries diretas

    @Column(nullable = false)
    private LocalDate calibratedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalibrationResult result;      // IN_TOLERANCE, OUT_OF_TOLERANCE, ADJUSTED

    @Column(nullable = false, length = 200)
    private String technician;

    private UUID certificateDocumentId;    // FK leve para Document (GED) — sem @ManyToOne para evitar ciclo de dependência entre módulos

    @Column(length = 500)
    private String certificateStoragePath; // chave MinIO, mutuamente exclusivo com certificateDocumentId

    @Column(columnDefinition = "TEXT")
    private String notes;

    private UUID autoNcId;                 // NC criada automaticamente, nullable

    @Column(nullable = false, length = 100)
    private String recordedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;
}
```

**`equipment` desnormalizado em `CalibrationRecord`**: apesar de `schedule.equipment` ser o mesmo equipamento, a desnormalização permite buscar registros por equipamento sem JOIN com `CalibrationSchedule`. Índice em `equipment_id` suporta a query `?equipmentId=` sem traversal extra.

**Referência leve `certificateDocumentId: UUID`** (sem `@ManyToOne` para `Document`): segue o padrão de `linkedNcId` em outras entidades (ADR-050 Decisão 1). Módulos são independentes — a FK física entre `maintenance` e `qms/ged` criaria acoplamento estrutural. A existência do documento é validada no use case antes de salvar.

---

### Decisão 2 — Mutually exclusive: `certificateDocumentId` vs `certificateStoragePath`

Quando um técnico registra uma calibração, pode vincular o certificado de duas formas mutuamente exclusivas:
1. **GED**: certificado já existente no repositório de documentos controlados (`certificateDocumentId`)
2. **Upload direto**: arquivo PDF enviado em multipart (`certificateStoragePath`)

Ambos presentes → `400 { "message": "Informe o certificado via GED ou upload direto, não ambos." }`. Nenhum dos dois → calibração sem certificado (permitido — o campo `hasCertificate` no response indica `false`).

```java
// CreateCalibrationRecordUseCase.java
if (req.certificateDocumentId() != null && file != null && !file.isEmpty()) {
    throw new IllegalArgumentException(
        "Informe o certificado via GED ou upload direto, não ambos.");
}
if (req.certificateDocumentId() != null) {
    documentRepository.findById(req.certificateDocumentId())
        .orElseThrow(() -> new EntityNotFoundException("Documento GED não encontrado"));
}
if (file != null && !file.isEmpty()) {
    gedFileValidator.validate(file); // MIME check via Tika (ADR-049)
    String key = "calibration/%s/%s_%s".formatted(
        equipment.getCode(), UUID.randomUUID(),
        GedFileValidator.sanitizeFilename(file.getOriginalFilename())
    );
    storageService.upload(key, file.getInputStream(), file.getContentType(), file.getSize());
}
```

---

### Decisão 3 — NC automática em `OUT_OF_TOLERANCE`

Quando `result == OUT_OF_TOLERANCE`, o use case de criação de registro de calibração chama `CreateNonConformanceUseCase` na mesma transação:

```java
// CreateCalibrationRecordUseCase.java (dentro de @Transactional)
if (req.result() == CalibrationResult.OUT_OF_TOLERANCE) {
    CreateNcRequest ncRequest = new CreateNcRequest(
        "Equipamento fora de tolerância: " + equipment.getCode(),
        NcType.EQUIPMENT,
        NcSeverity.HIGH,
        "NC gerada automaticamente por calibração fora de tolerância em " + req.calibratedAt()
    );
    NcResponse nc = createNonConformanceUseCase.execute(ncRequest, "system");
    record.setAutoNcId(nc.id());
}
```

**`recordedBy = "system"`** para NCs automáticas — identificador reservado que distingue NCs manuais de automáticas no `AuditLog`. Alternativa descartada: criar a NC fora da transação via `@TransactionalEventListener` — isso separa os commits, mas uma NC sem calibração correspondente (ou calibração sem NC) seria um estado incoerente.

---

### Decisão 4 — Job de alertas e frequência

`CalibrationExpiryAlertJob` roda diariamente às 07h (America/Sao_Paulo) — diferente do job de treinamento (semanal). Calibrações vencidas representam risco de compliance imediato; frequência diária é adequada:

```java
@Scheduled(cron = "0 0 7 * * *", zone = "America/Sao_Paulo")
public void runDaily() { calibrationAlertUseCase.execute(); }
```

Thresholds de debounce: 72h para `nextDueAt BETWEEN today AND today+14` (`WARNING`); 24h para `nextDueAt < today` (`CRITICAL`). Mesma lógica de `existsByTitleAndCreatedAtAfter` (padrão estabelecido no Sprint 20).

---

### Contrato de API — Sprint 41

**US-121 — Planos:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/maintenance/calibration-schedules` | SUPERVISOR+ | 201 |
| GET | `/api/v1/maintenance/calibration-schedules` | OPERATOR+ | 200 |
| PUT | `/api/v1/maintenance/calibration-schedules/{id}` | SUPERVISOR+ | 200 |
| PUT | `/api/v1/maintenance/calibration-schedules/{id}/deactivate` | SUPERVISOR+ | 204 |

**US-121 — Registros:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| POST | `/api/v1/maintenance/calibration-records` (multipart) | SUPERVISOR+ | 201 |
| GET | `/api/v1/maintenance/calibration-records` | OPERATOR+ | 200 |
| GET | `/api/v1/maintenance/calibration-records/{id}/certificate` | OPERATOR+ | 200 (URL pré-assinada) |

**US-122:**

| Método | Endpoint | Auth | HTTP |
|--------|----------|------|------|
| GET | `/api/v1/maintenance/calibration-schedules/summary` | SUPERVISOR+ | 200 |
| POST | `/api/v1/admin/calibration/alerts/run-now` | ADMIN | 200 |

Endpoints adicionados ao `MaintenanceController` (existente) — o volume de endpoints de calibração não justifica um controller dedicado. Se ultrapassar 15 endpoints, avaliar `CalibrationController` em sprint futura (padrão SRP já aplicado em `GedController` e `CapaController`).

---

### Consequências

✅ Entidades em `maintenance/domain/` — coesão com `Equipment`, sem novo pacote top-level para poucas entidades
✅ Referência leve `certificateDocumentId: UUID` para GED — sem acoplamento estrutural entre `maintenance` e `qms/ged`; validação de existência no use case
✅ NC automática `OUT_OF_TOLERANCE` na mesma transação — garante consistência entre calibração e NC sem estado incoerente
✅ Validação MIME de certificado via `GedFileValidator.validate()` (ADR-049) — reúso sem duplicação
✅ Desnormalização `equipment_id` em `CalibrationRecord` — queries por equipamento sem JOIN extra
✅ Job diário (vs. semanal do treinamento) — resposta mais rápida a equipamentos com calibração vencida

⚠️ NC automática criada com `recordedBy = "system"` — relatórios de NCs devem filtrar ou sinalizar NCs de origem automática para não confundir com NCs humanas; considerar campo `source` em NC futura
⚠️ `MaintenanceController` acumulará endpoints de calibração além dos de equipamento, OS e peças — monitorar tamanho e extrair `CalibrationController` se necessário
⚠️ Validação de existência de `certificateDocumentId` no use case não impede obsolescência posterior do documento GED — mesmo padrão de risco documentado em ADR-050 Consequências para vínculos NC↔GED
⚠️ `lastCalibratedAt` e `nextDueAt` em `CalibrationSchedule` são atualizados a cada registro — race condition possível se dois registros forem criados simultaneamente para o mesmo plano; considerar `@Lock(LockModeType.PESSIMISTIC_WRITE)` em `findByIdForUpdate` (padrão ADR-049 §6)
