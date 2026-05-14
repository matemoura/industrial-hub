## ADR-023: Progressive Web App (PWA) — Operação Offline e Instalação
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-069, US-070

### Contexto

Operadores no chão de fábrica usam tablets e celulares com conectividade instável. A ausência de suporte offline significa que quedas de rede interrompem o registro de ocorrências. Esta ADR transforma o frontend Angular em uma PWA com service worker, cache offline e instalação no dispositivo.

---

### Decisão 1 — `@angular/pwa`

```bash
ng add @angular/pwa
```

Gera automaticamente: `manifest.webmanifest`, `ngsw-config.json`, `ngsw-worker.js`, ícones PWA em múltiplas resoluções. Sem dependência manual — Angular CLI gerencia o service worker.

---

### Decisão 2 — Estratégia de cache por rota

`ngsw-config.json` configurado com:

```json
{
  "dataGroups": [
    {
      "name": "api-equipment",
      "urls": ["/api/v1/maintenance/equipment"],
      "cacheConfig": { "strategy": "freshness", "maxAge": "5m", "timeout": "3s" }
    },
    {
      "name": "api-kpi",
      "urls": ["/api/v1/kpi/summary"],
      "cacheConfig": { "strategy": "freshness", "maxAge": "10m", "timeout": "5s" }
    },
    {
      "name": "api-nc-list",
      "urls": ["/api/v1/qms/non-conformances"],
      "cacheConfig": { "strategy": "freshness", "maxAge": "2m", "timeout": "3s" }
    }
  ]
}
```

Estratégia `freshness`: tenta rede primeiro; se falhar (offline/timeout), serve cache. Endpoints de mutação (POST/PUT/DELETE) **não são cacheados** — falham com erro de rede offline.

---

### Decisão 3 — Offline queue para criação de NC

Para o caso crítico de "registrar NC sem conexão":

```typescript
// offline-queue.service.ts
// Armazena operações pendentes em IndexedDB
// Quando online volta, drena a fila via Background Sync API
```

**Escopo limitado**: apenas `POST /api/v1/qms/non-conformances` entra na fila offline. Outras mutações falham com snackbar "Sem conexão. Tente novamente."

`SwUpdate` service monitora atualizações do service worker — banner "Nova versão disponível. Recarregar?" quando update encontrado.

---

### Decisão 4 — Manifest e instalação

```json
// manifest.webmanifest
{
  "name": "Industrial Hub — MSB",
  "short_name": "IndustrialHub",
  "theme_color": "#0099B8",
  "background_color": "#F4F6F9",
  "display": "standalone",
  "start_url": "/dashboard",
  "icons": [{ "src": "assets/icons/icon-192.png", "sizes": "192x192" }]
}
```

Botão "Instalar App" no nav detecta `beforeinstallprompt` event — exibido apenas quando browser suporta e o app ainda não está instalado.

---

### Decisão 5 — Service Worker apenas em production build

`ngsw` desativado em `ng serve` (dev) para evitar cache stale durante desenvolvimento. CI/CD verifica `ng build --configuration=production` com `serviceWorker: true`.

---

### Consequências
✅ `@angular/pwa` gerencia versionamento do cache automaticamente (hash-based)
✅ Offline queue limitada a NC creation — escopo controlado, sem complexidade de sync bidirecional
✅ Instalação como app standalone melhora UX para operadores no tablet
⚠️ Service worker só ativo em production — testar com `http-server` local ou Docker
⚠️ Background Sync API não suportada em todos os browsers — fallback: retry manual via snackbar
