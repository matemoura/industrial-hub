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

---

### Decisão 7 — Algoritmo de resolução overnight em `ShiftResolverService`

O campo `overnight = true` indica que o turno cruza meia-noite (ex: 22h–06h, onde `endTime < startTime`). `resolveCurrentShift()` precisa de lógica diferente para turnos normais e overnight:

```java
@Service
public class ShiftResolverService {

    private final ShiftRepository shiftRepository;

    public ShiftResolverService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public Optional<Shift> resolveCurrentShift() {
        LocalTime now = LocalTime.now();
        return shiftRepository.findAllByActiveTrue().stream()
            .filter(shift -> containsTime(shift, now))
            .findFirst();
    }

    /**
     * Verifica se um dado LocalTime está dentro do intervalo do turno.
     * Caso overnight (endTime < startTime): o intervalo cobre dois segmentos —
     *   [startTime, 23:59:59] e [00:00, endTime).
     * Caso normal (endTime >= startTime): intervalo contíguo [startTime, endTime).
     */
    static boolean containsTime(Shift shift, LocalTime time) {
        LocalTime start = shift.getStartTime();
        LocalTime end   = shift.getEndTime();
        if (shift.isOvernight()) {
            // overnight: cobre o trecho após startTime OU antes de endTime
            return !time.isBefore(start) || time.isBefore(end);
        } else {
            return !time.isBefore(start) && time.isBefore(end);
        }
    }
}
```

**Nota sobre `findFirst()`**: se dois turnos ativos cobrirem o mesmo horário (sobreposição), o primeiro retornado pelo banco é usado. A validação de sobreposição na criação/update (Decisão 8) deve eliminar esse cenário em produção.

**Testabilidade**: `containsTime` é `static` — pode ser testado em `ShiftResolverServiceTest` sem banco, cobrindo os quatro casos: normal-dentro, normal-fora, overnight-após-meia-noite, overnight-antes-meia-noite.

---

### Decisão 8 — Algoritmo de verificação de sobreposição de turnos

Usada em `CreateShiftUseCase` e `UpdateShiftUseCase` antes de persistir. Retorna `422` se o novo turno sobrepõe qualquer turno ativo existente (excluindo o próprio turno no caso de update).

```java
/**
 * Dois turnos se sobrepõem se existe ao menos um instante coberto por ambos.
 * Considera os quatro combinações de overnight entre o turno novo e o existente.
 */
static boolean overlaps(Shift a, Shift b) {
    // Converte cada turno em um conjunto de dois intervalos half-open [start, end)
    // no espaço de 0..1440 minutos desde meia-noite, desdobrando overnight em dois.
    return intervalsOverlap(a.getStartTime(), a.getEndTime(), a.isOvernight(),
                            b.getStartTime(), b.getEndTime(), b.isOvernight());
}

private static boolean intervalsOverlap(
        LocalTime aStart, LocalTime aEnd, boolean aOvernight,
        LocalTime bStart, LocalTime bEnd, boolean bOvernight) {

    // Normaliza para List<int[2]> de pares [startMinute, endMinute) no espaço 0..2879
    // (0..1439 = hoje, 1440..2879 = amanhã) para lidar com overnight sem aritmética modular
    List<int[]> aRanges = toRanges(aStart, aEnd, aOvernight);
    List<int[]> bRanges = toRanges(bStart, bEnd, bOvernight);

    for (int[] ar : aRanges) {
        for (int[] br : bRanges) {
            // Dois intervalos [as, ae) e [bs, be) se sobrepõem se as < be && bs < ae
            if (ar[0] < br[1] && br[0] < ar[1]) return true;
        }
    }
    return false;
}

private static List<int[]> toRanges(LocalTime start, LocalTime end, boolean overnight) {
    int s = start.toSecondOfDay() / 60;
    int e = end.toSecondOfDay() / 60;
    if (overnight) {
        // [s, 1440) + [1440, 1440+e)
        return List.of(new int[]{s, 1440}, new int[]{1440, 1440 + e});
    } else {
        return List.of(new int[]{s, e});
    }
}
```

