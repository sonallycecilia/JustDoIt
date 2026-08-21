# Arquitetura atual do JustDoIt Backend

Este documento descreve o que está implementado em `origin/main`, commit
`dc6d9f2`, verificado em 20/08/2026. Propostas futuras são identificadas como
pendências, não como funcionalidades existentes.

## 1. Visão geral

O backend é composto por quatro processos Spring Boot atrás de um reverse proxy
Nginx. Eles têm limites funcionais claros, mas compartilham um único schema
MySQL. Por isso, a descrição mais precisa é **microsserviços implantáveis de
forma independente com persistência compartilhada**.

```text
Frontend React
      │ HTTPS + JSON + Bearer JWT
      ▼
Nginx / justdoitapi.duckdns.org
      ├── auth-service :8080
      ├── task-service :8081
      ├── schedule-service :8082
      └── notification-service :8083
                 │
                 ▼
          MySQL justdoit_db
```

Cada serviço segue, de forma pragmática, esta sequência:

```text
Controller HTTP → Service transacional → Repository JPA → MySQL
                         └──────────────→ cliente de integração ou job
```

Não há uma implementação estrita de arquitetura hexagonal: os serviços de
aplicação dependem diretamente de Spring Data/JPA e dos clientes HTTP.

## 2. Componentes e responsabilidades

### `libs/common`

Biblioteca incorporada aos quatro JARs. Contém o `JwtValidator`, o
`JwtAuthFilter`, tratamento uniforme de erros de segurança, suporte de testes e
validações reutilizadas, como texto seguro e senha forte. Somente o
`auth-service` emite tokens.

### `auth-service`

Responsável por:

- cadastro e login protegidos por Cloudflare Turnstile;
- consulta de disponibilidade de e-mail;
- emissão de access token JWT HS256;
- refresh token opaco, persistido no servidor somente como hash;
- rotação, tolerância curta para concorrência e detecção de reutilização;
- logout, leitura/alteração do perfil e exclusão da conta;
- rate limit em memória para endpoints públicos;
- limpeza diária de refresh tokens expirados.

Rotas públicas: `POST /auth/register`, `POST /auth/login`,
`POST /auth/refresh` e `GET /auth/check-email`. As rotas `/auth/me` e
`/auth/logout` exigem JWT.

### `task-service`

É o núcleo funcional do produto. Mantém:

- tarefas, subtarefas, categorias e notas;
- configuração dos módulos de uma tarefa;
- cronômetro ativo, lançamentos de tempo e sessões de foco;
- regras de recorrência e materialização de ocorrências;
- lembrete configurado na tarefa;
- relatórios por período e categoria;
- fechamento de ciclo semanal, histórico e snapshots imutáveis;
- exportação assíncrona em CSV ou JSON.

A exportação cria um job em `POST /me/exports`, processa os dados em páginas e
grava o arquivo fora da árvore pública. O download usa URL temporária assinada.
O endpoint legado `GET /me/export` também dispara o fluxo assíncrono.

Jobs atuais:

- tarefas vencidas: a cada hora, no minuto 15;
- materialização de recorrências: diariamente às 00:30;
- expiração de ciclos semanais: a cada hora;
- limpeza de exportações vencidas: intervalo padrão de cinco minutos.

### `schedule-service`

Mantém blocos de tempo, planos semanais, resumos e snapshots de análise. Expõe:

- CRUD de `/time-blocks`;
- criação, consulta, histórico e fechamento de `/weekly-plans`;
- criação/leitura de resumo por plano;
- `/analytics/overall` e `/analytics/weeks/{weekStart}`.

Ao fechar uma semana, o frontend primeiro conclui o ciclo no `task-service` e
depois fecha o plano no `schedule-service`. As operações foram desenhadas para
permitir repetição após falha de rede sem duplicar o fechamento.

### `notification-service`

Mantém notificações, preferências e o canal de mensagens de suporte. As rotas
externas ficam em `/notifications` e `/support/messages`.

O job de lembretes roda a cada 15 segundos por padrão e consulta diretamente as
tabelas de tarefas no banco compartilhado. Ele cria um único `TASK_REMINDER` por
tarefa e remove lembretes obsoletos. Isso funciona na topologia atual, mas acopla
o serviço ao schema do `task-service`.

`POST /internal/notifications` não exige JWT de usuário; o controller valida o
header `X-Internal-Token`. O endpoint não é publicado pelo Nginx.

## 3. Roteamento externo

| Prefixo | Destino |
|---|---|
| `/auth`, `/users` | auth-service |
| `/tasks`, `/timers`, `/categories`, `/notes` | task-service |
| `/me/note`, `/me/export`, `/me/exports` | task-service |
| `/weekly-cycles` | task-service |
| `/analytics/categories` | task-service |
| `/time-blocks`, `/weekly-plans`, demais `/analytics` | schedule-service |
| `/notifications`, `/support` | notification-service |

