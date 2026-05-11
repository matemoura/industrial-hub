## ADR-009: Production Readiness — Audit Trail, E2E Tests, Observability & Performance
**Status**: Aprovado
**Data**: 2026-05-11
**US relacionadas**: US-030, US-031, US-033, US-034, US-035, US-036

### Contexto
Sprint 9 (cross-module KPI + relatórios agendados) e Sprint 10 (audit trail, E2E, health, performance) introduzem padrões transversais que afetam todos os módulos. As decisões aqui estabelecem como cada padrão é implementado de forma consistente em todo o sistema.

---

### Decisão 1 — Audit Trail: `AuditLog` em `common/`

O audit trail é uma preocupação transversal (cross-cutting). Fica em `common/domain/` e `common/application/` — não pertence a nenhum módulo de negócio.

```java
// common/domain/AuditLog.java
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_timestamp",  columnList = "timestamp"),
    @Index(name = "idx_audit_username",   columnList = "username"),
    @Index(name = "idx_audit_entity",     columnList = "entity_type, entity_id")
})
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private LocalDateTime timestamp;
    private String username;
    private String action;       // NC_CREATED, WORK_ORDER_STATUS_CHANGED, etc.
    private String entityType;   // NonConformance, WorkOrder, ImportBatch, User
    private String entityId;     // UUID as string
    @Column(columnDefinition = "TEXT")
    private String details;      // JSON string: { "from": "OPEN", "to": "IN_ANALYSIS" }
    private String ipAddress;    // opcional, via HttpServletRequest
}

// common/application/AuditService.java
@Service
public class AuditService {
    private final AuditLogRepository repository;

    @Async
    public void log(String username, String action, String entityType,
                    String entityId, Map<String, Object> details) {
        AuditLog entry = new AuditLog();
        // ... set fields, serialize details to JSON via ObjectMapper
        repository.save(entry);
    }
}
```

**Ações auditadas** (constantes em `AuditAction`):
```
IMPORT_CREATED, IMPORT_DELETED,
NC_CREATED, NC_STATUS_CHANGED,
ACTION_CREATED, ACTION_COMPLETED,
WORK_ORDER_CREATED, WORK_ORDER_STATUS_CHANGED,
USER_CREATED, USER_ROLE_CHANGED, USER_DEACTIVATED,
THRESHOLD_CHANGED
```

**AsyncUncaughtExceptionHandler** obrigatório para não perder exceções silenciosas:
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("Async audit failed: {} — {}", method.getName(), ex.getMessage());
    }
}
```

Audit log é imutável: `AuditLogRepository` expõe apenas `save` e `findAll` (paginado). Nenhum `delete` ou `update` é exposto.

---

### Decisão 2 — Relatórios agendados: Spring `@Scheduled`

`@EnableScheduling` adicionado em `BackendApplication`. Timezone definida no cron expression via property:

```java
@Scheduled(cron = "0 0 7 * * MON", zone = "${app.reports.timezone:America/Sao_Paulo}")
public void sendWeeklyReport() { ... }
```

A lógica de coleta de dados fica em `ReportDataService` — separada do `@Scheduled` para testabilidade (o bean schedulado só chama o service, fácil de testar com mocks sem precisar disparar o scheduler).

**Endpoint de disparo manual** para testar sem esperar segunda-feira:
```
POST /api/v1/admin/reports/weekly/send-now   → ADMIN only
```

---

### Decisão 3 — KPI unificado: endpoint de agregação sem tabela nova

`GET /api/v1/kpi/summary` agrega dados em tempo real dos 3 repositórios existentes. Nenhuma tabela de materialização — a query é rápida o suficiente para os volumes atuais (benchmarked em US-036).

```java
// common/presentation/KpiController.java  (ou portal/ se módulo portal existir)
@RestController
@RequestMapping("/api/v1/kpi")
public class KpiController { ... }
```

Se a query de KPI exceder 300ms em produção → materializar com `@Scheduled` a cada 5min em tabela `kpi_snapshot` (decisão deferida para post-Sprint 10).

---

### Decisão 4 — E2E Tests: Playwright

Playwright configurado em `apps/frontend/` como devDependency:

```bash
npm install -D @playwright/test
npx playwright install chromium   # apenas Chromium para CI
```

Configuração em `apps/frontend/playwright.config.ts`:
```typescript
import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    headless: true,
  },
  reporter: [['html', { outputFolder: 'playwright-report' }]],
});
```

**Pré-requisito para CI:** `docker-compose up -d` antes de `npm run e2e`. A suíte E2E **não** roda no pipeline de PR (muito lento) — roda no pipeline de `main` após deploy Docker.

```json
// package.json scripts adicionados
"e2e": "playwright test",
"e2e:report": "playwright show-report"
```

**Page Object Model (POM)** obrigatório para as 6 jornadas — evitar seletores duplicados entre testes:
```
apps/frontend/e2e/
├── pages/
│   ├── login.page.ts
│   ├── dashboard.page.ts
│   ├── imports.page.ts
│   ├── nc.page.ts
│   └── maintenance.page.ts
└── specs/
    ├── auth.spec.ts
    ├── oee-import.spec.ts
    ├── oee-dashboard.spec.ts
    ├── nc-lifecycle.spec.ts
    ├── maintenance-wo.spec.ts
    └── role-access.spec.ts
