## ADR-039: Sprint 24 — Decisões de Implementação (OEE Benchmarking + Tech Debt SEC-078→081)
**Status**: Aprovado
**Data**: 2026-05-22
**US relacionadas**: US-075, US-076 + tech debt SEC-078, SEC-079, SEC-080, SEC-081

---

### Contexto

O Sprint 24 tem dois eixos:

1. **Novas funcionalidades**: OEE Benchmarking (US-075, US-076) — comparativo de performance entre trabalhadores, turnos e tipos de equipamento, conforme previsto no ADR-026. Os endpoints utilizam os dados já persistidos em `ImportBatch` e `TimeRecord`, sem criação de novas entidades.

2. **Tech debt de segurança diferidos do Sprint 23** por Beatriz: SEC-078 (`AssignUserPlantsRequest.plantIds` sem `@Size(max=100)`), SEC-079 (`EmailEscalationService.fromAddress` expõe `spring.mail.username`), SEC-080 (`plantHeaderInterceptor` injeta `X-Plant-Id` na request de login), SEC-081 (`AssignUserPlantsUseCase` aceita plantas inativas). Todos classificados como LOW pela Beatriz no log Sprint 23 — sem bloqueantes; implementados em paralelo com US-075/076.

---

### Decisão 1 — Tech Debt SEC-078: `@Size(max=100)` em `AssignUserPlantsRequest.plantIds`

**Problema**: `AssignUserPlantsRequest.plantIds` tem `@NotNull List<UUID>` sem bound de tamanho. Um ADMIN pode enviar uma lista com N UUIDs, disparando N SELECT (`plantRepository.findByIdAndActiveTrue`) + N INSERT em `user_plant` em uma única transação — sem limite, a transação cresce indefinidamente e pode causar degradação ou timeout de DB.

**Decisão**: adicionar `@Size(max = 100)` no campo `plantIds` do DTO e garantir que o controller tenha `@Validated` (necessário para que `ConstraintViolationException` seja lançada em parâmetros de nível de classe — padrão estabelecido em ADR-031).

```java
// AssignUserPlantsRequest.java — alterar campo
public record AssignUserPlantsRequest(
    @NotNull
    @Size(max = 100, message = "plantIds não pode conter mais de 100 plantas")
    List<UUID> plantIds
) {}
```

```java
// PlantController.java — garantir @Validated na classe
@RestController
@RequestMapping("/api/v1/admin/plants")
@PreAuthorize("hasRole('ADMIN')")
@Validated  // obrigatório para @Size em parâmetros de nível de classe
public class PlantController { ... }
```

**Resposta de erro**: `GlobalExceptionHandler.handleConstraintViolation()` já trata `ConstraintViolationException` e retorna `400` com `{ "message": "plantIds: plantIds não pode conter mais de 100 plantas" }` — sem handler adicional necessário.

**Por que 100?** Limite operacional conservador: o MSB tem ~53 usuários internos e dificilmente haverá mais de 100 plantas num cenário realista. O número é explicitamente documentado na mensagem de erro para comunicação clara com o cliente.

---

### Decisão 2 — Tech Debt SEC-079: propriedade dedicada `app.mail.from` para endereço `From`

**Problema**: `EmailEscalationService.fromAddress` usa `@Value("${spring.mail.username:noreply@industrialhub.com}")` como endereço `From`, expondo o username da conta SMTP nos headers de email enviados a SUPERVISOR+. Se a conta SMTP tiver um username que revela a infraestrutura interna (ex: `smtp-relay@msb-internal.com`), esse endereço fica visível para todos os destinatários.

**Decisão**: introduzir propriedade dedicada `app.mail.from` separada de `spring.mail.username`. O `from` é o endereço amigável para os destinatários; o `username` é a credencial SMTP da conta de envio — são preocupações distintas.

```java
// EmailEscalationService.java — alterar @Value
@Value("${app.mail.from:noreply@industrialhub.com}")
private String fromAddress;
```

```properties
# application.properties — adicionar propriedade
app.mail.from=noreply@msb.com.br
```

