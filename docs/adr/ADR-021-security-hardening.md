## ADR-021: API Security Hardening — Rate Limiting, Headers e Proteções
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-065, US-066

### Contexto

O sistema está em produção com 53 usuários internos. Ataques de força bruta contra `/auth/login`, header injection e falta de Content Security Policy são riscos reais mesmo em ambiente interno. Esta ADR endurece a segurança da API e da SPA sem adicionar fricção para usuários legítimos.

---

### Decisão 1 — Rate Limiting no endpoint de login

**Biblioteca**: `bucket4j-spring-boot-starter` (compatível com Spring Boot 3.x).

```java
// LoginRateLimiter — interceptor por IP + username
// 5 tentativas por minuto por IP; bloqueio de 5 min após exceder
// 10 tentativas por hora por username
```

Resposta em caso de limite excedido: `429 Too Many Requests` com `Retry-After: 300` (segundos) e body `{ "message": "Muitas tentativas. Tente novamente em 5 minutos." }`.

Sem persistência de estado de rate limiting em banco — armazenado em memória (`Caffeine`). Reinicialização do servidor reseta contadores (aceitável para ambiente single-node).

---

### Decisão 2 — Security Headers

`SecurityHeadersFilter` (`OncePerRequestFilter`) adiciona em toda resposta:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

CSP `unsafe-inline` para styles necessário para Angular Material inline styles — aceito como compromisso.

---

### Decisão 3 — Proteções adicionais

**CORS**: restringir `allowedOrigins` via `application.properties`:
```properties
app.security.cors.allowed-origins=https://industrial-hub.msb.com.br
```
Em dev: `http://localhost:4200` (property separada por profile).

**JWT claims adicionais**: adicionar `jti` (JWT ID — UUID v4) para suporte futuro a revogação.

**Password attempt logging**: falha de login registrada no `AuditLog` (ADR-009) com action `LOGIN_FAILED` e `ipAddress` do request.

**Input size limit**: `server.tomcat.max-http-form-post-size=2MB` e `spring.servlet.multipart.max-file-size=10MB` (já definido em ADR-018) — rejeitar payloads gigantes antes do controller.

---

### Decisão 4 — Frontend: Content Security Policy e HTTPS

Angular build production com `--subresource-integrity` para SRI hashes nos scripts.

`HttpOnly` cookie para token JWT é out-of-scope (requer refactor completo de auth) — manter `localStorage` com CSP como mitigação.

Redirect HTTP → HTTPS configurado no nginx/proxy reverso (infraestrutura, fora do app).

---

### Consequências
✅ Rate limiting em memória — zero latência de banco, sem dependência de Redis
✅ Security headers via filter único — aplicado a todas as rotas automaticamente
✅ `jti` no JWT prepara terreno para revogação sem implementar blacklist agora
⚠️ Bucket4j em modo single-node — em cluster, cada instância tem contadores independentes; solução: Redis como storage (sprint futura)
⚠️ CSP `unsafe-inline` para styles é uma concessão; monitorar se Angular Material fornece alternativa
