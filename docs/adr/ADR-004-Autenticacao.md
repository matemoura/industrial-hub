## ADR-004: Autenticação
**Status**: Aprovado
**Data**: 2026-05-09

### Contexto
Sistema interno com 53 usuários. Sem domínio corporativo Microsoft/Google disponível.
Time pequeno sem capacidade de operar infraestrutura adicional.

### Opções consideradas
1. **Usuário e senha + JWT** — Prós: simples, sem dependência externa, controle total | Contras: gerenciamento manual de usuários
2. **Google/Microsoft SSO** — Prós: zero senha | Contras: requer domínio corporativo
3. **Keycloak** — Prós: robusto, SSO | Contras: mais infra para operar

### Decisão
Autenticação própria com usuário/senha + JWT.
- Backend Spring Boot gerencia geração e validação de tokens
- Senhas armazenadas com bcrypt
- Frontend Angular guarda token no localStorage e envia no header Authorization: Bearer
- 3 papéis de usuário: OPERATOR, SUPERVISOR, ADMIN

### Consequências
✅ Zero dependência externa
✅ Controle total sobre usuários e permissões
⚠️ Responsabilidade de gerenciar senhas com segurança (bcrypt obrigatório)
🔮 Revisitar se empresa adotar Microsoft 365 ou Google Workspace