## ADR-024: Outbound Webhooks — Integração com Sistemas Externos
**Status**: Aprovado
**Data**: 2026-05-25
**US relacionadas**: US-071, US-072

---

### Contexto

O MSB opera com um ERP externo (Dynamics) e ferramentas de comunicação corporativa (Teams/Slack). Quando uma NC crítica é aberta, uma OS urgente criada ou um SLA violado, outros sistemas precisam ser notificados em tempo real para acionar workflows externos sem polling ativo. Webhooks outbound permitem essa integração de forma padronizada e desacoplada, sem criar dependência direta do Industrial Hub a sistemas de terceiros.

O Sprint 27 entrega o suporte completo: gerenciamento de subscriptions via CRUD (US-071) e o mecanismo de entrega assíncrona com retry e assinatura HMAC-SHA256 (US-072).

---

### Decisão 1 — Modelo de Dados: `WebhookSubscription` e `WebhookDelivery`

#### Entidade `WebhookSubscription`

```java
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String url;                 // endpoint destino (HTTPS obrigatório em prod)

    @Column(length = 100)
    private String secret;              // chave HMAC-SHA256 — opcional; null desativa assinatura

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "webhook_subscription_events",
        joinColumns = @JoinColumn(name = "webhook_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private Set<WebhookEvent> events;   // conjunto de eventos assinados

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 255)
    private String description;         // ex: "ERP Dynamics — NCs críticas"

    @Column(nullable = false, length = 50)
    private String createdBy;           // username do ADMIN que criou

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime disabledAt;   // preenchido ao desativar (manual ou auto após 3 falhas)
}
```

#### Enum `WebhookEvent`

```java
public enum WebhookEvent {
    NC_CREATED,                  // NC registrada (qualquer severidade)
    NC_STATUS_CHANGED,           // transição de status de NC
    NC_CRITICAL_OPENED,          // NC criada com severity = CRITICAL
    WORK_ORDER_CREATED,          // OS criada (qualquer tipo/prioridade)
    WORK_ORDER_STATUS_CHANGED,   // transição de status de OS
    EQUIPMENT_DECOMMISSIONED,    // equipamento marcado como DECOMMISSIONED
    SLA_BREACHED                 // violação de regra SLA detectada pelo EscalationJob
}
```

**Rationale de granularidade**: `NC_CRITICAL_OPENED` é evento separado de `NC_CREATED` para permitir que receptores assinem apenas NCs críticas sem processar volume elevado de NCs LOW/MEDIUM. Receptores que precisam de todas as NCs assinam `NC_CREATED`; receptores de alertas de urgência assinam apenas `NC_CRITICAL_OPENED`.

#### Entidade `WebhookDelivery`

```java
@Entity
@Table(name = "webhook_delivery",
    indexes = {
        @Index(name = "idx_webhook_delivery_subscription", columnList = "subscription_id"),
        @Index(name = "idx_webhook_delivery_created_at", columnList = "created_at")
    })
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private WebhookSubscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WebhookEvent event;

    @Column(nullable = false)
    private int attempt;                // 1, 2 ou 3

    @Column
    private Integer responseCode;       // null se timeout/exceção de rede

    @Column
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;      // SUCCESS, FAILED, PENDING_RETRY

    @Column(columnDefinition = "TEXT")
    private String errorMessage;        // mensagem de erro quando status = FAILED

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

public enum DeliveryStatus {
    SUCCESS,       // resposta 2xx recebida
    FAILED,        // falhou após todas as tentativas
    PENDING_RETRY  // aguardando próxima tentativa (backoff)
}
```

**Decisão de retenção**: `WebhookDelivery` acumula registros continuamente. Job de limpeza (fora do escopo do Sprint 27) deve remover entregas com mais de 30 dias para evitar crescimento ilimitado da tabela.

#### Migrations

```sql
-- V27_1__webhook_subscription.sql
CREATE TABLE webhook_subscription (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url             VARCHAR(500) NOT NULL,
    secret          VARCHAR(100),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     VARCHAR(255),
    created_by      VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP,
    disabled_at     TIMESTAMP
);

CREATE TABLE webhook_subscription_events (
    webhook_id  UUID NOT NULL REFERENCES webhook_subscription(id) ON DELETE CASCADE,
    event_type  VARCHAR(50) NOT NULL,
    PRIMARY KEY (webhook_id, event_type)
);

-- V27_2__webhook_delivery.sql
CREATE TABLE webhook_delivery (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES webhook_subscription(id) ON DELETE CASCADE,
    event           VARCHAR(50) NOT NULL,
    attempt         INT NOT NULL,
    response_code   INT,
    duration_ms     BIGINT,
    status          VARCHAR(20) NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_webhook_delivery_subscription ON webhook_delivery(subscription_id);
CREATE INDEX idx_webhook_delivery_created_at   ON webhook_delivery(created_at);
```

