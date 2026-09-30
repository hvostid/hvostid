#!/usr/bin/env bash
set -euo pipefail
# COMPOSE_FILE / COMPOSE_PROJECT_NAME select the source environment.
backup_dir="${1:?Usage: scripts/backup.sh NEW_BACKUP_DIRECTORY}"
[[ ! -e "$backup_dir" ]] || { echo 'Backup target already exists; choose a new directory.' >&2; exit 1; }
umask 077
mkdir -p "$backup_dir/minio"
backup_dir="$(cd "$backup_dir" && pwd)"
mapfile -t running < <(docker compose ps --services --status running | grep -E '^(frontend|api-gateway|auth-service|listing-service|passport-service|matching-service)$' || true)
resume() { if (( ${#running[@]} )); then docker compose start "${running[@]}" >/dev/null; fi; }
trap resume EXIT
if (( ${#running[@]} )); then docker compose stop "${running[@]}" >/dev/null; fi
for service in auth listing passport matching; do
    docker compose exec -T postgres sh -ec 'exec pg_dump -U "$POSTGRES_USER" -Fc --no-owner --no-acl "$1"' sh "hvostid_$service" > "$backup_dir/$service.dump"
done
docker compose run --pull never --rm --no-deps --entrypoint sh -v "$backup_dir:/backup" minio-init -ec '
    mc alias set source http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null
    for bucket in "$MINIO_DOCUMENTS_BUCKET" "$MINIO_PHOTOS_BUCKET"; do
        mkdir -p "/backup/minio/$bucket"
        mc mirror "source/$bucket" "/backup/minio/$bucket"
    done'
docker compose ps -aq | while read -r container; do
    docker inspect --format '{{.Config.Image}} {{.Image}}' "$container"
done > "$backup_dir/images.txt"
date -u +%FT%TZ > "$backup_dir/completed-at.txt"
(cd "$backup_dir" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
echo "Consistent backup complete: $backup_dir"
