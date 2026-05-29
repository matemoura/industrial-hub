## ADR-044: Sprint 33 — BOM Import, Production Planning Report & Tech Debt MRP
**Status**: Aprovado
**Data**: 2026-05-31
**US relacionadas**: US-101, US-102, US-103

---

### Contexto

O ADR-030 (Decisão 3) previa explosão de BOM de 2 níveis no `MrpCalculationService`, mas a execução do Sprint 32 confirmou que a entidade `ProductComponent` (BOM) ainda não existe no domínio — `MrpCalculationService` gera `purchaseNeeds` com `qty = null` para RAW_MATERIAL como placeholder explícito. Este ADR cobre:

1. **BOM import** (US-101): entidade `ProductComponent`, importação via Excel do Dynamics, integração com o motor MRP para explosão real de necessidades de componentes e matérias-primas
2. **Production planning report** (US-102): endpoint de aggregation planned vs. actual por produto/família, exportação CSV para Excel pt-BR
3. **Tech debt MRP** (US-103): SEC-116 (`@Size` em `RejectMrpSuggestionRequest`), SEC-117 (cache `StaffingConfig` antes do loop), SEC-118 (`clearTimeout` no `PlanningBoardComponent`)

---

### Decisão 1 — Entidade `ProductComponent` (BOM normalizado)

A estrutura de BOM é modelada como uma entidade de relacionamento N:N entre produtos, evitando JSON aninhado:

```java
@Entity
@Table(
    name = "product_component",
    indexes = {
        @Index(name = "idx_bom_parent",    columnList = "parent_product_id"),
        @Index(name = "idx_bom_component", columnList = "component_product_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bom_parent_component",
            columnNames = {"parent_product_id", "component_product_id"}
        )
    }
)
public class ProductComponent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id", nullable = false)
    private Product parentProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_product_id", nullable = false)
    private Product componentProduct;

    @Column(nullable = false)
    private Double quantity;      // unidades do componente por unidade do produto pai

    @Column(length = 10)
    private String unit;          // ex: "UN", "KG", "M" — conforme Dynamics

    @Column(nullable = false)
    private Integer level;        // 1 = componente direto, 2 = sub-componente

    @Column(nullable = false)
    private Boolean active = true;
}
```

**Justificativa**: BOM normalizado (tabela de adjacência) permite queries eficientes por `parentProduct` sem desserializar JSON. O `purchaseNeedsJson` em `MrpRun` permanece como snapshot histórico do run — o BOM normalizado é a fonte de verdade para novos runs.

**Alternativa descartada**: BOM em JSON aninhado na entidade `Product` — inviabiliza queries de "quais produtos usam este componente" e updates parciais sem reescrever o JSON completo.

---

### Decisão 2 — Estratégia de importação: substituição total por produto

Em vez de upsert linha a linha, a importação do BOM adota **substituição total por produto pai**:

```java
// ImportBomUseCase.java
@Transactional
public BomImportResponse execute(MultipartFile file, String importedBy) {
    // 1. ler Excel → Map<String parentCode, List<BomRow>>
    // 2. para cada parentCode no arquivo:
    //    a. buscar Product parentProduct pelo código (erro se não existe)
    //    b. deleteByParentProduct(parentProduct)  ← substitui BOM completo deste produto
    //    c. para cada BomRow: buscar componentProduct, criar ProductComponent, salvar
    // 3. retornar BomImportResponse com contadores globais
}

// ProductComponentRepository.java
@Modifying
@Query("DELETE FROM ProductComponent pc WHERE pc.parentProduct.dynamicsCode = :parentCode")
void deleteByParentProductCode(@Param("parentCode") String parentCode);
```

**Justificativa**: Um BOM parcial (upsert linha a linha) pode deixar componentes obsoletos que foram removidos no Dynamics. A substituição total por produto pai é mais segura e consistente com o padrão de importação do projeto (ADR-028 Decisão 3: upsert por código externo para OPs; BOM vai além — substitui o conjunto inteiro). Produtos sem linhas no arquivo Excel **não têm seu BOM alterado** — apenas os produtos presentes no arquivo são afetados.