**Mesmo padrão para `QmsEmailService`**: verificar se `QmsEmailService` também usa `spring.mail.username` como `From` e aplicar a mesma mudança para consistência. A propriedade `app.mail.from` é centralizada e usada por ambos os serviços de email.

**Compatibilidade**: o valor default `:noreply@industrialhub.com` garante que ambientes sem a propriedade configurada continuem funcionando sem `NullPointerException` ou `IllegalStateException` na inicialização.

---

### Decisão 3 — Tech Debt SEC-080: excluir `/auth/` do `plantHeaderInterceptor` Angular

**Problema**: o `plantHeaderInterceptor` Angular injeta o header `X-Plant-Id` em todas as requisições com URL contendo `/api/` — incluindo `POST /api/v1/auth/login`. Se um `plant_id` estiver salvo no `localStorage` de uma sessão anterior, ele é enviado junto com as credenciais de login. O backend ignora o header (o `PlantContextFilter` só processa requests autenticadas), mas o `plant_id` da sessão anterior é exposto desnecessariamente ao backend na request de autenticação.

**Decisão**: adicionar `/api/v1/auth/` à condição de exclusão do interceptor. A condição atual `req.url.includes('/api/')` é expandida com uma negação explícita do path de autenticação.

```typescript
// shared/plant/plant-header.interceptor.ts — alterar condição
export const plantHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  const plantService = inject(PlantService);
  const selectedPlantId = plantService.selectedPlantId();

  const isApiRequest = req.url.includes('/api/');
  const isAuthRequest = req.url.includes('/api/v1/auth/');  // excluir auth

  if (isApiRequest && !isAuthRequest && selectedPlantId) {
    const modified = req.clone({
      headers: req.headers.set('X-Plant-Id', selectedPlantId)
    });
    return next(modified);
  }

  return next(req);
};
```

**Por que excluir `/auth/` especificamente?** Os endpoints sob `/auth/` não processam `X-Plant-Id` e não devem receber dados de sessão de outra sessão. Padrão de exclusão explícita por prefixo de path é mais seguro que heurísticas genéricas.

**Teste unitário**: `plant-header.interceptor.spec.ts` deve incluir: (a) request para `/api/v1/auth/login` com `selectedPlantId` não-null → header `X-Plant-Id` **não** enviado; (b) request para `/api/v1/maintenance/equipment` com `selectedPlantId` → header enviado; (c) request para `/api/v1/auth/login` sem `selectedPlantId` → sem header (comportamento já existente).

---

### Decisão 4 — Tech Debt SEC-081: rejeitar plantas inativas em `AssignUserPlantsUseCase`

**Problema**: `AssignUserPlantsUseCase` usa `plantRepository.findById(plantId)` que retorna plantas inativas. Um ADMIN pode associar um usuário a uma planta com `active = false`, resultando em `PlantContext.current()` retornando IDs de plantas inativas — o usuário verá listas vazias silenciosas sem indicação de por quê (plants inativas não retornam dados).

**Decisão**: substituir `plantRepository.findById(plantId)` por `plantRepository.findByIdAndActiveTrue(plantId)` no `AssignUserPlantsUseCase`. Planta inativa resulta em `400` com mensagem explícita.

```java
// AssignUserPlantsUseCase.java — alterar lookup de planta
for (UUID plantId : request.plantIds()) {
    Plant plant = plantRepository.findByIdAndActiveTrue(plantId)
        .orElseThrow(() -> new PlantNotFoundException(
            "Planta não encontrada ou inativa: " + plantId));
    // ...
}
```

```java
// PlantRepository.java — método já existente (confirmar) ou adicionar
Optional<Plant> findByIdAndActiveTrue(UUID id);
```

**Resposta de erro**: `PlantNotFoundException` já mapeada para `404` em `GlobalExceptionHandler`. A mensagem "Planta não encontrada ou inativa" informa o ADMIN sem revelar detalhes de infraestrutura.

**Alternativa considerada e rejeitada**: verificar `plant.isActive()` após `findById()` com lançamento de exceção separada. Preferiu-se `findByIdAndActiveTrue()` por consistência com o padrão já usado em `GetEquipmentListUseCase` (ex: `findByIdAndActiveTrue` em equipment) e por ser uma query derivada Spring Data — sem query JPQL manual.

