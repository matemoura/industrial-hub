## ADR-040: Sprint 27 — Decisões de Implementação (Outbound Webhooks + BUG-S27 + SEC-092/095)
**Status**: Aprovado
**Data**: 2026-05-26
**US relacionadas**: US-071, US-072

---

### Contexto

O Sprint 27 implementou o sistema de outbound webhooks conforme especificado no ADR-024. Durante o ciclo de desenvolvimento, QA (Maiana), Security (Beatriz) e Pipeline (Maitê) identificaram 3 blockers funcionais (BUG-S27-01/02/03) e 2 achados de segurança fixados ainda na sprint (SEC-092 HIGH — SSRF e SEC-095 MEDIUM — ausência de auditoria). Este ADR documenta as decisões de implementação complementares ao ADR-024 (que cobre o design do sistema), os fixes aplicados nos blockers e as limitações conhecidas diferidas para Sprint 28.

---

### Decisão 1 — BUG-S27-01/02: campo `success` em `WebhookDeliveryResponse`

**Problema**: `WebhookDeliveryResponse` não incluía o campo `success` (boolean calculado como `responseCode` entre 200–299). O frontend dependia do enum `status` para determinar sucesso/falha, divergindo do contrato definido no AC#15.

**Decisão**: adicionar `success: boolean` ao record `WebhookDeliveryResponse`, calculado no factory `from()`.

```java
// WebhookDeliveryResponse.java
public record WebhookDeliveryResponse(
    UUID id,
    WebhookEvent event,
    int attempt,
    Integer responseCode,
    long durationMs,
    boolean success,   // ADICIONADO — BUG-S27-01
    String errorMessage,
    LocalDateTime createdAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(
            d.getId(), d.getEvent(), d.getAttempt(),
            d.getResponseCode(), d.getDurationMs(),
            d.getResponseCode() != null
                && d.getResponseCode() >= 200
                && d.getResponseCode() < 300,
            d.getErrorMessage(), d.getCreatedAt()
        );
    }
}
```

Teste `WebhookDeliveryResponseTest` cobre: `responseCode=200` → `success=true`; `responseCode=503` → `success=false`; `responseCode=null` (timeout) → `success=false`.

---

### Decisão 2 — BUG-S27-03: dispatch `EQUIPMENT_DECOMMISSIONED` em `DeleteEquipmentUseCase`

**Problema**: `WebhookEvent.EQUIPMENT_DECOMMISSIONED` existia no enum mas nunca era disparado. `DeleteEquipmentUseCase` realizava soft-delete sem invocar `webhookDispatchService.dispatch()`.

**Decisão**: adicionar dispatch condicional em `DeleteEquipmentUseCase.execute()`, após `repository.save(equipment)`, somente quando `equipment.getStatus() == EquipmentStatus.DECOMMISSIONED`. Equipamentos podem ser desativados (`active=false`) sem estar decommissioned — o evento só dispara na retirada definitiva.

```java
// DeleteEquipmentUseCase.java — após repository.save(equipment)
if (equipment.getStatus() == EquipmentStatus.DECOMMISSIONED) {
    webhookDispatchService.dispatch(
        WebhookEvent.EQUIPMENT_DECOMMISSIONED,
        WebhookEquipmentPayload.from(equipment)
    );
}
```

Teste `DeleteEquipmentUseCaseTest` cobre: (a) `status = DECOMMISSIONED` → `dispatch()` invocado 1x; (b) `status = OPERATIONAL` → `dispatch()` não invocado.

---

### Decisão 3 — SEC-092 (HIGH): proteção SSRF via `WebhookUrlValidator`

**Problema**: o `@Pattern` em `CreateWebhookRequest.url` validava apenas prefixo HTTP/HTTPS sintático, sem resolver o hostname DNS. Um ADMIN podia criar subscription apontando para IPs RFC-1918 (10.x.x.x, 172.16–31.x.x, 192.168.x.x), link-local (169.254.x.x — incluindo o endpoint de metadados AWS) ou loopback, expondo infraestrutura interna via requisições automáticas a cada evento de negócio.

