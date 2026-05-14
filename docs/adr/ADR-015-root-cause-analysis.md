## ADR-015: Root Cause Analysis — 5-Porquês vinculado a NC
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-052, US-053

### Contexto

O módulo QMS registra NCs e ações corretivas, mas não estrutura a análise da causa raiz. A técnica dos 5-Porquês é o método mais simples e amplamente adotado em ambientes industriais (ISO 9001, TPM). Esta ADR adiciona uma entidade de análise vinculada à NC, com até 5 iterações "Por quê → Resposta".

---

### Decisão 1 — Entidade `RootCauseAnalysis`

```java
@Entity
@Table(name = "root_cause_analysis")
public class RootCauseAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nc_id", nullable = false, unique = true)
    private NonConformance nonConformance;  // 1 NC → 0 ou 1 RCA

    // Os 5 porquês (why1–why5) e suas respostas
    @Column(nullable = false, length = 500)
    private String why1;
    @Column(length = 500)
    private String answer1;
    @Column(length = 500)
    private String why2;
    @Column(length = 500)
    private String answer2;
    @Column(length = 500)
    private String why3;
    @Column(length = 500)
    private String answer3;
    @Column(length = 500)
    private String why4;
    @Column(length = 500)
    private String answer4;
    @Column(length = 500)
    private String why5;
    @Column(length = 500)
    private String answer5;

    @Column(length = 1000)
    private String rootCause;     // conclusão final (causa raiz identificada)

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Decisão de modelagem**: colunas fixas (why1–why5) em vez de coleção (`@OneToMany`). Justificativa: máximo de 5 iterações é uma constraint do negócio; colunas fixas simplificam queries e evitam JOIN extra para exibição.

---

### Decisão 2 — Regras de negócio

- Apenas NCs com `status = IN_ANALYSIS` ou `CLOSED` podem ter RCA criada
- Tentativa em NC com `status = OPEN` retorna `422` com `{ "message": "RCA só pode ser criada após início da análise" }`
- `why1` e `answer1` são obrigatórios; os demais pares são opcionais
- Uma NC pode ter no máximo 1 RCA — `POST` em NC que já tem RCA retorna `409`
- RCA pode ser atualizada via `PUT` (substituição completa) enquanto NC não estiver `CLOSED`
- NC fechada → RCA somente leitura (retorna `422` em `PUT`)

---

### Decisão 3 — Package

```
qms/
├── domain/
│   └── RootCauseAnalysis.java
├── application/dto/
│   ├── CreateRcaRequest.java
│   └── RcaResponse.java
├── application/usecase/
│   ├── CreateRcaUseCase.java
│   ├── UpdateRcaUseCase.java
│   └── GetRcaByNcUseCase.java
├── infrastructure/
│   └── RootCauseAnalysisRepository.java
└── presentation/
    └── QmsController.java  (existente — adicionar endpoints de RCA)
```

---

### Decisão 4 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/qms/non-conformances/{ncId}/rca | SUPERVISOR+ | criar RCA |
| GET | /api/v1/qms/non-conformances/{ncId}/rca | OPERATOR+ | obter RCA da NC |
| PUT | /api/v1/qms/non-conformances/{ncId}/rca | SUPERVISOR+ | atualizar RCA |

`GET /api/v1/qms/non-conformances/{id}` (detalhe de NC — US-023) passa a incluir o campo `rca` nullable no response.

---

### Decisão 5 — Frontend: wizard interativo de 5-Porquês

Componente de RCA na página de detalhe da NC (abaixo das ações corretivas):
- Se NC não tem RCA: botão "Iniciar Análise de Causa Raiz" (SUPERVISOR+)
- Wizard vertical: cada "Por quê / Resposta" é um step; botão "+ Adicionar próximo Porquê" habilita o par seguinte (máximo 5)
- Campo "Causa Raiz Identificada" aparece após preencher pelo menos `why1 + answer1`
- Botão "Salvar RCA" gera POST ou PUT dependendo de existência prévia
- Para OPERATOR: visualização somente leitura dos pares preenchidos

---

### Consequências
✅ Colunas fixas (why1–why5) — sem JOIN, sem complexidade de ordenação de coleção
✅ RCA somente leitura quando NC fechada — garante imutabilidade do histórico de análise
⚠️ Modelo não suporta mais de 5 porquês — constraint intencional e alinhada à metodologia
⚠️ `@OneToOne unique` em `nc_id` — migration deve adicionar `UNIQUE CONSTRAINT` na coluna
