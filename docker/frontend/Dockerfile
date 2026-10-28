# syntax=docker/dockerfile:1
# build a partir da raiz: docker build -f docker/frontend/Dockerfile -t nexo-frontend .
ARG NODE_VERSION=22
ARG NGINX_VERSION=1.28

FROM node:${NODE_VERSION}-bookworm-slim AS build
WORKDIR /build

ENV npm_config_fund=false \
    npm_config_audit=false \
    npm_config_update_notifier=false

# --legacy-peer-deps: o ng2-charts pede @angular/cdk e o npm ci dá ERESOLVE sem a flag
COPY nexo/package.json nexo/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked \
    npm ci --legacy-peer-deps

COPY nexo/ ./
RUN npx ng build --configuration production --base-href /

FROM nginxinc/nginx-unprivileged:${NGINX_VERSION}-alpine AS runtime

USER root
RUN apk upgrade --no-cache
USER nginx

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/frontend/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build --chown=nginx:nginx /build/dist/nexo/ /usr/share/nginx/html/

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
