## ADR-035: Sprint 20 — Tech Debt
**Status**: Aprovado
**Data**: 2026-05-21
**US relacionadas**: US-090

### Contexto

Ao final da Sprint 19, Beatriz (Security Engineer) e Helena (Code Reviewer) identificaram cinco itens de débito técnico no módulo de gestão de turnos implementado na Sprint 19 (US-054, US-055, US-056) que não puderam ser endereçados naquele escopo.

**SEC-063** — Race condition no `evaluateNow` do `AlertThresholdController` (implementado no ADR-034 / Sprint 19): a sequência `AtomicReference.get()` + `AtomicReference.set()` não é atômica. Se dois threads chegam simultaneamente no endpoint `POST /evaluate-now`, ambos podem ler `last` antes que qualquer um escreva `now`, passando pelo guard e executando duas avaliações em paralelo. O `AtomicReference` garante visibilidade, mas não exclusividade da seção crítica.

**SEC-064** — `SecurityConfig` não possui URL-level rule para `/api/v1/admin/shifts/**`. O caminho cai em `anyRequest().authenticated()`, que exige apenas autenticação válida. A proteção de escrita (POST, PUT, DELETE) depende exclusivamente do `@PreAuthorize` method-level. Embora funcionalmente correto, a ausência de URL-level rule é inconsistente com outros paths protegidos (`/api/v1/admin/users/**`) e reduz a defesa em profundidade.

**SEC-065** — Os três use cases de turno (`CreateShiftUseCase`, `UpdateShiftUseCase`, `DeactivateShiftUseCase`) não registram entradas no `AuditLog`. Operações de escrita administrativas em turnos — que afetam diretamente o cálculo de OEE — ficam sem rastreabilidade forense.

**SH-52** — `UpdateShiftUseCase` chama `CreateShiftUseCase.overlaps()` de forma cross-use-case: `CreateShiftUseCase.overlaps(existing, candidate)` é chamado diretamente. Os métodos `overlaps()`, `toMinuteRanges()` e `rangesIntersect()` pertencem semanticamente ao `ShiftResolverService` (já existe como `@Service`), não a um use case de criação.

**SH-53** — `ShiftListComponent.showSnackbar` usa `setTimeout(() => this.snackbar.set(null), 4000)`, que não é cancelado quando o componente é destruído. Se o componente for destruído antes dos 4 segundos, o callback ainda executa e pode causar `ExpressionChangedAfterItHasBeenCheckedError` ou atualização de sinal em componente destruído.

---

### Decisão 1 — SEC-063: `compareAndSet` no debounce do `evaluate-now`

**Problema**: `get()` + verificação + `set()` não é atômico. Dois threads podem ler o mesmo `last`, ambos passarem no guard e ambos chamarem `set(now)`.

**Solução**: substituir a sequência `get/if/set` por `compareAndSet`, que é garantidamente atômico pela JVM. Se dois threads chegam simultaneamente, apenas o que executar o CAS primeiro avança; o segundo verá o `last` atualizado e calculará o `secondsRemaining` corretamente.

```java
// AlertThresholdController.java — substituir o método evaluateNow:

@PostMapping("/evaluate-now")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<EvaluateNowResponse> evaluateNow(Principal principal) {
    Instant now = Instant.now();
    Instant last = lastManualEvaluation.get();

    if (Duration.between(last, now).compareTo(EVALUATE_NOW_COOLDOWN) < 0) {
        long secondsRemaining = EVALUATE_NOW_COOLDOWN.toSeconds()
            - Duration.between(last, now).toSeconds();
        throw new EvaluateNowCooldownException(secondsRemaining);
    }

    // CAS garante atomicidade: se outro thread já atualizou last, retentamos o guard
    if (!lastManualEvaluation.compareAndSet(last, now)) {
        // outro thread ganhou a corrida — recalcula secondsRemaining
        Instant updatedLast = lastManualEvaluation.get();
        long secondsRemaining = EVALUATE_NOW_COOLDOWN.toSeconds()
            - Duration.between(updatedLast, now).toSeconds();
        if (secondsRemaining > 0) {
            throw new EvaluateNowCooldownException(secondsRemaining);
        }
    }

    int evaluated = alertEvaluatorUseCase.execute();

    auditService.log(principal.getName(), AuditAction.ALERT_EVALUATED_MANUAL,
        "AlertThreshold", "all", Map.of("evaluated", String.valueOf(evaluated)));

    return ResponseEntity.ok(new EvaluateNowResponse(evaluated));
}
```

