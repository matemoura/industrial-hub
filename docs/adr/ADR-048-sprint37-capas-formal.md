## ADR-048: Sprint 37 — CAPAS Formal (Corrective and Preventive Actions)

**Status**: Aprovado
**Data**: 2026-06-01
**US relacionadas**: US-112

---

### Contexto

ISO 13485 §8.5.2 (ação corretiva) e §8.5.3 (ação preventiva) exigem:
- Tipo explícito: corretiva (reativa a um problema ocorrido) vs preventiva (proativa para evitar ocorrência)
- Verificação formal de eficácia antes do fechamento — a ação foi realmente efetiva?
- Rastreabilidade da causa raiz confirmada e das medidas implementadas

A entidade `CorrectiveAction` (ADR-007) tem apenas `PENDING` e `DONE` em `ActionStatus` — sem intermediário de verificação de eficácia, sem tipo, sem campos de causa raiz confirmada. Essa lacuna impede conformidade com auditorias ISO 13485.

Esta ADR estende a entidade existente de forma **backward-compatible** sem reescrever o módulo QMS.

---

### Decisão 1 — Extensão backward-compatible: novos campos nullable via ALTER TABLE

Todos os novos campos são `nullable` — registros existentes continuam válidos sem migração de dados. Nenhuma coluna `NOT NULL` sem valor default.

```sql
-- V{N}__corrective_action_capa_extension.sql
ALTER TABLE corrective_action
    ADD COLUMN type               VARCHAR(20) DEFAULT 'CORRECTIVE',
    ADD COLUMN root_cause_confirmed TEXT,
    ADD COLUMN preventive_measure   TEXT,
    ADD COLUMN effectiveness_check_date DATE,
    ADD COLUMN effectiveness_checked_by VARCHAR(255),
    ADD COLUMN effectiveness_result     TEXT;
```

```java
// CorrectiveAction.java — novos campos
@Enumerated(EnumType.STRING)
@Column(length = 20, columnDefinition = "varchar(20) default 'CORRECTIVE'")
private ActionType type = ActionType.CORRECTIVE;

@Column(columnDefinition = "text")
private String rootCauseConfirmed;

@Column(columnDefinition = "text")
private String preventiveMeasure;

private LocalDate effectivenessCheckDate;
private String effectivenessCheckedBy;

@Column(columnDefinition = "text")
private String effectivenessResult;
```

Novo enum `ActionType` no package `qms/domain/`:
```java
public enum ActionType {
    CORRECTIVE,   // ISO 13485 §8.5.2 — reativa a problema ocorrido
    PREVENTIVE    // ISO 13485 §8.5.3 — proativa, evita recorrência
}
```

Alternativa descartada: criar entidade `CAPA` separada e migrar dados — risco de regressão nos endpoints existentes de NC; extensão nullable é cirúrgica e segura.

---

### Decisão 2 — Novo status `PENDING_EFFECTIVENESS` no enum `ActionStatus`

```java
public enum ActionStatus {
    PENDING,                  // ação criada, responsável trabalhando
    PENDING_EFFECTIVENESS,    // implementada, aguardando verificação de eficácia
    DONE                      // eficácia confirmada, ação encerrada
}
```

Máquina de estados:

```
PENDING ──────────────────▶ PENDING_EFFECTIVENESS ──────────────▶ DONE
         (submit-for-eff.)   [rootCauseConfirmed obrigatório]        (verify-effectiveness)
                                                                     [effectivenessResult obrigatório]
```

Regras:
- `PENDING → PENDING_EFFECTIVENESS`: obrigatório ter `rootCauseConfirmed` preenchido
- `PENDING_EFFECTIVENESS → DONE`: obrigatório ter `effectivenessResult` preenchido; registra `effectivenessCheckedBy`
- `PENDING → DONE` diretamente: **proibido** — todo CAPA exige verificação de eficácia
- Ações sem tipo definido (`null`) são tratadas como `CORRECTIVE` para fins de validação