---

### Decisão 2 — Payload Padronizado e Assinatura HMAC-SHA256

#### Estrutura do payload

Todos os eventos usam o mesmo envelope JSON:

```json
{
  "event": "NC_CRITICAL_OPENED",
  "timestamp": "2026-05-25T10:30:00Z",
  "payload": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "title": "Contaminação em linha A",
    "severity": "CRITICAL",
    "type": "PROCESS",
    "status": "OPEN",
    "reportedBy": "operator1",
    "reportedAt": "2026-05-25T10:29:55Z"
  }
}
```

O campo `payload` contém os campos principais da entidade envolvida — sem incluir campos internos de auditoria ou relacionamentos aninhados que aumentem desnecessariamente o tamanho do body.

**Payloads por evento**:

| Evento | Campos em `payload` |
|--------|-------------------|
| `NC_CREATED` | `id`, `title`, `type`, `severity`, `status`, `reportedBy`, `reportedAt` |
| `NC_STATUS_CHANGED` | `id`, `title`, `previousStatus`, `newStatus`, `changedBy` |
| `NC_CRITICAL_OPENED` | `id`, `title`, `severity`, `type`, `reportedBy`, `reportedAt` |
| `WORK_ORDER_CREATED` | `id`, `title`, `type`, `priority`, `status`, `equipmentId`, `openedBy`, `openedAt` |
| `WORK_ORDER_STATUS_CHANGED` | `id`, `title`, `previousStatus`, `newStatus`, `changedBy` |
| `EQUIPMENT_DECOMMISSIONED` | `id`, `code`, `name`, `type`, `decommissionedBy` |
| `SLA_BREACHED` | `slaRuleId`, `entityType`, `entityId`, `breachLevel`, `detectedAt` |

#### Assinatura HMAC-SHA256

Quando a subscription tem `secret` configurado, o header `X-Hub-Signature-256` é enviado:

```
X-Hub-Signature-256: sha256=<hmac-sha256-hex>
```

Cálculo:
```java
// WebhookSignatureService.java
public String sign(String secret, String payloadJson) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
        throw new WebhookSignatureException("Falha ao assinar payload", e);
    }
}
```

Padrão idêntico ao GitHub Webhooks — receptores podem reutilizar bibliotecas existentes de verificação.

**Armazenamento do `secret`**: o campo `secret` é persistido em texto plano na tabela `webhook_subscription` (criptografia de colunas fora do escopo). A API de criação/atualização aceita o secret mas **nunca o retorna** nos endpoints GET — o campo é omitido da projeção `WebhookSubscriptionResponse`. ADMIN pode rotacionar o secret via `PUT /{id}`.

---

### Decisão 3 — Mecanismo de Entrega: Assíncrono, Retry com Backoff Exponencial

#### `WebhookDispatchService`

```java
@Service
public class WebhookDispatchService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSignatureService signatureService;
    private final RestTemplate restTemplate;        // timeout 5s configurado
    private final TaskScheduler taskScheduler;      // Spring ThreadPoolTaskScheduler

    @Async
    public void dispatch(WebhookEvent event, Object entityPayload) {
        List<WebhookSubscription> targets = subscriptionRepository
            .findByActiveAndEventsContaining(true, event);

        for (WebhookSubscription sub : targets) {
            sendWithRetry(sub, event, entityPayload, 1);
        }
    }

    private void sendWithRetry(WebhookSubscription sub, WebhookEvent event,
                               Object payload, int attempt) {
        String payloadJson = buildPayloadJson(event, payload);
        WebhookDelivery delivery = createDeliveryRecord(sub, event, attempt);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (sub.getSecret() != null) {
                headers.set("X-Hub-Signature-256", signatureService.sign(sub.getSecret(), payloadJson));
            }
            headers.set("X-Industrial-Hub-Event", event.name());
            headers.set("X-Industrial-Hub-Delivery", delivery.getId().toString());

            long start = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(
                sub.getUrl(), new HttpEntity<>(payloadJson, headers), String.class);
            long duration = System.currentTimeMillis() - start;

            if (response.getStatusCode().is2xxSuccessful()) {
                markSuccess(delivery, response.getStatusCodeValue(), duration);
            } else {
                handleFailure(sub, event, payload, delivery, attempt,
                    response.getStatusCodeValue(), duration, null);
            }
        } catch (Exception e) {
            handleFailure(sub, event, payload, delivery, attempt, null, 0L, e.getMessage());
        }
    }

    private void handleFailure(WebhookSubscription sub, WebhookEvent event,
                               Object payload, WebhookDelivery delivery,
                               int attempt, Integer responseCode, long duration, String error) {
        markFailed(delivery, responseCode, duration, error, attempt < 3);

        if (attempt < 3) {
            long delayMs = switch (attempt) {
                case 1 -> 5_000L;    //  5 segundos
                case 2 -> 30_000L;   // 30 segundos
                default -> 120_000L; //  2 minutos
            };
            taskScheduler.schedule(
                () -> sendWithRetry(sub, event, payload, attempt + 1),
                Instant.now().plusMillis(delayMs)
            );
        } else {
            // 3ª falha: desativar subscription e notificar ADMIN
            sub.setActive(false);
            sub.setDisabledAt(LocalDateTime.now());
            subscriptionRepository.save(sub);
            notificationService.notifyAdmins(
                "Webhook desativado após 3 falhas",
                "A subscription '" + sub.getDescription() + "' (" + sub.getUrl() +
                ") foi desativada automaticamente após 3 tentativas consecutivas sem resposta 2xx."
            );
        }
    }
}
```