**Por que não usar `synchronized`**: `AtomicReference.compareAndSet` é lock-free e suficiente para este caso. `synchronized` adicionaria contenção desnecessária em um endpoint de uso esporádico.

---

### Decisão 2 — SEC-064: URL-level rule para `/api/v1/admin/shifts/**`

**Abordagem**: como GET e writes usam roles diferentes no mesmo path, não é possível ter uma única URL-level rule granular por método HTTP sem duplicar todas as regras. A decisão é adicionar uma rule permissiva no nível de URL que exija autenticação para OPERATOR+ (mínimo necessário para GET), deixando o controle fino de escrita exclusivamente para o `@PreAuthorize` method-level — que já está correto em todos os métodos.

```java
// SecurityConfig.java — adicionar dentro do bloco authorizeHttpRequests,
// antes de .anyRequest().authenticated():

// Admin shifts: URL-level exige OPERATOR+ (controle fino por @PreAuthorize)
.requestMatchers("/api/v1/admin/shifts/**")
    .hasAnyRole("OPERATOR", "SUPERVISOR", "ADMIN")
```

**Justificativa de consistência**: o padrão vigente em `SecurityConfig` é usar URL-level rules para garantir que qualquer request ao path exija pelo menos autenticação qualificada (não apenas um token JWT válido). Para paths `/admin/**` que têm GETs públicos para OPERATOR e writes restritos a ADMIN, a estratégia correta é: URL-level = role mínima necessária; `@PreAuthorize` = controle fino.

**Tabela de controle resultante para `/api/v1/admin/shifts/**`**:

| Método | URL-level rule | `@PreAuthorize` | Acesso efetivo |
|--------|---------------|-----------------|----------------|
| GET | `OPERATOR+` | `hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')` | OPERATOR+ |
| POST | `OPERATOR+` | `hasRole('ADMIN')` | ADMIN |
| PUT `/{id}` | `OPERATOR+` | `hasRole('ADMIN')` | ADMIN |
| PUT `/{id}/deactivate` | `OPERATOR+` | `hasRole('ADMIN')` | ADMIN |

---

### Decisão 3 — SEC-065: Audit log para operações de turno

**Novos valores no enum `AuditAction`**:

```java
// common/domain/AuditAction.java — adicionar ao final:
SHIFT_CREATED,
SHIFT_UPDATED,
SHIFT_DEACTIVATED
```

**Assinaturas dos use cases atualizadas** (injeção de `AuditService` via construtor):

```java
// CreateShiftUseCase.java:
private final ShiftRepository shiftRepository;
private final AuditService auditService;

public CreateShiftUseCase(ShiftRepository shiftRepository, AuditService auditService) {
    this.shiftRepository = shiftRepository;
    this.auditService = auditService;
}

// Após shiftRepository.save(candidate):
auditService.log(addedBy, AuditAction.SHIFT_CREATED,
    "Shift", saved.getId().toString(),
    Map.of("name", saved.getName(), "startTime", saved.getStartTime().toString(),
            "endTime", saved.getEndTime().toString(), "overnight", String.valueOf(saved.isOvernight())));
```

```java
// UpdateShiftUseCase.java:
private final ShiftRepository shiftRepository;
private final AuditService auditService;

// Após shiftRepository.save(shift):
auditService.log(updatedBy, AuditAction.SHIFT_UPDATED,
    "Shift", id.toString(),
    Map.of("name", request.name(), "startTime", request.startTime().toString(),
            "endTime", request.endTime().toString()));
```

```java
// DeactivateShiftUseCase.java:
private final ShiftRepository shiftRepository;
private final AuditService auditService;

// Após shiftRepository.save(shift):
auditService.log(deactivatedBy, AuditAction.SHIFT_DEACTIVATED,
    "Shift", id.toString(), Map.of("name", shift.getName()));
```

**`ShiftController` atualizado** para passar `principal.getName()` aos use cases:

```java
// ShiftController.java — todos os métodos de escrita recebem Principal:

@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ShiftResponse> create(@Valid @RequestBody CreateShiftRequest request,
                                             Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(createShift.execute(request, principal.getName()));
}

@PutMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ShiftResponse> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateShiftRequest request,
                                             Principal principal) {
    return ResponseEntity.ok(updateShift.execute(id, request, principal.getName()));
}

@PutMapping("/{id}/deactivate")
@ResponseStatus(HttpStatus.NO_CONTENT)
@PreAuthorize("hasRole('ADMIN')")
public void deactivate(@PathVariable UUID id, Principal principal) {
    deactivateShift.execute(id, principal.getName());
}
```