**Decisão**: implementar `WebhookUrlValidator` chamado dentro de `CreateWebhookSubscriptionUseCase.execute()`, `UpdateWebhookSubscriptionUseCase.execute()` e `TestWebhookUseCase.execute()`.

```java
// common/webhook/application/WebhookUrlValidator.java
@Component
public class WebhookUrlValidator {

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public void validate(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) throw new WebhookInvalidUrlException("URL sem hostname: " + url);

            // Permitir localhost em perfis dev/test sem resolução DNS
            boolean isDevOrTest = activeProfile.contains("dev") || activeProfile.contains("test");
            if (isDevOrTest && (host.equals("localhost") || host.equals("127.0.0.1"))) return;

            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()   // RFC-1918
                    || address.isLinkLocalAddress()   // 169.254.x.x
                    || address.isAnyLocalAddress()) {
                throw new WebhookInvalidUrlException(
                    "URL de webhook aponta para endereço privado ou reservado");
            }
        } catch (UnknownHostException e) {
            throw new WebhookInvalidUrlException("Hostname inválido ou não resolvível: " + url);
        } catch (URISyntaxException e) {
            throw new WebhookInvalidUrlException("URL malformada: " + url);
        }
    }
}
```

`GlobalExceptionHandler` mapeia `WebhookInvalidUrlException` para `400` com `{ "message": "..." }`.

**Limitação conhecida**: validação ocorre no momento do cadastro — DNS rebinding não mitigado (hostname pode resolver para IP público no cadastro e para IP interno no momento do dispatch). Aceito para v1; monitorar com log de URL resolvida em `WebhookDispatchService` em sprint futura.

---

### Decisão 4 — SEC-095 (MEDIUM): auditoria de operações CRUD de webhook

**Problema**: `CreateWebhookSubscriptionUseCase`, `UpdateWebhookSubscriptionUseCase`, `DeleteWebhookSubscriptionUseCase` e `ActivateWebhookSubscriptionUseCase` não chamavam `auditService.log()`. Webhooks definem destinos de dados sensíveis de negócio — criação, atualização e deleção devem ser rastreáveis para conformidade LGPD e forense.

**Decisão**: adicionar ao enum `AuditAction`:
```java
WEBHOOK_CREATED, WEBHOOK_UPDATED, WEBHOOK_DELETED, WEBHOOK_ACTIVATED, WEBHOOK_TESTED
```

`auditService.log()` chamado em cada use case com `username` do `Principal` já injetado nos controllers via `@AuthenticationPrincipal UserDetails user`.

---

### Decisão 5 — Package e configuração de beans do módulo Webhook

**Package**:
```
common/webhook/
├── domain/          — WebhookSubscription, WebhookDelivery, WebhookEvent (enum)
├── application/
│   ├── dto/         — request/response records + payload records
│   ├── usecase/     — CreateWebhookSubscriptionUseCase, ..., TestWebhookUseCase
│   └── service/     — WebhookDispatchService, WebhookUrlValidator
├── infrastructure/  — WebhookSubscriptionRepository, WebhookDeliveryRepository
└── presentation/    — WebhookAdminController
```

`WebhookConfig.java` declara:
- `@Bean @Qualifier("webhookRestTemplate") RestTemplate webhookRestTemplate()` — `ConnectTimeout=5s`, `ReadTimeout=5s`
- `@Bean @Qualifier("webhookTaskScheduler") TaskScheduler webhookTaskScheduler()` — `ThreadPoolTaskScheduler` com pool de 2 threads

`@Qualifier` obrigatório em ambos os beans para evitar conflito com o `ThreadPoolTaskScheduler` registrado por `@EnableScheduling` — sem `@Qualifier`, Spring lançaria `NoUniqueBeanDefinitionException` ao resolver o `TaskScheduler` no `WebhookDispatchService`.

---

### Decisão 6 — `TaskScheduler` para retry (vs `Thread.sleep` do AC original)

**Contexto**: os ACs US-072 AC#11 e AC#14 especificavam `Thread.sleep(5_000)` / `Thread.sleep(30_000)` dentro do `@Async`, com `BiConsumer<Integer, Long>` injetável para testabilidade.

