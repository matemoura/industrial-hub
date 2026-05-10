## ADR-003: Banco de Dados
**Status**: Aprovado
**Data**: 2026-05-09

### Contexto
Dados estruturados, integração via Excel, sem requisitos de tempo real.

### Decisão
PostgreSQL 16 como banco único.

### Consequências
- Suporte nativo a JSON para dados semi-estruturados
- Um único banco para operar e fazer backup