#!/usr/bin/env bash
set -euo pipefail
for service in auth listing passport matching; do
    variable="${service^^}_DB_PASSWORD"
    password="${!variable:?Separate database password is required}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
        --set=role="hvostid_${service}" --set=password="$password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'role', :'role')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'role') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'role') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'role', :'role') \gexec
SQL
done
