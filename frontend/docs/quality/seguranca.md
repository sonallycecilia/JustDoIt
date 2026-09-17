# Segurança

Última evidência conferida: CI do commit `1b4663b`, executado em
21/08/2026 UTC no GitHub Actions.

| Métrica | Situação | Resultado | Meta |
|---|---|---:|---:|
| Proteção do ciclo de sessão no frontend | APROVADA | 11/11 (100%) | 100% |

Os cenários cobrem renovação concorrente, token atualizado por outra aba,
rotação sem trocar o storage escolhido, refresh recusado, 429, 5xx, falha de
rede, rejeição depois da rotação, diferenças entre 401/403 e migração de sessão
legada.

O backend possui cinco cenários complementares, mas os resultados de frontend e
backend ainda são gerados por pipelines independentes. Não há uma execução E2E
única que permita declarar automaticamente 16/16 para o sistema completo.

Risco aberto: access e refresh tokens permanecem no Web Storage e podem ser
lidos por JavaScript executado na origem. Consulte
[RISK-SESSION-001](../security/session-storage-risk.md) e
[SEC-001](../backlog/SEC-001-http-only-session.md).
