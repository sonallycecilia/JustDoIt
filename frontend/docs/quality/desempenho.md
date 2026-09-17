# Desempenho

Última evidência conferida: CI do commit `1b4663b`, executado em
21/08/2026 UTC no GitHub Actions.

| Métrica | Situação | Amostras | Resultado | Limite |
|---|---|---|---:|---:|
| LCP no percentil 75 | APROVADA | 846, 847, 913 e 1092 ms | **913 ms** | 2500 ms |

O cálculo usa *nearest rank*: posição `ceil(0,75 × N)` das quatro amostras
ordenadas. A medição usa Lighthouse desktop sobre o build de produção servido
localmente e cobre somente a página inicial. Ela não representa latência da API,
dispositivos móveis reais nem conexões lentas de usuários.

O build emite atualmente um aviso de bundle JavaScript acima de 500 kB. É um
ponto de otimização por divisão de código, não uma reprovação do gate atual.

[Consultar execução](https://github.com/sonallycecilia/justdoit-frontend/actions/runs/32437634036)
