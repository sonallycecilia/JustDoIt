# Segurança

> Gerado automaticamente.
>
> Commit: `d5bf645743c3854ba17d79ef3f5d66a8c838d4e7`
>
> Árvore de trabalho: com alterações não commitadas
>
> Execução: `pr-validation-backend-quality`
>
> Data UTC: `2026-08-20T14:17:40Z`
>
> Ambiente: Validação local / Windows / Java disponível / H2 em modo MySQL

| Métrica | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Bloqueio de acesso não autorizado | NÃO EXECUTADA | — bloqueios | 36 requisições inválidas | — | 100% |
| Validação do corpus malicioso | NÃO EXECUTADA | — rejeições | 144 casos esperados | — | 100% |
| Proteção do ciclo de sessão (backend) | IMPLEMENTADA / APROVADA | 5 cenários corretos | 5 cenários obrigatórios | 100,00% | 100% |
| TPS sistêmica | NÃO AGREGADA | — | 16 cenários esperados | — | 5/5 backend e 11/11 frontend |

O denominador de acesso é 4 endpoints × 9 condições sem credencial válida = 36. O corpus de entrada esperado soma 36 Auth + 84 Task + 24 Notification = 144.

A TPS usa cenários corretos ÷ cenários testados × 100. O backend exige 5/5: JWT expirado, rotação do refresh token, detecção de reutilização, logout e rate limiting. O frontend exige 11/11 no workflow próprio. O contrato esperado é 16/16, mas não é declarado aprovado sem uma execução sistêmica que agregue os dois artefatos.
