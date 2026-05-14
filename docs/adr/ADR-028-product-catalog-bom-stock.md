## ADR-028: Product Catalog, BOM & Stock Levels — Catálogo de Produtos e Gestão de Estoque
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-079, US-080, US-081

### Contexto

O MSB fabrica dispositivos médicos (seringas, agulhas, cateteres e produtos intermediários) em processo que inclui etapas de fabricação e esterilização. Não existe no sistema nenhuma entidade de produto, família de produto ou BOM. Este ADR é a **fundação** do módulo MRP: define o catálogo de produtos, estrutura de BOM simplificada, posições de estoque e importação de lead times.

**Separação clara de domínios:**
- **OEE** → eficiência de mão de obra (tempo de trabalhadores, importação do Dynamics) — **sem mudanças**
- **Maintenance** → equipamentos e OSs de manutenção — **sem mudanças**
- **MRP (novo)** → produtos, BOM, estoque, ordens de produção, planejamento

---

### Decisão 1 — Package `mrp/`

```
src/main/java/com/industrialhub/backend/mrp/
├── domain/
│   ├── Product.java
│   ├── ProductFamily.java
│   ├── ProductType.java        (enum)
│   ├── BomItem.java
│   └── StockLevel.java
├── application/
│   ├── dto/
│   │   ├── CreateProductRequest.java
│   │   ├── ProductResponse.java
│   │   ├── BomItemResponse.java
│   │   ├── StockLevelResponse.java
│   │   └── ImportLeadTimesResult.java
│   └── usecase/
│       ├── CreateProductUseCase.java
│       ├── GetProductListUseCase.java
│       ├── GetProductDetailUseCase.java
│       ├── UpdateProductUseCase.java
│       ├── CreateBomItemUseCase.java
│       ├── AdjustStockUseCase.java
│       └── ImportLeadTimesUseCase.java
├── infrastructure/
│   ├── ProductRepository.java
│   ├── ProductFamilyRepository.java
│   ├── BomItemRepository.java
│   └── StockLevelRepository.java
└── presentation/
    └── MrpController.java         (/api/v1/mrp/*)
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
    private String code;      // ex: "SER", "AGU", "CAT"

    @Column(nullable = false, length = 200)
    private String name;      // ex: "Seringas", "Agulhas", "Cateteres"

    private String description;
    private boolean active = true;
}
```

---

### Decisão 3 — Entidade `Product`

```java
@Entity
@Table(name = "product", indexes = {
    @Index(name = "idx_product_code",   columnList = "code", unique = true),
    @Index(name = "idx_product_family", columnList = "family_id"),
    @Index(name = "idx_product_type",   columnList = "type")
})
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;          // ex: "SER-5ML", "INT-CILINDRO-5ML"

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;     // FINISHED | INTERMEDIATE | RAW_MATERIAL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily family; // nullable para RAW_MATERIAL

    private String unit;          // "un", "cx", "kg"
    private Integer leadTimeDays; // lead time de fabricação em dias úteis
    private Integer minStockQty;  // estoque mínimo de segurança
    private Integer batchSize;    // tamanho padrão de lote de produção (ex: 1000 unidades/OP)
    private boolean requiresSterilization; // true para produtos FINISHED sujeitos a esterilização
    private boolean active = true;
}

public enum ProductType {
    FINISHED,       // produto acabado (ex: Seringa 5mL estéril)
    INTERMEDIATE,   // produto intermediário (ex: Cilindro 5mL sem êmbolo, sem esterilização)
    RAW_MATERIAL    // matéria-prima (ex: Polipropileno grau médico)
}
```

---

### Decisão 4 — Entidade `BomItem` (BOM de nível único)

