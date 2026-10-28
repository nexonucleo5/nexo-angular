# Nexo — Gestão Escolar

Sistema de gestão escolar com três partes independentes, cada uma em sua pasta e
com sua própria imagem Docker:

| Pasta       | O que é                                  | Tecnologia           | Porta no host |
|-------------|------------------------------------------|----------------------|---------------|
| `nexo/`     | Frontend (interface web)                 | Angular 21 + nginx   | 4200          |
| `backend/`  | Backend (API REST e WebSocket)           | Spring Boot 3 / Java 21 | (interna)  |
| `database/` | Banco de dados e script de criação       | PostgreSQL 16        | (interna)     |

O `compose.yaml` na raiz orquestra as três. Só o frontend fica exposto para o seu
navegador; o backend e o banco ficam numa rede Docker interna (`interna`), acessíveis
apenas pelos nomes `backend` e `database`.

```
navegador ──► frontend (nginx :4200)
                 ├── /api/* ──► backend (Spring :8080) ──► database (Postgres :5432)
                 └── /ws/*  ──► backend
```

O nginx do frontend repassa `/api` e `/ws` para o backend, então o navegador fala
sempre com a mesma origem (sem problemas de CORS).

---

## Pré-requisitos

- Docker Desktop instalado e **aberto** (o ícone da baleia precisa estar ativo).
- Git (para baixar o projeto).
- Cerca de 3 GB livres em disco e conexão com a internet no primeiro build.

---

## 1. Baixar o projeto

```bash
git clone https://github.com/nexonucleo5/nexo-angular.git
cd nexo-angular
git checkout f21
```

## 2. Configurar as variáveis de ambiente

Copie o modelo e preencha os dois valores obrigatórios:

```bash
cp .env.docker.example .env.docker
```

Edite o `.env.docker`:

```
NEXO_JWT_SECRET=    # chave dos tokens de login (mínimo 32 caracteres)
POSTGRES_PASSWORD=  # senha do banco
```

Gerar uma chave aleatória (PowerShell):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Gerar uma chave aleatória (Linux/macOS):

```bash
openssl rand -base64 48
```

O arquivo `.env.docker` **não vai para o git** (está no `.gitignore`).

## 3. Subir o ambiente

```bash
docker compose --env-file .env.docker up -d --build
```

Na primeira vez o build leva de 5 a 10 minutos (baixa imagens e compila o frontend
e o backend). As seguintes são bem mais rápidas.

## 4. Verificar se está no ar

```bash
docker compose --env-file .env.docker ps
```

Os três serviços devem aparecer como `running` ou `healthy`. O backend pode levar
cerca de 1 minuto para ficar `healthy` depois da subida.

## 5. Testar a aplicação

1. Abra **http://localhost:4200** no navegador.
2. Entre com um dos usuários de teste (senha `123456` para todos):

| Usuário      | Perfil            |
|--------------|-------------------|
| `aluno`      | Aluno             |
| `professor`  | Professor         |
| `diretor`    | Diretor           |

3. Se a tela de login aparecer e o login funcionar, a integração
   frontend → backend → banco está validada.

Os dados de exemplo são criados na primeira subida (`SEED_ENABLED=true` no `.env.docker`).

## 6. Ver logs

```bash
docker compose --env-file .env.docker logs -f backend
docker compose --env-file .env.docker logs -f frontend
docker compose --env-file .env.docker logs -f database
```

`Ctrl + C` sai do modo de acompanhamento sem parar os containers.

## 7. Encerrar o ambiente

```bash
# para os containers e mantém os dados do banco (volume)
docker compose --env-file .env.docker down

# para os containers e APAGA o banco (começa do zero na próxima subida)
docker compose --env-file .env.docker down -v
```

---

## Persistência de dados

- O banco usa o volume Docker `nexo_nexo-pgdata`. Ele sobrevive a `down` e a rebuilds.
- Só é apagado com `down -v`.
- O script `database/init/02-schema.sql` cria as tabelas somente quando o volume está
  vazio (primeira subida). Depois disso, o Hibernate do backend mantém o schema.

## Estrutura do repositório

```
.
├── compose.yaml              # orquestra banco, backend e frontend
├── .env.docker.example       # modelo de variáveis de ambiente
├── backend/
│   ├── Dockerfile            # build do Spring Boot (multi-stage)
│   ├── .dockerignore
│   └── src/ ...              # código-fonte, pom.xml
├── nexo/
│   ├── Dockerfile            # build do Angular + nginx com proxy
│   ├── nginx.conf            # proxy de /api e /ws para o backend
│   ├── .dockerignore
│   └── src/ ...              # código-fonte Angular
├── database/
│   ├── Dockerfile            # imagem do PostgreSQL
│   └── init/
│       ├── 01-config.sh      # configura o fuso horário
│       └── 02-schema.sql     # criação das tabelas
├── scripts/
│   ├── docker-publish.sh     # build e push das imagens (Linux/macOS)
│   └── docker-publish.ps1    # build e push das imagens (Windows)
└── compose.monolito.yaml     # versão antiga: tudo numa imagem só (deploy Render)
```

## Publicar as imagens (opcional)

```bash
docker login
DOCKERHUB_USER=seu-usuario TAG=1.0.0 ./scripts/docker-publish.sh
```

No Windows, use `scripts\docker-publish.ps1` com `$env:DOCKERHUB_USER` e `$env:TAG`
definidos antes.

Para subir **sem build**, usando as imagens já publicadas:

```bash
docker compose --env-file .env.docker pull
docker compose --env-file .env.docker up -d --no-build
```

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---------|----------------|-------------|
| `NEXO_JWT_SECRET` … `defina` ao subir | `.env.docker` sem a chave | Preencha o valor e rode de novo |
| `port is already allocated` na porta 4200 | Outro programa usa a porta | Mude `FRONT_PORT` no `.env.docker` |
| `failed to connect to the docker API` | Docker Desktop fechado | Abra o Docker Desktop e espere ficar ativo |
| Backend `unhealthy` ou reiniciando | Banco ainda subindo ou senha errada | Veja `logs backend`; confira `POSTGRES_PASSWORD` |
| Login não funciona após trocar a senha do banco | Volume antigo com a senha antiga | `down -v` e suba de novo (apaga os dados) |

## Autor

Projeto acadêmico — UniSENAI.
