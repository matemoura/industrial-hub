## ADR-020: Multi-Plant Support — Dimensão de Planta/Unidade
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-063, US-064

### Contexto

O MSB opera atualmente em uma única planta. A expansão para outras unidades requer que OEE, equipamentos, NCs e ordens de serviço sejam isolados por planta, mantendo visibilidade consolidada para administradores. Esta ADR introduz a dimensão `Plant` de forma retrocompatível — os dados existentes pertencem à planta default.

---

### Decisão 1 — Entidade `Plant`

```java
@Entity
@Table(name = "plant")
public class Plant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true, nullable = false, length = 50)
    private String code;         // ex: "SP-01", "RJ-01"
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 200)
    private String address;
    private String timezone;     // ex: "America/Sao_Paulo"
    private boolean active = true;
    private boolean isDefault = false;  // apenas uma planta pode ser default
}
```

**Seed no `DataInitializer`**: planta default `{ code: "HQ", name: "Matriz", isDefault: true }`.

---

### Decisão 2 — Associação de `Plant` às entidades existentes

Todas as entidades de negócio ganham FK nullable para `Plant`. Registros existentes recebem a planta default via migration `UPDATE ... SET plant_id = (SELECT id FROM plant WHERE is_default = true)`.

```
Equipment    → plant_id (NOT NULL após migration)
NonConformance → plant_id (NOT NULL após migration)
WorkOrder    → plant_id (herdado do Equipment)
ImportBatch  → plant_id (NOT NULL após migration)
```

`WorkOrder.plant` derivado do `equipment.plant` na criação — não aceito no request body.

---

### Decisão 3 — Escopo de dados por usuário

**OPERATOR / SUPERVISOR**: veem apenas dados da(s) planta(s) às quais estão vinculados.

```java
// UserPlant — tabela de associação
@Entity
@Table(name = "user_plant")
public class UserPlant {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne private User user;
    @ManyToOne private Plant plant;
}
```

**ADMIN**: vê dados de todas as plantas (sem filtro de planta).

`PlantContextFilter` (Spring `OncePerRequestFilter`) resolve a planta do usuário autenticado e injeta em `PlantContext` (ThreadLocal) — use cases consultam `PlantContext.current()` para filtrar queries.

---

### Decisão 4 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| POST | /api/v1/admin/plants | ADMIN | criar planta |
| GET | /api/v1/admin/plants | ADMIN | listar plantas |
| PUT | /api/v1/admin/plants/{id} | ADMIN | atualizar |
| PUT | /api/v1/admin/plants/{id}/deactivate | ADMIN | desativar |
| PUT | /api/v1/admin/users/{id}/plants | ADMIN | vincular usuário a plantas |

Query param `?plantId=<uuid>` em endpoints de listagem permite que ADMINs filtrem explicitamente por planta.

---

### Decisão 5 — Frontend

- Selector de planta no nav (visível para ADMIN): dropdown para trocar contexto de planta; OPERATOR/SUPERVISOR veem apenas suas plantas
- Chip de planta em cards de NC, OS e equipamentos
- Rota `/admin/plants` (ADMIN): CRUD de plantas + associação de usuários a plantas

---

### Consequências
✅ Retrocompatível: dados existentes migrados para planta default automaticamente
✅ `PlantContext` ThreadLocal isolado — use cases não precisam de parâmetros extras
⚠️ Migration pesada: 4 tabelas ganham `plant_id NOT NULL` — executar em janela de manutenção
⚠️ Filtros de relatório e analytics devem respeitar `PlantContext` — revisar todos os use cases de leitura
