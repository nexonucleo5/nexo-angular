# syntax=docker/dockerfile:1

# Frontend isolado: build do Angular + nginx. O nginx serve os estáticos e
# repassa /api e /ws para o serviço "backend" (ver nginx.conf) — por isso o
# build de produção, que usa apiUrl "/api" relativo, funciona sem CORS.
#
# Contexto de build: a raiz do repositório (precisa enxergar nexo/ E docker/frontend/;
# o Dockerfile.dockerignore ao lado deixa passar só isso).
#   docker build -f docker/frontend/Dockerfile .
ARG NODE_VERSION=22

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build do Angular
# bookworm-slim: mesma glibc da tag cheia, ~10x menor e sem o toolchain que
# responde pela maior parte dos CVEs.
# ─────────────────────────────────────────────────────────────────────────────
FROM node:${NODE_VERSION}-bookworm-slim AS build
WORKDIR /build

ENV npm_config_fund=false \
    npm_config_audit=false \
    npm_config_update_notifier=false

# package*.json antes do resto: mexer no código-fonte não reinstala as deps.
COPY nexo/package.json nexo/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked \
    npm ci

COPY nexo/ ./
RUN npx ng build --configuration production --base-href /
# saída: /build/dist/nexo/ (angular.json usa browser:"" → index.html na raiz)

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — nginx sem privilégio
# nginx-unprivileged roda como usuário comum e escuta na 8080 (não precisa de
# root nem de CAP_NET_BIND_SERVICE), o que permite read_only + cap_drop ALL.
# ─────────────────────────────────────────────────────────────────────────────
FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/frontend/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /build/dist/nexo/ /usr/share/nginx/html/

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
