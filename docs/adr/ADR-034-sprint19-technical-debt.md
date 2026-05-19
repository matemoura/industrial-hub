## ADR-034: Sprint 19 — Tech Debt
**Status**: Aprovado
**Data**: 2026-05-21
**US relacionadas**: US-089

### Contexto

Ao final da Sprint 18, Beatriz (Security Engineer) identificou um item diferido no módulo de alertas implementado na Sprint 18 (US-046, US-047, US-048) que não pôde ser endereçado no escopo daquela sprint.

**SEC-061**: O endpoint `POST /api/v1/admin/alert-thresholds/evaluate-now` (implementado em US-047 AC#10) dispara avaliação imediata de todos os thresholds de alerta, incluindo envio de emails. O endpoint é restrito a ADMIN, mas não tem nenhum mecanismo de rate limiting: um ADMIN pode chamá-lo em loop, disparando avaliações repetidas, criando `Notification` duplicadas e potencialmente enviando dezenas de emails em minutos. O debounce de 60 minutos já implementado no `AlertEvaluatorUseCase` (ADR-013 Decisão 3) protege parcialmente contra criação de notificações duplicadas, mas não bloqueia a execução do use case em si — cada chamada ao endpoint executa queries nos repositórios de OEE, QMS e Maintenance, independentemente do debounce.

Adicionalmente, não existe entrada no `AuditLog` quando `evaluate-now` é invocado manualmente, tornando impossível rastrear quem e quando disparou avaliações fora do ciclo regular de 30 minutos.

---

### Decisão 1 — Rate limiting via debounce em memória para `evaluate-now`

**Alternativas consideradas:**

| Abordagem | Prós | Contras |
|-----------|------|---------|
| `@RateLimiter` Resilience4j | Robusto, configurável, padrão cloud | Requer `resilience4j-spring-boot3` na `pom.xml`; adiciona dep por um endpoint; configuração em `application.properties` |
| `lastEvaluationTime` em memória (`AtomicReference<Instant>`) | Zero dependências extras; simples; consistente com o debounce já existente no `AlertEvaluatorUseCase` | Reinicia com o processo (não persiste entre restarts); sem compartilhamento em cluster — aceitável para instalação single-instance do MSB |

**Decisão**: debounce em memória via `AtomicReference<Instant>` no próprio `AlertThresholdController` (ou extraído para um `EvaluateNowGuard` `@Component`). Escolhido por consistência com o padrão de debounce já adotado no projeto (ADR-013 Decisão 3) e por não introduzir dependências extras para um único endpoint.

**Intervalo mínimo entre chamadas manuais**: 5 minutos (`PT5M`). Justificativa: o ciclo automático é de 30 minutos; uma chamada manual a cada 5 minutos é razoável para operações de diagnóstico sem risco de flood.

```java
// AlertThresholdController.java — adicionar campo e lógica:

@RestController
@RequestMapping("/api/v1/admin/alert-thresholds")
@Validated
public class AlertThresholdController {

    private static final Duration EVALUATE_NOW_COOLDOWN = Duration.ofMinutes(5);

    private final AtomicReference<Instant> lastManualEvaluation =
        new AtomicReference<>(Instant.EPOCH);

    // ... outros campos ...

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
        lastManualEvaluation.set(now);

        int evaluated = alertEvaluatorUseCase.execute();

        // Audit — ver Decisão 2
        auditService.log(principal.getName(), AuditAction.ALERT_EVALUATED_MANUAL,
            "AlertThreshold", "all", Map.of("evaluated", String.valueOf(evaluated)));

        return ResponseEntity.ok(new EvaluateNowResponse(evaluated));
    }
}
```

**Exceção e resposta HTTP:**

```java
// common/domain/exception/EvaluateNowCooldownException.java
public class EvaluateNowCooldownException extends RuntimeException {
    private final long secondsRemaining;
    public EvaluateNowCooldownException(long secondsRemaining) {
        super("Avaliação manual disponível em " + secondsRemaining + " segundos");
        this.secondsRemaining = secondsRemaining;
    }
    public long getSecondsRemaining() { return secondsRemaining; }
}
```

```java
// GlobalExceptionHandler.java — adicionar handler:
@ExceptionHandler(EvaluateNowCooldownException.class)
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public Map<String, Object> handleEvaluateNowCooldown(EvaluateNowCooldownException ex) {
    return Map.of(
        "message", ex.getMessage(),
        "secondsRemaining", ex.getSecondsRemaining()
    );
}
```

**Contrato de API atualizado:**

| Situação | HTTP | Body |
|----------|------|------|
| Chamada permitida (cooldown expirado) | `200` | `{ "evaluated": N }` |
| Chamada dentro do cooldown de 5 min | `429` | `{ "message": "Avaliação manual disponível em X segundos", "secondsRemaining": X }` |

---

### Decisão 2 — Audit log para `evaluate-now`

**Problema**: chamadas manuais ao `evaluate-now` não deixam rastro no `AuditLog`, impossibilitando rastreabilidade forense de quem disparou avaliações fora do ciclo automático.

**Decisão**: adicionar `ALERT_EVALUATED_MANUAL` ao enum `AuditAction` e chamar `auditService.log()` após cada execução bem-sucedida de `evaluate-now`.

```java
// common/domain/AuditAction.java — adicionar:
ALERT_EVALUATED_MANUAL
// Não recriar valores existentes
```

**Chamada no controller** (já incluída no snippet da Decisão 1):

```java
auditService.log(
    principal.getName(),
    AuditAction.ALERT_EVALUATED_MANUAL,
    "AlertThreshold",
    "all",
    Map.of("evaluated", String.valueOf(evaluated))
);
```

**Campos do `AuditLog` resultante:**

| Campo | Valor |
|-------|-------|
| `username` | username do ADMIN que chamou o endpoint |
| `action` | `ALERT_EVALUATED_MANUAL` |
| `entityType` | `"AlertThreshold"` |
| `entityId` | `"all"` (não é uma entidade específica) |
| `details` | `{ "evaluated": "N" }` (N = número de thresholds avaliados) |

---

### Decisão 3 — Ordem de implementação na Sprint 19

Para evitar conflitos com a implementação principal de turnos (US-054, US-055, US-056):

| Ordem | Item | Tipo | Impacto |
|-------|------|------|---------|
| 1 | `AuditAction.ALERT_EVALUATED_MANUAL` no enum | Backend shared | Base para Decisão 2 |
| 2 | `EvaluateNowCooldownException` + handler no `GlobalExceptionHandler` | Backend common | Isolado |
| 3 | Debounce `AtomicReference<Instant>` + audit no `AlertThresholdController` | Backend common | Requer itens 1 e 2 |
| 4 | Testes unitários do cooldown | Backend common | Requer item 3 |

**Implementação dos turnos (US-054, 055, 056) é independente — pode ser desenvolvida em paralelo ou antes.**

---

### Contrato de API — novos comportamentos

| Situação | Antes (buggy) | Depois (corrigido) |
|----------|---------------|-------------------|
| `POST /evaluate-now` chamado repetidamente | Executa avaliação completa a cada chamada sem limite | `429` após primeira chamada; próxima execução disponível em ≤ 5 min |
| `POST /evaluate-now` bem-sucedido | Sem entrada no `AuditLog` | Entrada `ALERT_EVALUATED_MANUAL` registrada com username e N avaliados |

---

### Consequências
✅ Rate limiting sem dependência extra — debounce em memória consistente com o padrão já adotado em `AlertEvaluatorUseCase`
✅ `ALERT_EVALUATED_MANUAL` no `AuditLog` permite rastrear quem e quando disparou avaliações manuais
✅ HTTP `429 Too Many Requests` com campo `secondsRemaining` permite que o frontend informe o usuário sobre o tempo de espera
⚠️ Debounce em memória não persiste entre reinicializações do processo — após restart, a primeira chamada manual sempre é permitida independentemente do tempo desde a última execução. Aceitável para instalação single-instance; documentar como limitação conhecida
⚠️ `lastManualEvaluation` compartilhado entre threads via `AtomicReference` — thread-safe, mas sem sincronização distribuída. Suficiente para deployment single-instance do MSB
