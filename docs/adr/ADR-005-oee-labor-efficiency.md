## ADR-005: Módulo OEE — Labor Efficiency
**Status**: Aprovado
**Data**: 2026-05-10
**US relacionadas**: US-001 a US-005 (Sprint 1 — OEE)

### Contexto
O módulo de OEE não rastreia máquinas — rastreia eficiência de mão de obra. A fonte de dados é um Excel exportado manualmente do Dynamics (production floor), contendo apontamentos de tempo por trabalhador com diferenciação entre registros produtivos (`Processo`) e não-produtivos (`Atividade indireta`).

O cálculo atual assume 100% de produtividade pelo tempo de ponto. O sistema substituirá isso por:
`Disponibilidade = Σ horas[Tipo='Processo'] / (timestamp saída − timestamp entrada)`

Sprint 1 entrega apenas Disponibilidade. Performance e Qualidade dependem de dados não presentes no arquivo atual.

### Decisão 1 — Estrutura de pacotes: feature-first

Opções consideradas:
1. **Layer-first** (`controller/`, `service/`, `repository/` na raiz) — mistura os 4 módulos futuros
2. **Feature-first** (`oee/`, `qms/` como módulos auto-contidos) — cada módulo é coeso

**Decisão: Feature-first.** O projeto tem 4 módulos planejados. Com layer-first, as pastas acumulam classes de OEE + QMS + Portal juntos. Com feature-first, cada módulo pode crescer ou ser extraído independentemente.

```
src/main/java/com/industrialhub/backend/
└── oee/
    ├── domain/
    ├── application/
    │   ├── dto/
    │   ├── usecase/
    │   └── parser/
    ├── infrastructure/
    └── presentation/
```

### Decisão 2 — Parser do Excel: Apache POI

Opções consideradas:
1. **Apache POI** — padrão para .xlsx, suporte a encoding, maduro
2. **EasyExcel** — mais performático para volumes altos
3. **OpenCSV** — não suporta .xlsx, descartado

**Decisão: Apache POI.** Volume atual ~4.400 linhas/mês. Streaming desnecessário. Encoding Windows-1252 do Dynamics tratado nativamente via `WorkbookFactory`.

Dependência:
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

### Decisão 3 — Cálculo OEE: on-the-fly, sem pré-agregação

Opções consideradas:
1. **Calcular na query** — sem tabela extra, sempre atualizado
2. **Pré-agregar em tabela** — mais rápido para dashboards, requer recálculo ao reimportar

**Decisão: Calcular na query.** Com 13 trabalhadores e ~4.400 registros/mês, a query de Disponibilidade é uma soma simples com GROUP BY. Pré-agregação entra se o volume crescer.

### Decisão 4 — Detecção de duplicatas: ImportBatch

Cada upload gera um `ImportBatch` com a data de cobertura. O sistema bloqueia reimportação do mesmo período com 409 Conflict e opção de sobrescrita.

### Modelo de dados

```
import_batch
  id UUID PK
  file_name VARCHAR
  imported_at TIMESTAMP
  period_date DATE
  total_records INT
  worker_count INT

time_record
  id UUID PK
  batch_id UUID FK → import_batch
  worker_id BIGINT
  worker_name VARCHAR
  profile_date DATE
  start_time TIMESTAMP
  end_time TIMESTAMP
  record_type VARCHAR (enum: PROCESSO, ATIVIDADE_INDIRETA, REGISTRO_ENTRADA, REGISTRO_SAIDA, INTERVALO)
  reference VARCHAR
  operation_number INT
  work_identifier VARCHAR
  description VARCHAR
  hours DECIMAL(10,4)
```

Nota: worker_id/worker_name desnormalizados em time_record — Worker não vira entidade no Sprint 1.
Revisitar quando Auth (ADR-004) associar roles a trabalhadores.

### Contrato de API

Base: `/api/v1/oee/` — sem autenticação no Sprint 1.

**POST /api/v1/oee/imports**
- Request: multipart/form-data { file: .xlsx }
- 201: { batchId, periodDate, workerCount, recordsImported, recordsSkipped, errors[] }
- 409: dados do período já importados
- 422: coluna ausente no arquivo

**GET /api/v1/oee/dashboard?startDate=&endDate=**
- 200: [{ workerId, workerName, date, productiveHours, indirectHours, shiftDuration, availability }]

**GET /api/v1/oee/indirect-activities?startDate=&endDate=&workerId=**
- 200: [{ description, occurrences, totalHours, percentOfTotal }]

**GET /api/v1/oee/summary?startDate=&endDate=&groupBy=DAY|WEEK|MONTH**
- 200: [{ period, avgAvailability, workerCount }]

### Consequências
✅ Feature-first prepara o repo para 4 módulos sem conflito de namespaces
✅ Apache POI resolve encoding Windows-1252 do arquivo Dynamics
✅ Cálculo on-the-fly correto e simples para volume atual
✅ ImportBatch previne reimportações sem complexidade extra
⚠️ worker_name desnormalizado — inconsistente se Dynamics mudar o nome cadastrado
⚠️ Spring Boot versão no pom.xml marcada como 4.0.6 (não existe) — provável typo de 3.0.6; confirmar antes de implementar
🔮 Pré-agregar se dashboard ficar lento com > 50k registros
🔮 Normalizar Worker como entidade quando Auth precisar associar roles
🔮 Integração direta via Dynamics API (sem Excel manual) — candidato Sprint 3+
