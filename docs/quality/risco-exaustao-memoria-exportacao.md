# Risco: exaustão de memória na exportação

Status técnico: **mitigado para execução em instância única**.

## Arquitetura implementada

`POST /me/exports?format=csv|json` responde imediatamente com `202 Accepted`,
`jobId`, URL de status e estado `PENDING`. O endpoint legado `GET /me/export`
também cria um job e não serializa mais o relatório na thread HTTP.

O worker usa páginas curtas do banco e escreve CSV/JSON diretamente em arquivo.
Em nenhum momento existe uma lista ou `StringBuilder` com todo o relatório. Cada
página encerra sua própria transação `readOnly`, permitindo liberar as entidades
antes da próxima leitura.

| Controle | Variável | Padrão |
|---|---|---:|
| Registros por página | `EXPORT_PAGE_SIZE` | 200 |
| Workers simultâneos | `EXPORT_MAX_CONCURRENCY` | 2 |
| Jobs aguardando | `EXPORT_QUEUE_CAPACITY` | 8 |
| Jobs ativos por usuário | `EXPORT_MAX_ACTIVE_PER_USER` | 1 |
| Registros por arquivo | `EXPORT_MAX_RECORDS` | 1.000.000 |
| Tamanho do arquivo | `EXPORT_MAX_FILE_SIZE_BYTES` | 512 MiB |
| Duração máxima | `EXPORT_MAX_DURATION` | 30 min |
| Validade do download | `EXPORT_DOWNLOAD_TTL` | 15 min |

Fila cheia e excesso por usuário retornam `429`; volume conhecido acima do teto
retorna `413`. Tamanho e timeout também são verificados durante a escrita, e um
arquivo parcial é removido em caso de falha.

## Entrega e expiração

O arquivo fica em `EXPORT_STORAGE_PATH`, fora da árvore pública da aplicação. Ao
concluir, o job grava registros, bytes, duração e expiração e cria uma notificação
`EXPORT_READY`. O link usa HMAC-SHA256 vinculado a `jobId`, usuário e instante de
expiração. Um agendador remove o arquivo expirado e marca o job como `EXPIRED`.

Defina `EXPORT_DOWNLOAD_SECRET` com um segredo exclusivo em produção e configure
`PUBLIC_TASK_API_URL` com a URL pública do task-service.

## Observabilidade

Prometheus e Grafana estão provisionados em `infra/docker-compose.yml`. O painel
**JustDoIt / Exportação assíncrona** mostra duração P95, memória JVM, ocupação do
pool e da fila, registros, bytes, erros, limites, timeouts e P95 dos demais
endpoints. Consulte [Observabilidade](../observabilidade.md) para iniciar o
ambiente.

## Evidências automatizadas

- `TaskExportStreamingWriterTest`: 10.000 registros com lote máximo de 100,
  limite de bytes e timeout durante a geração;
- `ExportAsyncConfigTest`: concorrência fixa, fila limitada e gauges do executor;
- `ExportJobServiceTest`: despacho imediato, limite por usuário e teto de linhas;
- `TaskExportWorkerTest`: conclusão, metadados, notificação e limpeza em timeout;
- `TemporaryDownloadTokenServiceTest` e `ExpiredExportCleanupJobTest`: vínculo,
  expiração do link e remoção do arquivo;
- `TaskExportIntegrationTest`: aceite `202`, isolamento do usuário e conteúdo
  paginado de CSV/JSON;
- `quality-tests/k6/export-concurrency.js`: carga simultânea com gates de P95 e
  ausência de falhas/timeouts no endpoint comum.

Exemplo do teste de concorrência, usando um JWT por usuário:

```powershell
$env:ACCESS_TOKENS='jwt-user-1,jwt-user-2,jwt-user-3,jwt-user-4'
k6 run quality-tests/k6/export-concurrency.js
```

## Limite conhecido

O controle de concorrência é local à JVM e o storage é filesystem. Antes de
escalar o task-service horizontalmente, use fila distribuída e object storage
com lifecycle/TTL. O teste k6 e os gráficos devem ser preservados em ambiente
representativo antes de declarar o risco mitigado em produção.
