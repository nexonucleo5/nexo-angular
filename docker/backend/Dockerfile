# syntax=docker/dockerfile:1

# Backend isolado: só a API Spring (jar) + JRE. O Angular NÃO entra aqui — ele
# tem a própria imagem em docker/frontend e o nginx faz a ponte para /api e /ws.
#
# Contexto de build: ./backend (o compose e o CI já apontam para lá).
#   docker build -f docker/backend/Dockerfile backend
ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build do jar
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /build

# pom antes do código: mexer em .java não invalida a camada de dependências.
COPY pom.xml ./
COPY src ./src

# Quem guarda as dependências é o cache mount do /root/.m2. As três tentativas
# existem porque o Maven Central às vezes corta o download no meio ("Premature
# end of Content-Length delimited message body"); cada tentativa reaproveita o
# que já veio, então repetir custa pouco.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    for tentativa in 1 2 3; do \
        mvn -B -ntp package -DskipTests && exit 0; \
        echo ">> tentativa $tentativa falhou; refazendo os downloads truncados"; \
    done; \
    exit 1

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime enxuto
# alpine: o app é Java puro (sem JNI/AWT), nada depende de glibc.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

# TZ: o app grava LocalDate.now() em chamada, frequência e notas; em UTC tudo
# lançado depois das 21h cairia no dia seguinte.
#
# gnupg/pinentry vêm na base do Temurin só para conferir assinatura no build
# dela (e arrastam o sqlite-libs, origem dos CVEs HIGH); coreutils sai porque o
# busybox já cobre o que o app usa. Tudo na mesma camada do apk, senão os
# arquivos ficam presos na camada de baixo.
RUN apk upgrade --no-cache \
 && apk add --no-cache tzdata \
 && apk del --purge gnupg gnupg-dirmngr gnupg-gpgconf gnupg-keyboxd \
                    gnupg-utils gnupg-wks-client pinentry \
                    coreutils coreutils-env coreutils-fmt coreutils-sha512sum
ENV TZ=America/Sao_Paulo

# Usuário sem privilégio: falha no app não vira root dentro do container.
RUN addgroup -S nexo && adduser -S -G nexo -h /app nexo
WORKDIR /app

# Só o fat jar casa o curinga — o repackage deixa o original como *.jar.original.
COPY --from=build --chown=nexo:nexo /build/target/*.jar app.jar

USER nexo

# O banco de verdade vem do perfil prod (DB_URL/DB_USER/DB_PASSWORD), então o
# container não precisa de volume nem de disco gravável.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Sem actuator e sem rota pública de saúde: sonda a porta TCP, que só abre
# depois que o Spring terminou de subir (Tomcat é o último a iniciar).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD nc -z 127.0.0.1 8080 || exit 1

# exec: o java vira PID 1 e recebe o SIGTERM do `docker stop` direto, o que
# ativa o shutdown gracioso do Spring.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