**Assinaturas atualizadas dos use cases**:

| Use case | Assinatura antes | Assinatura depois |
|----------|-----------------|-------------------|
| `CreateShiftUseCase.execute` | `(CreateShiftRequest)` | `(CreateShiftRequest, String addedBy)` |
| `UpdateShiftUseCase.execute` | `(UUID, UpdateShiftRequest)` | `(UUID, UpdateShiftRequest, String updatedBy)` |
| `DeactivateShiftUseCase.execute` | `(UUID)` | `(UUID, String deactivatedBy)` |

---

### Decisão 4 — SH-52: Mover lógica de sobreposição para `ShiftResolverService`

**Problema**: `UpdateShiftUseCase` chama `CreateShiftUseCase.overlaps(existing, candidate)` — acoplamento direto entre dois use cases. Os métodos `overlaps()`, `toMinuteRanges()` e `rangesIntersect()` são lógica de domínio de turno, não lógica de criação.

**Solução**: mover os três métodos para `ShiftResolverService` (já é `@Service` em `common/application/usecase/`). Os use cases injetam `ShiftResolverService` e delegam a verificação.

```java
// ShiftResolverService.java — adicionar métodos:

/**
 * Verifica sobreposição entre dois turnos.
 * Exposto como public para uso em CreateShiftUseCase e UpdateShiftUseCase.
 */
public boolean overlaps(Shift a, Shift b) {
    List<int[]> rangesA = toMinuteRanges(a);
    List<int[]> rangesB = toMinuteRanges(b);
    for (int[] ra : rangesA) {
        for (int[] rb : rangesB) {
            if (rangesIntersect(ra, rb)) return true;
        }
    }
    return false;
}

private List<int[]> toMinuteRanges(Shift s) {
    int start = s.getStartTime().toSecondOfDay() / 60;
    int end   = s.getEndTime().toSecondOfDay() / 60;
    List<int[]> ranges = new ArrayList<>();
    if (!s.isOvernight()) {
        ranges.add(new int[]{start, end});
    } else {
        if (start < 1440) ranges.add(new int[]{start, 1440});
        if (end > 0)      ranges.add(new int[]{0, end});
    }
    return ranges;
}

private boolean rangesIntersect(int[] ra, int[] rb) {
    return ra[0] < rb[1] && rb[0] < ra[1];
}
```

**`CreateShiftUseCase` atualizado**:

```java
// Injetar ShiftResolverService e remover os métodos estáticos overlaps/toMinuteRanges/rangesIntersect:
private final ShiftRepository shiftRepository;
private final ShiftResolverService shiftResolverService;
private final AuditService auditService;

// No loop de verificação:
if (shiftResolverService.overlaps(existing, candidate)) {
    throw new IllegalStateException("Turno sobrepõe turno existente: " + existing.getName());
}
```

**`UpdateShiftUseCase` atualizado**:

```java
// Substituir chamada estática:
// ANTES: if (CreateShiftUseCase.overlaps(existing, candidate))
// DEPOIS:
if (shiftResolverService.overlaps(existing, candidate)) {
    throw new IllegalStateException("Turno sobrepõe turno existente: " + existing.getName());
}
```

**Testes**: `CreateShiftUseCaseTest` e `UpdateShiftUseCaseTest` que testam o algoritmo de sobreposição devem ser adaptados para mockar `ShiftResolverService.overlaps()` — ou mantidos como testes de integração que instanciam o `ShiftResolverService` real (preferível, pois o algoritmo é puro e não tem efeitos colaterais).

---

### Decisão 5 — SH-53: Substituir `setTimeout` por `timer` + `takeUntilDestroyed`

**Problema**: `setTimeout` em `ShiftListComponent.showSnackbar` não é cancelado na destruição do componente. Em Angular com `ChangeDetectionStrategy.OnPush` e sinais, atualizar um sinal após destruição pode gerar erros silenciosos ou warnings.

**Solução**: usar `timer` do RxJS com `takeUntilDestroyed(this.destroyRef)`, que cancela automaticamente o timer quando o componente é destruído.

