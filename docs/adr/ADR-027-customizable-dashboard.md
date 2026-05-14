## ADR-027: Customizable Dashboard — Widgets por Usuário
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-077, US-078

### Contexto

O dashboard atual (US-030) mostra 6 cards fixos para todos os usuários. SUPERVISOR de produção quer ver OEE e NCs. SUPERVISOR de manutenção quer MTTR e OSs. ADMIN quer ver tudo. Um layout de widgets personalizável por usuário elimina o ruído e aumenta o foco.

---

### Decisão 1 — Persistência de layout: `UserDashboardConfig`

```java
@Entity
@Table(name = "user_dashboard_config")
public class UserDashboardConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String widgetsJson; // JSON array de WidgetConfig
}
```

`widgetsJson` armazena o layout serializado como JSON:

```json
[
  { "id": "oee-avg", "col": 0, "row": 0, "cols": 2, "rows": 1 },
  { "id": "nc-open", "col": 2, "row": 0, "cols": 1, "rows": 1 },
  { "id": "mttr-global", "col": 0, "row": 1, "cols": 1, "rows": 1 }
]
```

**Sem tabela de widget catalog** — catálogo é hardcoded no frontend (evitar over-engineering). Backend apenas persiste e retorna o JSON opaco.

---

### Decisão 2 — Widgets disponíveis

| Widget ID | Título | Dados | Role mínimo |
|-----------|--------|-------|-------------|
| `oee-avg` | OEE Médio (30d) | `/kpi/summary.oeeAvgLast30Days` | OPERATOR |
| `nc-open` | NCs Abertas | `/kpi/summary.totalNcOpen` | OPERATOR |
| `nc-critical` | NCs Críticas | `/kpi/summary.totalNcCritical` | OPERATOR |
| `wo-open` | OSs Abertas | `/kpi/summary.totalWorkOrdersOpen` | OPERATOR |
| `mttr-global` | MTTR Global | `/kpi/summary.mttrGlobalHours` | OPERATOR |
| `equipment-active` | Equip. Ativos | `/kpi/summary.activeEquipmentCount` | OPERATOR |
| `sla-breached` | SLA Vencidos | cálculo local em `/kpi/summary` | SUPERVISOR |
| `stock-critical` | Estoque Crítico | `/spare-parts?belowMin=true` (count) | SUPERVISOR |
| `oee-trend` | OEE Trend Chart | `/analytics/oee/trend?weeks=4` | SUPERVISOR |
| `nc-pareto` | NC Pareto Chart | `/analytics/nc/pareto?days=30` | SUPERVISOR |

---

### Decisão 3 — Layout engine: CSS Grid (sem biblioteca)

Grid de 3 colunas fixas, widgets com `colspan` 1–3 e `rowspan` 1–2. Draggable via HTML5 Drag-and-Drop API nativa — sem dependência de `angular-gridster` ou similar.

Modo de edição ativado por botão "Personalizar" no canto superior direito — em modo de edição, widgets exibem handles de drag e botão de remoção. Catálogo lateral mostra widgets não adicionados ao dashboard.

---

### Decisão 4 — Endpoints

| Método | Endpoint | Auth | Descrição |
|--------|----------|------|-----------|
| GET | /api/v1/users/me/dashboard | autenticado | retorna layout salvo (ou default) |
| PUT | /api/v1/users/me/dashboard | autenticado | salva layout |
| DELETE | /api/v1/users/me/dashboard | autenticado | reseta para layout default |

Layout default retornado quando usuário ainda não personalizou: todos os widgets disponíveis para o role do usuário em ordem padrão.

---

### Decisão 5 — Isolamento de dados de widget

Cada widget é um componente standalone que chama seu próprio endpoint. Sem "super-request" que busca todos os dados de uma vez — widgets carregam independentemente, falhas isoladas, sem loading global.

`KPI summary` (US-030) continua existindo para os widgets de card simples. Widgets de chart (oee-trend, nc-pareto) chamam os endpoints de analytics individualmente.

---

### Consequências
✅ JSON opaco no banco — schema de widget evolui sem migration
✅ Drag-and-drop nativo — sem biblioteca extra (90% dos casos de uso atendidos)
✅ Widgets isolados — falha de um não impede carregamento dos demais
⚠️ JSON opaco dificulta queries de admin sobre layouts — tradeoff intencional
⚠️ HTML5 D&D não funciona bem em touch (tablet) — investigar `Pointer Events API` como fallback
