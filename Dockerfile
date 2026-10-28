# syntax=docker/dockerfile:1

# Monolito: o Angular é compilado e embutido no jar do Spring (usado no Render).
# Na versão com 3 containers, veja docker/*/Dockerfile.
ARG NODE_VERSION=22
ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9

# 1) build do Angular
FROM node:${NODE_VERSION}-bookworm-slim AS frontend
WORKDIR /build

ENV npm_config_fund=false \
    npm_config_audit=false \
    npm_config_update_notifier=false

COPY nexo/package.json nexo/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm,sharing=locked \
    npm ci

COPY nexo/ ./
RUN npx ng build --configuration production --base-href /

# 2) build do Spring, com o Angular dentro de resources/static
FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS backend
WORKDIR /build

COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /build/dist/nexo/ ./src/main/resources/static/

# tenta 3x porque o Maven Central às vezes corta o download
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    for tentativa in 1 2 3; do \
        mvn -B -ntp package -DskipTests && exit 0; \
        echo ">> tentativa $tentativa falhou"; \
    done; \
    exit 1

# 3) imagem final: só JRE + jar
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

# tzdata para as datas (frequência, notas) não virarem o dia seguinte em UTC;
# o resto remove pacotes da base que o app não usa
RUN apk upgrade --no-cache \
 && apk add --no-cache tzdata \
 && apk del --purge gnupg gnupg-dirmngr gnupg-gpgconf gnupg-keyboxd \
                    gnupg-utils gnupg-wks-client pinentry \
                    coreutils coreutils-env coreutils-fmt coreutils-sha512sum
ENV TZ=America/Sao_Paulo

RUN addgroup -S nexo && adduser -S -G nexo -h /app nexo
WORKDIR /app

# o H2 grava em ./data
RUN mkdir -p /app/data && chown -R nexo:nexo /app

COPY --from=backend --chown=nexo:nexo /build/target/*.jar app.jar

USER nexo

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider "http://127.0.0.1:${PORT:-8080}/" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
