# syntax=docker/dockerfile:1
# Frontend Angular servido por nginx, que também faz proxy de /api e /ws para o
# backend (mesma origem: sem CORS e sem URL de API embutida no bundle).
# Contexto de build: ./nexo
ARG NODE_VERSION=22

FROM node:${NODE_VERSION}-bookworm-slim AS build
WORKDIR /build
ENV npm_config_fund=false npm_config_audit=false npm_config_update_notifier=false
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked npm ci
COPY . ./
RUN npx ng build --configuration production --base-href /
# saída: /build/dist/nexo (angular.json usa browser:"")

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
# O template é processado pelo entrypoint do nginx (envsubst) a cada start.
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist/nexo/ /usr/share/nginx/html/
ENV BACKEND_HOST=backend BACKEND_PORT=8080 \
    NGINX_ENVSUBST_FILTER=BACKEND_
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