Implementação em `ShiftOverlapChecker` (classe utilitária estática, sem Spring, em `common/application/usecase/`) — testável sem banco.

**No use case de criação:**
```java
List<Shift> actives = shiftRepository.findAllByActiveTrue();
actives.stream()
    .filter(existing -> ShiftOverlapChecker.overlaps(newShift, existing))
    .findFirst()
    .ifPresent(conflict -> {
        throw new ShiftOverlapException(conflict.getName());
    });
```

**No use case de update:** excluir o próprio turno da lista antes de verificar.

---

### Decisão 9 — `ShiftResolverService` em `ImportDynamicsExcelUseCase`

O mesmo `ShiftResolverService.resolveCurrentShift()` da Decisão 7 é injetado em `ImportDynamicsExcelUseCase`. No momento em que o `ImportBatch` é criado (durante o processamento do upload), `LocalTime.now()` captura o horário do servidor — esse instante determina o turno do batch.

```java
// ImportDynamicsExcelUseCase.java — trecho relevante:
ImportBatch batch = new ImportBatch();
// ... preenche demais campos ...
shiftResolverService.resolveCurrentShift().ifPresent(batch::setShift);
importBatchRepository.save(batch);
```

**Consequência**: se o upload acontecer às 05:59 e o processamento terminar às 06:01, o turno registrado pode ser o noturno (22h–06h), não o matutino (06h–14h) — porque `LocalTime.now()` é capturado **no início do processamento** (criação do batch), não ao final. Isso é aceitável e deve ser documentado no Javadoc do método `execute()`.

---

### Decisão 10 — JPQL para filtro opcional `shiftId` em `work-orders`

O filtro `?shiftId=<uuid>` em `GET /api/v1/maintenance/work-orders` é opcional. A abordagem com JPQL condicional via Spring Data evita dois métodos separados:

```java
// WorkOrderRepository.java
@Query("""
    SELECT w FROM WorkOrder w
    WHERE (:shiftId IS NULL OR w.shift.id = :shiftId)
      AND (:status IS NULL OR w.status = :status)
      AND (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
    ORDER BY w.openedAt DESC
    """)
Page<WorkOrder> findWithFilters(
    @Param("shiftId") UUID shiftId,
    @Param("status") WorkOrderStatus status,
    @Param("equipmentId") UUID equipmentId,
    Pageable pageable);
```

**Nota sobre `(:shiftId IS NULL OR w.shift.id = :shiftId)`**: quando `shift` é `null` na entidade (OS criada antes da feature de turnos), o filtro `w.shift.id = :shiftId` retorna `false` para qualquer `shiftId` fornecido — comportamento correto. Quando `shiftId = null` (filtro não aplicado), a condição `IS NULL` passa e toda a tabela é considerada.

**Atenção com JPQL e parâmetros UUID nulos**: testar que H2 e PostgreSQL tratam `:shiftId IS NULL` identicamente quando o parâmetro Java é `null`. Em H2 há quirk histórico; se necessário, usar `@Query` com `nativeQuery = true` apenas para este método.

---

### Consequências
✅ `ShiftResolverService.resolveCurrentShift()` isolado e testável sem banco
✅ `containsTime` e `ShiftOverlapChecker.overlaps` como métodos estáticos — 100% testáveis sem banco, cobrindo todos os casos overnight
✅ `shift = null` graceful — turnos são opcionais; sistema funciona sem configuração prévia
✅ JPQL com filtro condicional `IS NULL OR` mantém um único método de repositório para todas as combinações de filtro
⚠️ Sobreposição de turnos overnight requer algoritmo não-trivial (`toRanges`); encapsular em `ShiftOverlapChecker` com testes unitários exaustivos antes de integrar
⚠️ Migration: colunas `shift_id` nullable em `work_order` e `import_batch`
⚠️ `LocalTime.now()` no `ImportDynamicsExcelUseCase` captura o horário de início do processamento — documentar no Javadoc que batches de upload de fronteira de turno podem ser associados ao turno anterior
⚠️ Quirk do H2 com parâmetros UUID nulos em JPQL — validar no `WorkOrderRepositoryTest` com H2 antes de assumir paridade com PostgreSQL
