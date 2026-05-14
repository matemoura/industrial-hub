## ADR-019: SLA & Escalation Rules — Prazos e Escalação Automática
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-061, US-062

### Contexto

NCs críticas não fechadas em 48 horas e ordens de serviço urgentes não iniciadas em 4 horas representam risco operacional. Hoje o sistema não tem mecanismo de prazo (SLA) nem escala automaticamente. Esta ADR define SLAs configuráveis por severity/priority e um job de escalação que notifica e sinaliza itens vencidos.

---

### Decisão 1 — Entidade `SlaRule`

```java
@Entity
@Table(name = "sla_rule")
public class SlaRule {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlaEntityType entityType;  // NC | WORK_ORDER

    @Column(nullable = false, length = 30)
    private String classifierValue;    // "CRITICAL", "HIGH", "URGENT", etc. (valor do enum como string)

    @Column(nullable = false)
    private Integer slaHours;          // SLA em horas a partir de abertura

    private boolean escalateByEmail;
    private boolean active = true;
}

public enum SlaEntityType { NC, WORK_ORDER }
```

**Exemplos de regras default** (seed no `DataInitializer`):
```
NC / CRITICAL       → 48h
NC / HIGH           → 72h
WORK_ORDER / URGENT → 4h
WORK_ORDER / HIGH   → 24h
```

---

### Decisão 2 — Flag `slaBreached` nas entidades existentes

```java
// NonConformance — campo novo
private boolean slaBreached = false;
private LocalDateTime slaBreachedAt;

// WorkOrder — campo novo
private boolean slaBreached = false;
private LocalDateTime slaBreachedAt;
```

Migration: colunas nullable em `non_conformance` e `work_order`.

---

### Decisão 3 — Job de escalação

```java
@Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo") // a cada hora
public void runEscalation() { escalationUseCase.execute(); }
```

`EscalationUseCase.execute()`:
1. Para cada `SlaRule` ativa:
   - Busca NCs/OSs abertas (não `CLOSED`/`DONE`/`CANCELLED`) com `reportedAt`/`openedAt` + `slaHours` < `now()`
   - Se `slaBreached = false`: seta `slaBreached = true`, `slaBreachedAt = now()`
   - Cria `Notification` via `NotificationService` (ADR-013): `severity = CRITICAL`, broadcast para SUPERVISOR+
   - Se `escalateByEmail = true`: envia email para SUPERVISOR+
2. Não renotifica entidades que já têm `slaBreached = true` (idempotente)

---

### Decisão 4 — Package

```
common/
├── domain/
│   ├── SlaRule.java
│   └── SlaEntityType.java
├── application/usecase/
│   ├── CreateSlaRuleUseCase.java
│   ├── GetSlaRuleListUseCase.java
│   ├── UpdateSlaRuleUseCase.java
│   └── EscalationUseCase.java
├── infrastructure/
│   └── SlaRuleRepository.java
└── presentation/
    └── SlaRuleController.java   (/api/v1/admin/sla-rules)
```

---

### Decisão 5 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/admin/sla-rules | ADMIN | listar regras |
| POST | /api/v1/admin/sla-rules | ADMIN | criar regra |
| PUT | /api/v1/admin/sla-rules/{id} | ADMIN | atualizar |
| DELETE | /api/v1/admin/sla-rules/{id} | ADMIN | remover regra |
| POST | /api/v1/admin/sla-rules/run-now | ADMIN | disparar job manualmente |

Filtros nas listagens existentes ganham parâmetro `?slaBreached=true`:
- `GET /api/v1/qms/non-conformances?slaBreached=true` — NCs com SLA vencido
- `GET /api/v1/maintenance/work-orders?slaBreached=true` — OSs com SLA vencido

---

### Decisão 6 — Frontend

- Chip "SLA Vencido" (vermelho, pulsante) nas listagens e detalhes de NCs e OSs com `slaBreached = true`
- Painel "SLA em Risco" no dashboard (US-030): contagem de NCs e OSs com SLA vencido
- Rota `/admin/sla-rules` (ADMIN): tabela de regras com campos editáveis inline (slaHours, escalateByEmail, active)
- Notificações do sino (ADR-013) exibem alertas de SLA com severidade CRITICAL

---

### Consequências
✅ Flag `slaBreached` em banco — permite filtros eficientes sem recalcular em cada query
✅ Job idempotente — rodar duas vezes não cria notificações duplicadas
✅ Integração com `NotificationService` (ADR-013) — reutiliza infraestrutura existente
⚠️ `classifierValue` como string em vez de enum FK — permite suportar qualquer severity/priority sem migration ao adicionar novos valores de enum
⚠️ SLA contado a partir de abertura, não do horário de turno — simplificação aceitável; calendários de turno são out-of-scope
⚠️ Migration: colunas `sla_breached` e `sla_breached_at` em `non_conformance` e `work_order`
