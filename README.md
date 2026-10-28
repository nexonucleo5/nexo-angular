# Nexo — gestão escolar

Sistema de gestão escolar com três componentes, cada um em um container:

| Componente | Pasta do código | Dockerfile | Tecnologia |
|---|---|---|---|
| **Frontend** | [nexo/](nexo/) | [docker/frontend/Dockerfile](docker/frontend/Dockerfile) | Angular 21 + nginx |
| **Backend** | [backend/](backend/) | [docker/backend/Dockerfile](docker/backend/Dockerfile) | Spring Boot 3 / Java 21 |
| **Banco de dados** | [docker/database/](docker/database/) | [docker/database/Dockerfile](docker/database/Dockerfile) | PostgreSQL 17 |

Os scripts de criação do banco estão em
[docker/database/init/01-schema.sql](docker/database/init/01-schema.sql) (estrutura das 31 tabelas).
Eles rodam sozinhos na primeira subida, quando o volume do banco está vazio.

## Arquitetura

```
navegador ──► frontend (nginx :8080) ──/api, /ws──► backend (:8080) ──► database (:5432)
                       rede "web"                      rede "dados"
```

- Os containers se encontram pelo **nome do serviço** (`backend`, `database`), pela
  rede interna do Docker — nenhum IP é configurado à mão.
- O banco fica na rede `dados`, que só o backend enxerga; o frontend não alcança o banco.
- O frontend chama `/api` e `/ws` com caminho relativo e o nginx repassa ao backend:
  tudo é same-origin, sem CORS.
- Os dados do banco ficam no volume `nexo-pgdata` e sobrevivem a reinícios e rebuilds.

## Pré-requisitos

Docker com Docker Compose v2 (Docker Desktop no Windows/Mac). Nada mais: Node, Java e
Maven rodam dentro do build das imagens.

## Construir e iniciar

```bash
docker compose up -d --build
```

Não precisa de nenhuma configuração: o compose traz valores padrão de desenvolvimento.
Na primeira vez o build leva alguns minutos (baixa dependências do npm e do Maven).

Acesse **http://localhost:8080**.

Logins de exemplo (dados de demonstração criados no banco vazio; senha `123456`):
`admin`, `diretor`, `professor` e `aluno`.

### Configuração opcional

Para trocar senhas, portas ou nomes, copie o modelo e edite:

```bash
cp .env.example .env
```

| Variável | Padrão | Para quê |
|---|---|---|
| `NEXO_JWT_SECRET` | valor público de dev | Assinatura dos tokens (≥ 32 caracteres). **Troque fora de dev**: `openssl rand -base64 48` |
| `POSTGRES_PASSWORD` | `nexo_dev_senha` | Senha do banco. **Troque fora de dev** |
| `POSTGRES_DB` / `POSTGRES_USER` | `nexo` | Banco e usuário |
| `FRONTEND_PORT` | `8080` | Porta do site (o CORS acompanha) |
| `DB_PORT` | `5433` | Porta do banco na sua máquina (só 127.0.0.1); 5433 evita conflito com um Postgres local |
| `SEED_ENABLED` | `true` | Dados de exemplo no banco vazio; `false` em instalação real |

## Testar

```bash
docker compose ps                 # os três serviços devem estar "healthy"
curl -i http://localhost:8080/    # 200 — frontend (nginx)
curl -i http://localhost:8080/api/alunos   # 401 — chegou ao backend (precisa de token)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"admin","senha":"123456"}'  # devolve o token — backend falando com o banco
docker compose logs -f backend    # logs
```

Acessar o banco pela sua máquina: `localhost:5433` (usuário/banco `nexo`, senha `nexo_dev_senha` ou a do `.env`):

```bash
docker compose exec database psql -U nexo -d nexo
```

## Encerrar

```bash
docker compose down        # para e remove os containers, MANTÉM o banco
docker compose down -v     # para e APAGA o banco (volume nexo-pgdata)
```

## Registry local e Docker Hub

Cada imagem é `<registry>/<namespace>/nexo-<serviço>:<tag>` (`nexo-database`,
`nexo-backend`, `nexo-frontend`).

**Registry local** (`registry:2`, em [docker/registry/](docker/registry/)):

```bash
docker compose -f docker/registry/compose.yaml up -d      # localhost:5000
# no .env: IMAGE_REGISTRY=localhost:5000  IMAGE_NAMESPACE=nexo
docker compose build && docker compose push
curl http://localhost:5000/v2/_catalog
```

**Docker Hub** — o repositório é criado no primeiro push:

```bash
docker login                       # usuário + Personal Access Token (Read & Write)
# no .env: IMAGE_REGISTRY=docker.io  IMAGE_NAMESPACE=<seu-usuario>  NEXO_TAG=1.0.0
docker compose build && docker compose push
```

O workflow [.github/workflows/docker-publish.yml](.github/workflows/docker-publish.yml)
faz isso a cada push na `master` e a cada tag `vX.Y.Z`; precisa dos secrets
`DOCKERHUB_USERNAME` e `DOCKERHUB_TOKEN` no GitHub.

Rodar a partir das imagens publicadas, sem build: `docker compose pull && docker compose up -d`.

## Observações

- O `Dockerfile` da raiz é o monolito (Angular dentro do jar) usado no deploy do Render; o
  ambiente Docker Compose acima usa as três imagens separadas de `docker/`.
- Mudou uma entidade do backend? O Hibernate acrescenta o que faltar ao subir. Para
  regenerar o `01-schema.sql`, veja o comando no cabeçalho do próprio arquivo.
