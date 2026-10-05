#!/usr/bin/env bash
set -euo pipefail
# Only restore into a newly initialized, empty target selected by Compose env vars.
backup_dir="${1:?Usage: scripts/restore.sh BACKUP_DIRECTORY --empty-target}"
[[ "${2:-}" == '--empty-target' ]] || { echo 'Restore requires --empty-target and refuses existing application data.' >&2; exit 1; }
backup_dir="$(cd "$backup_dir" && pwd)"
(cd "$backup_dir" && sha256sum --check --quiet SHA256SUMS)
if docker compose ps --services --status running | grep -Eq '^(frontend|api-gateway|auth-service|listing-service|passport-service|matching-service)$'; then
    echo 'Target application services must be stopped.' >&2; exit 1
fi
for service in auth listing passport matching; do
    rows=$(docker compose exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d "$1" -Atc "select count(*) from information_schema.tables where table_schema = '\''public'\''"' sh "hvostid_$service")
    [[ "$rows" == '0' ]] || { echo "Target database hvostid_$service is not empty." >&2; exit 1; }
done
docker compose run --rm --no-deps --entrypoint sh minio-init -ec '
    mc alias set target http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null
    for bucket in "$MINIO_DOCUMENTS_BUCKET" "$MINIO_PHOTOS_BUCKET"; do
        test -z "$(mc ls --recursive "target/$bucket")" || { echo "Target bucket is not empty" >&2; exit 1; }
    done'
for service in auth listing passport matching; do
    docker compose exec -T postgres sh -ec '
        name="$1"; variable="$(printf "%s" "$name" | tr a-z A-Z)_DB_PASSWORD"
        export PGPASSWORD="$(printenv "$variable")"
        exec pg_restore --exit-on-error --no-owner --no-acl -h localhost -U "hvostid_$name" -d "hvostid_$name"
    ' sh "$service" < "$backup_dir/$service.dump"
done
docker compose run --rm --no-deps --entrypoint sh -v "$backup_dir:/backup:ro" minio-init -ec '
    mc alias set target http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null
    for bucket in "$MINIO_DOCUMENTS_BUCKET" "$MINIO_PHOTOS_BUCKET"; do
        mc mirror "/backup/minio/$bucket" "target/$bucket"
    done'
echo 'Restore complete. Start application services and run the smoke checks before routing traffic.'
