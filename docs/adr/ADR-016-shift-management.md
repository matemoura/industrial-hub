## ADR-016: Shift Management — Turnos e Rastreabilidade por Turno
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-054, US-055, US-056

### Contexto

A planta opera em múltiplos turnos (ex: manhã 06h–14h, tarde 14h–22h, noite 22h–06h). OEE e ordens de serviço gerados em turnos diferentes têm comportamentos distintos. O sistema hoje não distingue por turno: todos os registros de um dia são agregados juntos. Esta ADR adiciona a dimensão de turno para rastreabilidade e análise comparativa.

---

### Decisão 1 — Entidade `Shift`

```java
@Entity
@Table(name = "shift")
public class Shift {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;   // ex: "Manhã", "Tarde", "Noite"

    private LocalTime startTime;  // ex: 06:00
    private LocalTime endTime;    // ex: 14:00

    private boolean overnight;    // true quando endTime < startTime (ex: 22:00–06:00)
    private boolean active = true;
}
```

**Sem entidade de `ShiftAssignment` (grade de escalas)**: fora do escopo. O turno é registrado no momento da criação de uma OS ou importação OEE, baseado no horário atual do servidor.

---

### Decisão 2 — Associação de turno a WorkOrder e ImportBatch

```java
// WorkOrder — campo nullable (retrocompatível)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "shift_id")
private Shift shift;  // preenchido automaticamente na criação com base em LocalTime.now()

// ImportBatch — campo nullable
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "shift_id")
private Shift shift;
```

**Preenchimento automático**: `ShiftResolverService.resolveCurrentShift()` busca o turno ativo cujo intervalo `[startTime, endTime)` contém `LocalTime.now()`. Se nenhum turno ativo cobre o horário atual, `shift = null` (graceful degradation — não bloqueia criação de OS/import).

---

### Decisão 3 — Package

```
common/
├── domain/
│   └── Shift.java
├── application/usecase/
│   ├── CreateShiftUseCase.java
│   ├── GetShiftListUseCase.java
│   ├── UpdateShiftUseCase.java
│   ├── DeactivateShiftUseCase.java
│   └── ShiftResolverService.java     // utilitário shared, não um use case isolado
└── presentation/
    └── ShiftController.java          (/api/v1/admin/shifts)
```

---

### Decisão 4 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/admin/shifts | ADMIN | criar turno |
| GET | /api/v1/admin/shifts | OPERATOR+ | listar turnos ativos |
| PUT | /api/v1/admin/shifts/{id} | ADMIN | atualizar |
| PUT | /api/v1/admin/shifts/{id}/deactivate | ADMIN | desativar |
| GET | /api/v1/maintenance/work-orders?shiftId=<uuid> | OPERATOR+ | filtro por turno (novo param) |

---

### Decisão 5 — Relatório por turno

`GET /api/v1/analytics/maintenance/wo-summary?shiftId=<uuid>` (extensão de US-045) retorna distribuição de OSs filtrada por turno.

`GET /api/v1/analytics/oee/trend?shiftId=<uuid>` retorna OEE apenas dos `ImportBatch` do turno especificado.

Sem endpoint de relatório de turno dedicado — filtros nos analytics existentes são suficientes.

---

### Decisão 6 — Frontend

- Rota `/admin/shifts` (ADMIN): tabela de turnos com horários e status
- Formulário de criação: nome, hora início, hora fim, checkbox "turno noturno (passa meia-noite)"
- Chip de turno exibido em: card de OS (detalhe), listagem de OSs, detalhe de ImportBatch
- Dropdown "Turno" adicionado aos filtros das páginas `/maintenance/work-orders` e `/analytics/oee`

---

### Consequências
✅ `ShiftResolverService.resolveCurrentShift()` isolado e testável sem banco
✅ `shift = null` graceful — turnos são opcionais; sistema funciona sem configuração prévia
⚠️ Sobreposição de turnos (ex: dois turnos cobrindo 14h) retorna o primeiro encontrado — validar no use case de criação: `422` se novo turno sobrepõe turno ativo existente
⚠️ Migration: colunas `shift_id` nullable em `work_order` e `import_batch`
