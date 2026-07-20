# Nexo - Gestão Escolar — Deploy (Task 6)

> Guia de publicação do sistema (frontend Angular + backend Spring) em produção,
> incluindo a correção do erro de `index.html` no GitHub Actions e o CI/CD.

---

## 1. O que foi corrigido

### Erro "GitHub Actions não encontra index.html"
O builder novo do Angular (`@angular/build:application`) gera a saída em
`dist/nexo/**browser**/`, mas o workflow subia `dist/nexo` (sem `browser`) — daí o
`index.html` não era encontrado.

**Correção aplicada** em `angular.json`:
```json
"outputPath": { "base": "dist/nexo", "browser": "" }
```
Com `browser: ""` o `index.html` e os assets passam a ficar direto em
`dist/nexo/`. O workflow (`.github/workflows/static.yml`) foi atualizado para
apontar para `nexo/dist/nexo`.

### Build de produção
```bash
cd nexo
npm ci
npx ng build --configuration production --base-href /
# saída: nexo/dist/nexo/  (index.html na raiz)
```
```bash
cd backend
mvn -B clean package -DskipTests
# saída: backend/target/nexo-backend-0.1.0.jar
```

---

## 2. CI/CD (GitHub Actions)

Arquivo: `.github/workflows/static.yml`. Dispara em `push` na `master`.

- **build-frontend**: `npm ci` → `ng build` → copia `index.html` para `404.html`
  (fallback de SPA no Pages) → publica em **GitHub Pages**.
- **build-backend**: `mvn package` → sobe o `jar` como artefato de CI.

Correções relevantes no workflow:
- Adicionadas as **permissões** exigidas pelo Pages (`pages: write`, `id-token: write`)
  e o `environment: github-pages` — a ausência delas era causa comum do deploy falhar.
- `concurrency` para não sobrepor deploys.
- Caminho de upload corrigido para `nexo/dist/nexo`.

**Config única necessária no GitHub** (uma vez): repositório → *Settings → Pages →
Build and deployment → Source: **GitHub Actions***. O domínio custom
`nexo-gestao-escolar.com.br` já está no arquivo `CNAME` da raiz.

---

## 3. Topologias de produção

O frontend é estático, mas o backend (Spring + **WebSocket** + banco) precisa de um
processo rodando. Há duas formas:

### Opção A — Servidor único com Nginx (recomendada)

Um servidor (VPS) serve o frontend **e** faz reverse-proxy de `/api` e `/ws` para o
jar. Assim o `environment.ts` de produção (`apiUrl: '/api'`, `wsUrl: same-host/ws`)
funciona sem alterações e tudo fica no mesmo domínio (sem CORS).

**Nginx** (`/etc/nginx/sites-available/nexo`):
```nginx
server {
    listen 80;
    server_name nexo-gestao-escolar.com.br;

    root /var/www/nexo;           # conteúdo de dist/nexo
    index index.html;

    # SPA: qualquer rota desconhecida cai no index.html (deep-link/refresh)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API REST → Spring
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket do chat (professor ↔ diretor) → precisa dos headers de Upgrade
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;   # mantém a conexão do chat aberta
    }
}
```
Depois: `certbot --nginx -d nexo-gestao-escolar.com.br` para HTTPS (o `wsUrl` de
produção já escolhe `wss` automaticamente quando o site é servido via HTTPS).

**Backend como serviço** (`/etc/systemd/system/nexo.service`):
```ini
[Unit]
Description=Nexo Backend
After=network.target postgresql.service

[Service]
User=nexo
WorkingDirectory=/opt/nexo
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=NEXO_JWT_SECRET=<segredo-forte-gerado>
Environment=DB_URL=jdbc:postgresql://localhost:5432/nexo
Environment=DB_USER=nexo
Environment=DB_PASSWORD=<senha-do-banco>
Environment=CORS_ORIGINS=https://nexo-gestao-escolar.com.br
ExecStart=/usr/bin/java -Dfile.encoding=UTF-8 -jar /opt/nexo/nexo-backend-0.1.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl daemon-reload && sudo systemctl enable --now nexo
```

### Opção B — Frontend no GitHub Pages + backend hospedado à parte

O Pages só serve arquivos estáticos: **não** consegue proxyar `/api` nem `/ws`. Se
mantiver o frontend no Pages, o backend precisa de um host próprio (Render, Railway,
Fly.io, VPS…) e o frontend deve apontar para a URL **absoluta** dele:

`src/environments/environment.ts`:
```ts
export const environment = {
  production: true,
  apiUrl: 'https://api.nexo-gestao-escolar.com.br/api',
  wsUrl: 'wss://api.nexo-gestao-escolar.com.br/ws',
};
```
E no backend, incluir o domínio do Pages em `CORS_ORIGINS`
(`https://nexo-gestao-escolar.com.br`). O WebSocket **exige `wss`** quando a página
é HTTPS (o navegador bloqueia `ws` a partir de página segura).

---

## 4. Banco de dados (H2 → PostgreSQL)

- **Dev:** H2 em arquivo (`backend/data/nexo.mv.db`), com seed de exemplo.
- **Prod:** PostgreSQL via perfil `prod` (`application-prod.yml`), driver já incluído
  no `pom.xml`. Criar o banco e usuário:
  ```sql
  CREATE DATABASE nexo;
  CREATE USER nexo WITH PASSWORD '...';
  GRANT ALL PRIVILEGES ON DATABASE nexo TO nexo;
  ```
  O `ddl-auto: update` cria o schema na primeira execução. `SEED_ENABLED` fica
  `false` em produção (não popular dados de exemplo). Em produção madura, migrar
  para Flyway/Liquibase.

---

## 5. Variáveis de ambiente (produção)

| Variável | Obrigatória | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | ✅ | Ativa `application-prod.yml` |
| `NEXO_JWT_SECRET` | ✅ | Segredo do JWT (forte, ≥ 64 chars). **Nunca** usar o default de dev |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | ✅ | Conexão PostgreSQL |
| `CORS_ORIGINS` | ✅ | Origens liberadas (o domínio do frontend) |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | — | Duração dos tokens (default 15 / 7) |
| `SEED_ENABLED` | — | `false` em produção |

---

## 6. Checklist pós-deploy

- [ ] `GET https://nexo-gestao-escolar.com.br/` carrega o app (index.html encontrado).
- [ ] Recarregar em uma rota profunda (ex.: `/configuracoes`) não dá 404 (fallback SPA).
- [ ] Login funciona (`POST /api/auth/login`).
- [ ] Acentos corretos nas telas (Jackson `escape-non-ascii` já ativo).
- [ ] Chat professor ↔ diretor conecta (indicador verde) e troca mensagens em tempo real.
- [ ] Exportações (PDF/XLSX/CSV) baixam corretamente.
- [ ] `NEXO_JWT_SECRET` de produção configurado (não o de dev).