---

### Decisão 3 — Integração BOM × MRP: explosão de nível 1

O `MrpCalculationService` é atualizado para considerar o BOM ao gerar `purchaseNeeds`:

```java
// MrpCalculationService.java — trecho atualizado
private List<PurchaseNeedResponse> explodePurchaseNeeds(
        Product product, int suggestedQty, Map<String, List<ProductComponent>> bomByParent) {

    List<ProductComponent> components = bomByParent.getOrDefault(product.getDynamicsCode(), List.of());

    if (components.isEmpty()) {
        // Comportamento legado: BOM não cadastrado — purchaseNeeds placeholder
        return List.of(new PurchaseNeedResponse(
            product.getDynamicsCode(), product.getName(),
            null, "UN", "BOM não cadastrado para este produto"
        ));
    }

    return components.stream()
        .filter(c -> c.getComponentProduct().getType() == ProductType.RAW_MATERIAL)
        .map(c -> new PurchaseNeedResponse(
            c.getComponentProduct().getDynamicsCode(),
            c.getComponentProduct().getName(),
            (double) suggestedQty * c.getQuantity(),
            c.getUnit(),
            null  // sem observação — dados reais do BOM
        ))
        .toList();

    // Componentes INTERMEDIATE → geram MrpPlannedOrder próprio (explosão recursiva nível 1)
    // Componentes RAW_MATERIAL → entram diretamente em purchaseNeeds
}
```

**BOM carregado uma única vez** antes do loop de produtos para evitar N+1:
```java
Map<String, List<ProductComponent>> bomByParent =
    componentRepository.findAllActive().stream()
        .collect(Collectors.groupingBy(c -> c.getParentProduct().getDynamicsCode()));
```

**Nível máximo de explosão**: 1 nível no MVP. Sub-componentes (nível 2) não são explodidos — o ADR-030 previa "2 níveis" como aspiracional; para o volume do MSB (~53 usuários), nível 1 cobre a maioria dos casos de uso de compras.

**Justificativa**: explosão recursiva completa requer análise de ciclos no BOM (produto que é componente de si mesmo) e aumenta complexidade do algoritmo sem valor imediato para o MSB. Expansível para nível 2 em sprint futura adicionando um loop na recursão.

---

### Decisão 4 — Formato do arquivo Excel de BOM

Colunas obrigatórias (case-insensitive, baseadas nos exports padrão do Dynamics 365):

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `parent_code` | String | `dynamicsCode` do produto pai (FINISHED ou INTERMEDIATE) |
| `component_code` | String | `dynamicsCode` do componente (INTERMEDIATE ou RAW_MATERIAL) |
| `quantity` | Double | Quantidade do componente por unidade do produto pai |
| `unit` | String | Unidade de medida (UN, KG, M, etc.) |

**Template para download**: arquivo `.xlsx` com cabeçalho e 2 linhas de exemplo; gerado inline no backend via `XSSFWorkbook` (Apache POI já disponível — sem nova dependência).

**Endpoint de download do template**: `GET /api/v1/production/import/bom/template` (SUPERVISOR+) — retorna bytes do `.xlsx`; consistente com padrão de templates de outros módulos de importação.

---

### Decisão 5 — Endpoint de relatório de planejamento

```java
// ProductionController.java — novos endpoints
@GetMapping("/reports/planning-summary")
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public List<PlanningSummaryRow> getPlanningSummary(
        @RequestParam(required = false) String familyCode,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { ... }

@GetMapping("/reports/planning-summary/export")
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public ResponseEntity<byte[]> exportPlanningSummaryCsv(
        @RequestParam(required = false) String familyCode,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { ... }
```

