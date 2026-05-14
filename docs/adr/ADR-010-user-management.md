## ADR-010: User Management — Admin CRUD + Self-Service Password
**Status**: Aprovado
**Data**: 2026-05-13
**US relacionadas**: US-037, US-038, US-039

### Contexto

Os 53 usuários do sistema são hoje gerenciados apenas via seed (`DataInitializer`). Não existe UI para ADMIN criar, desativar ou alterar o role de um usuário. Não existe endpoint de troca de senha para o próprio usuário. A entidade `User` já está em `common/auth/domain/` com os campos: `id`, `username`, `password`, `role`, `active`, `email`.

---

### Decisão 1 — Package e classes novas

Nenhum package novo necessário — os casos de uso de admin entram em `common/auth/application/usecase/` e o controller de admin em `common/auth/presentation/`.

```
common/auth/
├── application/
│   ├── dto/
│   │   ├── CreateUserRequest.java        (novo)
│   │   ├── UpdateUserRoleRequest.java    (novo)
│   │   ├── ChangePasswordRequest.java    (novo)
│   │   └── UserResponse.java             (novo)
│   └── usecase/
│       ├── CreateUserUseCase.java         (novo)
│       ├── GetUserListUseCase.java        (novo)
│       ├── UpdateUserRoleUseCase.java     (novo)
│       ├── DeactivateUserUseCase.java     (novo)
│       └── ChangeOwnPasswordUseCase.java  (novo)
└── presentation/
    ├── AuthController.java               (existente)
    └── UserController.java               (novo — /api/v1/admin/users + /api/v1/users/me)
```

---

### Decisão 2 — Campo `mustChangePassword` na entidade `User`

Campo booleano adicionado via migration:

```java
@Column(nullable = false)
private boolean mustChangePassword = false;
```

**Quando é `true`**: ADMIN cria o usuário com senha temporária. Na primeira resposta autenticada, o frontend detecta o flag e redireciona para a tela de troca de senha obrigatória.

**JWT inclui o claim `mustChangePassword`** no payload para o frontend detectar sem chamada adicional. Após troca bem-sucedida, o campo é zerado e um novo JWT sem o claim é emitido.

---

### Decisão 3 — Endpoints de administração de usuários

| Método | Endpoint | Auth | Retorno |
|--------|----------|------|---------|
| GET | /api/v1/admin/users | ADMIN | `List<UserResponse>` |
| POST | /api/v1/admin/users | ADMIN | `201 UserResponse` |
| PUT | /api/v1/admin/users/{id}/role | ADMIN | `200 UserResponse` |
| PUT | /api/v1/admin/users/{id}/deactivate | ADMIN | `204` |
| PUT | /api/v1/admin/users/{id}/reactivate | ADMIN | `204` |
| PUT | /api/v1/users/me/password | qualquer autenticado | `200` |

**Sem DELETE físico** — desativação é `active = false`. Histórico de OSs/NCs abertas pelo usuário preservado.

**Sem endpoint de reset de senha por ADMIN** — ADMIN cria usuário com senha temporária e seta `mustChangePassword = true`; é responsabilidade do usuário trocar no primeiro login.

---

### Decisão 4 — Validação de senha

Regras aplicadas nos use cases `CreateUserUseCase` e `ChangeOwnPasswordUseCase`:
- Mínimo 8 caracteres
- Pelo menos 1 letra maiúscula
- Pelo menos 1 dígito
- Validação via `@Pattern` no DTO ou método utilitário em `PasswordValidator`

Violação retorna `400` com `{ "message": "Senha deve ter no mínimo 8 caracteres, 1 maiúscula e 1 dígito" }`.

---

### Decisão 5 — `UserResponse` sem expor password

```java
public record UserResponse(
    UUID id,
    String username,
    String email,
    Role role,
    boolean active,
    boolean mustChangePassword
) {}
```

`password` nunca serializado em nenhum DTO de resposta.

---

### Decisão 6 — Frontend: rota `/admin/users` protegida por role

Guard de role no frontend verifica `role === 'ADMIN'`; rota não aparece no nav para OPERATOR/SUPERVISOR. Tentativa de acesso direto à URL retorna para `/dashboard`.

---

### Contrato de API

```
GET  /api/v1/admin/users                      → ADMIN → List<UserResponse>
POST /api/v1/admin/users                      → ADMIN → 201 UserResponse | 409 (username duplicado)
PUT  /api/v1/admin/users/{id}/role            → ADMIN → 200 UserResponse | 404
PUT  /api/v1/admin/users/{id}/deactivate      → ADMIN → 204 | 404 | 422 (último ADMIN ativo)
PUT  /api/v1/admin/users/{id}/reactivate      → ADMIN → 204 | 404
PUT  /api/v1/users/me/password                → autenticado → 200 | 400 (senha inválida)
```

**Guard de integridade**: `DeactivateUserUseCase` verifica se o usuário a ser desativado é o último ADMIN ativo do sistema; se for, retorna `422` com `{ "message": "Não é possível desativar o único administrador ativo" }`.

---

### Consequências
✅ Nenhum package novo — código entra em `common/auth/` já existente
✅ `mustChangePassword` no JWT elimina chamada extra do frontend
✅ Soft-delete preserva integridade referencial com OSs e NCs
⚠️ Migration necessária para adicionar `must_change_password` em `users` — adicionar coluna com default `false` (sem downtime)
⚠️ Guard "último ADMIN ativo" deve ser testado como edge case no use case
