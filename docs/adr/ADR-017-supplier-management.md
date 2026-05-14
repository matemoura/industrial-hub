## ADR-017: Supplier Management — Cadastro e Score de Qualidade
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-057, US-058

### Contexto

`NcType.SUPPLIER` indica que a não-conformidade tem origem em um fornecedor, mas o sistema não associa a NC a um fornecedor específico. Sem rastreabilidade, não é possível calcular a taxa de qualidade por fornecedor, o que é exigido em auditorias ISO 9001. Esta ADR adiciona o cadastro de fornecedores e o score automático de qualidade.

---

### Decisão 1 — Entidade `Supplier`

```java
@Entity
@Table(name = "supplier", indexes = {
    @Index(name = "idx_supplier_code", columnList = "code", unique = true),
    @Index(name = "idx_supplier_active", columnList = "active")
})
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;         // ex: "FORN-001"

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String contactEmail;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 200)
    private String address;

    private boolean active = true;
    private LocalDate onboardedAt;
}
```

---

### Decisão 2 — Associação opcional `NonConformance → Supplier`

```java
// NonConformance — campo nullable (retrocompatível)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "supplier_id")
private Supplier supplier;  // obrigatório quando type = SUPPLIER; null para demais tipos
```

**Validação no use case**: se `type = SUPPLIER` e `supplierId` ausente no request → `400` com `{ "message": "supplierId é obrigatório para NCs do tipo SUPPLIER" }`. Para outros tipos, `supplierId` é ignorado mesmo se enviado.

`CreateNcRequest` e `NcResponse` ganham campo `supplierId` (UUID nullable).

---

### Decisão 3 — Score de qualidade

O score é calculado sob demanda (não materializado):

```java
// SupplierQualityScore — DTO de resposta, sem entidade persistida
public record SupplierQualityScore(
    UUID supplierId,
    String supplierName,
    long totalNcs,             // NCs tipo SUPPLIER no período
    long criticalNcs,          // NCs com severity=CRITICAL
    long highNcs,
    double qualityScore        // 100 - (criticalNcs*5 + highNcs*2 + otherNcs*1) / max(totalNcs,1) * 100
) {}
```

Formula de score é opinativa e pode ser ajustada via configuração futura. A lógica fica em `GetSupplierQualityUseCase` e é calculada em Java sobre lista de NCs do período.

---

### Decisão 4 — Package

```
qms/
├── domain/
│   └── Supplier.java          // entidade em qms/ (domínio de qualidade)
├── application/dto/
│   ├── CreateSupplierRequest.java
│   ├── SupplierResponse.java
│   └── SupplierQualityScore.java
├── application/usecase/
│   ├── CreateSupplierUseCase.java
│   ├── GetSupplierListUseCase.java
│   ├── UpdateSupplierUseCase.java
│   ├── DeactivateSupplierUseCase.java
│   └── GetSupplierQualityUseCase.java
├── infrastructure/
│   └── SupplierRepository.java
└── presentation/
    └── SupplierController.java   (/api/v1/qms/suppliers)
```

---

### Decisão 5 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/qms/suppliers | ADMIN | criar fornecedor |
| GET | /api/v1/qms/suppliers | OPERATOR+ | listar ativos |
| GET | /api/v1/qms/suppliers/{id} | OPERATOR+ | detalhe |
| PUT | /api/v1/qms/suppliers/{id} | ADMIN | atualizar |
| PUT | /api/v1/qms/suppliers/{id}/deactivate | ADMIN | desativar |
| GET | /api/v1/qms/suppliers/{id}/quality-score?days=90 | SUPERVISOR+ | score de qualidade |
| GET | /api/v1/qms/suppliers/quality-ranking?days=90 | SUPERVISOR+ | ranking de todos fornecedores |

---

### Decisão 6 — Frontend

- Rota `/qms/suppliers`: tabela de fornecedores com código, nome, contato, status
- Formulário de NC atualizado: quando tipo = "SUPPLIER", exibe campo autocomplete de fornecedor
- Rota `/qms/suppliers/{id}`: detalhe com card de score de qualidade (score %, NCs totais, críticas)
- Rota `/qms/suppliers/ranking`: tabela ordenada por score ASC (piores no topo) com filtro de período
- Score exibido como gauge de cor: verde (≥80), amarelo (60–79), vermelho (<60)

---

### Consequências
✅ Score calculado em Java — sem stored procedure, sem tabela nova
✅ Campo `supplierId` nullable — retrocompatível; NCs antigas sem fornecedor permanecem válidas
⚠️ Migration: coluna `supplier_id` nullable em `non_conformance` + tabela `supplier`
⚠️ Formula de score é arbitrária — documentar como "pode ser configurável via `AlertThreshold` em sprint futura"