**Configuração do `RestTemplate`**:
```java
@Bean
@Qualifier("webhookRestTemplate")
public RestTemplate webhookRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(5));
    return new RestTemplate(factory);
}
```

**Backoff exponencial**:
| Tentativa | Delay |
|-----------|-------|
| 1 (imediata) | 0s — fire and forget assíncrono |
| 2 (retry 1)  | 5s após falha da tentativa 1 |
| 3 (retry 2)  | 30s após falha da tentativa 2 |
| — (após 3 falhas) | subscription desativada |

**Rationale**: `@Async` garante que falha de webhook nunca aborta a operação de negócio. O `TaskScheduler` agenda retries sem bloquear a thread. Retries em memória são perdidos em caso de reinicialização — limitação conhecida e aceita para o volume atual (53 usuários internos, baixa frequência de eventos). Fila persistente (ex: tabela `work_queue`) é registrada como melhoria futura.

---

### Decisão 4 — Integração com Use Cases Existentes

`WebhookDispatchService.dispatch()` é injetado nos use cases que produzem eventos relevantes. A chamada sempre ocorre **após** a persistência bem-sucedida da entidade, nunca dentro da mesma transação:

```java
// CreateNonConformanceUseCase.java
@Transactional
public NcResponse execute(CreateNcRequest request, String username) {
    NonConformance nc = /* ... cria e persiste ... */;
    auditService.log(username, AuditAction.NC_CREATED, "NonConformance", nc.getId(), ...);
    webhookDispatchService.dispatch(WebhookEvent.NC_CREATED, nc);
    if (nc.getSeverity() == NcSeverity.CRITICAL) {
        webhookDispatchService.dispatch(WebhookEvent.NC_CRITICAL_OPENED, nc);
    }
    return NcResponse.from(nc);
}

// TransitionNcStatusUseCase.java
@Transactional
public NcResponse execute(...) {
    // ... transição e persistência ...
    webhookDispatchService.dispatch(WebhookEvent.NC_STATUS_CHANGED, nc);
    return NcResponse.from(nc);
}
```

**Use cases que disparam webhooks**:

| Use Case | Evento(s) disparado(s) |
|----------|----------------------|
| `CreateNonConformanceUseCase` | `NC_CREATED` + `NC_CRITICAL_OPENED` (se CRITICAL) |
| `TransitionNcStatusUseCase` | `NC_STATUS_CHANGED` |
| `CreateWorkOrderUseCase` | `WORK_ORDER_CREATED` |
| `TransitionWorkOrderStatusUseCase` | `WORK_ORDER_STATUS_CHANGED` |
| `DeleteEquipmentUseCase` (decommission) | `EQUIPMENT_DECOMMISSIONED` |
| `EscalationUseCase` | `SLA_BREACHED` |

---

### Decisão 5 — Segurança: Roles e Controle de Acesso

**Apenas ADMIN pode gerenciar webhooks**. Fundamentação: webhooks expõem dados de negócio a sistemas externos; configurar um destino de dados incorreto pode vazar informações sensíveis. SUPERVISOR e OPERATOR não têm visibilidade da feature.

```java
@RestController
@RequestMapping("/api/v1/admin/webhooks")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class WebhookController { ... }
```

**O `secret` nunca é retornado na API**:

```java
public record WebhookSubscriptionResponse(
    UUID id,
    String url,
    boolean hasSecret,          // boolean — indica se secret está configurado, sem revelá-lo
    Set<WebhookEvent> events,
    boolean active,
    String description,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime disabledAt
) {}
```

O campo `hasSecret` permite que o frontend indique ao ADMIN se a assinatura está ativa sem expor o valor. Para rotacionar o secret, o ADMIN envia `PUT /{id}` com o novo valor.

**Validações de URL**:
- URL deve iniciar com `https://` (validado no DTO com `@Pattern`)
- Exceção: `http://localhost` e `http://127.0.0.1` são permitidos apenas em perfil `dev` — validação condicional no use case
- URL máxima: 500 caracteres (`@Size(max = 500)`)

```java
public record CreateWebhookRequest(
    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = "https://.*", message = "URL deve usar HTTPS")
    String url,

    @Size(max = 100)
    String secret,

    @NotEmpty(message = "Pelo menos um evento deve ser selecionado")
    Set<WebhookEvent> events,

    @Size(max = 255)
    String description
) {}
```

---

### Contrato de API

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| POST | /api/v1/admin/webhooks | ADMIN | 201 `WebhookSubscriptionResponse` | Criar subscription |
| GET | /api/v1/admin/webhooks | ADMIN | 200 `List<WebhookSubscriptionResponse>` | Listar todas as subscriptions |
| GET | /api/v1/admin/webhooks/{id} | ADMIN | 200 `WebhookSubscriptionResponse` | Detalhe da subscription |
| PUT | /api/v1/admin/webhooks/{id} | ADMIN | 200 `WebhookSubscriptionResponse` | Atualizar URL, secret, eventos, descrição |
| DELETE | /api/v1/admin/webhooks/{id} | ADMIN | 204 | Remover subscription permanentemente |
| POST | /api/v1/admin/webhooks/{id}/test | ADMIN | 200 `WebhookTestResponse` | Enviar payload de teste imediato |
| GET | /api/v1/admin/webhooks/{id}/deliveries | ADMIN | 200 `List<WebhookDeliveryResponse>` | Últimas 50 entregas da subscription |
| PUT | /api/v1/admin/webhooks/{id}/activate | ADMIN | 200 `WebhookSubscriptionResponse` | Reativar subscription desativada |

**Respostas de erro padronizadas**:
- `400`: URL inválida, events vazio, secret > 100 chars → `{ "message": "..." }`
- `404`: subscription não encontrada → `{ "message": "Webhook não encontrado: {id}" }`
- `409`: não usado (sem constraint de unicidade em URL — múltiplas subscriptions para a mesma URL são permitidas)

**`WebhookTestResponse`**:
```json
{
  "url": "https://erp.msb.com.br/webhooks/incoming",
  "responseCode": 200,
  "durationMs": 245,
  "success": true,
  "errorMessage": null
}
```

**`WebhookDeliveryResponse`**:
```json
{
  "id": "uuid",
  "event": "NC_CRITICAL_OPENED",
  "attempt": 2,
  "responseCode": 503,
  "durationMs": 5003,
  "status": "PENDING_RETRY",
  "errorMessage": null,
  "createdAt": "2026-05-25T10:30:05Z"
}
```

---

### Decisão 6 — Frontend: Rota `/admin/webhooks`

**Rota**: `/admin/webhooks` (ADMIN only, lazy-loaded; guard redireciona SUPERVISOR/OPERATOR para `/dashboard`)

**Componentes**:

1. **Tabela de subscriptions** (`webhooks-list.component.ts`):
   - Colunas: URL (truncada em 60 chars com tooltip completo), Eventos (chips coloridos por categoria), Status (chip ATIVO=verde / INATIVO=vermelho), Última entrega (responseCode + tempo relativo), Ações
   - Botão "Nova Subscription" navega para formulário
   - Botão "Testar" por linha: chama `POST /{id}/test`; spinner durante chamada; snackbar com resultado `"Teste: 200 OK (245ms)"` ou `"Teste falhou: timeout após 5s"`
   - Botão "Histórico" por linha: abre painel lateral (MatSidenav) com tabela das últimas 50 entregas
   - Botão "Reativar" visível apenas em subscriptions inativas

2. **Formulário de criação/edição** (`webhook-form.component.ts`):
   - Campo URL (input text, validação `https://` client-side)
   - Campo Secret (input `type="password"` com toggle show/hide; placeholder "Deixe vazio para desativar assinatura")
   - Checkboxes de eventos agrupados por categoria:
     - **QMS**: NC_CREATED, NC_STATUS_CHANGED, NC_CRITICAL_OPENED
     - **Manutenção**: WORK_ORDER_CREATED, WORK_ORDER_STATUS_CHANGED, EQUIPMENT_DECOMMISSIONED
     - **SLA**: SLA_BREACHED
   - Campo Descrição (textarea opcional)
   - Botão "Salvar" desabilitado até URL válida + pelo menos 1 evento selecionado