---

### Decisão 5 — OEE Benchmarking: use cases e package (US-075)

O ADR-026 define os endpoints e o formato de resposta. Este ADR detalha as decisões de implementação.

**Package**:
```
oee/
└── application/
    └── usecase/
        ├── GetWorkerBenchmarkUseCase.java
        ├── GetShiftBenchmarkUseCase.java
        ├── GetEquipmentTypeBenchmarkUseCase.java
        └── GetPeriodComparisonUseCase.java
```

Os endpoints são adicionados ao `AnalyticsController` existente em `common/presentation/` (mesmo padrão de US-043, US-044, US-045 — ADR-012 Decisão 2). **Não** é criado um `BenchmarkController` separado: a concentração de endpoints de analytics em um único controller reduz a superfície de configuração de segurança.

**Fonte de dados**: todos os use cases consultam `TimeRecordRepository` (Sprint 1–3) filtrando por `profileDate` entre `from` e `to`. Agrupamento em Java via `Collectors.groupingBy` — sem SQL nativo, alinhado com ADR-012 Decisão 1.

**Limite de 90 dias** (US-075 AC#5): validado no use case antes de qualquer query.

```java
// Validação comum nos 4 use cases
private void validatePeriod(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
        throw new IllegalArgumentException("Período inválido: 'from' deve ser anterior a 'to'");
    }
    if (ChronoUnit.DAYS.between(from, to) > 90) {
        throw new IllegalArgumentException("Período máximo de benchmarking é 90 dias");
    }
}
```

`IllegalArgumentException` → 400 via `GlobalExceptionHandler.handleIllegalArgument()` (handler adicionado em ADR-031 / Sprint 16).

**Cálculo de `stdDev`**: implementado em Java puro — sem biblioteca estatística externa.

```java
// Exemplo em GetWorkerBenchmarkUseCase.java
private Double calculateStdDev(List<Double> values) {
    if (values.size() < 3) return null;  // AC#6: null quando sampleCount < 3
    double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    double variance = values.stream()
        .mapToDouble(v -> Math.pow(v - mean, 2))
        .average()
        .orElse(0.0);
    return Math.sqrt(variance);
}
```

---

### Decisão 6 — OEE Benchmarking: benchmark por turno (dependência Sprint 17)

O endpoint `/benchmark/shifts` depende do campo `shiftId` em `ImportBatch`, introduzido no Sprint 17 (ADR-016). Registros anteriores ao Sprint 17 têm `shiftId = null`.

**Decisão**: o use case `GetShiftBenchmarkUseCase` filtra registros com `shiftId != null`. Registros sem turno são excluídos silenciosamente do ranking. A resposta inclui campo auxiliar `recordsWithoutShift: Long` para informar o frontend quantos registros foram excluídos.

```java
public record BenchmarkResponse(
    List<BenchmarkEntry> ranking,
    BenchmarkEntry best,
    BenchmarkEntry worst,
    Double overallAvg,
    Long recordsWithoutShift  // nullable; presente apenas no endpoint /shifts
) {}
```

**Frontend**: quando `recordsWithoutShift > 0`, exibir aviso inline abaixo do chart: *"N registro(s) sem turno associado foram excluídos do ranking."*

---

### Decisão 7 — OEE Benchmarking: frontend (US-076)

Nova aba "Benchmark" adicionada à rota `/analytics/oee` existente — sem nova rota. Consistente com o padrão de abas já utilizado em `/analytics/oee` (aba "Trend" + aba "Benchmark").

**Filtro de período**: reutiliza o mesmo `MatDatepicker` já presente na aba Trend; estado do período é compartilhado entre abas via signal no componente pai `OeeAnalyticsComponent`.

**Cores dos bar charts** (US-076 AC#2): classificação por threshold de OEE definida no ADR-026:
- `oeeAvg >= 0.65` → verde (`#4CAF50`)
- `0.50 <= oeeAvg < 0.65` → amarelo (`#FF9800`)
- `oeeAvg < 0.50` → vermelho (`#F44336`)

**Toggle "OEE Classe Mundial (85%)"** (US-076 AC#6): implementado como `signal<boolean>` no componente; quando ativo, todos os charts recebem `referenceValue = 0.85` via `input()` para `LineChartComponent` e `BarChartComponent` já existentes em `shared/charts/` (Sprint 16 — ADR-012 Decisão 4). **Não** é criada nova lógica de linha de referência — reutiliza o `referenceValue?: InputSignal<number>` já existente no `LineChartComponent`.

**Componente de comparação de período**: seção "Comparação de Período" usa `LineChartComponent` duas vezes com datasets distintos. Sem criação de novo chart component — `LineChartComponent` aceita múltiplos `datasets` via input existente.

---

### Contrato de API

#### Endpoints novos — OEE Benchmarking

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| GET | /api/v1/analytics/oee/benchmark/workers | SUPERVISOR+ | 200 `BenchmarkResponse` | Ranking de trabalhadores por OEE médio |
| GET | /api/v1/analytics/oee/benchmark/shifts | SUPERVISOR+ | 200 `BenchmarkResponse` | Ranking de turnos por OEE médio |
| GET | /api/v1/analytics/oee/benchmark/equipment-type | SUPERVISOR+ | 200 `BenchmarkResponse` | Ranking por tipo de equipamento |
| GET | /api/v1/analytics/oee/benchmark/period-comparison | SUPERVISOR+ | 200 `PeriodComparisonResponse` | Comparação período atual vs anterior |

Todos suportam `?from=<date>&to=<date>` (obrigatórios) e `?plantId=<uuid>` (opcional — ADR-020).

**Parâmetros**:
- `from`, `to`: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` — padrão do projeto
- Período máximo: 90 dias (validado no use case — `IllegalArgumentException` → 400)
- `plantId`: UUID opcional; SUPERVISOR/OPERATOR filtrado pelo `PlantContext` (ADR-038 Decisão 5); ADMIN pode passar explicitamente

#### Sem migrations de banco

Sprint 24 não introduz novas entidades nem altera schema. Todos os dados necessários para benchmarking já existem em `time_record` e `import_batch`.

---

### Consequências

✅ SEC-078: `@Size(max=100)` em `plantIds` + `@Validated` no controller previne transações excessivas; limite comunicado explicitamente na mensagem de erro
✅ SEC-079: `app.mail.from` desacopla identidade do remetente das credenciais SMTP; ambos `EmailEscalationService` e `QmsEmailService` usam a mesma propriedade — consistência
✅ SEC-080: exclusão de `/api/v1/auth/` do `plantHeaderInterceptor` elimina vazamento desnecessário de `plant_id` de sessões anteriores na request de login
✅ SEC-081: `findByIdAndActiveTrue` rejeita plantas inativas explicitamente com `400` — usuário nunca fica com `PlantContext` contendo IDs de plantas inativas; sem listas vazias silenciosas
✅ Benchmarking sem entidades novas — zero migrations; dados existentes em `time_record` e `import_batch`
✅ `stdDev` calculado em Java puro — sem dependência nova; `null` quando `sampleCount < 3` comunica a limitação estatística claramente
✅ Aba "Benchmark" reutiliza `AnalyticsController`, `LineChartComponent` e `BarChartComponent` existentes — zero componentes novos de chart necessários
⚠️ Benchmark por turno retorna ranking incompleto para dados anteriores ao Sprint 17 — campo `recordsWithoutShift` na response informa o frontend; sem dados "fantasma"
⚠️ Cálculo de `stdDev` em Java sobre listas longas: para período de 90 dias com muitos trabalhadores, a lista de doubles pode ser grande; limite de 90 dias mitiga o risco de OOM; monitorar com `Actuator /metrics` se volume crescer
⚠️ `app.mail.from` deve ser configurado em todos os ambientes (dev, staging, prod); sem a propriedade, o default `noreply@industrialhub.com` é usado — aceitável mas deve ser documentado no `application.properties` de exemplo
⚠️ Excluir `/auth/` do interceptor via string matching — se novos paths de autenticação forem adicionados sob paths diferentes (ex: `/api/v2/login`), a condição precisará ser atualizada; considerar lista de exclusões configurável se necessário em sprints futuras
