## ADR-022: LGPD Compliance & Data Retention — Retenção e Anonimização
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-067, US-068

### Contexto

A LGPD (Lei 13.709/2018) exige que dados pessoais sejam tratados com finalidade definida, prazo de retenção claro e possibilidade de esquecimento. O sistema armazena `username`, `email` e `ipAddress` em `AuditLog`, além de dados de usuários. Esta ADR define a política de retenção e os mecanismos de anonimização.

---

### Decisão 1 — Política de retenção por entidade

| Entidade | Dados pessoais | Retenção | Ação após prazo |
|----------|---------------|----------|-----------------|
| `User` | username, email, password | Indefinida enquanto ativo; 2 anos após desativação | Anonimização |
| `AuditLog` | username, ipAddress, details | 5 anos (requisito de auditoria industrial) | Anonimização (manter evento, apagar PII) |
| `NonConformance` | reportedBy, closedBy | 5 anos | Substituir por `[anonimizado]` |
| `WorkOrder` | openedBy, assignedTo, closedBy | 5 anos | Substituir por `[anonimizado]` |
| `Notification` | username | 90 dias | Deletar fisicamente |

---

### Decisão 2 — Job de retenção: `DataRetentionJob`

```java
@Scheduled(cron = "0 0 2 1 * *", zone = "America/Sao_Paulo") // 1º de cada mês às 2h
public void runRetention() { dataRetentionUseCase.execute(); }
```

`DataRetentionUseCase.execute()`:
1. Usuários desativados há > 2 anos: `username → "[usuario-{id.toString().substring(0,8)}]"`, `email → null`, `password → BCrypt("*invalid*")`
2. `AuditLog` com `timestamp` > 5 anos: `username → "[anonimizado]"`, `ipAddress → null`, `details → null`
3. `NonConformance` com `reportedAt` > 5 anos: `reportedBy → "[anonimizado]"`, `closedBy → "[anonimizado]"`
4. `WorkOrder` com `openedAt` > 5 anos: `openedBy → "[anonimizado]"`, `assignedTo → null`, `closedBy → "[anonimizado]"`  
5. `Notification` com `createdAt` > 90 dias: `DELETE` físico
6. Todas as operações em uma única transação por entidade; rollback parcial logado sem abortar o job

---

### Decisão 3 — Direito ao Esquecimento (Art. 18 LGPD)

`POST /api/v1/admin/users/{id}/anonymize` (ADMIN):
- Executa anonimização imediata do usuário e seus dados nas entidades associadas
- Retorna `200 { "anonymized": true, "affectedEntities": { "auditLogs": N, "workOrders": M } }`
- Irreversível — confirmação dupla no frontend (dialog com texto "CONFIRMAR ANONIMIZAÇÃO")
- Registra a própria operação de anonimização no `AuditLog` com `action = USER_ANONYMIZED`

---

### Decisão 4 — LGPD Consent record

```java
// Tabela simples — consentimento do usuário aos termos de uso
@Entity
@Table(name = "consent_record")
public class ConsentRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String username;
    private String consentVersion;  // ex: "v1.0"
    private LocalDateTime acceptedAt;
    private String ipAddress;
}
```

Na criação de usuário (US-037), `consentVersion` é registrada automaticamente. Frontend exibe banner de consentimento no primeiro login de usuários criados antes desta sprint.

---

### Decisão 5 — Endpoints e frontend

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/admin/data-retention/preview | ADMIN | lista candidatos à anonimização |
| POST | /api/v1/admin/data-retention/run-now | ADMIN | executa job manualmente |
| POST | /api/v1/admin/users/{id}/anonymize | ADMIN | anonimização individual imediata |
| GET | /api/v1/users/me/data-export | autenticado | exporta dados pessoais do próprio usuário (JSON) |

Frontend: rota `/admin/lgpd` com tabela de candidatos à anonimização (paginada), botão "Executar Retenção Agora" (ADMIN), botão "Anonimizar" por linha com dialog de confirmação.

---

### Consequências
✅ Anonimização preserva integridade referencial — sem DELETE em cascata que quebraria histórico
✅ Direito ao esquecimento implementável via endpoint sem reescrever toda a lógica de negócio
✅ `DataRetentionJob` mensal — impacto mínimo em produção
⚠️ Anonimização irreversível — exigir confirmação dupla e logar como ação crítica de auditoria
⚠️ `consentVersion` hardcoded no `DataInitializer` — atualizar manualmente ao alterar termos de uso
