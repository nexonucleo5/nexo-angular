# syntax=docker/dockerfile:1
# Backend Spring Boot isolado (API + WebSocket). O Angular NÃO vai junto: ele
# tem imagem própria em nexo/Dockerfile. Contexto de build: ./backend
ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9

FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Três tentativas: o Maven Central às vezes corta o download no meio.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    for tentativa in 1 2 3; do \
        mvn -B -ntp package -DskipTests && exit 0; \
        echo ">> tentativa $tentativa falhou; refazendo"; \
    done; \
    exit 1

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime
RUN apk upgrade --no-cache && apk add --no-cache tzdata
ENV TZ=America/Sao_Paulo
RUN addgroup -S nexo && adduser -S -G nexo -h /app nexo
WORKDIR /app
COPY --from=build --chown=nexo:nexo /build/target/*.jar app.jar
USER nexo

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# Qualquer resposta HTTP (mesmo 401/404) prova que o Tomcat está de pé; só
# conexão recusada reprova. wget -S imprime os cabeçalhos mesmo em erro HTTP.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -S -O /dev/null "http://127.0.0.1:${PORT:-8080}/" 2>&1 | grep -q "HTTP/" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
