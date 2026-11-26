# Nexo — Gestão Escolar

Frontend Angular (nginx), backend Spring Boot e banco PostgreSQL, cada um em um container.

```
navegador -> frontend (nginx :8080) -> backend (:8080) -> db (:5432)
                       rede Docker "interna"
```

O nginx repassa `/api` e `/ws` para `backend:8080` e o backend conecta em `db:5432`, pelo nome do serviço (DNS da rede `interna`). Só o frontend é publicado (`localhost:8080`); o banco fica em `127.0.0.1:5433`. Os dados ficam no volume `nexo-pgdata`.

| Serviço | Dockerfile | Imagem |
|---|---|---|
| frontend | `docker/frontend/Dockerfile` | `1dzk/nexo-frontend:1.0` |
| backend | `docker/backend/Dockerfile` | `1dzk/nexo-backend:1.0` |
| db | `docker/database/Dockerfile` (schema em `docker/database/initdb/01-schema.sql`) | `1dzk/nexo-db:1.0` |

## Subir

```bash
docker compose up -d --build
```

Não precisa de `.env`: o compose tem valores padrão de desenvolvimento. Para trocar a senha do banco ou o segredo do JWT, copie o `.env.example` para `.env` e preencha `POSTGRES_PASSWORD` e `NEXO_JWT_SECRET` (mín. 32 caracteres).

Acesse http://localhost:8080. Logins de exemplo: `admin`, `diretor`, `professor` e `aluno`, senha `123456`.

## Testar

```bash
docker compose ps                      # db, backend e frontend healthy
curl -i http://localhost:8080/healthz  # 200 ok
curl -i http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"login":"admin","senha":"123456"}'   # passa por frontend -> backend -> db
docker compose logs -f backend
docker compose exec backend id         # uid=nexo, não é root
```

## Parar

```bash
docker compose down      # mantém o banco (volume)
docker compose down -v   # apaga o banco também
```

## Imagens e Docker Hub

As imagens saem do build com tag semântica (`1.0`) e o namespace do Docker Hub (`1dzk`). Para publicar:

```bash
docker login
docker compose push
```

Usar outra versão: `TAG=1.1 docker compose up -d --build`.

## Rollback

Mudou de versão e deu problema? Volte para a tag anterior, sem rebuild:

```bash
TAG=1.0 docker compose up -d --no-build   # usa a imagem 1dzk/nexo-*:1.0 (local ou do Docker Hub)
```

O banco não é afetado, porque está no volume.

## Segurança

- backend (`nexo`), frontend (`nginx`) e Postgres (`postgres`) rodam sem root; para o banco, confira com `docker compose exec db ps` (o `exec id` usa o usuário padrão da imagem)
- backend e frontend com `read_only`, `cap_drop: ALL`, `no-new-privileges` e `tmpfs` em `/tmp`
- multi-stage build: as imagens finais só têm o JRE + jar e o nginx + estáticos
- `.dockerignore` deixa de fora `.env`, `.git`, `node_modules`, `target` e logs

Scan da imagem:

```bash
docker scout quickview 1dzk/nexo-backend:1.0
docker scout cves --only-severity critical,high 1dzk/nexo-backend:1.0
```

Resultado em 28/10/2026 no backend: 6 críticas e 5 altas. A base (`eclipse-temurin:21-jre-alpine`) tem 1 crítica; o resto está nas dependências do Spring Boot 3.5.16 (`tomcat-embed-core`, `spring-webmvc` e `jackson-*`). A correção é subir a versão do Spring Boot no `backend/pom.xml`, o que ainda não foi feito.

## Variáveis

| Variável | Padrão | |
|---|---|---|
| `POSTGRES_PASSWORD` | `nexo-dev` | senha do banco (só vale na criação do volume) |
| `NEXO_JWT_SECRET` | valor de dev | segredo dos tokens, mín. 32 caracteres |
| `HOST_PORT` | `8080` | porta do site |
| `DB_HOST_PORT` | `5433` | porta do Postgres no host |
| `SEED_ENABLED` | `true` | cria os dados de exemplo |
| `REGISTRY_NAMESPACE` / `TAG` | `1dzk` / `1.0` | nome e versão das imagens |
