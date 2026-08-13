# syntax=docker/dockerfile:1

# Monolito: o Angular é compilado e embutido como estático dentro do jar do
# Spring, que é a única coisa que vai para a imagem final.
#
# Versões em um lugar só. As tags são de major/LTS de propósito: assim cada
# build pega o último patch de segurança da base sem precisar editar arquivo.
ARG NODE_VERSION=22
ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build do frontend Angular
# bookworm-slim (e não a tag cheia do node): mesma glibc, ~10x menor e sem o
# toolchain de compilação que responde pela maior parte dos CVEs da imagem full.
# ─────────────────────────────────────────────────────────────────────────────
FROM node:${NODE_VERSION}-bookworm-slim AS frontend
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
# saída: /build/dist/nexo/ (index.html + assets na raiz — angular.json usa browser:"")

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — build do backend Spring, com o Angular embutido como estáticos
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS backend
WORKDIR /build

COPY backend/pom.xml ./
COPY backend/src ./src
# injeta o build do Angular em resources/static → o Spring serve as telas
COPY --from=frontend /build/dist/nexo/ ./src/main/resources/static/

# Quem guarda as dependências é o cache mount do /root/.m2, não uma camada com
# `dependency:go-offline` — aquele goal resolve até o que é opcional (icu4j e
# bouncycastle vêm junto do POI e nunca são usados aqui), o que só rende
# 14MB de download a mais e um ponto extra de falha de rede.
#
# As três tentativas não são paranoia: o Maven Central corta download no meio
# aqui com alguma frequência ("Premature end of Content-Length delimited
# message body") e um único jar pela metade derruba o build inteiro — o que no
# Render, que constrói do zero a cada deploy, vira deploy falho. Cada tentativa
# reaproveita o que já veio, então repetir custa pouco.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    for tentativa in 1 2 3; do \
        mvn -B -ntp package -DskipTests && exit 0; \
        echo ">> tentativa $tentativa falhou; refazendo os downloads truncados"; \
    done; \
    exit 1

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — runtime enxuto: só o JRE + o jar
# alpine porque o app é Java puro (sem JNI, sem AWT): nada aqui depende de
# glibc, e a base sai de ~200MB de Debian para ~50MB com quase nenhum CVE.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

# O app grava LocalDate.now() em chamada, frequência e lançamento de notas. Sem
# isso o container roda em UTC e tudo que for lançado depois das 21h cai no dia
# seguinte. tzdata deixa TZ valer para o Java e para os logs.
#
# O gnupg e o pinentry vêm na base do Temurin só para conferir assinatura no
# build dela, e arrastam o sqlite-libs junto — que é de onde saíam os únicos
# CVEs HIGH da imagem. O coreutils sai pelo mesmo motivo: o busybox reassume o
# /usr/bin/env sozinho e nada num app Java precisa das versões GNU. Tudo na
# mesma camada do apk add, senão os arquivos ficariam presos na camada de baixo.
#
# O apk upgrade pega os patches que sairam depois da base ser publicada — hoje
# expat 2.8.2 e p11-kit 0.26.2, que sozinhos respondem por 14 das 20
# vulnerabilidades que a imagem tinha.
RUN apk upgrade --no-cache \
 && apk add --no-cache tzdata \
 && apk del --purge gnupg gnupg-dirmngr gnupg-gpgconf gnupg-keyboxd \
                    gnupg-utils gnupg-wks-client pinentry \
                    coreutils coreutils-env coreutils-fmt coreutils-sha512sum
ENV TZ=America/Sao_Paulo

# Usuário sem privilégio: uma falha no app não vira root dentro do container.
RUN addgroup -S nexo && adduser -S -G nexo -h /app nexo
WORKDIR /app

# O H2 de dev grava em ./data (application.yml); o diretório precisa existir e
# pertencer ao usuário, senão o app não sobe com o filesystem em read-only.
RUN mkdir -p /app/data && chown -R nexo:nexo /app

# Curinga em vez do nome versionado: subir a versão no pom não quebra a imagem.
# Só o fat jar casa — o repackage deixa o jar original como *.jar.original.
COPY --from=backend --chown=nexo:nexo /build/target/*.jar app.jar

USER nexo

# MaxRAMPercentage: a JVM enxerga o limite do container, não a RAM da máquina —
# sem isso ela dimensiona o heap pela RAM do host e o container leva OOM kill.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# O perfil vive na imagem, não só no painel do Render. Sem esta linha a imagem
# subia no perfil default — o de desenvolvimento —, e todo o endurecimento de
# produção dependia de alguém lembrar de uma variável fora do repositório. Quem
# esquecesse ficava com o console do H2 aberto e com as contas de demonstração
# (senha 123456) no ar.
#
# Agora errar exige uma ação: o perfil prod não tem default para DB_URL nem para
# NEXO_JWT_SECRET, então uma configuração incompleta derruba o boot com mensagem
# clara em vez de servir um H2 em arquivo achando que está tudo bem.
# O compose.yaml sobrescreve para `dev`, que é o que faz sentido na máquina local.
ENV SPRING_PROFILES_ACTIVE=prod

# Informativo: o Render (e o compose) injetam a porta real em $PORT.
EXPOSE 8080

# /actuator/health é público e sem detalhe (application.yml), então dá para sondar
# sem token. Antes isto batia em "/", que devolve o index.html do Angular: o
# container continuava "saudável" com o banco inacessível, porque o Tomcat servia
# o arquivo estático do mesmo jeito. O health agrega o indicador do DataSource e
# responde 503 nesse caso, que é o que faz o Render tirar a instância do ar.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

# exec: o java vira PID 1 e recebe o SIGTERM do `docker stop` direto, fechando
# o pool de conexões em vez de morrer no timeout.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
