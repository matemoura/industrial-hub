## ADR-001: Organização do Repositório
**Status**: Aprovado
**Data**: 2026-05-09

### Contexto
Time pequeno (1 desenvolvedor + agentes Claude Code). Necessidade de visibilidade total do projeto pelos agentes.

### Decisão
Monorepo único com separação por pastas: apps/, packages/, infra/, docs/

### Consequências
- Agentes conseguem ver frontend e backend simultaneamente
- Um único clone e CI/CD
