# syntax=docker/dockerfile:1
# Contexto: raiz do repo. docker build -f docker/backend/Dockerfile -t nexo-backend .
ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9

FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /build

COPY backend/pom.xml ./
COPY backend/src ./src

# Maven Central às vezes corta o download no meio; repete até 3 vezes.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    for tentativa in 1 2 3; do \
        mvn -B -ntp package -DskipTests && exit 0; \
        echo ">> tentativa $tentativa falhou"; \
    done; \
    exit 1

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

# gnupg/coreutils vêm na base do Temurin e trazem CVEs que o app não usa.
RUN apk upgrade --no-cache \
 && apk add --no-cache tzdata \
 && apk del --purge gnupg gnupg-dirmngr gnupg-gpgconf gnupg-keyboxd \
                    gnupg-utils gnupg-wks-client pinentry \
                    coreutils coreutils-env coreutils-fmt coreutils-sha512sum
ENV TZ=America/Sao_Paulo

RUN addgroup -S nexo && adduser -S -G nexo -h /app nexo
WORKDIR /app
RUN chown nexo:nexo /app

COPY --from=build --chown=nexo:nexo /build/target/*.jar app.jar

USER nexo

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Sem actuator nem rota pública de saúde: só testa a porta.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD nc -z 127.0.0.1 "${PORT:-8080}" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