```java
// SubmitForEffectivenessUseCase.java
public ActionResponse submit(UUID ncId, UUID actionId, String principal) {
    CorrectiveAction action = findAndValidate(ncId, actionId);

    if (action.getStatus() != ActionStatus.PENDING)
        throw new IllegalStateException("Apenas ações PENDING podem ser enviadas para verificação");

    if (action.getRootCauseConfirmed() == null || action.getRootCauseConfirmed().isBlank())
        throw new IllegalArgumentException("Causa raiz confirmada é obrigatória antes da verificação de eficácia");

    action.setStatus(ActionStatus.PENDING_EFFECTIVENESS);
    return ActionResponse.from(correctiveActionRepository.save(action));
}
```

Alternativa descartada: manter dois fluxos (com/sem verificação de eficácia baseado no tipo) — acoplamento desnecessário; ISO 13485 exige verificação para ambos os tipos.

---

### Decisão 3 — Fechamento automático da NC quando todas as ações estão DONE

Quando `VerifyEffectivenessUseCase` transiciona a última ação de `PENDING_EFFECTIVENESS → DONE`, verifica automaticamente se todas as ações da NC estão `DONE`. Se sim, transiciona o `NcStatus` para `CLOSED`.

```java
// VerifyEffectivenessUseCase.java
@Transactional
public ActionResponse verify(UUID ncId, UUID actionId, VerifyEffectivenessRequest req, String principal) {
    CorrectiveAction action = findAndValidate(ncId, actionId);

    if (action.getStatus() != ActionStatus.PENDING_EFFECTIVENESS)
        throw new IllegalStateException("Apenas ações PENDING_EFFECTIVENESS podem ser verificadas");

    action.setStatus(ActionStatus.DONE);
    action.setEffectivenessResult(req.effectivenessResult());
    action.setEffectivenessCheckedBy(req.effectivenessCheckedBy());
    correctiveActionRepository.save(action);

    // Auto-close NC se todas as ações estão DONE
    boolean allDone = correctiveActionRepository
        .findByNonConformanceId(ncId)
        .stream()
        .allMatch(a -> a.getStatus() == ActionStatus.DONE);

    if (allDone) {
        NonConformance nc = nonConformanceRepository.findById(ncId).orElseThrow();
        if (nc.getStatus() == NcStatus.IN_ANALYSIS || nc.getStatus() == NcStatus.CORRECTIVE_ACTION) {
            nc.setStatus(NcStatus.CLOSED);
            nc.setClosedAt(LocalDateTime.now());
            nc.setClosedBy(principal);
            nonConformanceRepository.save(nc);
        }
    }

    return ActionResponse.from(action);
}
```

**Pré-condição do auto-close**: NC deve estar em `IN_ANALYSIS` ou `CORRECTIVE_ACTION` — não fecha NCs já `CLOSED` ou `CANCELLED`.

Alternativa descartada: fechamento manual da NC independente das ações — permite inconsistência (NC fechada com ações abertas); o automático garante rastreabilidade.

---

### Decisão 4 — Endpoint consolidado `GET /api/v1/qms/capas` (visão cross-NC)

Supervisores precisam de uma visão centralizada de todas as CAPAs abertas — independentemente de qual NC originou cada ação. Um único endpoint com filtros cobre esse caso sem criar entidade nova.

```java
// CAPASummaryResponse (projeção — sem carregar entidade completa)
public interface CAPASummaryProjection {
    UUID getActionId();
    String getNcCode();
    String getNcTitle();
    String getDescription();
    String getType();
    String getStatus();
    String getResponsible();
    LocalDate getDueDate();
    LocalDate getEffectivenessCheckDate();
}
```

```java
// CorrectiveActionRepository.java
@Query("""
    SELECT
        a.id             AS actionId,
        nc.code          AS ncCode,
        nc.title         AS ncTitle,
        a.description    AS description,
        a.type           AS type,
        a.status         AS status,
        a.responsible    AS responsible,
        a.dueDate        AS dueDate,
        a.effectivenessCheckDate AS effectivenessCheckDate
    FROM CorrectiveAction a
    JOIN a.nonConformance nc
    WHERE (:type IS NULL OR a.type = :type)
      AND (:status IS NULL OR a.status = :status)
      AND (:ncId IS NULL OR nc.id = :ncId)
    ORDER BY a.dueDate ASC NULLS LAST
""")
Page<CAPASummaryProjection> findAllCapas(
    @Param("type") ActionType type,
    @Param("status") ActionStatus status,
    @Param("ncId") UUID ncId,
    Pageable pageable);
```

