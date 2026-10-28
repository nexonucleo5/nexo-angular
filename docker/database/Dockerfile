FROM postgres:17-alpine

ENV TZ=America/Sao_Paulo \
    POSTGRES_DB=nexo \
    POSTGRES_USER=nexo

# scripts de initdb rodam só quando o volume está vazio
COPY docker/database/initdb/ /docker-entrypoint-initdb.d/

EXPOSE 5432

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
    CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" || exit 1
