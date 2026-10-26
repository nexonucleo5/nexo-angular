#!/bin/sh
# Roda uma única vez, ao criar o banco. O schema é criado pelo Hibernate (DDL_AUTO=update).
set -e
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "ALTER DATABASE \"$POSTGRES_DB\" SET timezone TO 'America/Sao_Paulo';"
