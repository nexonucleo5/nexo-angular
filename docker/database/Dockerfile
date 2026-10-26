# syntax=docker/dockerfile:1

# PostgreSQL do Nexo. É o mesmo banco do perfil prod do backend, então o que
# roda aqui é o que roda em produção (o H2 fica só para `mvn spring-boot:run`).
#
# Major fixada de propósito: trocar de major exige pg_upgrade/dump, e com a
# tag solta um `pull` quebraria o volume existente. Os patches de segurança
# entram sozinhos (17-alpine segue o último 17.x).
FROM postgres:17-alpine

# Usuário, senha e banco vêm do ambiente (POSTGRES_USER/PASSWORD/DB) — nada de
# credencial na imagem, que vai para o Docker Hub.
#
# max_connections: o pool do backend usa 5 (DB_POOL_MAX); 50 sobra com folga
# para psql/DBeaver e para reiniciar o backend sem esperar as conexões antigas.
CMD ["postgres", "-c", "max_connections=50"]

EXPOSE 5432

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD pg_isready -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" || exit 1
