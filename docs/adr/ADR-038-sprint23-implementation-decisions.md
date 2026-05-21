## ADR-038: Sprint 23 — Decisões de Implementação (Multi-Plant + Tech Debt SEC-075/076/077 + escalateByEmail)
**Status**: Aprovado
**Data**: 2026-05-21
**US relacionadas**: US-063, US-064, US-092 (tech debt)

---

### Contexto

O Sprint 23 tem dois eixos:

1. **Novas funcionalidades**: multi-plant support (US-063, US-064), complementando o ADR-020 que define a arquitetura principal da dimensão `Plant`. Este ADR detalha as decisões de implementação não cobertas: estratégia de `PlantContext`, package completo, migração retrocompatível, filtros nas queries existentes e frontend selector.

2. **Tech debt de segurança diferidos do Sprint 22** por Beatriz: SEC-075 (`/run-now` sem rate limiting), SEC-076 (`classifierValue` sem validação semântica), SEC-077 (execução manual de escalação não auditada), e o gap `escalateByEmail` identificado por Maiana (US-062 AC#4d/e: `EscalationUseCase` não envia email via `JavaMailSender` quando `escalateByEmail = true`).

---

### Decisão 1 — Tech Debt SEC-075: cooldown em `/run-now` (padrão `AtomicReference<Instant>`)

**Problema**: `SlaRuleController.runNow()` invoca `escalationUseCase.execute()` diretamente sem debounce. Um ADMIN pode disparar múltiplas varreduras em sequência, gerando N broadcasts `CRITICAL` por chamada. Análogo ao SEC-063 corrigido em `AlertThresholdController.evaluateNow()` no Sprint 20.

**Decisão**: aplicar exatamente o mesmo padrão `AtomicReference<Instant>` já presente em `AlertThresholdController` — sem dependência externa (Bucket4j seria over-engineering para 1 ADMIN). Cooldown de **5 minutos**, consistente com `EVALUATE_NOW_COOLDOWN`.

```java
// SlaRuleController.java — adicionar campos
private static final Duration RUN_NOW_COOLDOWN = Duration.ofMinutes(5);
private final AtomicReference<Instant> lastManualEscalation = new AtomicReference<>(Instant.EPOCH);

// runNow() — substituir implementação atual
@PostMapping("/run-now")
@PreAuthorize("hasRole('ADMIN')")
public EscalationRunResponse runNow(Principal principal) {
    Instant now = Instant.now();
    Instant last = lastManualEscalation.get();

    if (Duration.between(last, now).compareTo(RUN_NOW_COOLDOWN) < 0) {
        long remaining = RUN_NOW_COOLDOWN.toSeconds() - Duration.between(last, now).toSeconds();
        throw new EscalationCooldownException(remaining);
    }

    if (!lastManualEscalation.compareAndSet(last, now)) {
        Instant updatedLast = lastManualEscalation.get();
        long remaining = RUN_NOW_COOLDOWN.toSeconds() - Duration.between(updatedLast, now).toSeconds();
        if (remaining > 0) {
            throw new EscalationCooldownException(remaining);
        }
    }

    return escalationUseCase.execute(principal.getName());
}
```

```java
// common/domain/EscalationCooldownException.java
public class EscalationCooldownException extends RuntimeException {
    public EscalationCooldownException(long remainingSeconds) {
        super(String.format("Escalação manual em cooldown. Aguarde %ds antes de tentar novamente.",
            remainingSeconds));
    }
}
```

```java
// GlobalExceptionHandler.java — adicionar handler
@ExceptionHandler(EscalationCooldownException.class)
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public Map<String, String> handle(EscalationCooldownException ex) {
    return Map.of("message", ex.getMessage());
}
```

**Nota**: `Principal` é promovido a parâmetro de `runNow()` — necessário para SEC-077 (Decisão 3). `EscalationUseCase.execute()` recebe `String triggeredBy` (ver Decisão 3).

**Justificativa**: padrão `compareAndSet` garante atomicidade sem lock — idêntico ao `AlertThresholdController`. Bucket4j foi considerado e rejeitado: adiciona dependência maven para um único endpoint ADMIN-only com 53 usuários totais, dos quais poucos têm role ADMIN.

---

### Decisão 2 — Tech Debt SEC-076: validação semântica de `classifierValue`

**Problema**: `CreateSlaRuleRequest.classifierValue` aceita qualquer string `@Size(max=30)`. Um ADMIN pode persistir `classifierValue = "INVALIDO"` — a regra SLA fica ativa mas silenciosamente inoperante (nunca faz match em `nc.getSeverity().name().equalsIgnoreCase(...)`).

**Decisão**: validar no use case, após deserialização, que `classifierValue` pertence ao enum correspondente ao `classifierField`. A validação usa `Enum.valueOf()` com tratamento de `IllegalArgumentException`.

```java
// CreateSlaRuleUseCase.java — adicionar método privado e chamá-lo em execute()
private void validateClassifierValue(SlaClassifierField field, String value) {
    try {
        switch (field) {
            case SEVERITY -> NcSeverity.valueOf(value.toUpperCase());
            case PRIORITY -> WorkOrderPriority.valueOf(value.toUpperCase());
        }
    } catch (IllegalArgumentException ex) {
        String allowed = switch (field) {
            case SEVERITY -> Arrays.stream(NcSeverity.values())
                .map(Enum::name).collect(Collectors.joining(", "));
            case PRIORITY -> Arrays.stream(WorkOrderPriority.values())
                .map(Enum::name).collect(Collectors.joining(", "));
        };
        throw new InvalidClassifierValueException(value, field.name(), allowed);
    }
}
```

```java
// common/domain/InvalidClassifierValueException.java
public class InvalidClassifierValueException extends RuntimeException {
    public InvalidClassifierValueException(String value, String field, String allowed) {
        super(String.format("Valor '%s' inválido para %s. Valores permitidos: %s",
            value, field, allowed));
    }
}
```

```java
// GlobalExceptionHandler.java — adicionar handler
@ExceptionHandler(InvalidClassifierValueException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public Map<String, String> handle(InvalidClassifierValueException ex) {
    return Map.of("message", ex.getMessage());
}
```

**Chamada no use case** (em `execute()`, após `@Valid` do request):

```java
validateClassifierValue(request.classifierField(), request.classifierValue());
```

**Por que no use case e não no DTO?** A validação cruzada (field + value) depende de dois campos do mesmo DTO — impossível com uma única annotation `@NotNull`/`@Size`. Uma `@Constraint` customizada seria possível mas viola o princípio de simplicidade: a lógica ficaria num validador separado sem visibilidade clara. O use case é o lugar natural para regras de negócio que envolvem combinações de campos.

**Normalização**: o use case normaliza `classifierValue` para uppercase antes de persistir (`request.classifierValue().toUpperCase()`), garantindo consistência com `.name()` dos enums.

---

### Decisão 3 — Tech Debt SEC-077: auditoria da execução manual de `/run-now`

**Problema**: `SlaRuleController.runNow()` não passa `Principal` ao use case. Execução agendada (`EscalationJob`) registra `auditService.log("system", ...)` por entidade afetada, mas não há log da execução manual em si.

**Decisão**: adicionar `AuditAction.ESCALATION_RUN_MANUAL` e registrar no controller (não no use case, para não poluir o `EscalationUseCase` com lógica de apresentação).

```java
// AuditAction.java — adicionar
ESCALATION_RUN_MANUAL
```

```java
// EscalationUseCase.java — alterar assinatura de execute()
// ANTES: public EscalationRunResponse execute()
// DEPOIS:
public EscalationRunResponse execute(String triggeredBy) { ... }
```

O `EscalationJob` chama `escalationUseCase.execute("system")`. O controller chama `escalationUseCase.execute(principal.getName())`.

**Auditoria no controller** (após a chamada ao use case):

```java
EscalationRunResponse result = escalationUseCase.execute(principal.getName());
auditService.log(
    principal.getName(),
    AuditAction.ESCALATION_RUN_MANUAL,
    "SlaRule",
    "all",
    Map.of("breachedNcs",        String.valueOf(result.breachedNcs()),
           "breachedWorkOrders", String.valueOf(result.breachedWorkOrders()))
);
return result;
```

**Justificativa**: colocar o log no controller (não no use case) separa claramente "quem disparou" (apresentação) de "o que aconteceu" (negócio). O `EscalationJob` não tem usuário — usa `"system"` como convenção já estabelecida no projeto.

---

### Decisão 4 — escalateByEmail: envio assíncrono via `EmailEscalationService`

**Problema (gap Maiana)**: `EscalationUseCase` não implementa envio de email quando `slaRule.escalateByEmail = true`. O ADR-037 Decisão 3 previa `EmailEscalationService` mas a implementação foi diferida.

**Decisão**: criar `EmailEscalationService` no pacote `common/application/` seguindo o mesmo padrão de `QmsEmailService` (já existente em `qms/application/usecase/`): `@Service`, `@Async`, `JavaMailSender` com `@Autowired(required = false)`, flag `${mail.enabled:false}`.

```java
// common/application/EmailEscalationService.java
@Service
public class EmailEscalationService {

    private static final Logger log = LoggerFactory.getLogger(EmailEscalationService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final UserRepository userRepository;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:noreply@industrialhub.com}")
    private String fromAddress;

    public EmailEscalationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Async
    public void notifySlaBreached(String entityLabel, String entityId,
                                   String entityTitle, int slaHours) {
        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] SLA vencido: {} '{}' ({}h)", entityLabel, entityTitle, slaHours);
            return;
        }

        List<String> emails = userRepository
            .findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN))
            .stream()
            .map(User::getEmail)
            .filter(e -> e != null && !e.isBlank())
            .toList();

        emails.forEach(email -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(email);
                msg.setSubject(String.format("[MSB] SLA Vencido: %s '%s'", entityLabel, entityTitle));
                msg.setText(String.format(
                    "O SLA da %s '%s' (ID: %s) foi ultrapassado.\n\n" +
                    "Prazo configurado: %dh\n\n" +
                    "Acesse o sistema para tomar as ações necessárias.",
                    entityLabel, entityTitle, entityId, slaHours
                ));
                mailSender.send(msg);
            } catch (Exception e) {
                log.warn("Falha ao enviar email de SLA vencido para {}: {}", email, e.getMessage());
            }
        });
    }
}
```

**Integração no `EscalationUseCase`**: injetar `EmailEscalationService` e chamar após marcar `slaBreached = true`:

```java
// EscalationUseCase.java — dentro do loop, após nc.setSlaBreachedAt(now)
if (rule.isEscalateByEmail()) {
    emailEscalationService.notifySlaBreached(
        "NC", nc.getId().toString(), nc.getTitle(), rule.getSlaHours());
}
```

```java
// idem para WorkOrder:
if (rule.isEscalateByEmail()) {
    emailEscalationService.notifySlaBreached(
        "OS", wo.getId().toString(), wo.getTitle(), rule.getSlaHours());
}
```

**Tratamento de falha**: `@Async` + try-catch com `log.warn` — falha no email não interrompe o loop de escalação nem faz rollback da transação principal. Alinhado com `QmsEmailService`.

**Habilitação por feature flag** (`mail.enabled=false` em dev/test): sem `JavaMailSender` no contexto, o método retorna imediatamente com log INFO — sem NPE, sem erro de startup.

---

### Decisão 5 — Multi-Plant: `PlantContext` com ThreadLocal e `PlantContextFilter`

O ADR-020 define a entidade `Plant`, a tabela `user_plant` e a decisão de usar `PlantContext` ThreadLocal. Este ADR detalha a implementação.

```java
// common/application/PlantContext.java
public class PlantContext {

    private static final ThreadLocal<List<UUID>> CURRENT_PLANT_IDS = new ThreadLocal<>();

    public static void set(List<UUID> plantIds) {
        CURRENT_PLANT_IDS.set(plantIds != null ? List.copyOf(plantIds) : List.of());
    }

    public static List<UUID> current() {
        List<UUID> ids = CURRENT_PLANT_IDS.get();
        return ids != null ? ids : List.of();
    }

    public static boolean isAdminContext() {
        return CURRENT_PLANT_IDS.get() == null; // null = ADMIN sem filtro
    }

    public static void clear() {
        CURRENT_PLANT_IDS.remove();
    }
}
```

```java
// common/presentation/PlantContextFilter.java
@Component
@RequiredArgsConstructor
public class PlantContextFilter extends OncePerRequestFilter {

    private final UserPlantRepository userPlantRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                String username = auth.getName();
                userRepository.findByUsername(username).ifPresent(user -> {
                    if (user.getRole() == Role.ADMIN) {
                        PlantContext.set(null); // ADMIN vê tudo — null = sem filtro
                    } else {
                        List<UUID> plantIds = userPlantRepository
                            .findPlantIdsByUsername(username);
                        PlantContext.set(plantIds);
                    }
                });
            }
            chain.doFilter(request, response);
        } finally {
            PlantContext.clear(); // obrigatório: evita vazamento entre requisições no pool de threads
        }
    }
}
```

**Registro no `SecurityConfig`**: `PlantContextFilter` é adicionado após `JwtAuthFilter` na cadeia de filtros (`addFilterAfter`).

**Por que null para ADMIN e não `List.of(allPlantIds)`?** ADMINs podem criar novas plantas entre requisições. Consultar IDs de todas as plantas no filter seria uma query extra por request. A semântica `null = sem filtro` é mais eficiente e robusta.

---

### Decisão 6 — Multi-Plant: queries com filtro de planta

Os use cases de listagem consultam `PlantContext.current()` para aplicar filtro. Padrão condicional JPQL (mesmo padrão de `slaBreached` em ADR-037 Decisão 4 e `shiftId` em ADR-034):

```java
// Nos use cases de listagem — antes da query
boolean isAdmin = PlantContext.isAdminContext();
List<UUID> plantIds = PlantContext.current();

// Exemplo em GetEquipmentListUseCase.java
List<Equipment> equipment = isAdmin
    ? equipmentRepository.findAllActive()
    : equipmentRepository.findActiveByPlantIds(plantIds);
```

```java
// EquipmentRepository.java — método adicional
@Query("""
    SELECT e FROM Equipment e
    WHERE e.active = true
      AND e.plant.id IN :plantIds
    ORDER BY e.name ASC
    """)
List<Equipment> findActiveByPlantIds(@Param("plantIds") List<UUID> plantIds);
```

Para use cases que já têm filtros múltiplos (ex: `NonConformanceRepository.findAllFiltered`), adicionar condição extra:

```jpql
AND (:isAdmin = true OR e.plant.id IN :plantIds)
```

**Parâmetro `isAdmin` em JPQL**: não há tipo booleano literal em JPQL — usar `@Param("isAdmin") boolean isAdmin` com `= true` funciona em Hibernate 6 (Spring Boot 3.4.x). Alternativa: duas queries separadas com `if (isAdmin)` no use case — mais verboso mas mais explícito. **Decisão**: usar duas queries separadas quando possível (clareza), condicional JPQL apenas quando a query já tem muitos filtros opcionais.

---

### Decisão 7 — Multi-Plant: migração retrocompatível e seed

Alinhado com ADR-020 Decisão 2. A estratégia de migração via `SchemaInitializer` (padrão do projeto, sem Flyway):

1. `Plant` criada na inicialização; seed `{ code: "HQ", name: "Matriz", isDefault: true }` no `DataInitializer`
2. `Equipment`, `NonConformance`, `ImportBatch` ganham `@ManyToOne @JoinColumn(name = "plant_id", nullable = true)` na entidade — nullable para compatibilidade inicial
3. `DataInitializer` executa UPDATE após o seed de `Plant`: `UPDATE equipment SET plant_id = <hqId> WHERE plant_id IS NULL` (e idem para NC e ImportBatch)
4. Após garantir que todos os registros têm `plant_id`, a coluna pode ser declarada `nullable = false` em futuro sprint — por ora, nullable evita falhas em ambientes com dados legados

**Associação User-Plant no seed**: todos os usuários seed (`admin`, `supervisor`, `operator`) são vinculados à planta HQ no `DataInitializer`.

---

### Decisão 8 — Multi-Plant: package completo

```
common/
├── domain/
│   └── Plant.java                  (ADR-020 Decisão 1)
├── application/
│   ├── PlantContext.java            (Decisão 5)
│   ├── EmailEscalationService.java  (Decisão 4)
│   ├── dto/
│   │   ├── CreatePlantRequest.java
│   │   ├── UpdatePlantRequest.java
│   │   ├── PlantResponse.java
│   │   └── AssignUserPlantsRequest.java  — { List<UUID> plantIds }
│   └── usecase/
│       ├── CreatePlantUseCase.java
│       ├── GetPlantListUseCase.java
│       ├── UpdatePlantUseCase.java
│       ├── DeactivatePlantUseCase.java
│       └── AssignUserPlantsUseCase.java
├── infrastructure/
│   ├── PlantRepository.java
│   └── UserPlantRepository.java
└── presentation/
    ├── PlantController.java       (/api/v1/admin/plants)
    └── PlantContextFilter.java    (OncePerRequestFilter)
```

```java
// CreatePlantRequest.java
public record CreatePlantRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) String address,
    @Size(max = 50) String timezone   // ex: "America/Sao_Paulo"
) {}

// PlantResponse.java
public record PlantResponse(
    UUID id,
    String code,
    String name,
    String address,
    String timezone,
    boolean active,
    boolean isDefault
) {}

// AssignUserPlantsRequest.java
public record AssignUserPlantsRequest(
    @NotNull List<UUID> plantIds   // lista vazia = remover todas as associações
) {}
```

**`UserPlant` entity** (conforme ADR-020 Decisão 3):

```java
@Entity
@Table(name = "user_plant",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "plant_id"}))
public class UserPlant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;
}
```

---

### Decisão 9 — Multi-Plant: frontend selector de planta

**ADMIN**: dropdown no nav com todas as plantas ativas; seleção persiste em `localStorage` sob chave `msb_selected_plant_id`. Quando planta selecionada, o `PlantService` adiciona header `X-Plant-Id` em todas as requisições via HTTP interceptor. **Nota**: o backend resolve o escopo via JWT (role ADMIN = sem filtro), então o header `X-Plant-Id` é usado apenas para o query param `?plantId=<uuid>` nos endpoints de listagem — não altera o escopo de segurança.

```typescript
// shared/plant/plant.service.ts
export interface Plant {
  id: string;
  code: string;
  name: string;
  active: boolean;
  isDefault: boolean;
}

@Injectable({ providedIn: 'root' })
export class PlantService {
  private readonly STORAGE_KEY = 'msb_selected_plant_id';

  readonly selectedPlantId = signal<string | null>(
    localStorage.getItem(this.STORAGE_KEY)
  );

  selectPlant(id: string | null): void {
    this.selectedPlantId.set(id);
    if (id) {
      localStorage.setItem(this.STORAGE_KEY, id);
    } else {
      localStorage.removeItem(this.STORAGE_KEY);
    }
  }
}
```

**OPERATOR/SUPERVISOR**: se vinculado a 1 planta, nome da planta exibido no nav sem dropdown. Se 2+ plantas, exibe dropdown restrito às suas plantas.

**Chip de planta**: componente `PlantChipComponent` (standalone, OnPush) exibido em cards de NC, OS e equipamentos; oculto quando o usuário tem acesso a apenas 1 planta (sinal `hideChip = computed(() => userPlants().length <= 1)`).

---

### Contrato de API

#### Endpoints novos — módulo Plant (ADR-020 Decisão 4, confirmados)

| Método | Endpoint | Auth | Status HTTP | Descrição |
|--------|----------|------|-------------|-----------|
| POST | /api/v1/admin/plants | ADMIN | 201 `PlantResponse` | Criar planta |
| GET | /api/v1/admin/plants | ADMIN | 200 `List<PlantResponse>` | Listar plantas ativas |
| PUT | /api/v1/admin/plants/{id} | ADMIN | 200 `PlantResponse` | Atualizar nome/endereço/timezone |
| PUT | /api/v1/admin/plants/{id}/deactivate | ADMIN | 204 | Desativar planta (soft-delete) |
| PUT | /api/v1/admin/users/{id}/plants | ADMIN | 200 `PlantResponse[]` | Vincular usuário a plantas |

#### Filtros adicionados a endpoints existentes

| Método | Endpoint | Parâmetro novo | Descrição |
|--------|----------|----------------|-----------|
| GET | /api/v1/maintenance/equipment | `plantId` (UUID, opcional) | ADMIN: filtra por planta; OPERATOR/SUPERVISOR: ignorado (escopo via JWT) |
| GET | /api/v1/qms/non-conformances | `plantId` (UUID, opcional) | idem |
| GET | /api/v1/maintenance/work-orders | `plantId` (UUID, opcional) | idem |
| GET | /api/v1/oee/import-batches | `plantId` (UUID, opcional) | idem |

#### Migrations de banco (acumuladas no Sprint 23)

1. Nova tabela `plant` (conforme ADR-020 Decisão 1)
2. Nova tabela `user_plant` (conforme ADR-020 Decisão 3)
3. `ALTER TABLE equipment ADD COLUMN plant_id UUID REFERENCES plant(id)` (nullable inicialmente)
4. `ALTER TABLE non_conformance ADD COLUMN plant_id UUID REFERENCES plant(id)` (nullable inicialmente)
5. `ALTER TABLE import_batch ADD COLUMN plant_id UUID REFERENCES plant(id)` (nullable inicialmente)
6. `UPDATE equipment SET plant_id = (SELECT id FROM plant WHERE is_default = true) WHERE plant_id IS NULL` (idem NC e ImportBatch)
7. `AuditAction.ESCALATION_RUN_MANUAL` adicionado ao enum (sem migration de DB — enum Java)

---

### Consequências

✅ SEC-075: cooldown `AtomicReference<Instant>` elimina abuso do `/run-now` sem dependência externa; padrão já conhecido pelo time (mesmo que `evaluateNow`)
✅ SEC-076: validação semântica de `classifierValue` no use case elimina regras SLA silenciosamente inoperantes; mensagem de erro lista valores válidos
✅ SEC-077: `ESCALATION_RUN_MANUAL` auditado com username do ADMIN que disparou + contagens de entidades afetadas; `EscalationJob` continua registrando com `"system"`
✅ `EmailEscalationService` segue exatamente o padrão de `QmsEmailService` — curva de aprendizado zero; feature flag `mail.enabled` garante ambiente dev sem SMTP configurado
✅ `PlantContext` ThreadLocal com `clear()` em `finally` garante isolamento por request sem vazamento entre threads do pool
✅ Migração retrocompatível: colunas `plant_id` nullable inicialmente — sem falha em ambientes com dados legados; seed HQ garante que todos os registros existentes terão planta associada após `DataInitializer`
✅ `PlantChipComponent` standalone com signal `hideChip` — zero rendering desnecessário para usuários de planta única
⚠️ `PlantContextFilter` adiciona 1 query de banco por request (busca de `user_plant`) — com 53 usuários, sem impacto de performance; se volume crescer, considerar cache com `@Cacheable` em `userPlantRepository.findPlantIdsByUsername()`
⚠️ Dois use cases com assinaturas alteradas: `EscalationUseCase.execute(String triggeredBy)` — `EscalationJob` deve ser atualizado para passar `"system"` imediatamente
⚠️ Colunas `plant_id` nullable: se a query de UPDATE no `DataInitializer` falhar silenciosamente (ex: planta default não encontrada no seed), registros ficam sem planta associada e OPERATOR/SUPERVISOR veriam dados em branco; adicionar `log.error` e assertion após o UPDATE
⚠️ `classifierValue.toUpperCase()` normalizado antes de persistir — regras existentes com lowercase no banco (se houver) precisam de data fix: `UPDATE sla_rule SET classifier_value = UPPER(classifier_value)`
⚠️ Frontend `PlantService` persiste `plant_id` em `localStorage` — se planta for desativada, seleção persistida ficará inválida; o service deve validar contra a lista de plantas ativas ao carregar e resetar para null se não encontrar
