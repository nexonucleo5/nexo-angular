# Docker do Nexo

Três containers independentes, cada um com a sua pasta:

| Pasta | Serviço | Imagem | O que é |
|---|---|---|---|
| [`database/`](database) | `db` | `nexo-db` | Postgres 17 (volume `nexo-pgdata`) |
| [`backend/`](backend) | `backend` | `nexo-backend` | API Spring Boot (REST + WebSocket), perfil `prod` |
| [`frontend/`](frontend) | `frontend` | `nexo-frontend` | Angular servido por nginx + proxy de `/api` e `/ws` |
| [`registry/`](registry) | `registry` | `registry:2` | Registry local opcional |

```
navegador ──► frontend (nginx :8080) ──/api, /ws──► backend (:8080) ──► db (:5432)
              única porta publicada                  rede interna        rede interna
```

O navegador só conversa com o frontend; a API e o WebSocket passam pelo nginx no
mesmo domínio, então não há CORS no caminho. O backend e o banco não ficam
expostos (o banco só em `127.0.0.1:5433`, para DBeaver/psql).

## Atalho: `docker/nexo`

`docker/nexo.cmd` (ou `docker/nexo.ps1`) faz tudo. Rode da raiz do repo:

```powershell
.\docker\nexo up                    # cria o .env com segredos gerados, sobe os 3 e abre o navegador
.\docker\nexo restart backend       # rebuilda e sobe só um serviço
.\docker\nexo logs backend          # logs (sem serviço = todos)
.\docker\nexo status | down | reset
.\docker\nexo push <usuario> 0.1.1  # publica no Docker Hub
.\docker\nexo registry              # registry local + push
```

## Sem o atalho

```bash
cp .env.example .env          # preencha NEXO_JWT_SECRET e POSTGRES_PASSWORD
docker compose up --build     # http://localhost:8080
```

Dados de exemplo (`SEED_ENABLED=true`): logins `diretor`, `professor`, `aluno`
e `admin`, todos com a senha provisória `123456`.

```bash
docker compose up -d --build backend   # rebuilda e sobe só o backend
docker compose logs -f backend         # logs de um serviço
docker compose exec db psql -U nexo    # shell do banco
docker compose down                    # para (mantém o banco)
docker compose down -v                 # para e APAGA o banco
```

Cada serviço também builda sozinho (contexto = raiz do repo):

```bash
docker build -f docker/backend/Dockerfile  -t nexo-backend  .
docker build -f docker/frontend/Dockerfile -t nexo-frontend .
docker build -f docker/database/Dockerfile -t nexo-db       .
```

## Registry e Docker Hub

O nome das imagens é `${REGISTRY_NAMESPACE}/nexo-<serviço>:${TAG}`. Basta trocar
o namespace para decidir para onde elas vão.

### Docker Hub

Um repositório por imagem: `<usuario>/nexo-frontend`, `<usuario>/nexo-backend`,
`<usuario>/nexo-db`. O Docker Hub cria o repositório no primeiro push.

```bash
docker login
REGISTRY_NAMESPACE=<usuario> TAG=0.1.1 docker compose build
REGISTRY_NAMESPACE=<usuario> TAG=0.1.1 docker compose push
```

No PowerShell: `$env:REGISTRY_NAMESPACE="<usuario>"; $env:TAG="0.1.1"`.

Para publicar pelo GitHub, o workflow
[`docker-publish.yml`](../.github/workflows/docker-publish.yml) faz isso a cada
push na `master` e em tags `v*`. Cadastre os secrets `DOCKERHUB_USERNAME` e
`DOCKERHUB_TOKEN` (token com permissão *Read & Write*).

Para rodar a partir das imagens publicadas, em qualquer máquina com Docker:

```bash
REGISTRY_NAMESPACE=<usuario> TAG=0.1.1 docker compose up -d --no-build
```

### Registry local

Sobe um `registry:2` em `127.0.0.1:5000` (sem autenticação — só para teste):

```bash
docker compose --profile registry up -d registry
REGISTRY_NAMESPACE=localhost:5000/nexo docker compose build
REGISTRY_NAMESPACE=localhost:5000/nexo docker compose push
curl http://localhost:5000/v2/_catalog
```

## Variáveis (`.env`)

| Variável | Default | Para quê |
|---|---|---|
| `NEXO_JWT_SECRET` | — (obrigatória) | assinatura dos tokens, mín. 32 caracteres |
| `POSTGRES_PASSWORD` | — (obrigatória) | senha do banco; só vale na criação do volume |
| `POSTGRES_USER` / `POSTGRES_DB` | `nexo` | usuário e nome do banco |
| `HOST_PORT` | `8080` | porta do site no host (ajuste `CORS_ORIGINS` junto) |
| `CORS_ORIGINS` | `http://localhost:8080` | origens aceitas na API e no WebSocket |
| `DB_HOST_PORT` | `5433` | porta do Postgres em `127.0.0.1` |
| `SEED_ENABLED` | `true` | semeia dados de exemplo; use `false` em produção |
| `REGISTRY_NAMESPACE` / `TAG` | `nexo` / `latest` | nome das imagens |

## Monolito (Render)

O `Dockerfile` da raiz continua como estava: o Angular embutido no jar do Spring,
com H2. É o que o Render constrói. Para rodá-lo localmente:

```bash
docker compose -f docker/monolito/compose.yaml --env-file .env up --build
```
