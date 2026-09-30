#!/usr/bin/env bash
set -euo pipefail
# Run once during an upgrade of an existing volume, with application writers stopped.
if docker compose ps --services --status running | grep -Eq '^(auth-service|listing-service|passport-service|matching-service)$'; then
    echo 'Stop application services before transferring database ownership.' >&2; exit 1
fi
docker compose exec -T postgres bash /docker-entrypoint-initdb.d/init-databases.sh
for service in auth listing passport matching; do
    docker compose exec -T postgres sh -ec 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" --set=role="$1"' sh "hvostid_$service" <<'SQL'
SELECT format('ALTER DATABASE %I OWNER TO %I', :'role', :'role') \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'role') \gexec
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'role')
FROM pg_tables WHERE schemaname = 'public' \gexec
SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', sequence_schema, sequence_name, :'role')
FROM information_schema.sequences WHERE sequence_schema = 'public' \gexec
SQL
done
echo 'Database ownership transferred to separate application roles.'
