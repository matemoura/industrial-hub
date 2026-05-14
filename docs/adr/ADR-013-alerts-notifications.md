## ADR-013: Threshold Alerts & Notification Center
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-046, US-047, US-048

### Contexto

Gestores precisam ser alertados proativamente quando métricas críticas ultrapassam limites configurados: OEE abaixo de um patamar, NCs críticas acima de um número, ordens de serviço urgentes pendentes por mais de X horas. O sistema hoje só envia emails em eventos pontuais (NC criada, NC fechada). Esta ADR adiciona um mecanismo de thresholds configuráveis e uma central de notificações in-app.

---

### Decisão 1 — Entidade `AlertThreshold`

```java
@Entity
@Table(name = "alert_threshold")
public class AlertThreshold {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AlertMetric metric; // OEE_AVG_BELOW, NC_CRITICAL_ABOVE, WO_URGENT_PENDING_HOURS

    @Column(nullable = false)
    private Double threshold;   // valor limite (ex: 0.65 para OEE, 3 para NCs críticas)

    private boolean emailEnabled;  // enviar email quando disparado
    private boolean active = true;
    private String createdBy;
    private LocalDateTime updatedAt;
}

public enum AlertMetric {
    OEE_AVG_BELOW,           // OEE médio (30 dias) < threshold
    NC_CRITICAL_ABOVE,       // NCs com severity=CRITICAL e status OPEN > threshold
    WO_URGENT_PENDING_HOURS  // OS com priority=URGENT em OPEN há mais de threshold horas
}
```

---

### Decisão 2 — Entidade `Notification`

```java
@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_notif_username", columnList = "username"),
    @Index(name = "idx_notif_read",     columnList = "read_at"),
    @Index(name = "idx_notif_created",  columnList = "created_at")
})
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String username;    // destinatário; null = broadcast para SUPERVISOR+
    private String title;
    private String body;
    @Enumerated(EnumType.STRING)
    private NotificationSeverity severity; // INFO, WARNING, CRITICAL

    private LocalDateTime createdAt;
    private LocalDateTime readAt;  // null = não lida
}

public enum NotificationSeverity { INFO, WARNING, CRITICAL }
```

---

### Decisão 3 — Motor de avaliação: `AlertEvaluatorJob`

```java
@Scheduled(cron = "0 0/30 * * * *", zone = "America/Sao_Paulo") // a cada 30 minutos
public void evaluate() { alertEvaluatorUseCase.execute(); }
```

`AlertEvaluatorUseCase` avalia cada `AlertThreshold` ativo:
- Busca o valor atual da métrica nos repositórios correspondentes
- Se violação: cria `Notification` (broadcast) e envia email se `emailEnabled = true`
- **Debounce**: não dispara nova notificação para a mesma métrica se já existe uma com `createdAt` nos últimos 60 minutos (evita spam)

---

### Decisão 4 — Package

```
common/
├── domain/
│   ├── AlertThreshold.java
│   ├── AlertMetric.java
│   ├── Notification.java
│   └── NotificationSeverity.java
├── application/usecase/
│   ├── AlertEvaluatorUseCase.java
│   ├── GetNotificationsUseCase.java
│   └── MarkNotificationReadUseCase.java
├── infrastructure/
│   ├── AlertThresholdRepository.java
│   └── NotificationRepository.java
└── presentation/
    ├── AlertThresholdController.java  (/api/v1/admin/alert-thresholds)
    └── NotificationController.java    (/api/v1/notifications)
```

---

### Decisão 5 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/admin/alert-thresholds | ADMIN | listar thresholds |
| POST | /api/v1/admin/alert-thresholds | ADMIN | criar threshold |
| PUT | /api/v1/admin/alert-thresholds/{id} | ADMIN | atualizar |
| DELETE | /api/v1/admin/alert-thresholds/{id} | ADMIN | remover |
| GET | /api/v1/notifications | autenticado | notificações do usuário (não lidas primeiro) |
| PUT | /api/v1/notifications/{id}/read | autenticado | marcar como lida |
| PUT | /api/v1/notifications/read-all | autenticado | marcar todas como lidas |

---

### Decisão 6 — Frontend: sino de notificações no nav

Ícone de sino no `NavComponent` com badge de contagem (não lidas). Ao clicar: dropdown/panel com lista das últimas 10 notificações. Polling a cada 60 segundos via `interval(60_000)`. Notificações CRITICAL exibidas com fundo vermelho.

---

### Consequências
✅ Debounce de 60 min evita spam de alertas repetidos
✅ `username = null` em Notification = broadcast — sem criar N registros por usuário
⚠️ Polling de 60s é suficiente para alertas operacionais; WebSocket é overkill para 53 usuários
⚠️ Avaliador a cada 30 min pode detectar violações com até 30 min de atraso — aceitável para KPIs diários
