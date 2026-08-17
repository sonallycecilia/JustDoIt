# Desempenho

> Gerado automaticamente.  
> Commit: `b5774b3b68a5d8823790c0dac5530fb75b999e70`  
> Data UTC: `2026-08-10T01:26:16Z`  
> Ambiente: Local limpo / Windows 11 / Temurin 21.0.12+8 / H2 modo MySQL

| Métrica | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Latência P95 da API | NÃO IMPLEMENTADA | — | Requisições medidas (não coletadas) | — | Não definido |
| Bloqueio de cronômetro concorrente | APROVADA | 130 bloqueios corretos | 130 disputas esperadas | 100,00% | 100% |

O teste do cronômetro executa 3 cenários, 10 acionamentos por cenário e 5 repetições. O denominador esperado é (9 + 9 + 8) × 5 = 130. O ambiente usa MockMvc e H2 em modo MySQL, não uma implantação produtiva.