**DTO de resposta**:
```java
public record PlanningSummaryRow(
    String familyCode,
    String familyName,
    String productCode,
    String productName,
    Integer plannedQty,    // soma plannedQty OPs [PLANNED, RELEASED, IN_PROGRESS, DONE] com dueDate no período
    Integer producedQty,   // soma producedQty OPs DONE com dueDate no período
    Double efficiency,     // producedQty * 100.0 / plannedQty; null se plannedQty = 0
    Integer pendingMrpQty  // suggestedQty de MrpPlannedOrders [SUGGESTED, ACCEPTED] do produto
) {}
```

**Query de aggregation** (JPQL):
```java
@Query("""
    SELECT new com.industrialhub.backend.production.application.dto.PlanningSummaryRow(
        f.code, f.name, p.dynamicsCode, p.name,
        COALESCE(SUM(po.plannedQty), 0),
        COALESCE(SUM(CASE WHEN po.status = 'DONE' THEN po.producedQty ELSE 0 END), 0),
        CAST(NULL AS java.lang.Double),
        COALESCE((SELECT SUM(m.suggestedQty) FROM MrpPlannedOrder m
                  WHERE m.product = p AND m.status IN ('SUGGESTED','ACCEPTED')), 0)
    )
    FROM ProductionOrder po
    JOIN po.product p
    JOIN p.family f
    WHERE (:familyCode IS NULL OR f.code = :familyCode)
      AND po.dueDate BETWEEN :from AND :to
      AND po.status <> 'CANCELLED'
    GROUP BY f.code, f.name, p.dynamicsCode, p.name
    ORDER BY f.code, p.dynamicsCode
""")
List<PlanningSummaryRow> findPlanningSummary(...);
```

**`efficiency`** calculado em Java (não em JPQL) para evitar divisão por zero com semântica clara:
```java
double eff = row.plannedQty() > 0
    ? row.producedQty() * 100.0 / row.plannedQty()
    : null;
```

**Justificativa**: query JPQL com `COALESCE` é mais legível e consistente com o padrão do projeto do que query nativa. `efficiency` calculada em Java segue o princípio "lógica de negócio no use case" (ADR-001).

---

### Decisão 6 — Exportação CSV: charset UTF-8 BOM

Exportação CSV segue o padrão já estabelecido na US-023 (NCs) e US-044 (analytics), com ajuste para Excel pt-BR:

```java
// GetPlanningSummaryUseCase.java
public byte[] exportCsv(String familyCode, LocalDate from, LocalDate to) {
    List<PlanningSummaryRow> rows = getSummary(familyCode, from, to);
    StringBuilder sb = new StringBuilder();
    sb.append('﻿'); // UTF-8 BOM — compatibilidade com Excel pt-BR (separador ponto-e-vírgula)
    sb.append("Família;Produto;Código;Qtd Planejada;Qtd Produzida;Eficiência (%);Sugestões MRP Pendentes\n");
    for (var row : rows) {
        sb.append(row.familyName()).append(';')
          .append(row.productName()).append(';')
          .append(row.productCode()).append(';')
          .append(row.plannedQty()).append(';')
          .append(row.producedQty()).append(';')
          .append(row.efficiency() != null
              ? String.format("%.1f", row.efficiency()).replace('.', ',')
              : "").append(';')
          .append(row.pendingMrpQty()).append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
}
```

**Content-Disposition**: `attachment; filename="planejamento-{from}-{to}.csv"`

**Justificativa**: UTF-8 BOM (`﻿`) é necessário para que o Excel pt-BR (configurado para separador ponto-e-vírgula) interprete corretamente os caracteres acentuados. Separador `;` é padrão pt-BR. Eficiência formatada com vírgula decimal (ex: "95,3") para consistência com localização brasileira.

---

### Decisão 7 — Tech debt: fixes cirúrgicos do Sprint 32

**SEC-116 — `@Size` em `RejectMrpSuggestionRequest`** (1 linha):
```java
// RejectMrpSuggestionRequest.java
public record RejectMrpSuggestionRequest(
    @NotBlank String reason,
    @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres") String reason
) {}
// Nota: controller RejectMrpSuggestionController já tem @Validated na classe (ADR-031)
```

