# Deploy do backend na VPS

O workflow `Deploy VPS` implanta os quatro serviços Spring Boot depois que o
workflow `Qualidade` conclui com sucesso no `main`. Também é possível iniciar um
deploy manual pela interface do GitHub, escolhendo um commit, tag ou branch.

## Fluxo

1. O workflow baixa exatamente o commit aprovado pela qualidade.
2. O Gradle gera os quatro JARs executáveis.
3. Um pacote com a revisão é mantido como artifact por 14 dias.
4. O pacote é enviado para a VPS por SSH.
5. A VPS cria e valida um dump do MySQL em `/opt/justdoit/backups`.
6. A VPS cria `/opt/justdoit/releases/<commit>` e atualiza o link `current`.
7. Cada serviço é reiniciado e validado em `/actuator/health`.
8. Se qualquer serviço falhar, os links voltam para a release anterior e os
   quatro serviços são reiniciados novamente.

O arquivo `/opt/justdoit/.env` e o Redis não são substituídos durante o deploy.
O banco pode receber migrations Flyway durante a inicialização, por isso o dump
pré-deploy é obrigatório e o processo para antes de reiniciar os serviços caso o
backup não possa ser criado ou validado.

## Preparação única da VPS

A VPS precisa ter Java 21, `curl`, `tar`, Nginx, Docker com Compose e os serviços
de MySQL e Redis em execução. O usuário de deploy deve ser o mesmo configurado nas
unidades `systemd` (atualmente `ubuntu`).

```bash
sudo mkdir -p /opt/justdoit/releases /opt/justdoit/backups
sudo chown -R ubuntu:ubuntu /opt/justdoit

sudo install -m 0644 infra/justdoit-auth.service /etc/systemd/system/
sudo install -m 0644 infra/justdoit-task.service /etc/systemd/system/
sudo install -m 0644 infra/justdoit-schedule.service /etc/systemd/system/
sudo install -m 0644 infra/justdoit-notification.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable justdoit-auth justdoit-task justdoit-schedule justdoit-notification
```

Mantenha `/opt/justdoit/.env` apenas na VPS, legível pelo usuário dos serviços e
fora do Git. Antes do primeiro deploy, confirme que ele contém todas as variáveis
exigidas pelas aplicações, incluindo `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`,
`CORS_ALLOWED_ORIGINS` e `INTERNAL_API_TOKEN` quando aplicável.

O usuário de deploy precisa executar apenas os seguintes comandos privilegiados
sem senha. Crie a regra com `sudo visudo -f /etc/sudoers.d/justdoit-deploy`:

```sudoers
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart justdoit-auth.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart justdoit-task.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart justdoit-schedule.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart justdoit-notification.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/journalctl -u justdoit-auth.service -n 80 --no-pager
ubuntu ALL=(root) NOPASSWD: /usr/bin/journalctl -u justdoit-task.service -n 80 --no-pager
ubuntu ALL=(root) NOPASSWD: /usr/bin/journalctl -u justdoit-schedule.service -n 80 --no-pager
ubuntu ALL=(root) NOPASSWD: /usr/bin/journalctl -u justdoit-notification.service -n 80 --no-pager
```

Confirme os caminhos reais com `command -v systemctl` e `command -v journalctl`
antes de salvar a regra.

## Environment e secrets no GitHub

Crie o environment `production`. Se desejar aprovação humana antes de cada
deploy, configure required reviewers nesse environment.

Cadastre estes secrets no environment:

| Secret | Conteúdo |
|---|---|
| `VPS_HOST` | Host ou IP da VPS |
| `VPS_USER` | Usuário SSH, normalmente `ubuntu` |
| `VPS_SSH_PRIVATE_KEY` | Chave privada exclusiva do GitHub Actions |
| `VPS_KNOWN_HOSTS` | Linha validada da chave pública SSH da VPS |

As variáveis abaixo são opcionais:

| Variable | Padrão | Uso |
|---|---:|---|
| `VPS_SSH_PORT` | `22` | Porta do SSH |
| `VPS_APP_DIR` | `/opt/justdoit` | Diretório das releases |

Não desative a verificação de host SSH. Obtenha `VPS_KNOWN_HOSTS` por um canal
confiável; para uma porta diferente de 22, a entrada deve identificar
`[host]:porta`.

## Primeiro deploy e operação

No primeiro deploy versionado, o script preserva os quatro JARs antigos, caso
existam, em uma release `pre-deploy-*`. Releases posteriores usam o destino do
link `current` como rollback.

Para implantar manualmente, abra **Actions → Deploy VPS → Run workflow**. O
environment `production` ainda aplica suas regras de aprovação.

Depois do deploy, confira:

```bash
readlink -f /opt/justdoit/current
systemctl status justdoit-auth justdoit-task justdoit-schedule justdoit-notification
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:8081/actuator/health
curl --fail http://127.0.0.1:8082/actuator/health
curl --fail http://127.0.0.1:8083/actuator/health
```

## Migrações

O deploy inicia as aplicações, que executam Flyway. Migrações já aplicadas nunca
devem ser removidas, renomeadas ou alteradas. Mudanças de schema devem ser
retrocompatíveis com a release anterior, pois rollback de aplicação não desfaz o
banco automaticamente. O dump em `/opt/justdoit/backups` permite uma restauração
manual se uma migration MySQL parcialmente aplicada exigir recuperação; ele não é
restaurado automaticamente para evitar apagar gravações legítimas feitas após o
backup.