```java
@Entity
@Table(name = "bom_item", indexes = {
    @Index(name = "idx_bom_parent", columnList = "parent_product_id"),
    @Index(name = "idx_bom_child",  columnList = "child_product_id")
})
public class BomItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id", nullable = false)
    private Product parentProduct;   // produto que está sendo fabricado

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_product_id", nullable = false)
    private Product childProduct;    // componente (RAW_MATERIAL ou INTERMEDIATE)

    @Column(nullable = false)
    private Double quantity;         // quantidade necessária por unidade do pai

    private String unit;             // unidade do componente
}
```

**BOM de nível único** — sem explosão multinível no MVP. Cada produto tem um BOM plano: `FINISHED → [INTERMEDIATE, RAW_MATERIAL]` ou `INTERMEDIATE → [RAW_MATERIAL]`. Explosão multinível é calculada em Java via recursão nos use cases de MRP quando necessário.

**Regras de validação:**
- `childProduct` não pode ser `FINISHED` (sem autoconsumo circular)
- Mesmo par `(parentProduct, childProduct)` não pode aparecer duas vezes no BOM

---

### Decisão 5 — Entidade `StockLevel`

```java
@Entity
@Table(name = "stock_level")
public class StockLevel {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;   // 1 produto → 1 posição de estoque

    @Column(nullable = false)
    private Integer currentQty = 0;

    private LocalDateTime lastUpdatedAt;
    private String lastUpdatedBy;
}
```

Sem gestão de localização (bin/slot) — posição única por produto. `StockLevel` criada automaticamente com `currentQty = 0` quando um produto é cadastrado.

---

### Decisão 6 — Importação de lead times via Excel

Seguindo o padrão de importação do OEE (`ImportDynamicsExcelUseCase`):

`POST /api/v1/mrp/products/import-lead-times` (multipart, ADMIN) recebe planilha com colunas:
```
code | leadTimeDays | minStockQty | batchSize
```

Produtos existentes com `code` correspondente são atualizados. Códigos não encontrados são reportados como erros (`unknownCodes: [...]`). Retorna `ImportLeadTimesResult`:
```json
{ "updated": 45, "errors": [{ "code": "XXX-001", "reason": "Produto não encontrado" }] }
```

---

### Decisão 7 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/mrp/families | ADMIN | criar família |
| GET | /api/v1/mrp/families | OPERATOR+ | listar famílias |
| POST | /api/v1/mrp/products | ADMIN | criar produto |
| GET | /api/v1/mrp/products | OPERATOR+ | listar (filtros: family, type, active) |
| GET | /api/v1/mrp/products/{id} | OPERATOR+ | detalhe + BOM + estoque |
| PUT | /api/v1/mrp/products/{id} | ADMIN | atualizar |
| POST | /api/v1/mrp/products/{id}/bom | ADMIN | adicionar item ao BOM |
| DELETE | /api/v1/mrp/products/{id}/bom/{bomItemId} | ADMIN | remover item do BOM |
| GET | /api/v1/mrp/stock | OPERATOR+ | posições de estoque (filtro: belowMin, family) |
| PUT | /api/v1/mrp/stock/{productId}/adjust | SUPERVISOR+ | ajuste manual de estoque |
| POST | /api/v1/mrp/products/import-lead-times | ADMIN | importar lead times via Excel |

---

### Consequências
✅ BOM de nível único no banco — explosão multinível em Java quando necessário (MRP engine, Sprint 32)
✅ `StockLevel` criado automaticamente no cadastro do produto — sem step extra para o usuário
✅ Importação de lead times via Excel — mesmo padrão do OEE (usuários já conhecem o fluxo)
✅ `requiresSterilization` no `Product` — flag limpo para decidir se OP entra em carga de esterilização
⚠️ Sem controle de lote de estoque (lot traceability) — rastreabilidade granular por lote é out-of-scope (seria um WMS completo); para ANVISA, usar `SterilizationLoad` como unidade de rastreabilidade
⚠️ `batchSize` é sugestão padrão — OP pode ter quantidade diferente; MRP usa `batchSize` como quantidade default ao gerar OPs planejadas
