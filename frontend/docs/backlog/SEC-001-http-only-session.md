# SEC-001 — Migrar sessão para cookies HttpOnly

- Tipo: estudo técnico e implementação de segurança
- Prioridade sugerida: alta
- Estado: planejado
- Risco relacionado: [`RISK-SESSION-001`](../security/session-storage-risk.md)

## Objetivo

Retirar do JavaScript todas as credenciais que não precisam ser lidas pelo frontend, iniciando pelo refresh token. Avaliar manter o access token apenas em memória ou também em cookie, conforme o modelo de CSRF escolhido.

## Decisões que o estudo deve produzir

1. Contrato dos endpoints de login, refresh e logout sem refresh token no corpo JSON.
2. Atributos definitivos do cookie: `HttpOnly`, `Secure`, `SameSite`, `Path`, `Domain` e expiração.
3. Estratégia CSRF para requisições mutáveis, incluindo token antifalsificação e validação de `Origin`/`Referer`.
4. Política CORS com credenciais e origens explícitas para desenvolvimento e produção.
5. Comportamento entre abas, "manter conectado", rotação, janela de tolerância e logout global.
6. Plano de migração e rollback sem invalidar silenciosamente sessões existentes.

## Critérios de aceite da futura implementação

- refresh token ausente de `localStorage`, `sessionStorage`, respostas JSON e logs;
- cookie emitido com `HttpOnly`, `Secure` e política `SameSite` documentada;
- proteção CSRF automatizada para todos os métodos mutáveis;
- rotação, reutilização, concorrência entre abas, logout e `rememberMe` cobertos por integração/E2E;
- TPS permanece em 100% com o novo transporte de credenciais;
- documentação operacional e de rollback publicada.
