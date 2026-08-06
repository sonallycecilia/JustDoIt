# 12. Topologia de produção e deploy

## 12.1 Onde cada coisa roda

```mermaid
flowchart TB
    user(["Usuário"])

    subgraph gp["GitHub Pages"]
        front["Frontend estático<br/><small>justdoit-app.duckdns.org<br/>via CNAME no repositório</small>"]
    end

    subgraph vps["VPS Oracle Cloud — ARM, 2 vCPU / 12 GB"]
        nginx["nginx :443<br/><small>justdoitapi.duckdns.org<br/>TLS por Let's Encrypt<br/>HSTS, CSP, nosniff, DENY, no-referrer<br/>server_tokens off</small>"]

        subgraph units["systemd — uma unit por serviço"]
            a["justdoit-auth<br/><small>auth-service.jar :8080</small>"]
            t["justdoit-task<br/><small>task-service.jar :8081</small>"]
            s["justdoit-schedule<br/><small>schedule-service.jar :8082</small>"]
            n["justdoit-notification<br/><small>notification-service.jar :8083</small>"]
        end

        subgraph docker["docker-compose"]
            my[("MySQL 8.0<br/><small>127.0.0.1:3306 — só loopback</small>")]
            rd[("Redis<br/><small>127.0.0.1:6379 — com requirepass</small>")]
        end

        env["/opt/justdoit/.env<br/><small>EnvironmentFile das units:<br/>JWT_SECRET, SPRING_DATASOURCE_PASSWORD,<br/>REDIS_PASSWORD, CORS_ALLOWED_ORIGINS,<br/>INTERNAL_API_TOKEN</small>"]
    end

    duck["DuckDNS<br/><small>DNS dinâmico</small>"]

    user -- HTTPS --> front
    front -- "chamadas de API, HTTPS" --> duck
    duck -- "resolve para o IP da VPS" --> nginx
    nginx --> a & t & s & n
    a & t & s & n --> my
    env -.-> a & t & s & n

    classDef cli fill:#ede7f6,stroke:#4527a0,color:#311b92
    classDef srv fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef inf fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef sec fill:#fff8e1,stroke:#ff8f00,color:#e65100
    class front cli
    class a,t,s,n srv
    class my,rd,nginx,duck inf
    class env sec
```

Frontend e backend estão em **hosts diferentes** — daí o CORS ser configuração de
produção, não só de desenvolvimento. Cada serviço lê as origens permitidas de
`CORS_ALLOWED_ORIGINS`.

O Redis sobe no compose mas **nenhum serviço o usa** hoje — está provisionado para
o balde de rate limit compartilhado, quando houver mais de uma réplica.

## 12.2 O deploy

```mermaid
sequenceDiagram
    autonumber
    actor D as Dev
    participant L as Máquina local
    participant V as VPS
    participant SD as systemd

    D->>L: python docs/automações/deploy.py
    L->>L: ./gradlew :services:X:bootJar
    L->>V: scp do .jar para /opt/justdoit
    L->>V: ssh — sudo systemctl restart justdoit-X
    V->>SD: reinicia a unit
    SD->>SD: Restart=always, RestartSec=10
    SD-->>D: serviço no ar com o novo jar
```

O `deploy.py` também tem um modo `setup` que provisiona a VPS de zero: instala
Java 21, Docker e nginx, copia `docker-compose.yml`, `nginx.conf` e as units,
habilita tudo no systemd, sobe MySQL e Redis e roda o certbot.

## 12.3 Configuração por ambiente

```mermaid
flowchart LR
    subgraph dev["Desenvolvimento"]
        d1["MySQL local :3306<br/>createDatabaseIfNotExist=true"]
        d2["CORS: http://localhost:3000"]
        d3["4 serviços via ./gradlew bootRun<br/>ou run configs do IntelliJ"]
        d4["Frontend: npm run dev<br/>Vite em 127.0.0.1:3000"]
    end

    subgraph prod["Produção"]
        p1["MySQL no Docker, só loopback"]
        p2["CORS: origem do GitHub Pages"]
        p3["4 units systemd, Restart=always"]
        p4["Frontend no GitHub Pages"]
    end

    subgraph test["Testes"]
        t1["H2 em memória<br/>application-test.yml"]
        t2["AuthTestSupport do libs/common<br/>authenticatedUser(UUID)"]
    end

    classDef d fill:#ede7f6,stroke:#4527a0,color:#311b92
    classDef p fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef t fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    class d1,d2,d3,d4 d
    class p1,p2,p3,p4 p
    class t1,t2 t
```

## Pontos operacionais que valem citar

**O schema é gerenciado pelo Hibernate** (`ddl-auto=update`), sem Flyway. Funciona
para o estágio atual, mas significa que não há migração versionada nem rollback de
schema — é o ponto a resolver antes de qualquer mudança destrutiva em tabela.

**Sem lock distribuído.** Os jobs `@Scheduled` e o balde do `RateLimitFilter` são
locais ao processo. O deploy é de instância única por serviço, então funcionam;
escalar horizontalmente exige resolver os dois — ver
[09-jobs-agendados.md](09-jobs-agendados.md).

**`iptables` precisa ser persistido.** As regras das portas 80/443 na VPS Oracle
não sobrevivem ao reboot se não forem salvas — é armadilha conhecida deste
ambiente.

**O nginx não expõe rotas internas.** `/me/data` e `/internal/**` ficam fora de
qualquer `location`: são alcançáveis apenas de dentro da VPS, entre os serviços.

Detalhes completos de infraestrutura em
[`docs/INFRA-DEPLOY.md`](../INFRA-DEPLOY.md).
