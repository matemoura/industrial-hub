## ADR-028: Production Data Import from Dynamics — Produtos, Estoque, OPs e Tempos de Ciclo
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-079, US-080, US-081

### Contexto

O Dynamics (ERP do MSB) é a **fonte de verdade** para toda a produção: famílias de produto, catálogo de produtos, estoque atual, ordens de produção (OPs) e lead times. O Hub não cria nem modifica esses dados — ele os **importa periodicamente** e adiciona uma camada de planejamento e visualização sobre eles.

O fluxo é:
```
Dynamics (fonte de verdade)
  ├── Famílias e produtos
  ├── Estoque atual por produto
  ├── Ordens de produção (status, qtd, datas)
  └── Lead times e tempos de ciclo
        ↓  importação via Excel (padrão OEE) ou planilha extraída do Dynamics
Industrial Hub (planejamento + visualização)
  ├── Importado (read-only): produtos, estoque, OPs, lead times, tempos de ciclo
  ├── Gerenciado pelo Hub: cargas de esterilização, plano MRP, plano de pessoal
  └── Visualizações: dashboard por família, tracking de cargas, board de planejamento
```

O padrão de importação via Excel já existe no módulo OEE (`ImportDynamicsExcelUseCase`) e é seguido aqui.

---

### Decisão 1 — Pacote `production/` (novo módulo)

O módulo se chama `production/` — não `mrp/` — porque engloba tanto o acompanhamento de dados vindos do Dynamics quanto o planejamento do Hub.

```
src/main/java/com/industrialhub/backend/production/
├── domain/
│   ├── ProductFamily.java
│   ├── Product.java
│   ├── ProductType.java           (enum)
│   ├── StockSnapshot.java
│   ├── ProductionOrder.java       (importada do Dynamics — read-only)
│   ├── ProductionOrderStatus.java (enum)
│   ├── CycleTime.java
│   └── ImportProductionBatch.java (metadados da importação)
├── application/
│   ├── dto/ ...
│   └── usecase/
│       ├── ImportProductCatalogUseCase.java
│       ├── ImportStockSnapshotUseCase.java
│       ├── ImportProductionOrdersUseCase.java
│       ├── ImportCycleTimesUseCase.java
│       ├── ImportLeadTimesUseCase.java
│       └── GetProductionTrackingUseCase.java
├── infrastructure/
│   ├── ProductFamilyRepository.java
│   ├── ProductRepository.java
│   ├── StockSnapshotRepository.java
│   ├── ProductionOrderRepository.java
│   └── CycleTimeRepository.java
└── presentation/
    └── ProductionController.java   (/api/v1/production/*)
```

---

### Decisão 2 — Entidade `ProductFamily`

```java
@Entity
@Table(name = "product_family")
public class ProductFamily {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;    // ex: "SER", "AGU", "CAT" — vem do Dynamics
    @Column(nullable = false, length = 200)
    private String name;    // ex: "Seringas", "Agulhas", "Cateteres"
    private boolean active = true;
}
```

---

### Decisão 3 — Entidade `Product`

```java
@Entity
@Table(name = "product", indexes = {
    @Index(name = "idx_product_code",   columnList = "dynamics_code", unique = true),
    @Index(name = "idx_product_family", columnList = "family_id"),
    @Index(name = "idx_product_type",   columnList = "type")
})
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 100)
    private String dynamicsCode;   // código no Dynamics (chave de matching em reimportações)

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;      // FINISHED | INTERMEDIATE | RAW_MATERIAL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily family;  // nullable para RAW_MATERIAL

    private String unit;           // "un", "cx", "kg"
    private Integer leadTimeDays;  // lead time de fabricação em dias — importado ou editável
    private Integer minStockQty;   // estoque mínimo de segurança — importado ou editável
    private Integer batchSize;     // tamanho padrão do lote de produção
    private boolean requiresSterilization; // true para produtos FINISHED sujeitos a esterilização
    private boolean active = true;
    private LocalDateTime lastSyncAt; // última vez que foi sincronizado do Dynamics
}

public enum ProductType { FINISHED, INTERMEDIATE, RAW_MATERIAL }
```

---

### Decisão 4 — Entidade `ProductionOrder` (importada do Dynamics, read-only no Hub)

```java
@Entity
@Table(name = "production_order", indexes = {
    @Index(name = "idx_po_dynamics_number", columnList = "dynamics_order_number", unique = true),
    @Index(name = "idx_po_product",         columnList = "product_id"),
    @Index(name = "idx_po_family",          columnList = "family_id"),
    @Index(name = "idx_po_status",          columnList = "status"),
    @Index(name = "idx_po_due_date",        columnList = "due_date"),
    @Index(name = "idx_po_batch",           columnList = "import_batch_id")
})
public class ProductionOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String dynamicsOrderNumber;  // número da OP no Dynamics (ex: "OP-2026-00123")

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private ProductFamily family;        // desnormalizado para filtros rápidos

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionOrderStatus status;  // reflete o status no Dynamics

    @Column(nullable = false)
    private Integer plannedQty;

    private Integer producedQty;     // quantidade já produzida (do Dynamics)
    private LocalDate startDate;
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportProductionBatch importBatch;  // batch da última importação que atualizou esta OP

    // Campos gerenciados pelo Hub (não vêm do Dynamics):
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sterilization_load_id")
    private SterilizationLoad sterilizationLoad;  // null até ser alocada em uma carga

    private Integer plannedPeople;  // calculado (ou editado por SUPERVISOR) a partir do CycleTime
    private boolean peopleOverridden; // true se SUPERVISOR editou manualmente
}

public enum ProductionOrderStatus {
    PLANNED, RELEASED, IN_PROGRESS, DONE, CANCELLED
    // Status vem do Dynamics; PENDING_STERILIZATION e STERILIZING são overlay do Hub (via SterilizationLoad)
}
```