O Nginx ainda aceita o prefixo `/events`, embora não exista controller com essa
rota. `/me/data` e `/internal/**` permanecem internos.

## 4. Segurança e sessão

- O frontend envia `Authorization: Bearer <access-token>`.
- Os quatro serviços validam assinatura, emissor, audiência, tipo e expiração.
- O principal no Spring Security é o UUID do usuário.
- CORS usa uma lista explícita configurada por ambiente.
- CSRF está desabilitado porque a autenticação atual usa token no header.
- Refresh tokens ainda trafegam no JSON e ficam no Web Storage do frontend.
- O segredo HMAC é compartilhado entre os quatro serviços.
- O download de exportação é público apenas com token temporário HMAC válido.
- Health checks são públicos; Prometheus é público apenas no `task-service` e
  deve ser protegido pela rede em produção.

Limitações que permanecem:

- tokens no Web Storage continuam expostos a JavaScript executado na origem;
- JWT simétrico amplia o impacto do vazamento do segredo compartilhado;
- rate limit em memória e jobs locais não coordenam múltiplas réplicas;
- o valor padrão do Turnstile não deve ser usado como segredo de produção;
- `show-sql` está habilitado nos quatro serviços e deve ser desligado em produção.

## 5. Persistência

Todos os serviços usam `ddl-auto=validate`, `open-in-view=false` e Flyway. Cada
domínio tem uma tabela de histórico independente:

| Serviço | Histórico | Versões atuais |
|---|---|---|
| auth | `flyway_auth_history` | V1 |
| task | `flyway_task_history` | V1 a V8 |
| schedule | `flyway_schedule_history` | V1 a V4 |
| notification | `flyway_notification_history` | V1 a V5 |

As relações entre domínios são principalmente UUIDs lógicos. Como o schema e a
credencial do banco são compartilhados, a separação é uma convenção do código,
não uma barreira de infraestrutura.

As versões são únicas no estado verificado. O script
`scripts/check-flyway-migrations.sh`, executado nos workflows de qualidade e
deploy, bloqueia novas duplicidades.

## 6. Integrações e consistência

| Origem | Destino | Finalidade | Política atual |
|---|---|---|---|
| auth | Cloudflare | validar Turnstile | chamada síncrona |
| auth | task | apagar dados da conta | síncrona dentro do fluxo de exclusão |
| schedule | task | obter relatório semanal | timeout e resumo parcial em falha |
| task | notification | criar notificações internas | HTTP ou eventos locais, conforme o fluxo |
| notification | tabelas de task | sincronizar lembretes | consulta JDBC periódica |

Não existe transação distribuída. A exclusão de conta ainda apaga dados do
`task-service`, mas não há orquestração equivalente documentada para dados de
agenda, notificações e suporte. Esse é um risco de retenção parcial.

## 7. Operação e entrega

- MySQL, Redis, Prometheus e Grafana estão definidos no Compose.
- Redis está provisionado, mas não participa do rate limit nem dos locks dos jobs.
- Os quatro JARs são executados por unidades systemd na VPS.
- O Nginx termina TLS e adiciona headers de segurança.
- O workflow `Qualidade` valida migrations, executa testes e publica evidências.
- O workflow `Deploy VPS` empacota a revisão aprovada, cria backup do MySQL,
  reinicia os serviços, verifica `/actuator/health` e volta aos JARs anteriores
  se um health check falhar.
- O rollback da aplicação não reverte migrations automaticamente.

Prometheus coleta `/actuator/prometheus` do `task-service`. O dashboard atual é
focado na exportação assíncrona e nos efeitos dela sobre a JVM e endpoints.

## 8. Testes e evidências

O projeto usa JUnit 5, Spring Boot Test, MockMvc, H2 em modo MySQL e JaCoCo. Há
testes de serviços, controllers, segurança, sessão, concorrência, exportação,
fechamento semanal e métricas de qualidade.

Os relatórios em `docs/quality/` informam o commit e o ambiente de cada execução.
Um relatório marcado como “não executado” não deve ser reinterpretado como
aprovação. O pipeline do GitHub é a verificação reproduzível de integração.

Limitações de teste conhecidas:

- H2 não reproduz integralmente o comportamento e o DDL do MySQL;
- não há Testcontainers no projeto;
- não há E2E dos quatro processos passando pelo Nginx;
- o teste k6 da exportação depende de ambiente e tokens fornecidos externamente.

## 9. Riscos e próximos passos realistas

1. Remover o segredo padrão do Turnstile e exigir configuração por ambiente.
2. Incluir schedule, notification e support na exclusão verificável da conta.
3. Proteger Prometheus por rede e desligar SQL em produção.
4. Adicionar timeout explícito à integração auth → task.
5. Coordenar jobs e rate limit antes de escalar horizontalmente.
6. Separar credenciais ou schemas por serviço.
7. Adicionar Testcontainers MySQL e testes de contrato entre serviços.
