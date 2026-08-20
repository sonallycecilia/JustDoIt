# Desempenho

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
| Latência P95 da API | NÃO IMPLEMENTADA | — | Requisições medidas (não coletadas) | — | Não definido |
| Bloqueio de cronômetro concorrente | NÃO EXECUTADA | — bloqueios corretos | 130 disputas esperadas | — | 100% |

O teste do cronômetro executa 3 cenários, 10 acionamentos por cenário e 5 repetições. O denominador esperado é (9 + 9 + 8) × 5 = 130. O ambiente usa MockMvc e H2 em modo MySQL, não uma implantação produtiva.
