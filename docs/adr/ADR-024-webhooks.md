## ADR-024: Outbound Webhooks — Integração com Sistemas Externos
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-071, US-072

### Contexto

O MSB usa um ERP externo e ferramentas de comunicação (Teams/Slack). Quando uma NC crítica é aberta ou uma OS urgente criada, outras ferramentas precisam ser notificadas automaticamente. Webhooks outbound permitem essa integração sem acoplar o Industrial Hub a sistemas específicos.

---

### Decisão 1 — Entidade `WebhookSubscription`

```java
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String url;            // endpoint destino

    @Column(length = 100)
    private String secret;         // HMAC-SHA256 signing secret (opcional)

    @ElementCollection
    @CollectionTable(name = "webhook_event_type")
    @Enumerated(EnumType.STRING)
    private Set<WebhookEvent> events; // eventos assinados

    private boolean active = true;
    private String description;    // ex: "ERP - NCs críticas"
    private String createdBy;
    private LocalDateTime createdAt;
}

public enum WebhookEvent {
    NC_CREATED, NC_STATUS_CHANGED, NC_CRITICAL_OPENED,
    WORK_ORDER_CREATED, WORK_ORDER_STATUS_CHANGED,
    EQUIPMENT_DECOMMISSIONED,
    SLA_BREACHED
}
```

---

### Decisão 2 — Payload e assinatura

Payload JSON padronizado para todos os eventos:
```json
{
  "event": "NC_CRITICAL_OPENED",
  "timestamp": "2026-05-13T10:30:00Z",
  "payload": {
    "id": "uuid",
    "title": "...",
    "severity": "CRITICAL",
    "reportedBy": "operator1"
  }
}
```

Se `secret` configurado, header `X-Hub-Signature-256: sha256=<hmac>` adicionado (padrão GitHub Webhooks). Receptor pode verificar autenticidade.

---

### Decisão 3 — Entrega e retry

`WebhookDeliveryService` com `@Async`:
1. Tenta `POST url` com timeout de 5 segundos
2. Resposta 2xx = sucesso; qualquer outro status = falha
3. Retry automático: 3 tentativas com backoff exponencial (5s, 30s, 2min)
4. Após 3 falhas: desativa a subscription (`active = false`) e envia `Notification` ADMIN

`WebhookDelivery` entity registra cada tentativa (status, responseCode, attempt) para diagnóstico.

---

### Decisão 4 — Integração com use cases existentes

`WebhookDispatchService` injetado nos use cases relevantes:

```java
// CreateNonConformanceUseCase
webhookDispatchService.dispatch(WebhookEvent.NC_CREATED, nc);
if (nc.getSeverity() == NcSeverity.CRITICAL) {
    webhookDispatchService.dispatch(WebhookEvent.NC_CRITICAL_OPENED, nc);
}
```

`WebhookDispatchService.dispatch()` busca subscriptions ativas para o evento e envia de forma assíncrona — falha de webhook nunca aborta a operação de negócio.

---

### Decisão 5 — Endpoints e frontend

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/admin/webhooks | ADMIN | criar subscription |
| GET | /api/v1/admin/webhooks | ADMIN | listar subscriptions |
| PUT | /api/v1/admin/webhooks/{id} | ADMIN | atualizar |
| DELETE | /api/v1/admin/webhooks/{id} | ADMIN | remover |
| POST | /api/v1/admin/webhooks/{id}/test | ADMIN | enviar payload de teste |
| GET | /api/v1/admin/webhooks/{id}/deliveries | ADMIN | histórico de entregas |

Frontend: rota `/admin/webhooks` com tabela de subscriptions, eventos assinados (chips), status de última entrega; botão "Testar" envia payload de teste e exibe response code.

---

### Consequências
✅ Assíncrono — falha de webhook nunca bloqueia operação de negócio
✅ HMAC-SHA256 signing — receptores podem verificar autenticidade
✅ Auto-desativação após 3 falhas — evita tentativas infinitas contra endpoint morto
⚠️ `@ElementCollection` em `events` cria tabela extra `webhook_event_type` — aceito para evitar coluna de CSV
⚠️ Retry com backoff em memória — reinicialização do servidor perde retries pendentes; solução futura: fila persistente (ex: `work_queue` table)