**SEC-117 — Cache `StaffingConfig` antes do loop** (refatoração cirúrgica):
```java
// ImportProductionOrdersUseCase.java — trecho refatorado
StaffingConfig config = staffingConfigUseCase.getOrCreate();  // ← fora do loop
for (Row row : rows) {
    // ... processar OP ...
    calculateStaffing(order, config);  // ← recebe config como parâmetro
}
```

**SEC-118 — `clearTimeout` no `PlanningBoardComponent`**:
```typescript
// planning-board.component.ts
private toastTimeoutId: ReturnType<typeof setTimeout> | null = null;

showToast(msg: string): void {
    this.toast.set(msg);
    if (this.toastTimeoutId !== null) clearTimeout(this.toastTimeoutId);
    this.toastTimeoutId = setTimeout(() => this.toast.set(null), 4000);
}

ngOnDestroy(): void {
    if (this.toastTimeoutId !== null) clearTimeout(this.toastTimeoutId);
}
```

**Justificativa**: SEC-116 previne ISE genérico (500) em payloads >500 chars, alinhando ao `@Column(length=500)` da entidade. SEC-117 é otimização de performance para importações de OPs com muitos registros (evita N queries desnecessárias). SEC-118 é correção de boas práticas que previne callbacks pós-destruição em testes e produção.

---

### Contrato de API — Sprint 33

**BOM:**
| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| POST | /api/v1/production/import/bom | ADMIN | 200 | Import BOM via Excel |
| GET | /api/v1/production/import/bom/template | SUPERVISOR+ | 200 | Download template Excel |
| GET | /api/v1/production/products/{code}/bom | SUPERVISOR+ | 200 | Estrutura BOM do produto |

**Relatório:**
| Método | Endpoint | Auth | HTTP | Descrição |
|--------|----------|------|------|-----------|
| GET | /api/v1/production/reports/planning-summary | SUPERVISOR+ | 200 | Planned vs actual por produto |
| GET | /api/v1/production/reports/planning-summary/export | SUPERVISOR+ | 200 | CSV para download |

**Erros esperados:**
- `400` — arquivo Excel inválido / colunas ausentes / `@Size` violado
- `404` — produto não encontrado na importação BOM (linha ignorada com erro, importação continua)

---

### Consequências

✅ BOM normalizado permite explosão MRP real de nível 1 — `purchaseNeeds` com quantidades calculadas corretamente (antes: placeholder sem qty)
✅ Substituição total por produto pai na importação garante BOM sempre consistente com o Dynamics — sem componentes obsoletos acumulados
✅ Relatório planned vs actual fecha o ciclo de gestão: importação → MRP → execução → análise de resultado
✅ CSV com UTF-8 BOM e separador `;` abre corretamente no Excel pt-BR sem configuração manual do usuário
✅ SEC-117 elimina N queries de `StaffingConfig` por OP importada — impacto visível em lotes grandes (ex: 500 OPs = 499 queries desnecessárias eliminadas)
⚠️ Explosão BOM limitada a nível 1 — sub-componentes (nível 2) não são explodidos; SUPERVISOR deve interpretar `purchaseNeeds` de INTERMEDIATE como aproximação; expansão para nível 2 requer análise de ciclos no BOM (sprint futura)
⚠️ `deleteByParentProductCode` na importação é destrutivo — uma planilha com linha `parent_code` errada substitui o BOM daquele produto; mitigação: validação de existência do `parent_code` antes de deletar (erro de linha não executa delete)
⚠️ Relatório sem paginação — assume volume < 200 produtos ativos para 53 usuários; adicionar `@PageableDefault` se o catálogo crescer além de 500 produtos
⚠️ Template BOM gerado inline (Apache POI) — sem cache; para 53 usuários, geração a cada request é aceitável; considerar cache de bytes em `@PostConstruct` em sprint futura se latência for problema