Alternativa descartada: endpoint no `QmsController` existente sobrescrito — separar em `CapaController` mantém SRP e evita crescimento do controller de NC.

---

### Contrato de API

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| PUT | `/api/v1/qms/non-conformances/{ncId}/corrective-actions/{actionId}` | SUPERVISOR+ | 200 | Atualiza type, rootCauseConfirmed, preventiveMeasure, effectivenessCheckDate |
| POST | `/api/v1/qms/non-conformances/{ncId}/corrective-actions/{actionId}/submit-for-effectiveness` | SUPERVISOR+ | 200 | PENDING → PENDING_EFFECTIVENESS |
| POST | `/api/v1/qms/non-conformances/{ncId}/corrective-actions/{actionId}/verify-effectiveness` | SUPERVISOR+ | 200 | PENDING_EFFECTIVENESS → DONE + auto-close NC |
| GET | `/api/v1/qms/capas` | SUPERVISOR+ | 200 | Lista paginada cross-NC (`?type=&status=&ncId=`) |

**CAPAUpdateRequest**:
```json
{
  "type": "PREVENTIVE",
  "rootCauseConfirmed": "Falha no processo de higienização por falta de treinamento.",
  "preventiveMeasure": "Inclusão no calendário de treinamentos semestral.",
  "effectivenessCheckDate": "2026-09-01"
}
```

**VerifyEffectivenessRequest**:
```json
{
  "effectivenessResult": "Reincidência zero nos 90 dias subsequentes. Treinamento confirmado em 100% dos turnos.",
  "effectivenessCheckedBy": "supervisora.maria"
}
```

**ActionResponse** — campos adicionados:
```json
{
  "id": "uuid",
  "description": "...",
  "responsible": "...",
  "dueDate": "2026-08-01",
  "status": "PENDING_EFFECTIVENESS",
  "type": "CORRECTIVE",
  "rootCauseConfirmed": "...",
  "preventiveMeasure": null,
  "effectivenessCheckDate": "2026-09-01",
  "effectivenessCheckedBy": null,
  "effectivenessResult": null
}
```

---

### Consequências

✅ Extensão backward-compatible — nenhum dado existente quebra; migration segura com ALTER TABLE + nullable
✅ `PENDING_EFFECTIVENESS` garante que verificação de eficácia é etapa obrigatória, não opcional
✅ Auto-close da NC elimina inconsistência (NC aberta com todas as ações DONE)
✅ `GET /api/v1/qms/capas` com projeção JPQL — sem N+1; uma única query com JOIN
✅ `ActionType` (CORRECTIVE/PREVENTIVE) mapeia diretamente aos artigos §8.5.2 e §8.5.3 da ISO 13485
✅ Rota `/qms/capas` no frontend dá visibilidade cross-NC aos supervisores sem navegar NC por NC

⚠️ `PENDING → DONE` direto agora é inválido — código legado que completava ações diretamente (`CompleteCorrectiveActionUseCase`) precisa ser atualizado para respeitar a nova máquina de estados; verificar se há testes quebrando
⚠️ Auto-close da NC é irreversível — supervisor que marcou eficácia por engano não pode desfazer sem intervenção ADMIN; considerar botão "Reabrir NC" em sprint futura
⚠️ `effectivenessCheckDate` é data planejada (não de execução) — não há validação de que a verificação ocorreu após essa data; auditores podem questionar; monitorar uso real
⚠️ `GET /api/v1/qms/capas` retorna ações de todas as plantas quando multi-plant está ativo (ADR-020); filtro por planta pode ser necessário em Sprint futura se volume crescer