```

---

### Decisão 5 — Observabilidade: Spring Boot Actuator

Dependência já disponível em Spring Boot 3.4.1 — adicionar ao `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Configuração em `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when_authorized
management.endpoint.health.roles=ADMIN
info.app.version=@project.version@
info.app.build-time=@maven.build.timestamp@
```

**Custom health indicator** para dados stale:
```java
@Component
public class DynamicsImportHealthIndicator extends AbstractHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        boolean hasRecentImport = batchRepository.existsByPeriodDateAfter(thirtyDaysAgo);
        if (hasRecentImport) builder.up().withDetail("lastImport", "within 30 days");
        else builder.down().withDetail("warning", "No import in last 30 days");
    }
}
```

---

### Decisão 6 — Performance: Java-side aggregation vs. SQL

Regra para otimização:
1. **SQL nativo** apenas para agregações sobre tabelas com > 10k rows esperadas (`time_record`)
2. **Java stream** para tabelas menores (`non_conformance`, `work_order`, `corrective_action`) — mais portável entre H2 e PG
3. **`@EntityGraph`** para eliminar N+1 em relacionamentos lazy
4. **`spring.jpa.properties.hibernate.generate_statistics=true`** apenas no profile `performance` (nunca em `prod`)

Benchmark mínimo aceitável (medido com `StopWatch` em `@SpringBootTest`):
- `GET /kpi/summary` ≤ 300ms com 1k time_records + 200 NCs + 100 WOs
- `GET /oee/dashboard` (30 dias, 13 workers) ≤ 400ms

---

### Consequências
✅ Audit log assíncrono com `AsyncUncaughtExceptionHandler` — falha de auditoria nunca aborta operação de negócio
✅ Playwright com POM — testes E2E manuteníveis, sem duplicação de seletores
✅ Actuator exposto com controle de role — ADMIN vê detalhe, load balancer vê apenas status
✅ `@Scheduled` separado do service — testável com mocks sem framework de scheduler
⚠️ E2E na pipeline de `main` (não em PRs) — risco: regressão E2E detectada só após merge; mitigar com testes unitários rigorosos nos PRs
⚠️ KPI sem materialização — se volume crescer além de ~50k time_records, reavaliar snapshot agendado
⚠️ `info.app.build-time=@maven.build.timestamp@` requer `<plugin>maven-resources-plugin</plugin>` com filtering ativo no `pom.xml`
