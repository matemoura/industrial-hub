## ADR-025: Planned Downtime — Separação de Paradas Planejadas no OEE
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-073, US-074

### Contexto

O cálculo de OEE atual trata toda parada como não-planejada, distorcendo a disponibilidade em dias com manutenção preventiva programada. A ISO 22400 distingue parada planejada (excluded from OEE denominator) de não-planejada (reduz disponibilidade). Esta ADR adiciona o registro de paradas planejadas e ajusta o cálculo de OEE.

---

### Decisão 1 — Entidade `PlannedDowntime`

```java
@Entity
@Table(name = "planned_downtime", indexes = {
    @Index(name = "idx_pdt_equipment", columnList = "equipment_id"),
    @Index(name = "idx_pdt_date",      columnList = "date")
})
public class PlannedDowntime {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;  // null = parada de planta inteira

    private LocalDate date;
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    private DowntimeReason reason; // PREVENTIVE_MAINTENANCE | SCHEDULED_SETUP | HOLIDAY | OTHER

    private String description;
    private String registeredBy;
    private LocalDateTime registeredAt;
}

public enum DowntimeReason {
    PREVENTIVE_MAINTENANCE,  // OS preventiva programada
    SCHEDULED_SETUP,         // troca de ferramental
    HOLIDAY,                 // feriado/recesso
    OTHER
}
```

---

### Decisão 2 — Impacto no cálculo de OEE

OEE de Disponibilidade atual:
```
Disponibilidade = availableTimeMinutes / totalTimeMinutes
```

Novo cálculo:
```
Disponibilidade = availableTimeMinutes / (totalTimeMinutes - plannedDowntimeMinutes)
```

`plannedDowntimeMinutes` = soma de `PlannedDowntime.durationMinutes` para o equipamento (ou planta inteira) na data do `ImportBatch`.

Implementado em `CalculateOeeUseCase` sem alterar a entidade `ImportBatch` — paradas planejadas são subtraídas em tempo de cálculo, não na importação.

---

### Decisão 3 — Retrocompatibilidade

Toggle `includePlannedDowntime` nos endpoints de OEE:
- `GET /api/v1/oee/dashboard?excludePlannedDowntime=true` (default `false` — comportamento atual preservado)
- Novo modo: `excludePlannedDowntime=true` aplica a subtração

Evita quebrar relatórios históricos. Versão futura pode tornar `excludePlannedDowntime=true` o padrão após validação com usuários.

---

### Decisão 4 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/oee/planned-downtimes | SUPERVISOR+ | registrar parada |
| GET | /api/v1/oee/planned-downtimes | OPERATOR+ | listar (filtros: date, equipmentId) |
| PUT | /api/v1/oee/planned-downtimes/{id} | SUPERVISOR+ | atualizar |
| DELETE | /api/v1/oee/planned-downtimes/{id} | SUPERVISOR+ | remover |

---

### Decisão 5 — Frontend

- Rota `/oee/planned-downtimes`: calendário mensal com dias marcados por equipamento
- Formulário de registro: equipamento (optional — null = planta inteira), data, duração (minutos), razão (select), descrição
- Toggle "Excluir paradas planejadas" no dashboard de OEE — controla `excludePlannedDowntime` query param
- Tooltip nos cards de OEE: "Calculado com/sem paradas planejadas"

---

### Consequências
✅ Retrocompatível — toggle preserva comportamento atual como default
✅ Cálculo em Java (subtração em `CalculateOeeUseCase`) — sem alterar schema de `ImportBatch`
⚠️ `equipment = null` (parada de planta inteira) requer query adicional para somar paradas globais ao denominador de cada equipamento — cuidado com N+1 no loop de equipamentos
