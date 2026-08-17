# Segurança

> Gerado automaticamente.  
> Commit: `b5774b3b68a5d8823790c0dac5530fb75b999e70`  
> Data UTC: `2026-08-10T01:26:16Z`  
> Ambiente: Local limpo / Windows 11 / Temurin 21.0.12+8 / H2 modo MySQL

| Métrica | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Bloqueio de acesso não autorizado | APROVADA | 36 bloqueios | 36 requisições inválidas | 100,00% | 100% |
| Validação do corpus malicioso | APROVADA | 144 rejeições | 144 casos esperados | 100,00% | 100% |
| Proteção do ciclo de sessão | PARCIAL / NÃO AGREGADA | — | Cenários ainda não formalizados | — | Não definido |

O denominador de acesso é 4 endpoints × 9 condições sem credencial válida = 36. O corpus de entrada esperado soma 36 Auth + 84 Task + 24 Notification = 144. A proteção de sessão possui testes individuais, mas ainda não dispõe de fórmula agregadora estável.