```typescript
// shift-list.component.ts:

import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';

// ...

export class ShiftListComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  // ... demais campos ...

  private showSnackbar(message: string, type: 'success' | 'error'): void {
    this.snackbar.set({ message, type });
    timer(4000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.snackbar.set(null));
  }
}
```

**`takeUntilDestroyed` fora do construtor**: a partir do Angular 16+, `takeUntilDestroyed(destroyRef)` aceita o `DestroyRef` injetado explicitamente, sendo válido em qualquer método (não exige ser chamado na raiz do injetor). A injeção de `DestroyRef` via `inject(DestroyRef)` deve ocorrer durante a construção do componente (campo de classe com `inject()` — já é o padrão do componente).

---

### Decisão 6 — Ordem de implementação na Sprint 20

Para minimizar conflitos de merge com a funcionalidade principal de peças de reposição (US-049, US-050, US-051):

| Ordem | Item | Tipo | Depende de |
|-------|------|------|-----------|
| 1 | `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DEACTIVATED` no `AuditAction` | Backend shared | — |
| 2 | `overlaps()` + helpers movidos para `ShiftResolverService` | Backend common | — |
| 3 | `CreateShiftUseCase`: injetar `ShiftResolverService` + `AuditService`; remover métodos estáticos | Backend | 1, 2 |
| 4 | `UpdateShiftUseCase`: injetar `ShiftResolverService` + `AuditService`; remover chamada estática | Backend | 1, 2 |
| 5 | `DeactivateShiftUseCase`: injetar `AuditService` | Backend | 1 |
| 6 | `ShiftController`: adicionar `Principal` nos três métodos de escrita | Backend | 3, 4, 5 |
| 7 | `SecurityConfig`: adicionar rule `/api/v1/admin/shifts/**` | Backend | — |
| 8 | `AlertThresholdController`: substituir `get/set` por `compareAndSet` | Backend | — |
| 9 | `ShiftListComponent`: substituir `setTimeout` por `timer` + `takeUntilDestroyed` | Frontend | — |

**Implementação da funcionalidade principal (US-049, US-050, US-051) é independente — pode ser desenvolvida em paralelo.**

---

### Contrato de API — impacto em endpoints existentes

| Endpoint | Antes | Depois |
|----------|-------|--------|
| `POST /api/v1/admin/shifts` | Sem audit; sem URL-level rule | Entrada `SHIFT_CREATED` no `AuditLog`; URL-level rule `OPERATOR+` aplicada |
| `PUT /api/v1/admin/shifts/{id}` | Sem audit; sem URL-level rule | Entrada `SHIFT_UPDATED` no `AuditLog`; URL-level rule `OPERATOR+` aplicada |
| `PUT /api/v1/admin/shifts/{id}/deactivate` | Sem audit; sem URL-level rule | Entrada `SHIFT_DEACTIVATED` no `AuditLog`; URL-level rule `OPERATOR+` aplicada |
| `POST /api/v1/admin/alert-thresholds/evaluate-now` | Race condition com `get/set` | `compareAndSet` garante que apenas um thread passe pelo cooldown por vez |

---

### Consequências
✅ `compareAndSet` elimina a race condition do `evaluate-now` sem adicionar locks ou dependências
✅ URL-level rule para `/admin/shifts/**` traz defesa em profundidade consistente com `/admin/users/**`
✅ `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DEACTIVATED` no `AuditLog` cobrem rastreabilidade de operações administrativas que afetam o cálculo de OEE
✅ `overlaps()` centralizado em `ShiftResolverService` elimina o acoplamento cross-use-case e facilita testes do algoritmo
✅ `takeUntilDestroyed` no Angular evita callbacks de `setTimeout` em componentes destruídos, seguindo a prática recomendada do Angular 16+
⚠️ Assinaturas dos três use cases de turno mudam — qualquer teste unitário que os instancia diretamente precisa ser atualizado para passar `addedBy`/`updatedBy`/`deactivatedBy` e mockar `AuditService`
⚠️ `ShiftResolverService` passa a ter responsabilidade dupla (resolver turno atual + verificar sobreposição) — aceitável enquanto o serviço permanecer coeso em torno do domínio de turno; revisar se crescer além de ~100 linhas
⚠️ `compareAndSet` no `evaluate-now` retorna `429` para o segundo thread em caso de corrida — comportamento correto mas pode surpreender em ambientes de teste com threads simultâneos; documentar nos testes unitários
