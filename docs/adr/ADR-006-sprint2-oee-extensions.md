## ADR-006: OEE Sprint 2 — Validation, Overwrite & Frontend Navigation
**Status**: Aprovado
**Data**: 2026-05-10
**US relacionadas**: US-006, US-007, US-008, US-009

### Contexto
Sprint 2 expande o módulo OEE com: overwrite atômico de importações, validação cross-cutting de faixa de datas, e duas novas páginas Angular para atividades indiretas e resumo por período.

---

### Decisão 1 — Validação de faixa de datas: serviço compartilhado

Opções consideradas:
1. **Validar no controller** — duplicação implícita nos 3 endpoints
2. **Validar em cada use case** — correto, mas prolixo
3. **`DateRangeValidator` injetável** — validação centralizada, testável isoladamente

**Decisão: `DateRangeValidator` como `@Component`**, chamado no início de cada use case.

```java
// oee/application/validation/DateRangeValidator.java
@Component
public class DateRangeValidator {
    private static final int MAX_DAYS = 366;

    public void validate(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate))
            throw new InvalidDateRangeException("startDate must not be after endDate");
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_DAYS)
            throw new InvalidDateRangeException("Date range must not exceed 366 days");
    }
}
```

`InvalidDateRangeException` é uma `RuntimeException` mapeada para `400` no `GlobalExceptionHandler` existente:
```java
@ExceptionHandler(InvalidDateRangeException.class)
public ResponseEntity<Map<String, String>> handleInvalidDateRange(InvalidDateRangeException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
}
```

---

### Decisão 2 — Overwrite atômico: cascade delete + reimport

Opções consideradas:
1. **Soft delete** com campo `active` — query complexity aumenta em todos os endpoints
2. **Hard delete + reimport em `@Transactional`** — simples, sem dados fantasma

**Decisão: Hard delete com cascade.** `ImportBatch` receberá `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` → deletar o batch cascadeia para todos os `TimeRecord`.

Fluxo do use case com `overwrite=true`:
1. Parser valida o arquivo (mesmo pipeline atual)
2. Se `overwrite=true` e batch do período existe → `importBatchRepository.deleteByPeriodDate(periodDate)` + flush
3. Salva novo batch + records
4. Retorna `ImportResultDto` com campo `replaced: boolean`

Tudo dentro de um único `@Transactional` — se o step 3 falhar, o delete é revertido.

```java
// ImportResultDto — campo adicionado
public record ImportResultDto(
    UUID batchId, LocalDate periodDate, int workerCount,
    int recordsImported, int recordsSkipped,
    List<String> errors, boolean replaced
) {}
```

Novo método no repositório:
```java
// ImportBatchRepository
void deleteByPeriodDate(LocalDate periodDate);
Optional<ImportBatch> findByPeriodDate(LocalDate periodDate);
```

---

### Decisão 3 — Frontend: NavComponent compartilhado

US-008 e US-009 exigem (AC7) links de navegação visíveis a partir do dashboard. Em vez de duplicar links em cada componente:

**Decisão: `NavComponent` standalone** adicionado ao `app.html` acima do `<router-outlet>`. Contém links para `/dashboard`, `/indirect-activities`, `/summary`.

```
app/
└── shared/
    └── nav/
        ├── nav.component.ts
        └── nav.component.html
```

---

### Decisão 4 — OeeService: extensão com novos métodos

`OeeService` existente recebe dois novos métodos:

```typescript
// Tipagem frontend para GroupBy
export type GroupBy = 'DAY' | 'WEEK' | 'MONTH';

export interface IndirectActivityDto {
  description: string;
  occurrences: number;
  totalHours: number;
  percentOfTotal: number;
}

export interface PeriodSummaryDto {
  period: string;
  avgAvailability: number | null;
  workerCount: number;
}

// Novos métodos em OeeService
getIndirectActivities(startDate: string, endDate: string, workerId?: number): Observable<IndirectActivityDto[]>
getSummary(startDate: string, endDate: string, groupBy?: GroupBy): Observable<PeriodSummaryDto[]>
```

---

### Contrato de API — alterações Sprint 2

**POST /api/v1/oee/imports?overwrite=false** *(default — sem mudança)*
- 409: período já importado

**POST /api/v1/oee/imports?overwrite=true** *(novo comportamento)*
- 201: `{ batchId, periodDate, workerCount, recordsImported, recordsSkipped, errors[], replaced: true }`
- 422: arquivo inválido (overwrite NÃO ocorre se validação falhar)

**GET /api/v1/oee/dashboard | /indirect-activities | /summary** *(validação adicionada)*
- 400: `{ "message": "startDate must not be after endDate" }`
- 400: `{ "message": "Date range must not exceed 366 days" }`

---

### Consequências
✅ `DateRangeValidator` isolado e testável sem spring context (unit test puro)
✅ Overwrite atômico por `@Transactional` — sem risco de dados parciais
✅ `NavComponent` evita duplicação de links entre as 3 páginas
⚠️ Cascade ALL em `ImportBatch.timeRecords` — garantir que o relacionamento já está mapeado (checar entidade existente)
⚠️ `deleteByPeriodDate` causa flush imediato — usar `@Modifying + @Transactional` no repositório