**Regra de upsert**: na importação, se `dynamicsOrderNumber` já existe → atualizar campos do Dynamics (`status`, `producedQty`, `plannedQty`, `dueDate`). Preservar campos gerenciados pelo Hub (`sterilizationLoad`, `plannedPeople`, `peopleOverridden`).

---

### Decisão 5 — Entidade `StockSnapshot`

```java
@Entity
@Table(name = "stock_snapshot", indexes = {
    @Index(name = "idx_stock_product", columnList = "product_id"),
    @Index(name = "idx_stock_date",    columnList = "snapshot_date")
})
public class StockSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer qty;

    private LocalDate snapshotDate;    // data do snapshot (permite histórico)
    private LocalDateTime importedAt;
    private String importedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportProductionBatch importBatch;
}
```

Mantém **histórico** de snapshots de estoque (um registro por produto por importação). A posição mais recente por produto é consultada com `findTopByProductOrderBySnapshotDateDesc()`.

---

### Decisão 6 — Entidade `CycleTime`

```java
@Entity
@Table(name = "cycle_time",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "effective_date"}))
public class CycleTime {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Double secondsPerUnit;  // tempo de ciclo: segundos por unidade produzida

    private LocalDate effectiveDate; // a partir de quando este ciclo é válido (versionado)
    private String importedBy;
    private LocalDateTime importedAt;
}
```

Versionado por data: ao importar um novo tempo de ciclo para o mesmo produto, cria-se um novo registro com `effectiveDate = hoje`. O motor de staffing usa sempre o mais recente.

---

### Decisão 7 — Entidade `ImportProductionBatch` (metadados de importação)

```java
@Entity
@Table(name = "import_production_batch")
public class ImportProductionBatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ProductionImportType type; // PRODUCT_CATALOG | STOCK | PRODUCTION_ORDERS | CYCLE_TIMES | LEAD_TIMES

    private String fileName;
    private LocalDateTime importedAt;
    private String importedBy;
    private Integer totalRecords;
    private Integer createdRecords;
    private Integer updatedRecords;
    private Integer errorRecords;
}

public enum ProductionImportType {
    PRODUCT_CATALOG, STOCK, PRODUCTION_ORDERS, CYCLE_TIMES, LEAD_TIMES
}
```

---

### Decisão 8 — Formato das planilhas de importação

Cada planilha segue o padrão OEE (upload via `multipart/form-data`).

**Catálogo de produtos** (`/import/products`):
```
dynamics_code | name | type | family_code | family_name | unit | requires_sterilization
```

**Estoque** (`/import/stock`):
```
dynamics_code | qty | snapshot_date
```

**Ordens de Produção** (`/import/production-orders`):
```
op_number | dynamics_code | status | planned_qty | produced_qty | start_date | due_date
```

**Tempos de Ciclo** (`/import/cycle-times`):
```
dynamics_code | seconds_per_unit | effective_date
```

**Lead times** (`/import/lead-times`):
```
dynamics_code | lead_time_days | min_stock_qty | batch_size
```

---

### Decisão 9 — Endpoints de importação e consulta

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/production/import/products | ADMIN | importar catálogo de produtos |
| POST | /api/v1/production/import/stock | SUPERVISOR+ | importar snapshot de estoque |
| POST | /api/v1/production/import/production-orders | SUPERVISOR+ | importar OPs do Dynamics |
| POST | /api/v1/production/import/cycle-times | ADMIN | importar tempos de ciclo |
| POST | /api/v1/production/import/lead-times | ADMIN | importar lead times e lotes |
| GET | /api/v1/production/import/history | SUPERVISOR+ | histórico de importações |
| GET | /api/v1/production/products | OPERATOR+ | listar produtos (filtros: family, type) |
| GET | /api/v1/production/products/{id} | OPERATOR+ | detalhe + estoque atual + tempo de ciclo |
| GET | /api/v1/production/families | OPERATOR+ | listar famílias |
| GET | /api/v1/production/stock | SUPERVISOR+ | posições de estoque (filtro: belowMin) |

---

### Consequências
✅ Hub nunca é fonte de verdade de produção — Dynamics permanece como master data
✅ Upsert por `dynamicsOrderNumber` — reimportações atualizando campos do Dynamics, preservando campos do Hub (carga, pessoas)
✅ `StockSnapshot` com histórico — permite análise de evolução de estoque no tempo
✅ `CycleTime` versionado — mudanças de processo não destroem histórico de planejamento
⚠️ Importação é manual (upload de Excel) — frequência de sincronização depende do usuário; evolução futura: webhook do Dynamics ou polling via API
⚠️ Status da OP no Hub é sempre o do Dynamics na última importação — Hub pode estar desatualizado entre importações; exibir `lastSyncAt` no frontend para o usuário saber a defasagem