3. **Painel de histórico de entregas** (inline no sidenav):
   - Tabela: tentativa, evento, status (chip), response code, duração, data/hora
   - Status FAILED=vermelho, SUCCESS=verde, PENDING_RETRY=âmbar
   - Empty state: "Nenhuma entrega registrada ainda"

---

### Decisão 7 — Package e Estrutura de Arquivos (Backend)

```
common/
└── webhook/
    ├── domain/
    │   ├── WebhookSubscription.java
    │   ├── WebhookDelivery.java
    │   ├── WebhookEvent.java
    │   └── DeliveryStatus.java
    ├── application/
    │   ├── dto/
    │   │   ├── CreateWebhookRequest.java
    │   │   ├── UpdateWebhookRequest.java
    │   │   ├── WebhookSubscriptionResponse.java
    │   │   ├── WebhookDeliveryResponse.java
    │   │   └── WebhookTestResponse.java
    │   └── usecase/
    │       ├── CreateWebhookSubscriptionUseCase.java
    │       ├── UpdateWebhookSubscriptionUseCase.java
    │       ├── DeleteWebhookSubscriptionUseCase.java
    │       ├── ListWebhookSubscriptionsUseCase.java
    │       ├── GetWebhookDeliveriesUseCase.java
    │       └── TestWebhookUseCase.java
    ├── infrastructure/
    │   ├── WebhookSubscriptionRepository.java
    │   └── WebhookDeliveryRepository.java
    └── presentation/
        └── WebhookController.java

common/webhook/service/
    ├── WebhookDispatchService.java   — dispatch @Async + retry
    └── WebhookSignatureService.java  — cálculo HMAC-SHA256
```

**Rationale do package `common/webhook/`**: webhooks são infraestrutura transversal — não pertencem ao domínio QMS nem Maintenance. O package `common/` já abriga `auth/`, `kpi/`, `notification/` e outras features transversais.

---

### Decisão 8 — Considerações de Volume e Performance

- **Volume esperado**: ~53 usuários internos; eventos estimados em menos de 100/dia em operação normal. O mecanismo assíncrono é suficiente sem fila persistente.
- **Timeout de 5s**: adequado para integrações internas (Dynamics na mesma rede corporativa). Integrações cloud (Teams/Slack) têm SLA de resposta geralmente < 2s.
- **Subscriptions em memória**: `findByActiveAndEventsContaining()` executa query a cada dispatch. Para volume futuro > 500 eventos/dia, considerar cache de subscriptions ativas com `@Scheduled` de invalidação.
- **`WebhookDelivery` crescimento**: sem TTL automático no Sprint 27. ADR futuro deve incluir job de limpeza com retenção de 30 dias.

---

### Consequências

✅ `@Async` garante que falha de webhook nunca bloqueia operação de negócio — latência de use case não aumenta
✅ HMAC-SHA256 padronizado (padrão GitHub Webhooks) — receptores reutilizam bibliotecas existentes de verificação
✅ Auto-desativação após 3 falhas com notificação ADMIN — evita tentativas infinitas contra endpoint morto
✅ `secret` nunca retornado pela API — campo `hasSecret: boolean` informa estado sem expor o valor
✅ Backoff exponencial (5s → 30s → 2min) respeita receptores temporariamente indisponíveis
✅ `WebhookDelivery` por tentativa — histórico completo para diagnóstico de falhas de integração
✅ Endpoint `POST /{id}/test` permite validar integração sem esperar evento real de negócio
✅ Apenas ADMIN pode gerenciar webhooks — superfície de dados expostos a sistemas externos controlada
⚠️ Retries em memória (`TaskScheduler`) — reinicialização do servidor descarta retries pendentes; solução futura: tabela `scheduled_webhook_retry` com job de recuperação no startup
⚠️ `@ElementCollection` em `events` cria tabela extra `webhook_subscription_events` — aceito para evitar coluna CSV (padrão existente no projeto em outras entidades)
⚠️ `WebhookDelivery` sem TTL automático — cresce indefinidamente; job de limpeza com retenção 30 dias deve ser incluído em sprint futura
⚠️ Validação `https://` apenas em prod — perfil `dev` aceita `http://localhost` para testes locais; garantir que a exceção não vaze para staging/prod via profile guard explícito no use case