**Decisão**: implementado com `TaskScheduler.schedule(Runnable, Instant.now().plusSeconds(delay))` em vez de `Thread.sleep`. Diferença: `Thread.sleep` bloqueia o thread do pool assíncrono pelos 5s/30s do backoff; `TaskScheduler` libera o thread imediatamente e agenda a tentativa futura via callback.

**Trade-off**: retries em memória — reinício do servidor descarta tentativas pendentes; `WebhookDelivery` com status `PENDING_RETRY` ficam órfãs no banco. Limitação documentada no ADR-024. Fix mínimo (marcar deliveries órfãs como `FAILED` ao reativar subscription) diferido para Sprint 28 (US-096 SEC-100).

**Testabilidade**: `TaskScheduler` mockado com `mock(TaskScheduler.class)`; `ArgumentCaptor<Instant>` captura o delay agendado para verificação nos testes.

---

### Decisão 7 — Alinhamento de versões Angular e `--legacy-peer-deps` (commit e887575)

Build Docker falhava em `npm ci` por incompatibilidade de peer dependencies entre pacotes `@angular/*` em versões mistas. Fix: todos os pacotes `@angular/*` alinhados para `21.2.12`; `--legacy-peer-deps` adicionado ao `RUN npm ci` no `Dockerfile` do frontend para evitar falha em peer deps transitivas de bibliotecas de terceiros que ainda não declararam suporte formal a Angular 21. Não impacta lógica de negócio.

---

### Contrato de API — Sprint 27

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| POST | /api/v1/admin/webhooks | ADMIN | 201 `WebhookSubscriptionResponse` | Cria subscription |
| GET | /api/v1/admin/webhooks | ADMIN | 200 `List<WebhookSubscriptionResponse>` | Lista subscriptions |
| PUT | /api/v1/admin/webhooks/{id} | ADMIN | 200 / 404 | Atualiza subscription |
| DELETE | /api/v1/admin/webhooks/{id} | ADMIN | 204 / 404 | Remove subscription |
| PUT | /api/v1/admin/webhooks/{id}/activate | ADMIN | 200 / 404 | Reativa subscription desativada |
| POST | /api/v1/admin/webhooks/{id}/test | ADMIN | 200 `WebhookTestResponse` | Testa entrega síncrona |
| GET | /api/v1/admin/webhooks/{id}/deliveries | ADMIN | 200 `List<WebhookDeliveryResponse>` / 404 | Histórico (últimas 50) |

**Nota de segurança**: `WebhookSubscriptionResponse.hasSecret` retorna `boolean` — o campo `secret` jamais é exposto via API (ADR-024 Decisão 1).

---

### Consequências

✅ BUG-S27-01/02: campo `success` em `WebhookDeliveryResponse` — frontend pode determinar sucesso/falha sem iterar enum `status`
✅ BUG-S27-03: `EQUIPMENT_DECOMMISSIONED` disparado corretamente em `DeleteEquipmentUseCase`
✅ SEC-092: `WebhookUrlValidator` bloqueia IPs RFC-1918, link-local e loopback — vetor SSRF mitigado para casos diretos
✅ SEC-095: 5 novos valores em `AuditAction` para webhooks — rastreabilidade LGPD garantida nas operações de configuração
✅ `@Qualifier` em `webhookTaskScheduler` e `webhookRestTemplate` previne conflito de beans Spring
✅ Angular 21.2.12 alinhado em todos `@angular/*` — build Docker estável
⚠️ DNS rebinding não mitigado pela validação no cadastro — limitação aceita para v1; considerar validação no momento do dispatch em sprint futura
⚠️ Retries em `TaskScheduler` perdem estado em restart — `WebhookDelivery` com `PENDING_RETRY` ficam órfãs; fix mínimo diferido para US-096 (Sprint 28)
⚠️ SEC-093/094/096/097 diferidos para Sprint 28 — sanitização de URL em logs, categorização de errorMessage e clareza do `@NotBlank` pendentes
⚠️ SEC-088/089/090 (PWA/offline — deferred do Sprint 26) também diferidos para Sprint 28
