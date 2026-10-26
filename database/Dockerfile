# PostgreSQL do Nexo. Os scripts de database/init/ rodam só na primeira subida
# (volume vazio). Usuário, senha e banco vêm de POSTGRES_* no compose.
FROM postgres:16-alpine
COPY init/ /docker-entrypoint-initdb.d/
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" || exit 1
